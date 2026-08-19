package anilord.app.anime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import anilord.app.core.model.MangaSource
import anilord.app.core.model.SourceContentType
import anilord.app.core.model.isAnimeSource
import anilord.app.core.model.sourceContentType
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
