package org.manga.peak.tracker.domain

import org.manga.peak.core.prefs.AppSettings
import org.manga.peak.favourites.domain.FavouritesRepository
import org.manga.peak.list.domain.ListFilterOption
import org.manga.peak.list.domain.MangaListQuickFilter
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
