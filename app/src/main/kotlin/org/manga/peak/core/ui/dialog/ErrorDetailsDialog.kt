package org.manga.peak.core.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.manga.peak.R
import org.manga.peak.core.nav.AppRouter
import org.manga.peak.core.nav.router
import org.manga.peak.core.ui.AlertDialogFragment
import org.manga.peak.core.util.ext.copyToClipboard
import org.manga.peak.core.util.ext.getCauseUrl
import org.manga.peak.core.util.ext.isHttpUrl
import org.manga.peak.core.util.ext.isReportable
import org.manga.peak.core.util.ext.report
import org.manga.peak.core.util.ext.requireSerializable
import org.manga.peak.core.util.ext.setTextAndVisible
import org.manga.peak.databinding.DialogErrorDetailsBinding

class ErrorDetailsDialog : AlertDialogFragment<DialogErrorDetailsBinding>(), View.OnClickListener {

	private lateinit var exception: Throwable

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val args = requireArguments()
		exception = args.requireSerializable(AppRouter.KEY_ERROR)
	}

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): DialogErrorDetailsBinding {
		return DialogErrorDetailsBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: DialogErrorDetailsBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		binding.buttonBrowser.setOnClickListener(this)
		binding.textViewSummary.text = exception.message
		val isUrlAvailable = exception.getCauseUrl()?.isHttpUrl() == true
		binding.buttonBrowser.isVisible = isUrlAvailable
		binding.textViewBrowser.isVisible = isUrlAvailable
		binding.textViewDescription.setTextAndVisible(
			if (exception.isReportable()) R.string.error_disclaimer_report else 0,
		)
	}

	@Suppress("NAME_SHADOWING")
	override fun onBuildDialog(builder: MaterialAlertDialogBuilder): MaterialAlertDialogBuilder {
		val builder = super.onBuildDialog(builder)
			.setCancelable(true)
			.setNegativeButton(R.string.close, null)
			.setTitle(R.string.error_details)
			.setNeutralButton(androidx.preference.R.string.copy) { _, _ ->
				context?.copyToClipboard(getString(R.string.error), exception.stackTraceToString())
			}
		if (exception.isReportable()) {
			builder.setPositiveButton(R.string.report) { _, _ ->
				exception.report(silent = true)
				dismiss()
			}
		}
		return builder
	}

	override fun onClick(v: View) {
		router.openBrowser(
			url = exception.getCauseUrl() ?: return,
			source = null,
			title = null,
		)
	}
}
