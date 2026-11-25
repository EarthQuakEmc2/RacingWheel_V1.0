package com.racingwheelpro.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.racingwheelpro.BuildConfig
import com.racingwheelpro.catalog.EntitlementPolicy
import com.racingwheelpro.catalog.WheelCatalog

fun createPremiumGate(context: Context): PremiumGate = PlayPremiumGate(context.applicationContext)

/**
 * Google Play Billing Library 7 client for the non-consumable product
 * [WheelCatalog.PRODUCT_ID]. The price is configured in Play Console ($3.99).
 * This file is compiled only into the premium flavor.
 *
 * Debug builds grant the entitlement so a test APK can drive every wheel
 * without a Play Console product. Release builds grant it only after
 * queryPurchasesAsync reports an acknowledged purchase. A failed query keeps
 * the local cache.
 */
class PlayPremiumGate(private val context: Context) : PremiumGate() {
    private val prefs = context.getSharedPreferences("rwp", Context.MODE_PRIVATE)
    private val debugGrant = BuildConfig.DEBUG
    private var query = EntitlementPolicy.Query.PENDING
    private var ownsProduct = false
    private var message = "Checking Google Play for Racing Wheel Premium."
    private var client: BillingClient? = null

    override val unlocked: Boolean
        get() = EntitlementPolicy.resolve(
            query,
            ownsProduct,
            prefs.getBoolean(EntitlementPolicy.CACHE_KEY, false),
            debugGrant,
        )

    override val priceLabel: String
        get() = livePrice.ifBlank { WheelCatalog.PRICE_LABEL }

    override val detail: String
        get() = message

    private var livePrice: String = ""

    private val purchasesUpdated = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            message = "Purchase canceled."
            notifyListeners()
            return@PurchasesUpdatedListener
        }
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            message = "Purchase failed (${result.debugMessage})."
            notifyListeners()
            return@PurchasesUpdatedListener
        }
        applyPurchases(result, purchases)
    }

    override fun start() {
        if (client != null) return
        val billing = BillingClient.newBuilder(context)
            .setListener(purchasesUpdated)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
            )
            .build()
        client = billing
        billing.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    refreshPurchases()
                } else {
                    query = EntitlementPolicy.Query.FAILED
                    message = "Google Play is unavailable (${result.debugMessage})."
                    notifyListeners()
                }
            }

            override fun onBillingServiceDisconnected() {
                query = EntitlementPolicy.Query.FAILED
                message = "Google Play disconnected. The last unlock is kept until the next check."
                notifyListeners()
            }
        })
    }

    override fun purchase(activity: Activity) {
        val billing = client
        if (billing == null || !billing.isReady) {
            message = "Google Play is not ready yet."
            notifyListeners()
            start()
            return
        }
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(WheelCatalog.PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()
        billing.queryProductDetailsAsync(params) { result, details ->
            val found = details.firstOrNull()
            if (result.responseCode != BillingClient.BillingResponseCode.OK || found == null) {
                message = "racing_wheel_premium is not available in Play Console (${result.debugMessage})."
                notifyListeners()
                return@queryProductDetailsAsync
            }
            rememberPrice(found)
            val flow = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(found)
                            .build(),
                    ),
                )
                .build()
            billing.launchBillingFlow(activity, flow)
        }
    }

    override fun restore() {
        message = "Checking Google Play for an existing purchase."
        notifyListeners()
        val billing = client
        if (billing != null && billing.isReady) refreshPurchases() else start()
    }

    override fun close() {
        client?.endConnection()
        client = null
    }

    private fun refreshPurchases() {
        val billing = client ?: return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billing.queryPurchasesAsync(params) { result, purchases ->
            applyPurchases(result, purchases)
        }
    }

    private fun applyPurchases(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            query = EntitlementPolicy.Query.FAILED
            message = "Could not read purchases (${result.debugMessage})."
            notifyListeners()
            return
        }
        val mine = purchases.orEmpty().filter { WheelCatalog.PRODUCT_ID in it.products }
        val bought = mine.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        val pending = mine.any { it.purchaseState == Purchase.PurchaseState.PENDING }
        val waiting = bought.filter { !it.isAcknowledged }
        if (waiting.isNotEmpty()) {
            waiting.forEach { acknowledge(it) }
            return
        }
        ownsProduct = bought.isNotEmpty()
        query = EntitlementPolicy.Query.SUCCEEDED
        prefs.edit().putBoolean(EntitlementPolicy.CACHE_KEY, ownsProduct).apply()
        message = when {
            ownsProduct -> "Racing Wheel Premium is active on this Google account."
            pending -> "The purchase is pending. The extra wheels stay locked until Play completes it."
            else -> "No purchase of ${WheelCatalog.PRODUCT_ID} on this account."
        }
        notifyListeners()
    }

    private fun acknowledge(purchase: Purchase) {
        val billing = client ?: return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billing.acknowledgePurchase(params) { result ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                ownsProduct = true
                query = EntitlementPolicy.Query.SUCCEEDED
                prefs.edit().putBoolean(EntitlementPolicy.CACHE_KEY, true).apply()
                message = "Racing Wheel Premium is active on this Google account."
            } else {
                query = EntitlementPolicy.Query.FAILED
                message = "Purchase received but not acknowledged (${result.debugMessage})."
            }
            notifyListeners()
        }
    }

    private fun rememberPrice(details: ProductDetails) {
        val formatted = details.oneTimePurchaseOfferDetails?.formattedPrice
        if (!formatted.isNullOrBlank()) livePrice = formatted
    }
}
