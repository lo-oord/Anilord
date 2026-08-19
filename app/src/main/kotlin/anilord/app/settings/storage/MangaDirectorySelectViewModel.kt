package anilord.app.settings.storage

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import anilord.app.core.prefs.AppSettings
import anilord.app.core.ui.BaseViewModel
import anilord.app.core.util.ext.MutableEventFlow
import anilord.app.core.util.ext.call
import anilord.app.core.util.ext.isWriteable
import anilord.app.local.data.LocalStorageManager
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
