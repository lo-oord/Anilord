package anilord.app.scrobbling.common.ui.config.adapter

import anilord.app.core.ui.BaseListAdapter
import anilord.app.core.ui.list.OnListItemClickListener
import anilord.app.list.ui.adapter.ListItemType
import anilord.app.list.ui.adapter.emptyStateListAD
import anilord.app.list.ui.model.ListModel
import anilord.app.scrobbling.common.domain.model.ScrobblingInfo

class ScrobblingMangaAdapter(
	clickListener: OnListItemClickListener<ScrobblingInfo>,
) : BaseListAdapter<ListModel>() {

	init {
		addDelegate(ListItemType.HEADER, scrobblingHeaderAD())
		addDelegate(ListItemType.STATE_EMPTY, emptyStateListAD(null))
		addDelegate(ListItemType.MANGA_SCROBBLING, scrobblingMangaAD(clickListener))
	}
}
