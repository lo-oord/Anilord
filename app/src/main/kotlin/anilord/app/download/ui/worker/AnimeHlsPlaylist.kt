package anilord.app.download.ui.worker

/** Small, non-executing HLS helpers used by the offline anime downloader. */
internal object AnimeHlsPlaylist {

	fun selectVariant(playlist: String, preferredHeight: Int): String? {
		val lines = playlist.lineSequence().map(String::trim).toList()
		val variants = buildList {
			for (index in lines.indices) {
				val info = lines[index]
				if (!info.startsWith("#EXT-X-STREAM-INF:", ignoreCase = true)) continue
				val uri = lines.drop(index + 1).firstOrNull { it.isNotEmpty() && !it.startsWith('#') }
					?: continue
				val height = RESOLUTION.find(info)?.groupValues?.getOrNull(1)?.toIntOrNull()
				val bandwidth = BANDWIDTH.find(info)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
				add(Variant(uri, height, bandwidth))
			}
		}
		if (variants.isEmpty()) return null
		val target = preferredHeight.takeIf { it > 0 } ?: 720
		return variants.minWithOrNull(compareBy<Variant>(
			{ qualityPenalty(it.height, target) },
			{ it.bandwidth },
		))?.uri
	}

	fun uriAttribute(line: String): String? = URI_ATTRIBUTE.find(line)?.let { match ->
		match.groupValues[1].ifEmpty { match.groupValues[2].trim() }.takeIf(String::isNotEmpty)
	}

	fun replaceUriAttribute(line: String, localName: String): String =
		URI_ATTRIBUTE.replaceFirst(line, "URI=\"$localName\"")

	fun extension(url: String, fallback: String): String {
		val path = url.substringBefore('?').substringBefore('#')
		return path.substringAfterLast('.', "")
			.lowercase()
			.takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) }
			?: fallback
	}

	private fun qualityPenalty(height: Int?, target: Int): Int = when (height) {
		null -> 1_500
		in 1..target -> target - height
		else -> 2_000 + (height - target)
	}

	private data class Variant(val uri: String, val height: Int?, val bandwidth: Long)

	private val RESOLUTION = Regex("""RESOLUTION=\d+x(\d+)""", RegexOption.IGNORE_CASE)
	private val BANDWIDTH = Regex("""(?:AVERAGE-)?BANDWIDTH=(\d+)""", RegexOption.IGNORE_CASE)
	private val URI_ATTRIBUTE = Regex("""URI=(?:\"([^\"]+)\"|([^,\s]+))""", RegexOption.IGNORE_CASE)
}
