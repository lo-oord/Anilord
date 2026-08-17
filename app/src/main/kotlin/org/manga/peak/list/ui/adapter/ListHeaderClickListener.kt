package org.manga.peak.list.ui.adapter

import android.view.View
import org.manga.peak.list.ui.model.ListHeader

interface ListHeaderClickListener {

	fun onListHeaderClick(item: ListHeader, view: View)
}
