package org.manga.peak.details.ui.adapter

import android.content.Context
import org.manga.peak.core.ui.BaseListAdapter
import org.manga.peak.core.ui.list.OnListItemClickListener
import org.manga.peak.core.ui.list.fastscroll.FastScroller
import org.manga.peak.details.ui.model.ChapterListItem
import org.manga.peak.list.ui.adapter.ListItemType
import org.manga.peak.list.ui.adapter.listHeaderAD
import org.manga.peak.list.ui.model.ListHeader
import org.manga.peak.list.ui.model.ListModel

class ChaptersAdapter(
	onItemClickListener: OnListItemClickListener<ChapterListItem>,
) : BaseListAdapter<ListModel>(), FastScroller.SectionIndexer {

	private var hasVolumes = false

	init {
		addDelegate(ListItemType.HEADER, listHeaderAD(null))
		addDelegate(ListItemType.CHAPTER_LIST, chapterListItemAD(onItemClickListener))
		addDelegate(ListItemType.CHAPTER_GRID, chapterGridItemAD(onItemClickListener))
	}

	override suspend fun emit(value: List<ListModel>?) {
		super.emit(value)
		hasVolumes = value != null && value.any { it is ListHeader }
	}

	override fun getSectionText(context: Context, position: Int): CharSequence? {
		return if (hasVolumes) {
			findHeader(position)?.getText(context)
		} else {
			val chapter = (items.getOrNull(position) as? ChapterListItem)?.chapter ?: return null
			chapter.numberString()
		}
	}
}
