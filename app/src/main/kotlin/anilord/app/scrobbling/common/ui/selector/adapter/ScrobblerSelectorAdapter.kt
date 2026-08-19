package anilord.app.scrobbling.common.ui.selector.adapter

import anilord.app.core.ui.BaseListAdapter
import anilord.app.core.ui.list.OnListItemClickListener
import anilord.app.list.ui.adapter.ListItemType
import anilord.app.list.ui.adapter.ListStateHolderListener
import anilord.app.list.ui.adapter.loadingFooterAD
import anilord.app.list.ui.adapter.loadingStateAD
import anilord.app.list.ui.model.ListModel
import anilord.app.scrobbling.common.domain.model.ScrobblerManga

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
