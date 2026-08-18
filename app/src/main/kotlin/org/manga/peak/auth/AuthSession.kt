package org.manga.peak.auth

import android.content.Context

object AuthSession {
    private const val PREFS = "anilord_supabase_session"
    private const val KEY_ACCESS = "access_token"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_EMAIL = "email"
    private const val KEY_AVATAR_URI = "avatar_uri"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasSession(context: Context): Boolean =
        prefs(context).getString(KEY_ACCESS, null).orEmpty().isNotBlank()

    fun accessToken(context: Context): String? = prefs(context).getString(KEY_ACCESS, null)

    fun refreshToken(context: Context): String? = prefs(context).getString(KEY_REFRESH, null)

    fun email(context: Context): String? = prefs(context).getString(KEY_EMAIL, null)

    fun userId(context: Context): String? = prefs(context).getString(KEY_USER_ID, null)

    fun avatarUri(context: Context): String? = prefs(context).getString(KEY_AVATAR_URI, null)

    fun saveAvatarUri(context: Context, uri: String?) {
        prefs(context).edit().putString(KEY_AVATAR_URI, uri).apply()
    }

    fun saveSession(context: Context, session: SupabaseSession) {
        prefs(context).edit()
            .putString(KEY_ACCESS, session.accessToken)
            .putString(KEY_REFRESH, session.refreshToken)
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_EMAIL, session.email)
            .apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
