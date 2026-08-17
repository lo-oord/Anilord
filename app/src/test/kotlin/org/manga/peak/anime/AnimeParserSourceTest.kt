package org.manga.peak.anime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.manga.peak.core.model.MangaSource
import org.manga.peak.core.model.SourceContentType
import org.manga.peak.core.model.isAnimeSource
import org.manga.peak.core.model.sourceContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource

class AnimeParserSourceTest {

	@Test
	fun `anime source is resolved from parser enum`() {
		val source = MangaParserSource.ANIME_RISTO
		assertSame(source, MangaSource(source.name))
		assertEquals(SourceContentType.ANIME, source.sourceContentType)
		assertEquals(true, source.isAnimeSource())
	}
}
