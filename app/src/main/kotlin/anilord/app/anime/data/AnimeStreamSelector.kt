package anilord.app.anime.data

import org.koitharu.kotatsu.parsers.model.AnimeStream
import java.util.Locale

/** Orders fixed anime streams with a battery-friendly quality and codec first. */
object AnimeStreamSelector {

	const val DEFAULT_PREFERRED_HEIGHT = 720

	fun orderForPlayback(
		streams: List<AnimeStream>,
		preferredHeight: Int = DEFAULT_PREFERRED_HEIGHT,
	): List<AnimeStream> {
		val targetHeight = preferredHeight.takeIf { it > 0 } ?: DEFAULT_PREFERRED_HEIGHT
		return streams.withIndex()
			.sortedWith(compareBy<IndexedValue<AnimeStream>>(
				{ streamScore(it.value, targetHeight) },
				{ it.index },
			))
			.map(IndexedValue<AnimeStream>::value)
	}

	fun qualityHeight(stream: AnimeStream): Int? {
		val text = listOfNotNull(stream.quality, stream.name, stream.url).joinToString(" ")
		return QUALITY_HEIGHT.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
	}

	private fun streamScore(stream: AnimeStream, preferredHeight: Int): Int {
		val heightPenalty = when (val height = qualityHeight(stream)) {
			null -> UNKNOWN_QUALITY_PENALTY
			in 1..preferredHeight -> preferredHeight - height
			else -> ABOVE_PREFERRED_QUALITY_PENALTY + (height - preferredHeight)
		}
		val description = "${stream.name} ${stream.quality.orEmpty()} ${stream.url}"
			.lowercase(Locale.ROOT)
		val codecPenalty = if (HEVC_MARKERS.any(description::contains)) HEVC_PENALTY else 0
		return codecPenalty + heightPenalty
	}

	private const val UNKNOWN_QUALITY_PENALTY = 1_500
	private const val ABOVE_PREFERRED_QUALITY_PENALTY = 2_000
	private const val HEVC_PENALTY = 4_000
	private val HEVC_MARKERS = arrayOf("x265", "h265", "h.265", "hevc")
	private val QUALITY_HEIGHT = Regex("""(?:^|\D)(\d{3,4})\s*p?(?:\D|$)""", RegexOption.IGNORE_CASE)
}
