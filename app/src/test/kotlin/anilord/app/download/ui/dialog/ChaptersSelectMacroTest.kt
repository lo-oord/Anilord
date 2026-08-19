package anilord.app.download.ui.dialog

import org.junit.Assert.assertEquals
import org.junit.Test
import anilord.app.core.model.TestMangaSource
import org.koitharu.kotatsu.parsers.model.MangaChapter

class ChaptersSelectMacroTest {

	@Test
	fun selectsRequestedChaptersAfterLatestDownload() {
		val chapters = (1L..210L).map(::chapter)
		val macro = ChaptersSelectMacro.NotDownloadedChapters(
			chaptersCount = 5,
			maxAvailableCount = 10,
			downloadedChapterIds = mapOf(MANGA_ID to (1L..200L).toSet()),
			preferredBranches = mapOf(MANGA_ID to null),
		)

		assertEquals(
			setOf(201L, 202L, 203L, 204L, 205L),
			macro.getChaptersIds(MANGA_ID, chapters),
		)
	}

	@Test
	fun skipsAlreadyDownloadedChaptersAfterBoundary() {
		val chapters = (1L..8L).map(::chapter)
		val macro = ChaptersSelectMacro.NotDownloadedChapters(
			chaptersCount = 3,
			maxAvailableCount = 3,
			downloadedChapterIds = mapOf(MANGA_ID to setOf(1L, 2L, 3L, 5L)),
			preferredBranches = mapOf(MANGA_ID to null),
		)

		assertEquals(
			setOf(6L, 7L, 8L),
			macro.getChaptersIds(MANGA_ID, chapters),
		)
	}

	private fun chapter(id: Long) = MangaChapter(
		id = id,
		title = null,
		number = id.toFloat(),
		volume = 0,
		url = "/chapter/$id",
		scanlator = null,
		uploadDate = 0L,
		branch = null,
		source = TestMangaSource,
	)

	private companion object {
		const val MANGA_ID = 42L
	}
}
