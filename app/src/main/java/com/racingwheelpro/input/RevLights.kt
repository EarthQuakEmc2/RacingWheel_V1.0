package com.racingwheelpro.input

import kotlin.math.roundToInt

/**
 * Progressive shift lights. The left of the strip is green, then yellow, then
 * red, then blue and purple at the right. Each lamp is either on or dim.
 * Throttle fills the strip from 0 to full pedal. RPM stays dark until 75% of
 * the engine's maximum and is full at the redline, which is how a wheel's
 * shift lights behave in the car.
 */
object RevLights {
    const val RPM_START = 0.75f

    data class Rgb(val r: Int, val g: Int, val b: Int)

    fun litCount(level: Float, count: Int): Int {
        if (count <= 0) return 0
        return (level.coerceIn(0f, 1f) * count).roundToInt().coerceIn(0, count)
    }

    /** 0 below [RPM_START] of max, 1 at max. */
    fun rpmLevel(rpm: Int, maxRpm: Int): Float {
        if (maxRpm <= 0 || rpm <= 0) return 0f
        val frac = rpm.toFloat() / maxRpm.toFloat()
        return ((frac - RPM_START) / (1f - RPM_START)).coerceIn(0f, 1f)
    }

    /**
     * Which signal drives the strip. RPM is used only while Assetto Corsa
     * telemetry is arriving. Otherwise the throttle pedal fills the lights.
     */
    fun sourceLevel(
        mode: String,
        throttle: Float,
        rpm: Int,
        maxRpm: Int,
        telemetryLive: Boolean,
    ): Float {
        if (mode == "rpm" && telemetryLive) return rpmLevel(rpm, maxRpm)
        return throttle.coerceIn(0f, 1f)
    }

    /** Color of lamp [index] in a strip of [count], left to right. */
    fun colorAt(index: Int, count: Int): Rgb {
        val n = count.coerceAtLeast(1)
        val t = if (n == 1) 0f else index.coerceIn(0, n - 1).toFloat() / (n - 1).toFloat()
        return when {
            t < 0.40f -> mix(0x3DDC57, 0xC6F000, t / 0.40f)
            t < 0.62f -> mix(0xF5C518, 0xFF8A00, (t - 0.40f) / 0.22f)
            t < 0.82f -> mix(0xFF3B30, 0xE10600, (t - 0.62f) / 0.20f)
            else -> mix(0x7A4DFF, 0x3D6BFF, (t - 0.82f) / 0.18f)
        }
    }

    private fun mix(from: Int, to: Int, t: Float): Rgb {
        val u = t.coerceIn(0f, 1f)
        fun ch(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * u).roundToInt().coerceIn(0, 255)
        }
        return Rgb(ch(16), ch(8), ch(0))
    }
}
