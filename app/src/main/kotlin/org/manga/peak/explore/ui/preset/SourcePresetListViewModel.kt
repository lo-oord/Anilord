package org.manga.peak.explore.ui.preset

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import org.manga.peak.core.prefs.AppSettings
import org.manga.peak.core.ui.BaseViewModel
import org.manga.peak.core.util.ext.MutableEventFlow
import org.manga.peak.core.util.ext.call
import org.manga.peak.explore.data.MangaSourcesRepository
import org.manga.peak.explore.data.SourcePreset
import org.manga.peak.explore.data.SourcePresetsRepository
import javax.inject.Inject

@HiltViewModel
class SourcePresetListViewModel @Inject constructor(
	private val presetsRepository: SourcePresetsRepository,
	private val settings: AppSettings,
	private val sourcesRepository: MangaSourcesRepository,
) : BaseViewModel() {

	val presets: StateFlow<List<SourcePreset>> = presetsRepository.observeAll()
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, emptyList())

	val onPresetDeleted = MutableEventFlow<Unit>()

	val activePresetId: Long
		get() = settings.activeSourcePresetId

	fun setActivePreset(presetId: Long) {
		settings.activeSourcePresetId = presetId
	}

	fun countSourcesForPreset(preset: SourcePreset): Int {
		return preset.sources.size
	}

	fun deletePreset(presetId: Long) {
		launchJob(Dispatchers.Default) {
			if (settings.activeSourcePresetId == presetId) {
				settings.activeSourcePresetId = 0L
			}
			presetsRepository.deletePreset(presetId)
			onPresetDeleted.call(Unit)
		}
	}
}
