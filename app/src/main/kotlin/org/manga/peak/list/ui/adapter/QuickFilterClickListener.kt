package org.manga.peak.list.ui.adapter

import org.manga.peak.list.domain.ListFilterOption

interface QuickFilterClickListener {

	fun onFilterOptionClick(option: ListFilterOption)
}
