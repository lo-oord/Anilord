package org.manga.peak.settings.about

import android.app.Activity
import android.content.Context
import androidx.annotation.StringRes
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import org.manga.peak.R

/**
 * Handles a repeatable, consumable one-time support purchase.
 *
 * The Play Console product id must match [PRODUCT_ID]. The purchase does not
 * unlock content; it is consumed after Play confirms payment so it can be
 * purchased again later.
 */
internal class SupportBillingManager(
	context: Context,
	private val listener: Listener,
) : PurchasesUpdatedListener, BillingClientStateListener {

	private val consumingTokens = HashSet<String>()
	private var productDetails: ProductDetails? = null
	private var isConnecting = false
	private var isClosed = false

	private val billingClient = BillingClient.newBuilder(context.applicationContext)
		.setListener(this)
		.enablePendingPurchases(
			PendingPurchasesParams.newBuilder()
				.enableOneTimeProducts()
				.build(),
		)
		.enableAutoServiceReconnection()
		.build()

	fun start() {
		if (isClosed || isConnecting || billingClient.isReady) {
			if (billingClient.isReady) {
				queryProduct()
				queryExistingPurchases()
			}
			return
		}
		isConnecting = true
		billingClient.startConnection(this)
	}

	fun launch(activity: Activity) {
		val details = productDetails
		val offer = details?.oneTimePurchaseOfferDetailsList?.firstOrNull()
		if (!billingClient.isReady || details == null || offer == null) {
			start()
			listener.onMessage(R.string.support_developer_unavailable)
			return
		}
		val productParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
			.setProductDetails(details)
		offer.offerToken?.let(productParamsBuilder::setOfferToken)
		val productParams = productParamsBuilder.build()
		val result = billingClient.launchBillingFlow(
			activity,
			BillingFlowParams.newBuilder()
				.setProductDetailsParamsList(listOf(productParams))
				.build(),
		)
		if (result.responseCode != BillingClient.BillingResponseCode.OK) {
			listener.onMessage(R.string.support_developer_error)
		}
	}

	fun close() {
		isClosed = true
		productDetails = null
		consumingTokens.clear()
		billingClient.endConnection()
	}

	override fun onBillingSetupFinished(billingResult: BillingResult) {
		isConnecting = false
		if (isClosed) return
		if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
			queryProduct()
			queryExistingPurchases()
		} else {
			productDetails = null
			listener.onProductChanged(null)
		}
	}

	override fun onBillingServiceDisconnected() {
		isConnecting = false
	}

	override fun onPurchasesUpdated(
		billingResult: BillingResult,
		purchases: List<Purchase>?,
	) {
		when (billingResult.responseCode) {
			BillingClient.BillingResponseCode.OK -> purchases.orEmpty().forEach(::processPurchase)
			BillingClient.BillingResponseCode.USER_CANCELED -> Unit
			BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> queryExistingPurchases()
			else -> listener.onMessage(R.string.support_developer_error)
		}
	}

	private fun queryProduct() {
		val product = QueryProductDetailsParams.Product.newBuilder()
			.setProductId(PRODUCT_ID)
			.setProductType(BillingClient.ProductType.INAPP)
			.build()
		val params = QueryProductDetailsParams.newBuilder()
			.setProductList(listOf(product))
			.build()
		billingClient.queryProductDetailsAsync(params) { billingResult, result ->
			if (isClosed) return@queryProductDetailsAsync
			productDetails = if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
				result.productDetailsList.firstOrNull()
			} else {
				null
			}
			val price = productDetails
				?.oneTimePurchaseOfferDetailsList
				?.firstOrNull()
				?.formattedPrice
			listener.onProductChanged(price)
		}
	}

	private fun queryExistingPurchases() {
		val params = QueryPurchasesParams.newBuilder()
			.setProductType(BillingClient.ProductType.INAPP)
			.build()
		billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
			if (isClosed || billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
				return@queryPurchasesAsync
			}
			purchases.forEach(::processPurchase)
		}
	}

	private fun processPurchase(purchase: Purchase) {
		if (PRODUCT_ID !in purchase.products) return
		when (purchase.purchaseState) {
			Purchase.PurchaseState.PURCHASED -> consume(purchase)
			Purchase.PurchaseState.PENDING -> listener.onMessage(R.string.support_developer_pending)
		}
	}

	private fun consume(purchase: Purchase) {
		val token = purchase.purchaseToken
		if (!consumingTokens.add(token)) return
		val params = ConsumeParams.newBuilder()
			.setPurchaseToken(token)
			.build()
		billingClient.consumeAsync(params) { billingResult, purchaseToken ->
			consumingTokens.remove(purchaseToken)
			if (isClosed) return@consumeAsync
			if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
				listener.onMessage(R.string.support_developer_thanks)
				queryProduct()
			} else {
				listener.onMessage(R.string.support_developer_error)
			}
		}
	}

	internal interface Listener {
		fun onProductChanged(formattedPrice: String?)

		fun onMessage(@StringRes message: Int)
	}

	internal companion object {
		/**
		 * Create and activate this exact one-time product id in Play Console.
		 */
		const val PRODUCT_ID = "support1"
	}
}
