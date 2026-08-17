package org.manga.peak.scrobbling.common.ui.config.adapter

import org.manga.peak.core.ui.BaseListAdapter
import org.manga.peak.core.ui.list.OnListItemClickListener
import org.manga.peak.list.ui.adapter.ListItemType
import org.manga.peak.list.ui.adapter.emptyStateListAD
import org.manga.peak.list.ui.model.ListModel
import org.manga.peak.scrobbling.common.domain.model.ScrobblingInfo

class ScrobblingMangaAdapter(
	clickListener: OnListItemClickListener<ScrobblingInfo>,
) : BaseListAdapter<ListModel>() {

	init {
		addDelegate(ListItemType.HEADER, scrobblingHeaderAD())
		addDelegate(ListItemType.STATE_EMPTY, emptyStateListAD(null))
		addDelegate(ListItemType.MANGA_SCROBBLING, scrobblingMangaAD(clickListener))
	}
}
