package org.manga.peak.main.domain

import android.app.Activity
import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallState
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import org.manga.peak.BuildConfig
import org.manga.peak.core.util.ext.printStackTraceDebug
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InAppUpdateCoordinator @Inject constructor(
	@ApplicationContext context: Context,
) : InstallStateUpdatedListener {

	private val updateManager: AppUpdateManager = AppUpdateManagerFactory.create(context)
	private var updateFlowStarted = false
	private var onUpdateDownloaded: (() -> Unit)? = null

	fun start(
		activity: Activity,
		launcher: ActivityResultLauncher<IntentSenderRequest>,
		onUpdateDownloaded: () -> Unit,
	) {
		if (!isPlayRelease()) {
			return
		}
		this.onUpdateDownloaded = onUpdateDownloaded
		updateManager.registerListener(this)
		checkForUpdate(activity, launcher, allowNewUpdate = true)
	}

	fun resume(
		activity: Activity,
		launcher: ActivityResultLauncher<IntentSenderRequest>,
	) {
		if (isPlayRelease()) {
			checkForUpdate(activity, launcher, allowNewUpdate = false)
		}
	}

	fun completeUpdate() {
		if (isPlayRelease()) {
			updateManager.completeUpdate()
		}
	}

	fun stop() {
		if (!isPlayRelease()) {
			return
		}
		updateManager.unregisterListener(this)
		onUpdateDownloaded = null
		updateFlowStarted = false
	}

	override fun onStateUpdate(state: InstallState) {
		if (state.installStatus() == InstallStatus.DOWNLOADED) {
			onUpdateDownloaded?.invoke()
		}
	}

	private fun checkForUpdate(
		activity: Activity,
		launcher: ActivityResultLauncher<IntentSenderRequest>,
		allowNewUpdate: Boolean,
	) {
		updateManager.appUpdateInfo
			.addOnSuccessListener { updateInfo ->
				if (activity.isFinishing || activity.isDestroyed) {
					return@addOnSuccessListener
				}
				when {
					updateInfo.installStatus() == InstallStatus.DOWNLOADED -> {
						onUpdateDownloaded?.invoke()
					}

					allowNewUpdate &&
						!updateFlowStarted &&
						updateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
						updateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> {
						updateFlowStarted = true
						runCatching {
							val started = updateManager.startUpdateFlowForResult(
								updateInfo,
								launcher,
								AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
							)
							if (!started) {
								updateFlowStarted = false
							}
						}.onFailure {
							updateFlowStarted = false
							it.printStackTraceDebug()
						}
					}
				}
			}
			.addOnFailureListener { it.printStackTraceDebug() }
	}

	private fun isPlayRelease(): Boolean = BuildConfig.BUILD_TYPE == "release"
}
