package org.manga.peak.favourites.ui

import android.os.Bundle
import org.manga.peak.core.nav.AppRouter
import org.manga.peak.core.ui.FragmentContainerActivity
import org.manga.peak.favourites.ui.list.FavouritesListFragment

class FavouritesActivity : FragmentContainerActivity(FavouritesListFragment::class.java) {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val categoryTitle = intent.getStringExtra(AppRouter.KEY_TITLE)
		if (categoryTitle != null) {
			title = categoryTitle
		}
	}
}
