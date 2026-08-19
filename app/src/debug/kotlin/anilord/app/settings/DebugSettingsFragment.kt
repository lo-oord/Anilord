package anilord.app.settings

import android.os.Bundle
import androidx.preference.Preference
import anilord.app.R
import anilord.app.core.model.TestMangaSource
import anilord.app.core.nav.router
import anilord.app.core.ui.BasePreferenceFragment
import org.koitharu.workinspector.WorkInspector

class DebugSettingsFragment : BasePreferenceFragment(R.string.debug) {

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_debug)
	}


	override fun onPreferenceTreeClick(preference: Preference): Boolean = when (preference.key) {
		KEY_WORK_INSPECTOR -> {
			startActivity(WorkInspector.getIntent(preference.context))
			true
		}

		KEY_TEST_PARSER -> {
			router.openList(TestMangaSource, null, null)
			true
		}

		else -> super.onPreferenceTreeClick(preference)
	}

	private companion object {

		const val KEY_WORK_INSPECTOR = "work_inspector"
		const val KEY_TEST_PARSER = "test_parser"
	}
}
