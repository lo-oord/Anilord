package anilord.app.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import dagger.hilt.android.AndroidEntryPoint
import anilord.app.R
import anilord.app.core.prefs.AppSettings
import anilord.app.core.ui.BasePreferenceFragment

@AndroidEntryPoint
class ThemeSettingsFragment :
    BasePreferenceFragment(R.string.theme),
    SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_theme)
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settings.subscribe(this)
    }

    override fun onDestroyView() {
        settings.unsubscribe(this)
        super.onDestroyView()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        if (key == AppSettings.KEY_THEME) {
            AppCompatDelegate.setDefaultNightMode(settings.theme)
        }
    }
}
