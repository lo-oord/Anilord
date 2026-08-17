package org.koitharu.kotatsu.parsers.site.madara.ar

import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class HizoMangaTest {

	@Test
	fun recognizesTheCurrentSeriesListingCards() {
		val document = Jsoup.parse(
			"""
			<div class="page-item-detail">
			  <div class="item-thumb"><a href="/serie/a/"><img src="/a.webp"></a></div>
			  <div class="item-summary"><div class="post-title"><h3><a href="/serie/a/">رواية أ</a></h3></div></div>
			</div>
			<div class="page-item-detail">
			  <div class="item-thumb"><a href="/serie/b/"><img src="/b.webp"></a></div>
			  <div class="item-summary"><div class="post-title"><h3><a href="/serie/b/">رواية ب</a></h3></div></div>
			</div>
			""".trimIndent(),
		)

		assertEquals(2, HizoManga.countCurrentListingCards(document))
	}

	@Test
	fun keepsNovelTextAndPromotesLazyImages() {
		val document = Jsoup.parse(
			"""
			<div class="text-left">
			  <p style="font-family:Arial">نص الفصل العربي</p>
			  <div class="advert-box">إعلان</div>
			  <img src="#" data-lazy-src="https://hizomanga.net/chapter/image.webp">
			</div>
			""".trimIndent(),
		)
		val content = document.selectFirst(".text-left")!!

		HizoManga.sanitizeHizoChapterContent(content)

		assertTrue(content.text().contains("نص الفصل العربي"))
		assertFalse(content.text().contains("إعلان"))
		assertFalse(content.html().contains("font-family"))
		assertEquals(
			"https://hizomanga.net/chapter/image.webp",
			content.selectFirst("img")?.attr("src"),
		)
	}

	@Test
	fun extractsImagesFromMangaChaptersUsingTheSameReader() {
		val document = Jsoup.parse(
			"""
			<div class="read-container">
			  <div class="reading-content current">
			    <div class="page-break">
			      <img src="#" data-lazy-src="/uploads/chapter-1.webp">
			    </div>
			  </div>
			</div>
			""".trimIndent(),
			"https://hizomanga.net/serie/title/1/",
		)

		assertEquals(
			listOf("https://hizomanga.net/uploads/chapter-1.webp"),
			HizoManga.extractHizoImageUrls(
				document,
				"https://hizomanga.net/serie/title/1/",
			),
		)
	}
}
