package org.manga.peak.list.ui.adapter

import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.manga.peak.core.util.ext.setTextAndVisible
import org.manga.peak.databinding.ItemLoadingStateBinding
import org.manga.peak.list.ui.model.ListModel
import org.manga.peak.list.ui.model.LoadingState

fun loadingStateAD() = adapterDelegateViewBinding<LoadingState, ListModel, ItemLoadingStateBinding>(
	{ inflater, parent -> ItemLoadingStateBinding.inflate(inflater, parent, false) },
) {

	bind {
		binding.textViewMessage.setTextAndVisible(item.textResId)
	}
}
