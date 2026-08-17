package org.manga.peak.auth

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
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
        startLogoGlowAnimation()

        lifecycleScope.launch {
            delay(850)
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

    private fun startLogoGlowAnimation() {
        val logo = binding.imageLogo
        val wordmark = binding.textBrand
        val alpha = ObjectAnimator.ofFloat(logo, View.ALPHA, 0.72f, 1f).apply {
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
        }
        val scaleX = ObjectAnimator.ofFloat(logo, View.SCALE_X, 0.97f, 1.04f).apply {
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
        }
        val scaleY = ObjectAnimator.ofFloat(logo, View.SCALE_Y, 0.97f, 1.04f).apply {
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
        }
        AnimatorSet().apply {
            playTogether(alpha, scaleX, scaleY)
            duration = 900L
            startDelay = 120L
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            start()
        }
        ObjectAnimator.ofFloat(wordmark, View.ALPHA, 0.55f, 1f).apply {
            duration = 900L
            startDelay = 120L
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            start()
        }
    }
}
