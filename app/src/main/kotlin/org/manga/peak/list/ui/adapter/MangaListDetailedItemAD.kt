package org.manga.peak.list.ui.adapter

import androidx.core.view.isVisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.manga.peak.R
import org.manga.peak.core.ui.list.AdapterDelegateClickListenerAdapter
import org.manga.peak.core.util.ext.textAndVisible
import org.manga.peak.databinding.ItemMangaListDetailsBinding
import org.manga.peak.list.ui.ListModelDiffCallback
import org.manga.peak.list.ui.model.ListModel
import org.manga.peak.list.ui.model.MangaDetailedListModel

fun mangaListDetailedItemAD(
	clickListener: MangaDetailsClickListener,
) = adapterDelegateViewBinding<MangaDetailedListModel, ListModel, ItemMangaListDetailsBinding>(
	{ inflater, parent -> ItemMangaListDetailsBinding.inflate(inflater, parent, false) },
) {

	AdapterDelegateClickListenerAdapter(this, clickListener)
		.attach(itemView)

	bind { payloads ->
		binding.textViewTitle.text = item.title
		binding.textViewAuthor.textAndVisible = item.manga.authors.joinToString(", ")
		binding.progressView.setProgress(
			value = item.progress,
			animate = ListModelDiffCallback.PAYLOAD_PROGRESS_CHANGED in payloads,
		)
		with(binding.iconsView) {
			clearIcons()
			if (item.isPinned) addIcon(R.drawable.ic_pin_small)
			if (item.isSaved) addIcon(R.drawable.ic_storage)
			if (item.isFavorite) addIcon(R.drawable.ic_heart_outline)
			isVisible = iconsCount > 0
		}
		binding.imageViewCover.setImageAsync(item.coverUrl, item.manga)
		binding.textViewTags.text = item.tags.joinToString(separator = ", ") { it.title ?: "" }
		binding.badge.number = item.counter
		binding.badge.isVisible = item.counter > 0
	}
}
