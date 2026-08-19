package anilord.app.settings.account

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.AndroidEntryPoint
import anilord.app.R
import anilord.app.core.ui.BaseActivity
import anilord.app.databinding.ActivityFirebaseAccountBinding

@AndroidEntryPoint
class FirebaseAccountActivity : BaseActivity<ActivityFirebaseAccountBinding>() {

    private var auth: FirebaseAuth? = null
    private var isCreateMode = false

    private val googleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val account: GoogleSignInAccount? = GoogleSignIn.getSignedInAccountFromIntent(result.data).result
        val credential = account?.idToken?.let { GoogleAuthProvider.getCredential(it, null) }
        if (credential == null) {
            showMessage(getString(R.string.account_google_unavailable))
            return@registerForActivityResult
        }
        runAuthAction { auth?.signInWithCredential(credential) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ActivityFirebaseAccountBinding.inflate(layoutInflater))
        setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
        auth = FirebaseApp.initializeApp(this)?.let(FirebaseAuth::getInstance)
        setupActions()
        render(auth?.currentUser)
    }

    override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat = insets

    override fun onStart() {
        super.onStart()
        render(auth?.currentUser)
    }

    private fun setupActions() = with(viewBinding) {
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        emailAction.setOnClickListener { submitEmail() }
        toggleMode.setOnClickListener {
            isCreateMode = !isCreateMode
            updateMode()
        }
        googleAction.setOnClickListener { signInWithGoogle() }
        guestAction.setOnClickListener { runAuthAction { auth?.signInAnonymously() } }
        syncAction.setOnClickListener {
            render(auth?.currentUser)
            showMessage(getString(R.string.account_sync_ready))
        }
        signOutAction.setOnClickListener {
            auth?.signOut()
            GoogleSignIn.getClient(this@FirebaseAccountActivity, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
            render(null)
            showMessage(getString(R.string.account_signed_out))
        }
        updateMode()
    }

    private fun updateMode() = with(viewBinding) {
        authTitle.setText(if (isCreateMode) R.string.account_create_title else R.string.account_sign_in_title)
        authSubtitle.setText(if (isCreateMode) R.string.account_create_subtitle else R.string.account_sign_in_subtitle)
        emailAction.setText(if (isCreateMode) R.string.account_create_account else R.string.account_sign_in)
        toggleMode.setText(if (isCreateMode) R.string.account_have_account else R.string.account_create_account)
    }

    private fun submitEmail() = with(viewBinding) {
        val email = emailInput.text?.toString()?.trim().orEmpty()
        val password = passwordInput.text?.toString().orEmpty()
        emailLayout.error = if (email.contains("@")) null else getString(R.string.account_invalid_email)
        passwordLayout.error = if (password.length >= 6) null else getString(R.string.account_password_short)
        if (emailLayout.error != null || passwordLayout.error != null) return
        runAuthAction {
            if (isCreateMode) auth?.createUserWithEmailAndPassword(email, password)
            else auth?.signInWithEmailAndPassword(email, password)
        }
    }

    private fun signInWithGoogle() {
        val id = resources.getIdentifier("default_web_client_id", "string", packageName)
        if (id == 0 || getString(id).isBlank()) {
            showMessage(getString(R.string.account_google_unavailable))
            return
        }
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(id))
            .requestEmail()
            .build()
        googleLauncher.launch(GoogleSignIn.getClient(this, options).signInIntent)
    }

    private fun runAuthAction(action: () -> com.google.android.gms.tasks.Task<*>?) {
        val task = runCatching { action() }.getOrNull()
        if (task == null) {
            showMessage(getString(R.string.account_firebase_unavailable))
            return
        }
        setLoading(true)
        task.addOnCompleteListener { completed ->
            setLoading(false)
            if (completed.isSuccessful) {
                render(auth?.currentUser)
                showMessage(getString(R.string.account_signed_in))
            } else {
                showMessage(completed.exception?.localizedMessage ?: getString(R.string.account_auth_failed))
            }
        }
    }

    private fun render(user: com.google.firebase.auth.FirebaseUser?) = with(viewBinding) {
        val signedIn = user != null
        authFormContainer.animateVisibility(!signedIn)
        profileContainer.animateVisibility(signedIn)
        if (!signedIn) return@with
        val name = user?.displayName?.takeIf { it.isNotBlank() }
            ?: user?.email?.substringBefore('@')
            ?: getString(R.string.account_guest)
        profileAvatar.text = name.firstOrNull()?.uppercase() ?: "?"
        profileName.text = name
        profileEmail.text = user?.email ?: getString(R.string.account_guest_account)
        profileStatus.setText(if (user?.isAnonymous == true) R.string.account_guest_status else R.string.account_connected_status)
        profileStatusDetail.setText(if (user?.isAnonymous == true) R.string.account_guest_detail else R.string.account_connected_detail)
    }

    private fun setLoading(loading: Boolean) = with(viewBinding) {
        progress.isVisible = loading
        emailAction.isEnabled = !loading
        googleAction.isEnabled = !loading
        guestAction.isEnabled = !loading
        toggleMode.isEnabled = !loading
    }

    private fun showMessage(message: String) {
        Snackbar.make(viewBinding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun View.animateVisibility(visible: Boolean) {
        if (isVisible == visible) return
        if (visible) {
            isVisible = true
            alpha = 0f
            translationY = 12f
            animate().alpha(1f).translationY(0f).setDuration(220).start()
        } else {
            animate().alpha(0f).translationY(-8f).setDuration(160).withEndAction {
                isVisible = false
                alpha = 1f
                translationY = 0f
            }.start()
        }
    }
}
