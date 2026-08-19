package anilord.app.explore.ui.adapter

import anilord.app.core.ui.BaseListAdapter
import anilord.app.core.ui.list.OnListItemClickListener
import anilord.app.explore.ui.model.MangaSourceItem
import anilord.app.list.ui.adapter.ListItemType
import anilord.app.list.ui.adapter.emptyHintAD
import anilord.app.list.ui.adapter.listHeaderAD
import anilord.app.list.ui.adapter.loadingStateAD
import anilord.app.list.ui.model.ListModel
import org.koitharu.kotatsu.parsers.model.Manga

class ExploreAdapter(
	listener: ExploreListEventListener,
	clickListener: OnListItemClickListener<MangaSourceItem>,
	mangaClickListener: OnListItemClickListener<Manga>,
) : BaseListAdapter<ListModel>() {

	init {
		addDelegate(ListItemType.EXPLORE_BUTTONS, exploreButtonsAD(listener))
		addDelegate(
			ListItemType.EXPLORE_SUGGESTION,
			exploreRecommendationItemAD(mangaClickListener),
		)
		addDelegate(ListItemType.HEADER, listHeaderAD(listener))
		addDelegate(ListItemType.EXPLORE_SOURCE_LIST, exploreSourceListItemAD(clickListener))
		addDelegate(ListItemType.EXPLORE_SOURCE_GRID, exploreSourceGridItemAD(clickListener))
		addDelegate(ListItemType.HINT_EMPTY, emptyHintAD(listener))
		addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
	}
}
