package org.manga.peak.scrobbling.shikimori.domain

import org.manga.peak.core.db.MangaDatabase
import org.manga.peak.core.parser.MangaRepository
import org.manga.peak.scrobbling.common.domain.Scrobbler
import org.manga.peak.scrobbling.common.domain.model.ScrobblerService
import org.manga.peak.scrobbling.common.domain.model.ScrobblingStatus
import org.manga.peak.scrobbling.shikimori.data.ShikimoriRepository
import javax.inject.Inject
import javax.inject.Singleton

private const val RATING_MAX = 10f

@Singleton
class ShikimoriScrobbler @Inject constructor(
	private val repository: ShikimoriRepository,
	db: MangaDatabase,
	mangaRepositoryFactory: MangaRepository.Factory,
) : Scrobbler(db, ScrobblerService.SHIKIMORI, repository, mangaRepositoryFactory) {

	init {
		statuses[ScrobblingStatus.PLANNED] = "planned"
		statuses[ScrobblingStatus.READING] = "watching"
		statuses[ScrobblingStatus.RE_READING] = "rewatching"
		statuses[ScrobblingStatus.COMPLETED] = "completed"
		statuses[ScrobblingStatus.ON_HOLD] = "on_hold"
		statuses[ScrobblingStatus.DROPPED] = "dropped"
	}

	override suspend fun updateScrobblingInfo(
		mangaId: Long,
		rating: Float,
		status: ScrobblingStatus?,
		comment: String?,
	) {
		val entity = db.getScrobblingDao().find(scrobblerService.id, mangaId)
		requireNotNull(entity) { "Scrobbling info for manga $mangaId not found" }
		repository.updateRate(
			rateId = entity.id,
			mangaId = entity.mangaId,
			rating = rating * RATING_MAX,
			status = statuses[status],
			comment = comment,
		)
	}
}
