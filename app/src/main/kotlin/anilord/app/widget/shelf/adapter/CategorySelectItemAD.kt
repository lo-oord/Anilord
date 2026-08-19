package anilord.app.widget.shelf.adapter

import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import anilord.app.R
import anilord.app.core.ui.list.OnListItemClickListener
import anilord.app.databinding.ItemCategoryCheckableSingleBinding
import anilord.app.widget.shelf.model.CategoryItem

fun categorySelectItemAD(
	clickListener: OnListItemClickListener<CategoryItem>
) = adapterDelegateViewBinding<CategoryItem, CategoryItem, ItemCategoryCheckableSingleBinding>(
	{ inflater, parent -> ItemCategoryCheckableSingleBinding.inflate(inflater, parent, false) },
) {

	itemView.setOnClickListener {
		clickListener.onItemClick(item, it)
	}

	bind {
		with(binding.checkedTextView) {
			text = item.name ?: getString(R.string.all_favourites)
			isChecked = item.isSelected
		}
	}
}
