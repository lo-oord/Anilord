package org.manga.peak.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.manga.peak.databinding.ActivityStartupBinding
import org.manga.peak.main.ui.MainActivity

class StartupActivity : AppCompatActivity() {
    private lateinit var binding: ActivityStartupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStartupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            delay(450)
            val hasSession = if (AuthSession.hasSession(this@StartupActivity)) {
                val refreshToken = AuthSession.refreshToken(this@StartupActivity)
                if (refreshToken.isNullOrBlank()) {
                    true
                } else {
                    runCatching {
                        withContext(Dispatchers.IO) { SupabaseAuthClient.refresh(refreshToken) }
                    }.onSuccess { AuthSession.saveSession(this@StartupActivity, it) }
                        .onFailure { AuthSession.clear(this@StartupActivity) }
                        .isSuccess
                }
            } else {
                false
            }
            val destination = if (hasSession) {
                Intent(this@StartupActivity, MainActivity::class.java)
            } else {
                Intent(this@StartupActivity, AuthActivity::class.java)
            }
            startActivity(destination)
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }
}
