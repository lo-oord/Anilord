package org.manga.peak.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.annotation.MainThread
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import org.manga.peak.R

/**
 * Owns consent, SDK initialization, and the single preloaded anime interstitial.
 *
 * All public methods are called on the main thread. Ads are never requested until
 * UMP reports that the app is allowed to request them.
 */
object AdMobManager {

	private const val TAG = "AdMobManager"
	private const val AD_PREFERENCES = "admob_preferences"
	private const val KEY_ANIME_OFFER_WAS_SHOWN = "anime_offer_was_shown"
	private const val KEY_EPISODES_SINCE_ANIME_OFFER = "episodes_since_anime_offer"
	private const val EPISODES_BETWEEN_ANIME_OFFERS = 2

	private var consentRequestStarted = false
	private var sdkInitializationStarted = false
	private var sdkInitialized = false
	private var consentResolutionFinished = false
	private val sdkReadyCallbacks = mutableListOf<(Boolean) -> Unit>()

	private var animeInterstitial: InterstitialAd? = null
	private var isAnimeInterstitialLoading = false

	@MainThread
	fun initialize(activity: Activity) {
		if (consentRequestStarted) return
		consentRequestStarted = true

		val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
		val requestParameters = ConsentRequestParameters.Builder()
			.setTagForUnderAgeOfConsent(
				activity.resources.getBoolean(R.bool.admob_tag_for_under_age_of_consent),
			)
			.build()

		consentInformation.requestConsentInfoUpdate(
			activity,
			requestParameters,
			{
				UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
					if (formError != null) {
						Log.w(TAG, "Consent form was not shown: ${formError.message}")
					}
					consentResolutionFinished = true
					if (consentInformation.canRequestAds()) {
						initializeMobileAds(activity.applicationContext)
					} else {
						resolveSdkReadyCallbacks(isReady = false)
					}
				}
			},
			{ requestError ->
				Log.w(TAG, "Consent information update failed: ${requestError.message}")
				consentResolutionFinished = true
				if (consentInformation.canRequestAds()) {
					initializeMobileAds(activity.applicationContext)
				} else {
					resolveSdkReadyCallbacks(isReady = false)
				}
			},
		)

		// Returning users may already have a valid consent state. This avoids delaying
		// the first ad request while the latest consent information is refreshed.
		if (consentInformation.canRequestAds()) {
			initializeMobileAds(activity.applicationContext)
		}
	}

	@MainThread
	fun isPrivacyOptionsRequired(context: Context): Boolean {
		return UserMessagingPlatform.getConsentInformation(context)
			.privacyOptionsRequirementStatus ==
			ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
	}

	@MainThread
	fun showPrivacyOptions(activity: Activity, onFinished: (String?) -> Unit) {
		UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
			if (UserMessagingPlatform.getConsentInformation(activity).canRequestAds()) {
				initializeMobileAds(activity.applicationContext)
			}
			onFinished(formError?.message)
		}
	}

	@MainThread
	fun loadNativeAd(
		context: Context,
		onLoaded: (NativeAd) -> Unit,
		onFailed: () -> Unit,
	) {
		runWhenSdkReady { isReady ->
			if (!isReady) {
				onFailed()
				return@runWhenSdkReady
			}
			AdLoader.Builder(context, context.getString(R.string.admob_native_ad_unit_id))
				.forNativeAd { nativeAd -> onLoaded(nativeAd) }
				.withNativeAdOptions(
					NativeAdOptions.Builder()
						.setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
						.setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_LANDSCAPE)
						.build(),
				)
				.withAdListener(
					object : AdListener() {
						override fun onAdFailedToLoad(error: LoadAdError) {
							Log.w(TAG, "Native ad failed to load: ${error.message}")
							onFailed()
						}
					},
				)
				.build()
				.loadAd(AdRequest.Builder().build())
		}
	}

	/**
	 * Offers the already-preloaded ad on the first eligible episode, then once for
	 * every two episodes started after the previous offer. Declining, cancellation,
	 * a failed display, or an unavailable ad all continue playback without blocking
	 * the user.
	 */
	@MainThread
	fun offerAnimeInterstitial(activity: Activity, onContinue: () -> Unit) {
		if (activity.isFinishing || activity.isDestroyed) {
			onContinue()
			return
		}
		val preferences = activity.getSharedPreferences(AD_PREFERENCES, Context.MODE_PRIVATE)
		val wasShownBefore = preferences.getBoolean(KEY_ANIME_OFFER_WAS_SHOWN, false)
		if (wasShownBefore) {
			val episodeCount = (
				preferences.getInt(KEY_EPISODES_SINCE_ANIME_OFFER, 0) + 1
				).coerceAtMost(EPISODES_BETWEEN_ANIME_OFFERS)
			preferences.edit()
				.putInt(KEY_EPISODES_SINCE_ANIME_OFFER, episodeCount)
				.apply()
			if (episodeCount < EPISODES_BETWEEN_ANIME_OFFERS) {
				onContinue()
				return
			}
		}
		if (animeInterstitial == null) {
			preloadAnimeInterstitial(activity.applicationContext)
			onContinue()
			return
		}

		val dialog = MaterialAlertDialogBuilder(activity)
			.setTitle(R.string.optional_anime_ad_title)
			.setMessage(R.string.optional_anime_ad_message)
			.setPositiveButton(R.string.watch_ad) { _, _ ->
				showAnimeInterstitial(activity, onContinue)
			}
			.setNegativeButton(R.string.play_now) { _, _ ->
				onContinue()
			}
			.setOnCancelListener {
				onContinue()
			}
			.create()
		dialog.setOnShowListener {
			preferences.edit()
				.putBoolean(KEY_ANIME_OFFER_WAS_SHOWN, true)
				.putInt(KEY_EPISODES_SINCE_ANIME_OFFER, 0)
				.apply()
		}
		runCatching(dialog::show).onFailure {
			Log.w(TAG, "Anime interstitial offer could not be shown", it)
			onContinue()
		}
	}

	@MainThread
	private fun initializeMobileAds(context: Context) {
		if (sdkInitialized || sdkInitializationStarted) return
		sdkInitializationStarted = true
		MobileAds.initialize(context) {
			sdkInitialized = true
			resolveSdkReadyCallbacks(isReady = true)
			preloadAnimeInterstitial(context)
		}
	}

	@MainThread
	private fun runWhenSdkReady(callback: (Boolean) -> Unit) {
		when {
			sdkInitialized -> callback(true)
			consentResolutionFinished && !sdkInitializationStarted -> callback(false)
			else -> sdkReadyCallbacks += callback
		}
	}

	@MainThread
	private fun resolveSdkReadyCallbacks(isReady: Boolean) {
		val callbacks = sdkReadyCallbacks.toList()
		sdkReadyCallbacks.clear()
		callbacks.forEach { it(isReady) }
	}

	@MainThread
	private fun preloadAnimeInterstitial(context: Context) {
		if (!sdkInitialized || animeInterstitial != null || isAnimeInterstitialLoading) return
		isAnimeInterstitialLoading = true
		InterstitialAd.load(
			context,
			context.getString(R.string.admob_anime_interstitial_ad_unit_id),
			AdRequest.Builder().build(),
			object : InterstitialAdLoadCallback() {
				override fun onAdLoaded(ad: InterstitialAd) {
					isAnimeInterstitialLoading = false
					animeInterstitial = ad
				}

				override fun onAdFailedToLoad(error: LoadAdError) {
					isAnimeInterstitialLoading = false
					animeInterstitial = null
					Log.w(TAG, "Anime interstitial failed to load: ${error.message}")
				}
			},
		)
	}

	@MainThread
	private fun showAnimeInterstitial(activity: Activity, onContinue: () -> Unit) {
		val ad = animeInterstitial
		if (ad == null) {
			preloadAnimeInterstitial(activity.applicationContext)
			onContinue()
			return
		}
		animeInterstitial = null
		var hasContinued = false
		fun continueOnce() {
			if (hasContinued) return
			hasContinued = true
			onContinue()
		}
		ad.fullScreenContentCallback = object : FullScreenContentCallback() {
			override fun onAdDismissedFullScreenContent() {
				continueOnce()
				preloadAnimeInterstitial(activity.applicationContext)
			}

			override fun onAdFailedToShowFullScreenContent(error: AdError) {
				Log.w(TAG, "Anime interstitial failed to show: ${error.message}")
				continueOnce()
				preloadAnimeInterstitial(activity.applicationContext)
			}
		}
		runCatching {
			ad.show(activity)
		}.onFailure {
			Log.w(TAG, "Anime interstitial could not be shown", it)
			continueOnce()
			preloadAnimeInterstitial(activity.applicationContext)
		}
	}
}
