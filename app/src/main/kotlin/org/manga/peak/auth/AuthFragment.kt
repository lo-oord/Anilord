package org.manga.peak.auth

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.manga.peak.R
import org.manga.peak.databinding.FragmentAuthBinding

class AuthFragment : Fragment() {
    private var _binding: FragmentAuthBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val authActivity get() = requireActivity() as AuthActivity
    private lateinit var screen: AuthScreen
    private var verificationEmail: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screen = requireArguments().getSerializable(ARG_SCREEN) as AuthScreen
        verificationEmail = requireArguments().getString(ARG_EMAIL).orEmpty()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAuthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        render()
    }

    private fun render() {
        binding.buttonBack.isVisible = screen != AuthScreen.LOGIN
        binding.buttonBack.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        binding.buttonGoogle.setOnClickListener { openGoogleSignIn() }
        binding.buttonGuest.setOnClickListener { continueAsGuest() }
        binding.textLinkPrimary.setOnClickListener { if (screen == AuthScreen.LOGIN) authActivity.show(AuthScreen.FORGOT_PASSWORD) }
        binding.textLinkSecondary.setOnClickListener { if (screen == AuthScreen.EMAIL_VERIFICATION) resendVerification() }
        binding.textBottomAction.setOnClickListener {
            when (screen) {
                AuthScreen.LOGIN -> authActivity.show(AuthScreen.CREATE_ACCOUNT)
                AuthScreen.CREATE_ACCOUNT, AuthScreen.FORGOT_PASSWORD, AuthScreen.EMAIL_VERIFICATION,
                AuthScreen.RESET_PASSWORD, AuthScreen.EMAIL_VERIFIED -> authActivity.show(AuthScreen.LOGIN)
                AuthScreen.SPLASH -> Unit
            }
        }
        binding.buttonPrimary.setOnClickListener { onPrimaryAction() }

        binding.inputUsername.isVisible = screen == AuthScreen.CREATE_ACCOUNT
        binding.inputConfirmPassword.isVisible = screen == AuthScreen.CREATE_ACCOUNT || screen == AuthScreen.RESET_PASSWORD
        binding.checkboxTerms.isVisible = screen == AuthScreen.CREATE_ACCOUNT
        binding.textRequirements.isVisible = screen == AuthScreen.RESET_PASSWORD
        binding.inputEmail.isVisible = screen in setOf(AuthScreen.LOGIN, AuthScreen.CREATE_ACCOUNT, AuthScreen.FORGOT_PASSWORD)
        binding.inputPassword.isVisible = screen in setOf(AuthScreen.LOGIN, AuthScreen.CREATE_ACCOUNT, AuthScreen.RESET_PASSWORD)
        binding.formFields.isVisible = screen in setOf(AuthScreen.LOGIN, AuthScreen.CREATE_ACCOUNT, AuthScreen.FORGOT_PASSWORD, AuthScreen.RESET_PASSWORD)
        binding.buttonGoogle.isVisible = screen == AuthScreen.LOGIN || screen == AuthScreen.CREATE_ACCOUNT
        binding.buttonGuest.isVisible = screen == AuthScreen.LOGIN || screen == AuthScreen.CREATE_ACCOUNT
        binding.textSecondaryInfo.isVisible = screen == AuthScreen.EMAIL_VERIFICATION || screen == AuthScreen.EMAIL_VERIFIED
        binding.textLinkPrimary.isVisible = screen == AuthScreen.LOGIN
        binding.textLinkSecondary.isVisible = screen == AuthScreen.EMAIL_VERIFICATION
        binding.textBottomAction.isVisible = screen != AuthScreen.SPLASH

        when (screen) {
            AuthScreen.SPLASH -> Unit
            AuthScreen.LOGIN -> renderLogin()
            AuthScreen.CREATE_ACCOUNT -> renderCreateAccount()
            AuthScreen.FORGOT_PASSWORD -> renderForgotPassword()
            AuthScreen.EMAIL_VERIFICATION -> renderEmailVerification()
            AuthScreen.RESET_PASSWORD -> renderResetPassword()
            AuthScreen.EMAIL_VERIFIED -> renderEmailVerified()
        }
    }

    private fun renderLogin() {
        binding.textTitle.setText(R.string.auth_welcome_title)
        binding.textSubtitle.setText(R.string.auth_welcome_subtitle)
        binding.buttonPrimary.setText(R.string.auth_login)
        binding.textLinkPrimary.setText(R.string.auth_forgot_password)
        binding.textBottomAction.setText(R.string.auth_no_account)
    }

    private fun renderCreateAccount() {
        binding.textTitle.setText(R.string.auth_create_title)
        binding.textSubtitle.setText(R.string.auth_create_subtitle)
        binding.buttonPrimary.setText(R.string.auth_create_account)
        binding.textBottomAction.setText(R.string.auth_have_account)
    }

    private fun renderForgotPassword() {
        binding.textTitle.setText(R.string.auth_forgot_title)
        binding.textSubtitle.setText(R.string.auth_forgot_subtitle)
        binding.buttonPrimary.setText(R.string.auth_send_reset_link)
        binding.textBottomAction.setText(R.string.auth_remember_password)
    }

    private fun renderEmailVerification() {
        binding.textTitle.setText(R.string.auth_verification_title)
        binding.textSubtitle.text = getString(R.string.auth_verification_subtitle, verificationEmail)
        binding.formFields.isVisible = false
        binding.textSecondaryInfo.setText(R.string.auth_check_inbox)
        binding.buttonPrimary.setText(R.string.auth_open_email_app)
        binding.textLinkSecondary.setText(R.string.auth_resend_email)
        binding.textBottomAction.setText(R.string.auth_back_to_login)
    }

    private fun renderResetPassword() {
        binding.textTitle.setText(R.string.auth_reset_title)
        binding.textSubtitle.setText(R.string.auth_reset_subtitle)
        binding.inputEmail.isVisible = false
        binding.inputPassword.hint = getString(R.string.auth_new_password)
        binding.inputConfirmPassword.hint = getString(R.string.auth_confirm_new_password)
        binding.buttonPrimary.setText(R.string.auth_update_password)
        binding.textBottomAction.setText(R.string.auth_back_to_login)
    }

    private fun renderEmailVerified() {
        binding.textTitle.setText(R.string.auth_verified_title)
        binding.textSubtitle.setText(R.string.auth_verified_subtitle)
        binding.formFields.isVisible = false
        binding.buttonPrimary.setText(R.string.auth_go_to_login)
        binding.textBottomAction.setText(R.string.auth_back_to_login)
    }

    private fun onPrimaryAction() {
        when (screen) {
            AuthScreen.LOGIN -> login()
            AuthScreen.CREATE_ACCOUNT -> createAccount()
            AuthScreen.FORGOT_PASSWORD -> sendResetLink()
            AuthScreen.EMAIL_VERIFICATION -> openEmailApp()
            AuthScreen.RESET_PASSWORD -> updatePassword()
            AuthScreen.EMAIL_VERIFIED -> authActivity.show(AuthScreen.LOGIN)
            AuthScreen.SPLASH -> Unit
        }
    }

    private fun login() {
        val email = binding.editEmail.text?.toString()?.trim().orEmpty()
        val password = binding.editPassword.text?.toString().orEmpty()
        if (!validateEmail(email) || !validatePassword(password)) return
        withLoading {
            val session = SupabaseAuthClient.signIn(email, password)
            AuthSession.saveSession(requireContext(), session)
            authActivity.finishAuthentication()
        }
    }

    private fun createAccount() {
        val username = binding.editUsername.text?.toString()?.trim().orEmpty()
        val email = binding.editEmail.text?.toString()?.trim().orEmpty()
        val password = binding.editPassword.text?.toString().orEmpty()
        val confirm = binding.editConfirmPassword.text?.toString().orEmpty()
        var valid = username.length >= 2
        if (!valid) binding.inputUsername.error = getString(R.string.auth_required_field)
        valid = validateEmail(email) && valid
        valid = validatePassword(password) && valid
        if (password != confirm) {
            binding.inputConfirmPassword.error = getString(R.string.auth_password_mismatch)
            valid = false
        }
        if (!binding.checkboxTerms.isChecked) {
            binding.checkboxTerms.error = getString(R.string.auth_terms_required)
            valid = false
        }
        if (!valid) return
        withLoading {
            val session = SupabaseAuthClient.signUp(email, password, username)
            verificationEmail = email
            if (session != null) {
                AuthSession.saveSession(requireContext(), session)
                authActivity.finishAuthentication()
            } else {
                authActivity.show(AuthScreen.EMAIL_VERIFICATION, email = email)
            }
        }
    }

    private fun sendResetLink() {
        val email = binding.editEmail.text?.toString()?.trim().orEmpty()
        if (!validateEmail(email)) return
        withLoading {
            SupabaseAuthClient.sendPasswordReset(email)
            verificationEmail = email
            toast(getString(R.string.auth_reset_sent, email))
            authActivity.show(AuthScreen.RESET_PASSWORD, email = email)
        }
    }

    private fun resendVerification() {
        if (verificationEmail.isBlank()) return
        withLoading {
            SupabaseAuthClient.resendVerification(verificationEmail)
            toast(getString(R.string.auth_verification_resent))
        }
    }

    private fun updatePassword() {
        val password = binding.editPassword.text?.toString().orEmpty()
        val confirm = binding.editConfirmPassword.text?.toString().orEmpty()
        if (!validatePassword(password)) return
        if (password != confirm) {
            binding.inputConfirmPassword.error = getString(R.string.auth_password_mismatch)
            return
        }
        val token = AuthSession.accessToken(requireContext())
        if (token.isNullOrBlank()) {
            toast(getString(R.string.auth_open_reset_email))
            return
        }
        withLoading {
            val session = SupabaseAuthClient.updatePassword(token, password)
            AuthSession.saveSession(requireContext(), session)
            authActivity.show(AuthScreen.EMAIL_VERIFIED)
        }
    }

    private fun openGoogleSignIn() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SupabaseAuthClient.googleSignInUrl())))
        } catch (_: ActivityNotFoundException) {
            toast(getString(R.string.auth_google_unavailable))
        }
    }

    private fun continueAsGuest() {
        withLoading {
            val session = SupabaseAuthClient.signInAnonymously()
            AuthSession.saveSession(requireContext(), session)
            authActivity.finishAuthentication()
        }
    }

    private fun openEmailApp() {
        try {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_EMAIL))
        } catch (_: ActivityNotFoundException) {
            toast(getString(R.string.auth_no_email_app))
        }
    }

    private fun validateEmail(email: String): Boolean {
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.inputEmail.error = getString(R.string.auth_invalid_email)
            return false
        }
        binding.inputEmail.error = null
        return true
    }

    private fun validatePassword(password: String): Boolean {
        if (password.length < 8) {
            binding.inputPassword.error = getString(R.string.auth_password_too_short)
            return false
        }
        binding.inputPassword.error = null
        return true
    }

    private fun withLoading(block: suspend () -> Unit) {
        binding.buttonPrimary.isEnabled = false
        binding.buttonGoogle.isEnabled = false
        binding.buttonGuest.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                block()
            } catch (error: SupabaseAuthException) {
                toast(error.message.orEmpty())
            } catch (error: Exception) {
                toast(error.message ?: getString(R.string.auth_network_error))
            } finally {
                if (_binding != null) {
                    binding.buttonPrimary.isEnabled = true
                    binding.buttonGoogle.isEnabled = true
                    binding.buttonGuest.isEnabled = true
                }
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_SCREEN = "screen"
        private const val ARG_EMAIL = "email"

        fun newInstance(screen: AuthScreen, email: String = ""): AuthFragment = AuthFragment().apply {
            arguments = Bundle().apply {
                putSerializable(ARG_SCREEN, screen)
                putString(ARG_EMAIL, email)
            }
        }
    }
}
