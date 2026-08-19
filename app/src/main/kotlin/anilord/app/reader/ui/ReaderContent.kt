package anilord.app.reader.ui

import anilord.app.reader.ui.pager.ReaderPage

data class ReaderContent(
	val pages: List<ReaderPage>,
	val state: ReaderState?
)