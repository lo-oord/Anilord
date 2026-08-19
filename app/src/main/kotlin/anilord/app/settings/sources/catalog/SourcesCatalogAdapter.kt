package anilord.app.settings.sources.catalog

import android.content.Context
import anilord.app.core.model.getTitle
import anilord.app.core.ui.BaseListAdapter
import anilord.app.core.ui.list.OnListItemClickListener
import anilord.app.core.ui.list.fastscroll.FastScroller
import anilord.app.list.ui.adapter.ListItemType
import anilord.app.list.ui.adapter.loadingStateAD
import anilord.app.list.ui.model.ListModel

class SourcesCatalogAdapter(
	listener: OnListItemClickListener<SourceCatalogItem.Source>,
) : BaseListAdapter<ListModel>(), FastScroller.SectionIndexer {

	init {
		addDelegate(ListItemType.CHAPTER_LIST, sourceCatalogItemSourceAD(listener))
		addDelegate(ListItemType.HINT_EMPTY, sourceCatalogItemHintAD())
		addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
	}

	override fun getSectionText(context: Context, position: Int): CharSequence? {
		return (items.getOrNull(position) as? SourceCatalogItem.Source)?.source?.getTitle(context)?.take(1)
	}
}
