package org.manga.peak.settings.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.snackbar.Snackbar
import org.manga.peak.R
import org.manga.peak.core.prefs.AppSettings
import org.manga.peak.core.util.ext.powerManager

class DozeHelper(
	private val fragment: PreferenceFragmentCompat,
) {

	private val startForDozeResult = fragment.registerForActivityResult(
		ActivityResultContracts.StartActivityForResult(),
	) {
		updatePreference()
	}

	fun updatePreference() {
		val preference = fragment.findPreference<Preference>(AppSettings.KEY_IGNORE_DOZE) ?: return
		preference.isVisible = isDozeIgnoreAvailable()
	}

	fun startIgnoreDoseActivity(): Boolean {
		val context = fragment.context ?: return false
		val powerManager = context.powerManager ?: return false
		return if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
			try {
				val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
				startForDozeResult.launch(intent)
				true
			} catch (e: ActivityNotFoundException) {
				Snackbar.make(fragment.listView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
				false
			}
		} else {
			false
		}
	}

	private fun isDozeIgnoreAvailable(): Boolean {
		val context = fragment.context ?: return false
		val packageName = context.packageName
		val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
		return !powerManager.isIgnoringBatteryOptimizations(packageName)
	}
}
