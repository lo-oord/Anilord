package anilord.app.list.ui

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import anilord.app.R
import anilord.app.core.nav.router
import anilord.app.favourites.ui.list.FavouritesListFragment
import anilord.app.history.ui.HistoryListFragment
import anilord.app.list.ui.config.ListConfigSection
import anilord.app.suggestions.ui.SuggestionsFragment
import anilord.app.tracker.ui.updates.UpdatesFragment

class MangaListMenuProvider(
	private val fragment: Fragment,
) : MenuProvider {

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		menuInflater.inflate(R.menu.opt_list, menu)
	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
		R.id.action_list_mode -> {
			val section: ListConfigSection = when (fragment) {
				is HistoryListFragment -> ListConfigSection.History
				is SuggestionsFragment -> ListConfigSection.Suggestions
				is FavouritesListFragment -> ListConfigSection.Favorites(fragment.categoryId)
				is UpdatesFragment -> ListConfigSection.Updated
				else -> ListConfigSection.General
			}
			fragment.router.showListConfigSheet(section)
			true
		}

		else -> false
	}
}
