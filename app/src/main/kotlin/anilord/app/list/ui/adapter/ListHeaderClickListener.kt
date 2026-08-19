package anilord.app.list.ui.adapter

import android.view.View
import anilord.app.list.ui.model.ListHeader

interface ListHeaderClickListener {

	fun onListHeaderClick(item: ListHeader, view: View)
}
