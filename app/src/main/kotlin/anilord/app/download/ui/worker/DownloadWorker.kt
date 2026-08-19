package anilord.app.download.ui.worker

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import dagger.Reusable
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.internal.closeQuietly
import okio.Buffer
import okio.IOException
import okio.buffer
import okio.sink
import okio.use
import anilord.app.R
import anilord.app.anime.data.AnimePlaybackRepository
import anilord.app.anime.data.AnimeStreamSelector
import anilord.app.core.image.BitmapDecoderCompat
import anilord.app.core.model.ids
import anilord.app.core.model.isAnimeSource
import anilord.app.core.model.isLocal
import anilord.app.core.network.MangaHttpClient
import anilord.app.core.network.imageproxy.ImageProxyInterceptor
import anilord.app.core.parser.MangaDataRepository
import anilord.app.core.parser.MangaRepository
import anilord.app.core.prefs.AppSettings
import anilord.app.core.util.MimeTypes
import anilord.app.core.util.Throttler
import anilord.app.core.util.ext.MimeType
import anilord.app.core.util.ext.awaitFinishedWorkInfosByTag
import anilord.app.core.util.ext.awaitUpdateWork
import anilord.app.core.util.ext.awaitWorkInfosByTag
import anilord.app.core.util.ext.cancellable
import anilord.app.core.util.ext.deleteAwait
import anilord.app.core.util.ext.deleteWork
import anilord.app.core.util.ext.deleteWorks
import anilord.app.core.util.ext.ensureSuccess
import anilord.app.core.util.ext.getDisplayMessage
import anilord.app.core.util.ext.getWorkInputData
import anilord.app.core.util.ext.getWorkSpec
import anilord.app.core.util.ext.openSource
import anilord.app.core.util.ext.printStackTraceDebug
import anilord.app.core.util.ext.toFileOrNull
import anilord.app.core.util.ext.toMimeType
import anilord.app.core.util.ext.toMimeTypeOrNull
import anilord.app.core.util.ext.withTicker
import anilord.app.core.util.ext.writeAllCancellable
import anilord.app.core.util.progress.RealtimeEtaEstimator
import anilord.app.download.domain.DownloadProgress
import anilord.app.download.domain.DownloadState
import anilord.app.local.data.LocalMangaRepository
import anilord.app.local.data.LocalStorageCache
import anilord.app.local.data.LocalStorageChanges
import anilord.app.local.data.PageCache
import anilord.app.local.data.TempFileFilter
import anilord.app.local.data.input.LocalMangaParser
import anilord.app.local.data.output.LocalAnimeOutput
import anilord.app.local.data.output.LocalMangaOutput
import anilord.app.local.domain.MangaLock
import anilord.app.local.domain.model.LocalManga
import org.koitharu.kotatsu.parsers.exception.TooManyRequestExceptions
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.AnimeStream
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.ifNullOrEmpty
import org.koitharu.kotatsu.parsers.util.await
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.requireBody
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import anilord.app.reader.domain.PageLoader
import anilord.app.reader.domain.ProChanPageStitcher
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@HiltWorker
class DownloadWorker @AssistedInject constructor(
	@Assisted appContext: Context,
	@Assisted params: WorkerParameters,
	@MangaHttpClient private val okHttp: OkHttpClient,
	@PageCache private val cache: LocalStorageCache,
	private val localMangaRepository: LocalMangaRepository,
	private val mangaLock: MangaLock,
	private val mangaDataRepository: MangaDataRepository,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val settings: AppSettings,
	@LocalStorageChanges private val localStorageChanges: MutableSharedFlow<LocalManga?>,
	private val slowdownDispatcher: DownloadSlowdownDispatcher,
	private val imageProxyInterceptor: ImageProxyInterceptor,
	private val proChanPageStitcher: ProChanPageStitcher,
	notificationFactoryFactory: DownloadNotificationFactory.Factory,
) : CoroutineWorker(appContext, params) {

	private val task = DownloadTask(params.inputData)
	private val notificationFactory = notificationFactoryFactory.create(uuid = params.id, isSilent = task.isSilent)
	private val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

	@Volatile
	private var lastPublishedState: DownloadState? = null
	private val currentState: DownloadState
		get() = checkNotNull(lastPublishedState)

	private val etaEstimator = RealtimeEtaEstimator()
	private val notificationThrottler = Throttler(400)

	override suspend fun doWork(): Result {
		setForeground(getForegroundInfo())
		val manga = mangaDataRepository.findMangaById(task.mangaId, withChapters = true) ?: return Result.failure()
		publishState(DownloadState(manga = manga, isIndeterminate = true).also { lastPublishedState = it })
		val downloadedIds = getDoneChapters(manga)
		return try {
			val pausingHandle = PausingHandle()
			if (task.isPaused) {
				pausingHandle.pause()
			}
			withContext(pausingHandle) {
				if (manga.source.isAnimeSource()) {
					downloadAnimeImpl(manga, task, downloadedIds)
				} else {
					downloadMangaImpl(manga, task, downloadedIds)
				}
			}
			Result.success(currentState.toWorkData())
		} catch (_: CancellationException) {
			withContext(NonCancellable) {
				val notification = notificationFactory.create(currentState.copy(isStopped = true))
				notificationManager.notify(id.hashCode(), notification)
			}
			Result.failure(
				currentState.copy(eta = -1L, isStuck = false).toWorkData(),
			)
		} catch (e: Exception) {
			e.printStackTraceDebug()
			Result.failure(
				currentState.copy(
					error = e,
					errorMessage = e.getDisplayMessage(applicationContext.resources),
					eta = -1L,
					isStuck = false,
				).toWorkData(),
			)
		} finally {
			notificationManager.cancel(id.hashCode())
		}
	}

	override suspend fun getForegroundInfo() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
		ForegroundInfo(
			id.hashCode(),
			notificationFactory.create(lastPublishedState),
			ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
		)
	} else {
		ForegroundInfo(
			id.hashCode(),
			notificationFactory.create(lastPublishedState),
		)
	}

	private suspend fun downloadAnimeImpl(
		subject: Manga,
		task: DownloadTask,
		excludedIds: Set<Long>,
	) {
		var manga = subject
		val chaptersToSkip = excludedIds.toMutableSet()
		val pausingReceiver = PausingReceiver(id, PausingHandle.current())
		mangaLock.withLock(manga) {
			ContextCompat.registerReceiver(
				applicationContext,
				pausingReceiver,
				PausingReceiver.createIntentFilter(id),
				ContextCompat.RECEIVER_NOT_EXPORTED,
			)
			val destination = localMangaRepository.getOutputDir(manga, task.destination)
			checkNotNull(destination) { applicationContext.getString(R.string.cannot_find_available_storage) }
			val tempFiles = ConcurrentLinkedQueue<File>()
			try {
				if (manga.isLocal) {
					manga = localMangaRepository.getRemoteManga(manga)
						?: error("Cannot obtain remote anime instance")
				}
				val repo = mangaRepositoryFactory.create(manga.source)
				val playbackRepo = repo as? AnimePlaybackRepository
					?: error("Anime downloads are not supported by ${manga.source.name}")
				val details = if (manga.chapters.isNullOrEmpty() || manga.description.isNullOrEmpty()) {
					repo.getDetails(manga)
				} else {
					manga
				}
				val output = LocalAnimeOutput.getOrCreate(destination, details)
				val coverUrl = details.largeCoverUrl.ifNullOrEmpty { details.coverUrl }
				if (!coverUrl.isNullOrEmpty()) {
					downloadFile(coverUrl, destination, repo.source, tempFiles).let { file ->
						output.addCover(file, getMediaType(coverUrl, file))
						file.deleteAwait()
					}
				}
				val chapters = getChapters(details, task)
				for ((chapterIndex, chapter) in chapters.withIndex()) {
					checkIsPaused()
					if (chaptersToSkip.remove(chapter.value.id)) {
						publishState(currentState.copy(downloadedChapters = currentState.downloadedChapters + 1))
						continue
					}
					val streams = runFailsafe {
						playbackRepo.getAnimeStreams(chapter.value).ifEmpty {
							throw IOException("No downloadable streams for ${chapter.value.title.orEmpty()}")
						}
					} ?: continue
					val orderedStreams = AnimeStreamSelector.orderForPlayback(
						streams,
						settings.animePlayerQualityHeight,
					)
					var artifact: AnimeDownloadArtifact? = null
					var lastError: Throwable? = null
					for (stream in orderedStreams) {
						checkIsPaused()
						try {
							artifact = downloadAnimeStream(
								stream = stream,
								destination = destination,
								source = repo.source,
								tempFiles = tempFiles,
								chapterIndex = chapterIndex,
								chaptersCount = chapters.size,
							)
							break
						} catch (e: CancellationException) {
							throw e
						} catch (e: Exception) {
							lastError = e
							e.printStackTraceDebug()
						}
					}
					val completed = artifact ?: throw (
						lastError ?: IOException("All anime download servers failed")
					)
					output.addEpisode(chapter, completed.file, completed.manifestName)
					output.finish()
					runCatchingCancellable {
						localStorageChanges.emit(LocalMangaParser(output.rootFile).getManga(withDetails = false))
					}.onFailure(Throwable::printStackTraceDebug)
					publishState(
						currentState.copy(
							downloadedChapters = currentState.downloadedChapters + 1,
							isIndeterminate = true,
							eta = -1L,
							isStuck = false,
						),
					)
				}
				output.finish()
				val localAnime = LocalMangaParser(output.rootFile).getManga(withDetails = false)
				localStorageChanges.emit(localAnime)
				publishState(currentState.copy(localManga = localAnime, eta = -1L, isStuck = false))
			} finally {
				withContext(NonCancellable) {
					applicationContext.unregisterReceiver(pausingReceiver)
					tempFiles.forEach { file ->
						if (file.isDirectory) file.deleteRecursively() else file.deleteAwait()
					}
				}
			}
		}
	}

	private suspend fun downloadMangaImpl(
		subject: Manga,
		task: DownloadTask,
		excludedIds: Set<Long>,
	) {
		var manga = subject
		val chaptersToSkip = excludedIds.toMutableSet()
		val pausingReceiver = PausingReceiver(id, PausingHandle.current())
		mangaLock.withLock(manga) {
			ContextCompat.registerReceiver(
				applicationContext,
				pausingReceiver,
				PausingReceiver.createIntentFilter(id),
				ContextCompat.RECEIVER_NOT_EXPORTED,
			)
			val destination = localMangaRepository.getOutputDir(manga, task.destination)
			checkNotNull(destination) { applicationContext.getString(R.string.cannot_find_available_storage) }
			val tempFiles = ConcurrentLinkedQueue<File>()
			var output: LocalMangaOutput? = null
			try {
				if (manga.isLocal) {
					manga = localMangaRepository.getRemoteManga(manga)
						?: error("Cannot obtain remote manga instance")
				}
				val repo = mangaRepositoryFactory.create(manga.source)
				val mangaDetails = if (manga.chapters.isNullOrEmpty() || manga.description.isNullOrEmpty()) repo.getDetails(manga) else manga
				output = LocalMangaOutput.getOrCreate(
					root = destination,
					manga = mangaDetails,
					format = task.format ?: settings.preferredDownloadFormat,
				)
				val coverUrl = mangaDetails.largeCoverUrl.ifNullOrEmpty { mangaDetails.coverUrl }
				if (!coverUrl.isNullOrEmpty()) {
					downloadFile(coverUrl, destination, repo.source, tempFiles).let { file ->
						output.addCover(file, getMediaType(coverUrl, file))
						file.deleteAwait()
					}
				}
				val isNovel = (mangaDetails.source as? MangaParserSource)?.contentType == ContentType.NOVEL
				val chapters = getChapters(mangaDetails, task)
				for ((chapterIndex, chapter) in chapters.withIndex()) {
					checkIsPaused()
					if (chaptersToSkip.remove(chapter.value.id)) {
						publishState(currentState.copy(downloadedChapters = currentState.downloadedChapters + 1))
						continue
					}
					if (isNovel) {
						val novelContent = runFailsafe {
							repo.getNovelContent(chapter.value)
						} ?: continue
						val htmlFile = File(destination, "novel_chapter_${chapter.value.id}.html")
						runInterruptible(Dispatchers.IO) {
							htmlFile.writeText(novelContent.html, Charsets.UTF_8)
						}
						output.addPage(
							chapter = chapter,
							file = htmlFile,
							pageNumber = 0,
							type = "text/html".toMimeTypeOrNull(),
						)
						htmlFile.deleteAwait()
						if (output.flushChapter(chapter.value)) {
							runCatchingCancellable {
								localStorageChanges.emit(LocalMangaParser(output.rootFile).getManga(withDetails = false))
							}.onFailure(Throwable::printStackTraceDebug)
						}
						publishState(currentState.copy(downloadedChapters = currentState.downloadedChapters + 1))
						continue
					}
					val pages = runFailsafe {
						repo.getPages(chapter.value)
					} ?: continue
					val pageCounter = AtomicInteger(0)
					channelFlow {
						val semaphore = Semaphore(MAX_PAGES_PARALLELISM)
						for ((pageIndex, page) in pages.withIndex()) {
							checkIsPaused()
							launch {
								semaphore.withPermit {
									runFailsafe {
										val url = repo.getPageUrl(page)
										val file = if (proChanPageStitcher.isStitchUrl(url)) {
											proChanPageStitcher.load(url, repo.source)
										} else {
											cache[url] ?: downloadFile(url, destination, repo.source, tempFiles)
										}
										output.addPage(
											chapter = chapter,
											file = file,
											pageNumber = pageIndex,
											type = getMediaType(url, file),
										)
										if (file.extension == "tmp") {
											file.deleteAwait()
										}
									}
									send(pageIndex)
								}
							}
						}
					}.map {
						DownloadProgress(
							totalChapters = chapters.size,
							currentChapter = chapterIndex,
							totalPages = pages.size,
							currentPage = pageCounter.getAndIncrement(),
						)
					}.withTicker(2L, TimeUnit.SECONDS).collect { progress ->
						publishState(
							currentState.copy(
								totalChapters = progress.totalChapters,
								currentChapter = progress.currentChapter,
								totalPages = progress.totalPages,
								currentPage = progress.currentPage,
								isIndeterminate = false,
								eta = etaEstimator.getEta(),
								isStuck = etaEstimator.isStuck(),
							),
						)
					}
					if (output.flushChapter(chapter.value)) {
						runCatchingCancellable {
							localStorageChanges.emit(LocalMangaParser(output.rootFile).getManga(withDetails = false))
						}.onFailure(Throwable::printStackTraceDebug)
					}
					publishState(currentState.copy(downloadedChapters = currentState.downloadedChapters + 1))
				}
				publishState(currentState.copy(isIndeterminate = true, eta = -1L, isStuck = false))
				output.mergeWithExisting()
				output.finish()
				val localManga = LocalMangaParser(output.rootFile).getManga(withDetails = false)
				localStorageChanges.emit(localManga)
				publishState(currentState.copy(localManga = localManga, eta = -1L, isStuck = false))
			} catch (e: Exception) {
				if (e !is CancellationException) {
					publishState(
						currentState.copy(
							error = e,
							errorMessage = e.getDisplayMessage(applicationContext.resources),
						),
					)
				}
				throw e
			} finally {
				withContext(NonCancellable) {
					applicationContext.unregisterReceiver(pausingReceiver)
					output?.closeQuietly()
					output?.cleanup()
					tempFiles.forEach { it.deleteAwait() }
				}
			}
		}
	}

	private suspend fun downloadAnimeStream(
		stream: AnimeStream,
		destination: File,
		source: MangaSource,
		tempFiles: MutableCollection<File>,
		chapterIndex: Int,
		chaptersCount: Int,
	): AnimeDownloadArtifact {
		if (isHlsStream(stream.url)) {
			return downloadHlsStream(
				stream = stream,
				destination = destination,
				source = source,
				tempFiles = tempFiles,
				chapterIndex = chapterIndex,
				chaptersCount = chaptersCount,
			)
		}
		val extension = AnimeHlsPlaylist.extension(stream.url, "mp4")
		val file = downloadFile(
			url = stream.url,
			destination = destination,
			source = source,
			tempFiles = tempFiles,
			requestHeaders = stream.headers,
			preferredExtension = extension,
			useImageProxy = false,
			onProgress = { bytesRead, contentLength ->
				publishAnimeByteProgress(chapterIndex, chaptersCount, bytesRead, contentLength)
			},
		)
		return AnimeDownloadArtifact(file = file, manifestName = null)
	}

	private suspend fun downloadHlsStream(
		stream: AnimeStream,
		destination: File,
		source: MangaSource,
		tempFiles: MutableCollection<File>,
		chapterIndex: Int,
		chaptersCount: Int,
	): AnimeDownloadArtifact {
		val workingDirectory = File(destination, ".anime_${UUID.randomUUID()}.tmp")
		check(workingDirectory.mkdirs()) { "Cannot create temporary anime download directory" }
		tempFiles += workingDirectory
		try {
			val (playlistUrl, playlist) = resolveMediaPlaylist(stream, source)
			val lines = playlist.lineSequence().map { it.trimEnd('\r') }.toList()
			if (lines.any { it.startsWith("#EXT-X-BYTERANGE", ignoreCase = true) }) {
				throw IOException("Byte-range HLS is not supported for offline anime downloads")
			}
			val resourceCount = lines.count { line ->
				val trimmed = line.trim()
				(trimmed.isNotEmpty() && !trimmed.startsWith('#')) ||
					(trimmed.startsWith("#EXT-X-KEY", true) && !trimmed.contains("METHOD=NONE", true)) ||
					trimmed.startsWith("#EXT-X-MAP", true) || trimmed.startsWith("#EXT-X-PART", true)
			}.coerceAtLeast(1)
			val downloadedResources = HashMap<String, String>()
			var resourceIndex = 0

			suspend fun downloadResource(reference: String, fallbackExtension: String): String {
				val absoluteUrl = playlistUrl.toHttpUrlOrNull()?.resolve(reference)?.toString()
					?: throw IOException("Invalid HLS resource URL: $reference")
				downloadedResources[absoluteUrl]?.let { return it }
				val extension = AnimeHlsPlaylist.extension(absoluteUrl, fallbackExtension)
				val temp = downloadFile(
					url = absoluteUrl,
					destination = workingDirectory,
					source = source,
					tempFiles = tempFiles,
					requestHeaders = stream.headers,
					preferredExtension = extension,
					useImageProxy = false,
				)
				val localName = "asset_${resourceIndex.toString().padStart(5, '0')}.$extension"
				val localFile = File(workingDirectory, localName)
				check(temp.renameTo(localFile) || runCatching {
					temp.copyTo(localFile, overwrite = true)
					temp.delete()
					localFile.isFile
				}.getOrDefault(false)) { "Cannot store HLS resource" }
				tempFiles.remove(temp)
				resourceIndex++
				downloadedResources[absoluteUrl] = localName
				publishAnimeUnitProgress(
					chapterIndex = chapterIndex,
					chaptersCount = chaptersCount,
					currentUnit = resourceIndex,
					totalUnits = resourceCount,
				)
				return localName
			}

			val localPlaylist = buildList {
				for (rawLine in lines) {
					val line = rawLine.trim()
					when {
						line.startsWith("#EXT-X-PRELOAD-HINT", true) ||
							line.startsWith("#EXT-X-RENDITION-REPORT", true) -> Unit

						line.startsWith("#EXT-X-KEY", true) && !line.contains("METHOD=NONE", true) -> {
							val uri = AnimeHlsPlaylist.uriAttribute(line)
								?: throw IOException("HLS key has no URI")
							add(AnimeHlsPlaylist.replaceUriAttribute(rawLine, downloadResource(uri, "key")))
						}

						line.startsWith("#EXT-X-MAP", true) || line.startsWith("#EXT-X-PART", true) -> {
							val uri = AnimeHlsPlaylist.uriAttribute(line)
								?: throw IOException("HLS resource has no URI")
							add(AnimeHlsPlaylist.replaceUriAttribute(rawLine, downloadResource(uri, "mp4")))
						}

						line.isNotEmpty() && !line.startsWith('#') -> {
							add(downloadResource(line, "ts"))
						}

						else -> add(rawLine)
					}
				}
				if (none { it.trim().equals("#EXT-X-ENDLIST", true) }) add("#EXT-X-ENDLIST")
			}.joinToString("\n")
			val manifest = File(workingDirectory, OFFLINE_HLS_MANIFEST)
			runInterruptible(Dispatchers.IO) {
				manifest.writeText(localPlaylist, Charsets.UTF_8)
			}
			return AnimeDownloadArtifact(workingDirectory, OFFLINE_HLS_MANIFEST)
		} catch (e: Exception) {
			workingDirectory.deleteRecursively()
			tempFiles.remove(workingDirectory)
			throw e
		}
	}

	private suspend fun resolveMediaPlaylist(
		stream: AnimeStream,
		source: MangaSource,
	): Pair<String, String> {
		var url = stream.url
		repeat(MAX_HLS_PLAYLIST_DEPTH) {
			val playlist = loadText(url, source, stream.headers)
			val variant = AnimeHlsPlaylist.selectVariant(playlist, settings.animePlayerQualityHeight)
				?: return url to playlist
			url = url.toHttpUrlOrNull()?.resolve(variant)?.toString()
				?: throw IOException("Invalid HLS variant URL: $variant")
		}
		throw IOException("HLS playlist nesting is too deep")
	}

	private suspend fun loadText(
		url: String,
		source: MangaSource,
		requestHeaders: Map<String, String>,
	): String {
		val request = PageLoader.createPageRequest(url, source).newBuilder()
			.header("Accept", "application/vnd.apple.mpegurl, application/x-mpegURL, */*")
			.apply { requestHeaders.forEach { (name, value) -> header(name, value) } }
			.build()
		slowdownDispatcher.delay(source)
		return okHttp.newCall(request).await().ensureSuccess().use { response ->
			response.requireBody().string()
		}
	}

	private suspend fun publishAnimeByteProgress(
		chapterIndex: Int,
		chaptersCount: Int,
		bytesRead: Long,
		contentLength: Long,
	) {
		if (contentLength <= 0L) return
		val unit = ((bytesRead.coerceAtMost(contentLength) * ANIME_PROGRESS_UNITS) / contentLength)
			.toInt()
		publishAnimeUnitProgress(chapterIndex, chaptersCount, unit, ANIME_PROGRESS_UNITS)
	}

	private suspend fun publishAnimeUnitProgress(
		chapterIndex: Int,
		chaptersCount: Int,
		currentUnit: Int,
		totalUnits: Int,
	) {
		publishState(
			currentState.copy(
				totalChapters = chaptersCount,
				currentChapter = chapterIndex,
				totalPages = totalUnits.coerceAtLeast(1),
				currentPage = (currentUnit - 1).coerceIn(0, totalUnits.coerceAtLeast(1) - 1),
				isIndeterminate = false,
				eta = etaEstimator.getEta(),
				isStuck = etaEstimator.isStuck(),
			),
		)
	}

	private fun isHlsStream(url: String): Boolean =
		url.substringBefore('?').endsWith(".m3u8", ignoreCase = true) ||
			url.contains("m3u8", ignoreCase = true)

	private data class AnimeDownloadArtifact(val file: File, val manifestName: String?)

	private suspend fun <R> runFailsafe(
		block: suspend () -> R,
	): R? {
		checkIsPaused()
		var countDown = MAX_FAILSAFE_ATTEMPTS
		failsafe@ while (true) {
			try {
				return block()
			} catch (e: IOException) {
				val retryDelay = if (e is TooManyRequestExceptions) {
					e.getRetryDelay()
				} else {
					DOWNLOAD_ERROR_DELAY
				}
				if (countDown <= 0 || retryDelay < 0 || retryDelay > MAX_RETRY_DELAY) {
					val pausingHandle = PausingHandle.current()
					if (pausingHandle.skipAllErrors()) {
						return null
					}
					publishState(
						currentState.copy(
							isPaused = true,
							error = e,
							errorMessage = e.getDisplayMessage(applicationContext.resources),
							eta = -1L,
							isStuck = false,
						),
					)
					countDown = MAX_FAILSAFE_ATTEMPTS
					pausingHandle.pause()
					try {
						pausingHandle.awaitResumed()
						if (pausingHandle.skipCurrentError()) {
							return null
						}
					} finally {
						publishState(currentState.copy(isPaused = false, error = null, errorMessage = null))
					}
				} else {
					countDown--
					delay(retryDelay)
				}
			}
		}
	}

	private suspend fun checkIsPaused() {
		val pausingHandle = PausingHandle.current()
		if (pausingHandle.isPaused) {
			publishState(currentState.copy(isPaused = true, eta = -1L, isStuck = false))
			try {
				pausingHandle.awaitResumed()
			} finally {
				publishState(currentState.copy(isPaused = false))
			}
		}
	}

	private suspend fun getMediaType(url: String, file: File): MimeType? = runInterruptible(Dispatchers.IO) {
		BitmapDecoderCompat.probeMimeType(file)?.let {
			return@runInterruptible it
		}
		MimeTypes.getMimeTypeFromUrl(url)
	}

	private suspend fun downloadFile(
		url: String,
		destination: File,
		source: MangaSource,
		tempFiles: MutableCollection<File>,
		requestHeaders: Map<String, String> = emptyMap(),
		onProgress: (suspend (bytesRead: Long, contentLength: Long) -> Unit)? = null,
		preferredExtension: String? = null,
		useImageProxy: Boolean = true,
	): File {
		if (url.startsWith("content:", ignoreCase = true) || url.startsWith("file:", ignoreCase = true)) {
			val uri = url.toUri()
			val cr = applicationContext.contentResolver
			val ext = uri.toFileOrNull()?.let {
				MimeTypes.getNormalizedExtension(it.name)
			} ?: cr.getType(uri)?.toMimeTypeOrNull()?.let { MimeTypes.getExtension(it) }
			val file = destination.createTempFile(ext)
			tempFiles += file
			try {
				cr.openSource(uri).use { input ->
					file.sink(append = false).buffer().use {
						it.writeAllCancellable(input)
					}
				}
			} catch (e: Exception) {
				file.delete()
				throw e
			}
			return file
		}
		val request = PageLoader.createPageRequest(url, source).newBuilder()
			.header("Accept", "*/*")
			.apply {
				requestHeaders.forEach { (name, value) -> header(name, value) }
			}
			.build()
		slowdownDispatcher.delay(source)
		val response = if (useImageProxy) {
			imageProxyInterceptor.interceptPageRequest(request, okHttp)
		} else {
			// Video manifests and segments are not images. Sending them through the
			// optional image proxy can leave large downloads waiting indefinitely.
			okHttp.newCall(request).await()
		}
		return response
			.ensureSuccess()
			.use { response ->
				var file: File? = null
				try {
					response.requireBody().use { body ->
						file = destination.createTempFile(
							ext = preferredExtension ?: MimeTypes.getExtension(body.contentType()?.toMimeType()),
						).also { tempFiles += it }
						file.sink(append = false).buffer().use { sink ->
							if (onProgress == null) {
								sink.writeAllCancellable(body.source())
							} else {
								val contentLength = body.contentLength()
								withContext(Dispatchers.IO) {
									val source = body.source().cancellable()
									val buffer = Buffer()
									var totalBytesRead = 0L
									while (true) {
										val read = source.read(buffer, ANIME_COPY_BUFFER_SIZE)
										if (read == -1L) break
										sink.write(buffer, read)
										totalBytesRead += read
										onProgress(totalBytesRead, contentLength)
									}
								}
							}
						}
					}
				} catch (e: Exception) {
					file?.delete()
					throw e
				}
				checkNotNull(file)
			}
	}

	private fun File.createTempFile(ext: String?) = File(
		this,
		buildString {
			append(UUID.randomUUID().toString())
			if (!ext.isNullOrEmpty()) {
				append('.')
				append(ext)
			}
			append(".tmp")
		},
	)

	private suspend fun publishState(state: DownloadState) {
		val previousState = currentState
		lastPublishedState = state
		if (previousState.isParticularProgress && state.isParticularProgress) {
			etaEstimator.onProgressChanged(state.progress, state.max)
		} else {
			etaEstimator.reset()
			notificationThrottler.reset()
		}
		val notification = notificationFactory.create(state)
		if (state.isFinalState) {
			if (!notificationFactory.isSilent) {
				notificationManager.notify(id.toString(), id.hashCode(), notification)
			}
		} else if (notificationThrottler.throttle()) {
			notificationManager.notify(id.hashCode(), notification)
		} else {
			return
		}
		setProgress(state.toWorkData())
	}

	private suspend fun getDoneChapters(manga: Manga) = runCatchingCancellable {
		localMangaRepository.getDetails(manga).chapters?.ids()
	}.getOrNull().orEmpty()

	private fun getChapters(
		manga: Manga,
		task: DownloadTask,
	): List<IndexedValue<MangaChapter>> {
		val chapters = checkNotNull(manga.chapters) { "Chapters list must not be null" }
		val chaptersIdsSet = task.chaptersIds?.toMutableSet()
		val result = ArrayList<IndexedValue<MangaChapter>>((chaptersIdsSet ?: chapters).size)
		val counters = HashMap<String?, Int>()
		for (chapter in chapters) {
			val index = counters[chapter.branch] ?: 0
			counters[chapter.branch] = index + 1
			if (chaptersIdsSet != null && !chaptersIdsSet.remove(chapter.id)) {
				continue
			}
			result.add(IndexedValue(index, chapter))
		}
		if (chaptersIdsSet != null) {
			check(chaptersIdsSet.isEmpty()) {
				"${chaptersIdsSet.size} of ${task.chaptersIds.size} requested chapters not found in manga"
			}
		}
		check(result.isNotEmpty()) { "Chapters list must not be empty" }
		return result
	}

	@Reusable
	class Scheduler @Inject constructor(
		@ApplicationContext private val context: Context,
		private val mangaDataRepository: MangaDataRepository,
		private val workManager: WorkManager,
	) {

		fun observeWorks(): Flow<List<WorkInfo>> = workManager
			.getWorkInfosByTagFlow(TAG)

		@SuppressLint("RestrictedApi")
		suspend fun getInputData(id: UUID): Data? {
			val spec = workManager.getWorkSpec(id) ?: return null
			return Data.Builder()
				.putAll(spec.input)
				.putLong(DownloadState.DATA_TIMESTAMP, spec.scheduleRequestedAt)
				.build()
		}

		suspend fun getTask(workId: UUID): DownloadTask? {
			return workManager.getWorkInputData(workId)?.let { DownloadTask(it) }
		}

		suspend fun cancel(id: UUID) {
			workManager.cancelWorkById(id).await()
		}

		suspend fun cancelAll() {
			workManager.cancelAllWorkByTag(TAG).await()
		}

		fun pause(id: UUID) = context.sendBroadcast(
			PausingReceiver.getPauseIntent(context, id),
		)

		fun resume(id: UUID) = context.sendBroadcast(
			PausingReceiver.getResumeIntent(context, id),
		)

		fun skip(id: UUID) = context.sendBroadcast(
			PausingReceiver.getSkipIntent(context, id),
		)

		fun skipAll(id: UUID) = context.sendBroadcast(
			PausingReceiver.getSkipAllIntent(context, id),
		)

		suspend fun delete(id: UUID) {
			workManager.deleteWork(id)
		}

		suspend fun delete(ids: Collection<UUID>) {
			val wm = workManager
			ids.forEach { id -> wm.cancelWorkById(id).await() }
			workManager.deleteWorks(ids)
		}

		suspend fun removeCompleted() {
			val finishedWorks = workManager.awaitFinishedWorkInfosByTag(TAG)
			workManager.deleteWorks(finishedWorks.mapToSet { it.id })
		}

		suspend fun updateConstraints(allowMeteredNetwork: Boolean) {
			val constraints = createConstraints(allowMeteredNetwork)
			val works = workManager.awaitWorkInfosByTag(TAG)
			for (work in works) {
				if (work.state.isFinished) {
					continue
				}
				val request = OneTimeWorkRequestBuilder<DownloadWorker>()
					.setConstraints(constraints)
					.addTag(TAG)
					.setId(work.id)
					.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
					.build()
				workManager.awaitUpdateWork(request)
			}
		}

		suspend fun schedule(tasks: Collection<Pair<Manga, DownloadTask>>) {
			val downloadableTasks = tasks.filterNot { (manga, _) -> manga.isLocal }
			if (downloadableTasks.isEmpty()) {
				return
			}
			val requests = downloadableTasks.map { (manga, task) ->
				mangaDataRepository.storeManga(manga, replaceExisting = true)
				OneTimeWorkRequestBuilder<DownloadWorker>()
					.setConstraints(createConstraints(task.allowMeteredNetwork))
					.addTag(TAG)
					.keepResultsForAtLeast(30, TimeUnit.DAYS)
					.setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
					.setInputData(task.toData())
					.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
					.build()
			}
			workManager.enqueue(requests).await()
		}

		private fun createConstraints(allowMeteredNetwork: Boolean) = Constraints.Builder()
			.setRequiredNetworkType(if (allowMeteredNetwork) NetworkType.CONNECTED else NetworkType.UNMETERED)
			.build()
	}

	private companion object {

		const val MAX_FAILSAFE_ATTEMPTS = 2
		const val MAX_PAGES_PARALLELISM = 4
		const val ANIME_COPY_BUFFER_SIZE = 64L * 1024L
		const val ANIME_PROGRESS_UNITS = 1_000
		const val MAX_HLS_PLAYLIST_DEPTH = 4
		const val OFFLINE_HLS_MANIFEST = "offline.m3u8"
		const val DOWNLOAD_ERROR_DELAY = 2_000L
		const val MAX_RETRY_DELAY = 7_200_000L // 2 hours
		const val TAG = "download"
	}
}
