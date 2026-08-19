package anilord.app.reader.ui.pager.standard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import anilord.app.core.exceptions.resolve.ExceptionResolver
import anilord.app.core.os.NetworkState
import anilord.app.databinding.ItemPageBinding
import anilord.app.reader.domain.PageLoader
import anilord.app.reader.ui.config.ReaderSettings
import anilord.app.reader.ui.pager.BaseReaderAdapter

class PagesAdapter(
	private val lifecycleOwner: LifecycleOwner,
	loader: PageLoader,
	readerSettingsProducer: ReaderSettings.Producer,
	networkState: NetworkState,
	exceptionResolver: ExceptionResolver,
) : BaseReaderAdapter<PageHolder>(
	loader = loader,
	readerSettingsProducer = readerSettingsProducer,
	networkState = networkState,
	exceptionResolver = exceptionResolver,
) {

	override fun onCreateViewHolder(
		parent: ViewGroup,
		loader: PageLoader,
		readerSettingsProducer: ReaderSettings.Producer,
		networkState: NetworkState,
		exceptionResolver: ExceptionResolver,
	) = PageHolder(
		owner = lifecycleOwner,
		binding = ItemPageBinding.inflate(LayoutInflater.from(parent.context), parent, false),
		loader = loader,
		readerSettingsProducer = readerSettingsProducer,
		networkState = networkState,
		exceptionResolver = exceptionResolver,
	)
}
