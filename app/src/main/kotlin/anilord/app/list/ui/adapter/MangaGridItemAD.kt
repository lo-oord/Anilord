package anilord.app.list.ui.adapter

import androidx.core.view.isVisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import anilord.app.R
import anilord.app.core.ui.list.AdapterDelegateClickListenerAdapter
import anilord.app.core.ui.list.OnListItemClickListener
import anilord.app.core.util.ext.setTooltipCompat
import anilord.app.databinding.ItemMangaGridBinding
import anilord.app.list.ui.ListModelDiffCallback.Companion.PAYLOAD_PROGRESS_CHANGED
import anilord.app.list.ui.model.ListModel
import anilord.app.list.ui.model.MangaGridModel
import anilord.app.list.ui.model.MangaListModel
import anilord.app.list.ui.size.ItemSizeResolver

fun mangaGridItemAD(
	sizeResolver: ItemSizeResolver,
	clickListener: OnListItemClickListener<MangaListModel>,
) = adapterDelegateViewBinding<MangaGridModel, ListModel, ItemMangaGridBinding>(
	{ inflater, parent -> ItemMangaGridBinding.inflate(inflater, parent, false) },
) {

	AdapterDelegateClickListenerAdapter(this, clickListener).attach(itemView)
	sizeResolver.attachToView(itemView, binding.textViewTitle, binding.progressView)

	bind { payloads ->
		itemView.setTooltipCompat(item.getSummary(context))
		binding.textViewTitle.text = item.title
		binding.progressView.setProgress(item.progress, PAYLOAD_PROGRESS_CHANGED in payloads)
		with(binding.iconsView) {
			clearIcons()
			if (item.isPinned) addIcon(R.drawable.ic_pin_small)
			if (item.isSaved) addIcon(R.drawable.ic_storage)
			if (item.isFavorite) addIcon(R.drawable.ic_heart_outline)
			isVisible = iconsCount > 0
		}
		binding.imageViewCover.setImageAsync(item.coverUrl, item.manga)
		binding.badge.number = item.counter
		binding.badge.isVisible = item.counter > 0
	}
}
