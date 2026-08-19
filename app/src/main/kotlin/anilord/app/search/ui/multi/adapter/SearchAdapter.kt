package anilord.app.search.ui.multi.adapter

import android.content.Context
import androidx.recyclerview.widget.RecyclerView.RecycledViewPool
import anilord.app.core.ui.BaseListAdapter
import anilord.app.core.ui.list.OnListItemClickListener
import anilord.app.core.ui.list.fastscroll.FastScroller
import anilord.app.list.ui.MangaSelectionDecoration
import anilord.app.list.ui.adapter.ListItemType
import anilord.app.list.ui.adapter.MangaListListener
import anilord.app.list.ui.adapter.buttonFooterAD
import anilord.app.list.ui.adapter.emptyStateListAD
import anilord.app.list.ui.adapter.errorStateListAD
import anilord.app.list.ui.adapter.loadingFooterAD
import anilord.app.list.ui.adapter.loadingStateAD
import anilord.app.list.ui.model.ListModel
import anilord.app.list.ui.size.ItemSizeResolver
import anilord.app.search.ui.multi.SearchResultsListModel

class SearchAdapter(
	listener: MangaListListener,
	itemClickListener: OnListItemClickListener<SearchResultsListModel>,
	sizeResolver: ItemSizeResolver,
	selectionDecoration: MangaSelectionDecoration,
) : BaseListAdapter<ListModel>(), FastScroller.SectionIndexer {

	init {
		val pool = RecycledViewPool()
		addDelegate(
			ListItemType.MANGA_NESTED_GROUP,
			searchResultsAD(
				sharedPool = pool,
				sizeResolver = sizeResolver,
				selectionDecoration = selectionDecoration,
				listener = listener,
				itemClickListener = itemClickListener,
			),
		)
		addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
		addDelegate(ListItemType.FOOTER_LOADING, loadingFooterAD())
		addDelegate(ListItemType.STATE_EMPTY, emptyStateListAD(listener))
		addDelegate(ListItemType.STATE_ERROR, errorStateListAD(listener))
		addDelegate(ListItemType.FOOTER_BUTTON, buttonFooterAD(listener))
	}

	override fun getSectionText(context: Context, position: Int): CharSequence? {
		return (items.getOrNull(position) as? SearchResultsListModel)?.getTitle(context)
	}
}
