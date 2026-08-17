package org.manga.peak.list.ui.adapter

import androidx.core.view.isVisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.manga.peak.ads.NativeAdController
import org.manga.peak.ads.bindNativeAd
import org.manga.peak.databinding.ItemNativeAdBinding
import org.manga.peak.list.ui.model.ListModel
import org.manga.peak.list.ui.model.NativeAdListModel

fun nativeAdListItemAD(
	controller: NativeAdController,
) = adapterDelegateViewBinding<NativeAdListModel, ListModel, ItemNativeAdBinding>(
	{ inflater, parent -> ItemNativeAdBinding.inflate(inflater, parent, false) },
) {

	bind {
		val slot = item.slot
		itemView.tag = slot
		itemView.isVisible = false
		controller.getOrLoad(context, slot) { nativeAd ->
			if (itemView.tag != slot || nativeAd == null) return@getOrLoad
			binding.bindNativeAd(nativeAd)
			itemView.isVisible = true
		}
	}
}
