package anilord.app.explore.ui

import androidx.collection.LongSet
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import anilord.app.R
import anilord.app.core.model.MangaSourceInfo
import anilord.app.core.model.isNsfw
import anilord.app.core.model.isVisibleInCurrentUi
import anilord.app.core.os.AppShortcutManager
import anilord.app.core.prefs.AppSettings
import anilord.app.core.prefs.observeAsFlow
import anilord.app.core.prefs.observeAsStateFlow
import anilord.app.core.ui.BaseViewModel
import anilord.app.core.ui.util.ReversibleAction
import anilord.app.core.util.ext.MutableEventFlow
import anilord.app.core.util.ext.call
import anilord.app.core.util.ext.combine
import anilord.app.explore.data.MangaSourcesRepository
import anilord.app.explore.data.SourcePreset
import anilord.app.explore.data.SourcePresetsRepository
import anilord.app.explore.domain.ExploreRepository
import anilord.app.explore.ui.model.ExploreButtons
import anilord.app.explore.ui.model.BrowseGroup
import anilord.app.explore.ui.model.MangaSourceItem
import anilord.app.explore.ui.model.RecommendationsItem
import anilord.app.list.ui.model.EmptyHint
import anilord.app.list.ui.model.ListHeader
import anilord.app.list.ui.model.ListModel
import anilord.app.list.ui.model.LoadingState
import anilord.app.list.ui.model.MangaCompactListModel
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import anilord.app.suggestions.domain.SuggestionRepository
import javax.inject.Inject

@HiltViewModel
class ExploreViewModel @Inject constructor(
	private val settings: AppSettings,
	private val suggestionRepository: SuggestionRepository,
	private val exploreRepository: ExploreRepository,
	private val sourcesRepository: MangaSourcesRepository,
	private val presetsRepository: SourcePresetsRepository,
	private val shortcutManager: AppShortcutManager,
) : BaseViewModel() {

	val isGrid = settings.observeAsStateFlow(
		key = AppSettings.KEY_SOURCES_GRID,
		scope = viewModelScope + Dispatchers.IO,
		valueProducer = { isSourcesGridMode },
	)

	val isAllSourcesEnabled = settings.observeAsStateFlow(
		scope = viewModelScope + Dispatchers.IO,
		key = AppSettings.KEY_SOURCES_ENABLED_ALL,
		valueProducer = { isAllSourcesEnabled },
	)

	private val isSuggestionsEnabled = settings.observeAsFlow(
		key = AppSettings.KEY_SUGGESTIONS,
		valueProducer = { isSuggestionsEnabled },
	)

	private val activePresetFlow: StateFlow<SourcePreset?> = settings.observeAsFlow(
		key = AppSettings.KEY_ACTIVE_SOURCE_PRESET,
		valueProducer = { activeSourcePresetId },
	).flatMapLatest { id ->
		if (id == 0L) flowOf(null) else presetsRepository.observe(id)
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, null)

	val onOpenManga = MutableEventFlow<Manga>()
	val onActionDone = MutableEventFlow<ReversibleAction>()
	val onShowSuggestionsTip = MutableEventFlow<Unit>()
	private val isRandomLoading = MutableStateFlow(false)
	val selectedBrowseGroup = MutableStateFlow(BrowseGroup.ALL)

	val presets: StateFlow<List<SourcePreset>> = presetsRepository.observeAll()
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, emptyList())

	val activePresetId: Long
		get() = settings.activeSourcePresetId

	fun setActivePreset(presetId: Long) {
		settings.activeSourcePresetId = presetId
	}

	fun setBrowseGroup(group: BrowseGroup) {
		selectedBrowseGroup.value = group
	}

	val content: StateFlow<List<ListModel>> = isLoading.flatMapLatest { loading ->
		if (loading) {
			flowOf(getLoadingStateList())
		} else {
			createContentFlow()
		}
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, getLoadingStateList())

	init {
		launchJob(Dispatchers.Default) {
			if (!settings.isSuggestionsEnabled && settings.isTipEnabled(TIP_SUGGESTIONS)) {
				onShowSuggestionsTip.call(Unit)
			}
		}
	}

	fun openRandom() {
		if (isRandomLoading.value) {
			return
		}
		launchJob(Dispatchers.Default) {
			isRandomLoading.value = true
			try {
				val manga = exploreRepository.findRandomManga(tagsLimit = 8)
				onOpenManga.call(manga)
			} finally {
				isRandomLoading.value = false
			}
		}
	}

	fun disableSources(sources: Collection<MangaSource>) {
		launchJob(Dispatchers.Default) {
			val preset = activePresetFlow.value
			if (preset != null) {
				val namesToRemove = sources.mapTo(HashSet(sources.size)) { it.name }
				val updated = preset.sources - namesToRemove
				presetsRepository.updatePresetSources(preset.id, updated)
				val message = if (sources.size == 1) R.string.source_disabled else R.string.sources_disabled
				onActionDone.call(ReversibleAction(message, null))
			} else {
				val rollback = sourcesRepository.setSourcesEnabled(sources, isEnabled = false)
				val message = if (sources.size == 1) R.string.source_disabled else R.string.sources_disabled
				onActionDone.call(ReversibleAction(message, rollback))
			}
		}
	}

	fun requestPinShortcut(source: MangaSource) {
		launchLoadingJob(Dispatchers.Default) {
			shortcutManager.requestPinShortcut(source)
		}
	}

	fun setSourcesPinned(sources: Collection<MangaSource>, isPinned: Boolean) {
		launchJob(Dispatchers.Default) {
			sourcesRepository.setIsPinned(sources, isPinned)
			val message = if (sources.size == 1) {
				if (isPinned) R.string.source_pinned else R.string.source_unpinned
			} else {
				if (isPinned) R.string.sources_pinned else R.string.sources_unpinned
			}
			onActionDone.call(ReversibleAction(message, null))
		}
	}

	fun respondSuggestionTip(isAccepted: Boolean) {
		settings.isSuggestionsEnabled = isAccepted
		settings.closeTip(TIP_SUGGESTIONS)
	}

	fun sourcesSnapshot(ids: LongSet): List<MangaSourceInfo> {
		return content.value.mapNotNull {
			(it as? MangaSourceItem)?.takeIf { x -> x.id in ids }?.source
		}
	}

	private fun observeSourcesForDisplay(): Flow<List<MangaSourceInfo>> =
		activePresetFlow.flatMapLatest { preset: SourcePreset? ->
			if (preset != null) {
				flowOf(getPresetSources(preset))
			} else {
				sourcesRepository.observeEnabledSources()
					.map { sources -> sources.filter { it.mangaSource.isVisibleInCurrentUi() } }
			}
		}

	private fun getPresetSources(preset: SourcePreset): List<MangaSourceInfo> {
		if (preset.sources.isEmpty()) return emptyList()
		val skipNsfw = settings.isNsfwContentDisabled
		return sourcesRepository.allMangaSources
			.filter { it.name in preset.sources && it.isVisibleInCurrentUi() && (!skipNsfw || !it.isNsfw()) }
			.map { MangaSourceInfo(it, isEnabled = true, isPinned = false) }
	}

	private fun createContentFlow() = combine(
		observeSourcesForDisplay(),
		getSuggestionFlow(),
		isGrid,
		isRandomLoading,
		isAllSourcesEnabled,
		activePresetFlow,
		selectedBrowseGroup,
	) { sources, suggestions, grid, randomLoading, allSourcesEnabled, activePreset, group ->
		buildList(sources, suggestions, grid, randomLoading, allSourcesEnabled, activePreset, group)
	}.withErrorHandling()

	private fun buildList(
		sources: List<MangaSourceInfo>,
		recommendation: List<Manga>,
		isGrid: Boolean,
		randomLoading: Boolean,
		allSourcesEnabled: Boolean,
		activePreset: SourcePreset?,
		group: BrowseGroup,
	): List<ListModel> {
		val visibleSources = sources.filter { group.matches(it.mangaSource) }
		val visibleRecommendations = recommendation.filter { group.matches(it.source) }
		val result = ArrayList<ListModel>(visibleSources.size + 3)
		result += ExploreButtons(randomLoading, activePreset?.title)
		if (visibleRecommendations.isNotEmpty()) {
			result += ListHeader(R.string.suggestions, R.string.more, R.id.nav_suggestions)
			result += RecommendationsItem(visibleRecommendations.toRecommendationList())
		}
		if (visibleSources.isNotEmpty()) {
			result += ListHeader(
				textRes = R.string.remote_sources,
				buttonTextRes = if (allSourcesEnabled) R.string.manage else R.string.catalog,
			)
			visibleSources.mapTo(result) { MangaSourceItem(it, isGrid) }
		} else {
			result += EmptyHint(
				icon = R.drawable.ic_empty_common,
				textPrimary = R.string.no_manga_sources,
				textSecondary = R.string.no_manga_sources_text,
				actionStringRes = R.string.catalog,
			)
		}
		return result
	}

	private fun getLoadingStateList() = listOf(
		ExploreButtons(isRandomLoading.value),
		LoadingState(),
	)

	private fun getSuggestionFlow() = isSuggestionsEnabled.mapLatest { isEnabled ->
		if (isEnabled) {
			runCatchingCancellable {
				suggestionRepository.getRandomList(SUGGESTIONS_COUNT)
			}.getOrDefault(emptyList())
		} else {
			emptyList()
		}
	}

	private fun List<Manga>.toRecommendationList() = map { manga ->
		MangaCompactListModel(
			manga = manga,
			override = null,
			subtitle = manga.tags.joinToString { it.title },
			counter = 0,
		)
	}

	companion object {

		private const val TIP_SUGGESTIONS = "suggestions"
		private const val SUGGESTIONS_COUNT = 8
	}
}
