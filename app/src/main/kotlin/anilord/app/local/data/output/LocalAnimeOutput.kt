package anilord.app.local.data.output

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import anilord.app.core.model.isLocal
import anilord.app.core.util.MimeTypes
import anilord.app.core.util.ext.MimeType
import anilord.app.core.util.ext.takeIfReadable
import anilord.app.core.util.ext.toFileNameSafe
import anilord.app.local.data.MangaIndex
import anilord.app.local.data.input.LocalMangaParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import java.io.File

/** Stores downloaded anime as directly playable files instead of manga page archives. */
class LocalAnimeOutput private constructor(
	val rootFile: File,
	manga: Manga,
) {

	private val indexFile = File(rootFile, LocalMangaOutput.ENTRY_NAME_INDEX)
	private val index = MangaIndex(indexFile.takeIfReadable()?.readText())

	init {
		require(!manga.isLocal)
		rootFile.mkdirs()
		index.setMangaInfo(manga)
	}

	suspend fun addCover(file: File, type: MimeType?) = runInterruptible(Dispatchers.IO) {
		val extension = MimeTypes.getExtension(type)
		val name = if (extension.isNullOrEmpty()) COVER_NAME else "$COVER_NAME.$extension"
		file.copyTo(File(rootFile, name), overwrite = true)
		index.setCoverEntry(name)
		flushIndex()
	}

	suspend fun addEpisode(chapter: IndexedValue<MangaChapter>, source: File, manifestName: String? = null) =
		runInterruptible(Dispatchers.IO) {
			val oldRelativePath = index.getChapterFileName(chapter.value.id)
			val relativePath: String
			if (source.isDirectory) {
				val directoryName = "episode_${chapter.value.id}"
				val destination = File(rootFile, directoryName)
				if (destination.exists()) destination.deleteRecursively()
				check(source.renameTo(destination) || source.copyRecursively(destination, overwrite = true)) {
					"Cannot store downloaded anime episode"
				}
				relativePath = "$directoryName/${requireNotNull(manifestName)}"
			} else {
				// DownloadWorker stores in-progress files as <name>.<media-ext>.tmp. Using
				// the last extension saved completed videos as episode_<id>.tmp, which makes
				// Media3 unable to infer the offline media type on a number of devices.
				val mediaName = source.name.removeSuffix(TEMP_FILE_SUFFIX)
				val extension = MimeTypes.getNormalizedExtension(mediaName) ?: DEFAULT_VIDEO_EXTENSION
				val fileName = "episode_${chapter.value.id}.$extension"
				val destination = File(rootFile, fileName)
				if (destination.exists()) destination.delete()
				check(source.renameTo(destination) || source.copyTo(destination, overwrite = true).isFile) {
					"Cannot store downloaded anime episode"
				}
				relativePath = fileName
			}
			if (oldRelativePath != null && oldRelativePath != relativePath) {
				val oldTopLevelPath = oldRelativePath.substringBefore('/')
				File(rootFile, oldTopLevelPath).run { delete() || deleteRecursively() }
			}
			if (oldRelativePath != null) index.removeChapter(chapter.value.id)
			index.addChapter(chapter, relativePath)
			flushIndex()
		}

	suspend fun finish() = runInterruptible(Dispatchers.IO) {
		flushIndex()
	}

	private fun flushIndex() {
		indexFile.writeText(index.toString())
	}

	companion object {
		private const val COVER_NAME = "cover"
		private const val DEFAULT_VIDEO_EXTENSION = "mp4"
		private const val TEMP_FILE_SUFFIX = ".tmp"
		private val mutex = Mutex()

		suspend fun getOrCreate(root: File, manga: Manga): LocalAnimeOutput = mutex.withLock {
			var suffix = 0
			var result: LocalAnimeOutput? = null
			val baseName = manga.title.toFileNameSafe()
			while (result == null) {
				val fileName = if (suffix == 0) baseName else "${baseName}_$suffix"
				val directory = File(root, fileName)
				suffix++
				if (!directory.exists()) {
					result = LocalAnimeOutput(directory, manga)
					continue
				}
				if (!directory.isDirectory) continue
				val info = runCatchingCancellable {
					LocalMangaParser(directory).getMangaInfo()
				}.getOrNull()
				if (info?.id == manga.id) {
					result = LocalAnimeOutput(directory, manga)
				}
			}
			result
		}
	}
}
