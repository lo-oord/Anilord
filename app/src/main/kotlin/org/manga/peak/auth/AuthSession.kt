package org.manga.peak.auth

import android.content.Context

/**
 * Local-only session used while the authentication screens are UI/navigation only.
 * It is intentionally isolated so a real provider can replace it later without
 * changing the Auth screen flow.
 */
object AuthSession {
    private const val PREFS = "anilord_auth_preview"
    private const val KEY_EMAIL = "email"
    private const val KEY_PASSWORD = "password"
    private const val KEY_SIGNED_IN = "signed_in"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasSession(context: Context): Boolean = prefs(context).getBoolean(KEY_SIGNED_IN, false)

    fun saveAccount(context: Context, email: String, password: String) {
        prefs(context).edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun signIn(context: Context, email: String, password: String): Boolean {
        val storedEmail = prefs(context).getString(KEY_EMAIL, null)
        val storedPassword = prefs(context).getString(KEY_PASSWORD, null)
        val valid = storedEmail.equals(email, ignoreCase = true) && storedPassword == password
        if (valid) {
            prefs(context).edit().putBoolean(KEY_SIGNED_IN, true).apply()
        }
        return valid
    }

    fun signOut(context: Context) {
        prefs(context).edit().putBoolean(KEY_SIGNED_IN, false).apply()
    }
}
