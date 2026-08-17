package org.manga.peak.explore.ui.model

import androidx.annotation.StringRes
import org.manga.peak.R
import org.manga.peak.core.model.SourceContentType
import org.manga.peak.core.model.sourceContentType
import org.koitharu.kotatsu.parsers.model.MangaSource

enum class BrowseGroup(@StringRes val titleResId: Int) {
	ALL(R.string.content_type_all),
	MANGA(R.string.content_type_manga),
	NOVEL(R.string.content_type_novel),
	ANIME(R.string.content_type_anime),
	;

	fun matches(source: MangaSource): Boolean = when (this) {
		ALL -> true
		NOVEL -> source.sourceContentType == SourceContentType.NOVEL
		ANIME -> source.sourceContentType == SourceContentType.ANIME
		MANGA -> source.sourceContentType != SourceContentType.NOVEL &&
			source.sourceContentType != SourceContentType.ANIME
	}
}
