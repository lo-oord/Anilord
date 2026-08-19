package anilord.app.settings.nav

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import anilord.app.R
import anilord.app.core.prefs.NavItem
import anilord.app.core.ui.BaseFragment
import anilord.app.core.ui.BaseListAdapter
import anilord.app.core.ui.dialog.buildAlertDialog
import anilord.app.core.ui.dialog.setRecyclerViewList
import anilord.app.core.ui.list.OnListItemClickListener
import anilord.app.core.ui.util.RecyclerViewOwner
import anilord.app.core.util.ext.consumeAllSystemBarsInsets
import anilord.app.core.util.ext.container
import anilord.app.core.util.ext.end
import anilord.app.core.util.ext.observe
import anilord.app.core.util.ext.start
import anilord.app.core.util.ext.systemBarsInsets
import anilord.app.databinding.FragmentSettingsSourcesBinding
import anilord.app.list.ui.adapter.ListItemType
import anilord.app.list.ui.model.ListModel
import anilord.app.settings.nav.adapter.navAddAD
import anilord.app.settings.nav.adapter.navAvailableAD
import anilord.app.settings.nav.adapter.navConfigAD

@AndroidEntryPoint
class NavConfigFragment : BaseFragment<FragmentSettingsSourcesBinding>(), RecyclerViewOwner,
	OnListItemClickListener<NavItem>, View.OnClickListener {

	private var reorderHelper: ItemTouchHelper? = null
	private val viewModel by viewModels<NavConfigViewModel>()

	override val recyclerView: RecyclerView?
		get() = viewBinding?.recyclerView

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	): FragmentSettingsSourcesBinding {
		return FragmentSettingsSourcesBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(
		binding: FragmentSettingsSourcesBinding,
		savedInstanceState: Bundle?,
	) {
		super.onViewBindingCreated(binding, savedInstanceState)
		val navConfigAdapter = BaseListAdapter<ListModel>()
			.addDelegate(ListItemType.NAV_ITEM, navConfigAD(this))
			.addDelegate(ListItemType.FOOTER_LOADING, navAddAD(this))
		with(binding.recyclerView) {
			setHasFixedSize(true)
			adapter = navConfigAdapter
			reorderHelper = ItemTouchHelper(ReorderCallback()).also {
				it.attachToRecyclerView(this)
			}
		}
		viewModel.content.observe(viewLifecycleOwner, navConfigAdapter)
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val barsInsets = insets.systemBarsInsets
		val isTablet = !resources.getBoolean(R.bool.is_tablet)
		val isMaster = container?.id == R.id.container_master
		v.setPaddingRelative(
			if (isTablet && !isMaster) 0 else barsInsets.start(v),
			0,
			if (isTablet && isMaster) 0 else barsInsets.end(v),
			barsInsets.bottom,
		)
		return insets.consumeAllSystemBarsInsets()
	}

	override fun onResume() {
		super.onResume()
		activity?.setTitle(R.string.main_screen_sections)
	}

	override fun onDestroyView() {
		reorderHelper = null
		super.onDestroyView()
	}

	override fun onClick(v: View) {
		var dialog: DialogInterface? = null
		val listener = OnListItemClickListener<NavItem> { item, _ ->
			viewModel.addItem(item)
			dialog?.dismiss()
		}
		dialog = buildAlertDialog(v.context) {
			setTitle(R.string.add)
			setCancelable(true)
			setRecyclerViewList(viewModel.availableItems, navAvailableAD(listener))
			setNegativeButton(android.R.string.cancel, null)
		}.apply { show() }
	}

	override fun onItemClick(item: NavItem, view: View) {
		viewModel.removeItem(item)
	}

	override fun onItemLongClick(item: NavItem, view: View): Boolean {
		val holder = viewBinding?.recyclerView?.findContainingViewHolder(view) ?: return false
		reorderHelper?.startDrag(holder)
		return true
	}

	private inner class ReorderCallback : ItemTouchHelper.SimpleCallback(
		ItemTouchHelper.DOWN or ItemTouchHelper.UP,
		0,
	) {

		override fun onMove(
			recyclerView: RecyclerView,
			viewHolder: RecyclerView.ViewHolder,
			target: RecyclerView.ViewHolder,
		): Boolean = target.itemViewType == ListItemType.NAV_ITEM.ordinal

		override fun onMoved(
			recyclerView: RecyclerView,
			viewHolder: RecyclerView.ViewHolder,
			fromPos: Int,
			target: RecyclerView.ViewHolder,
			toPos: Int,
			x: Int,
			y: Int,
		) {
			super.onMoved(recyclerView, viewHolder, fromPos, target, toPos, x, y)
			viewModel.reorder(fromPos, toPos)
		}

		override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

		override fun isLongPressDragEnabled() = false
	}
}
