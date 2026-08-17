package org.manga.peak.reader.ui.novel

import org.koitharu.kotatsu.parsers.model.NovelImage
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.Locale

data class NovelContent(
	val html: String,
	val chapterId: Long,
	val chapterTitle: String?,
	val baseUrl: String?,
	val images: List<NovelImage>,
)

/**
 * Repairs only transport/lazy-loading artefacts that WebView cannot interpret.
 * Valid parser HTML and all of its formatting attributes are left untouched.
 */
internal fun normalizeNovelMarkupForDisplay(rawHtml: String): String = runCatching {
	var html = rawHtml
		.replace(Regex("(?i)\\\\u003c"), "<")
		.replace(Regex("(?i)\\\\u003e"), ">")
		.replace(Regex("(?i)\\\\u0026"), "&")
		.replace("\\/", "/")

	// Decode only when the value clearly contains encoded HTML tags. Normal
	// entities in prose (&lt;, &gt;) must stay text.
	repeat(2) {
		if (ENCODED_NOVEL_TAG.containsMatchIn(html)) {
			html = html
				.replace("&amp;lt;", "&lt;", ignoreCase = true)
				.replace("&amp;gt;", "&gt;", ignoreCase = true)
				.replace("&lt;", "<", ignoreCase = true)
				.replace("&gt;", ">", ignoreCase = true)
		}
	}

	// A few APIs return tags as <>p<>. Repair those exact wrappers without
	// rewriting ordinary, already valid tags.
	html = BROKEN_NOVEL_TAG.replace(html) { match ->
		"<${match.groupValues[1].trim()}>"
	}
	// Remove a stray transport bracket only at a line boundary. This fixes
	// chapters whose rows start with `><p` while preserving valid `</div><p>`.
	html = STRAY_ROW_PREFIX.replace(html, "$1")

	// WebView has JavaScript disabled, so promote lazy image URLs to src. Keep
	// every other attribute intact because parsers may rely on it for layout.
	IMG_TAG.replace(html) { match -> promoteLazyImage(match.value) }
}.getOrElse { rawHtml }

/**
 * Keeps chapter prose and illustrations while removing web-page chrome that
 * must never become part of an in-app novel chapter (payment forms, ads,
 * scripts, navigation, and interactive controls).
 */
internal fun sanitizeNovelHtmlForReader(rawHtml: String): String = runCatching {
	val body = Jsoup.parseBodyFragment(rawHtml).body()

	// Remove a compact payment/support section as one unit. Requiring both a
	// payment marker and an interactive/payment element avoids deleting normal
	// prose that merely mentions support or money.
	body.select("form, div, section, aside, article, table, figure").toList().forEach { element ->
		val signature = element.readerSignature()
		val hasPaymentMarker = PAYMENT_MARKERS.any(signature::contains)
		val hasPaymentMechanism = element.tagName() == "form" ||
			element.select("form, input, select, option, textarea, button").isNotEmpty() ||
			element.select("a[href], img[src], img[alt]").any { child ->
				PAYMENT_URL_MARKERS.any(child.readerSignature()::contains)
			}
		if (hasPaymentMarker && hasPaymentMechanism && element.text().length <= 1200) {
			element.remove()
		}
	}

	body.select(
		"script, style, link, meta, title, noscript, iframe, object, embed, " +
			"form, input, select, option, textarea, button, label, nav, canvas, svg",
	).remove()

	// Remove standalone payment buttons/logos left outside a wrapper, but do not
	// touch ordinary chapter illustrations.
	body.select("a[href], img[src], img[alt]").toList().forEach { element ->
		val signature = element.readerSignature()
		if (PAYMENT_URL_MARKERS.any(signature::contains)) {
			val wrapper = element.parents().firstOrNull { parent ->
				parent.tagName() in PAYMENT_WRAPPER_TAGS &&
					parent.text().length <= 600 &&
					parent !== body
			}
			(wrapper ?: element).remove()
		}
	}

	body.allElements.forEach { element ->
		element.attributes().asList()
			.filter { it.key.startsWith("on", ignoreCase = true) }
			.forEach { element.removeAttr(it.key) }
		element.removeAttr("face")
			.removeAttr("contenteditable")
		if (element.tagName() !in setOf("img", "image")) {
			element.removeAttr("width")
				.removeAttr("height")
		}
		val style = element.attr("style").takeIf(String::isNotBlank) ?: return@forEach
		val cleanStyle = style.split(';')
			.map(String::trim)
			.filter(String::isNotEmpty)
			.filterNot { declaration ->
				val property = declaration.substringBefore(':').trim().lowercase(Locale.ROOT)
				property in READER_CONTROLLED_STYLE_PROPERTIES
			}
			.joinToString("; ")
		if (cleanStyle.isBlank()) {
			element.removeAttr("style")
		} else {
			element.attr("style", cleanStyle)
		}
	}
	body.html()
}.getOrElse { rawHtml }

private fun Element.readerSignature(): String = buildString {
	append(className())
	append(' ')
	append(id())
	append(' ')
	append(text())
	select("[href], [src], [alt], [title]").forEach { child ->
		append(' ')
		append(child.attr("href"))
		append(' ')
		append(child.attr("src"))
		append(' ')
		append(child.attr("alt"))
		append(' ')
		append(child.attr("title"))
	}
}.lowercase(Locale.ROOT)

private fun promoteLazyImage(tag: String): String {
	val current = findNovelAttribute(tag, "src")
	val lazy = LAZY_IMAGE_ATTRIBUTES
		.asSequence()
		.mapNotNull { findNovelAttribute(tag, it) }
		.firstOrNull(String::isNotBlank)
		?: findNovelAttribute(tag, "srcset")
			?.substringBefore(',')
			?.trim()
			?.substringBefore(' ')
	val shouldReplace = current.isNullOrBlank() || current.startsWith("data:image/gif", true) ||
		current.contains("placeholder", true) || current.contains("loading", true)
	val source = lazy?.takeIf { shouldReplace && it.isNotBlank() } ?: return tag
	val safeSource = source.replace("\"", "&quot;")
	return if (current == null) {
		tag.replaceFirst(Regex("(?i)<img\\b"), "<img src=\"$safeSource\"")
	} else {
		tag.replaceFirst(
			Regex("(?is)(\\bsrc\\s*=\\s*)(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)"),
			"$1\"$safeSource\"",
		)
	}
}

private fun findNovelAttribute(tag: String, name: String): String? =
	Regex("(?is)\\b${Regex.escape(name)}\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")
		.find(tag)
		?.groupValues
		?.drop(1)
		?.firstOrNull(String::isNotEmpty)

private val ENCODED_NOVEL_TAG = Regex(
	"(?i)(?:&amp;lt;|&lt;)(?:(?:&amp;gt;|&gt;))?\\s*/?\\s*(?:p|div|span|br|h[1-6]|section|article|blockquote|ul|ol|li|strong|em|img|table|body)\\b",
)
private val BROKEN_NOVEL_TAG = Regex(
	"(?is)<>\\s*(/?\\s*(?:p|div|span|br|h[1-6]|section|article|blockquote|ul|ol|li|b|strong|i|em|img|a|table|tbody|thead|tfoot|tr|td|th|body)\\b[^<>]{0,2000})\\s*<>",
)
private val STRAY_ROW_PREFIX = Regex(
	"(?im)(^|[\\r\\n]+)[ \\t]*>[ \\t]*(?=</?(?:p|div|span|h[1-6]|section|article|blockquote|li|img)\\b)",
)
private val IMG_TAG = Regex("(?is)<img\\b[^>]*>")
private val LAZY_IMAGE_ATTRIBUTES = arrayOf("data-src", "data-original", "data-lazy-src", "data-url")
private val PAYMENT_MARKERS = listOf(
	"paypal",
	"patreon",
	"ko-fi",
	"kofi",
	"buymeacoffee",
	"mastercard",
	"pay now",
	"donat",
	"support me",
	"support us",
	"support the novel",
	"vip",
	"ادعم الرواية",
	"دعم الرواية",
	"أنواع تبرعات",
	"انواع تبرعات",
	"اشترك في العضوية",
	"إشترك في العضوية",
	"دعمك للموقع",
	"تبرعات",
)
private val PAYMENT_URL_MARKERS = listOf(
	"paypal",
	"patreon",
	"ko-fi",
	"kofi",
	"buymeacoffee",
	"mastercard",
	"visa",
	"pay-now",
	"donat",
	"support",
)
private val PAYMENT_WRAPPER_TAGS = setOf("a", "p", "div", "section", "aside", "figure", "td")
private val READER_CONTROLLED_STYLE_PROPERTIES = setOf(
	"float",
	"position",
	"width",
	"height",
	"min-width",
	"max-width",
	"min-height",
	"max-height",
	"font",
	"font-family",
	"font-size",
	"line-height",
)
