package org.manga.peak.history.data

import dagger.Reusable
import org.manga.peak.core.db.MangaDatabase
import org.manga.peak.core.db.entity.toManga
import org.manga.peak.core.db.entity.toMangaTags
import org.manga.peak.history.domain.model.MangaWithHistory
import org.manga.peak.list.domain.ListFilterOption
import org.manga.peak.list.domain.ListSortOrder
import org.manga.peak.local.data.index.LocalMangaIndex
import org.manga.peak.local.domain.LocalObserveMapper
import org.koitharu.kotatsu.parsers.model.Manga
import javax.inject.Inject

@Reusable
class HistoryLocalObserver @Inject constructor(
	localMangaIndex: LocalMangaIndex,
	private val db: MangaDatabase,
) : LocalObserveMapper<HistoryWithManga, MangaWithHistory>(localMangaIndex) {

	fun observeAll(
		order: ListSortOrder,
		filterOptions: Set<ListFilterOption>,
		limit: Int
	) = db.getHistoryDao().observeAll(order, filterOptions, limit).mapToLocal()

	override fun toManga(e: HistoryWithManga) = e.manga.toManga(e.tags.toMangaTags(), null)

	override fun toResult(e: HistoryWithManga, manga: Manga) = MangaWithHistory(
		manga = manga,
		history = e.history.toMangaHistory(),
	)
}
