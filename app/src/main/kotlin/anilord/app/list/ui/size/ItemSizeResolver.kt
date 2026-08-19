package anilord.app.list.ui.size

import android.view.View
import android.widget.TextView
import anilord.app.history.ui.util.ReadingProgressView

interface ItemSizeResolver {

	val cellWidth: Int

	fun attachToView(
		view: View,
		textView: TextView?,
		progressView: ReadingProgressView?,
	)
}
