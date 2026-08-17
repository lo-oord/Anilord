package org.manga.peak.scrobbling.common.ui.config.adapter

import androidx.core.view.isInvisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.manga.peak.R
import org.manga.peak.databinding.ItemHeaderBinding
import org.manga.peak.list.ui.model.ListModel
import org.manga.peak.scrobbling.common.domain.model.ScrobblingStatus

fun scrobblingHeaderAD() = adapterDelegateViewBinding<ScrobblingStatus, ListModel, ItemHeaderBinding>(
	{ inflater, parent -> ItemHeaderBinding.inflate(inflater, parent, false) },
) {

	binding.buttonMore.isInvisible = true
	val strings = context.resources.getStringArray(R.array.scrobbling_statuses)

	bind {
		binding.textViewTitle.text = strings.getOrNull(item.ordinal)
	}
}
