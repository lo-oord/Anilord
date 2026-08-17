package org.manga.peak.core.exceptions

import org.manga.peak.details.ui.pager.EmptyMangaReason
import org.koitharu.kotatsu.parsers.model.Manga

class EmptyMangaException(
    val reason: EmptyMangaReason?,
    val manga: Manga,
    cause: Throwable?
) : IllegalStateException(cause)
