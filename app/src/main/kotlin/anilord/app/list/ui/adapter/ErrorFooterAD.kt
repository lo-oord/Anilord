package anilord.app.list.ui.adapter

import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import anilord.app.core.util.ext.getDisplayMessage
import anilord.app.databinding.ItemErrorFooterBinding
import anilord.app.list.ui.model.ErrorFooter
import anilord.app.list.ui.model.ListModel

fun errorFooterAD(
	listener: ListStateHolderListener?,
) = adapterDelegateViewBinding<ErrorFooter, ListModel, ItemErrorFooterBinding>(
	{ inflater, parent -> ItemErrorFooterBinding.inflate(inflater, parent, false) },
) {

	if (listener != null) {
		binding.root.setOnClickListener {
			listener.onRetryClick(item.exception)
		}
	}

	bind {
		binding.textViewTitle.text = item.exception.getDisplayMessage(context.resources)
	}
}
