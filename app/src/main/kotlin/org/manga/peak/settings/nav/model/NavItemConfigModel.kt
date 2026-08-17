package org.manga.peak.settings.nav.model

import androidx.annotation.StringRes
import org.manga.peak.core.prefs.NavItem
import org.manga.peak.list.ui.model.ListModel

data class NavItemConfigModel(
	val item: NavItem,
	@StringRes val disabledHintResId: Int,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is NavItemConfigModel && other.item == item
	}
}
