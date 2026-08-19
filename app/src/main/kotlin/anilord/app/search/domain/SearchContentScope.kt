package anilord.app.search.domain

import anilord.app.core.model.SourceContentType

/** Content bucket selected in the search bar. */
enum class SearchContentScope {
	ALL,
	ANIME,
	MANGA,
}

fun SearchContentScope.matches(sourceContentType: SourceContentType): Boolean = when (this) {
	SearchContentScope.ALL -> true
	SearchContentScope.ANIME -> sourceContentType == SourceContentType.ANIME
	SearchContentScope.MANGA -> sourceContentType == SourceContentType.MANGA ||
		sourceContentType == SourceContentType.MANHWA ||
		sourceContentType == SourceContentType.MANHUA
}
