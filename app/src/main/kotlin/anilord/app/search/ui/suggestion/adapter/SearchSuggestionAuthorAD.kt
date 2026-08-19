package anilord.app.search.ui.suggestion.adapter

import android.view.View
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import anilord.app.R
import anilord.app.databinding.ItemSearchSuggestionQueryHintBinding
import anilord.app.search.domain.SearchKind
import anilord.app.search.ui.suggestion.SearchSuggestionListener
import anilord.app.search.ui.suggestion.model.SearchSuggestionItem

fun searchSuggestionAuthorAD(
	listener: SearchSuggestionListener,
) = adapterDelegateViewBinding<SearchSuggestionItem.Author, SearchSuggestionItem, ItemSearchSuggestionQueryHintBinding>(
	{ inflater, parent -> ItemSearchSuggestionQueryHintBinding.inflate(inflater, parent, false) },
) {

	val viewClickListener = View.OnClickListener { _ ->
		listener.onQueryClick(item.name, SearchKind.AUTHOR, true)
	}

	binding.root.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_user, 0, 0, 0)
	binding.root.setOnClickListener(viewClickListener)

	bind {
		binding.root.text = item.name
	}
}
