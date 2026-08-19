package anilord.app.settings.tracker.categories

import anilord.app.core.model.FavouriteCategory
import anilord.app.core.ui.BaseListAdapter
import anilord.app.core.ui.list.OnListItemClickListener

class TrackerCategoriesConfigAdapter(
	listener: OnListItemClickListener<FavouriteCategory>,
) : BaseListAdapter<FavouriteCategory>() {

	init {
		delegatesManager.addDelegate(trackerCategoryAD(listener))
	}
}
