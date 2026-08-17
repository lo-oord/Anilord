package org.manga.peak.core.model

import androidx.annotation.StringRes
import org.manga.peak.R
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaSource

enum class SourceContentType(@StringRes val titleResId: Int) {
	MANGA(R.string.content_type_manga),
	MANHWA(R.string.content_type_manhwa),
	MANHUA(R.string.content_type_manhua),
	HENTAI(R.string.content_type_hentai),
	COMICS(R.string.content_type_comics),
	NOVEL(R.string.content_type_novel),
	ANIME(R.string.content_type_anime),
	ONE_SHOT(R.string.content_type_one_shot),
	DOUJINSHI(R.string.content_type_doujinshi),
	IMAGE_SET(R.string.content_type_image_set),
	ARTIST_CG(R.string.content_type_artist_cg),
	GAME_CG(R.string.content_type_game_cg),
	OTHER(R.string.content_type_other),
}

val MangaSource.sourceContentType: SourceContentType
	get() = when (val source = unwrap()) {
		is MangaParserSource -> source.contentType.toSourceContentType()
		else -> SourceContentType.OTHER
	}

private fun ContentType.toSourceContentType(): SourceContentType = when (this) {
	ContentType.MANGA -> SourceContentType.MANGA
	ContentType.MANHWA -> SourceContentType.MANHWA
	ContentType.MANHUA -> SourceContentType.MANHUA
	ContentType.HENTAI -> SourceContentType.HENTAI
	ContentType.COMICS -> SourceContentType.COMICS
	ContentType.NOVEL -> SourceContentType.NOVEL
	ContentType.ANIME -> SourceContentType.ANIME
	ContentType.ONE_SHOT -> SourceContentType.ONE_SHOT
	ContentType.DOUJINSHI -> SourceContentType.DOUJINSHI
	ContentType.IMAGE_SET -> SourceContentType.IMAGE_SET
	ContentType.ARTIST_CG -> SourceContentType.ARTIST_CG
	ContentType.GAME_CG -> SourceContentType.GAME_CG
	ContentType.OTHER -> SourceContentType.OTHER
}
