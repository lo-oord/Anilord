package org.manga.peak.list.ui.model

data class NativeAdListModel(
	val slot: Int,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is NativeAdListModel && other.slot == slot
	}
}
