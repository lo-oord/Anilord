package org.manga.peak.download.ui.list

import androidx.lifecycle.LifecycleOwner
import org.manga.peak.core.ui.BaseListAdapter
import org.manga.peak.list.ui.adapter.ListItemType
import org.manga.peak.list.ui.adapter.emptyStateListAD
import org.manga.peak.list.ui.adapter.listHeaderAD
import org.manga.peak.list.ui.adapter.loadingStateAD
import org.manga.peak.list.ui.model.ListModel

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
