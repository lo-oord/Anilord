package org.koitharu.kotatsu.parsers.site.madara.ar

import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koitharu.kotatsu.parsers.model.MangaParserSource

internal class AnyoneMangaTest {

	@Test
	fun parsesTheCurrentHomePageCards() {
		val document = Jsoup.parse(
			"""
			<div id="am-manga-grid">
			  <div class="am-manga-card">
			    <div class="am-manga-card__thumb">
			      <a href="https://anyonemanga.com/manga/infinite-evolution-from-zero/">
			        <img src="https://anyonemanga.com/cover.webp">
			      </a>
			    </div>
			    <h3 class="am-manga-card__title">
			      <a href="https://anyonemanga.com/manga/infinite-evolution-from-zero/">Infinite Evolution From Zero</a>
			    </h3>
			    <div class="am-manga-card__author"><a>Author</a></div>
			    <div class="am-manga-card__rating"><span class="score">4.7</span></div>
			  </div>
			</div>
			""".trimIndent(),
			"https://anyonemanga.com/",
		)

		val result = AnyoneManga.parseAnyoneCatalogue(
			document,
			"anyonemanga.com",
			MangaParserSource.ANYONEMANGA,
		)

		assertEquals(1, result.size)
		assertEquals("Infinite Evolution From Zero", result.single().title)
		assertEquals("/manga/infinite-evolution-from-zero/", result.single().url)
		assertEquals("https://anyonemanga.com/cover.webp", result.single().coverUrl)
		assertEquals(setOf("Author"), result.single().authors)
		assertEquals(0.94f, result.single().rating, 0.001f)
	}

	@Test
	fun resolvesLazyImageWhenSrcIsAPlaceholder() {
		val image = Jsoup.parse(
			"""<img src="data:image/gif;base64,AA" data-src="/uploads/chapter/page-1.webp">""",
			"https://anyonemanga.com/manga/title/1/",
		).selectFirst("img")!!

		assertEquals(
			"https://anyonemanga.com/uploads/chapter/page-1.webp",
			AnyoneManga.resolveAnyoneImageUrl(image, "https://anyonemanga.com/manga/title/1/"),
		)
	}

	@Test
	fun extractsImagesFromTheCurrentCustomReaderWrapper() {
		val document = Jsoup.parse(
			"""
			<div class="am-reading-wrap">
			  <div class="am-reading-content">
			    <img src="data:image/gif;base64,AA" data-src="/chapter/page-1.webp">
			    <img srcset="/chapter/page-2.webp 1200w, /chapter/page-2-small.webp 600w">
			  </div>
			</div>
			""".trimIndent(),
			"https://anyonemanga.com/manga/title/chapter/",
		)

		assertEquals(
			listOf(
				"https://anyonemanga.com/chapter/page-1.webp",
				"https://anyonemanga.com/chapter/page-2.webp",
			),
			AnyoneManga.extractAnyoneChapterImageUrls(
				document,
				"https://anyonemanga.com/manga/title/chapter/",
			),
		)
	}
}
