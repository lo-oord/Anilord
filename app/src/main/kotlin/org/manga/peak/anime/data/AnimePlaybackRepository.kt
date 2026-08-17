package org.manga.peak.anime.data

import org.koitharu.kotatsu.parsers.model.AnimeStream
import org.koitharu.kotatsu.parsers.model.MangaChapter

interface AnimePlaybackRepository {

	suspend fun getAnimeStreams(episode: MangaChapter): List<AnimeStream>
}
