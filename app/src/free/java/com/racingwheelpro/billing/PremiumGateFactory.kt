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
        "Four wheels are included. The others are Racing Wheel Premium, a one-time unlock."

    override val unlocked = false
    override val priceLabel = WheelCatalog.PRICE_LABEL
    override val detail: String get() = message

    override fun start() = Unit

    override fun purchase(activity: Activity) {
        message = "This build has no Play Billing client. Steering, throttle, brake, buttons, " +
            "the layout editor, and the PC receiver stay fully usable. " +
            "Racing Wheel Premium (${WheelCatalog.PRICE_LABEL}) unlocks the other wheels in the Play build."
        notifyListeners()
    }

    override fun restore() {
        message = "There is no purchase to restore in this build. The Play build checks Google Play " +
            "for ${WheelCatalog.PRODUCT_ID}."
        notifyListeners()
    }
}
