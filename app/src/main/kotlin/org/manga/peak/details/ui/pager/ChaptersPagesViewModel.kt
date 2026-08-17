package org.manga.peak.details.ui.pager

import android.app.Activity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.plus
import okio.FileNotFoundException
import org.manga.peak.bookmarks.domain.BookmarksRepository
import org.manga.peak.core.model.toChipModel
import org.manga.peak.core.prefs.AppSettings
import org.manga.peak.core.prefs.observeAsStateFlow
import org.manga.peak.core.ui.BaseViewModel
import org.manga.peak.core.ui.util.ReversibleAction
import org.manga.peak.core.util.LocaleStringComparator
import org.manga.peak.core.util.ext.MutableEventFlow
import org.manga.peak.core.util.ext.call
import org.manga.peak.core.util.ext.combine
import org.manga.peak.core.util.ext.requireValue
import org.manga.peak.core.util.ext.sortedWithSafe
import org.manga.peak.details.data.MangaDetails
import org.manga.peak.details.domain.DetailsInteractor
import org.manga.peak.details.ui.DetailsActivity
import org.manga.peak.details.ui.DetailsViewModel
import org.manga.peak.details.ui.mapChapters
import org.manga.peak.details.ui.model.ChapterListItem
import org.manga.peak.download.ui.worker.DownloadTask
import org.manga.peak.download.ui.worker.DownloadWorker
import org.manga.peak.history.data.HistoryRepository
import org.manga.peak.list.domain.ListFilterOption
import org.manga.peak.local.domain.DeleteLocalMangaUseCase
import org.manga.peak.local.domain.model.LocalManga
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.manga.peak.reader.ui.ReaderActivity
import org.manga.peak.reader.ui.ReaderState
import org.manga.peak.reader.ui.ReaderViewModel

abstract class ChaptersPagesViewModel(
	@JvmField protected val settings: AppSettings,
	@JvmField protected val interactor: DetailsInteractor,
	private val bookmarksRepository: BookmarksRepository,
	private val historyRepository: HistoryRepository,
	private val downloadScheduler: DownloadWorker.Scheduler,
	private val deleteLocalMangaUseCase: DeleteLocalMangaUseCase,
	private val localStorageChanges: SharedFlow<LocalManga?>,
) : BaseViewModel() {

	val mangaDetails = MutableStateFlow<MangaDetails?>(null)
	val readingState = MutableStateFlow<ReaderState?>(null)
	protected val readChaptersCount = MutableStateFlow(0)

	val onActionDone = MutableEventFlow<ReversibleAction>()
	val onDownloadStarted = MutableEventFlow<Unit>()
	val onMangaRemoved = MutableEventFlow<Manga>()

	private val chaptersQuery = MutableStateFlow("")
	val selectedBranch = MutableStateFlow<String?>(null)

	val manga = mangaDetails.map { x -> x?.toManga() }
		.withErrorHandling()
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, null)

	val coverUrl = mangaDetails.map { x -> x?.coverUrl }
		.withErrorHandling()
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, null)

	val isChaptersReversed = settings.observeAsStateFlow(
		scope = viewModelScope + Dispatchers.Default,
		key = AppSettings.KEY_REVERSE_CHAPTERS,
		valueProducer = { isChaptersReverse },
	)

	val isChaptersInGridView = settings.observeAsStateFlow(
		scope = viewModelScope + Dispatchers.Default,
		key = AppSettings.KEY_GRID_VIEW_CHAPTERS,
		valueProducer = { isChaptersGridView },
	)

	val isDownloadedOnly = MutableStateFlow(false)

	val newChaptersCount = mangaDetails.flatMapLatest { d ->
		if (d?.isLocal == false) {
			interactor.observeNewChapters(d.id)
		} else {
			flowOf(0)
		}
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, 0)

	val emptyReason: StateFlow<EmptyMangaReason?> = combine(
		mangaDetails,
		isLoading,
		onError.onStart { emit(null) },
	) { details, loading, error ->
		when {
			details == null || loading -> null
			details.chapters.isNotEmpty() -> null
			details.toManga().state == MangaState.RESTRICTED -> EmptyMangaReason.RESTRICTED
			error != null -> EmptyMangaReason.LOADING_ERROR
			else -> EmptyMangaReason.NO_CHAPTERS
		}
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.WhileSubscribed(), null)

	val bookmarks = mangaDetails.flatMapLatest {
		if (it != null) {
			bookmarksRepository.observeBookmarks(it.toManga()).withErrorHandling()
		} else {
			flowOf(emptyList())
		}
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Lazily, emptyList())

	val chapters = combine(
		combine(
			mangaDetails,
			combine(
				readingState.map { it?.chapterId ?: 0L }.distinctUntilChanged(),
				readChaptersCount,
			) { chapterId, readCount -> chapterId to readCount },
			selectedBranch,
			newChaptersCount,
			bookmarks,
			isChaptersInGridView,
			isDownloadedOnly,
		) { manga, reading, branch, news, bookmarks, grid, downloadedOnly ->
			manga?.mapChapters(
				currentChapterId = reading.first,
				readChaptersCount = reading.second,
				newCount = news,
				branch = branch,
				bookmarks = bookmarks,
				isGrid = grid,
				isDownloadedOnly = downloadedOnly,
			).orEmpty()
		},
		isChaptersReversed,
		chaptersQuery,
	) { list, reversed, query ->
		(if (reversed) list.asReversed() else list).filterSearch(query)
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, emptyList())

	val quickFilter = combine(
		mangaDetails,
		selectedBranch,
	) { details, branch ->
		val branches = details?.chapters?.toList()?.sortedWithSafe(
			compareBy(LocaleStringComparator()) { it.first },
		).orEmpty()
		if (branches.size > 1) {
			branches.map {
				val option = ListFilterOption.Branch(titleText = it.first, chaptersCount = it.second.size)
				option.toChipModel(isChecked = it.first == branch)
			}
		} else {
			emptyList()
		}
	}

	init {
		launchJob(Dispatchers.Default) {
			localStorageChanges
				.collect { onDownloadComplete(it) }
		}
	}

	fun setChaptersReversed(newValue: Boolean) {
		settings.isChaptersReverse = newValue
	}

	fun setChaptersInGridView(newValue: Boolean) {
		settings.isChaptersGridView = newValue
	}

	fun setSelectedBranch(branch: String?) {
		selectedBranch.value = branch
	}

	fun performChapterSearch(query: String?) {
		chaptersQuery.value = query?.trim().orEmpty()
	}

	val isNovelContent: Boolean
		get() {
			val source = mangaDetails.value?.toManga()?.source
			return source is MangaParserSource && source.contentType == ContentType.NOVEL
		}

	fun getMangaOrNull(): Manga? = mangaDetails.value?.toManga()

	fun requireManga() = mangaDetails.requireValue().toManga()

	fun markChapterAsCurrent(chapterId: Long) {
		launchJob(Dispatchers.Default) {
			val manga = mangaDetails.requireValue()
			val chapters = checkNotNull(manga.chapters[selectedBranch.value])
			val chapterIndex = chapters.indexOfFirst { it.id == chapterId }
			check(chapterIndex in chapters.indices) { "Chapter not found" }
			val percent = chapterIndex / chapters.size.toFloat()
			historyRepository.addOrUpdate(
				manga = manga.toManga(),
				chapterId = chapterId,
				page = 0,
				scroll = 0,
				percent = percent,
				force = true,
			)
		}
	}

	fun download(chaptersIds: Set<Long>?, allowMeteredNetwork: Boolean) {
		launchJob(Dispatchers.Default) {
			val manga = requireManga()
			val task = DownloadTask(
				mangaId = manga.id,
				isPaused = false,
				isSilent = false,
				chaptersIds = chaptersIds?.toLongArray(),
				destination = null,
				format = null,
				allowMeteredNetwork = allowMeteredNetwork,
			)
			downloadScheduler.schedule(setOf(manga to task))
			onDownloadStarted.call(Unit)
		}
	}

	fun deleteLocal() {
		val m = mangaDetails.value?.local?.manga
		if (m == null) {
			errorEvent.call(FileNotFoundException())
			return
		}
		launchLoadingJob(Dispatchers.Default) {
			deleteLocalMangaUseCase(m)
			onMangaRemoved.call(m)
		}
	}

	private fun List<ChapterListItem>.filterSearch(query: String): List<ChapterListItem> {
		if (query.isEmpty() || this.isEmpty()) {
			return this
		}
		return filter { it.contains(query) }
	}

	private suspend fun onDownloadComplete(downloadedManga: LocalManga?) {
		downloadedManga ?: return
		mangaDetails.update {
			interactor.updateLocal(it, downloadedManga)
		}
	}

	class ActivityVMLazy(
		private val fragment: Fragment,
	) : Lazy<ChaptersPagesViewModel> {
		private var cached: ChaptersPagesViewModel? = null

		override val value: ChaptersPagesViewModel
			get() {
				val viewModel = cached
				return if (viewModel == null) {
					val activity = fragment.requireActivity()
					val vmClass = getViewModelClass(activity)
					ViewModelProvider.create(
						store = activity.viewModelStore,
						factory = activity.defaultViewModelProviderFactory,
						extras = activity.defaultViewModelCreationExtras,
					)[vmClass].also { cached = it }
				} else {
					viewModel
				}
			}

		override fun isInitialized(): Boolean = cached != null

		private fun getViewModelClass(activity: Activity) = when (activity) {
			is ReaderActivity -> ReaderViewModel::class.java
			is DetailsActivity -> DetailsViewModel::class.java
			else -> error("Wrong activity ${activity.javaClass.simpleName} for ${ChaptersPagesViewModel::class.java.simpleName}")
		}
	}
}
