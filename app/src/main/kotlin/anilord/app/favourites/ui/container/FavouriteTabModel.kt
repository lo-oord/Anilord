package anilord.app.favourites.ui.container

import anilord.app.list.ui.model.ListModel

data class FavouriteTabModel(
	val id: Long,
	val title: String?,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is FavouriteTabModel && other.id == id
	}
}
