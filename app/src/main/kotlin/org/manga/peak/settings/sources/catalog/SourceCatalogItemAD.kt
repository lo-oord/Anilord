package org.manga.peak.settings.sources.catalog

import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.manga.peak.R
import org.manga.peak.core.model.getSummary
import org.manga.peak.core.model.getTitle
import org.manga.peak.core.ui.image.FaviconDrawable
import org.manga.peak.core.ui.list.OnListItemClickListener
import org.manga.peak.core.util.ext.drawableStart
import org.manga.peak.core.util.ext.getThemeDimensionPixelOffset
import org.manga.peak.core.util.ext.setTextAndVisible
import org.manga.peak.databinding.ItemEmptyHintBinding
import org.manga.peak.databinding.ItemSourceCatalogBinding
import org.manga.peak.list.ui.model.ListModel
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import androidx.appcompat.R as appcompatR

fun sourceCatalogItemSourceAD(
	listener: OnListItemClickListener<SourceCatalogItem.Source>
) = adapterDelegateViewBinding<SourceCatalogItem.Source, ListModel, ItemSourceCatalogBinding>(
	{ layoutInflater, parent ->
		ItemSourceCatalogBinding.inflate(layoutInflater, parent, false)
	},
) {

	binding.imageViewAdd.setOnClickListener { v ->
		listener.onItemLongClick(item, v)
	}
	binding.root.setOnClickListener { v ->
		listener.onItemClick(item, v)
	}
	val basePadding = context.getThemeDimensionPixelOffset(
		appcompatR.attr.listPreferredItemPaddingEnd,
		binding.root.paddingStart,
	)
	binding.root.updatePaddingRelative(
		end = (basePadding - context.resources.getDimensionPixelOffset(R.dimen.margin_small)).coerceAtLeast(0),
	)

	bind {
		binding.textViewTitle.text = item.source.getTitle(context)
		binding.textViewDescription.text = item.source.getSummary(context)
		binding.textViewDescription.drawableStart = if ((item.source as? MangaParserSource)?.isBroken == true) {
			ContextCompat.getDrawable(context, R.drawable.ic_off_small)
		} else {
			null
		}
		FaviconDrawable(context, R.style.FaviconDrawable_Small, item.source.name)
		binding.imageViewIcon.setImageAsync(item.source)
		binding.imageViewAdd.setImageResource(
			if (item.isInPreset) R.drawable.ic_check else R.drawable.ic_add,
		)
	}
}

fun sourceCatalogItemHintAD() = adapterDelegateViewBinding<SourceCatalogItem.Hint, ListModel, ItemEmptyHintBinding>(
	{ inflater, parent -> ItemEmptyHintBinding.inflate(inflater, parent, false) },
) {

	binding.buttonRetry.isVisible = false

	bind {
		binding.icon.setImageAsync(item.icon)
		binding.textPrimary.setText(item.title)
		binding.textSecondary.setTextAndVisible(item.text)
	}
}
