package anilord.app.download.ui.worker

import org.junit.Assert.assertEquals
import org.junit.Test

class AnimeHlsPlaylistTest {

	@Test
	fun selectsClosestVariantNotAbovePreferredHeight() {
		val playlist = """
			#EXTM3U
			#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=854x480
			480/index.m3u8
			#EXT-X-STREAM-INF:BANDWIDTH=1600000,RESOLUTION=1280x720
			720/index.m3u8
			#EXT-X-STREAM-INF:BANDWIDTH=4000000,RESOLUTION=1920x1080
			1080/index.m3u8
		""".trimIndent()

		assertEquals("720/index.m3u8", AnimeHlsPlaylist.selectVariant(playlist, 720))
	}

	@Test
	fun rewritesQuotedResourceUri() {
		val line = "#EXT-X-KEY:METHOD=AES-128,URI=\"https://cdn.example/key.bin\",IV=0x01"

		assertEquals("https://cdn.example/key.bin", AnimeHlsPlaylist.uriAttribute(line))
		assertEquals(
			"#EXT-X-KEY:METHOD=AES-128,URI=\"key_1.bin\",IV=0x01",
			AnimeHlsPlaylist.replaceUriAttribute(line, "key_1.bin"),
		)
	}
}
