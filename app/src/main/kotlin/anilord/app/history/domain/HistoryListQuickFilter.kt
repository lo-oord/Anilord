package anilord.app.history.domain

import anilord.app.core.os.NetworkState
import anilord.app.core.prefs.AppSettings
import anilord.app.history.data.HistoryRepository
import anilord.app.list.domain.ListFilterOption
import anilord.app.list.domain.MangaListQuickFilter
import javax.inject.Inject

class HistoryListQuickFilter @Inject constructor(
	private val settings: AppSettings,
	private val repository: HistoryRepository,
	networkState: NetworkState,
) : MangaListQuickFilter(settings) {

	init {
		setFilterOption(ListFilterOption.Downloaded, !networkState.value)
	}

	override suspend fun getAvailableFilterOptions(): List<ListFilterOption> = buildList {
		add(ListFilterOption.Downloaded)
		if (settings.isTrackerEnabled) {
			add(ListFilterOption.Macro.NEW_CHAPTERS)
		}
		add(ListFilterOption.Macro.COMPLETED)
		add(ListFilterOption.Macro.FAVORITE)
		add(ListFilterOption.NOT_FAVORITE)
		if (!settings.isNsfwContentDisabled) {
			add(ListFilterOption.Macro.NSFW)
		}
		repository.getPopularTags(3).mapTo(this) {
			ListFilterOption.Tag(it)
		}
		repository.getPopularSources(3).mapTo(this) {
			ListFilterOption.Source(it)
		}
	}
}
