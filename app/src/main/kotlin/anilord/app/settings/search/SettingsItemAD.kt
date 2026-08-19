package anilord.app.settings.search

import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import anilord.app.R
import anilord.app.core.ui.list.AdapterDelegateClickListenerAdapter
import anilord.app.core.ui.list.OnListItemClickListener
import anilord.app.core.util.ext.textAndVisible
import anilord.app.databinding.ItemPreferenceBinding

fun settingsItemAD(
	listener: OnListItemClickListener<SettingsItem>,
) = adapterDelegateViewBinding<SettingsItem, SettingsItem, ItemPreferenceBinding>(
	{ layoutInflater, parent -> ItemPreferenceBinding.inflate(layoutInflater, parent, false) },
) {

	AdapterDelegateClickListenerAdapter(this, listener).attach()
	val breadcrumbsSeparator = getString(R.string.breadcrumbs_separator)

	bind {
		binding.textViewTitle.text = item.title
		binding.textViewSummary.textAndVisible = item.breadcrumbs.joinToString(breadcrumbsSeparator)
	}
}
