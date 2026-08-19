package anilord.app.core.model

import org.koitharu.kotatsu.parsers.model.MangaSource

/**
 * Sources intentionally exposed by the current app UI.
 * Parser implementations remain available internally even when not listed here.
 */
private val visibleSourceNames = setOf(
	"AZORAMOON", // Azorafly (user: Azorafy)
	"TEAMXNOVEL", // Team X Manga / TeamXNovel
	"DESPAIRMANGA",
	"HIJALACOM",
	"HIZOMANGA",
	"KOLNOVEL",
	"LAVATOONS", // Lavatoons (user: LavaScans)
	"MANGALEK",
	"MANGASTARZ",
	"MANGAMELLO_PLUS",
	"PROCHAN",
	"ANIME_RISTO",
	"ANIME_WITCHER",
	"ANIME_SLAYER",
	"ANIME3RB",
	"ANIME_PHOENIX",
	"SEANOVEL",
	"SUNOVELS",
	"CENELE",
	"MARKAZ_RIWAYAT",
	"REWAYAT",
)

fun MangaSource.isVisibleInCurrentUi(): Boolean = name in visibleSourceNames

fun <T : MangaSource> Iterable<T>.filterVisibleSources(): List<T> = filter { it.isVisibleInCurrentUi() }
