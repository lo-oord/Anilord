package anilord.app.settings.sources.catalog

import anilord.app.core.model.SourceContentType

data class SourcesCatalogFilter(
	val types: Set<SourceContentType>,
	val locale: String?,
	val isNewOnly: Boolean,
)
