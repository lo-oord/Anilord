package anilord.app.tracker.ui.feed.model

import anilord.app.core.model.withOverride
import anilord.app.core.ui.model.MangaOverride
import anilord.app.list.ui.ListModelDiffCallback
import anilord.app.list.ui.model.ListModel
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.util.ifNullOrEmpty

data class FeedItem(
	val id: Long,
	private val override: MangaOverride?,
	val manga: Manga,
	val count: Int,
	val isNew: Boolean,
) : ListModel {

	val imageUrl: String?
		get() = override?.coverUrl.ifNullOrEmpty { manga.coverUrl }

	val title: String
		get() = override?.title.ifNullOrEmpty { manga.title }

	fun toMangaWithOverride() = manga.withOverride(override)

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is FeedItem && other.id == id
	}

	override fun getChangePayload(previousState: ListModel): Any? = when {
		previousState !is FeedItem -> null
		isNew != previousState.isNew -> ListModelDiffCallback.PAYLOAD_ANYTHING_CHANGED
		else -> super.getChangePayload(previousState)
	}
}
