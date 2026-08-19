package anilord.app.download.ui.list

import androidx.lifecycle.LifecycleOwner
import anilord.app.core.ui.BaseListAdapter
import anilord.app.list.ui.adapter.ListItemType
import anilord.app.list.ui.adapter.emptyStateListAD
import anilord.app.list.ui.adapter.listHeaderAD
import anilord.app.list.ui.adapter.loadingStateAD
import anilord.app.list.ui.model.ListModel

class DownloadsAdapter(
	lifecycleOwner: LifecycleOwner,
	listener: DownloadItemListener,
) : BaseListAdapter<ListModel>() {

	init {
		addDelegate(ListItemType.DOWNLOAD, downloadItemAD(lifecycleOwner, listener))
		addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
		addDelegate(ListItemType.STATE_EMPTY, emptyStateListAD(null))
		addDelegate(ListItemType.HEADER, listHeaderAD(null))
	}
}
