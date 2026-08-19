package anilord.app.filter.ui.tags

import android.content.Context
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import anilord.app.core.ui.BaseListAdapter
import anilord.app.core.ui.list.OnListItemClickListener
import anilord.app.core.ui.list.fastscroll.FastScroller
import anilord.app.core.util.ext.setChecked
import anilord.app.databinding.ItemCheckableNewBinding
import anilord.app.filter.ui.model.TagCatalogItem
import anilord.app.list.ui.ListModelDiffCallback
import anilord.app.list.ui.adapter.ListItemType
import anilord.app.list.ui.adapter.errorFooterAD
import anilord.app.list.ui.adapter.errorStateListAD
import anilord.app.list.ui.adapter.loadingFooterAD
import anilord.app.list.ui.adapter.loadingStateAD
import anilord.app.list.ui.model.ListModel

class TagsCatalogAdapter(
	listener: OnListItemClickListener<TagCatalogItem>,
) : BaseListAdapter<ListModel>(), FastScroller.SectionIndexer {

	init {
		addDelegate(ListItemType.FILTER_TAG, tagCatalogDelegate(listener))
		addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
		addDelegate(ListItemType.FOOTER_LOADING, loadingFooterAD())
		addDelegate(ListItemType.FOOTER_ERROR, errorFooterAD(null))
		addDelegate(ListItemType.STATE_ERROR, errorStateListAD(null))
	}

	override fun getSectionText(context: Context, position: Int): CharSequence? {
		return (items.getOrNull(position) as? TagCatalogItem)?.tag?.title?.firstOrNull()?.uppercase()
	}

	private fun tagCatalogDelegate(
		listener: OnListItemClickListener<TagCatalogItem>,
	) = adapterDelegateViewBinding<TagCatalogItem, ListModel, ItemCheckableNewBinding>(
		{ layoutInflater, parent -> ItemCheckableNewBinding.inflate(layoutInflater, parent, false) },
	) {

		itemView.setOnClickListener {
			listener.onItemClick(item, itemView)
		}

		bind { payloads ->
			binding.root.text = item.tag.title
			binding.root.setChecked(item.isChecked, ListModelDiffCallback.PAYLOAD_CHECKED_CHANGED in payloads)
		}
	}
}
