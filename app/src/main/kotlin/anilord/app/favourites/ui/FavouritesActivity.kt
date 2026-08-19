package anilord.app.favourites.ui

import android.os.Bundle
import anilord.app.core.nav.AppRouter
import anilord.app.core.ui.FragmentContainerActivity
import anilord.app.favourites.ui.list.FavouritesListFragment

class FavouritesActivity : FragmentContainerActivity(FavouritesListFragment::class.java) {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val categoryTitle = intent.getStringExtra(AppRouter.KEY_TITLE)
		if (categoryTitle != null) {
			title = categoryTitle
		}
	}
}
