package org.manga.peak.history.ui

import android.content.Context
import org.manga.peak.core.ui.list.fastscroll.FastScroller
import org.manga.peak.list.ui.adapter.MangaListAdapter
import org.manga.peak.list.ui.adapter.MangaListListener
import org.manga.peak.list.ui.size.ItemSizeResolver

class HistoryListAdapter(
	listener: MangaListListener,
	sizeResolver: ItemSizeResolver,
) : MangaListAdapter(listener, sizeResolver), FastScroller.SectionIndexer {

	override fun getSectionText(context: Context, position: Int): CharSequence? {
		return findHeader(position)?.getText(context)
	}
}
