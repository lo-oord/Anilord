package anilord.app.settings.about

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.preference.Preference
import anilord.app.R
import anilord.app.core.prefs.AppSettings
import anilord.app.core.ui.BasePreferenceFragment

class AboutSettingsFragment : BasePreferenceFragment(R.string.about) {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_about)
        findPreference<Preference>(AppSettings.KEY_APP_VERSION)?.run {
            title = getString(R.string.about_display_version)
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        return when (preference.key) {
            KEY_PRIVACY -> {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.url_privacy_policy))))
                true
            }
            else -> super.onPreferenceTreeClick(preference)
        }
    }

    private companion object {
        const val KEY_PRIVACY = "about_privacy"
    }
}
