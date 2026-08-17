package org.manga.peak.reader.ui.novel

import org.manga.peak.core.prefs.AppSettings

data class NovelReaderSettings(
	val fontSize: Int = 18,
	val fontFamily: String = "cairo",
	val lineSpacing: Float = 1.6f,
	val textColor: Int = 0xFF000000.toInt(),
	val backgroundColor: Int = 0xFFFFFFFF.toInt(),
	val textAlign: String = "start",
) {
	companion object {

		fun from(settings: AppSettings): NovelReaderSettings {
			val (textColor, bgColor) = getThemeColors(settings.novelColorTheme)
			return NovelReaderSettings(
				fontSize = settings.novelFontSize,
				fontFamily = settings.novelFontFamily,
				lineSpacing = settings.novelLineSpacing,
				textColor = textColor,
				backgroundColor = bgColor,
				textAlign = settings.novelTextAlign,
			)
		}

		fun getThemeColors(theme: Int): Pair<Int, Int> = when (theme) {
			1 -> 0xFFE0E0E0.toInt() to 0xFF121212.toInt() // أسود (black)
			2 -> 0xFF5B4636.toInt() to 0xFFF5F0E8.toInt() // بني (brown/sepia)
			3 -> 0xFFD4E3FF.toInt() to 0xFF0D1B3E.toInt() // أزرق (blue)
			4 -> 0xFFD0F0D0.toInt() to 0xFF0A2E0A.toInt() // أخضر (green)
			5 -> 0xFF5D2040.toInt() to 0xFFFCE4EC.toInt() // زهري (pink)
			6 -> 0xFFF3E5F5.toInt() to 0xFF2E003E.toInt() // أرجواني (purple)
			else -> 0xFF000000.toInt() to 0xFFFFFFFF.toInt() // عادي (normal/white)
		}
	}
}
