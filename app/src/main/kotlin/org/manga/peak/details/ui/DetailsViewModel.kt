package org.manga.peak.details.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import org.manga.peak.R
import org.manga.peak.bookmarks.domain.BookmarksRepository
import org.manga.peak.core.model.getPreferredBranch
import org.manga.peak.core.model.isNsfw
import org.manga.peak.core.nav.MangaIntent
import org.manga.peak.core.prefs.AppSettings
import org.manga.peak.core.prefs.ListMode
import org.manga.peak.core.prefs.TriStateOption
import org.manga.peak.core.ui.util.ReversibleAction
import org.manga.peak.core.util.ext.call
import org.manga.peak.core.util.ext.computeSize
import org.manga.peak.core.util.ext.onEachWhile
import org.manga.peak.details.data.MangaDetails
import org.manga.peak.details.domain.BranchComparator
import org.manga.peak.details.domain.DetailsInteractor
import org.manga.peak.details.domain.DetailsLoadUseCase
import org.manga.peak.details.domain.ProgressUpdateUseCase
import org.manga.peak.details.domain.ReadingTimeUseCase
import org.manga.peak.details.domain.RelatedMangaUseCase
import org.manga.peak.details.ui.model.HistoryInfo
import org.manga.peak.details.ui.model.MangaBranch
import org.manga.peak.details.ui.pager.ChaptersPagesViewModel
import org.manga.peak.download.ui.worker.DownloadWorker
import org.manga.peak.history.data.HistoryRepository
import org.manga.peak.list.domain.MangaListMapper
import org.manga.peak.list.domain.ReadingProgress
import org.manga.peak.list.ui.model.MangaListModel
import org.manga.peak.local.data.LocalStorageChanges
import org.manga.peak.local.domain.DeleteLocalMangaUseCase
import org.manga.peak.local.domain.model.LocalManga
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.util.findById
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.manga.peak.reader.ui.ReaderState
import org.manga.peak.scrobbling.common.domain.Scrobbler
import org.manga.peak.scrobbling.common.domain.model.ScrobblingInfo
import org.manga.peak.scrobbling.common.domain.model.ScrobblingStatus
import org.manga.peak.stats.data.StatsRepository
import javax.inject.Inject

@HiltViewModel
class DetailsViewModel @Inject constructor(
	private val historyRepository: HistoryRepository,
	bookmarksRepository: BookmarksRepository,
	settings: AppSettings,
	private val scrobblers: Set<@JvmSuppressWildcards Scrobbler>,
	@LocalStorageChanges localStorageChanges: SharedFlow<LocalManga?>,
	downloadScheduler: DownloadWorker.Scheduler,
	interactor: DetailsInteractor,
	savedStateHandle: SavedStateHandle,
	deleteLocalMangaUseCase: DeleteLocalMangaUseCase,
	private val relatedMangaUseCase: RelatedMangaUseCase,
	private val mangaListMapper: MangaListMapper,
	private val detailsLoadUseCase: DetailsLoadUseCase,
	private val progressUpdateUseCase: ProgressUpdateUseCase,
	private val readingTimeUseCase: ReadingTimeUseCase,
	statsRepository: StatsRepository,
) : ChaptersPagesViewModel(
	settings = settings,
	interactor = interactor,
	bookmarksRepository = bookmarksRepository,
	historyRepository = historyRepository,
	downloadScheduler = downloadScheduler,
	deleteLocalMangaUseCase = deleteLocalMangaUseCase,
	localStorageChanges = localStorageChanges,
) {

	private val intent = MangaIntent(savedStateHandle)
	private var loadingJob: Job
	val mangaId = intent.mangaId

	init {
		mangaDetails.value = intent.manga?.let { MangaDetails(it) }
	}

	val history = historyRepository.observeOne(mangaId)
		.onEach { h ->
			readingState.value = h?.let(::ReaderState)
			readChaptersCount.value = if (
				h != null && h.chaptersCount > 0 && ReadingProgress.isValid(h.percent)
			) {
				(h.percent * h.chaptersCount).toInt().coerceIn(0, h.chaptersCount)
			} else {
				0
			}
		}.withErrorHandling()
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, null)

	val favouriteCategories = interactor.observeFavourite(mangaId)
		.withErrorHandling()
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, emptySet())

	val isStatsAvailable = statsRepository.observeHasStats(mangaId)
		.withErrorHandling()
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, false)

	val remoteManga = MutableStateFlow<Manga?>(null)

	val historyInfo: StateFlow<HistoryInfo> = combine(
		mangaDetails,
		selectedBranch,
		history,
		interactor.observeIncognitoMode(manga),
	) { m, b, h, im ->
		val estimatedTime = readingTimeUseCase.invoke(m, b, h)
		HistoryInfo(m, b, h, im == TriStateOption.ENABLED, estimatedTime)
	}.withErrorHandling()
		.stateIn(
			scope = viewModelScope + Dispatchers.Default,
			started = SharingStarted.Eagerly,
			initialValue = HistoryInfo(null, null, null, false, null),
		)

	val localSize = mangaDetails
		.map { it?.local }
		.distinctUntilChanged()
		.combine(localStorageChanges.onStart { emit(null) }) { x, _ -> x }
		.map { local ->
			if (local != null) {
				runCatchingCancellable {
					local.file.computeSize()
				}.getOrDefault(0L)
			} else {
				0L
			}
		}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.WhileSubscribed(5000), 0L)

	val isScrobblingAvailable: Boolean
		get() = scrobblers.any { it.isEnabled }

	val scrobblingInfo: StateFlow<List<ScrobblingInfo>> = interactor.observeScrobblingInfo(mangaId)
		.withErrorHandling()
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, emptyList())

	val relatedManga: StateFlow<List<MangaListModel>> = manga.mapLatest {
		if (it != null && settings.isRelatedMangaEnabled) {
			val related = relatedMangaUseCase(it).orEmpty().let { list ->
				if (settings.isNsfwContentDisabled) {
					list.filterNot { manga -> manga.isNsfw() }
				} else {
					list
				}
			}
			mangaListMapper.toListModelList(
				manga = related,
				mode = ListMode.GRID,
			)
		} else {
			emptyList()
		}
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Lazily, emptyList())

	val tags = manga.mapLatest {
		mangaListMapper.mapTags(it?.tags.orEmpty())
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, emptyList())

	val branches: StateFlow<List<MangaBranch>> = combine(
		mangaDetails,
		selectedBranch,
		history,
	) { m, b, h ->
		val c = m?.chapters
		if (c.isNullOrEmpty()) {
			return@combine emptyList()
		}
		val currentBranch = h?.let { m.allChapters.findById(it.chapterId) }?.branch
		c.map { x ->
			MangaBranch(
				name = x.key,
				count = x.value.size,
				isSelected = x.key == b,
				isCurrent = h != null && x.key == currentBranch,
			)
		}.sortedWith(BranchComparator())
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, emptyList())

	val selectedBranchValue: String?
		get() = selectedBranch.value

	init {
		loadingJob = doLoad(force = false)
		launchJob(Dispatchers.Default + SkipErrors) {
			val manga = mangaDetails.firstOrNull { !it?.chapters.isNullOrEmpty() } ?: return@launchJob
			val h = history.firstOrNull()
			if (h != null) {
				progressUpdateUseCase(manga.toManga())
			}
		}
		launchJob(Dispatchers.Default) {
			val manga = mangaDetails.firstOrNull { it != null && it.isLocal } ?: return@launchJob
			remoteManga.value = interactor.findRemote(manga.toManga())
		}
	}

	fun reload() {
		loadingJob.cancel()
		loadingJob = doLoad(force = true)
	}

	fun updateScrobbling(index: Int, rating: Float, status: ScrobblingStatus?) {
		val scrobbler = getScrobbler(index) ?: return
		launchJob(Dispatchers.Default) {
			scrobbler.updateScrobblingInfo(
				mangaId = mangaId,
				rating = rating,
				status = status,
				comment = null,
			)
		}
	}

	fun unregisterScrobbling(index: Int) {
		val scrobbler = getScrobbler(index) ?: return
		launchJob(Dispatchers.Default) {
			scrobbler.unregisterScrobbling(
				mangaId = mangaId,
			)
		}
	}

	fun removeFromHistory() {
		launchJob(Dispatchers.Default) {
			val handle = historyRepository.delete(setOf(mangaId))
			onActionDone.call(ReversibleAction(R.string.removed_from_history, handle))
		}
	}

	private fun doLoad(force: Boolean) = launchLoadingJob(Dispatchers.Default) {
		detailsLoadUseCase.invoke(intent, force)
			.onEachWhile {
				if (it.allChapters.isNotEmpty()) {
					val manga = it.toManga()
					// find default branch
					val hist = historyRepository.getOne(manga)
					selectedBranch.value = manga.getPreferredBranch(hist)
					true
				} else {
					false
				}
			}.collect {
				mangaDetails.value = it
			}
	}

	private fun getScrobbler(index: Int): Scrobbler? {
		val info = scrobblingInfo.value.getOrNull(index)
		val scrobbler = if (info != null) {
			scrobblers.find { it.scrobblerService == info.scrobbler && it.isEnabled }
		} else {
			null
		}
		if (scrobbler == null) {
			errorEvent.call(IllegalStateException("Scrobbler [$index] is not available"))
		}
		return scrobbler
	}
}
