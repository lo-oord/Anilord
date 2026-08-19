package anilord.app.core.network

import android.util.Log
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.Response
import anilord.app.BuildConfig
import java.util.concurrent.TimeUnit

class CacheLimitInterceptor : Interceptor {

	private val defaultMaxAge = TimeUnit.HOURS.toSeconds(1)
	private val defaultCacheControl = CacheControl.Builder()
		.maxAge(defaultMaxAge.toInt(), TimeUnit.SECONDS)
		.build()
		.toString()

	override fun intercept(chain: Interceptor.Chain): Response {
		val request = chain.request()
		val response = chain.proceed(request)
		if (BuildConfig.DEBUG && response.code in 300..399) {
			Log.w(
				REDIRECT_LOG_TAG,
				"${response.code} ${request.method} ${request.url} -> ${response.header("Location").orEmpty()}",
			)
		}
		val responseCacheControl = CacheControl.parse(response.headers)
		if (responseCacheControl.noStore || responseCacheControl.maxAgeSeconds <= defaultMaxAge) {
			return response
		}
		return response.newBuilder()
			.header(CommonHeaders.CACHE_CONTROL, defaultCacheControl)
			.build()
	}

	private companion object {
		const val REDIRECT_LOG_TAG = "HTTP_REDIRECT"
	}
}
