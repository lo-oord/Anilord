package anilord.app.settings

import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import anilord.app.R
import anilord.app.core.prefs.AppSettings
import anilord.app.core.ui.BasePreferenceFragment
import anilord.app.core.util.LocaleComparator
import anilord.app.core.util.ext.getLocalesConfig
import anilord.app.core.util.ext.setDefaultValueCompat
import anilord.app.core.util.ext.sortedWithSafe
import anilord.app.core.util.ext.toList
import anilord.app.settings.utils.ActivityListPreference
import org.koitharu.kotatsu.parsers.util.toTitleCase

@AndroidEntryPoint
class LanguageSettingsFragment :
    BasePreferenceFragment(R.string.language),
    SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_language)
        findPreference<ActivityListPreference>(AppSettings.KEY_APP_LOCALE)?.run {
            val locales = context.getLocalesConfig()
                .toList()
                .sortedWithSafe(LocaleComparator())
            entries = Array(locales.size + 1) { index ->
                if (index == 0) {
                    getString(R.string.follow_system)
                } else {
                    val locale = locales[index - 1]
                    locale.getDisplayName(locale).toTitleCase(locale)
                }
            }
            entryValues = Array(locales.size + 1) { index ->
                if (index == 0) "" else locales[index - 1].toLanguageTag()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activityIntent = android.content.Intent(
                    Settings.ACTION_APP_LOCALE_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                )
            }
            summaryProvider = Preference.SummaryProvider<ActivityListPreference> {
                val locale = AppCompatDelegate.getApplicationLocales().get(0)
                locale?.getDisplayName(locale)?.toTitleCase(locale)
                    ?: getString(R.string.follow_system)
            }
            setDefaultValueCompat("")
        }
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
        if (key == AppSettings.KEY_APP_LOCALE) {
            AppCompatDelegate.setApplicationLocales(settings.appLocales)
        }
    }
}
