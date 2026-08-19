package anilord.app.favourites.ui.categories.select.adapter

import anilord.app.core.ui.BaseListAdapter
import anilord.app.core.ui.list.OnListItemClickListener
import anilord.app.favourites.ui.categories.select.model.MangaCategoryItem
import anilord.app.list.ui.adapter.ListItemType
import anilord.app.list.ui.adapter.emptyStateListAD
import anilord.app.list.ui.adapter.loadingStateAD
import anilord.app.list.ui.model.ListModel

class MangaCategoriesAdapter(
	clickListener: OnListItemClickListener<MangaCategoryItem>,
) : BaseListAdapter<ListModel>() {

	init {
		addDelegate(ListItemType.NAV_ITEM, mangaCategoryAD(clickListener))
		addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
		addDelegate(ListItemType.STATE_EMPTY, emptyStateListAD(null))
	}
}
