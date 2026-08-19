package anilord.app.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import anilord.app.core.network.BaseHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateRepository @Inject constructor(
    @BaseHttpClient baseClient: OkHttpClient,
) {
    private val client = baseClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun latest(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(LATEST_RELEASE_API)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val json = JSONObject(response.body?.string().orEmpty())
                if (json.optBoolean("draft", true) || json.optBoolean("prerelease", true)) return@use null
                val tag = json.optString("tag_name").trim()
                val versionName = tag.removePrefix("v").takeIf { it.isNotBlank() } ?: return@use null
                val releaseUrl = json.optString("html_url").trim().takeIf { it.startsWith("https://github.com/") } ?: return@use null
                val apkUrl = json.optJSONArray("assets")?.let { assets ->
                    (0 until assets.length()).asSequence()
                        .map { assets.optJSONObject(it) }
                        .filterNotNull()
                        .firstOrNull { asset ->
                            asset.optString("name").endsWith(".apk", ignoreCase = true) &&
                                asset.optString("browser_download_url").startsWith("https://github.com/")
                        }
                        ?.optString("browser_download_url")
                }?.takeIf { it.isNotBlank() } ?: return@use null
                val versionCode = extractVersionCode(json.optString("body"), apkUrl)
                    ?: semanticVersionCode(versionName)
                    ?: return@use null
                UpdateInfo(versionCode, versionName, json.optString("body").trim(), releaseUrl, apkUrl)
            }
        }.getOrNull()
    }

    	private fun extractVersionCode(notes: String, apkUrl: String): Long? {

        val explicit = Regex("(?i)(?:versionCode|version code)\\s*[:=]?\\s*(\\d+)")
            .find(notes)?.groupValues?.getOrNull(1)?.toLongOrNull()
        if (explicit != null) return explicit
		return Regex("(?i)versionCode[-_ ]?(\\d+)").find(apkUrl)?.groupValues?.getOrNull(1)?.toLongOrNull()
	}

	private fun semanticVersionCode(versionName: String): Long? {
		val parts = versionName.substringBefore('-').split('.')
		if (parts.size < 3) return null
		val major = parts[0].toLongOrNull() ?: return null
		val minor = parts[1].toLongOrNull() ?: return null
		val patch = parts[2].toLongOrNull() ?: return null
		return major * 1_000_000L + minor * 1_000L + patch
	}

    private companion object {
        const val LATEST_RELEASE_API = "https://api.github.com/repos/lo-oord/Anilord/releases/latest"
    }
}
