package anilord.app.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.inappmessaging.FirebaseInAppMessaging
import com.google.firebase.messaging.FirebaseMessaging
import anilord.app.BuildConfig

object FirebaseInitializer {

	fun initialize(context: Context) {
		if (runCatching { FirebaseApp.getInstance() }.getOrNull() == null &&
			FirebaseApp.initializeApp(context) == null
		) {
			return
		}
		FirebaseAnalytics.getInstance(context).apply {
			setAnalyticsCollectionEnabled(true)
			setUserProperty(USER_PROPERTY_BUILD_TYPE, BuildConfig.BUILD_TYPE)
		}
		FirebaseCrashlytics.getInstance().apply {
			setCrashlyticsCollectionEnabled(true)
			setCustomKey(KEY_BUILD_TYPE, BuildConfig.BUILD_TYPE)
			setCustomKey(KEY_VERSION_CODE, BuildConfig.VERSION_CODE)
			setCustomKey(KEY_VERSION_NAME, BuildConfig.VERSION_NAME)
		}
		FirebaseMessaging.getInstance().isAutoInitEnabled = true
		FirebaseInAppMessaging.getInstance().setAutomaticDataCollectionEnabled(true)
		MangaPeakMessagingService.createNotificationChannel(context)
	}

	private const val USER_PROPERTY_BUILD_TYPE = "app_variant"
	private const val KEY_BUILD_TYPE = "build_type"
	private const val KEY_VERSION_CODE = "version_code"
	private const val KEY_VERSION_NAME = "version_name"
}
