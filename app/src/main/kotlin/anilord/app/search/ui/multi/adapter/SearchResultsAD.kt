package anilord.app.search.ui.multi.adapter

import android.annotation.SuppressLint
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView.RecycledViewPool
import com.hannesdorfmann.adapterdelegates4.ListDelegationAdapter
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import anilord.app.R
import anilord.app.core.model.UnknownMangaSource
import anilord.app.core.ui.list.AdapterDelegateClickListenerAdapter
import anilord.app.core.ui.list.OnListItemClickListener
import anilord.app.core.ui.list.decor.SpacingItemDecoration
import anilord.app.core.util.ext.getDisplayMessage
import anilord.app.core.util.ext.textAndVisible
import anilord.app.databinding.ItemListGroupBinding
import anilord.app.list.ui.MangaSelectionDecoration
import anilord.app.list.ui.adapter.mangaGridItemAD
import anilord.app.list.ui.model.ListModel
import anilord.app.list.ui.model.MangaListModel
import anilord.app.list.ui.size.ItemSizeResolver
import anilord.app.search.ui.multi.SearchResultsListModel

@SuppressLint("NotifyDataSetChanged")
fun searchResultsAD(
	sharedPool: RecycledViewPool,
	sizeResolver: ItemSizeResolver,
	selectionDecoration: MangaSelectionDecoration,
	listener: OnListItemClickListener<MangaListModel>,
	itemClickListener: OnListItemClickListener<SearchResultsListModel>,
) = adapterDelegateViewBinding<SearchResultsListModel, ListModel, ItemListGroupBinding>(
	{ layoutInflater, parent -> ItemListGroupBinding.inflate(layoutInflater, parent, false) },
) {

	binding.recyclerView.setRecycledViewPool(sharedPool)
	val adapter = ListDelegationAdapter(mangaGridItemAD(sizeResolver, listener))
	binding.recyclerView.addItemDecoration(selectionDecoration)
	binding.recyclerView.adapter = adapter
	val spacing = context.resources.getDimensionPixelOffset(R.dimen.grid_spacing_outer)
	binding.recyclerView.addItemDecoration(SpacingItemDecoration(spacing, withBottomPadding = true))
	val eventListener = AdapterDelegateClickListenerAdapter(this, itemClickListener)
	binding.buttonMore.setOnClickListener(eventListener)

	bind {
		binding.textViewTitle.text = item.getTitle(context)
		binding.buttonMore.isVisible = item.source !== UnknownMangaSource
		adapter.items = item.list
		adapter.notifyDataSetChanged()
		binding.recyclerView.isGone = item.list.isEmpty()
		binding.textViewError.textAndVisible = item.error?.getDisplayMessage(context.resources)
	}
}
