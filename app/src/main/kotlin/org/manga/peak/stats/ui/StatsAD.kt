package org.manga.peak.stats.ui

import android.content.res.ColorStateList
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.manga.peak.R
import org.manga.peak.core.ui.list.OnListItemClickListener
import org.manga.peak.core.util.KotatsuColors
import org.manga.peak.databinding.ItemStatsBinding
import org.koitharu.kotatsu.parsers.model.Manga
import org.manga.peak.stats.domain.StatsRecord

fun statsAD(
	listener: OnListItemClickListener<Manga>,
) = adapterDelegateViewBinding<StatsRecord, StatsRecord, ItemStatsBinding>(
	{ layoutInflater, parent -> ItemStatsBinding.inflate(layoutInflater, parent, false) },
) {

	binding.root.setOnClickListener { v ->
		listener.onItemClick(item.manga ?: return@setOnClickListener, v)
	}

	bind {
		binding.textViewTitle.text = item.manga?.title ?: getString(R.string.other_manga)
		binding.textViewSummary.text = item.time.format(context.resources)
		binding.imageViewBadge.imageTintList = ColorStateList.valueOf(KotatsuColors.ofManga(context, item.manga))
		binding.root.isClickable = item.manga != null
	}
}
