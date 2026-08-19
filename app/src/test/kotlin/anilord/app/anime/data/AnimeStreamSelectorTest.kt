package anilord.app.anime.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.koitharu.kotatsu.parsers.model.AnimeStream

class AnimeStreamSelectorTest {

	@Test
	fun prefers720pOver1080p() {
		val streams = listOf(
			AnimeStream("Server 1080p", "https://cdn.example/video_1080p.mp4", quality = "1080p"),
			AnimeStream("Server 720p", "https://cdn.example/video_720p.mp4", quality = "720p"),
		)

		assertEquals("Server 720p", AnimeStreamSelector.orderForPlayback(streams).first().name)
	}

	@Test
	fun avoidsHevcWhenComparableStreamExists() {
		val streams = listOf(
			AnimeStream("HEVC", "https://cdn.example/video_720p_x265.mkv", quality = "720p"),
			AnimeStream("H264", "https://cdn.example/video_720p.mp4", quality = "720p"),
		)

		assertEquals("H264", AnimeStreamSelector.orderForPlayback(streams).first().name)
	}

	@Test
	fun respectsExplicitLowerPreference() {
		val streams = listOf(
			AnimeStream("720", "https://cdn.example/video_720p.mp4"),
			AnimeStream("480", "https://cdn.example/video_480p.mp4"),
		)

		assertEquals("480", AnimeStreamSelector.orderForPlayback(streams, preferredHeight = 480).first().name)
	}
}
