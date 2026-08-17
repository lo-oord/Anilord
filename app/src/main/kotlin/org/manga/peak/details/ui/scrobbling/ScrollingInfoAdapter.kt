package org.manga.peak.details.ui.scrobbling

import org.manga.peak.core.nav.AppRouter
import org.manga.peak.core.ui.BaseListAdapter
import org.manga.peak.list.ui.model.ListModel

class ScrollingInfoAdapter(
	router: AppRouter,
) : BaseListAdapter<ListModel>() {

	init {
		delegatesManager.addDelegate(scrobblingInfoAD(router))
	}
}
