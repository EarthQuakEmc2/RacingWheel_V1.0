package com.racingwheelpro.input

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

object Curves {
    /** Map a 0..1 pedal reading through dead zone, outer clamp and gamma. */
    fun shape01(raw: Float, dead: Float, outer: Float, gamma: Float, invert: Boolean): Float {
        var x = raw.coerceIn(0f, 1f)
        if (invert) x = 1f - x
        val d = dead.coerceIn(0f, 0.9f)
        val end = outer.coerceIn(d + 0.02f, 1f)
        if (x <= d) return 0f
        if (x >= end) return 1f
        val n = ((x - d) / (end - d)).coerceIn(0f, 1f)
        val g = gamma.toDouble().coerceIn(0.2, 4.0)
        return n.toDouble().pow(g).toFloat()
    }

    /**
     * Signed steering. [degrees] is centered. [rangeDeg] is the full lock-to-lock angle.
     * Gamma below 1 makes the center more sensitive; above 1 softens it.
     */
    fun shapeSteer(
        degrees: Float,
        rangeDeg: Float,
        deadDeg: Float,
        gamma: Float,
        sensitivity: Float,
        invert: Boolean,
    ): Float {
        val half = (rangeDeg / 2f).coerceAtLeast(10f)
        val dead = deadDeg.coerceIn(0f, half - 1f)
        val ad = abs(degrees)
        val shaped = if (ad <= dead) {
            0f
        } else {
            sign(degrees) * (ad - dead) * (half / (half - dead))
        }
        var n = (shaped / half).coerceIn(-1f, 1f)
        val mag = abs(n).toDouble().pow(gamma.toDouble().coerceIn(0.2, 4.0)).toFloat()
        n = sign(n) * mag * sensitivity
        n = n.coerceIn(-1f, 1f)
        return if (invert) -n else n
    }
}

class Smoother(var amount: Float = 0.35f) {
    var value: Float = 0f

    fun reset(v: Float = 0f) {
        value = v
    }

    fun step(target: Float): Float {
        val keep = amount.coerceIn(0f, 0.95f)
        value += (1f - keep) * (target - value)
        return value
    }
}

/**
 * Unwraps a ±pi sensor angle so ranges past 180° keep turning.
 * The jump threshold matches the approach used by Touch Racer's wheel code.
 */
class AngleUnwrapper {
    private var prev = 0f
    private var offset = 0f
    private var ready = false

    fun reset() {
        ready = false
        offset = 0f
    }

    fun feed(angleRad: Float, unwrap: Boolean): Float {
        if (!ready) {
            prev = angleRad
            ready = true
            return angleRad
        }
        val d = angleRad - prev
        if (d > 4f) offset -= (Math.PI * 2).toFloat()
        else if (d < -4f) offset += (Math.PI * 2).toFloat()
        prev = angleRad
        return if (unwrap) angleRad + offset else angleRad
    }
}
