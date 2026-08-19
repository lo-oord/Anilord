package anilord.app.list.ui.adapter

import anilord.app.list.domain.ListFilterOption

interface QuickFilterClickListener {

	fun onFilterOptionClick(option: ListFilterOption)
}
