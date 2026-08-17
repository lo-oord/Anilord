package org.manga.peak.reader.domain

import android.util.LongSparseArray
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.manga.peak.core.model.LocalMangaSource
import org.manga.peak.core.parser.MangaRepository
import org.manga.peak.details.data.MangaDetails
import org.manga.peak.reader.ui.novel.NovelContent
import org.koitharu.kotatsu.parsers.model.MangaChapter
import javax.inject.Inject

@ViewModelScoped
class NovelChaptersLoader @Inject constructor(
	private val mangaRepositoryFactory: MangaRepository.Factory,
) {

	private val chapters = LongSparseArray<MangaChapter>()
	private val mutex = Mutex()
	private var currentContent: NovelContent? = null

	suspend fun init(manga: MangaDetails) = mutex.withLock {
		chapters.clear()
		manga.allChapters.forEach {
			chapters.put(it.id, it)
		}
	}

	suspend fun loadChapter(chapterId: Long): NovelContent = mutex.withLock {
		val chapter = checkNotNull(chapters[chapterId]) { "Requested chapter not found" }
		val repo = mangaRepositoryFactory.create(chapter.source)
		val novelChapterContent = repo.getNovelContent(chapter)
		checkNotNull(novelChapterContent) {
			"Novel content not available for this source"
		}
		val content = NovelContent(
			// Parser HTML is the source of truth. Normalizing it here used to remove
			// valid attributes and corrupt otherwise readable novel chapters.
			html = novelChapterContent.html,
			chapterId = chapterId,
			chapterTitle = chapter.title,
			baseUrl = chapter.url.takeUnless { chapter.source == LocalMangaSource },
			images = novelChapterContent.images,
		)
		currentContent = content
		content
	}

	fun peekChapter(chapterId: Long): MangaChapter? = chapters[chapterId]

	fun getCurrentContent(): NovelContent? = currentContent
}
