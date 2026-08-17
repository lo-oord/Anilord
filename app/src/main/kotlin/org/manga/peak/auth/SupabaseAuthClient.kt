package org.manga.peak.auth

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

class SupabaseAuthException(message: String) : IOException(message)

data class SupabaseSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String?,
    val email: String?,
    val emailVerified: Boolean,
)

object SupabaseAuthClient {
    private val http = OkHttpClient()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun endpoint(path: String): String =
        "${AuthConfig.url.trimEnd('/')}$path"

    private fun requestBuilder(path: String, accessToken: String? = null): Request.Builder =
        Request.Builder()
            .url(endpoint(path))
            .header("apikey", AuthConfig.publishableKey)
            .header("Accept", "application/json")
            .apply {
                if (!accessToken.isNullOrBlank()) header("Authorization", "Bearer $accessToken")
            }

    private suspend fun execute(request: Request): JSONObject = withContext(Dispatchers.IO) {
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val json = if (body.isBlank()) JSONObject() else JSONObject(body)
            if (!response.isSuccessful) {
                throw SupabaseAuthException(
                    json.optString("error_description")
                        .ifBlank { json.optString("msg") }
                        .ifBlank { json.optString("message") }
                        .ifBlank { "Supabase request failed (${response.code})" },
                )
            }
            json
        }
    }

    suspend fun signIn(email: String, password: String): SupabaseSession =
        sessionFrom(execute(jsonRequest("/auth/v1/token?grant_type=password", JSONObject().apply {
            put("email", email)
            put("password", password)
        })))

    suspend fun signUp(email: String, password: String, username: String): SupabaseSession? {
        val response = execute(jsonRequest("/auth/v1/signup", JSONObject().apply {
            put("email", email)
            put("password", password)
            put("data", JSONObject().apply { put("username", username) })
        }))
        return if (response.has("access_token")) sessionFrom(response) else null
    }

    suspend fun signInAnonymously(): SupabaseSession =
        sessionFrom(execute(jsonRequest("/auth/v1/signup", JSONObject())))

    suspend fun sendPasswordReset(email: String) {
        execute(jsonRequest("/auth/v1/recover", JSONObject().apply {
            put("email", email)
            put("redirect_to", AuthConfig.redirectUri)
        }))
    }

    suspend fun resendVerification(email: String) {
        execute(jsonRequest("/auth/v1/resend", JSONObject().apply {
            put("type", "signup")
            put("email", email)
        }))
    }

    suspend fun updatePassword(accessToken: String, password: String): SupabaseSession =
        sessionFrom(execute(requestBuilder("/auth/v1/user", accessToken)
            .put(JSONObject().apply { put("password", password) }.toString().toRequestBody(jsonType))
            .build()))

    suspend fun refresh(refreshToken: String): SupabaseSession =
        sessionFrom(execute(jsonRequest("/auth/v1/token?grant_type=refresh_token", JSONObject().apply {
            put("refresh_token", refreshToken)
        })))

    suspend fun signOut(accessToken: String?) {
        if (accessToken.isNullOrBlank()) return
        execute(requestBuilder("/auth/v1/logout", accessToken).post(JSONObject().toString().toRequestBody(jsonType)).build())
    }

    fun googleSignInUrl(): String {
        val redirect = URLEncoder.encode(AuthConfig.redirectUri, Charsets.UTF_8.name())
        return endpoint("/auth/v1/authorize?provider=google&redirect_to=$redirect")
    }

    fun sessionFromRedirect(uri: Uri): SupabaseSession? {
        val values = linkedMapOf<String, String>()
        uri.queryParameterNames.forEach { key -> uri.getQueryParameter(key)?.let { values[key] = it } }
        uri.fragment?.removePrefix("#")?.split('&')?.forEach { pair ->
            val parts = pair.split('=', limit = 2)
            if (parts.size == 2) values[parts[0]] = Uri.decode(parts[1])
        }
        val access = values["access_token"] ?: return null
        val refresh = values["refresh_token"].orEmpty()
        return SupabaseSession(access, refresh, values["user_id"], null, values["type"] != "recovery")
    }

    private fun jsonRequest(path: String, body: JSONObject, accessToken: String? = null): Request =
        requestBuilder(path, accessToken)
            .post(body.toString().toRequestBody(jsonType))
            .build()

    private fun sessionFrom(json: JSONObject): SupabaseSession {
        val user = json.optJSONObject("user")
        return SupabaseSession(
            accessToken = json.optString("access_token"),
            refreshToken = json.optString("refresh_token"),
            userId = user?.optString("id")?.ifBlank { null },
            email = user?.optString("email")?.ifBlank { null },
            emailVerified = user?.optString("email_confirmed_at")?.isNotBlank() == true,
        ).also {
            if (it.accessToken.isBlank()) throw SupabaseAuthException("Supabase did not return a session")
        }
    }

}

object AuthConfig {
    lateinit var url: String
    lateinit var publishableKey: String
    lateinit var redirectUri: String

    fun initialize(resources: android.content.res.Resources) {
        url = resources.getString(org.manga.peak.R.string.supabase_url)
        publishableKey = resources.getString(org.manga.peak.R.string.supabase_publishable_key)
        redirectUri = resources.getString(org.manga.peak.R.string.supabase_auth_redirect)
    }
}
