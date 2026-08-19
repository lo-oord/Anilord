package anilord.app.settings

import android.os.Bundle
import android.content.Intent
import android.view.View
import androidx.annotation.StringRes
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import anilord.app.R
import anilord.app.core.nav.router
import anilord.app.core.prefs.AppSettings
import anilord.app.core.ui.BasePreferenceFragment
import anilord.app.core.util.ext.addMenuProvider
import anilord.app.core.util.ext.getQuantityStringSafe
import anilord.app.core.util.ext.observe
import anilord.app.settings.search.SettingsSearchMenuProvider
import anilord.app.settings.search.SettingsSearchViewModel

@AndroidEntryPoint
class RootSettingsFragment : BasePreferenceFragment(0) {

	private val viewModel: RootSettingsViewModel by viewModels()
	private val activityViewModel: SettingsSearchViewModel by activityViewModels()

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_root)
		bindPreferenceSummary("appearance", R.string.theme, R.string.list_mode, R.string.language)
		bindPreferenceSummary("reader", R.string.read_mode, R.string.scale_mode, R.string.switch_pages)
		bindPreferenceSummary("network", R.string.storage_usage, R.string.proxy, R.string.prefetch_content)
		bindPreferenceSummary("userdata", R.string.create_or_restore_backup, R.string.periodic_backups)
		bindPreferenceSummary("downloads", R.string.manga_save_location, R.string.downloads_wifi_only)
		bindPreferenceSummary("tracker", R.string.track_sources, R.string.notifications_settings)
		bindPreferenceSummary("services", R.string.suggestions, R.string.sync, R.string.tracking)
		findPreference<Preference>("about")?.summary = getString(R.string.about_display_version)
		updateAccountSummary()
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean {
		when (preference.key) {
			"account" -> {
				startActivity(Intent(requireContext(), anilord.app.settings.account.FirebaseAccountActivity::class.java))
				return true
			}

			"explore" -> {
				router.openExplore()
				requireActivity().finish()
				return true
			}
		}
		return super.onPreferenceTreeClick(preference)
	}

	override fun onResume() {
		super.onResume()
		updateAccountSummary()
	}

	private fun updateAccountSummary() {
		val user = FirebaseApp.getApps(requireContext()).firstOrNull()?.let(FirebaseAuth::getInstance)?.currentUser
		findPreference<Preference>("account")?.summary = when {
			user == null -> getString(R.string.account_sync_title)
			user.isAnonymous -> getString(R.string.account_guest_status)
			else -> user.email ?: getString(R.string.account_connected_status)
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		findPreference<Preference>(AppSettings.KEY_REMOTE_SOURCES)?.let { pref ->
			val total = viewModel.totalSourcesCount
			viewModel.enabledSourcesCount.observe(viewLifecycleOwner) {
				pref.summary = if (it >= 0) {
					getString(R.string.enabled_d_of_d, it, total)
				} else {
					resources.getQuantityStringSafe(R.plurals.items, total, total)
				}
			}
		}
		addMenuProvider(SettingsSearchMenuProvider(activityViewModel))
	}

	override fun setTitle(title: CharSequence?) {
		if (!resources.getBoolean(R.bool.is_tablet)) {
			super.setTitle(title)
		}
	}

	private fun bindPreferenceSummary(key: String, @StringRes vararg items: Int) {
		findPreference<Preference>(key)?.summary = items.joinToString { getString(it) }
	}
}
