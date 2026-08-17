package org.manga.peak.anime.player

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.manga.peak.R
import org.manga.peak.ads.AdMobManager
import org.manga.peak.anime.data.AnimePlaybackRepository
import org.manga.peak.anime.data.AnimeStreamSelector
import org.manga.peak.core.model.parcelable.ParcelableChapter
import org.manga.peak.core.model.parcelable.ParcelableManga
import org.manga.peak.core.model.isLocal
import org.manga.peak.core.parser.MangaRepository
import org.manga.peak.core.prefs.AppSettings
import org.manga.peak.core.ui.BaseActivity
import org.manga.peak.core.util.ext.consumeAllSystemBarsInsets
import org.manga.peak.core.util.ext.getParcelableArrayListExtraCompat
import org.manga.peak.core.util.ext.getParcelableExtraCompat
import org.manga.peak.core.util.ext.systemBarsInsets
import org.manga.peak.databinding.ActivityAnimePlayerBinding
import org.manga.peak.history.domain.HistoryUpdateUseCase
import org.manga.peak.local.data.LocalMangaRepository
import org.manga.peak.reader.ui.ReaderState
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.AnimeStream
import kotlin.math.abs
import kotlin.math.roundToInt
import javax.inject.Inject

@AndroidEntryPoint
class AnimePlayerActivity : BaseActivity<ActivityAnimePlayerBinding>() {

	@Inject
	lateinit var repositoryFactory: MangaRepository.Factory

	@Inject
	lateinit var historyUpdateUseCase: HistoryUpdateUseCase

	@Inject
	lateinit var settings: AppSettings

	@Inject
	lateinit var localMangaRepository: LocalMangaRepository

	private lateinit var manga: Manga
	private lateinit var episode: MangaChapter
	private var playbackRepository: AnimePlaybackRepository? = null
	private var streams: List<AnimeStream> = emptyList()
	private var selectedStreamIndex = 0
	private var player: ExoPlayer? = null
	private var trackSelector: DefaultTrackSelector? = null
	private var resumePositionMs = 0L
	private var loadJob: Job? = null
	private var streamRefreshAttempts = 0
	private var isRefreshingStreams = false
	private var hasPlaybackStarted = false
	private var playbackSpeed = 1f
	private var resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
	private var autoPlayNextEpisode = true
	private var selectedQualityHeight = -1
	private lateinit var audioManager: AudioManager
	private var controlsLocked = false
	private var gestureMode = GestureMode.NONE
	private var gestureStartX = 0f
	private var gestureStartY = 0f
	private var gestureStartVolume = 0
	private var gestureStartBrightness = 0.5f
	private val hideGestureFeedback = Runnable {
		if (hasViewBinding()) viewBinding.gestureFeedback.isVisible = false
	}
	private val hideLockedControl = Runnable {
		if (hasViewBinding() && controlsLocked) {
			viewBinding.buttonControlsLock.isVisible = false
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val inputManga = intent.getParcelableExtraCompat<ParcelableManga>(EXTRA_MANGA)?.manga
		val inputEpisode = intent.getParcelableExtraCompat<ParcelableChapter>(EXTRA_EPISODE)?.chapter
		if (inputManga == null || inputEpisode == null) {
			finish()
			return
		}
		val inputEpisodes = intent
			.getParcelableArrayListExtraCompat<ParcelableChapter>(EXTRA_EPISODES)
			.orEmpty()
			.map(ParcelableChapter::chapter)
			.distinctBy(MangaChapter::id)
		manga = inputManga.copy(chapters = inputEpisodes.ifEmpty { listOf(inputEpisode) })
		episode = inputEpisode
		playbackRepository = repositoryFactory.create(manga.source) as? AnimePlaybackRepository
		if (playbackRepository == null && !manga.isLocal) {
			finish()
			return
		}
		resumePositionMs = savedInstanceState?.getLong(STATE_POSITION)
			?: intent.getLongExtra(EXTRA_POSITION, 0L)
		playbackSpeed = savedInstanceState?.getFloat(STATE_SPEED) ?: settings.animePlayerSpeed
		resizeMode = savedInstanceState?.getInt(STATE_RESIZE_MODE, AspectRatioFrameLayout.RESIZE_MODE_FIT)
			?: settings.animePlayerResizeMode
		autoPlayNextEpisode = savedInstanceState?.getBoolean(STATE_AUTO_NEXT)
			?: settings.isAnimeAutoNextEnabled
		selectedQualityHeight = savedInstanceState?.getInt(STATE_QUALITY_HEIGHT)
			?: settings.animePlayerQualityHeight
		controlsLocked = savedInstanceState?.getBoolean(STATE_CONTROLS_LOCKED) ?: false
		audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

		setContentView(ActivityAnimePlayerBinding.inflate(layoutInflater))
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		WindowCompat.setDecorFitsSystemWindows(window, false)
		WindowInsetsControllerCompat(window, window.decorView).systemBarsBehavior =
			WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

		setupModernPlayerUi()
		setupPlayerActions()
		updateTitle()
		updateQuickControls()
		applyControlsLockState(showMessage = false)
		updateSystemUi(resources.configuration.orientation)
		loadEpisode(resolveDetails = true)
	}

	private fun setupPlayerActions() {
		viewBinding.buttonPlayerSettings.setOnClickListener { showPlayerSettings() }
		viewBinding.buttonRetry.setOnClickListener { loadEpisode(resolveDetails = false) }
		viewBinding.buttonControlsLock.setOnClickListener {
			controlsLocked = !controlsLocked
			applyScreenRotationLock(controlsLocked)
			applyControlsLockState(showMessage = controlsLocked)
		}
		viewBinding.buttonQuickPrevious.setOnClickListener { moveEpisode(-1) }
		viewBinding.buttonQuickNext.setOnClickListener { moveEpisode(1) }
		viewBinding.buttonQuickServers.setOnClickListener { showServers() }
		viewBinding.buttonQuickQuality.setOnClickListener { showQualityDialog() }
		viewBinding.buttonQuickSpeed.setOnClickListener { showPlaybackSpeedDialog() }
		viewBinding.textEpisodeBadge.setOnClickListener { showEpisodes() }
		viewBinding.textServerBadge.setOnClickListener { showServers() }
		viewBinding.buttonPlayerSettings.bringToFront()
		viewBinding.buttonControlsLock.bringToFront()
	}

	@SuppressLint("ClickableViewAccessibility")
	private fun setupModernPlayerUi() {
		viewBinding.playerView.apply {
			resizeMode = this@AnimePlayerActivity.resizeMode
			setControllerShowTimeoutMs(CONTROLLER_TIMEOUT_MS)
			setControllerAutoShow(true)
			findViewById<View>(androidx.media3.ui.R.id.exo_settings)?.isVisible = false
			setControllerVisibilityListener(
				androidx.media3.ui.PlayerView.ControllerVisibilityListener { visibility ->
					if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !isInPictureInPictureMode) {
						val controlsVisible = visibility == View.VISIBLE && !controlsLocked
						viewBinding.appbar.isVisible = controlsVisible
						viewBinding.quickControls.isVisible = controlsVisible
						viewBinding.buttonPlayerSettings.isVisible = controlsVisible
						if (!controlsLocked) {
							viewBinding.buttonControlsLock.isVisible = controlsVisible
						}
					}
				},
			)
		}
		val gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
			override fun onDown(e: MotionEvent): Boolean = true

			override fun onDoubleTap(e: MotionEvent): Boolean {
				if (controlsLocked) return true
				val delta = if (e.x < viewBinding.playerView.width / 2f) -SEEK_INCREMENT_MS else SEEK_INCREMENT_MS
				seekBy(delta)
				showSeekFeedback(delta)
				return true
			}

			override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
				if (!controlsLocked) return false
				showLockedControlTemporarily()
				return true
			}
		})
		viewBinding.playerView.setOnTouchListener { _, event ->
			handlePlayerGesture(event, gestures)
		}
	}

	private fun handlePlayerGesture(event: MotionEvent, detector: GestureDetector): Boolean {
		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN -> {
				gestureMode = GestureMode.NONE
				gestureStartX = event.x
				gestureStartY = event.y
				gestureStartVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
				gestureStartBrightness = window.attributes.screenBrightness
					.takeIf { it >= 0f }
					?: DEFAULT_SCREEN_BRIGHTNESS
				detector.onTouchEvent(event)
			}

			MotionEvent.ACTION_MOVE -> {
				if (controlsLocked) return false
				val deltaX = event.x - gestureStartX
				val deltaY = gestureStartY - event.y
				if (
					gestureMode == GestureMode.NONE &&
					abs(deltaY) >= playerGestureThreshold() &&
					abs(deltaY) > abs(deltaX) &&
					gestureStartY < viewBinding.playerView.height * GESTURE_ACTIVE_HEIGHT_RATIO
				) {
					gestureMode = if (gestureStartX < viewBinding.playerView.width / 2f) {
						GestureMode.BRIGHTNESS
					} else {
						GestureMode.VOLUME
					}
				}
				when (gestureMode) {
					GestureMode.VOLUME -> updateGestureVolume(deltaY)
					GestureMode.BRIGHTNESS -> updateGestureBrightness(deltaY)
					GestureMode.NONE -> detector.onTouchEvent(event)
				}
			}

			MotionEvent.ACTION_UP,
			MotionEvent.ACTION_CANCEL -> {
				if (gestureMode == GestureMode.NONE) detector.onTouchEvent(event)
				gestureMode = GestureMode.NONE
			}
		}
		return controlsLocked
	}

	private fun updateGestureVolume(deltaY: Float) {
		val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
		val delta = (deltaY / viewBinding.playerView.height * maxVolume * GESTURE_SENSITIVITY).roundToInt()
		val volume = (gestureStartVolume + delta).coerceIn(0, maxVolume)
		audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
		val percent = (volume * 100f / maxVolume).roundToInt()
		showGestureFeedback(
			icon = R.drawable.ic_volume,
			text = getString(R.string.player_gesture_volume, percent),
			progress = percent,
		)
	}

	private fun updateGestureBrightness(deltaY: Float) {
		val brightness = (gestureStartBrightness +
			deltaY / viewBinding.playerView.height * GESTURE_SENSITIVITY)
			.coerceIn(MIN_SCREEN_BRIGHTNESS, 1f)
		window.attributes = window.attributes.apply { screenBrightness = brightness }
		val percent = (brightness * 100f).roundToInt()
		showGestureFeedback(
			icon = R.drawable.ic_brightness,
			text = getString(R.string.player_gesture_brightness, percent),
			progress = percent,
		)
	}

	private fun showSeekFeedback(deltaMs: Long) {
		showGestureFeedback(
			icon = if (deltaMs < 0L) R.drawable.ic_previous else R.drawable.ic_next,
			text = getString(R.string.player_seek_feedback, if (deltaMs < 0L) "−" else "+"),
			progress = null,
		)
	}

	private fun showGestureFeedback(@DrawableRes icon: Int, text: String, progress: Int?) {
		viewBinding.gestureFeedback.removeCallbacks(hideGestureFeedback)
		viewBinding.imageGestureFeedback.setImageResource(icon)
		viewBinding.textGestureFeedback.text = text
		viewBinding.progressGestureFeedback.isVisible = progress != null
		if (progress != null) {
			viewBinding.progressGestureFeedback.setProgressCompat(progress.coerceIn(0, 100), true)
		}
		viewBinding.gestureFeedback.isVisible = true
		viewBinding.gestureFeedback.postDelayed(hideGestureFeedback, GESTURE_FEEDBACK_TIMEOUT_MS)
	}

	private fun playerGestureThreshold(): Float = 18f * resources.displayMetrics.density

	private fun loadEpisode(resolveDetails: Boolean) {
		loadJob?.cancel()
		releasePlayer(saveProgress = false)
		streams = emptyList()
		selectedStreamIndex = 0
		streamRefreshAttempts = 0
		isRefreshingStreams = false
		showLoading(true)
		loadJob = lifecycleScope.launch {
			runCatching {
				val downloadedFile = localMangaRepository.findDownloadedEpisodeFile(manga, episode.id)
				if (downloadedFile != null) {
					listOf(
						AnimeStream(
							name = getString(R.string.downloaded),
							url = downloadedFile.toUri().toString(),
						),
					)
				} else {
					if (resolveDetails) {
						val resolved = repositoryFactory.create(manga.source).getDetails(manga)
						manga = if (resolved.chapters.isNullOrEmpty()) {
							resolved.copy(chapters = manga.chapters)
						} else {
							resolved
						}
						episode = manga.chapters?.firstOrNull { it.id == episode.id } ?: episode
					}
					checkNotNull(playbackRepository).getAnimeStreams(episode)
				}
			}.onSuccess { result ->
				streams = AnimeStreamSelector.orderForPlayback(result, selectedQualityHeight)
				showLoading(false)
				if (result.isEmpty()) {
					showError()
				} else {
					playStream(0, resumePositionMs)
				}
				updateQuickControls()
				invalidateOptionsMenu()
			}.onFailure {
				showLoading(false)
				showError()
				Snackbar.make(viewBinding.root, R.string.anime_stream_error, Snackbar.LENGTH_LONG).show()
			}
		}
	}

	private fun playStream(index: Int, positionMs: Long) {
		val stream = streams.getOrNull(index) ?: return
		releasePlayer(saveProgress = false)
		selectedStreamIndex = index
		updateQuickControls()
		resumePositionMs = positionMs.coerceAtLeast(0L)
		viewBinding.layoutError.isVisible = false
		viewBinding.playerView.isVisible = true
		viewBinding.playerView.resizeMode = resizeMode

		val httpFactory = DefaultHttpDataSource.Factory()
			.setAllowCrossProtocolRedirects(true)
			.setDefaultRequestProperties(stream.headers)
		val mediaSourceFactory = DefaultMediaSourceFactory(this)
			.setDataSourceFactory(DefaultDataSource.Factory(this, httpFactory))
		val selector = DefaultTrackSelector(this).also {
			applyQualityPreference(it)
			trackSelector = it
		}
		val renderersFactory = DefaultRenderersFactory(this)
			.setEnableDecoderFallback(true)
		player = ExoPlayer.Builder(this, renderersFactory)
			.setMediaSourceFactory(mediaSourceFactory)
			.setTrackSelector(selector)
			.build()
			.also { exoPlayer ->
				viewBinding.playerView.player = exoPlayer
				exoPlayer.addListener(object : Player.Listener {
					override fun onIsPlayingChanged(isPlaying: Boolean) {
						if (isPlaying) hasPlaybackStarted = true
					}

					override fun onPlayerError(error: PlaybackException) {
						val currentPosition = exoPlayer.currentPosition.coerceAtLeast(resumePositionMs)
						handlePlaybackError(error, currentPosition)
					}

						override fun onPlaybackStateChanged(playbackState: Int) {
						if (playbackState == Player.STATE_ENDED && autoPlayNextEpisode) {
							moveEpisode(1, showBoundaryMessage = false)
						}
					}

					override fun onVideoSizeChanged(videoSize: VideoSize) {
						updatePictureInPictureParams(videoSize.width, videoSize.height)
					}

					override fun onTracksChanged(tracks: Tracks) {
						updateQuickControls()
						invalidateOptionsMenu()
					}
				})
				exoPlayer.setHandleAudioBecomingNoisy(true)
				exoPlayer.setMediaItem(buildMediaItem(stream))
				if (resumePositionMs > 0L) exoPlayer.seekTo(resumePositionMs)
				exoPlayer.setPlaybackSpeed(playbackSpeed)
				exoPlayer.prepare()
				exoPlayer.playWhenReady = true
			}
		supportActionBar?.subtitle = listOfNotNull(episode.title, stream.name).joinToString(" · ")
	}

	private fun handlePlaybackError(error: PlaybackException, positionMs: Long) {
		Log.w(
			TAG,
			"Playback failed for ${manga.source.name}/${episode.id} " +
				"server=$selectedStreamIndex code=${error.errorCodeName}",
			error,
		)
		val next = selectedStreamIndex + 1
		if (next in streams.indices) {
			Snackbar.make(
				viewBinding.root,
				R.string.anime_trying_another_server,
				Snackbar.LENGTH_SHORT,
			).show()
			playStream(next, positionMs)
			return
		}
		refreshStreamsAfterFailure(positionMs)
	}

	/**
	 * Video providers use short-lived, IP-bound URLs. If every resolved URL has
	 * expired, ask the parser for a fresh set instead of leaving the user on the
	 * error screen. The bounded retry count prevents an unavailable source from
	 * creating an infinite refresh loop.
	 */
	private fun refreshStreamsAfterFailure(positionMs: Long) {
		val repository = playbackRepository
		if (repository == null || isRefreshingStreams) return
		if (streamRefreshAttempts >= MAX_STREAM_REFRESH_ATTEMPTS) {
			showError()
			return
		}
		isRefreshingStreams = true
		releasePlayer(saveProgress = false)
		showLoading(true)
		Snackbar.make(
			viewBinding.root,
			R.string.anime_refreshing_servers,
			Snackbar.LENGTH_SHORT,
		).show()
		loadJob?.cancel()
		loadJob = lifecycleScope.launch {
			var refreshed = emptyList<AnimeStream>()
			var lastFailure: Throwable? = null
			while (refreshed.isEmpty() && streamRefreshAttempts < MAX_STREAM_REFRESH_ATTEMPTS) {
				if (streamRefreshAttempts > 0) delay(STREAM_REFRESH_RETRY_DELAY_MS)
				streamRefreshAttempts++
				runCatching { repository.getAnimeStreams(episode) }
					.onSuccess { refreshed = it }
					.onFailure { lastFailure = it }
			}
			isRefreshingStreams = false
			showLoading(false)
			if (refreshed.isEmpty()) {
				lastFailure?.let { Log.w(TAG, "Refreshing anime streams failed", it) }
				showError()
				Snackbar.make(viewBinding.root, R.string.anime_stream_error, Snackbar.LENGTH_LONG).show()
			} else {
				streams = AnimeStreamSelector.orderForPlayback(refreshed, selectedQualityHeight)
				selectedStreamIndex = 0
				playStream(0, positionMs)
			}
		}
	}

	private fun buildMediaItem(stream: AnimeStream): MediaItem {
		val builder = MediaItem.Builder().setUri(stream.url)
		when {
			stream.url.substringBefore('?').endsWith(".m3u8", ignoreCase = true) ->
				builder.setMimeType(MimeTypes.APPLICATION_M3U8)
			stream.url.substringBefore('?').endsWith(".mkv", ignoreCase = true) ->
				builder.setMimeType(MimeTypes.VIDEO_MATROSKA)
		}
		return builder.build()
	}

	private fun showServers() {
		if (streams.isEmpty()) return
		MaterialAlertDialogBuilder(this)
			.setTitle(R.string.anime_servers)
			.setSingleChoiceItems(streams.map(AnimeStream::name).toTypedArray(), selectedStreamIndex) { dialog, which ->
				val position = player?.currentPosition ?: resumePositionMs
				playStream(which, position)
				dialog.dismiss()
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	private fun showEpisodes() {
		val episodes = sortedEpisodes()
		if (episodes.isEmpty()) return
		val checked = episodes.indexOfFirst { it.id == episode.id }
		MaterialAlertDialogBuilder(this)
			.setTitle(getString(R.string.anime_episode_list_title, episodes.size))
			.setSingleChoiceItems(
				episodes.map { it.title ?: getString(R.string.episode_number, it.number) }.toTypedArray(),
				checked,
			) { dialog, which ->
				selectEpisode(episodes[which])
				dialog.dismiss()
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	private fun sortedEpisodes(): List<MangaChapter> = manga.chapters.orEmpty().sortedBy(MangaChapter::number)

	private fun selectEpisode(selected: MangaChapter) {
		if (selected.id == episode.id) return
		AdMobManager.offerAnimeInterstitial(this) {
			if (isFinishing || isDestroyed || selected.id == episode.id) return@offerAnimeInterstitial
			saveProgress()
			episode = selected
			resumePositionMs = 0L
			hasPlaybackStarted = false
			updateTitle()
			loadEpisode(resolveDetails = false)
		}
	}

	private fun moveEpisode(delta: Int, showBoundaryMessage: Boolean = true): Boolean {
		val episodes = sortedEpisodes()
		val current = episodes.indexOfFirst { it.id == episode.id }
		val target = episodes.getOrNull(current + delta)
		if (target == null) {
			if (showBoundaryMessage) {
				Toast.makeText(
					this,
					if (delta > 0) R.string.anime_last_episode_reached else R.string.anime_first_episode_reached,
					Toast.LENGTH_SHORT,
				).show()
			}
			return false
		}
		selectEpisode(target)
		return true
	}

	private fun showPlaybackSpeedDialog() {
		val checked = PLAYBACK_SPEEDS.indexOfFirst { it == playbackSpeed }.coerceAtLeast(0)
		MaterialAlertDialogBuilder(this)
			.setTitle(R.string.player_playback_speed)
			.setSingleChoiceItems(PLAYBACK_SPEEDS.map(::formatSpeed).toTypedArray(), checked) { dialog, which ->
				playbackSpeed = PLAYBACK_SPEEDS[which]
				settings.animePlayerSpeed = playbackSpeed
				player?.setPlaybackSpeed(playbackSpeed)
				updateQuickControls()
				invalidateOptionsMenu()
				dialog.dismiss()
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	private fun showResizeModeDialog() {
		val modes = intArrayOf(
			AspectRatioFrameLayout.RESIZE_MODE_FIT,
			AspectRatioFrameLayout.RESIZE_MODE_FILL,
			AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
		)
		val labels = arrayOf(
			getString(R.string.player_display_fit),
			getString(R.string.player_display_fill),
			getString(R.string.player_display_zoom),
		)
		val checked = modes.indexOf(resizeMode).coerceAtLeast(0)
		MaterialAlertDialogBuilder(this)
			.setTitle(R.string.player_display_mode)
			.setSingleChoiceItems(labels, checked) { dialog, which ->
				resizeMode = modes[which]
				settings.animePlayerResizeMode = resizeMode
				viewBinding.playerView.resizeMode = resizeMode
				updateQuickControls()
				invalidateOptionsMenu()
				dialog.dismiss()
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	private fun showQualityDialog() {
		val trackHeights = availableVideoHeights()
		val explicitStreams = streams.mapIndexedNotNull { index, stream ->
			streamQualityHeight(stream)?.let { height -> height to index }
		}
		val heights = (trackHeights + explicitStreams.map { it.first })
			.distinct()
			.sortedDescending()
		if (heights.isEmpty()) {
			Toast.makeText(this, R.string.player_quality_unavailable, Toast.LENGTH_SHORT).show()
			return
		}
		val options = buildList {
			if (explicitStreams.isEmpty()) {
				add(QualityOption(height = -1, streamIndex = null))
			}
			for (height in heights) {
				val streamIndex = explicitStreams.firstOrNull { it.first == height }?.second
					?.takeUnless { height in trackHeights }
				add(QualityOption(height = height, streamIndex = streamIndex))
			}
		}
		val labels = options.map { option ->
			if (option.height < 0) getString(R.string.player_quality_auto) else "${option.height}p"
		}.toTypedArray()
		val currentHeight = streams.getOrNull(selectedStreamIndex)?.let(::streamQualityHeight)
			?: selectedQualityHeight
		val checked = options.indexOfFirst { it.height == currentHeight }.coerceAtLeast(0)
		MaterialAlertDialogBuilder(this)
			.setTitle(R.string.player_quality)
			.setSingleChoiceItems(labels, checked) { dialog, which ->
				val option = options[which]
				val streamIndex = option.streamIndex
				if (streamIndex != null && streamIndex != selectedStreamIndex) {
					selectedQualityHeight = -1
					settings.animePlayerQualityHeight = selectedQualityHeight
					val position = player?.currentPosition ?: resumePositionMs
					playStream(streamIndex, position)
				} else {
					selectedQualityHeight = option.height
					settings.animePlayerQualityHeight = selectedQualityHeight
					trackSelector?.let(::applyQualityPreference)
				}
				updateQuickControls()
				invalidateOptionsMenu()
				dialog.dismiss()
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	private fun availableVideoHeights(): List<Int> {
		return player?.currentTracks?.groups.orEmpty()
			.asSequence()
			.filter { it.type == C.TRACK_TYPE_VIDEO }
			.flatMap { group ->
				(0 until group.length).asSequence()
					.filter(group::isTrackSupported)
					.map { group.getTrackFormat(it).height }
			}
			.filter { it > 0 }
			.distinct()
			.sortedDescending()
			.toList()
	}

	private fun applyQualityPreference(selector: DefaultTrackSelector) {
		val parameters = selector.buildUponParameters().clearVideoSizeConstraints()
		if (selectedQualityHeight > 0) {
			parameters.setMaxVideoSize(Int.MAX_VALUE, selectedQualityHeight)
		}
		selector.setParameters(parameters)
	}

	private fun streamQualityHeight(stream: AnimeStream): Int? = AnimeStreamSelector.qualityHeight(stream)

	private fun qualityTitle(): String {
		val height = streams.getOrNull(selectedStreamIndex)?.let(::streamQualityHeight)
			?: selectedQualityHeight
		return if (height > 0) "${height}p" else getString(R.string.player_quality_auto)
	}

	private fun updateQuickControls() {
		if (!hasViewBinding()) return
		val episodes = sortedEpisodes()
		val episodeIndex = episodes.indexOfFirst { it.id == episode.id }
		val stream = streams.getOrNull(selectedStreamIndex)
		viewBinding.textEpisodeBadge.text = episodeDisplayTitle(episode)
		viewBinding.textServerBadge.text = if (stream == null) {
			getString(R.string.anime_loading_servers)
		} else {
			getString(R.string.player_option_value, stream.name, qualityTitle())
		}
		viewBinding.buttonQuickServers.text = stream?.name ?: getString(R.string.anime_servers)
		viewBinding.buttonQuickQuality.text = qualityTitle()
		viewBinding.buttonQuickSpeed.text = getString(
			R.string.player_speed_short_value,
			formatSpeed(playbackSpeed),
		)
		setQuickEpisodeButtonState(viewBinding.buttonQuickPrevious, episodeIndex > 0)
		setQuickEpisodeButtonState(viewBinding.buttonQuickNext, episodeIndex in 0 until episodes.lastIndex)
	}

	private fun setQuickEpisodeButtonState(button: View, enabled: Boolean) {
		button.isEnabled = enabled
		button.alpha = if (enabled) 1f else 0.38f
	}

	private fun applyControlsLockState(showMessage: Boolean) {
		if (!hasViewBinding()) return
		applyScreenRotationLock(controlsLocked)
		if (controlsLocked) {
			viewBinding.playerView.hideController()
			viewBinding.playerView.setUseController(false)
			viewBinding.appbar.isVisible = false
			viewBinding.quickControls.isVisible = false
			viewBinding.buttonPlayerSettings.isVisible = false
			viewBinding.buttonControlsLock.setIconResource(R.drawable.ic_lock)
			viewBinding.buttonControlsLock.contentDescription = getString(R.string.player_unlock_controls)
			showLockedControlTemporarily()
			if (showMessage) {
				Toast.makeText(this, R.string.player_controls_locked, Toast.LENGTH_SHORT).show()
			}
		} else {
			viewBinding.buttonControlsLock.removeCallbacks(hideLockedControl)
			viewBinding.playerView.setUseController(true)
			viewBinding.buttonPlayerSettings.isVisible = true
			viewBinding.buttonControlsLock.isVisible = true
			viewBinding.buttonControlsLock.setIconResource(R.drawable.ic_lock_open)
			viewBinding.buttonControlsLock.contentDescription = getString(R.string.player_lock_controls)
			viewBinding.playerView.showController()
		}
	}

	private fun showLockedControlTemporarily() {
		if (!hasViewBinding() || !controlsLocked) return
		viewBinding.buttonControlsLock.removeCallbacks(hideLockedControl)
		viewBinding.buttonControlsLock.isVisible = true
		viewBinding.buttonControlsLock.postDelayed(hideLockedControl, CONTROLLER_TIMEOUT_MS.toLong())
	}

	private fun applyScreenRotationLock(locked: Boolean) {
		val orientation = if (locked) {
			ActivityInfo.SCREEN_ORIENTATION_LOCKED
		} else {
			ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
		}
		if (requestedOrientation != orientation) {
			requestedOrientation = orientation
		}
	}

	private fun showPlayerSettings() {
		val episodes = sortedEpisodes()
		val episodeIndex = episodes.indexOfFirst { it.id == episode.id }
		val unavailable = getString(R.string.player_setting_unavailable)
		val serverTitle = streams.getOrNull(selectedStreamIndex)?.name ?: getString(R.string.loading_)
		val enabledTitle = getString(
			if (autoPlayNextEpisode) R.string.player_setting_enabled else R.string.player_setting_disabled,
		)
		val items = listOf(
			PlayerSettingItem(R.drawable.ic_list, getString(R.string.episodes), episodes.size.toString()),
			PlayerSettingItem(
				R.drawable.ic_previous,
				getString(R.string.previous_episode),
				episodes.getOrNull(episodeIndex - 1)?.let(::episodeDisplayTitle) ?: unavailable,
			),
			PlayerSettingItem(
				R.drawable.ic_next,
				getString(R.string.next_episode),
				episodes.getOrNull(episodeIndex + 1)?.let(::episodeDisplayTitle) ?: unavailable,
			),
			PlayerSettingItem(R.drawable.ic_storage, getString(R.string.anime_servers), serverTitle),
			PlayerSettingItem(R.drawable.ic_high_quality, getString(R.string.player_quality), qualityTitle()),
			PlayerSettingItem(
				R.drawable.ic_player_speed,
				getString(R.string.player_playback_speed),
				getString(R.string.player_speed_short_value, formatSpeed(playbackSpeed)),
			),
			PlayerSettingItem(R.drawable.ic_aspect_ratio, getString(R.string.player_display_mode), resizeModeTitle()),
			PlayerSettingItem(R.drawable.ic_sync, getString(R.string.player_auto_next), enabledTitle),
			PlayerSettingItem(
				R.drawable.ic_screen_rotation,
				getString(R.string.player_rotate_screen),
				getString(
					if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
						R.string.player_screen_landscape
					} else {
						R.string.player_screen_portrait
					},
				),
			),
		)
		MaterialAlertDialogBuilder(this)
			.setTitle(R.string.player_settings)
			.setAdapter(PlayerSettingsAdapter(this, items)) { dialog, which ->
				dialog.dismiss()
				when (which) {
					SETTINGS_EPISODES -> showEpisodes()
					SETTINGS_PREVIOUS -> moveEpisode(-1)
					SETTINGS_NEXT -> moveEpisode(1)
					SETTINGS_SERVERS -> if (streams.isEmpty()) {
						Toast.makeText(this, R.string.anime_no_servers, Toast.LENGTH_SHORT).show()
					} else {
						showServers()
					}
					SETTINGS_QUALITY -> showQualityDialog()
					SETTINGS_SPEED -> showPlaybackSpeedDialog()
					SETTINGS_RESIZE -> showResizeModeDialog()
					SETTINGS_AUTO_NEXT -> {
						autoPlayNextEpisode = !autoPlayNextEpisode
						settings.isAnimeAutoNextEnabled = autoPlayNextEpisode
						showPlayerSettings()
					}
					SETTINGS_ROTATE -> toggleOrientation()
				}
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	private fun episodeDisplayTitle(value: MangaChapter): String {
		return value.title ?: getString(R.string.episode_number, formatEpisodeNumber(value.number))
	}

	private fun formatEpisodeNumber(number: Float): String = if (number % 1f == 0f) {
		number.toInt().toString()
	} else {
		number.toString()
	}

	private fun formatSpeed(speed: Float): String = if (speed % 1f == 0f) {
		speed.toInt().toString()
	} else {
		speed.toString()
	}

	private fun resizeModeTitle(): String = getString(
		when (resizeMode) {
			AspectRatioFrameLayout.RESIZE_MODE_FILL -> R.string.player_display_fill
			AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> R.string.player_display_zoom
			else -> R.string.player_display_fit
		},
	)

	private fun seekBy(deltaMs: Long) {
		val currentPlayer = player ?: return
		val duration = currentPlayer.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
		currentPlayer.seekTo((currentPlayer.currentPosition + deltaMs).coerceIn(0L, duration))
		viewBinding.playerView.showController()
	}

	private fun toggleOrientation() {
		requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
			ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
		} else {
			ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
		}
	}

	private fun updateTitle() {
		title = manga.title
		supportActionBar?.subtitle = episode.title
		updateQuickControls()
	}

	private fun showLoading(value: Boolean) {
		viewBinding.loadingGroup.isVisible = value
		if (value) {
			viewBinding.appbar.isVisible = !controlsLocked
			viewBinding.quickControls.isVisible = false
			viewBinding.buttonPlayerSettings.isVisible = !controlsLocked
			viewBinding.layoutError.isVisible = false
			viewBinding.playerView.isVisible = false
		}
	}

	private fun showError() {
		viewBinding.appbar.isVisible = !controlsLocked
		viewBinding.quickControls.isVisible = false
		viewBinding.buttonPlayerSettings.isVisible = !controlsLocked
		viewBinding.loadingGroup.isVisible = false
		viewBinding.layoutError.isVisible = true
		viewBinding.playerView.isVisible = false
	}

	private fun saveProgress() {
		if (!hasPlaybackStarted) return
		val currentPlayer = player ?: return
		val position = currentPlayer.currentPosition.coerceAtLeast(0L)
		val duration = currentPlayer.duration
		if (position <= 0L) return
		resumePositionMs = position
		val percent = if (duration > 0L) {
			(position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
		} else {
			0f
		}
		val historyManga = if (manga.chapters.isNullOrEmpty()) manga.copy(chapters = listOf(episode)) else manga
		historyUpdateUseCase.invokeAsync(
			manga = historyManga,
			readerState = ReaderState(episode.id, 0, position.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()),
			percent = percent,
		)
	}

	private fun releasePlayer(saveProgress: Boolean) {
		if (saveProgress) saveProgress()
		player?.let {
			resumePositionMs = it.currentPosition.coerceAtLeast(0L)
			it.release()
		}
		player = null
		trackSelector = null
		if (hasViewBinding()) viewBinding.playerView.player = null
	}

	override fun onStart() {
		super.onStart()
		if (player == null && streams.isNotEmpty()) {
			playStream(selectedStreamIndex, resumePositionMs)
		}
	}

	override fun onStop() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !isInPictureInPictureMode) {
			releasePlayer(saveProgress = true)
		}
		super.onStop()
	}

	override fun onSaveInstanceState(outState: Bundle) {
		outState.putLong(STATE_POSITION, player?.currentPosition ?: resumePositionMs)
		outState.putFloat(STATE_SPEED, playbackSpeed)
		outState.putInt(STATE_RESIZE_MODE, resizeMode)
		outState.putBoolean(STATE_AUTO_NEXT, autoPlayNextEpisode)
		outState.putInt(STATE_QUALITY_HEIGHT, selectedQualityHeight)
		outState.putBoolean(STATE_CONTROLS_LOCKED, controlsLocked)
		super.onSaveInstanceState(outState)
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
		android.R.id.home -> {
			finishAfterTransition()
			true
		}

		else -> super.onOptionsItemSelected(item)
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val bars = insets.systemBarsInsets
		viewBinding.appbar.setPadding(bars.left, bars.top, bars.right, 0)
		viewBinding.buttonPlayerSettings.updateLayoutParams<ViewGroup.MarginLayoutParams> {
			val endInset = if (viewBinding.buttonPlayerSettings.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
				bars.left
			} else {
				bars.right
			}
			topMargin = bars.top + (12f * resources.displayMetrics.density).toInt()
			marginEnd = endInset + (12f * resources.displayMetrics.density).toInt()
		}
		viewBinding.buttonControlsLock.updateLayoutParams<ViewGroup.MarginLayoutParams> {
			marginStart = bars.left + (12f * resources.displayMetrics.density).toInt()
		}
		viewBinding.quickControls.updateLayoutParams<ViewGroup.MarginLayoutParams> {
			bottomMargin = bars.bottom + (68f * resources.displayMetrics.density).toInt()
		}
		return insets.consumeAllSystemBarsInsets()
	}

	override fun onConfigurationChanged(newConfig: Configuration) {
		super.onConfigurationChanged(newConfig)
		updateSystemUi(newConfig.orientation)
		if (!controlsLocked) viewBinding.playerView.showController()
	}

	private fun updateSystemUi(orientation: Int) {
		val controller = WindowInsetsControllerCompat(window, window.decorView)
		if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
			controller.hide(WindowInsetsCompat.Type.systemBars())
		} else {
			controller.show(WindowInsetsCompat.Type.systemBars())
		}
	}

	override fun onUserLeaveHint() {
		super.onUserLeaveHint()
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && player?.isPlaying == true) {
			runCatching { enterPictureInPictureMode(buildPictureInPictureParams()) }
		}
	}

	override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
		super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
		viewBinding.appbar.isVisible = !isInPictureInPictureMode && !controlsLocked
		viewBinding.quickControls.isVisible = false
		viewBinding.buttonPlayerSettings.isVisible = !isInPictureInPictureMode && !controlsLocked
		viewBinding.buttonControlsLock.isVisible = !isInPictureInPictureMode
		viewBinding.gestureFeedback.isVisible = false
		viewBinding.playerView.setUseController(!isInPictureInPictureMode)
		if (!isInPictureInPictureMode) {
			if (controlsLocked) {
				viewBinding.playerView.setUseController(false)
				showLockedControlTemporarily()
			} else {
				viewBinding.playerView.showController()
			}
		}
	}

	private fun updatePictureInPictureParams(width: Int, height: Int) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || width <= 0 || height <= 0) return
		val ratio = width.toFloat() / height.toFloat()
		if (ratio in MIN_PIP_RATIO..MAX_PIP_RATIO) {
			setPictureInPictureParams(buildPictureInPictureParams(width, height))
		}
	}

	private fun buildPictureInPictureParams(width: Int = 16, height: Int = 9): PictureInPictureParams {
		return PictureInPictureParams.Builder()
			.setAspectRatio(Rational(width, height))
			.build()
	}

	override fun onDestroy() {
		loadJob?.cancel()
		if (hasViewBinding()) viewBinding.gestureFeedback.removeCallbacks(hideGestureFeedback)
		if (hasViewBinding()) viewBinding.buttonControlsLock.removeCallbacks(hideLockedControl)
		releasePlayer(saveProgress = false)
		super.onDestroy()
	}

	private data class PlayerSettingItem(
		@DrawableRes val icon: Int,
		val title: String,
		val value: String,
	)

	private data class QualityOption(
		val height: Int,
		val streamIndex: Int?,
	)

	private enum class GestureMode {
		NONE,
		VOLUME,
		BRIGHTNESS,
	}

	private class PlayerSettingsAdapter(
		context: Context,
		private val items: List<PlayerSettingItem>,
	) : BaseAdapter() {

		private val inflater = LayoutInflater.from(context)

		override fun getCount(): Int = items.size

		override fun getItem(position: Int): PlayerSettingItem = items[position]

		override fun getItemId(position: Int): Long = position.toLong()

		override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
			val view = convertView ?: inflater.inflate(R.layout.item_player_setting, parent, false)
			val item = getItem(position)
			view.findViewById<ImageView>(R.id.player_setting_icon).setImageResource(item.icon)
			view.findViewById<TextView>(R.id.player_setting_title).text = item.title
			view.findViewById<TextView>(R.id.player_setting_value).apply {
				text = item.value
				isVisible = item.value.isNotEmpty()
			}
			return view
		}
	}

	companion object {
		private const val TAG = "AnimePlayer"
		private const val EXTRA_MANGA = "anime_manga"
		private const val EXTRA_EPISODE = "anime_episode"
		private const val EXTRA_EPISODES = "anime_episodes"
		private const val EXTRA_POSITION = "anime_position"
		private const val STATE_POSITION = "player_position"
		private const val STATE_SPEED = "player_speed"
		private const val STATE_RESIZE_MODE = "player_resize_mode"
		private const val STATE_AUTO_NEXT = "player_auto_next"
		private const val STATE_QUALITY_HEIGHT = "player_quality_height"
		private const val STATE_CONTROLS_LOCKED = "player_controls_locked"
		private const val SETTINGS_EPISODES = 0
		private const val SETTINGS_PREVIOUS = 1
		private const val SETTINGS_NEXT = 2
		private const val SETTINGS_SERVERS = 3
		private const val SETTINGS_QUALITY = 4
		private const val SETTINGS_SPEED = 5
		private const val SETTINGS_RESIZE = 6
		private const val SETTINGS_AUTO_NEXT = 7
		private const val SETTINGS_ROTATE = 8
		private const val CONTROLLER_TIMEOUT_MS = 3500
		private const val SEEK_INCREMENT_MS = 10_000L
		private const val GESTURE_FEEDBACK_TIMEOUT_MS = 850L
		private const val MAX_STREAM_REFRESH_ATTEMPTS = 3
		private const val STREAM_REFRESH_RETRY_DELAY_MS = 650L
		private const val GESTURE_SENSITIVITY = 1.35f
		private const val GESTURE_ACTIVE_HEIGHT_RATIO = 0.8f
		private const val DEFAULT_SCREEN_BRIGHTNESS = 0.5f
		private const val MIN_SCREEN_BRIGHTNESS = 0.05f
		private const val MIN_PIP_RATIO = 0.42f
		private const val MAX_PIP_RATIO = 2.39f
		private val PLAYBACK_SPEEDS = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

		fun start(context: Context, manga: Manga, episode: MangaChapter, positionMs: Long = 0L) {
			val episodes = manga.chapters.orEmpty()
				.ifEmpty { listOf(episode) }
				.mapTo(ArrayList()) { ParcelableChapter(it) }
			val playerIntent = Intent(context, AnimePlayerActivity::class.java)
				.putExtra(EXTRA_MANGA, ParcelableManga(manga))
				.putExtra(EXTRA_EPISODE, ParcelableChapter(episode))
				.putParcelableArrayListExtra(EXTRA_EPISODES, episodes)
				.putExtra(EXTRA_POSITION, positionMs)
			val launchPlayer = { context.startActivity(playerIntent) }
			val activity = findActivity(context)
			if (activity == null) {
				launchPlayer()
			} else {
				AdMobManager.offerAnimeInterstitial(activity, launchPlayer)
			}
		}

		private fun findActivity(context: Context): Activity? {
			var current = context
			while (current is ContextWrapper) {
				if (current is Activity) return current
				val base = current.baseContext
				if (base === current) break
				current = base
			}
			return current as? Activity
		}
	}
}
