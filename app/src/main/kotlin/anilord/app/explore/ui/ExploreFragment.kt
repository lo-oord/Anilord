package anilord.app.explore.ui

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.appcompat.view.ActionMode
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import anilord.app.R
import anilord.app.core.exceptions.resolve.SnackbarErrorObserver
import anilord.app.core.model.LocalMangaSource
import anilord.app.core.nav.router
import anilord.app.core.parser.external.ExternalMangaSource
import anilord.app.core.ui.BaseFragment
import anilord.app.core.ui.dialog.BigButtonsAlertDialog
import anilord.app.core.ui.list.ListSelectionController
import anilord.app.core.ui.list.OnListItemClickListener
import anilord.app.core.ui.util.RecyclerViewOwner
import anilord.app.core.ui.util.ReversibleActionObserver
import anilord.app.core.ui.util.SpanSizeResolver
import anilord.app.core.ui.widgets.ChipsView
import anilord.app.core.util.ext.addMenuProvider
import anilord.app.core.util.ext.consumeAllSystemBarsInsets
import anilord.app.core.util.ext.findAppCompatDelegate
import anilord.app.core.util.ext.observe
import anilord.app.core.util.ext.observeEvent
import anilord.app.core.util.ext.systemBarsInsets
import anilord.app.databinding.FragmentExploreBinding
import anilord.app.explore.ui.adapter.ExploreAdapter
import anilord.app.explore.ui.preset.SourcePresetListActivity
import anilord.app.explore.ui.adapter.ExploreListEventListener
import anilord.app.explore.ui.model.BrowseGroup
import anilord.app.explore.ui.model.MangaSourceItem
import anilord.app.list.ui.adapter.TypedListSpacingDecoration
import anilord.app.list.ui.model.ListHeader
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaParserSource

@AndroidEntryPoint
class ExploreFragment :
	BaseFragment<FragmentExploreBinding>(),
	RecyclerViewOwner,
	ExploreListEventListener,
	OnListItemClickListener<MangaSourceItem>,
	ListSelectionController.Callback,
	ChipsView.OnChipClickListener {

	private val viewModel by viewModels<ExploreViewModel>()
	private var exploreAdapter: ExploreAdapter? = null
	private var sourceSelectionController: ListSelectionController? = null

	override val recyclerView: RecyclerView?
		get() = viewBinding?.recyclerView

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentExploreBinding {
		return FragmentExploreBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: FragmentExploreBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		exploreAdapter = ExploreAdapter(this, this) { manga, view ->
			router.openDetails(manga)
		}
		sourceSelectionController = ListSelectionController(
			appCompatDelegate = checkNotNull(findAppCompatDelegate()),
			decoration = SourceSelectionDecoration(binding.root.context),
			registryOwner = this,
			callback = this,
		)
		with(binding.recyclerView) {
			adapter = exploreAdapter
			setHasFixedSize(true)
			SpanSizeResolver(this, resources.getDimensionPixelSize(R.dimen.explore_grid_width)).attach()
			addItemDecoration(TypedListSpacingDecoration(context, false))
			checkNotNull(sourceSelectionController).attachToRecyclerView(this)
		}
		addMenuProvider(ExploreMenuProvider(requireContext(), router))
		binding.chipsContentType.onChipClickListener = this
		viewModel.selectedBrowseGroup.observe(viewLifecycleOwner, ::onBrowseGroupChanged)
		viewModel.content.observe(viewLifecycleOwner, checkNotNull(exploreAdapter))
		viewModel.onError.observeEvent(viewLifecycleOwner, SnackbarErrorObserver(binding.recyclerView, this))
		viewModel.onOpenManga.observeEvent(viewLifecycleOwner, ::onOpenManga)
		viewModel.onActionDone.observeEvent(viewLifecycleOwner, ReversibleActionObserver(binding.recyclerView))
		viewModel.isGrid.observe(viewLifecycleOwner, ::onGridModeChanged)
		viewModel.onShowSuggestionsTip.observeEvent(viewLifecycleOwner) {
			showSuggestionsTip()
		}
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val barsInsets = insets.systemBarsInsets
		val basePadding = v.resources.getDimensionPixelOffset(R.dimen.list_spacing_normal)
		viewBinding?.recyclerView?.setPadding(
			/* left = */ barsInsets.left + basePadding,
			/* top = */ basePadding,
			/* right = */ barsInsets.right + basePadding,
			/* bottom = */ barsInsets.bottom + basePadding,
		)
		viewBinding?.chipsContentType?.setPadding(
			barsInsets.left + basePadding,
			0,
			barsInsets.right + basePadding,
			0,
		)
		return insets.consumeAllSystemBarsInsets()
	}

	override fun onDestroyView() {
		super.onDestroyView()
		sourceSelectionController = null
		exploreAdapter = null
	}

	override fun onListHeaderClick(item: ListHeader, view: View) {
		if (item.payload == R.id.nav_suggestions) {
			router.openSuggestions()
		} else if (viewModel.isAllSourcesEnabled.value) {
			router.openManageSources()
		} else {
			router.openSourcesCatalog()
		}
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.button_local -> router.openList(LocalMangaSource, null, null)
			R.id.button_bookmarks -> router.openBookmarks()
			R.id.button_more -> router.openSuggestions()
			R.id.button_downloads -> router.openDownloads()
			R.id.button_random -> viewModel.openRandom()
			R.id.button_presets -> startActivity(
				Intent(requireContext(), SourcePresetListActivity::class.java),
			)
			R.id.button_presets_dropdown -> showPresetsPopup(v)
		}
	}

	override fun onChipClick(chip: Chip, data: Any?) {
		if (data is BrowseGroup) viewModel.setBrowseGroup(data)
	}

	private fun onBrowseGroupChanged(selected: BrowseGroup) {
		viewBinding?.chipsContentType?.setChips(
			BrowseGroup.entries.map { group ->
				ChipsView.ChipModel(
					titleResId = group.titleResId,
					isChecked = group == selected,
					data = group,
				)
			},
		)
	}

	private fun showPresetsPopup(anchor: View) {
		val presets = viewModel.presets.value
		val popup = PopupMenu(requireContext(), anchor)
		popup.menu.add(Menu.NONE, 0, 0, R.string.default_sources)
		presets.forEachIndexed { index, preset ->
			popup.menu.add(Menu.NONE, index + 1, index + 1, preset.title)
		}
		popup.setOnMenuItemClickListener { menuItem ->
			val position = menuItem.itemId
			if (position == 0) {
				viewModel.setActivePreset(0L)
			} else {
				val preset = presets[position - 1]
				if (viewModel.activePresetId == preset.id) {
					viewModel.setActivePreset(0L)
				} else {
					viewModel.setActivePreset(preset.id)
				}
			}
			true
		}
		popup.show()
	}

	override fun onItemClick(item: MangaSourceItem, view: View) {
		if (sourceSelectionController?.onItemClick(item.id) == true) {
			return
		}
		router.openList(item.source, null, null)
	}

	override fun onItemLongClick(item: MangaSourceItem, view: View): Boolean {
		return sourceSelectionController?.onItemLongClick(view, item.id) == true
	}

	override fun onItemContextClick(item: MangaSourceItem, view: View): Boolean {
		return sourceSelectionController?.onItemContextClick(view, item.id) == true
	}

	override fun onRetryClick(error: Throwable) = Unit

	override fun onEmptyActionClick() = router.openSourcesCatalog()

	override fun onSelectionChanged(controller: ListSelectionController, count: Int) {
		viewBinding?.recyclerView?.invalidateItemDecorations()
	}

	override fun onCreateActionMode(
		controller: ListSelectionController,
		menuInflater: MenuInflater,
		menu: Menu
	): Boolean {
		menuInflater.inflate(R.menu.mode_source, menu)
		return true
	}

	override fun onPrepareActionMode(controller: ListSelectionController, mode: ActionMode?, menu: Menu): Boolean {
		val selectedSources = viewModel.sourcesSnapshot(controller.peekCheckedIds())
		val isSingleSelection = selectedSources.size == 1
		menu.findItem(R.id.action_settings).isVisible = isSingleSelection
		menu.findItem(R.id.action_shortcut).isVisible = isSingleSelection
		menu.findItem(R.id.action_pin).isVisible = selectedSources.all { !it.isPinned }
		menu.findItem(R.id.action_unpin).isVisible = selectedSources.all { it.isPinned }
		menu.findItem(R.id.action_disable)?.isVisible = !viewModel.isAllSourcesEnabled.value &&
			selectedSources.all { it.mangaSource is MangaParserSource }
		menu.findItem(R.id.action_delete)?.isVisible = selectedSources.all { it.mangaSource is ExternalMangaSource }
		return super.onPrepareActionMode(controller, mode, menu)
	}

	override fun onActionItemClicked(controller: ListSelectionController, mode: ActionMode?, item: MenuItem): Boolean {
		val selectedSources = viewModel.sourcesSnapshot(controller.peekCheckedIds())
		if (selectedSources.isEmpty()) {
			return false
		}
		when (item.itemId) {
			R.id.action_settings -> {
				val source = selectedSources.singleOrNull() ?: return false
				router.openSourceSettings(source)
				mode?.finish()
			}

			R.id.action_disable -> {
				viewModel.disableSources(selectedSources)
				mode?.finish()
			}

			R.id.action_delete -> {
				selectedSources.forEach {
					(it.mangaSource as? ExternalMangaSource)?.let { uninstallExternalSource(it) }
				}
				mode?.finish()
			}

			R.id.action_shortcut -> {
				val source = selectedSources.singleOrNull() ?: return false
				viewModel.requestPinShortcut(source)
				mode?.finish()
			}

			R.id.action_pin -> {
				viewModel.setSourcesPinned(selectedSources, isPinned = true)
				mode?.finish()
			}

			R.id.action_unpin -> {
				viewModel.setSourcesPinned(selectedSources, isPinned = false)
				mode?.finish()
			}

			else -> return false
		}
		return true
	}

	private fun onOpenManga(manga: Manga) {
		router.openDetails(manga)
	}

	private fun onGridModeChanged(isGrid: Boolean) {
		requireViewBinding().recyclerView.layoutManager = if (isGrid) {
			GridLayoutManager(requireContext(), 4).also { lm ->
				lm.spanSizeLookup = ExploreGridSpanSizeLookup(checkNotNull(exploreAdapter), lm)
			}
		} else {
			LinearLayoutManager(requireContext())
		}
	}

	private fun showSuggestionsTip() {
		val listener = DialogInterface.OnClickListener { _, which ->
			viewModel.respondSuggestionTip(which == DialogInterface.BUTTON_POSITIVE)
		}
		BigButtonsAlertDialog.Builder(requireContext())
			.setIcon(R.drawable.ic_suggestion)
			.setTitle(R.string.suggestions_enable_prompt)
			.setPositiveButton(R.string.enable, listener)
			.setNegativeButton(R.string.no_thanks, listener)
			.create()
			.show()
	}

	private fun uninstallExternalSource(source: ExternalMangaSource) {
		val uri = Uri.fromParts("package", source.packageName, null)
		context?.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
	}
}
