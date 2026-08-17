package org.manga.peak.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.manga.peak.R
import org.manga.peak.main.ui.MainActivity

class AuthActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)
        if (savedInstanceState == null) {
            show(AuthScreen.LOGIN, addToBackStack = false)
        }
    }

    fun show(screen: AuthScreen, addToBackStack: Boolean = true, email: String = "") {
        val transaction = supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out,
                android.R.anim.fade_in,
                android.R.anim.fade_out,
            )
            .replace(R.id.auth_container, AuthFragment.newInstance(screen, email))
        if (addToBackStack) transaction.addToBackStack(screen.name)
        transaction.commit()
    }

    fun finishAuthentication() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}

enum class AuthScreen {
    SPLASH,
    LOGIN,
    CREATE_ACCOUNT,
    FORGOT_PASSWORD,
    EMAIL_VERIFICATION,
    RESET_PASSWORD,
    EMAIL_VERIFIED,
}
