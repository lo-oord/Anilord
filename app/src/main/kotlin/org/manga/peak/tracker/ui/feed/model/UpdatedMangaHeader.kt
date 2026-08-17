package org.manga.peak.tracker.ui.feed.model

import org.manga.peak.list.ui.ListModelDiffCallback
import org.manga.peak.list.ui.model.ListModel
import org.manga.peak.list.ui.model.MangaListModel

data class UpdatedMangaHeader(
	val list: List<MangaListModel>,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is UpdatedMangaHeader
	}

	override fun getChangePayload(previousState: ListModel): Any {
		return ListModelDiffCallback.PAYLOAD_NESTED_LIST_CHANGED
	}
}
