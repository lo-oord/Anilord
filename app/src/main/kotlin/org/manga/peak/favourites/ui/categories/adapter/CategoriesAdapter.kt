package org.manga.peak.favourites.ui.categories.adapter

import org.manga.peak.core.ui.ReorderableListAdapter
import org.manga.peak.favourites.ui.categories.FavouriteCategoriesListListener
import org.manga.peak.list.ui.adapter.ListItemType
import org.manga.peak.list.ui.adapter.ListStateHolderListener
import org.manga.peak.list.ui.adapter.emptyStateListAD
import org.manga.peak.list.ui.adapter.loadingStateAD
import org.manga.peak.list.ui.model.ListModel

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
