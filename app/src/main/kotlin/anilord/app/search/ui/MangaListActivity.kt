package anilord.app.search.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.toDrawable
import androidx.core.os.bundleOf
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.AppBarLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import anilord.app.R
import anilord.app.core.model.LocalMangaSource
import anilord.app.core.model.MangaSource
import anilord.app.core.model.getSummary
import anilord.app.core.model.getTitle
import anilord.app.core.model.isNsfw
import anilord.app.core.model.parcelable.ParcelableManga
import anilord.app.core.model.parcelable.ParcelableMangaListFilter
import anilord.app.core.nav.AppRouter
import anilord.app.core.nav.router
import anilord.app.core.parser.MangaRepository
import anilord.app.core.parser.ParserMangaRepository
import anilord.app.core.ui.BaseActivity
import anilord.app.core.ui.model.titleRes
import anilord.app.core.ui.util.FadingAppbarMediator
import anilord.app.core.util.ViewBadge
import anilord.app.core.util.ext.consumeSystemBarsInsets
import anilord.app.core.util.ext.end
import anilord.app.core.util.ext.getParcelableExtraCompat
import anilord.app.core.util.ext.getSerializableExtraCompat
import anilord.app.core.util.ext.getThemeColor
import anilord.app.core.util.ext.observe
import anilord.app.core.util.ext.setTextAndVisible
import anilord.app.core.util.ext.start
import anilord.app.databinding.ActivityMangaListBinding
import anilord.app.filter.ui.FilterCoordinator
import anilord.app.filter.ui.FilterHeaderFragment
import anilord.app.filter.ui.sheet.FilterSheetFragment
import anilord.app.list.ui.preview.PreviewFragment
import anilord.app.local.ui.LocalListFragment
import anilord.app.main.ui.owners.AppBarOwner
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.SortOrder
import anilord.app.remotelist.ui.RemoteListFragment
import anilord.app.settings.sources.auth.SourceAuthActivity
import org.koitharu.kotatsu.parsers.MangaParserCredentialsAuthProvider
import kotlin.math.absoluteValue
import javax.inject.Inject
import com.google.android.material.R as materialR

@AndroidEntryPoint
class MangaListActivity :
	BaseActivity<ActivityMangaListBinding>(),
	AppBarOwner, View.OnClickListener,
	FilterCoordinator.Owner,
	AppBarLayout.OnOffsetChangedListener {

	override val appBar: AppBarLayout
		get() = viewBinding.appbar

	override val filterCoordinator: FilterCoordinator
		get() = checkNotNull(findFilterOwner()) {
			"Cannot find FilterCoordinator.Owner fragment in ${supportFragmentManager.fragments}"
		}.filterCoordinator

	private lateinit var source: MangaSource
	private var initialFilter: MangaListFilter? = null
	private var initialSortOrder: SortOrder? = null
	private var isListInitialized = false

	@Inject
	lateinit var repositoryFactory: MangaRepository.Factory

	private val sourceAuthContract = registerForActivityResult(SourceAuthActivity.Contract()) { isAuthorized ->
		if (isAuthorized) {
			initListOnce()
		} else {
			finishAfterTransition()
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityMangaListBinding.inflate(layoutInflater))
		viewBinding.collapsingToolbarLayout?.let { collapsingToolbarLayout ->
			FadingAppbarMediator(viewBinding.appbar, collapsingToolbarLayout).bind()
		}
		initialFilter = intent.getParcelableExtraCompat<ParcelableMangaListFilter>(AppRouter.KEY_FILTER)?.filter
		initialSortOrder = intent.getSerializableExtraCompat<SortOrder>(AppRouter.KEY_SORT_ORDER)
		source = MangaSource(intent.getStringExtra(AppRouter.KEY_SOURCE))
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
		if (viewBinding.containerFilterHeader != null) {
			viewBinding.appbar.addOnOffsetChangedListener(this)
		}
		viewBinding.buttonOrder?.setOnClickListener(this)
		title = source.getTitle(this)
		openSourceOrRequestAuth()
	}

	override fun isNsfwContent(): Flow<Boolean> = flowOf(source.isNsfw())

	override fun onOffsetChanged(appBarLayout: AppBarLayout, verticalOffset: Int) {
		val container = viewBinding.containerFilterHeader ?: return
		container.background = if (verticalOffset.absoluteValue < appBarLayout.totalScrollRange) {
			container.context.getThemeColor(materialR.attr.backgroundColor).toDrawable()
		} else {
			viewBinding.collapsingToolbarLayout?.contentScrim
		}
	}

	/**
	 * Only for landscape
	 */
	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val barsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
		viewBinding.cardSide?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
			marginEnd = barsInsets.end(v) + resources.getDimensionPixelOffset(R.dimen.side_card_offset)
			topMargin = barsInsets.top + resources.getDimensionPixelOffset(R.dimen.grid_spacing_outer_double)
			bottomMargin = barsInsets.bottom + resources.getDimensionPixelOffset(R.dimen.side_card_offset)
		}
		viewBinding.appbar.updatePaddingRelative(
			top = barsInsets.top,
			end = if (viewBinding.cardSide == null) barsInsets.end(v) else 0,
			start = barsInsets.start(v),
		)
		return insets.consumeSystemBarsInsets(v, top = true, end = true)
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.button_order -> router.showFilterSheet()
		}
	}

	fun showPreview(manga: Manga): Boolean = setSideFragment(
		PreviewFragment::class.java,
		bundleOf(AppRouter.KEY_MANGA to ParcelableManga(manga)),
	)

	fun hidePreview() = setSideFragment(FilterSheetFragment::class.java, null)

	private fun openSourceOrRequestAuth() {
		val authProvider = (repositoryFactory.create(source) as? ParserMangaRepository)
			?.getAuthProvider() as? MangaParserCredentialsAuthProvider
		if (authProvider == null) {
			initListOnce()
			return
		}
		lifecycleScope.launch {
			val isAuthorized = withContext(Dispatchers.IO) {
				runCatching { authProvider.isAuthorized() }.getOrDefault(false)
			}
			if (isAuthorized) {
				initListOnce()
			} else {
				sourceAuthContract.launch(source)
			}
		}
	}

	private fun initListOnce() {
		if (isListInitialized || isFinishing || isDestroyed) return
		isListInitialized = true
		initList(source, initialFilter, initialSortOrder)
	}

	private fun initList(source: MangaSource, filter: MangaListFilter?, sortOrder: SortOrder?) {
		val fm = supportFragmentManager
		val existingFragment = fm.findFragmentById(R.id.container)
		if (existingFragment is FilterCoordinator.Owner) {
			initFilter(existingFragment)
		} else {
			fm.commit {
				setReorderingAllowed(true)
				val fragment = if (source == LocalMangaSource) {
					LocalListFragment()
				} else {
					RemoteListFragment.newInstance(source)
				}
				replace(R.id.container, fragment)
				runOnCommit { initFilter(fragment) }
				if (filter != null || sortOrder != null) {
					runOnCommit(ApplyFilterRunnable(fragment, filter, sortOrder))
				}
			}
		}
	}

	private fun initFilter(filterOwner: FilterCoordinator.Owner) {
		if (viewBinding.containerSide != null) {
			if (supportFragmentManager.findFragmentById(R.id.container_side) == null) {
				setSideFragment(FilterSheetFragment::class.java, null)
			}
		} else if (viewBinding.containerFilterHeader != null) {
			if (supportFragmentManager.findFragmentById(R.id.container_filter_header) == null) {
				supportFragmentManager.commit {
					setReorderingAllowed(true)
					replace(R.id.container_filter_header, FilterHeaderFragment::class.java, null)
				}
			}
		}
		val filter = filterOwner.filterCoordinator
		val chipSort = viewBinding.buttonOrder
		if (chipSort != null) {
			val filterBadge = ViewBadge(chipSort, this)
			filterBadge.setMaxCharacterCount(0)
			filter.observe().observe(this) { snapshot ->
				chipSort.setTextAndVisible(snapshot.sortOrder.titleRes)
				filterBadge.counter = if (snapshot.listFilter.hasNonSearchOptions()) 1 else 0
			}
		} else {
			filter.observe().map {
				it.listFilter.getSummary()
			}.flowOn(Dispatchers.Default)
				.observe(this) {
					supportActionBar?.subtitle = it
				}
		}
	}

	private fun findFilterOwner(): FilterCoordinator.Owner? {
		return supportFragmentManager.findFragmentById(R.id.container) as? FilterCoordinator.Owner
	}

	private fun setSideFragment(cls: Class<out Fragment>, args: Bundle?) = if (viewBinding.containerSide != null) {
		supportFragmentManager.commit {
			setReorderingAllowed(true)
			replace(R.id.container_side, cls, args)
		}
		true
	} else {
		false
	}

	private class ApplyFilterRunnable(
		private val filterOwner: FilterCoordinator.Owner,
		private val filter: MangaListFilter?,
		private val sortOrder: SortOrder?,
	) : Runnable {

		override fun run() {
			if (sortOrder != null) {
				filterOwner.filterCoordinator.setSortOrder(sortOrder)
			}
			if (filter != null) {
				filterOwner.filterCoordinator.setAdjusted(filter)
			}
		}
	}
}
