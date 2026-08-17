package org.manga.peak.favourites.ui.categories

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import org.manga.peak.core.model.FavouriteCategory
import org.manga.peak.core.ui.list.OnListItemClickListener

interface FavouriteCategoriesListListener : OnListItemClickListener<FavouriteCategory?> {

	fun onDragHandleTouch(holder: RecyclerView.ViewHolder): Boolean

	fun onEditClick(item: FavouriteCategory, view: View)

	fun onShowAllClick(isChecked: Boolean)
}
