package anilord.app.bookmarks.ui.adapter

import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import anilord.app.bookmarks.domain.Bookmark
import anilord.app.core.ui.list.AdapterDelegateClickListenerAdapter
import anilord.app.core.ui.list.OnListItemClickListener
import anilord.app.databinding.ItemBookmarkLargeBinding
import anilord.app.list.ui.model.ListModel

fun bookmarkLargeAD(
	clickListener: OnListItemClickListener<Bookmark>,
) = adapterDelegateViewBinding<Bookmark, ListModel, ItemBookmarkLargeBinding>(
	{ inflater, parent -> ItemBookmarkLargeBinding.inflate(inflater, parent, false) },
) {
	AdapterDelegateClickListenerAdapter(this, clickListener).attach(itemView)

	bind {
		binding.imageViewThumb.setImageAsync(item)
		binding.progressView.setProgress(item.percent, false)
	}
}
