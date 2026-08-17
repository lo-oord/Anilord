package org.manga.peak.settings.storage

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.manga.peak.core.prefs.AppSettings
import org.manga.peak.core.ui.BaseViewModel
import org.manga.peak.core.util.ext.MutableEventFlow
import org.manga.peak.core.util.ext.call
import org.manga.peak.core.util.ext.isWriteable
import org.manga.peak.local.data.LocalStorageManager
import javax.inject.Inject

@HiltViewModel
class MangaDirectorySelectViewModel @Inject constructor(
	private val storageManager: LocalStorageManager,
	private val settings: AppSettings,
) : BaseViewModel() {

	val items = MutableStateFlow(emptyList<DirectoryModel>())
	val onDismissDialog = MutableEventFlow<Unit>()

	init {
		refresh()
	}

	fun onItemClick(item: DirectoryModel) {
		settings.mangaStorageDir = item.file ?: return
		onDismissDialog.call(Unit)
	}

	fun refresh() {
		launchJob(Dispatchers.Default) {
			val defaultValue = storageManager.getDefaultWriteableDir()
			val available = storageManager.getWriteableDirs()
			items.value = buildList(available.size) {
				available.mapTo(this) { dir ->
					DirectoryModel(
						title = storageManager.getDirectoryDisplayName(dir, isFullPath = false),
						titleRes = 0,
						file = dir,
						isChecked = dir == defaultValue,
						isAvailable = true,
						isRemovable = false,
					)
				}
			}
		}
	}
}
