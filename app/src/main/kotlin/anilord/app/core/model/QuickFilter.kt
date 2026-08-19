package anilord.app.core.model

import anilord.app.core.ui.widgets.ChipsView
import anilord.app.list.domain.ListFilterOption

fun ListFilterOption.toChipModel(isChecked: Boolean) = ChipsView.ChipModel(
	title = titleText,
	titleResId = titleResId,
	icon = iconResId,
	iconData = getIconData(),
	isChecked = isChecked,
	counter = if (this is ListFilterOption.Branch) chaptersCount else 0,
	data = this,
)
