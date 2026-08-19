package anilord.app.list.ui.adapter

import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import anilord.app.core.util.ext.setTextAndVisible
import anilord.app.databinding.ItemInfoBinding
import anilord.app.list.ui.model.InfoModel
import anilord.app.list.ui.model.ListModel

fun infoAD() = adapterDelegateViewBinding<InfoModel, ListModel, ItemInfoBinding>(
	{ layoutInflater, parent -> ItemInfoBinding.inflate(layoutInflater, parent, false) },
) {

	bind {
		binding.textViewTitle.setText(item.title)
		binding.textViewBody.setTextAndVisible(item.text)
		binding.textViewTitle.setCompoundDrawablesRelativeWithIntrinsicBounds(
			item.icon, 0, 0, 0,
		)
	}
}
