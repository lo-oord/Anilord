package org.manga.peak.reader.ui

import org.manga.peak.reader.ui.pager.ReaderPage

data class ReaderContent(
	val pages: List<ReaderPage>,
	val state: ReaderState?
)