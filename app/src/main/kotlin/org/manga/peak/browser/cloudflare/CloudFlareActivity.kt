package org.manga.peak.browser.cloudflare

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.webkit.CookieManager
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.view.isInvisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.yield
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.manga.peak.R
import org.manga.peak.browser.BaseBrowserActivity
import org.manga.peak.core.exceptions.CloudFlareProtectedException
import org.manga.peak.core.exceptions.resolve.CaptchaHandler
import org.manga.peak.core.model.MangaSource
import org.manga.peak.core.nav.AppRouter
import org.manga.peak.core.network.cookies.MutableCookieJar
import org.manga.peak.core.parser.ParserMangaRepository
import org.manga.peak.core.util.ext.getDisplayMessage
import org.manga.peak.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.network.CloudFlareHelper
import org.koitharu.kotatsu.parsers.util.ifNullOrEmpty
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import javax.inject.Inject

/** Visible, user-driven Cloudflare challenge screen. */
@AndroidEntryPoint
class CloudFlareActivity : BaseBrowserActivity(), CloudFlareCallback {

	private var pendingResult = RESULT_CANCELED
	private var loopHint: Snackbar? = null

	@Inject
	lateinit var cookieJar: MutableCookieJar

	@Inject
	lateinit var captchaHandler: CaptchaHandler

	private lateinit var cfClient: CloudFlareClient

	override fun onCreate2(savedInstanceState: Bundle?, source: MangaSource, repository: ParserMangaRepository?) {
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = true)
		val url = intent?.dataString
		if (url.isNullOrEmpty()) {
			finishAfterTransition()
			return
		}

		// Use the normal WebView request path just like the old implementation. Replaying
		// Cloudflare requests through OkHttp changes the browser fingerprint and can make
		// Turnstile repeat forever.
		cfClient = CloudFlareClient(cookieJar, this, url)
		viewBinding.webView.webViewClient = cfClient
		lifecycleScope.launch {
			try {
				proxyProvider.applyWebViewConfig()
			} catch (e: Exception) {
				Snackbar.make(viewBinding.webView, e.getDisplayMessage(resources), Snackbar.LENGTH_LONG).show()
			}
			if (savedInstanceState == null) {
				onTitleChanged(getString(R.string.loading_), url)
				viewBinding.webView.loadUrl(url)
			}
		}
	}

	override fun onCreateOptionsMenu(menu: Menu?): Boolean {
		menuInflater.inflate(R.menu.opt_captcha, menu)
		return super.onCreateOptionsMenu(menu)
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
		android.R.id.home -> {
			viewBinding.webView.stopLoading()
			finishAfterTransition()
			true
		}

		R.id.action_retry -> {
			restartCheck()
			true
		}

		else -> super.onOptionsItemSelected(item)
	}

	override fun finish() {
		setResult(pendingResult)
		super.finish()
	}

	override fun onLoadingStateChanged(isLoading: Boolean) = Unit

	override fun onPageLoaded() {
		viewBinding.progressBar.isInvisible = true
	}

	override fun onLoopDetected() {
		// Never reload automatically. Automatic restarts used to reset Turnstile and
		// send the user back to the checkbox. Keep the solved page intact and offer a
		// deliberate retry only when the user chooses it.
		if (loopHint?.isShown == true) return
		loopHint = Snackbar.make(
			viewBinding.webView,
			R.string.captcha_loop_hint,
			Snackbar.LENGTH_INDEFINITE,
		).setAction(R.string.try_again) {
			restartCheck()
		}.also { it.show() }
	}

	override fun onCheckPassed() {
		if (pendingResult == RESULT_OK) return
		pendingResult = RESULT_OK
		loopHint?.dismiss()
		lifecycleScope.launch {
			CookieManager.getInstance().flush()
			val source = intent?.getStringExtra(AppRouter.KEY_SOURCE)
			if (source != null) {
				runCatchingCancellable {
					captchaHandler.discard(MangaSource(source))
				}.onFailure {
					it.printStackTraceDebug()
				}
			}
			finishAfterTransition()
		}
	}

	override fun onTitleChanged(title: CharSequence, subtitle: CharSequence?) {
		setTitle(title)
		supportActionBar?.subtitle = subtitle?.toString()?.toHttpUrlOrNull()?.host.ifNullOrEmpty { subtitle }
	}

	private fun restartCheck() {
		loopHint?.dismiss()
		loopHint = null
		lifecycleScope.launch {
			viewBinding.webView.stopLoading()
			yield()
			cfClient.reset()
			val targetUrl = intent?.dataString?.toHttpUrlOrNull()
			if (targetUrl != null) {
				clearCfCookies(targetUrl)
				CookieManager.getInstance().flush()
				viewBinding.webView.loadUrl(targetUrl.toString())
			}
		}
	}

	private suspend fun clearCfCookies(url: HttpUrl) = runInterruptible(Dispatchers.Default) {
		cookieJar.removeCookies(url) { cookie ->
			CloudFlareHelper.isCloudFlareCookie(cookie.name)
		}
	}

	class Contract : ActivityResultContract<CloudFlareProtectedException, Boolean>() {
		override fun createIntent(context: Context, input: CloudFlareProtectedException): Intent {
			return AppRouter.cloudFlareResolveIntent(context, input)
		}

		override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
			return resultCode == RESULT_OK
		}
	}

	companion object {
		const val TAG = "CloudFlareActivity"
	}
}
