package org.manga.peak.main.domain

import android.app.Activity
import android.content.Context
import androidx.core.content.edit
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import org.manga.peak.BuildConfig
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InAppReviewCoordinator @Inject constructor(
	@ApplicationContext context: Context,
) {

	private val appContext = context.applicationContext
	private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
	private var requestInFlight = false

	fun recordColdLaunch(now: Long = System.currentTimeMillis()) {
		preferences.edit {
			if (!preferences.contains(KEY_FIRST_LAUNCH_AT)) {
				putLong(KEY_FIRST_LAUNCH_AT, now)
			}
			putInt(KEY_LAUNCH_COUNT, preferences.getInt(KEY_LAUNCH_COUNT, 0) + 1)
		}
	}

	fun launchIfEligible(activity: Activity, now: Long = System.currentTimeMillis()) {
		if (requestInFlight || !isEligible(
			launchCount = preferences.getInt(KEY_LAUNCH_COUNT, 0),
			firstLaunchAt = preferences.getLong(KEY_FIRST_LAUNCH_AT, now),
			lastAttemptAt = preferences.getLong(KEY_LAST_ATTEMPT_AT, 0L),
			attemptedVersion = preferences.getInt(KEY_ATTEMPTED_VERSION, 0),
			currentVersion = BuildConfig.VERSION_CODE,
			now = now,
		)) {
			return
		}
		requestInFlight = true
		val reviewManager = ReviewManagerFactory.create(appContext)
		reviewManager.requestReviewFlow().addOnCompleteListener { request ->
			if (!request.isSuccessful || activity.isFinishing || activity.isDestroyed) {
				requestInFlight = false
				return@addOnCompleteListener
			}
			reviewManager.launchReviewFlow(activity, request.result).addOnCompleteListener {
				preferences.edit {
					putLong(KEY_LAST_ATTEMPT_AT, System.currentTimeMillis())
					putInt(KEY_ATTEMPTED_VERSION, BuildConfig.VERSION_CODE)
				}
				requestInFlight = false
			}
		}
	}

	internal companion object {
		private const val PREFERENCES_NAME = "in_app_review"
		private const val KEY_FIRST_LAUNCH_AT = "first_launch_at"
		private const val KEY_LAUNCH_COUNT = "launch_count"
		private const val KEY_LAST_ATTEMPT_AT = "last_attempt_at"
		private const val KEY_ATTEMPTED_VERSION = "attempted_version"
		private const val MIN_LAUNCH_COUNT = 7
		private val MIN_INSTALL_AGE_MS = TimeUnit.DAYS.toMillis(3)
		private val MIN_ATTEMPT_INTERVAL_MS = TimeUnit.DAYS.toMillis(120)

		fun isEligible(
			launchCount: Int,
			firstLaunchAt: Long,
			lastAttemptAt: Long,
			attemptedVersion: Int,
			currentVersion: Int,
			now: Long,
		): Boolean = launchCount >= MIN_LAUNCH_COUNT &&
			now - firstLaunchAt >= MIN_INSTALL_AGE_MS &&
			(lastAttemptAt == 0L || now - lastAttemptAt >= MIN_ATTEMPT_INTERVAL_MS) &&
			attemptedVersion != currentVersion
	}
}
