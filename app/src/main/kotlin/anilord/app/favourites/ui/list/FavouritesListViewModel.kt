package anilord.app.favourites.ui.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import anilord.app.R
import anilord.app.core.nav.AppRouter
import anilord.app.core.parser.MangaDataRepository
import anilord.app.core.prefs.AppSettings
import anilord.app.core.prefs.ListMode
import anilord.app.core.prefs.observeAsFlow
import anilord.app.core.ui.util.ReversibleAction
import anilord.app.core.util.ext.call
import anilord.app.core.util.ext.flattenLatest
import anilord.app.favourites.domain.FavoritesListQuickFilter
import anilord.app.favourites.domain.FavouritesRepository
import anilord.app.favourites.ui.list.FavouritesListFragment.Companion.NO_ID
import anilord.app.history.domain.MarkAsReadUseCase
import anilord.app.list.domain.ListFilterOption
import anilord.app.list.domain.ListSortOrder
import anilord.app.list.domain.MangaListMapper
import anilord.app.list.domain.QuickFilterListener
import anilord.app.list.ui.MangaListViewModel
import anilord.app.list.ui.model.EmptyState
import anilord.app.list.ui.model.ListModel
import anilord.app.list.ui.model.LoadingState
import anilord.app.list.ui.model.toErrorState
import org.koitharu.kotatsu.parsers.model.Manga
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import anilord.app.local.data.LocalStorageChanges
import anilord.app.local.domain.model.LocalManga
import kotlinx.coroutines.flow.SharedFlow

private const val PAGE_SIZE = 16

@HiltViewModel
class FavouritesListViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val repository: FavouritesRepository,
	private val mangaListMapper: MangaListMapper,
	private val markAsReadUseCase: MarkAsReadUseCase,
	quickFilterFactory: FavoritesListQuickFilter.Factory,
	settings: AppSettings,
	mangaDataRepository: MangaDataRepository,
	@LocalStorageChanges localStorageChanges: SharedFlow<LocalManga?>,
) : MangaListViewModel(settings, mangaDataRepository, localStorageChanges), QuickFilterListener {

	val categoryId: Long = savedStateHandle[AppRouter.KEY_ID] ?: NO_ID
	private val quickFilter = quickFilterFactory.create(categoryId)
	private val refreshTrigger = MutableStateFlow(Any())
	private val limit = MutableStateFlow(PAGE_SIZE)
	private val isPaginationReady = AtomicBoolean(false)

	override val listMode = settings.observeAsFlow(AppSettings.KEY_LIST_MODE_FAVORITES) { favoritesListMode }
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, settings.favoritesListMode)

	val sortOrder: StateFlow<ListSortOrder?> = if (categoryId == NO_ID) {
		settings.observeAsFlow(AppSettings.KEY_FAVORITES_ORDER) {
			allFavoritesSortOrder
		}
	} else {
		repository.observeCategory(categoryId)
			.withErrorHandling()
			.map { it?.order }
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, null)

	override val content = combine(
		observeFavorites(),
		quickFilter.appliedOptions,
		observeListModeWithTriggers(),
		refreshTrigger,
	) { list, filters, mode, _ ->
		list.mapList(mode, filters)
	}.distinctUntilChanged().onEach {
		isPaginationReady.set(true)
	}.catch {
		emit(listOf(it.toErrorState(canRetry = false)))
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, listOf(LoadingState()))

	override fun onRefresh() {
		refreshTrigger.value = Any()
	}

	override fun onRetry() = Unit

	override fun setFilterOption(option: ListFilterOption, isApplied: Boolean) =
		quickFilter.setFilterOption(option, isApplied)

	override fun toggleFilterOption(option: ListFilterOption) = quickFilter.toggleFilterOption(option)

	override fun clearFilter() = quickFilter.clearFilter()

	fun markAsRead(items: Set<Manga>) {
		launchLoadingJob(Dispatchers.Default) {
			markAsReadUseCase(items)
			onRefresh()
		}
	}

	fun setPinned(ids: Set<Long>, isPinned: Boolean) {
		launchJob(Dispatchers.Default) {
			repository.setPinned(ids, categoryId, isPinned)
			onRefresh()
		}
	}

	fun removeFromFavourites(ids: Set<Long>) {
		if (ids.isEmpty()) {
			return
		}
		launchJob(Dispatchers.Default) {
			val handle = if (categoryId == NO_ID) {
				repository.removeFromFavourites(ids)
			} else {
				repository.removeFromCategory(categoryId, ids)
			}
			onActionDone.call(ReversibleAction(R.string.removed_from_favourites, handle))
		}
	}

	fun setSortOrder(order: ListSortOrder) {
		if (categoryId == NO_ID) {
			return
		}
		launchJob {
			repository.setCategoryOrder(categoryId, order)
		}
	}

	fun requestMoreItems() {
		if (isPaginationReady.compareAndSet(true, false)) {
			limit.value += PAGE_SIZE
		}
	}

	private suspend fun List<Manga>.mapList(mode: ListMode, filters: Set<ListFilterOption>): List<ListModel> {
		if (isEmpty()) {
			return if (filters.isEmpty()) {
				listOf(getEmptyState(hasFilters = false))
			} else {
				listOfNotNull(quickFilter.filterItem(filters), getEmptyState(hasFilters = true))
			}
		}
		val pinnedIds = repository.getPinnedIds(categoryId)
		val result = ArrayList<ListModel>(size + 1)
		quickFilter.filterItem(filters)?.let(result::add)
		mangaListMapper.toListModelList(result, this, mode, MangaListMapper.NO_FAVORITE, pinnedIds)
		return result
	}

	private fun observeFavorites() = if (categoryId == NO_ID) {
		combine(
			sortOrder.filterNotNull(),
			quickFilter.appliedOptions.combineWithSettings(),
			limit,
		) { order, filters, limit ->
			isPaginationReady.set(false)
			repository.observeAll(order, filters, limit)
		}.flattenLatest()
	} else {
		combine(quickFilter.appliedOptions.combineWithSettings(), limit) { filters, limit ->
			repository.observeAll(categoryId, filters, limit)
		}.flattenLatest()
	}

	private fun getEmptyState(hasFilters: Boolean) = if (hasFilters) {
		EmptyState(
			icon = R.drawable.ic_empty_favourites,
			textPrimary = R.string.nothing_found,
			textSecondary = R.string.text_empty_holder_secondary_filtered,
			actionStringRes = R.string.reset_filter,
		)
	} else {
		EmptyState(
			icon = R.drawable.ic_empty_favourites,
			textPrimary = R.string.text_empty_holder_primary,
			textSecondary = if (categoryId == NO_ID) {
				R.string.you_have_not_favourites_yet
			} else {
				R.string.favourites_category_empty
			},
			actionStringRes = 0,
		)
	}
}
