package anilord.app.details.ui.scrobbling

import anilord.app.core.nav.AppRouter
import anilord.app.core.ui.BaseListAdapter
import anilord.app.list.ui.model.ListModel

class ScrollingInfoAdapter(
	router: AppRouter,
) : BaseListAdapter<ListModel>() {

	init {
		delegatesManager.addDelegate(scrobblingInfoAD(router))
	}
}
