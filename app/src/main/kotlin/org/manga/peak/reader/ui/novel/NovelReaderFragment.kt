package org.manga.peak.reader.ui.novel

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.text.HtmlCompat
import dagger.hilt.android.AndroidEntryPoint
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import org.manga.peak.core.network.MangaHttpClient
import org.manga.peak.core.prefs.AppSettings
import org.manga.peak.core.util.ext.observe
import org.manga.peak.databinding.FragmentReaderNovelBinding
import org.manga.peak.reader.ui.ReaderState
import org.manga.peak.reader.ui.pager.BaseReaderFragment
import java.io.FilterInputStream
import java.net.URI
import java.net.URLConnection
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class NovelReaderFragment : BaseReaderFragment<FragmentReaderNovelBinding>(),
	SharedPreferences.OnSharedPreferenceChangeListener {

	@Inject
	lateinit var settings: AppSettings

	@Inject
	@MangaHttpClient
	lateinit var okHttpClient: OkHttpClient

	private val mainHandler = Handler(Looper.getMainLooper())
	private val javaScriptBridge = NovelReaderBridge()
	private var isContentLoaded = false
	private var renderedChapterIds: List<Long> = emptyList()
	@Volatile
	private var pendingState: ReaderState? = null
	@Volatile
	private var isRestoringPosition = false
	private var restoreGeneration = 0
	private var pageLoadGeneration = 0
	private var restoredPageLoadGeneration = -1
	@Volatile
	private var currentChapterId: Long? = null
	@Volatile
	private var currentChapterScroll: Int = 0
	@Volatile
	private var novelImageHeaders: Map<String, Map<String, String>> = emptyMap()
	@Volatile
	private var novelBaseUrl: String? = null

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	) = FragmentReaderNovelBinding.inflate(inflater, container, false)

	@SuppressLint("SetJavaScriptEnabled")
	override fun onViewBindingCreated(binding: FragmentReaderNovelBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		setupWebView(binding.webViewNovel)

		viewModel.novelContents.observe(viewLifecycleOwner) { contents ->
			if (contents.isEmpty()) {
				renderedChapterIds = emptyList()
				return@observe
			}
			val chapterIds = contents.map(NovelContent::chapterId)
			val renderedCount = renderedChapterIds.size
			val isAppend = isContentLoaded &&
				renderedChapterIds.isNotEmpty() &&
				chapterIds.size > renderedCount &&
				chapterIds.take(renderedCount) == renderedChapterIds
			Log.i(LOG_TAG, "Render contents=$chapterIds, previous=$renderedChapterIds, append=$isAppend")
			if (isAppend) {
				// Reloading the whole document resets images to zero height.
				// Their subsequent reflow can push the reader several chapters
				// backwards, so continuous reading appends only the new DOM.
				appendNovelHtml(
					allContents = contents,
					appendedContents = contents.drop(renderedCount),
				)
			} else {
				val state = viewModel.getCurrentState()
					?.takeIf { candidate -> chapterIds.contains(candidate.chapterId) }
					?: ReaderState(chapterIds.first(), 0, 0)
				pendingState = state
				isRestoringPosition = true
				currentChapterId = state.chapterId
				currentChapterScroll = state.scroll
				loadNovelHtml(contents)
			}
			renderedChapterIds = chapterIds
		}
		settings.subscribe(this)
	}

	@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
	private fun setupWebView(webView: WebView) {
		webView.settings.apply {
			// Only the reader-owned bridge script runs. Parser scripts, event
			// handlers, frames and interactive controls are removed before the
			// chapter markup reaches this WebView.
			javaScriptEnabled = true
			javaScriptCanOpenWindowsAutomatically = false
			setSupportMultipleWindows(false)
			loadWithOverviewMode = true
			useWideViewPort = false
			builtInZoomControls = false
			displayZoomControls = false
			cacheMode = WebSettings.LOAD_DEFAULT
			allowFileAccess = false
			allowContentAccess = false
			domStorageEnabled = false
			loadsImagesAutomatically = true
			blockNetworkImage = false
			mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
			setSupportZoom(false)
		}
		webView.addJavascriptInterface(javaScriptBridge, NOVEL_BRIDGE_NAME)
		webView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
			if (isRestoringPosition || !settings.isNovelContinuousChapters) return@setOnScrollChangeListener
			val contentHeight = (webView.contentHeight * webView.scale).toInt()
			val remaining = contentHeight - scrollY - webView.height
			if (remaining <= webView.height * NEXT_CHAPTER_PREFETCH_SCREENS) {
				renderedChapterIds.lastOrNull()?.let(::requestNextNovelChapter)
			}
		}
		webView.setBackgroundColor(NovelReaderSettings.from(settings).backgroundColor)
		webView.webViewClient = object : WebViewClient() {
			override fun shouldInterceptRequest(
				view: WebView?,
				request: WebResourceRequest?,
			): WebResourceResponse? {
				return request?.let { interceptNovelFontRequest(view, it) }
					?: request?.let(::interceptNovelImageRequest)
					?: super.shouldInterceptRequest(view, request)
			}

			override fun onPageFinished(view: WebView?, url: String?) {
				super.onPageFinished(view, url)
				if (view != null) {
					restoreLoadedPage(view, pageLoadGeneration, "onPageFinished")
				}
			}

			override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = true

			override fun shouldOverrideUrlLoading(
				view: WebView?,
				request: WebResourceRequest?,
			): Boolean = true
		}
	}

	override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
		if (key != null && key.startsWith("novel_") && isContentLoaded) {
			if (
				key == AppSettings.KEY_NOVEL_CONTINUOUS_CHAPTERS &&
				!settings.isNovelContinuousChapters
			) {
				viewModel.resetNovelSequenceToCurrentChapter()
			} else {
				reloadContent()
			}
		}
	}

	private fun loadNovelHtml(contents: List<NovelContent>) {
		val binding = viewBinding ?: return
		val firstContent = contents.firstOrNull() ?: return
		updateNovelRequestMetadata(contents)
		val readerSettings = NovelReaderSettings.from(settings)
		binding.webViewNovel.setBackgroundColor(readerSettings.backgroundColor)
		val html = buildHtml(contents, readerSettings)
		val generation = ++pageLoadGeneration
		binding.webViewNovel.loadDataWithBaseURL(firstContent.baseUrl, html, "text/html", "UTF-8", null)
		// Certain WebView builds keep the document in a loading state while a
		// subresource is pending and never dispatch onPageFinished, even though
		// the chapter is already fully readable. Restore independently so the
		// continuous-reader triggers can never remain disabled.
		binding.webViewNovel.postDelayed(
			{
				restoreLoadedPage(
					binding.webViewNovel,
					generation,
					"ready fallback",
				)
			},
			PAGE_READY_FALLBACK_MS,
		)
		isContentLoaded = true
	}

	private fun appendNovelHtml(
		allContents: List<NovelContent>,
		appendedContents: List<NovelContent>,
	) {
		val webView = viewBinding?.webViewNovel ?: return
		if (appendedContents.isEmpty()) return
		updateNovelRequestMetadata(allContents)
		val sectionsHtml = appendedContents.joinToString(separator = "\n", transform = ::buildChapterHtml)
		val script = """
			(function () {
				return !!(
					window.NovelReader &&
					window.NovelReader.appendChapters(${JSONObject.quote(sectionsHtml)})
				);
			})();
		""".trimIndent()
		webView.evaluateJavascript(script) { result ->
			if (result == "true" || viewBinding?.webViewNovel !== webView) {
				Log.i(
					LOG_TAG,
					"Appended chapters in place=${appendedContents.map(NovelContent::chapterId)}",
				)
				return@evaluateJavascript
			}
			// Keep a fallback for old WebView implementations which reject the
			// in-place append, rather than leaving the next chapter blank.
			Log.w(LOG_TAG, "DOM append unavailable ($result); falling back to a full render")
			pendingState = getCurrentState()
				?: ReaderState(allContents.first().chapterId, 0, 0)
			isRestoringPosition = true
			loadNovelHtml(allContents)
		}
	}

	private fun restoreLoadedPage(
		webView: WebView,
		generation: Int,
		reason: String,
	) {
		if (
			generation != pageLoadGeneration ||
			restoredPageLoadGeneration == generation ||
			viewBinding?.webViewNovel !== webView
		) {
			return
		}
		restoredPageLoadGeneration = generation
		val state = pendingState ?: viewModel.getCurrentState()
		pendingState = null
		Log.i(LOG_TAG, "Restoring page generation=$generation via $reason, state=$state")
		if (state != null) {
			restoreNovelPosition(webView, state)
		} else {
			isRestoringPosition = false
		}
	}

	private fun updateNovelRequestMetadata(contents: List<NovelContent>) {
		novelBaseUrl = contents.lastOrNull()?.baseUrl
		novelImageHeaders = buildImageHeaderMap(contents)
	}

	private fun restoreNovelPosition(webView: WebView, state: ReaderState) {
		val generation = ++restoreGeneration
		var completed = false
		fun completeRestoration() {
			if (completed || generation != restoreGeneration || viewBinding == null) return
			completed = true
			isRestoringPosition = false
			Log.i(LOG_TAG, "Position restoration complete for chapter=${state.chapterId}")
			currentChapterId?.let(::requestNextNovelChapter)
		}
		webView.evaluateJavascript(
			"window.NovelReader && window.NovelReader.scrollToChapter(" +
				"'${state.chapterId}', ${state.scroll.coerceAtLeast(0)});",
		) {
			completeRestoration()
		}
		// Some WebView versions do not invoke the ValueCallback for this
		// fire-and-forget script. Never leave the reader permanently blocked in
		// restoration mode, otherwise both native and JS next-chapter triggers
		// are ignored.
		webView.postDelayed(::completeRestoration, RESTORE_CALLBACK_TIMEOUT_MS)
	}

	private fun requestNextNovelChapter(chapterId: Long) {
		Log.i(
			LOG_TAG,
			"Request next after=$chapterId, enabled=${settings.isNovelContinuousChapters}, " +
				"renderedLast=${renderedChapterIds.lastOrNull()}",
		)
		if (
			viewBinding != null &&
			settings.isNovelContinuousChapters &&
			renderedChapterIds.lastOrNull() == chapterId
		) {
			viewModel.appendNextNovelChapter(chapterId)
		}
	}

	private fun interceptNovelFontRequest(
		view: WebView?,
		request: WebResourceRequest,
	): WebResourceResponse? {
		if (
			request.method != "GET" ||
			request.isForMainFrame ||
			!request.url.host.equals(NOVEL_FONT_HOST, ignoreCase = true)
		) {
			return null
		}
		val fileName = request.url.lastPathSegment
			?.takeIf { it in NOVEL_FONT_ASSET_FILES }
			?: return null
		val stream = runCatching {
			view?.context?.assets?.open("fonts/$fileName")
		}.getOrNull() ?: return null
		return WebResourceResponse(
			"font/ttf",
			null,
			200,
			"OK",
			mapOf(
				"Access-Control-Allow-Origin" to "*",
				"Cache-Control" to "public, max-age=31536000, immutable",
				"Cross-Origin-Resource-Policy" to "cross-origin",
			),
			stream,
		)
	}

	private fun buildImageHeaderMap(contents: List<NovelContent>): Map<String, Map<String, String>> {
		if (contents.none { it.images.isNotEmpty() }) return emptyMap()
		val result = LinkedHashMap<String, Map<String, String>>()
		for (content in contents) {
			for (image in content.images) {
				result[image.url] = image.headers
				resolveNovelUrl(content.baseUrl, image.url)?.let { result[it] = image.headers }
			}
		}
		return result
	}

	private fun interceptNovelImageRequest(request: WebResourceRequest): WebResourceResponse? {
		if (request.method != "GET" || request.isForMainFrame) return null
		val url = request.url.toString()
		if (!url.startsWith("https://", true) && !url.startsWith("http://", true)) return null
		val explicitHeaders = findImageHeaders(url)
		val acceptsImage = request.requestHeaders.entries.any { (name, value) ->
			name.equals("Accept", true) && value.contains("image", true)
		}
		if (explicitHeaders == null && !acceptsImage && !IMAGE_URL_HINT.containsMatchIn(url.substringBefore('?'))) {
			return null
		}

		return runCatching {
			val builder = Request.Builder().url(url).get()
			request.requestHeaders.forEach { (name, value) ->
				if (name.lowercase(Locale.ROOT) !in HOP_BY_HOP_HEADERS) builder.header(name, value)
			}
		explicitHeaders.orEmpty().forEach { (name, value) -> builder.header(name, value) }
			if (request.requestHeaders.keys.none { it.equals("Referer", true) }) {
				novelBaseUrl?.let { builder.header("Referer", it) }
			}
			CookieManager.getInstance().getCookie(url)?.takeIf(String::isNotBlank)?.let {
				builder.header("Cookie", it)
			}
			val response = okHttpClient.newCall(builder.build()).execute()
			val body = response.body ?: run {
				response.close()
				return@runCatching null
			}
			val mediaType = body.contentType()
			val mimeType = mediaType
				?.takeIf { it.type.equals("image", ignoreCase = true) }
				?.let { "${it.type}/${it.subtype}" }
				?: URLConnection.guessContentTypeFromName(url.substringBefore('?'))
				?: "image/*"
			val encoding = mediaType?.charset()?.name() ?: "UTF-8"
			val responseHeaders = LinkedHashMap<String, String>()
			response.headers.forEach { (name, value) ->
				if (!name.equals("Content-Encoding", true) && !name.equals("Content-Length", true)) {
					responseHeaders[name] = value
				}
			}
			val stream = object : FilterInputStream(body.byteStream()) {
				override fun close() {
					try {
						super.close()
					} finally {
						response.close()
					}
				}
			}
			WebResourceResponse(
				mimeType,
				encoding,
				response.code,
				response.message.ifBlank { "OK" },
				responseHeaders,
				stream,
			)
		}.getOrNull()
	}

	private fun findImageHeaders(url: String): Map<String, String>? {
		novelImageHeaders[url]?.let { return it }
		val comparable = url.substringBefore('#').substringBefore('?')
		return novelImageHeaders.entries.firstOrNull { (candidate, _) ->
			candidate.substringBefore('#').substringBefore('?') == comparable
		}?.value
	}

	private fun resolveNovelUrl(baseUrl: String?, value: String): String? = runCatching {
		when {
			value.startsWith("//") -> "${baseUrl?.let { URI(it).scheme } ?: "https"}:$value"
			baseUrl.isNullOrBlank() -> value
			else -> URI(baseUrl).resolve(value).toString()
		}
	}.getOrNull()

	private fun buildHtml(contents: List<NovelContent>, s: NovelReaderSettings): String {
		val textColorHex = String.format("#%06X", 0xFFFFFF and s.textColor)
		val bgColorHex = String.format("#%06X", 0xFFFFFF and s.backgroundColor)
		val textAlign = s.textAlign.takeIf { it in VALID_TEXT_ALIGNMENTS } ?: "start"
		val fontFamily = normalizeFontFamily(s.fontFamily)
		val cssFontFamily = FONT_CSS_FAMILIES.getValue(fontFamily)
		val fontSize = s.fontSize.coerceIn(10, 40)
		val lineSpacing = s.lineSpacing.coerceIn(1f, 2.5f)
		val chaptersHtml = contents.joinToString(separator = "\n", transform = ::buildChapterHtml)
		// CSS notes:
		//  - Each chapter detects its own direction and uses unicode-bidi:plaintext so
		//    each paragraph picks its own direction (Arabic text → RTL,
		//    English/URLs → LTR). Forcing RTL on everything mangled embedded
		//    English / numbers / URLs in older builds.
		//  - We DO NOT strip all inline styles: legitimate bold/italic/colors
		//    set by the source are preserved. We only neutralize layout
		//    properties (float / fixed positioning / fixed widths) that break
		//    the small-screen single-column flow.
		return """
		<!DOCTYPE html>
		<html>
		<head>
		<meta charset="UTF-8">
		<meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
		<style>
			@font-face {
				font-family: 'Novel Cairo';
				src: url('$NOVEL_FONT_ORIGIN/Cairo-Variable.ttf') format('truetype');
				font-style: normal;
				font-weight: 100 900;
				font-display: swap;
			}
			@font-face {
				font-family: 'Novel Tajawal';
				src: url('$NOVEL_FONT_ORIGIN/Tajawal-Regular.ttf') format('truetype');
				font-style: normal;
				font-weight: 400;
				font-display: swap;
			}
			@font-face {
				font-family: 'Novel Amiri';
				src: url('$NOVEL_FONT_ORIGIN/Amiri-Regular.ttf') format('truetype');
				font-style: normal;
				font-weight: 400;
				font-display: swap;
			}
			html, body { margin: 0; padding: 0; }
			body {
				padding: 16px;
				font-size: ${fontSize}px;
				font-family: $cssFontFamily;
				line-height: $lineSpacing;
				color: $textColorHex;
				background-color: $bgColorHex;
				text-align: $textAlign;
				unicode-bidi: plaintext;
				word-wrap: break-word;
				overflow-wrap: break-word;
				hyphens: auto;
			}
			body, body * {
				font-family: $cssFontFamily !important;
			}
			/* Block-level elements take the body's writing mode but don't get
			   their text-align overridden, so chapter titles / centered notes
			   from the source still read the way the author meant. */
			p, div, section, article, aside, header, footer, main, nav,
			h1, h2, h3, h4, h5, h6 {
				unicode-bidi: plaintext;
				float: none !important;
				position: static !important;
				max-width: 100% !important;
			}
			p { margin: 0 0 0.8em; }
			h1, h2, h3, h4, h5, h6 { margin: 1em 0 0.5em; }
			/* Tables collapse to a single column so they don't scroll sideways. */
			table, tbody, thead, tfoot, tr { display: block; width: 100% !important; }
			td, th {
				display: block;
				width: 100% !important;
				border: none !important;
				padding: 0 !important;
				float: none !important;
			}
			img, video {
				max-width: 100% !important;
				height: auto !important;
				display: block;
				margin: 8px auto;
			}
			a { color: inherit; }
			.novel-chapter {
				min-height: 1px;
			}
			.novel-chapter:not(:first-of-type) {
				border-top: 1px solid currentColor;
				margin-top: 2.5em;
				padding-top: 1.5em;
			}
			.novel-chapter-title {
				text-align: center;
				opacity: 0.75;
				overflow-wrap: anywhere;
			}
			/* Drop fixed/sticky overlays that some sources inject (ads, share
			   buttons) which would otherwise float over the text. */
			[style*="position:fixed"], [style*="position: fixed"],
			[style*="position:sticky"], [style*="position: sticky"] {
				position: static !important;
			}
		</style>
		</head>
		<body>
		<main id="novel-chapters">
			$chaptersHtml
		</main>
		<script>
			(function () {
				var bridge = window.$NOVEL_BRIDGE_NAME;
				var ticking = false;
				var requestingNext = false;
				var retryTimer = 0;

				function chapters() {
					return document.querySelectorAll('.novel-chapter[data-chapter-id]');
				}

				function findChapter(chapterId) {
					var items = chapters();
					for (var i = 0; i < items.length; i++) {
						if (items[i].getAttribute('data-chapter-id') === String(chapterId)) {
							return items[i];
						}
					}
					return null;
				}

				function reportPosition() {
					ticking = false;
					var items = chapters();
					if (!items.length || !bridge) return;
					var scrollY = Math.max(0, window.scrollY || document.documentElement.scrollTop || 0);
					var current = items[0];
					for (var i = 1; i < items.length; i++) {
						if (items[i].offsetTop <= scrollY + 1) {
							current = items[i];
						} else {
							break;
						}
					}
					var chapterId = current.getAttribute('data-chapter-id');
					var chapterScroll = Math.max(0, Math.round(scrollY - current.offsetTop));
					bridge.onPositionChanged(chapterId, chapterScroll);

					var root = document.documentElement;
					var remaining = root.scrollHeight - scrollY - window.innerHeight;
					if (!requestingNext && remaining <= window.innerHeight * 2) {
						requestingNext = true;
						bridge.onLoadNextChapter(
							items[items.length - 1].getAttribute('data-chapter-id')
						);
						clearTimeout(retryTimer);
						retryTimer = setTimeout(function () {
							requestingNext = false;
						}, 1500);
					}
				}

				function scheduleReport() {
					if (!ticking) {
						ticking = true;
						window.requestAnimationFrame(reportPosition);
					}
				}

				window.NovelReader = {
					scrollToChapter: function (chapterId, offset) {
						var section = findChapter(chapterId);
						if (section) {
							var restore = function () {
								window.scrollTo(
									0,
									section.offsetTop + Math.max(0, offset || 0)
								);
								scheduleReport();
							};
							restore();
							if (document.fonts && document.fonts.ready) {
								document.fonts.ready.then(restore);
							}
							var pendingImages = Array.prototype.slice.call(document.images)
								.filter(function (image) { return !image.complete; })
								.map(function (image) {
									return new Promise(function (resolve) {
										image.addEventListener('load', resolve, { once: true });
										image.addEventListener('error', resolve, { once: true });
									});
								});
							if (pendingImages.length) {
								Promise.all(pendingImages).then(restore);
							}
						}
						scheduleReport();
					},
					appendChapters: function (html) {
						var container = document.getElementById('novel-chapters');
						if (!container) return false;
						container.insertAdjacentHTML('beforeend', html);
						requestingNext = false;
						scheduleReport();
						return true;
					}
				};

				window.addEventListener('scroll', scheduleReport, { passive: true });
				window.addEventListener('resize', scheduleReport);
				scheduleReport();
			})();
		</script>
		</body>
		</html>
		""".trimIndent()
	}

	private fun buildChapterHtml(content: NovelContent): String {
		val normalizedHtml = normalizeNovelMarkupForDisplay(content.html)
		val cleanedHtml = sanitizeNovelHtmlForReader(normalizedHtml)
		val resolvedHtml = absolutizeNovelResourceUrls(cleanedHtml, content.baseUrl)
		val direction = detectTextDirection(toPlainText(resolvedHtml))
		val title = content.chapterTitle
			?.takeIf(String::isNotBlank)
			?.let { "<h2 class=\"novel-chapter-title\">${TextUtils.htmlEncode(it)}</h2>" }
			.orEmpty()
		return """
			<section
				class="novel-chapter"
				data-chapter-id="${content.chapterId}"
				dir="$direction">
				$title
				$resolvedHtml
			</section>
		""".trimIndent()
	}

	private fun absolutizeNovelResourceUrls(html: String, baseUrl: String?): String = runCatching {
		if (baseUrl.isNullOrBlank()) return@runCatching html
		val body = Jsoup.parseBodyFragment(html, baseUrl).body()
		body.select("[src], [poster]").forEach { element ->
			for (attribute in arrayOf("src", "poster")) {
				if (!element.hasAttr(attribute)) continue
				resolveNovelUrl(baseUrl, element.attr(attribute))
					?.let { element.attr(attribute, it) }
			}
		}
		body.select("[srcset]").forEach { element ->
			val resolved = element.attr("srcset").split(',').joinToString(", ") { candidate ->
				val trimmed = candidate.trim()
				val url = trimmed.substringBefore(' ')
				val descriptor = trimmed.substringAfter(' ', "").trim()
				buildString {
					append(resolveNovelUrl(baseUrl, url) ?: url)
					if (descriptor.isNotEmpty()) {
						append(' ')
						append(descriptor)
					}
				}
			}
			element.attr("srcset", resolved)
		}
		body.html()
	}.getOrElse { html }

	/**
	 * Defang source HTML so it doesn't fight our reader styling, but keep
	 * legitimate inline formatting (bold / italic / colors) intact.
	 *
	 * What we DO strip:
	 *  - <style>, <script>, <link>, <meta>, <title> (otherwise they render as
	 *    plain text in the body or hide content via display:none)
	 *  - dir="ltr" on block elements (we let unicode-bidi:plaintext infer it)
	 *  - hard-coded `width="..."` on non-image elements that overflow phone screens
	 *  - float/position/width CSS rules inside inline `style="..."` (other style
	 *    properties like color, font-weight, font-style are kept)
	 */
	private fun cleanNovelHtml(html: String): String {
		var result = html
			// Strip head-only / script-like tags that some sources leave inside
			// the body. These would otherwise render as raw text or hide blocks.
			.replace(Regex("""<style\b[^>]*>[\s\S]*?</style>""", RegexOption.IGNORE_CASE), "")
			.replace(Regex("""<script\b[^>]*>[\s\S]*?</script>""", RegexOption.IGNORE_CASE), "")
			.replace(Regex("""<link\b[^>]*/?>""", RegexOption.IGNORE_CASE), "")
			.replace(Regex("""<meta\b[^>]*/?>""", RegexOption.IGNORE_CASE), "")
			.replace(Regex("""<title\b[^>]*>[\s\S]*?</title>""", RegexOption.IGNORE_CASE), "")
			.replace(Regex("""<noscript\b[^>]*>[\s\S]*?</noscript>""", RegexOption.IGNORE_CASE), "")
			.replace(Regex("""<iframe\b[^>]*>[\s\S]*?</iframe>""", RegexOption.IGNORE_CASE), "")
			.replace(Regex("""<object\b[^>]*>[\s\S]*?</object>""", RegexOption.IGNORE_CASE), "")
			.replace(Regex("""<embed\b[^>]*/?>""", RegexOption.IGNORE_CASE), "")
			.replace(Regex("""</?form\b[^>]*>""", RegexOption.IGNORE_CASE), "")
			.replace(Regex("""\son[a-z]+\s*=\s*(?:\"[^\"]*\"|'[^']*'|[^\s>]+)""", RegexOption.IGNORE_CASE), "")
			// Remove fixed widths on non-image elements (they cause horizontal scrolling).
			.replace(Regex("""(<(?!img\b|image\b)[a-zA-Z][^>]*?)\s+width\s*=\s*"[^"]*"""", RegexOption.IGNORE_CASE), "$1")
			.replace(Regex("""(<(?!img\b|image\b)[a-zA-Z][^>]*?)\s+width\s*=\s*'[^']*'""", RegexOption.IGNORE_CASE), "$1")
		// Surgically scrub layout-breaking properties from inline `style="..."`,
		// preserving everything else (color, weight, decoration, …).
		val styleRegex = Regex("""style\s*=\s*(["'])([\s\S]*?)\1""", RegexOption.IGNORE_CASE)
		result = styleRegex.replace(result) { match ->
			val quote = match.groupValues[1]
			val inner = match.groupValues[2]
				.replace(Regex("""float\s*:\s*[^;"']+;?""", RegexOption.IGNORE_CASE), "")
				.replace(Regex("""position\s*:\s*(fixed|absolute|sticky)\s*;?""", RegexOption.IGNORE_CASE), "")
				.replace(Regex("""\bwidth\s*:\s*[^;"']+;?""", RegexOption.IGNORE_CASE), "")
				.replace(Regex("""\bheight\s*:\s*[^;"']+;?""", RegexOption.IGNORE_CASE), "")
				.replace(Regex(""";\s*;""", RegexOption.IGNORE_CASE), ";")
				.trim().trim(';').trim()
			if (inner.isEmpty()) "" else "style=$quote$inner$quote"
		}
		return result
	}

	private fun detectTextDirection(text: String): String {
		for (character in text) {
			when (Character.getDirectionality(character)) {
				Character.DIRECTIONALITY_RIGHT_TO_LEFT,
				Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return "rtl"

				Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return "ltr"
			}
		}
		return "auto"
	}

	private fun toPlainText(html: String): String = runCatching {
		HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
	}.getOrElse {
		html.replace(Regex("<[^>]+>"), " ")
	}

	private fun normalizeFontFamily(value: String): String = when (value) {
		FONT_CAIRO, "noto_naskh_arabic", "noto_kufi_arabic", "serif", "monospace" -> FONT_CAIRO
		FONT_TAJAWAL, "noto_sans_arabic", "sans-serif" -> FONT_TAJAWAL
		FONT_AMIRI, "cursive" -> FONT_AMIRI
		else -> FONT_CAIRO
	}

	override fun switchPageBy(delta: Int) {
		if (settings.isNovelContinuousChapters) {
			// Smooth scroll mode - scroll by a smaller amount
			val webView = viewBinding?.webViewNovel ?: return
			val scrollAmount = webView.height / 3
			webView.scrollBy(0, scrollAmount * delta)
		} else {
			// Page-flip mode - scroll by almost full screen
			val webView = viewBinding?.webViewNovel ?: return
			val scrollAmount = (webView.height * 0.85).toInt()
			webView.scrollBy(0, scrollAmount * delta)
		}
	}

	override fun switchPageTo(position: Int, smooth: Boolean) {
		// Not applicable for novel reader
	}

	override fun scrollBy(delta: Int, smooth: Boolean): Boolean {
		val webView = viewBinding?.webViewNovel ?: return false
		webView.scrollBy(0, delta)
		return true
	}

	override fun getCurrentState(): ReaderState? {
		val chapterId = currentChapterId
			?: viewModel.novelContent.value?.chapterId
			?: return null
		return ReaderState(
			chapterId = chapterId,
			page = 0,
			scroll = currentChapterScroll.coerceAtLeast(0),
		)
	}

	override fun onZoomIn() {
		settings.novelFontSize = (settings.novelFontSize + 2).coerceAtMost(40)
	}

	override fun onZoomOut() {
		settings.novelFontSize = (settings.novelFontSize - 2).coerceAtLeast(10)
	}

	fun reloadContent() {
		val contents = viewModel.novelContents.value
		if (contents.isEmpty()) return
		pendingState = getCurrentState()
		isRestoringPosition = true
		loadNovelHtml(contents)
	}

	override fun onDestroyView() {
		settings.unsubscribe(this)
		viewModel.saveCurrentState(getCurrentState())
		viewBinding?.webViewNovel?.apply {
			stopLoading()
			removeJavascriptInterface(NOVEL_BRIDGE_NAME)
			webViewClient = WebViewClient()
			destroy()
		}
		mainHandler.removeCallbacksAndMessages(null)
		pageLoadGeneration++
		restoreGeneration++
		isRestoringPosition = false
		isContentLoaded = false
		renderedChapterIds = emptyList()
		novelImageHeaders = emptyMap()
		novelBaseUrl = null
		super.onDestroyView()
	}

	private inner class NovelReaderBridge {

		@JavascriptInterface
		fun onPositionChanged(chapterId: String, scroll: Int) {
			if (isRestoringPosition) return
			val id = chapterId.toLongOrNull() ?: return
			val chapterChanged = currentChapterId != id
			currentChapterId = id
			currentChapterScroll = scroll.coerceAtLeast(0)
			if (chapterChanged) {
				Log.i(LOG_TAG, "Visible chapter changed to=$id, scroll=$scroll")
				mainHandler.post {
					if (viewBinding != null && currentChapterId == id) {
						viewModel.onNovelChapterVisible(
							ReaderState(id, 0, currentChapterScroll),
						)
						requestNextNovelChapter(id)
					}
				}
			}
		}

		@JavascriptInterface
		fun onLoadNextChapter(chapterId: String) {
			if (isRestoringPosition) return
			val id = chapterId.toLongOrNull() ?: return
			mainHandler.post {
				requestNextNovelChapter(id)
			}
		}
	}

	private companion object {

		const val FONT_CAIRO = "cairo"
		const val FONT_TAJAWAL = "tajawal"
		const val FONT_AMIRI = "amiri"
		const val LOG_TAG = "NovelContinuous"
		const val NOVEL_BRIDGE_NAME = "NovelReaderAndroid"
		const val PAGE_READY_FALLBACK_MS = 500L
		const val RESTORE_CALLBACK_TIMEOUT_MS = 350L
		const val NEXT_CHAPTER_PREFETCH_SCREENS = 2
		const val NOVEL_FONT_HOST = "novel-fonts.local"
		const val NOVEL_FONT_ORIGIN = "https://$NOVEL_FONT_HOST"
		val NOVEL_FONT_ASSET_FILES = setOf(
			"Cairo-Variable.ttf",
			"Tajawal-Regular.ttf",
			"Amiri-Regular.ttf",
		)
		val FONT_CSS_FAMILIES = mapOf(
			FONT_CAIRO to "'Novel Cairo', 'Cairo', sans-serif",
			FONT_TAJAWAL to "'Novel Tajawal', 'Tajawal', sans-serif",
			FONT_AMIRI to "'Novel Amiri', 'Amiri', serif",
		)
		val VALID_TEXT_ALIGNMENTS = setOf("start", "center", "justify", "end")
		val IMAGE_URL_HINT = Regex("(?i)\\.(?:avif|bmp|gif|jpe?g|png|svg|webp)$")
		val HOP_BY_HOP_HEADERS = setOf(
			"connection", "content-length", "host", "keep-alive", "proxy-authenticate",
			"proxy-authorization", "te", "trailer", "transfer-encoding", "upgrade",
		)
	}
}
