package org.manga.peak.settings.storage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hannesdorfmann.adapterdelegates4.AsyncListDifferDelegationAdapter
import dagger.hilt.android.AndroidEntryPoint
import org.manga.peak.R
import org.manga.peak.core.exceptions.resolve.ToastErrorObserver
import org.manga.peak.core.ui.AlertDialogFragment
import org.manga.peak.core.ui.list.OnListItemClickListener
import org.manga.peak.core.util.ext.observe
import org.manga.peak.core.util.ext.observeEvent
import org.manga.peak.databinding.DialogDirectorySelectBinding

@AndroidEntryPoint
class MangaDirectorySelectDialog : AlertDialogFragment<DialogDirectorySelectBinding>(),
	OnListItemClickListener<DirectoryModel> {

	private val viewModel: MangaDirectorySelectViewModel by viewModels()

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): DialogDirectorySelectBinding {
		return DialogDirectorySelectBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: DialogDirectorySelectBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		val adapter = AsyncListDifferDelegationAdapter(DirectoryDiffCallback(), directoryAD(this))
		binding.root.adapter = adapter
		viewModel.items.observe(viewLifecycleOwner) { adapter.items = it }
		viewModel.onDismissDialog.observeEvent(viewLifecycleOwner) { dismiss() }
		viewModel.onError.observeEvent(viewLifecycleOwner, ToastErrorObserver(binding.root, this))
	}

	override fun onBuildDialog(builder: MaterialAlertDialogBuilder): MaterialAlertDialogBuilder {
		return super.onBuildDialog(builder)
			.setCancelable(true)
			.setTitle(R.string.manga_save_location)
			.setNegativeButton(android.R.string.cancel, null)
	}

	override fun onItemClick(item: DirectoryModel, view: View) {
		viewModel.onItemClick(item)
	}

}
