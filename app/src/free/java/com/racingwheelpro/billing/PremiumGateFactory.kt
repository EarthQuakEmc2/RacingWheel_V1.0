package com.racingwheelpro.billing

import android.app.Activity
import android.content.Context
import com.racingwheelpro.catalog.WheelCatalog

fun createPremiumGate(context: Context): PremiumGate = FreePremiumGate()

/**
 * Free flavor. Four wheels, a knob-pointer demo on one of them, and no
 * dependency on the Play Billing library. Purchase and restore explain that
 * this build cannot take payment. Core driving features stay available.
 */
class FreePremiumGate : PremiumGate() {
    private var message =
        "Every wheel in this build is included. The paid Play build is not in this release."

    override val unlocked = true
    override val priceLabel = WheelCatalog.PRICE_LABEL
    override val detail: String get() = message

    override fun start() = Unit

    override fun purchase(activity: Activity) {
        message = "This build has no Play Billing client. Every wheel is already included."
        notifyListeners()
    }

    override fun restore() {
        message = "There is no purchase to restore in this build. The Play build checks Google Play " +
            "for ${WheelCatalog.PRODUCT_ID}."
        notifyListeners()
    }
}
