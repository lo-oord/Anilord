package org.manga.peak.ads

import android.content.Context
import androidx.annotation.MainThread
import com.google.android.gms.ads.nativead.NativeAd

/**
 * Keeps one native ad per visible list slot and owns their lifecycle.
 */
class NativeAdController {

	private val ads = mutableMapOf<Int, NativeAd>()
	private val callbacks = mutableMapOf<Int, MutableList<(NativeAd?) -> Unit>>()
	private val failedSlots = mutableSetOf<Int>()
	private var isDestroyed = false

	@MainThread
	fun getOrLoad(context: Context, slot: Int, callback: (NativeAd?) -> Unit) {
		if (isDestroyed || slot in failedSlots) {
			callback(null)
			return
		}
		ads[slot]?.let {
			callback(it)
			return
		}
		val slotCallbacks = callbacks.getOrPut(slot) { mutableListOf() }
		slotCallbacks += callback
		if (slotCallbacks.size > 1) return

		AdMobManager.loadNativeAd(
			context = context.applicationContext,
			onLoaded = { ad ->
				if (isDestroyed) {
					ad.destroy()
					return@loadNativeAd
				}
				ads[slot] = ad
				resolve(slot, ad)
			},
			onFailed = {
				failedSlots += slot
				resolve(slot, null)
			},
		)
	}

	@MainThread
	fun destroy() {
		if (isDestroyed) return
		isDestroyed = true
		ads.values.forEach(NativeAd::destroy)
		ads.clear()
		callbacks.values.flatten().forEach { it(null) }
		callbacks.clear()
		failedSlots.clear()
	}

	@MainThread
	private fun resolve(slot: Int, ad: NativeAd?) {
		callbacks.remove(slot).orEmpty().forEach { it(ad) }
	}
}
