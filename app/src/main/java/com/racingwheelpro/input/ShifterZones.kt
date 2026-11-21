package com.racingwheelpro.input

/**
 * Paddle zones on every wheel template.
 *
 * Each zone is a rectangle in the wheel picture, before steering rotation:
 * [WIDTH_FRACTION] of the wheel width, measured inward from that edge, and
 * [HEIGHT_FRACTION] of the wheel height, centered on [CENTER_Y_FRACTION].
 * The left zone is shift up. The right zone is shift down.
 *
 * Change the three fractions below to resize or slide the paddles.
 */
object ShifterZones {
    /** Inward from the left edge, and inward from the right edge. */
    const val WIDTH_FRACTION = 0.27f

    /** Height of each rectangle, as a fraction of the wheel picture. */
    const val HEIGHT_FRACTION = 0.47f

    /**
     * Vertical center of the paddle band. 0.5 sits on the grip midline,
     * which is where the trigger paddles are drawn on these templates.
     */
    const val CENTER_Y_FRACTION = 0.50f

    enum class Side { UP, DOWN }

    data class Zone(
        val side: Side,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        fun contains(x: Float, y: Float): Boolean =
            x >= left && x <= right && y >= top && y <= bottom
    }

    fun zone(side: Side): Zone {
        val top = (CENTER_Y_FRACTION - HEIGHT_FRACTION / 2f).coerceIn(0f, 1f - HEIGHT_FRACTION)
        val bottom = top + HEIGHT_FRACTION
        return if (side == Side.UP) {
            Zone(side, 0f, top, WIDTH_FRACTION.coerceIn(0f, 1f), bottom)
        } else {
            Zone(side, (1f - WIDTH_FRACTION).coerceIn(0f, 1f), top, 1f, bottom)
        }
    }

    val up: Zone get() = zone(Side.UP)
    val down: Zone get() = zone(Side.DOWN)

    /** Left edge is shift up. Right edge is shift down. Null is outside both. */
    fun hit(nx: Float, ny: Float): Side? {
        if (up.contains(nx, ny)) return Side.UP
        if (down.contains(nx, ny)) return Side.DOWN
        return null
    }

    /** True when a button or knob center sits inside a paddle rectangle. */
    fun claims(cx: Float, cy: Float): Boolean = hit(cx, cy) != null
}

/**
 * How far each pedal sits in from the screen edge, as a fraction of screen width.
 * 0 is flush with the bezel. Each pedal has its own value.
 */
object PedalInsets {
    const val MIN = 0f
    const val MAX = 0.10f
    const val DEFAULT = 0f

    fun clamp(value: Float): Float = value.coerceIn(MIN, MAX)
}
