package org.manga.peak.reader.ui.novel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelMarkupNormalizationTest {

	@Test
	fun `preserves valid parser html and formatting`() {
		val input = """<p class="chapter" style="color:red"><strong>Text</strong></p>"""
		assertEquals(input, normalizeNovelMarkupForDisplay(input))
	}

	@Test
	fun `repairs encoded broken paragraphs`() {
		val result = normalizeNovelMarkupForDisplay(
			"&lt;&gt;p&lt;&gt;First&lt;&gt;/p&lt;&gt;",
		)
		assertEquals("<p>First</p>", result)
	}

	@Test
	fun `removes stray bracket before a row tag`() {
		val result = normalizeNovelMarkupForDisplay("><p>First</p>\n  ><p>Second</p>")
		assertEquals("<p>First</p>\n<p>Second</p>", result)
	}

	@Test
	fun `does not change valid adjacent tags`() {
		val input = "<div>First</div><p>Second</p>"
		assertEquals(input, normalizeNovelMarkupForDisplay(input))
	}

	@Test
	fun `promotes lazy image but preserves its attributes`() {
		val result = normalizeNovelMarkupForDisplay(
			"""<img class="illustration lazy" src="placeholder.gif" data-src="/chapter/1.webp" alt="Art">""",
		)
		assertTrue(result.contains("src=\"/chapter/1.webp\""))
		assertTrue(result.contains("class=\"illustration lazy\""))
		assertTrue(result.contains("data-src=\"/chapter/1.webp\""))
		assertTrue(result.contains("alt=\"Art\""))
		assertFalse(result.contains("src=\"placeholder.gif\""))
	}

	@Test
	fun `keeps prose entities as text`() {
		val input = "<p>1 &lt; 2 and 3 &gt; 2</p>"
		assertEquals(input, normalizeNovelMarkupForDisplay(input))
	}

	@Test
	fun `removes support forms and payment artwork but keeps chapter`() {
		val result = sanitizeNovelHtmlForReader(
			"""
			<section class="support-box">
				<h2>ادعم الرواية</h2>
				<form><select><option>USD 3.00</option></select><input type="email"></form>
				<img src="/assets/paypal-mastercard.png" alt="PayPal Visa">
			</section>
			<p id="chapter-text">هذا هو النص الحقيقي للفصل.</p>
			<img src="/chapters/illustration.webp" alt="Chapter illustration">
			""".trimIndent(),
		)
		assertFalse(result.contains("ادعم الرواية"))
		assertFalse(result.contains("paypal", ignoreCase = true))
		assertFalse(result.contains("<form", ignoreCase = true))
		assertTrue(result.contains("هذا هو النص الحقيقي للفصل"))
		assertTrue(result.contains("/chapters/illustration.webp"))
	}

	@Test
	fun `reader controls font size and spacing while useful formatting survives`() {
		val result = sanitizeNovelHtmlForReader(
			"""<p face="Tahoma" style="font-family: Tahoma; font-size: 30px; line-height: 4; color: red; font-weight: bold">Text</p>""",
		)
		assertFalse(result.contains("font-family", ignoreCase = true))
		assertFalse(result.contains("font-size", ignoreCase = true))
		assertFalse(result.contains("line-height", ignoreCase = true))
		assertFalse(result.contains("face=", ignoreCase = true))
		assertTrue(result.contains("color: red"))
		assertTrue(result.contains("font-weight: bold"))
		assertTrue(result.contains(">Text</p>"))
	}
}
