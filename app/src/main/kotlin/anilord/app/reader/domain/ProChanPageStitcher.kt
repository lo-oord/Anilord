package anilord.app.reader.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import org.json.JSONArray
import anilord.app.core.image.BitmapDecoderCompat
import anilord.app.core.network.MangaHttpClient
import anilord.app.core.network.imageproxy.ImageProxyInterceptor
import anilord.app.core.util.ext.ensureRamAtLeast
import anilord.app.core.util.ext.ensureSuccess
import anilord.app.local.data.LocalStorageCache
import anilord.app.local.data.PageCache
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.requireBody
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject

/**
 * Turns ProChan's private page descriptor into a normal cached PNG.
 *
 * ProChan protects chapter pages by returning shuffled image tiles. The parser
 * describes how to restore them using [SCHEME], which must never be passed to
 * OkHttp or the disk cache directly because it is not an HTTP URL.
 */
@Reusable
class ProChanPageStitcher @Inject constructor(
	@ApplicationContext private val context: Context,
	@MangaHttpClient private val okHttp: OkHttpClient,
	@PageCache private val cache: LocalStorageCache,
	private val imageProxyInterceptor: ImageProxyInterceptor,
) {

	private val stitchLock = Mutex()

	fun isStitchUrl(url: String): Boolean = url.startsWith(SCHEME, ignoreCase = true)

	suspend fun load(
		mapUrl: String,
		source: MangaSource,
		skipCache: Boolean = false,
		onProgress: (Float) -> Unit = {},
	): File {
		require(isStitchUrl(mapUrl)) { "Unsupported ProChan page descriptor: $mapUrl" }
		val cacheKey = cacheKey(mapUrl)
		if (!skipCache) {
			cache.get(cacheKey)?.let { return it }
		}
		return stitchLock.withLock {
			if (!skipCache) {
				cache.get(cacheKey)?.let { return@withLock it }
			}
			stitch(mapUrl, cacheKey, source, onProgress)
		}
	}

	private suspend fun stitch(
		mapUrl: String,
		cacheKey: String,
		source: MangaSource,
		onProgress: (Float) -> Unit,
	): File {
		val uri = Uri.parse(mapUrl)
		val mode = uri.getQueryParameter("mode") ?: "vertical"
		val encodedPieces = uri.getQueryParameter("pieces") ?: error("Missing ProComic pieces")
		val pieceUrls = String(
			Base64.getUrlDecoder().decode(encodedPieces),
			Charsets.UTF_8,
		).split('|').filter(String::isNotBlank)
		check(pieceUrls.isNotEmpty()) { "Empty ProComic stitch map" }
		val targetWidth = uri.getQueryParameter("w")?.toIntOrNull()?.coerceAtLeast(1)
		val targetHeight = uri.getQueryParameter("h")?.toIntOrNull()?.coerceAtLeast(1)
		val layoutRects = uri.getQueryParameter("rects")?.let { encodedRects ->
			runCatching {
				val json = JSONArray(
					String(
						Base64.getUrlDecoder().decode(encodedRects),
						Charsets.UTF_8,
					),
				)
				List(json.length()) { index ->
					val item = json.getJSONObject(index)
					val left = item.optInt("left").coerceAtLeast(0)
					val top = item.optInt("top").coerceAtLeast(0)
					val width = item.optInt("width").coerceAtLeast(1)
					val height = item.optInt("height").coerceAtLeast(1)
					Rect(left, top, left + width, top + height)
				}
			}.getOrNull()
		}

		data class PieceFile(val file: File, val isTemporary: Boolean)

		val pieceFiles = ArrayList<PieceFile>(pieceUrls.size)
		try {
			pieceUrls.forEachIndexed { index, pieceUrl ->
				require(pieceUrl.startsWith("http://") || pieceUrl.startsWith("https://")) {
					"Invalid ProComic image tile URL: $pieceUrl"
				}
				val cachedPiece = cache.get(pieceUrl)
				if (cachedPiece != null) {
					pieceFiles += PieceFile(cachedPiece, isTemporary = false)
				} else {
					val tempFile = File.createTempFile("prochan_piece_${index}_", ".img", context.cacheDir)
					val request = PageLoader.createPageRequest(pieceUrl, source)
					imageProxyInterceptor.interceptPageRequest(request, okHttp).ensureSuccess().use { response ->
						runInterruptible(Dispatchers.IO) {
							response.requireBody().byteStream().use { input ->
								tempFile.outputStream().use(input::copyTo)
							}
						}
					}
					pieceFiles += PieceFile(tempFile, isTemporary = true)
				}
				onProgress((index + 1f) / pieceUrls.size * 0.75f)
			}

			val stitched = runInterruptible(Dispatchers.IO) {
				val bitmaps = pieceFiles.map { BitmapDecoderCompat.decode(it.file) }
				try {
					createStitchedBitmap(
						bitmaps = bitmaps,
						mode = mode,
						targetWidth = targetWidth,
						targetHeight = targetHeight,
						layoutRects = layoutRects,
					)
				} finally {
					bitmaps.forEach(Bitmap::recycle)
				}
			}

			try {
				onProgress(0.9f)
				return cache.set(cacheKey, stitched).also { onProgress(1f) }
			} finally {
				stitched.recycle()
			}
		} finally {
			pieceFiles.forEach { piece ->
				if (piece.isTemporary) piece.file.delete()
			}
		}
	}

	private fun createStitchedBitmap(
		bitmaps: List<Bitmap>,
		mode: String,
		targetWidth: Int?,
		targetHeight: Int?,
		layoutRects: List<Rect>?,
	): Bitmap {
		if (
			targetWidth != null &&
			targetHeight != null &&
			layoutRects?.size == bitmaps.size
		) {
			context.ensureRamAtLeast(targetWidth.toLong() * targetHeight.toLong() * 4L)
			return Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888).also { result ->
				val canvas = Canvas(result)
				bitmaps.forEachIndexed { index, bitmap ->
					canvas.drawBitmap(bitmap, null, layoutRects[index], null)
				}
			}
		}

		val (columns, rows) = proChanGridForMode(mode, bitmaps.size)
		val columnWidths = IntArray(columns)
		val rowHeights = IntArray(rows)
		bitmaps.forEachIndexed { index, bitmap ->
			val column = index % columns
			val row = index / columns
			if (row < rows) {
				columnWidths[column] = maxOf(columnWidths[column], bitmap.width)
				rowHeights[row] = maxOf(rowHeights[row], bitmap.height)
			}
		}
		val totalWidth = columnWidths.sum().coerceAtLeast(1)
		val totalHeight = rowHeights.sum().coerceAtLeast(1)
		context.ensureRamAtLeast(totalWidth.toLong() * totalHeight.toLong() * 4L)
		return Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888).also { result ->
			val canvas = Canvas(result)
			val columnOffsets = offsetsOf(columnWidths)
			val rowOffsets = offsetsOf(rowHeights)
			bitmaps.forEachIndexed { index, bitmap ->
				val column = index % columns
				val row = index / columns
				if (row < rows) {
					canvas.drawBitmap(
						bitmap,
						columnOffsets[column].toFloat(),
						rowOffsets[row].toFloat(),
						null,
					)
				}
			}
		}
	}

	private fun offsetsOf(sizes: IntArray): IntArray {
		var offset = 0
		return IntArray(sizes.size) { index ->
			offset.also { offset += sizes[index] }
		}
	}

	private fun cacheKey(mapUrl: String): String {
		val digest = MessageDigest.getInstance("SHA-256")
			.digest(mapUrl.toByteArray(Charsets.UTF_8))
		val hash = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
		return "https://prochan-map.invalid/stitch/$hash.png"
	}

	companion object {
		const val SCHEME = "prochan-map://stitch"
	}
}

internal fun proChanGridForMode(mode: String, pieceCount: Int): Pair<Int, Int> {
	val safePieceCount = pieceCount.coerceAtLeast(1)
	return when {
		mode.startsWith("grid_") -> {
			val dimensions = mode.substringAfter("grid_").split('x')
			val rows = dimensions.getOrNull(0)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
			val columns = dimensions.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1)
				?: safePieceCount
			columns to rows
		}
		mode.startsWith("horizontal") -> safePieceCount to 1
		else -> 1 to safePieceCount
	}
}
