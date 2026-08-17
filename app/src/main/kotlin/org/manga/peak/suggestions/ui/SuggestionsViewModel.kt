package org.manga.peak.suggestions.ui

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import org.manga.peak.R
import org.manga.peak.core.parser.MangaDataRepository
import org.manga.peak.core.prefs.AppSettings
import org.manga.peak.core.prefs.observeAsFlow
import org.manga.peak.core.util.ext.onFirst
import org.manga.peak.list.domain.MangaListMapper
import org.manga.peak.list.domain.QuickFilterListener
import org.manga.peak.list.ui.MangaListViewModel
import org.manga.peak.list.ui.model.EmptyState
import org.manga.peak.list.ui.model.LoadingState
import org.manga.peak.list.ui.model.toErrorState
import org.manga.peak.suggestions.domain.SuggestionRepository
import org.manga.peak.suggestions.domain.SuggestionsListQuickFilter
import javax.inject.Inject
import org.manga.peak.local.data.LocalStorageChanges
import org.manga.peak.local.domain.model.LocalManga
import kotlinx.coroutines.flow.SharedFlow

@HiltViewModel
class SuggestionsViewModel @Inject constructor(
	repository: SuggestionRepository,
	settings: AppSettings,
	private val mangaListMapper: MangaListMapper,
	private val quickFilter: SuggestionsListQuickFilter,
	private val suggestionsScheduler: SuggestionsWorker.Scheduler,
	mangaDataRepository: MangaDataRepository,
	@LocalStorageChanges localStorageChanges: SharedFlow<LocalManga?>,
) : MangaListViewModel(settings, mangaDataRepository, localStorageChanges), QuickFilterListener by quickFilter {

	override val listMode = settings.observeAsFlow(AppSettings.KEY_LIST_MODE_SUGGESTIONS) { suggestionsListMode }
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, settings.suggestionsListMode)

	override val content = combine(
		quickFilter.appliedOptions.combineWithSettings().flatMapLatest { repository.observeAll(0, it) },
		quickFilter.appliedOptions,
		observeListModeWithTriggers(),
	) { list, filters, mode ->
		when {
			list.isEmpty() -> if (filters.isEmpty()) {
				listOf(
					EmptyState(
						icon = R.drawable.ic_empty_common,
						textPrimary = R.string.nothing_found,
						textSecondary = R.string.text_suggestion_holder,
						actionStringRes = 0,
					),
				)
			} else {
				listOfNotNull(
					quickFilter.filterItem(filters),
					EmptyState(
						icon = R.drawable.ic_empty_common,
						textPrimary = R.string.nothing_found,
						textSecondary = R.string.text_empty_holder_secondary_filtered,
						actionStringRes = 0,
					),
				)
			}

			else -> buildList(list.size + 1) {
				quickFilter.filterItem(filters)?.let(::add)
				mangaListMapper.toListModelList(this, list, mode)
			}
		}
	}.onStart {
		loadingCounter.increment()
	}.onFirst {
		loadingCounter.decrement()
	}.catch {
		emit(listOf(it.toErrorState(canRetry = false)))
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, listOf(LoadingState()))

	override fun onRefresh() = Unit

	override fun onRetry() = Unit

	fun updateSuggestions() {
		launchJob(Dispatchers.Default) {
			suggestionsScheduler.startNow()
		}
	}
}
