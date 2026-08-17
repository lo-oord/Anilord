package org.manga.peak.tracker.ui.feed.adapter

import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.manga.peak.R
import org.manga.peak.core.ui.BaseListAdapter
import org.manga.peak.core.ui.list.OnListItemClickListener
import org.manga.peak.databinding.ItemListGroupBinding
import org.manga.peak.list.ui.adapter.ListHeaderClickListener
import org.manga.peak.list.ui.adapter.ListItemType
import org.manga.peak.list.ui.adapter.mangaGridItemAD
import org.manga.peak.list.ui.model.ListHeader
import org.manga.peak.list.ui.model.ListModel
import org.manga.peak.list.ui.model.MangaListModel
import org.manga.peak.list.ui.size.ItemSizeResolver
import org.manga.peak.tracker.ui.feed.model.UpdatedMangaHeader

fun updatedMangaAD(
	sizeResolver: ItemSizeResolver,
	listener: OnListItemClickListener<MangaListModel>,
	headerClickListener: ListHeaderClickListener,
) = adapterDelegateViewBinding<UpdatedMangaHeader, ListModel, ItemListGroupBinding>(
	{ layoutInflater, parent -> ItemListGroupBinding.inflate(layoutInflater, parent, false) },
) {

	val adapter = BaseListAdapter<ListModel>()
		.addDelegate(ListItemType.MANGA_GRID, mangaGridItemAD(sizeResolver, listener))
	binding.recyclerView.adapter = adapter
	binding.buttonMore.setOnClickListener { v ->
		headerClickListener.onListHeaderClick(ListHeader(0, payload = item), v)
	}
	binding.textViewTitle.setText(R.string.updates)
	binding.buttonMore.setText(R.string.more)

	bind {
		adapter.items = item.list
	}
}
