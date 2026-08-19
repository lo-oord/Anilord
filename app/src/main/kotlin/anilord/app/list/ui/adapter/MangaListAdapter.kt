package anilord.app.list.ui.adapter

import androidx.recyclerview.widget.RecyclerView
import anilord.app.core.ui.BaseListAdapter
import anilord.app.list.ui.model.ListModel
import anilord.app.list.ui.model.MangaListModel
import anilord.app.list.ui.size.ItemSizeResolver

open class MangaListAdapter(
	listener: MangaListListener,
	sizeResolver: ItemSizeResolver,
) : BaseListAdapter<ListModel>() {


	init {
		addDelegate(ListItemType.MANGA_LIST, mangaListItemAD(listener))
		addDelegate(ListItemType.MANGA_LIST_DETAILED, mangaListDetailedItemAD(listener))
		addDelegate(ListItemType.MANGA_GRID, mangaGridItemAD(sizeResolver, listener))
		addDelegate(ListItemType.FOOTER_LOADING, loadingFooterAD())
		addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
		addDelegate(ListItemType.STATE_ERROR, errorStateListAD(listener))
		addDelegate(ListItemType.FOOTER_ERROR, errorFooterAD(listener))
		addDelegate(ListItemType.STATE_EMPTY, emptyStateListAD(listener))
		addDelegate(ListItemType.HINT_EMPTY, emptyHintAD(listener))
		addDelegate(ListItemType.HEADER, listHeaderAD(listener))
		addDelegate(ListItemType.QUICK_FILTER, quickFilterAD(listener))
		addDelegate(ListItemType.TIP, tipAD(listener))
		addDelegate(ListItemType.INFO, infoAD())
		addDelegate(ListItemType.FOOTER_BUTTON, buttonFooterAD(listener))
	}

	override suspend fun emit(value: List<ListModel>?) {
		super.emit(value)
	}

	override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
		super.onDetachedFromRecyclerView(recyclerView)
	}


}
