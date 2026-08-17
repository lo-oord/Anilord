package org.manga.peak.explore.ui.model

import org.manga.peak.list.ui.model.ListModel

data class ExploreButtons(
	val isRandomLoading: Boolean,
	val activePresetName: String? = null,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is ExploreButtons
	}
}
