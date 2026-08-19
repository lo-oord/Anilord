package anilord.app.details.ui.pager.chapters

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import anilord.app.R
import anilord.app.anime.player.AnimePlayerActivity
import anilord.app.core.model.isAnime
import anilord.app.core.nav.ReaderIntent
import anilord.app.core.nav.dismissParentDialog
import anilord.app.core.nav.router
import anilord.app.core.ui.BaseFragment
import anilord.app.core.ui.list.ListSelectionController
import anilord.app.core.ui.list.OnListItemClickListener
import anilord.app.core.ui.util.PagerNestedScrollHelper
import anilord.app.core.ui.util.RecyclerViewOwner
import anilord.app.core.ui.widgets.ChipsView
import anilord.app.core.util.RecyclerViewScrollCallback
import anilord.app.core.util.ext.findAppCompatDelegate
import anilord.app.core.util.ext.findParentCallback
import anilord.app.core.util.ext.observe
import anilord.app.core.util.ext.setTextAndVisible
import anilord.app.databinding.FragmentChaptersBinding
import anilord.app.details.ui.adapter.ChaptersAdapter
import anilord.app.details.ui.adapter.ChaptersSelectionDecoration
import anilord.app.details.ui.model.ChapterListItem
import anilord.app.details.ui.pager.ChaptersPagesViewModel
import anilord.app.details.ui.withVolumeHeaders
import anilord.app.list.domain.ListFilterOption
import anilord.app.list.ui.adapter.TypedListSpacingDecoration
import anilord.app.list.ui.model.ListModel
import anilord.app.reader.ui.ReaderNavigationCallback
import anilord.app.reader.ui.ReaderState
import kotlin.math.roundToInt

@AndroidEntryPoint
class ChaptersFragment :
	BaseFragment<FragmentChaptersBinding>(),
	OnListItemClickListener<ChapterListItem>,
	RecyclerViewOwner,
	ChipsView.OnChipClickListener {

	private val viewModel by ChaptersPagesViewModel.ActivityVMLazy(this)

	private var chaptersAdapter: ChaptersAdapter? = null
	private var selectionController: ListSelectionController? = null

	override val recyclerView: RecyclerView?
		get() = viewBinding?.recyclerViewChapters

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	) = FragmentChaptersBinding.inflate(inflater, container, false)

	override fun onViewBindingCreated(binding: FragmentChaptersBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		chaptersAdapter = ChaptersAdapter(this)
		selectionController = ListSelectionController(
			appCompatDelegate = checkNotNull(findAppCompatDelegate()),
			decoration = ChaptersSelectionDecoration(binding.root.context),
			registryOwner = this,
			callback = ChaptersSelectionCallback(viewModel, router, binding.recyclerViewChapters),
		)
		viewModel.isChaptersInGridView.observe(viewLifecycleOwner) { chaptersInGridView ->
			binding.recyclerViewChapters.layoutManager = if (chaptersInGridView && !viewModel.isNovelContent) {
				GridLayoutManager(context, ChapterGridSpanHelper.getSpanCount(binding.recyclerViewChapters)).apply {
					spanSizeLookup = ChapterGridSpanHelper.SpanSizeLookup(binding.recyclerViewChapters)
				}
			} else {
				LinearLayoutManager(context)
			}
		}
		with(binding.recyclerViewChapters) {
			addItemDecoration(TypedListSpacingDecoration(context, true))
			checkNotNull(selectionController).attachToRecyclerView(this)
			setHasFixedSize(true)
			PagerNestedScrollHelper(this).bind(viewLifecycleOwner)
			adapter = chaptersAdapter
			ChapterGridSpanHelper.attach(this)
		}
		binding.chipsFilter.onChipClickListener = this
		viewModel.isLoading.observe(viewLifecycleOwner, this::onLoadingStateChanged)
		viewModel.chapters
			.map { it.withVolumeHeaders(requireContext()) }
			.flowOn(Dispatchers.Default)
			.observe(viewLifecycleOwner, this::onChaptersChanged)
		viewModel.quickFilter.observe(viewLifecycleOwner, this::onFilterChanged)
		viewModel.emptyReason.observe(viewLifecycleOwner) {
			binding.textViewHolder.setTextAndVisible(it?.msgResId ?: 0)
		}
	}

	override fun onDestroyView() {
		chaptersAdapter = null
		selectionController = null
		super.onDestroyView()
	}

	override fun onItemClick(item: ChapterListItem, view: View) {
		if (selectionController?.onItemClick(item.chapter.id) == true) {
			return
		}
		val manga = viewModel.getMangaOrNull() ?: return
		if (manga.isAnime) {
			val readingState = viewModel.readingState.value
			val startPosition = readingState?.scroll
				?.takeIf { readingState.chapterId == item.chapter.id }
				?.toLong()
				?: 0L
			AnimePlayerActivity.start(view.context, manga, item.chapter, startPosition)
			return
		}
		val listener = findParentCallback(ReaderNavigationCallback::class.java)
		if (listener != null && listener.onChapterSelected(item.chapter)) {
			dismissParentDialog()
		} else {
			router.openReader(
				ReaderIntent.Builder(view.context)
					.manga(manga)
					.state(ReaderState(item.chapter.id, 0, 0))
					.build(),
			)
		}
	}

	override fun onItemLongClick(item: ChapterListItem, view: View): Boolean {
		return selectionController?.onItemLongClick(view, item.chapter.id) == true
	}

	override fun onItemContextClick(item: ChapterListItem, view: View): Boolean {
		return selectionController?.onItemContextClick(view, item.chapter.id) == true
	}

	override fun onChipClick(chip: Chip, data: Any?) {
		if (data !is ListFilterOption.Branch) return
		viewModel.setSelectedBranch(data.titleText)
	}

	override fun onApplyWindowInsets(
		v: View,
		insets: WindowInsetsCompat
	): WindowInsetsCompat {
		viewBinding?.run {
			val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
			recyclerViewChapters.updatePadding(
				left = bars.left,
				right = bars.right,
				bottom = bars.bottom,
			)
			chipsFilter.updatePadding(
				left = bars.left,
				right = bars.right,
			)
		}
		return WindowInsetsCompat.CONSUMED
	}

	private fun onChaptersChanged(list: List<ListModel>) {
		val adapter = chaptersAdapter ?: return
		if (adapter.itemCount == 0) {
			val position = list.indexOfFirst { it is ChapterListItem && it.isCurrent } - 1
			if (position > 0) {
				val offset = (resources.getDimensionPixelSize(R.dimen.chapter_list_item_height) * 0.6).roundToInt()
				adapter.setItems(
					list,
					RecyclerViewScrollCallback(requireViewBinding().recyclerViewChapters, position, offset),
				)
			} else {
				adapter.items = list
			}
		} else {
			adapter.items = list
		}
	}

	private fun onFilterChanged(list: List<ChipsView.ChipModel>) {
		viewBinding?.chipsFilter?.run {
			setChips(list)
			isGone = list.isEmpty()
		}
	}

	private fun onLoadingStateChanged(isLoading: Boolean) {
		requireViewBinding().progressBar.isVisible = isLoading
	}
}
