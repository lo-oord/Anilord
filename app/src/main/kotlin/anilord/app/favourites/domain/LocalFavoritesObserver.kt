package anilord.app.favourites.domain

import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import anilord.app.core.db.MangaDatabase
import anilord.app.core.db.entity.toManga
import anilord.app.core.db.entity.toMangaTags
import anilord.app.favourites.data.FavouriteManga
import anilord.app.list.domain.ListFilterOption
import anilord.app.list.domain.ListSortOrder
import anilord.app.local.data.index.LocalMangaIndex
import anilord.app.local.domain.LocalObserveMapper
import org.koitharu.kotatsu.parsers.model.Manga
import javax.inject.Inject

@Reusable
class LocalFavoritesObserver @Inject constructor(
	localMangaIndex: LocalMangaIndex,
	private val db: MangaDatabase,
) : LocalObserveMapper<FavouriteManga, Manga>(localMangaIndex) {

	fun observeAll(
		order: ListSortOrder,
		filterOptions: Set<ListFilterOption>,
		limit: Int
	): Flow<List<Manga>> = db.getFavouritesDao().observeAll(order, filterOptions, limit).mapToLocal()

	fun observeAll(
		categoryId: Long,
		order: ListSortOrder,
		filterOptions: Set<ListFilterOption>,
		limit: Int
	): Flow<List<Manga>> = db.getFavouritesDao().observeAll(categoryId, order, filterOptions, limit).mapToLocal()

	override fun toManga(e: FavouriteManga) = e.manga.toManga(e.tags.toMangaTags(), null)

	override fun toResult(e: FavouriteManga, manga: Manga) = manga
}
