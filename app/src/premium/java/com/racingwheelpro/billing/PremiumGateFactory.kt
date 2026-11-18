package com.racingwheelpro.billing

import android.app.Activity
import android.content.Context
import com.racingwheelpro.catalog.WheelCatalog

fun createPremiumGate(context: Context): PremiumGate = OpenPremiumGate()

/**
 * Premium flavor on the public branch. Every wheel and every knob pointer is
 * available, and this source set does not import the Play Billing library.
 *
 * The paid-version branch replaces this file with a BillingClient implementation
 * for the non-consumable product [WheelCatalog.PRODUCT_ID]. Because that code
 * lives only in the premium source set, the free flavor still compiles with
 * an empty billing classpath. See docs/BILLING.md.
 */
class OpenPremiumGate : PremiumGate() {
    override val unlocked = true
    override val priceLabel = WheelCatalog.PRICE_LABEL
    override val detail = "Every wheel is unlocked in this build. It does not contact Google Play."

    override fun start() = Unit

    override fun purchase(activity: Activity) {
        // The open premium build has nothing to sell. The Play client, on the
        // unmerged paid-version branch, launches the billing flow from here.
    }

    override fun restore() = Unit
}
