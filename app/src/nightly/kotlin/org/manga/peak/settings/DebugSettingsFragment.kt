package org.manga.peak.settings

import android.os.Bundle
import org.manga.peak.R
import org.manga.peak.core.ui.BasePreferenceFragment
class DebugSettingsFragment : BasePreferenceFragment(R.string.debug) {

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_debug)
	}

}
