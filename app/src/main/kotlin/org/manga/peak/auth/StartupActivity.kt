package org.manga.peak.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
            val destination = if (AuthSession.hasSession(this@StartupActivity)) {
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
