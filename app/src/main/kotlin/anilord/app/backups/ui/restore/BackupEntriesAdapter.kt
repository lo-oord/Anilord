package anilord.app.backups.ui.restore

import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import anilord.app.core.ui.BaseListAdapter
import anilord.app.core.ui.list.OnListItemClickListener
import anilord.app.core.util.ext.setChecked
import anilord.app.databinding.ItemCheckableMultipleBinding
import anilord.app.list.ui.ListModelDiffCallback.Companion.PAYLOAD_CHECKED_CHANGED
import anilord.app.list.ui.adapter.ListItemType

class BackupSectionsAdapter(
	clickListener: OnListItemClickListener<BackupSectionModel>,
) : BaseListAdapter<BackupSectionModel>() {

	init {
		addDelegate(ListItemType.NAV_ITEM, backupSectionAD(clickListener))
	}
}

private fun backupSectionAD(
	clickListener: OnListItemClickListener<BackupSectionModel>,
) = adapterDelegateViewBinding<BackupSectionModel, BackupSectionModel, ItemCheckableMultipleBinding>(
	{ layoutInflater, parent -> ItemCheckableMultipleBinding.inflate(layoutInflater, parent, false) },
) {

	binding.root.setOnClickListener { v ->
		clickListener.onItemClick(item, v)
	}

	bind { payloads ->
		with(binding.root) {
			setText(item.titleResId)
			setChecked(item.isChecked, PAYLOAD_CHECKED_CHANGED in payloads)
			isEnabled = item.isEnabled
		}
	}
}
