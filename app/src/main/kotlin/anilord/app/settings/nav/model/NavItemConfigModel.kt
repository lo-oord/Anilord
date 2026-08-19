package anilord.app.settings.nav.model

import androidx.annotation.StringRes
import anilord.app.core.prefs.NavItem
import anilord.app.list.ui.model.ListModel

data class NavItemConfigModel(
	val item: NavItem,
	@StringRes val disabledHintResId: Int,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is NavItemConfigModel && other.item == item
	}
}
