package org.manga.peak.ads

import androidx.core.view.isVisible
import com.google.android.gms.ads.nativead.NativeAd
import org.manga.peak.databinding.ItemNativeAdBinding

fun ItemNativeAdBinding.bindNativeAd(ad: NativeAd) {
	adHeadline.text = ad.headline
	adBody.text = ad.body
	adBody.isVisible = !ad.body.isNullOrBlank()
	adAdvertiser.text = ad.advertiser ?: ad.store
	adAdvertiser.isVisible = !adAdvertiser.text.isNullOrBlank()
	adIcon.setImageDrawable(ad.icon?.drawable)
	adIcon.isVisible = ad.icon?.drawable != null
	adCallToAction.text = ad.callToAction
	adCallToAction.isVisible = !ad.callToAction.isNullOrBlank()
	adMedia.mediaContent = ad.mediaContent
	adMedia.isVisible = ad.mediaContent != null

	root.headlineView = adHeadline
	root.bodyView = adBody
	root.advertiserView = adAdvertiser
	root.iconView = adIcon
	root.callToActionView = adCallToAction
	root.mediaView = adMedia
	root.setNativeAd(ad)
}
