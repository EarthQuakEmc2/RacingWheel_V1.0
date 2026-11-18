package com.racingwheelpro.billing

import android.app.Activity
import com.racingwheelpro.catalog.WheelCatalog

/**
 * The only door from the UI to a purchase. The free flavor ships a gate with
 * no billing types on its classpath. The premium flavor's source set provides
 * [createPremiumGate]. On this branch that implementation unlocks every wheel
 * without Play Billing, so the published source stays fully usable. The
 * paid-version branch replaces the premium source set with the Play Billing
 * client and does not merge that client here.
 */
abstract class PremiumGate {
    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    protected fun notifyListeners() {
        listeners.toList().forEach { it() }
    }

    abstract val unlocked: Boolean
    abstract val priceLabel: String
    abstract val detail: String

    abstract fun start()
    abstract fun purchase(activity: Activity)
    abstract fun restore()

    fun wheelUnlocked(id: String): Boolean = unlocked || WheelCatalog.isFree(id)

    fun knobOverlay(wheelId: String): Boolean = unlocked || wheelId == WheelCatalog.DEMO_KNOB_WHEEL
}
