package org.koitharu.kotatsu.parsers.site.anime.ar

import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64

internal class AnimePhoenixTest {

	@Test
	fun extractsAjaxConfiguration() {
		val document = Jsoup.parse(
			"""
			<script>
			  var fjSearchPageData = {
			    "ajax_url":"https:\/\/anime-phoenix.com\/wp-admin\/admin-ajax.php",
			    "nonce":"abc123",
			    "page":1
			  };
			</script>
			""".trimIndent(),
		)

		val config = AnimePhoenix.extractSearchConfig(document)

		assertNotNull(config)
		assertEquals("abc123", config?.optString("nonce"))
		assertEquals(
			"https://anime-phoenix.com/wp-admin/admin-ajax.php",
			config?.optString("ajax_url"),
		)
	}

	@Test
	fun extractsNestedConfigurationWhenStringsContainBraces() {
		val script = """
			const before = {"ignored":true};
			window.fjSearchPageData = {
			  "ajax_url":"https:\/\/anime-phoenix.com\/wp-admin\/admin-ajax.php",
			  "nonce":"abc}123",
			  "nested":{"message":"keep { this } text"}
			};
			const after = {"ignored":true};
		""".trimIndent()

		val raw = AnimePhoenix.extractAssignedJsonObject(script, "fjSearchPageData")
		val config = raw?.let { org.json.JSONObject(it) }

		assertEquals("abc}123", config?.optString("nonce"))
		assertEquals(
			"keep { this } text",
			config?.optJSONObject("nested")?.optString("message"),
		)
	}

	@Test
	fun findsSeriesMetadataWithoutPickingBreadcrumbJson() {
		val document = Jsoup.parse(
			"""
			<script type="application/ld+json">
			  {"@type":"BreadcrumbList","name":"Wrong"}
			</script>
			<script type="application/ld+json">
			  {"@type":"TVSeries","name":"Sentenced to Be a Hero","genre":["Action"]}
			</script>
			""".trimIndent(),
		)

		assertEquals("Sentenced to Be a Hero", AnimePhoenix.findSeriesJson(document)?.optString("name"))
	}

	@Test
	fun decodesCurrentServerPayloadFormat() {
		val json = """
			{"name":"Phoenix Zenith","type":"direct","link":"https:\/\/cdn.example.com\/anime%20episode%20-%201080p.mkv"}
		""".trimIndent()
		val percentEncoded = URLEncoder.encode(json, StandardCharsets.UTF_8.name())
			.replace("+", "%20")
		val encoded = Base64.getEncoder().encodeToString(
			percentEncoded.toByteArray(StandardCharsets.UTF_8),
		)

		val server = AnimePhoenix.decodeServerPayload(encoded)

		assertEquals("Phoenix Zenith", server?.optString("name"))
		assertEquals(
			"https://cdn.example.com/anime%20episode%20-%201080p.mkv",
			server?.optString("link"),
		)
		assertEquals("1080p", AnimePhoenix.detectQuality(server?.optString("link").orEmpty()))
	}
}
