package org.manga.peak.details.ui

import android.content.Context
import org.manga.peak.R
import org.manga.peak.bookmarks.domain.Bookmark
import org.manga.peak.details.data.MangaDetails
import org.manga.peak.details.ui.model.ChapterListItem
import org.manga.peak.details.ui.model.toListItem
import org.manga.peak.list.ui.model.ListHeader
import org.manga.peak.list.ui.model.ListModel
import org.koitharu.kotatsu.parsers.util.mapToSet

fun MangaDetails.mapChapters(
	currentChapterId: Long,
	readChaptersCount: Int,
	newCount: Int,
	branch: String?,
	bookmarks: List<Bookmark>,
	isGrid: Boolean,
	isDownloadedOnly: Boolean,
): List<ChapterListItem> {
	val remoteChapters = chapters[branch].orEmpty()
	val localChapters = local?.manga?.getChapters(branch).orEmpty()
	if (remoteChapters.isEmpty() && localChapters.isEmpty()) {
		return emptyList()
	}
	val bookmarked = bookmarks.mapToSet { it.chapterId }
	val newFrom = if (newCount == 0 || remoteChapters.isEmpty()) Int.MAX_VALUE else remoteChapters.size - newCount
	val ids = buildSet(maxOf(remoteChapters.size, localChapters.size)) {
		remoteChapters.mapTo(this) { it.id }
		localChapters.mapTo(this) { it.id }
	}
	val orderedIds = buildList(ids.size) {
		remoteChapters.mapTo(this) { it.id }
		localChapters.mapTo(this) { it.id }
	}.distinct()
	val currentIndex = orderedIds.indexOf(currentChapterId)
	val readBoundary = maxOf(currentIndex, readChaptersCount.coerceIn(0, orderedIds.size))
	val readIds = orderedIds.take(readBoundary).toSet()
	val result = ArrayList<ChapterListItem>(ids.size)
	val localMap = if (localChapters.isNotEmpty()) {
		localChapters.associateByTo(LinkedHashMap(localChapters.size)) { it.id }
	} else {
		null
	}
	if (!isDownloadedOnly || local?.manga?.chapters == null) {
		for ((chapterIndex, chapter) in remoteChapters.withIndex()) {
			val local = localMap?.remove(chapter.id)
			val isUnread = chapter.id !in readIds
			result += (local ?: chapter).toListItem(
				isCurrent = chapter.id == currentChapterId,
				isUnread = isUnread,
				isNew = isUnread && chapterIndex >= newFrom,
				isDownloaded = local != null,
				isBookmarked = chapter.id in bookmarked,
				isGrid = isGrid,
			)
		}
	}
	if (!localMap.isNullOrEmpty()) {
		for (chapter in localMap.values) {
			result += chapter.toListItem(
				isCurrent = chapter.id == currentChapterId,
				isUnread = chapter.id !in readIds,
				isNew = false,
				isDownloaded = !isLocal,
				isBookmarked = chapter.id in bookmarked,
				isGrid = isGrid,
			)
		}
	}
	return result
}

fun List<ChapterListItem>.withVolumeHeaders(context: Context): MutableList<ListModel> {
	var prevVolume = 0
	val result = ArrayList<ListModel>((size * 1.4).toInt())
	for (item in this) {
		val chapter = item.chapter
		if (chapter.volume != prevVolume) {
			val text = if (chapter.volume == 0) {
				context.getString(R.string.volume_unknown)
			} else {
				context.getString(R.string.volume_, chapter.volume)
			}
			result.add(ListHeader(text))
			prevVolume = chapter.volume
		}
		result.add(item)
	}
	return result
}
