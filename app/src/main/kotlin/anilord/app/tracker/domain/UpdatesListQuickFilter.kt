package anilord.app.tracker.domain

import anilord.app.core.prefs.AppSettings
import anilord.app.favourites.domain.FavouritesRepository
import anilord.app.list.domain.ListFilterOption
import anilord.app.list.domain.MangaListQuickFilter
import javax.inject.Inject

class UpdatesListQuickFilter @Inject constructor(
	private val favouritesRepository: FavouritesRepository,
	settings: AppSettings,
) : MangaListQuickFilter(settings) {

	override suspend fun getAvailableFilterOptions(): List<ListFilterOption> =
		favouritesRepository.getMostUpdatedCategories(
			limit = 4,
		).map {
			ListFilterOption.Favorite(it)
		}
}
