package org.manga.peak.reader.ui.novel

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButtonToggleGroup
import dagger.hilt.android.AndroidEntryPoint
import org.manga.peak.R
import org.manga.peak.core.prefs.AppSettings
import org.manga.peak.core.util.ext.consume
import org.manga.peak.core.util.ext.observe
import org.manga.peak.databinding.SheetNovelReaderConfigBinding
import org.manga.peak.reader.ui.config.BaseReaderConfigSheet
import javax.inject.Inject

@AndroidEntryPoint
class NovelReaderConfigSheet :
	BaseReaderConfigSheet<SheetNovelReaderConfigBinding>(),
	View.OnClickListener,
	MaterialButtonToggleGroup.OnButtonCheckedListener {

	@Inject
	lateinit var settings: AppSettings

	override fun getScreenRotateButton(): View? = viewBinding?.buttonScreenRotate

	override fun getLockRotationSwitch(): CompoundButton? = viewBinding?.switchScreenLockRotation

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	) = SheetNovelReaderConfigBinding.inflate(inflater, container, false)

	override fun onViewBindingCreated(
		binding: SheetNovelReaderConfigBinding,
		savedInstanceState: Bundle?,
	) {
		super.onViewBindingCreated(binding, savedInstanceState)
		observeScreenOrientation()

		// Bookmark
		binding.buttonBookmark.setOnClickListener(this)
		viewModel.isBookmarkAdded.observe(viewLifecycleOwner) {
			binding.buttonBookmark.setText(if (it) R.string.bookmark_remove else R.string.bookmark_add)
			binding.buttonBookmark.setCompoundDrawablesRelativeWithIntrinsicBounds(
				if (it) R.drawable.ic_bookmark_checked else R.drawable.ic_bookmark, 0, 0, 0,
			)
		}

		// Screen rotation
		binding.buttonScreenRotate.setOnClickListener(this)

		// Font size
		binding.buttonFontDecrease.setOnClickListener(this)
		binding.buttonFontIncrease.setOnClickListener(this)
		updateFontSizeText(binding)

		// Color theme
		binding.toggleGroupColorTheme.addOnButtonCheckedListener(this)
		selectColorTheme(binding, settings.novelColorTheme)

		// Font family
		binding.toggleGroupFontFamily.addOnButtonCheckedListener(this)
		selectFontFamily(binding, settings.novelFontFamily)

		binding.switchNovelScrollMode.isChecked = settings.isNovelContinuousChapters
		binding.switchNovelScrollMode.setOnCheckedChangeListener { _, isChecked ->
			settings.isNovelContinuousChapters = isChecked
		}
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val typeMask = WindowInsetsCompat.Type.systemBars()
		viewBinding?.scrollView?.updatePadding(
			bottom = insets.getInsets(typeMask).bottom,
		)
		return insets.consume(v, typeMask, bottom = true)
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.button_bookmark -> {
				viewModel.toggleBookmark()
			}

			R.id.button_screen_rotate -> {
				orientationHelper.isLandscape = !orientationHelper.isLandscape
			}

			R.id.button_font_decrease -> {
				settings.novelFontSize = (settings.novelFontSize - 2).coerceAtLeast(10)
				viewBinding?.let { updateFontSizeText(it) }
			}

			R.id.button_font_increase -> {
				settings.novelFontSize = (settings.novelFontSize + 2).coerceAtMost(40)
				viewBinding?.let { updateFontSizeText(it) }
			}
		}
	}

	override fun onButtonChecked(
		group: MaterialButtonToggleGroup,
		checkedId: Int,
		isChecked: Boolean,
	) {
		if (!isChecked) return
		when (group.id) {
			R.id.toggleGroup_color_theme -> {
				val theme = when (checkedId) {
					R.id.button_theme_black -> 1
					R.id.button_theme_sepia -> 2
					R.id.button_theme_blue -> 3
					R.id.button_theme_green -> 4
					R.id.button_theme_pink -> 5
					else -> 0 // white/normal
				}
				settings.novelColorTheme = theme
			}

			R.id.toggleGroup_font_family -> {
				val family = when (checkedId) {
					R.id.button_font_sans -> "tajawal"
					R.id.button_font_cursive -> "amiri"
					else -> "cairo"
				}
				settings.novelFontFamily = family
			}
		}
	}

	private fun updateFontSizeText(binding: SheetNovelReaderConfigBinding) {
		binding.textViewFontSize.text = settings.novelFontSize.toString()
	}

	private fun selectColorTheme(binding: SheetNovelReaderConfigBinding, theme: Int) {
		val buttonId = when (theme) {
			1 -> R.id.button_theme_black
			2 -> R.id.button_theme_sepia
			3 -> R.id.button_theme_blue
			4 -> R.id.button_theme_green
			5 -> R.id.button_theme_pink
			else -> R.id.button_theme_white
		}
		binding.toggleGroupColorTheme.check(buttonId)
	}

	private fun selectFontFamily(binding: SheetNovelReaderConfigBinding, family: String) {
		val buttonId = when (family) {
			"tajawal", "noto_sans_arabic", "sans-serif" -> R.id.button_font_sans
			"amiri", "cursive" -> R.id.button_font_cursive
			else -> R.id.button_font_serif
		}
		binding.toggleGroupFontFamily.check(buttonId)
	}
}
