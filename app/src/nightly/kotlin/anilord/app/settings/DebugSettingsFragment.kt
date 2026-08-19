package anilord.app.settings

import android.os.Bundle
import anilord.app.R
import anilord.app.core.ui.BasePreferenceFragment
class DebugSettingsFragment : BasePreferenceFragment(R.string.debug) {

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_debug)
	}

}
