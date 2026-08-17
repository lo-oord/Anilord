package org.manga.peak.core.network

import okhttp3.Interceptor
import okhttp3.Response
import okio.IOException
import org.manga.peak.core.exceptions.CloudFlareBlockedException
import org.manga.peak.core.exceptions.CloudFlareProtectedException
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.network.CloudFlareHelper

class CloudFlareInterceptor : Interceptor {

	override fun intercept(chain: Interceptor.Chain): Response {
		val response = chain.proceed(chain.request())
		// Use the request that actually reached the network. Interceptors deeper in the
		// chain add the parser source, User-Agent and may follow a redirect. Reusing the
		// outer request here made the CAPTCHA WebView solve a different fingerprint/URL.
		val request = response.request
		// Some old Android decoders throw from jsoup while peeking at a malformed or
		// truncated response body. That must not turn an otherwise usable response into
		// a fatal application crash; the actual parser can still handle/report the body.
		val protection = try {
			CloudFlareHelper.checkResponseForProtection(response)
		} catch (_: IllegalArgumentException) {
			return response
		}
		return when (protection) {
			CloudFlareHelper.PROTECTION_BLOCKED -> response.closeThrowing(
				CloudFlareBlockedException(
					url = request.url.toString(),
					source = request.tag(MangaSource::class.java),
				),
			)

			CloudFlareHelper.PROTECTION_CAPTCHA -> response.closeThrowing(
				CloudFlareProtectedException(
					url = request.url.toString(),
					source = request.tag(MangaSource::class.java),
					headers = request.headers,
				),
			)

			else -> response
		}
	}

	private fun Response.closeThrowing(error: IOException): Nothing {
		try {
			close()
		} catch (e: Exception) {
			error.addSuppressed(e)
		}
		throw error
	}
}
