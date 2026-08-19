package anilord.app.tracker.ui.feed.adapter

import android.content.Context
import anilord.app.core.ui.BaseListAdapter
import anilord.app.core.ui.list.OnListItemClickListener
import anilord.app.core.ui.list.fastscroll.FastScroller
import anilord.app.list.ui.adapter.ListItemType
import anilord.app.list.ui.adapter.MangaListListener
import anilord.app.list.ui.adapter.emptyStateListAD
import anilord.app.list.ui.adapter.errorFooterAD
import anilord.app.list.ui.adapter.errorStateListAD
import anilord.app.list.ui.adapter.listHeaderAD
import anilord.app.list.ui.adapter.loadingFooterAD
import anilord.app.list.ui.adapter.loadingStateAD
import anilord.app.list.ui.adapter.quickFilterAD
import anilord.app.list.ui.model.ListModel
import anilord.app.list.ui.size.ItemSizeResolver
import anilord.app.tracker.ui.feed.model.FeedItem

class FeedAdapter(
	listener: MangaListListener,
	sizeResolver: ItemSizeResolver,
	feedClickListener: OnListItemClickListener<FeedItem>,
) : BaseListAdapter<ListModel>(), FastScroller.SectionIndexer {

	init {
		addDelegate(ListItemType.FEED, feedItemAD(feedClickListener))
		addDelegate(
			ListItemType.MANGA_NESTED_GROUP,
			updatedMangaAD(
				sizeResolver = sizeResolver,
				listener = listener,
				headerClickListener = listener,
			),
		)
		addDelegate(ListItemType.FOOTER_LOADING, loadingFooterAD())
		addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
		addDelegate(ListItemType.FOOTER_ERROR, errorFooterAD(listener))
		addDelegate(ListItemType.STATE_ERROR, errorStateListAD(listener))
		addDelegate(ListItemType.HEADER, listHeaderAD(listener))
		addDelegate(ListItemType.STATE_EMPTY, emptyStateListAD(listener))
		addDelegate(ListItemType.QUICK_FILTER, quickFilterAD(listener))
	}

	override fun getSectionText(context: Context, position: Int): CharSequence? {
		return findHeader(position)?.getText(context)
	}
}
