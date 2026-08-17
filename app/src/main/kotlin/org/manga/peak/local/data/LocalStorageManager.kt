package org.manga.peak.local.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.StatFs
import androidx.annotation.WorkerThread
import dagger.Reusable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.Cache
import org.manga.peak.core.LocalizedAppContext
import org.manga.peak.core.prefs.AppSettings
import org.manga.peak.core.util.ext.computeSize
import org.manga.peak.core.util.ext.getStorageName
import org.manga.peak.core.util.ext.isReadable
import org.manga.peak.core.util.ext.isWriteable
import org.manga.peak.core.util.ext.takeIfWriteable
import org.koitharu.kotatsu.parsers.util.mapToSet
import java.io.File
import javax.inject.Inject

private const val DIR_NAME = "manga"
private const val CACHE_DISK_PERCENTAGE = 0.02
private const val CACHE_SIZE_MIN: Long = 10 * 1024 * 1024 // 10MB
private const val CACHE_SIZE_MAX: Long = 250 * 1024 * 1024 // 250MB

@Reusable
class LocalStorageManager @Inject constructor(
    @LocalizedAppContext private val context: Context,
    private val settings: AppSettings,
) {

	val contentResolver: ContentResolver
		get() = context.contentResolver

	@WorkerThread
	fun createHttpCache(): Cache {
		val directory = File(context.externalCacheDir ?: context.cacheDir, "http")
		directory.mkdirs()
		val maxSize = calculateDiskCacheSize(directory)
		return Cache(directory, maxSize)
	}

	suspend fun computeCacheSize(cache: CacheDir) = withContext(Dispatchers.IO) {
		getCacheDirs(cache.dir).sumOf { it.computeSize() }
	}

	suspend fun computeCacheSize() = withContext(Dispatchers.IO) {
		getCacheDirs().sumOf { it.computeSize() }
	}

	suspend fun computeStorageSize() = withContext(Dispatchers.IO) {
		getConfiguredStorageDirs().sumOf { it.computeSize() }
	}

	suspend fun computeAvailableSize() = runInterruptible(Dispatchers.IO) {
		getConfiguredStorageDirs().mapToSet { it.freeSpace }.sum()
	}

	suspend fun clearCache(cache: CacheDir) = runInterruptible(Dispatchers.IO) {
		getCacheDirs(cache.dir).forEach { it.deleteRecursively() }
	}

	suspend fun getReadableDirs(): List<File> = runInterruptible(Dispatchers.IO) {
		getConfiguredStorageDirs()
			.filter { it.isReadable() }
	}

	suspend fun getWriteableDirs(): List<File> = runInterruptible(Dispatchers.IO) {
		getConfiguredStorageDirs()
			.filter { it.isWriteable() }
	}

	suspend fun getDefaultWriteableDir(): File? = runInterruptible(Dispatchers.IO) {
		val preferredDir = settings.mangaStorageDir?.takeIfWriteable()
		preferredDir ?: getFallbackStorageDir()?.takeIfWriteable()
	}

	suspend fun getApplicationStorageDirs(): Set<File> = runInterruptible(Dispatchers.IO) {
		getAvailableStorageDirs()
	}

	fun takePermissions(uri: Uri) {
		val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
		contentResolver.takePersistableUriPermission(uri, flags)
	}

	suspend fun getDirectoryDisplayName(dir: File, isFullPath: Boolean): String = runInterruptible(Dispatchers.IO) {
		val packageName = context.packageName
		if (dir.absolutePath.contains(packageName)) {
			dir.getStorageName(context)
		} else if (isFullPath) {
			dir.path
		} else {
			dir.name
		}
	}

	@WorkerThread
	private fun getConfiguredStorageDirs(): MutableSet<File> {
		return getAvailableStorageDirs()
	}

	@WorkerThread
	private fun getAvailableStorageDirs(): MutableSet<File> {
		val result = LinkedHashSet<File>()
		result += File(context.filesDir, DIR_NAME)
		context.getExternalFilesDirs(DIR_NAME).filterNotNullTo(result)
		result.retainAll { it.exists() || it.mkdirs() }
		return result
	}

	@WorkerThread
	private fun getFallbackStorageDir(): File? {
		return context.getExternalFilesDir(DIR_NAME) ?: File(context.filesDir, DIR_NAME).takeIf {
			it.exists() || it.mkdirs()
		}
	}

	@WorkerThread
	private fun getCacheDirs(subDir: String): MutableSet<File> {
		val result = LinkedHashSet<File>()
		result += File(context.cacheDir, subDir)
		context.externalCacheDirs.mapNotNullTo(result) {
			File(it ?: return@mapNotNullTo null, subDir)
		}
		return result
	}

	@WorkerThread
	private fun getCacheDirs(): MutableSet<File> {
		val result = LinkedHashSet<File>()
		result += context.cacheDir
		context.externalCacheDirs.filterNotNullTo(result)
		return result
	}

	private fun calculateDiskCacheSize(cacheDirectory: File): Long {
		return try {
			val cacheDir = StatFs(cacheDirectory.absolutePath)
			val size = CACHE_DISK_PERCENTAGE * cacheDir.blockCountLong * cacheDir.blockSizeLong
			return size.toLong().coerceIn(CACHE_SIZE_MIN, CACHE_SIZE_MAX)
		} catch (_: Exception) {
			CACHE_SIZE_MIN
		}
	}
}
