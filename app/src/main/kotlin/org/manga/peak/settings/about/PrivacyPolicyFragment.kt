package org.manga.peak.settings.about

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.manga.peak.R
import org.manga.peak.settings.SettingsActivity

class PrivacyPolicyFragment : Fragment(R.layout.fragment_privacy_policy) {

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		(activity as? SettingsActivity)?.setSectionTitle(getString(R.string.privacy))
		ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
			val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
			target.setPadding(
				target.paddingLeft,
				target.paddingTop,
				target.paddingRight,
				32 + bars.bottom,
			)
			insets
		}
		ViewCompat.requestApplyInsets(view)
	}
}
