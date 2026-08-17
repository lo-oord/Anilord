package org.manga.peak.reader.domain

import android.graphics.BitmapFactory
import android.util.Size
import androidx.core.net.toFile
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.OkHttpClient
import org.manga.peak.core.network.MangaHttpClient
import org.manga.peak.core.network.imageproxy.ImageProxyInterceptor
import org.manga.peak.core.parser.MangaDataRepository
import org.manga.peak.core.parser.MangaRepository
import org.manga.peak.core.prefs.AppSettings
import org.manga.peak.core.prefs.ReaderMode
import org.manga.peak.core.util.ext.isFileUri
import org.manga.peak.core.util.ext.isZipUri
import org.manga.peak.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.manga.peak.reader.ui.ReaderState
import java.io.InputStream
import java.util.zip.ZipFile
import javax.inject.Inject
import kotlin.math.roundToInt

class DetectReaderModeUseCase @Inject constructor(
	private val dataRepository: MangaDataRepository,
	private val settings: AppSettings,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	@MangaHttpClient private val okHttpClient: OkHttpClient,
	private val imageProxyInterceptor: ImageProxyInterceptor,
	private val proChanPageStitcher: ProChanPageStitcher,
) {

	suspend operator fun invoke(manga: Manga, state: ReaderState?): ReaderMode {
		dataRepository.getReaderMode(manga.id)?.let { return it }
		if ((manga.source as? MangaParserSource)?.contentType == ContentType.NOVEL) {
			dataRepository.saveReaderMode(manga, ReaderMode.NOVEL)
			return ReaderMode.NOVEL
		}
		val defaultMode = settings.defaultReaderMode
		val chapter = state?.let { manga.findChapterById(it.chapterId) }
			?: manga.chapters?.firstOrNull()
			?: error("There are no chapters in this manga")
		val repo = mangaRepositoryFactory.create(manga.source)
		val pages = repo.getPages(chapter)
		if (pages.any { it.url.endsWith(".html", ignoreCase = true) }) {
			dataRepository.saveReaderMode(manga, ReaderMode.NOVEL)
			return ReaderMode.NOVEL
		}
		if (!settings.isReaderModeDetectionEnabled || defaultMode == ReaderMode.WEBTOON) {
			return defaultMode
		}
		return runCatchingCancellable {
			val isWebtoon = guessMangaIsWebtoon(repo, pages)
			if (isWebtoon) ReaderMode.WEBTOON else defaultMode
		}.onSuccess {
			dataRepository.saveReaderMode(manga, it)
		}.onFailure {
			it.printStackTraceDebug()
		}.getOrDefault(defaultMode)
	}

	/**
	 * Automatic determine type of manga by page size
	 * @return ReaderMode.WEBTOON if page is wide
	 */
	private suspend fun guessMangaIsWebtoon(repository: MangaRepository, pages: List<MangaPage>): Boolean {
		val pageIndex = (pages.size * 0.3).roundToInt()
		val page = requireNotNull(pages.getOrNull(pageIndex)) { "No pages" }
		val url = repository.getPageUrl(page)
		val uri = url.toUri()

		val size = when {
			proChanPageStitcher.isStitchUrl(url) -> {
				val file = proChanPageStitcher.load(url, page.source)
				runInterruptible(Dispatchers.IO) {
					file.inputStream().use {
						getBitmapSize(it)
					}
				}
			}

			uri.isZipUri() -> runInterruptible(Dispatchers.IO) {
				ZipFile(uri.schemeSpecificPart).use { zip ->
					val entry = zip.getEntry(uri.fragment)
					zip.getInputStream(entry).use {
						getBitmapSize(it)
					}
				}
			}

			uri.isFileUri() -> runInterruptible(Dispatchers.IO) {
				uri.toFile().inputStream().use {
					getBitmapSize(it)
				}
			}

			else -> {
				val request = PageLoader.createPageRequest(url, page.source)
				imageProxyInterceptor.interceptPageRequest(request, okHttpClient).use {
					runInterruptible(Dispatchers.IO) {
						getBitmapSize(it.body?.byteStream())
					}
				}
			}
		}
		return size.width * MIN_WEBTOON_RATIO < size.height
	}

	companion object {

		private const val MIN_WEBTOON_RATIO = 1.8

		private fun getBitmapSize(input: InputStream?): Size {
			val options = BitmapFactory.Options().apply {
				inJustDecodeBounds = true
			}
			BitmapFactory.decodeStream(input, null, options)?.recycle()
			val imageHeight: Int = options.outHeight
			val imageWidth: Int = options.outWidth
			check(imageHeight > 0 && imageWidth > 0)
			return Size(imageWidth, imageHeight)
		}
	}
}
