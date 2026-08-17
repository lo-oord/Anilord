package org.manga.peak.scrobbling.common.ui.selector.adapter

import org.manga.peak.core.ui.BaseListAdapter
import org.manga.peak.core.ui.list.OnListItemClickListener
import org.manga.peak.list.ui.adapter.ListItemType
import org.manga.peak.list.ui.adapter.ListStateHolderListener
import org.manga.peak.list.ui.adapter.loadingFooterAD
import org.manga.peak.list.ui.adapter.loadingStateAD
import org.manga.peak.list.ui.model.ListModel
import org.manga.peak.scrobbling.common.domain.model.ScrobblerManga

class ScrobblerSelectorAdapter(
	clickListener: OnListItemClickListener<ScrobblerManga>,
	stateHolderListener: ListStateHolderListener,
) : BaseListAdapter<ListModel>() {

	init {
		addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
		addDelegate(ListItemType.MANGA_SCROBBLING, scrobblingMangaAD(clickListener))
		addDelegate(ListItemType.FOOTER_LOADING, loadingFooterAD())
		addDelegate(ListItemType.HINT_EMPTY, scrobblerHintAD(stateHolderListener))
	}
}
