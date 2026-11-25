package com.racingwheelpro.input

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Plain readouts for the wheel's built-in screen.
 * Gear uses the same numbers a driver reads: R, N, then 1 and up.
 * Assetto Corsa's shared-memory gear is one higher (0 is reverse, 1 is neutral).
 * The receiver subtracts one before the packet is sent.
 */
object DashFormat {
    fun gearLabel(gear: Int): String = when {
        gear < 0 -> "R"
        gear == 0 -> "N"
        else -> gear.toString()
    }

    fun acGearToDisplay(acGear: Int): Int = (acGear - 1).coerceIn(-1, 12)

    fun lapTime(ms: Int): String {
        if (ms <= 0) return "--:--.---"
        val clamped = ms.coerceAtMost(99 * 60000 + 59999)
        val minutes = clamped / 60000
        val seconds = (clamped % 60000) / 1000
        val millis = clamped % 1000
        return "%d:%02d.%03d".format(minutes, seconds, millis)
    }

    /** Signed gap to the best lap, in seconds with two decimals. Negative is faster. */
    fun delta(ms: Int): String {
        val sign = when {
            ms > 0 -> "+"
            ms < 0 -> "-"
            else -> ""
        }
        val hundredths = (abs(ms) / 10).coerceAtMost(99999)
        val seconds = hundredths / 100
        val frac = hundredths % 100
        return "%s%d.%02d".format(sign, seconds, frac)
    }

    fun speed(kmh: Float): String = if (kmh.isFinite()) kmh.roundToInt().coerceIn(0, 999).toString() else "0"

    fun rpm(rpm: Int): String = rpm.coerceAtLeast(0).toString()
}
