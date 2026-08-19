package anilord.app.bookmarks.ui

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import anilord.app.bookmarks.domain.Bookmark
import anilord.app.core.util.ext.getItem
import anilord.app.list.ui.MangaSelectionDecoration

class BookmarksSelectionDecoration(context: Context) : MangaSelectionDecoration(context) {

	override fun getItemId(parent: RecyclerView, child: View): Long {
		val holder = parent.getChildViewHolder(child) ?: return RecyclerView.NO_ID
		val item = holder.getItem(Bookmark::class.java) ?: return RecyclerView.NO_ID
		return item.pageId
	}
}
