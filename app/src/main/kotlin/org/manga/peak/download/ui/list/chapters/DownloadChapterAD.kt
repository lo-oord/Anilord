package org.manga.peak.download.ui.list.chapters

import androidx.core.content.ContextCompat
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.manga.peak.R
import org.manga.peak.core.util.ext.drawableEnd
import org.manga.peak.databinding.ItemChapterDownloadBinding

fun downloadChapterAD() = adapterDelegateViewBinding<DownloadChapter, DownloadChapter, ItemChapterDownloadBinding>(
	{ layoutInflater, parent -> ItemChapterDownloadBinding.inflate(layoutInflater, parent, false) },
) {

	val iconDone = ContextCompat.getDrawable(context, R.drawable.ic_check)

	bind {
		binding.textViewNumber.text = item.number
		binding.textViewTitle.text = item.name
		binding.textViewTitle.drawableEnd = if (item.isDownloaded) iconDone else null
	}
}
