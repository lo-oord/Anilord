package org.manga.peak.settings.sources.catalog

import org.manga.peak.core.model.SourceContentType

data class SourcesCatalogFilter(
	val types: Set<SourceContentType>,
	val locale: String?,
	val isNewOnly: Boolean,
)
