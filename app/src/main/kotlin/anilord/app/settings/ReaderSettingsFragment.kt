package anilord.app.settings

import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import anilord.app.R
import anilord.app.core.model.ZoomMode
import anilord.app.core.nav.router
import anilord.app.core.prefs.AppSettings
import anilord.app.core.prefs.EInkFlashColor
import anilord.app.core.prefs.ReaderAnimation
import anilord.app.core.prefs.ReaderBackground
import anilord.app.core.prefs.ReaderControl
import anilord.app.core.prefs.ReaderMode
import anilord.app.core.ui.BasePreferenceFragment
import anilord.app.core.util.ext.getQuantityStringSafe
import anilord.app.core.util.ext.setDefaultValueCompat
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.names
import anilord.app.settings.utils.MultiSummaryProvider
import anilord.app.settings.utils.PercentSummaryProvider
import anilord.app.settings.utils.SliderPreference

@AndroidEntryPoint
class ReaderSettingsFragment :
	BasePreferenceFragment(R.string.reader_settings),
	SharedPreferences.OnSharedPreferenceChangeListener {

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_reader)
		findPreference<ListPreference>(AppSettings.KEY_READER_MODE)?.run {
			entryValues = ReaderMode.entries.names()
			setDefaultValueCompat(ReaderMode.STANDARD.name)
		}
		findPreference<ListPreference>(AppSettings.KEY_READER_ORIENTATION)?.run {
			entryValues = arrayOf(
				ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED.toString(),
				ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR.toString(),
				ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT.toString(),
				ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE.toString(),
			)
			setDefaultValueCompat(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED.toString())
		}
		findPreference<MultiSelectListPreference>(AppSettings.KEY_READER_CONTROLS)?.run {
			entryValues = ReaderControl.entries.names()
			setDefaultValueCompat(ReaderControl.DEFAULT.mapToSet { it.name })
			summaryProvider = MultiSummaryProvider(R.string.none)
		}
		findPreference<ListPreference>(AppSettings.KEY_READER_BACKGROUND)?.run {
			entryValues = ReaderBackground.entries.names()
			setDefaultValueCompat(ReaderBackground.DEFAULT.name)
		}
		findPreference<ListPreference>(AppSettings.KEY_READER_ANIMATION)?.run {
			entryValues = ReaderAnimation.entries.names()
			setDefaultValueCompat(ReaderAnimation.DEFAULT.name)
		}
		findPreference<ListPreference>(AppSettings.KEY_ZOOM_MODE)?.run {
			entryValues = ZoomMode.entries.names()
			setDefaultValueCompat(ZoomMode.FIT_CENTER.name)
		}
		findPreference<MultiSelectListPreference>(AppSettings.KEY_READER_CROP)?.run {
			summaryProvider = MultiSummaryProvider(R.string.disabled)
		}
		findPreference<SliderPreference>(AppSettings.KEY_WEBTOON_ZOOM_OUT)?.summaryProvider = PercentSummaryProvider()
		findPreference<SliderPreference>(AppSettings.KEY_EINK_FLASH_DURATION)?.summaryProvider =
			FlashDurationSummaryProvider
		findPreference<SliderPreference>(AppSettings.KEY_EINK_FLASH_EVERY)?.summaryProvider =
			FlashEverySummaryProvider
		findPreference<ListPreference>(AppSettings.KEY_EINK_FLASH_COLOR)?.run {
			entries = arrayOf(
				getString(R.string.color_white),
				getString(R.string.color_black),
			)
			entryValues = EInkFlashColor.entries.names()
			setDefaultValueCompat(EInkFlashColor.WHITE.name)
		}
		updateReaderModeDependency()
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		settings.subscribe(this)
	}

	override fun onDestroyView() {
		settings.unsubscribe(this)
		super.onDestroyView()
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean {
		return when (preference.key) {
			AppSettings.KEY_READER_TAP_ACTIONS -> {
				router.openReaderTapGridSettings()
				true
			}

			else -> super.onPreferenceTreeClick(preference)
		}
	}

	override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
		when (key) {
			AppSettings.KEY_READER_MODE -> updateReaderModeDependency()
		}
	}

	private fun updateReaderModeDependency() {
		findPreference<Preference>(AppSettings.KEY_READER_MODE_DETECT)?.run {
			isEnabled = settings.defaultReaderMode != ReaderMode.WEBTOON
		}
	}

	private object FlashDurationSummaryProvider : Preference.SummaryProvider<SliderPreference> {

		override fun provideSummary(preference: SliderPreference): CharSequence {
			return preference.context.getString(R.string.milliseconds_pattern, preference.value)
		}
	}

	private object FlashEverySummaryProvider : Preference.SummaryProvider<SliderPreference> {

		override fun provideSummary(preference: SliderPreference): CharSequence {
			val value = preference.value
			return preference.context.resources.getQuantityStringSafe(R.plurals.pages, value, value)
		}
	}
}
