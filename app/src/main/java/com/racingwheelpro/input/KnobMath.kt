package com.racingwheelpro.input

import kotlin.math.roundToInt

/**
 * Detents for a rotary drawn on a wheel image.
 *
 * Angles are degrees clockwise from 3 o'clock in screen space, where y grows
 * downward. 0 is 3 o'clock, 90 is 6 o'clock, and −90 (or 270) is 12 o'clock.
 * Detent 0 sits at [startAngle]. The next detent is one step clockwise when
 * [clockwise] is true.
 */
object KnobMath {
    fun normalizeDeg(deg: Float): Float {
        var d = deg % 360f
        if (d < 0f) d += 360f
        return d
    }

    fun detentDegrees(startAngle: Float, index: Int, positions: Int, clockwise: Boolean): Float {
        val n = positions.coerceAtLeast(1)
        val step = 360f / n
        val signed = if (clockwise) step else -step
        return normalizeDeg(startAngle + signed * Math.floorMod(index, n))
    }

    /** Index of the detent closest to an absolute screen angle in radians. */
    fun nearestIndex(angleRad: Float, startAngle: Float, positions: Int, clockwise: Boolean): Int {
        val n = positions.coerceAtLeast(1)
        val deg = normalizeDeg(Math.toDegrees(angleRad.toDouble()).toFloat())
        var rel = deg - normalizeDeg(startAngle)
        if (!clockwise) rel = -rel
        rel = normalizeDeg(rel)
        val step = 360f / n
        return Math.floorMod((rel / step).roundToInt(), n)
    }

    /**
     * Shortest signed step count from [from] to [to]. Positive moves toward
     * higher indexes (clockwise when the control is clockwise).
     */
    fun shortestDelta(from: Int, to: Int, positions: Int): Int {
        val n = positions.coerceAtLeast(1)
        var delta = Math.floorMod(to - from, n)
        if (delta > n / 2) delta -= n
        return delta
    }
}
