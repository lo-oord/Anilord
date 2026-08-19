package anilord.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.StrictMode
import androidx.core.content.edit
import androidx.fragment.app.strictmode.FragmentStrictMode
import anilord.app.core.BaseApp

class KotatsuApp : BaseApp() {

	/**
	 * StrictMode violation notifications (visible on Android 11+) are disabled by
	 * default in this build so that normal usage of the app stays quiet. The full
	 * diagnostic policy is still applied when [isStrictModeEnabled] is opted in
	 * through the debug preferences; leaks are still logged to Logcat either way.
	 */
	var isStrictModeEnabled: Boolean
		get() = getDebugPreferences(this).getBoolean(KEY_STRICT_MODE, false)
		set(value) {
			getDebugPreferences(this).edit { putBoolean(KEY_STRICT_MODE, value) }
			enableStrictMode()
		}

	override fun attachBaseContext(base: Context) {
		super.attachBaseContext(base)
		enableStrictMode()
	}


	private fun enableStrictMode() {
		// Diagnostics still go to Logcat in every debug build, but visible
		// violation notifications are only attached when explicitly opted in.
		val notifier = if (isStrictModeEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			StrictModeNotifier(this)
		} else {
			null
		}
		StrictMode.setThreadPolicy(
			StrictMode.ThreadPolicy.Builder().apply {
			detectNetwork()
			detectDiskWrites()
			detectCustomSlowCalls()
			detectResourceMismatches()
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) detectUnbufferedIo()
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) detectExplicitGc()
			penaltyLog()
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && notifier != null) {
				penaltyListener(notifier.executor, notifier)
			}
		}.build(),
		)
		StrictMode.setVmPolicy(
			StrictMode.VmPolicy.Builder().apply {
			detectActivityLeaks()
			detectLeakedSqlLiteObjects()
			detectLeakedClosableObjects()
			detectLeakedRegistrationObjects()
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				detectContentUriWithoutPermission()
			}
			detectFileUriExposure()
			penaltyLog()
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && notifier != null) {
				penaltyListener(notifier.executor, notifier)
			}
		}.build(),
		)
		FragmentStrictMode.defaultPolicy = FragmentStrictMode.Policy.Builder().apply {
			detectWrongFragmentContainer()
			detectFragmentTagUsage()
			detectRetainInstanceUsage()
			detectSetUserVisibleHint()
			detectWrongNestedHierarchy()
			detectFragmentReuse()
			penaltyLog()
			if (notifier != null) {
				penaltyListener(notifier)
			}
		}.build()
	}

	private companion object {

		const val PREFS_DEBUG = "_debug"
		const val KEY_STRICT_MODE = "strict_mode"

		fun getDebugPreferences(context: Context): SharedPreferences =
			context.getSharedPreferences(PREFS_DEBUG, MODE_PRIVATE)
	}
}
