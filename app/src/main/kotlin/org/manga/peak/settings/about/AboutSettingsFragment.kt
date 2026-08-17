package org.manga.peak.settings.about

import android.os.Bundle
import androidx.preference.Preference
import com.google.android.material.snackbar.Snackbar
import org.manga.peak.R
import org.manga.peak.core.nav.router
import org.manga.peak.core.prefs.AppSettings
import org.manga.peak.core.ui.BasePreferenceFragment
import org.manga.peak.settings.SettingsActivity

class AboutSettingsFragment : BasePreferenceFragment(R.string.about) {

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_about)
		findPreference<Preference>(AppSettings.KEY_APP_VERSION)?.run {
			title = getString(R.string.about_display_version)
		}
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean {
		return when (preference.key) {
			AppSettings.KEY_APP_VERSION -> {
				if (!router.openPlayStore(preference.title)) {
					Snackbar.make(listView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
				}
				true
			}

			KEY_PRIVACY -> {
				(activity as? SettingsActivity)?.openFragment(
					PrivacyPolicyFragment::class.java,
					null,
					false,
				)
				true
			}

			else -> super.onPreferenceTreeClick(preference)
		}
	}

	private companion object {
		const val KEY_PRIVACY = "about_privacy"
	}
}
