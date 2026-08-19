package anilord.app.picker.ui

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import anilord.app.core.ui.BaseViewModel
import anilord.app.core.util.ext.MutableEventFlow
import anilord.app.core.util.ext.call
import anilord.app.reader.ui.PageSaveHelper
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PageImagePickViewModel @Inject constructor() : BaseViewModel() {

	val onFileReady = MutableEventFlow<File>()

	fun savePageToTempFile(pageSaveHelper: PageSaveHelper, task: PageSaveHelper.Task) {
		launchLoadingJob(Dispatchers.Default) {
			val file = pageSaveHelper.saveToTempFile(task)
			onFileReady.call(file)
		}
	}
}
