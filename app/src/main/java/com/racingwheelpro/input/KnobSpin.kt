package com.racingwheelpro.input

/**
 * How far the knob artwork turns so its pointer lands on the selected mark.
 *
 * The numbers printed around a dial stay on the wheel picture. Only the inner
 * cap rotates. Detent 0 is the picture as drawn. The next detent turns one
 * step clockwise when [clockwise] is true, which is the same direction
 * [KnobMath.detentDegrees] walks the overlay pointer.
 *
 * A thumb wheel uses the same step, driven by an up/down drag instead of a
 * circle. Dragging up (toward the top of the wheel picture) is the next
 * position. Dragging down is the previous one.
 */
object KnobSpin {
    fun discDegrees(startAngle: Float, index: Int, positions: Int, clockwise: Boolean): Float {
        val n = positions.coerceAtLeast(1)
        val step = 360f / n
        val signed = if (clockwise) step else -step
        return signed * Math.floorMod(index, n)
    }

    /** Pixels of vertical drag, in the wheel picture, that equal one detent. */
    fun thumbStepPx(radiusPx: Float): Float = (radiusPx * 0.70f).coerceAtLeast(14f)
}
