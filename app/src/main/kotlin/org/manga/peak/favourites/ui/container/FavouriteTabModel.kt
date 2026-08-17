package org.manga.peak.favourites.ui.container

import org.manga.peak.list.ui.model.ListModel

data class FavouriteTabModel(
	val id: Long,
	val title: String?,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is FavouriteTabModel && other.id == id
	}
}
