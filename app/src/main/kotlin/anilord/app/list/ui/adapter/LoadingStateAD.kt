package anilord.app.list.ui.adapter

import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import anilord.app.core.util.ext.setTextAndVisible
import anilord.app.databinding.ItemLoadingStateBinding
import anilord.app.list.ui.model.ListModel
import anilord.app.list.ui.model.LoadingState

fun loadingStateAD() = adapterDelegateViewBinding<LoadingState, ListModel, ItemLoadingStateBinding>(
	{ inflater, parent -> ItemLoadingStateBinding.inflate(inflater, parent, false) },
) {

	bind {
		binding.textViewMessage.setTextAndVisible(item.textResId)
	}
}
