package org.manga.peak.details.domain

import org.manga.peak.core.util.LocaleStringComparator
import org.manga.peak.details.ui.model.MangaBranch

class BranchComparator : Comparator<MangaBranch> {

	private val delegate = LocaleStringComparator()

	override fun compare(o1: MangaBranch, o2: MangaBranch): Int = delegate.compare(o1.name, o2.name)
}
