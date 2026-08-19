package anilord.app.search.ui.suggestion.adapter

import anilord.app.core.ui.BaseListAdapter
import anilord.app.search.ui.suggestion.SearchSuggestionListener
import anilord.app.search.ui.suggestion.model.SearchSuggestionItem

const val SEARCH_SUGGESTION_ITEM_TYPE_QUERY = 0

class SearchSuggestionAdapter(
	listener: SearchSuggestionListener,
) : BaseListAdapter<SearchSuggestionItem>() {

	init {
		delegatesManager
			.addDelegate(SEARCH_SUGGESTION_ITEM_TYPE_QUERY, searchSuggestionQueryAD(listener))
			.addDelegate(searchSuggestionSourceAD(listener))
			.addDelegate(searchSuggestionSourceTipAD(listener))
			.addDelegate(searchSuggestionTagsAD(listener))
			.addDelegate(searchSuggestionMangaListAD(listener))
			.addDelegate(searchSuggestionQueryHintAD(listener))
			.addDelegate(searchSuggestionAuthorAD(listener))
			.addDelegate(searchSuggestionTextAD())
	}
}
