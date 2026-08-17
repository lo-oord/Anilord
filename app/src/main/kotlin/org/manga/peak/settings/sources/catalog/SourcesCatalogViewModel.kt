package org.manga.peak.settings.sources.catalog

import androidx.annotation.WorkerThread
import androidx.lifecycle.viewModelScope
import androidx.room.invalidationTrackerFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import org.manga.peak.R
import org.manga.peak.core.db.MangaDatabase
import org.manga.peak.core.db.TABLE_SOURCES
import org.manga.peak.core.model.SourceContentType
import org.manga.peak.core.model.getLocaleCode
import org.manga.peak.core.model.sourceContentType
import org.manga.peak.core.prefs.AppSettings
import org.manga.peak.core.ui.BaseViewModel
import org.manga.peak.core.ui.util.ReversibleAction
import org.manga.peak.core.util.ext.MutableEventFlow
import org.manga.peak.core.util.ext.call
import org.manga.peak.core.util.ext.mapSortedByCount
import org.manga.peak.explore.data.MangaSourcesRepository
import org.manga.peak.explore.data.SourcePresetsRepository
import org.manga.peak.explore.data.SourcesSortOrder
import org.manga.peak.list.ui.model.ListModel
import org.manga.peak.list.ui.model.LoadingState
import org.koitharu.kotatsu.parsers.model.MangaSource
import java.util.EnumSet
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SourcesCatalogViewModel @Inject constructor(
	private val repository: MangaSourcesRepository,
	db: MangaDatabase,
	private val settings: AppSettings,
	private val presetsRepository: SourcePresetsRepository,
) : BaseViewModel() {

	val onActionDone = MutableEventFlow<ReversibleAction>()
	val locales: Set<String?> = repository.allMangaSources.mapTo(HashSet<String?>()) { it.getLocaleCode() }.also {
		it.add(null)
	}

	private val searchQuery = MutableStateFlow<String?>(null)
	val appliedFilter = MutableStateFlow(
		SourcesCatalogFilter(
			types = emptySet(),
			locale = Locale.getDefault().language.takeIf { it in locales },
			isNewOnly = false,
		),
	)

	val hasNewSources = repository.observeHasNewSources()
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Lazily, false)

	val contentTypes = MutableStateFlow<List<SourceContentType>>(emptyList())

	private val activePresetId = settings.activeSourcePresetId
	private val presetSources = MutableStateFlow<Set<String>>(emptySet())

	val isPresetMode: Boolean
		get() = activePresetId != 0L

	val content: StateFlow<List<ListModel>> = combine(
		searchQuery,
		appliedFilter,
		presetSources,
		db.invalidationTrackerFlow(TABLE_SOURCES),
	) { q, f, ps, _ ->
		buildSourcesList(f, q, ps)
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, listOf(LoadingState()))

	init {
		repository.clearNewSourcesBadge()
		launchJob(Dispatchers.Default) {
			contentTypes.value = getContentTypes(settings.isNsfwContentDisabled)
			loadActivePreset()
		}
	}

	fun performSearch(query: String?) {
		searchQuery.value = query?.trim()
	}

	fun setLocale(value: String?) {
		appliedFilter.value = appliedFilter.value.copy(locale = value)
	}

	fun addSource(source: MangaSource) {
		launchJob(Dispatchers.Default) {
			val rollback = repository.setSourcesEnabled(setOf(source), true)
			onActionDone.call(ReversibleAction(R.string.source_enabled, rollback))
		}
	}

	fun togglePresetSource(source: MangaSource) {
		if (activePresetId == 0L) return
		launchJob(Dispatchers.Default) {
			val current = presetSources.value.toMutableSet()
			val name = source.name
			if (name in current) {
				current.remove(name)
			} else {
				current.add(name)
			}
			presetsRepository.updatePresetSources(activePresetId, current)
			presetSources.value = current
		}
	}

	fun setContentType(value: SourceContentType, isAdd: Boolean) {
		val filter = appliedFilter.value
		val types = EnumSet.noneOf(SourceContentType::class.java)
		types.addAll(filter.types)
		if (isAdd) {
			types.add(value)
		} else {
			types.remove(value)
		}
		appliedFilter.value = filter.copy(types = types)
	}

	fun setNewOnly(value: Boolean) {
		appliedFilter.value = appliedFilter.value.copy(isNewOnly = value)
	}

	private suspend fun buildSourcesList(
		filter: SourcesCatalogFilter,
		query: String?,
		presetSourceNames: Set<String>,
	): List<SourceCatalogItem> {
		val isPreset = activePresetId != 0L
		val sources = repository.querySources(
			isDisabledOnly = !isPreset,
			isNewOnly = filter.isNewOnly,
			excludeBroken = isPreset,
			types = filter.types,
			query = query,
			locale = filter.locale,
			sortOrder = SourcesSortOrder.ALPHABETIC,
		)
		return if (sources.isEmpty()) {
			listOf(
				if (query == null) {
					SourceCatalogItem.Hint(
						icon = R.drawable.ic_empty_feed,
						title = R.string.no_manga_sources,
						text = R.string.no_manga_sources_catalog_text,
					)
				} else {
					SourceCatalogItem.Hint(
						icon = R.drawable.ic_empty_feed,
						title = R.string.nothing_found,
						text = R.string.no_manga_sources_found,
					)
				},
			)
		} else {
			sources.map {
				SourceCatalogItem.Source(
					source = it,
					isInPreset = isPreset && it.name in presetSourceNames,
				)
			}
		}
	}

	@WorkerThread
	private fun getContentTypes(isNsfwDisabled: Boolean): List<SourceContentType> {
		val result = repository.allMangaSources.mapSortedByCount { it.sourceContentType }
		return if (isNsfwDisabled) {
			result.filterNot { it == SourceContentType.HENTAI }
		} else {
			result
		}
	}

	private suspend fun loadActivePreset() {
		if (activePresetId != 0L) {
			val preset = presetsRepository.getById(activePresetId)
			if (preset != null) {
				presetSources.value = preset.sources
				val presetLocale = preset.languages.firstOrNull()
				if (presetLocale != null && presetLocale in locales) {
					appliedFilter.value = appliedFilter.value.copy(locale = presetLocale)
				}
			}
		}
	}
}
