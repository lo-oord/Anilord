package org.manga.peak.auth

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import org.manga.peak.R
import java.util.Calendar

class ProfileActivity : AppCompatActivity() {
    private lateinit var usernameInput: TextInputEditText
    private lateinit var birthDateInput: TextInputEditText
    private lateinit var avatarImage: ImageView
    private var loading = false

    private val pickAvatar = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        AuthSession.saveAvatarUri(this, uri.toString())
        avatarImage.setImageURI(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        usernameInput = findViewById(R.id.edit_profile_username)
        birthDateInput = findViewById(R.id.edit_profile_birth_date)
        avatarImage = findViewById(R.id.image_profile_avatar)
        findViewById<android.widget.TextView>(R.id.text_profile_email).text =
            AuthSession.email(this).orEmpty()

        restoreAvatar()
        findViewById<android.view.View>(R.id.button_profile_back).setOnClickListener { finish() }
        findViewById<android.view.View>(R.id.button_profile_avatar_edit).setOnClickListener {
            pickAvatar.launch("image/*")
        }
        birthDateInput.setOnClickListener { showDatePicker() }
        findViewById<android.view.View>(R.id.button_profile_save).setOnClickListener { saveProfile() }
        findViewById<android.view.View>(R.id.button_profile_logout).setOnClickListener { signOut() }
        loadProfile()
    }

    private fun restoreAvatar() {
        val uri = AuthSession.avatarUri(this)?.let(Uri::parse) ?: return
        runCatching { avatarImage.setImageURI(uri) }
            .onFailure {
                AuthSession.saveAvatarUri(this, null)
                Toast.makeText(this, R.string.profile_image_not_available, Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadProfile() {
        val accessToken = AuthSession.accessToken(this)
        val userId = AuthSession.userId(this)
        if (accessToken.isNullOrBlank() || userId.isNullOrBlank()) return
        lifecycleScope.launch {
            runCatching { SupabaseAuthClient.getProfile(accessToken, userId) }
                .onSuccess { profile ->
                    usernameInput.setText(profile.username.orEmpty())
                    birthDateInput.setText(profile.birthDate.orEmpty())
                }
                .onFailure {
                    Toast.makeText(this@ProfileActivity, it.message ?: getString(R.string.profile_load_failed), Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day -> birthDateInput.setText("%04d-%02d-%02d".format(year, month + 1, day)) },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun saveProfile() {
        if (loading) return
        val username = usernameInput.text?.toString()?.trim().orEmpty()
        val birthDate = birthDateInput.text?.toString()?.trim().orEmpty().ifBlank { null }
        if (username.length < 2) {
            usernameInput.error = getString(R.string.auth_invalid_display_name)
            return
        }
        val accessToken = AuthSession.accessToken(this)
        val userId = AuthSession.userId(this)
        if (accessToken.isNullOrBlank() || userId.isNullOrBlank()) return

        loading = true
        findViewById<android.view.View>(R.id.button_profile_save).isEnabled = false
        lifecycleScope.launch {
            runCatching { SupabaseAuthClient.updateProfile(accessToken, userId, username, birthDate) }
                .onSuccess {
                    Toast.makeText(this@ProfileActivity, R.string.profile_saved, Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    Toast.makeText(this@ProfileActivity, it.message ?: getString(R.string.profile_save_failed), Toast.LENGTH_LONG).show()
                }
            loading = false
            findViewById<android.view.View>(R.id.button_profile_save).isEnabled = true
        }
    }

    private fun signOut() {
        if (loading) return
        loading = true
        lifecycleScope.launch {
            runCatching { SupabaseAuthClient.signOut(AuthSession.accessToken(this@ProfileActivity)) }
            AuthSession.clear(this@ProfileActivity)
            startActivity(Intent(this@ProfileActivity, AuthActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
    }
}
