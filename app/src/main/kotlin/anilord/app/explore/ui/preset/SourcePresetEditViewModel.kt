package anilord.app.explore.ui.preset

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import anilord.app.core.nav.AppRouter
import anilord.app.core.model.filterVisibleSources
import anilord.app.core.model.isNsfw
import anilord.app.core.model.getLocaleCode
import anilord.app.core.prefs.AppSettings
import anilord.app.core.ui.BaseViewModel
import anilord.app.core.util.ext.MutableEventFlow
import anilord.app.core.util.ext.call
import anilord.app.explore.data.SourcePreset
import anilord.app.explore.data.SourcePresetsRepository
import anilord.app.explore.data.MangaSourcesRepository
import javax.inject.Inject

@HiltViewModel
class SourcePresetEditViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val presetsRepository: SourcePresetsRepository,
	private val sourcesRepository: MangaSourcesRepository,
	private val settings: AppSettings,
) : BaseViewModel() {

	private val presetId = savedStateHandle[AppRouter.KEY_ID] ?: NO_ID

	val onSaved = MutableEventFlow<Unit>()
	val preset = MutableStateFlow<SourcePreset?>(null)

	val allLocales: Set<String> = sourcesRepository.allMangaSources.filterVisibleSources()
		.mapNotNullTo(LinkedHashSet()) { it.getLocaleCode()?.takeIf { l -> l.isNotEmpty() } }

	init {
		launchLoadingJob(Dispatchers.Default) {
			preset.value = if (presetId != NO_ID) {
				presetsRepository.getById(presetId)
			} else {
				null
			}
		}
	}

	fun save(title: String, selectedLanguages: Set<String>) {
		launchLoadingJob(Dispatchers.Default) {
			check(title.isNotEmpty())
			if (presetId == NO_ID) {
				val initialSources = getSourcesForLanguages(selectedLanguages)
				presetsRepository.createPreset(title, selectedLanguages, initialSources)
			} else {
				presetsRepository.updatePreset(presetId, title, selectedLanguages)
			}
			onSaved.call(Unit)
		}
	}

	private fun getSourcesForLanguages(languages: Set<String>): Set<String> {
		if (languages.isEmpty()) return emptySet()
		val skipNsfw = settings.isNsfwContentDisabled
		return sourcesRepository.allMangaSources
			.filterVisibleSources()
			.filter { it.getLocaleCode() in languages && (!skipNsfw || !it.isNsfw()) }
			.mapTo(HashSet()) { it.name }
	}

	companion object {
		const val NO_ID = -1L
	}
}
