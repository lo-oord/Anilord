package org.manga.peak.favourites.ui.categories.select.adapter

import org.manga.peak.core.ui.BaseListAdapter
import org.manga.peak.core.ui.list.OnListItemClickListener
import org.manga.peak.favourites.ui.categories.select.model.MangaCategoryItem
import org.manga.peak.list.ui.adapter.ListItemType
import org.manga.peak.list.ui.adapter.emptyStateListAD
import org.manga.peak.list.ui.adapter.loadingStateAD
import org.manga.peak.list.ui.model.ListModel

class MangaCategoriesAdapter(
	clickListener: OnListItemClickListener<MangaCategoryItem>,
) : BaseListAdapter<ListModel>() {

	init {
		addDelegate(ListItemType.NAV_ITEM, mangaCategoryAD(clickListener))
		addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
		addDelegate(ListItemType.STATE_EMPTY, emptyStateListAD(null))
	}
}
