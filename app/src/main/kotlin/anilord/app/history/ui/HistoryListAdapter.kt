package anilord.app.history.ui

import android.content.Context
import anilord.app.core.ui.list.fastscroll.FastScroller
import anilord.app.list.ui.adapter.MangaListAdapter
import anilord.app.list.ui.adapter.MangaListListener
import anilord.app.list.ui.size.ItemSizeResolver

class HistoryListAdapter(
	listener: MangaListListener,
	sizeResolver: ItemSizeResolver,
) : MangaListAdapter(listener, sizeResolver), FastScroller.SectionIndexer {

	override fun getSectionText(context: Context, position: Int): CharSequence? {
		return findHeader(position)?.getText(context)
	}
}
