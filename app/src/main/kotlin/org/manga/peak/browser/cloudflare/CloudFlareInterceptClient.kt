package org.manga.peak.browser.cloudflare

import android.graphics.Bitmap
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import okhttp3.OkHttpClient
import okhttp3.Request
import org.manga.peak.core.network.cookies.MutableCookieJar
import java.io.FilterInputStream
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val TAG = "CFInterceptClient"

/**
 * CloudFlare client with header interception to bypass blocking
 * Filters out sec-ch-ua, sec-ch-ua-full-version-list, and x-requested-with headers
 */
class CloudFlareInterceptClient(
	private val cookieJar: MutableCookieJar,
	callback: CloudFlareCallback,
	targetUrl: String,
) : CloudFlareClient(cookieJar, callback, targetUrl) {

	private val targetUri = runCatching { URI(targetUrl) }.getOrNull()
	private val client = OkHttpClient.Builder()
		.cookieJar(cookieJar)
		.connectTimeout(15, TimeUnit.SECONDS)
		.readTimeout(15, TimeUnit.SECONDS)
		.build()

	// Headers we want to block
	private val blockedHeaders = setOf(
		"sec-ch-ua",
		"sec-ch-ua-full-version-list",
		"x-requested-with"
	)

	override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
		super.onPageStarted(view, url, favicon)
		Log.d(TAG, "Page started with interception enabled: $url")
	}

	override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
		if (request == null) return null

		try {
			if (!shouldReplayRequest(request)) {
				return super.shouldInterceptRequest(view, request)
			}

			Log.d(TAG, "Intercepting request: ${request.url}")

			val requestBuilder = Request.Builder()
				.url(request.url.toString())
				.method(request.method, null)

			// Filter headers using blocklist - keep everything except blocked headers
			val blockedCount = mutableListOf<String>()
			for ((key, value) in request.requestHeaders) {
				val lowerKey = key.lowercase(Locale.ROOT)
				if (!blockedHeaders.contains(lowerKey)) {
					requestBuilder.addHeader(key, value)
				} else {
					blockedCount.add(key)
				}
			}
			if (blockedCount.isNotEmpty()) {
				Log.d(TAG, "Blocked headers: ${blockedCount.joinToString(", ")}")
			}

			val response = client.newCall(requestBuilder.build()).execute()

			val body = response.body ?: run {
				response.close()
				return null
			}
			val mediaType = body.contentType()
			val mimeType = mediaType?.let { "${it.type}/${it.subtype}" } ?: "text/html"
			val charset = mediaType?.charset()?.name() ?: "UTF-8"
			val responseUrl = response.request.url.toString()
			response.headers("Set-Cookie").forEach { cookie ->
				CookieManager.getInstance().setCookie(responseUrl, cookie)
			}
			CookieManager.getInstance().flush()
			val headers = linkedMapOf<String, String>()
			response.headers.forEach { (name, value) -> headers[name] = value }
			val stream = object : FilterInputStream(body.byteStream()) {
				override fun close() {
					try {
						super.close()
					} finally {
						response.close()
					}
				}
			}
			return WebResourceResponse(
				mimeType,
				charset,
				response.code,
				response.message.ifBlank { "OK" },
				headers,
				stream,
			)
		} catch (e: Exception) {
			Log.e(TAG, "Error intercepting request: ${request.url}", e)
			return null
		}
	}

	private fun shouldReplayRequest(request: WebResourceRequest): Boolean {
		if (request.method != "GET") {
			Log.d(TAG, "Skipping non-GET request: ${request.method} ${request.url}")
			return false
		}
		if (!request.isForMainFrame) {
			return false
		}
		if (!hasBlockedHeaders(request)) {
			return false
		}
		val requestUri = runCatching { URI(request.url.toString()) }.getOrNull() ?: return false
		val sameOrigin = requestUri.scheme.equals(targetUri?.scheme, ignoreCase = true)
			&& requestUri.host.equals(targetUri?.host, ignoreCase = true)
			&& normalizedPort(requestUri) == normalizedPort(targetUri)
		if (!sameOrigin) {
			Log.d(TAG, "Skipping off-origin main frame request: ${request.url}")
		}
		return sameOrigin
	}

	private fun hasBlockedHeaders(request: WebResourceRequest): Boolean {
		return request.requestHeaders.keys.any { it.lowercase(Locale.ROOT) in blockedHeaders }
	}

	private fun normalizedPort(uri: URI?): Int {
		if (uri == null) return -1
		return when {
			uri.port != -1 -> uri.port
			uri.scheme.equals("https", ignoreCase = true) -> 443
			uri.scheme.equals("http", ignoreCase = true) -> 80
			else -> -1
		}
	}
}
