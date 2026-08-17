package org.manga.peak.settings.sources.auth

import android.os.Bundle
import android.util.Patterns
import android.view.MenuItem
import android.view.View
import androidx.annotation.StringRes
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.manga.peak.R
import org.manga.peak.core.model.MangaSource
import org.manga.peak.core.model.getTitle
import org.manga.peak.core.nav.AppRouter
import org.manga.peak.core.parser.MangaRepository
import org.manga.peak.core.parser.ParserMangaRepository
import org.manga.peak.core.ui.BaseActivity
import org.manga.peak.core.util.ext.consume
import org.manga.peak.core.util.ext.getDisplayMessage
import org.manga.peak.databinding.ActivityCredentialsSourceAuthBinding
import org.koitharu.kotatsu.parsers.MangaAuthAccount
import org.koitharu.kotatsu.parsers.MangaAuthException
import org.koitharu.kotatsu.parsers.MangaParserCredentialsAuthProvider
import javax.inject.Inject

@AndroidEntryPoint
class CredentialsSourceAuthActivity :
	BaseActivity<ActivityCredentialsSourceAuthBinding>() {

	@Inject
	lateinit var repositoryFactory: MangaRepository.Factory

	private lateinit var authProvider: MangaParserCredentialsAuthProvider
	private var createAccountMode = false
	private var currentAccount: MangaAuthAccount? = null
	private var isLoading = false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityCredentialsSourceAuthBinding.inflate(layoutInflater))
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = true)

		val source = MangaSource(intent.getStringExtra(AppRouter.KEY_SOURCE))
		title = source.getTitle(this)
		val repository = repositoryFactory.create(source) as? ParserMangaRepository
		authProvider = repository?.getAuthProvider() as? MangaParserCredentialsAuthProvider
			?: run {
				finishAfterTransition()
				return
			}

		viewBinding.toolbar.title = getString(R.string.anime_witcher_account)
		viewBinding.toggleAuthMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
			if (isChecked) {
				setCreateAccountMode(checkedId == R.id.button_mode_sign_up)
			}
		}
		viewBinding.buttonSubmit.setOnClickListener { submitCredentials() }
		viewBinding.buttonResetPassword.setOnClickListener { resetPassword() }
		viewBinding.buttonRefreshAccount.setOnClickListener { refreshAccount() }
		viewBinding.buttonResendVerification.setOnClickListener { resendVerification() }
		viewBinding.buttonLogout.setOnClickListener { signOut() }
		setCreateAccountMode(false)
		loadAccount()
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
		android.R.id.home -> {
			finishAfterTransition()
			true
		}

		else -> super.onOptionsItemSelected(item)
	}

	override fun onApplyWindowInsets(
		v: View,
		insets: WindowInsetsCompat,
	): WindowInsetsCompat {
		val types = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
		val bars = insets.getInsets(types)
		viewBinding.root.updatePadding(
			left = bars.left,
			top = bars.top,
			right = bars.right,
			bottom = bars.bottom,
		)
		return insets.consume(v, types)
	}

	private fun setCreateAccountMode(enabled: Boolean) {
		createAccountMode = enabled
		viewBinding.layoutDisplayName.isVisible = enabled
		viewBinding.layoutPasswordConfirm.isVisible = enabled
		viewBinding.buttonSubmit.setText(if (enabled) R.string.create_account else R.string.sign_in)
		clearInputErrors()
	}

	private fun loadAccount() {
		runAccountAction {
			authProvider.getAccount()
		}
	}

	private fun submitCredentials() {
		val email = viewBinding.editEmail.text?.toString()?.trim().orEmpty()
		val password = viewBinding.editPassword.text?.toString().orEmpty()
		val displayName = viewBinding.editDisplayName.text?.toString()?.trim().orEmpty()
		val confirmation = viewBinding.editPasswordConfirm.text?.toString().orEmpty()
		if (!validateCredentials(email, password, displayName, confirmation)) return

		runAccountAction(
			successMessage = if (createAccountMode) {
				R.string.anime_witcher_account_created
			} else {
				R.string.anime_witcher_signed_in
			},
		) {
			if (createAccountMode) {
				authProvider.signUp(displayName, email, password)
			} else {
				authProvider.signIn(email, password)
			}
		}
	}

	private fun refreshAccount() {
		runAccountAction {
			authProvider.refreshAccount()
		}
	}

	private fun resendVerification() {
		runSimpleAction(R.string.anime_witcher_verification_sent) {
			authProvider.sendVerificationEmail()
		}
	}

	private fun resetPassword() {
		val email = viewBinding.editEmail.text?.toString()?.trim().orEmpty()
		viewBinding.layoutEmail.error = null
		if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
			viewBinding.layoutEmail.error = getString(R.string.auth_invalid_email)
			return
		}
		runSimpleAction(R.string.anime_witcher_password_reset_sent) {
			authProvider.sendPasswordReset(email)
		}
	}

	private fun signOut() {
		runSimpleAction(R.string.anime_witcher_signed_out) {
			authProvider.signOut()
			currentAccount = null
		}
	}

	private fun validateCredentials(
		email: String,
		password: String,
		displayName: String,
		confirmation: String,
	): Boolean {
		clearInputErrors()
		var valid = true
		if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
			viewBinding.layoutEmail.error = getString(R.string.auth_invalid_email)
			valid = false
		}
		if (password.length < MIN_PASSWORD_LENGTH) {
			viewBinding.layoutPassword.error = getString(R.string.auth_password_too_short)
			valid = false
		}
		if (createAccountMode && displayName.length < MIN_DISPLAY_NAME_LENGTH) {
			viewBinding.layoutDisplayName.error = getString(R.string.auth_invalid_display_name)
			valid = false
		}
		if (createAccountMode && confirmation != password) {
			viewBinding.layoutPasswordConfirm.error = getString(R.string.passwords_mismatch)
			valid = false
		}
		return valid
	}

	private fun clearInputErrors() {
		viewBinding.layoutDisplayName.error = null
		viewBinding.layoutEmail.error = null
		viewBinding.layoutPassword.error = null
		viewBinding.layoutPasswordConfirm.error = null
	}

	private fun runAccountAction(
		@StringRes successMessage: Int? = null,
		action: suspend () -> MangaAuthAccount?,
	) {
		if (isLoading) return
		lifecycleScope.launch {
			setLoading(true)
			try {
				val account = withContext(Dispatchers.IO) { action() }
				currentAccount = account
				showAccount(account)
				viewBinding.editPassword.text?.clear()
				viewBinding.editPasswordConfirm.text?.clear()
				successMessage?.let(::showMessage)
			} catch (error: Exception) {
				showAuthError(error)
			} finally {
				setLoading(false)
			}
		}
	}

	private fun runSimpleAction(
		@StringRes successMessage: Int,
		action: suspend () -> Unit,
	) {
		if (isLoading) return
		lifecycleScope.launch {
			setLoading(true)
			try {
				withContext(Dispatchers.IO) { action() }
				showAccount(currentAccount)
				showMessage(successMessage)
			} catch (error: Exception) {
				showAuthError(error)
			} finally {
				setLoading(false)
			}
		}
	}

	private fun showAccount(account: MangaAuthAccount?) {
		viewBinding.groupForm.isVisible = account == null
		viewBinding.groupAccount.isVisible = account != null
		if (account == null) {
			return
		}

		viewBinding.textAccountEmail.text = account.displayName
			?.takeIf(String::isNotBlank)
			?.let { "$it\n${account.username}" }
			?: account.username
		viewBinding.textAccountStatus.setText(
			if (account.isEmailVerified) R.string.email_verified else R.string.email_not_verified,
		)
		viewBinding.textVerificationHint.isVisible = !account.isEmailVerified
		viewBinding.buttonRefreshAccount.isVisible = !account.isEmailVerified
		viewBinding.buttonResendVerification.isVisible = !account.isEmailVerified
		if (account.isEmailVerified) {
			setResult(RESULT_OK)
			if (intent.getBooleanExtra(AppRouter.KEY_AUTH_RETURN_ON_SUCCESS, false)) {
				finishAfterTransition()
			}
		}
	}

	private fun setLoading(loading: Boolean) {
		isLoading = loading
		viewBinding.progressBar.isVisible = loading
		viewBinding.toggleAuthMode.isEnabled = !loading
		viewBinding.buttonSubmit.isEnabled = !loading
		viewBinding.buttonResetPassword.isEnabled = !loading
		viewBinding.buttonRefreshAccount.isEnabled = !loading
		viewBinding.buttonResendVerification.isEnabled = !loading
		viewBinding.buttonLogout.isEnabled = !loading
	}

	private fun showAuthError(error: Exception) {
		val message = if (error is MangaAuthException) {
			when (error.code) {
				"EMAIL_EXISTS" -> getString(R.string.auth_error_email_exists)
				"INVALID_LOGIN_CREDENTIALS", "EMAIL_NOT_FOUND", "INVALID_PASSWORD" ->
					getString(R.string.auth_error_invalid_credentials)
				"USER_DISABLED" -> getString(R.string.auth_error_user_disabled)
				"WEAK_PASSWORD" -> getString(R.string.auth_error_weak_password)
				"TOO_MANY_ATTEMPTS_TRY_LATER" -> getString(R.string.auth_error_too_many_attempts)
				"OPERATION_NOT_ALLOWED" -> getString(R.string.auth_error_operation_not_allowed)
				else -> getString(R.string.auth_error_generic, error.code)
			}
		} else {
			error.getDisplayMessage(resources)
		}
		Snackbar.make(viewBinding.root, message, Snackbar.LENGTH_LONG).show()
	}

	private fun showMessage(@StringRes message: Int) {
		Snackbar.make(viewBinding.root, message, Snackbar.LENGTH_LONG).show()
	}

	companion object {
		private const val MIN_PASSWORD_LENGTH = 6
		private const val MIN_DISPLAY_NAME_LENGTH = 2
	}
}
