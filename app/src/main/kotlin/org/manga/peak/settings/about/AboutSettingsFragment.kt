package org.manga.peak.settings.about

import android.os.Bundle
import androidx.annotation.StringRes
import androidx.preference.Preference
import com.google.android.material.snackbar.Snackbar
import org.manga.peak.BuildConfig
import org.manga.peak.R
import org.manga.peak.core.nav.router
import org.manga.peak.core.prefs.AppSettings
import org.manga.peak.core.ui.BasePreferenceFragment

class AboutSettingsFragment : BasePreferenceFragment(R.string.about) {

	private var supportPreference: Preference? = null
	private var supportBillingManager: SupportBillingManager? = null

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_about)
		findPreference<Preference>(AppSettings.KEY_APP_VERSION)?.run {
			title = getString(R.string.app_version, BuildConfig.VERSION_NAME)
		}
		supportPreference = findPreference<Preference>(KEY_SUPPORT_DEVELOPER)?.apply {
			summary = getString(R.string.support_developer_loading)
		}
		supportBillingManager = SupportBillingManager(
			context = requireContext(),
			listener = object : SupportBillingManager.Listener {
				override fun onProductChanged(formattedPrice: String?) {
					if (!isAdded) return
					supportPreference?.summary = formattedPrice?.let {
						getString(R.string.support_developer_price, it)
					} ?: getString(R.string.support_developer_unavailable)
				}

				override fun onMessage(message: Int) {
					if (!isAdded || view == null) return
					Snackbar.make(listView, message, Snackbar.LENGTH_LONG).show()
				}
			},
		)
	}

	override fun onResume() {
		super.onResume()
		supportBillingManager?.start()
	}

	override fun onDestroy() {
		supportBillingManager?.close()
		supportBillingManager = null
		supportPreference = null
		super.onDestroy()
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean {
		return when (preference.key) {
			AppSettings.KEY_APP_VERSION -> {
				if (!router.openPlayStore(preference.title)) {
					Snackbar.make(listView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
				}
				true
			}

			KEY_SUPPORT_DEVELOPER -> {
				supportBillingManager?.launch(requireActivity())
				true
			}

			AppSettings.KEY_LINK_MANUAL -> {
				openLink(R.string.url_user_manual, preference.title)
				true
			}


			AppSettings.KEY_LINK_DISCORD -> {
				openLink(R.string.url_discord, preference.title)
				true
			}

			else -> super.onPreferenceTreeClick(preference)
		}
	}

	private fun openLink(
		@StringRes url: Int,
		title: CharSequence?
	): Boolean = if (router.openExternalBrowser(getString(url), title)) {
		true
	} else {
		Snackbar.make(listView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
		false
	}

	private companion object {
		const val KEY_SUPPORT_DEVELOPER = "support_developer"
	}
}
