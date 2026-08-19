package anilord.app.download.ui.worker

import android.view.View
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.FlowCollector
import anilord.app.R
import anilord.app.core.nav.AppRouter
import anilord.app.core.util.ext.findActivity
import anilord.app.main.ui.owners.BottomNavOwner

class DownloadStartedObserver(
	private val snackbarHost: View,
) : FlowCollector<Unit> {

	override suspend fun emit(value: Unit) {
		val snackbar = Snackbar.make(snackbarHost, R.string.download_started, Snackbar.LENGTH_LONG)
		(snackbarHost.context.findActivity() as? BottomNavOwner)?.let {
			snackbar.anchorView = it.bottomNav
		}
		val router = AppRouter.from(snackbarHost)
		if (router != null) {
			snackbar.setAction(R.string.details) { router.openDownloads() }
		}
		snackbar.show()
	}
}
