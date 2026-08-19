package anilord.app.favourites.ui.categories.adapter

import anilord.app.core.ui.ReorderableListAdapter
import anilord.app.favourites.ui.categories.FavouriteCategoriesListListener
import anilord.app.list.ui.adapter.ListItemType
import anilord.app.list.ui.adapter.ListStateHolderListener
import anilord.app.list.ui.adapter.emptyStateListAD
import anilord.app.list.ui.adapter.loadingStateAD
import anilord.app.list.ui.model.ListModel

class CategoriesAdapter(
	onItemClickListener: FavouriteCategoriesListListener,
	listListener: ListStateHolderListener,
) : ReorderableListAdapter<ListModel>() {

	init {
		addDelegate(ListItemType.CATEGORY_LARGE, categoryAD(onItemClickListener))
		addDelegate(ListItemType.NAV_ITEM, allCategoriesAD(onItemClickListener))
		addDelegate(ListItemType.STATE_EMPTY, emptyStateListAD(listListener))
		addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
	}
}
