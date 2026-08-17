package org.manga.peak.explore.ui.model

import org.manga.peak.list.ui.model.ListModel
import org.manga.peak.list.ui.model.MangaCompactListModel

data class RecommendationsItem(
	val manga: List<MangaCompactListModel>
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is RecommendationsItem
	}
}
