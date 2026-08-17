package org.manga.peak.settings.storage.directories

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.hannesdorfmann.adapterdelegates4.AsyncListDifferDelegationAdapter
import dagger.hilt.android.AndroidEntryPoint
import org.manga.peak.R
import org.manga.peak.core.exceptions.resolve.SnackbarErrorObserver
import org.manga.peak.core.ui.BaseActivity
import org.manga.peak.core.ui.list.OnListItemClickListener
import org.manga.peak.core.ui.list.decor.SpacingItemDecoration
import org.manga.peak.core.util.ext.consumeAllSystemBarsInsets
import org.manga.peak.core.util.ext.observe
import org.manga.peak.core.util.ext.observeEvent
import org.manga.peak.databinding.ActivityMangaDirectoriesBinding

@AndroidEntryPoint
class MangaDirectoriesActivity : BaseActivity<ActivityMangaDirectoriesBinding>(),
	OnListItemClickListener<DirectoryConfigModel> {

	private val viewModel: MangaDirectoriesViewModel by viewModels()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityMangaDirectoriesBinding.inflate(layoutInflater))
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
		val adapter = AsyncListDifferDelegationAdapter(DirectoryConfigDiffCallback(), directoryConfigAD(this))
        val spacing = resources.getDimensionPixelOffset(R.dimen.list_spacing_large)
        viewBinding.recyclerView.adapter = adapter
        viewBinding.recyclerView.addItemDecoration(SpacingItemDecoration(spacing, withBottomPadding = false))
		viewBinding.fabAdd.isVisible = false
		viewModel.items.observe(this) { adapter.items = it }
		viewModel.isLoading.observe(this) { viewBinding.progressBar.isVisible = it }
		viewModel.onError.observeEvent(
			this,
			SnackbarErrorObserver(viewBinding.root, null, exceptionResolver) {
				if (it) viewModel.updateList()
			},
		)
	}

	override fun onItemClick(item: DirectoryConfigModel, view: View) {
		viewModel.onRemoveClick(item.path)
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val barsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
		viewBinding.fabAdd.updateLayoutParams<ViewGroup.MarginLayoutParams> {
			rightMargin = topMargin + barsInsets.right
			leftMargin = topMargin + barsInsets.left
			bottomMargin = topMargin + barsInsets.bottom
		}
		viewBinding.appbar.updatePadding(
			left = barsInsets.left,
			right = barsInsets.right,
			top = barsInsets.top,
		)
		viewBinding.recyclerView.updatePadding(
			left = barsInsets.left,
			right = barsInsets.right,
			bottom = barsInsets.bottom,
		)
		return insets.consumeAllSystemBarsInsets()
	}
}
