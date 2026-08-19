package anilord.app.favourites.ui.categories

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import anilord.app.core.model.FavouriteCategory
import anilord.app.core.ui.list.OnListItemClickListener

interface FavouriteCategoriesListListener : OnListItemClickListener<FavouriteCategory?> {

	fun onDragHandleTouch(holder: RecyclerView.ViewHolder): Boolean

	fun onEditClick(item: FavouriteCategory, view: View)

	fun onShowAllClick(isChecked: Boolean)
}
