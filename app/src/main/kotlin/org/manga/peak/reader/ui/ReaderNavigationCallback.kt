package org.manga.peak.reader.ui

import org.manga.peak.bookmarks.domain.Bookmark
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.manga.peak.reader.ui.pager.ReaderPage

interface ReaderNavigationCallback {

	fun onPageSelected(page: ReaderPage): Boolean

	fun onChapterSelected(chapter: MangaChapter): Boolean

	fun onBookmarkSelected(bookmark: Bookmark): Boolean = onPageSelected(
		ReaderPage(bookmark.toMangaPage(), bookmark.page, bookmark.chapterId),
	)
}
