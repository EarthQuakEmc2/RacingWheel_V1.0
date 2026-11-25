package com.racingwheelpro.input

import com.racingwheelpro.model.Control
import com.racingwheelpro.model.ShifterSpec
import com.racingwheelpro.model.WheelBounds
import kotlin.math.hypot

/**
 * Paddle zones on every wheel template.
 *
 * Defaults are 20% of the wheel's width, inward from that side of the wheel plate,
 * and a shorter band ([HEIGHT_FRACTION] of the wheel's height) centered on the grip.
 * A template can move that band when its art sits higher or lower.
 * The left zone is shift up. The right zone is shift down.
 *
 * A programmable button is never covered. Its circle, plus [CUTOUT_MARGIN] and the
 * same fingertip slop the button hit test uses, is cut out of the rectangle.
 * A button on the edge leaves a notch. Touches in the notch belong to the button.
 */
object ShifterZones {
    /** Inward from the wheel's own left edge, and inward from its right edge. */
    const val WIDTH_FRACTION = 0.20f

    /** Height of the default band, as a fraction of the wheel plate. Shorter than the old 47% box. */
    const val HEIGHT_FRACTION = 0.32f

    /** Vertical center inside the wheel plate. 0 is the top of the plate, 1 is the bottom. */
    const val CENTER_Y_FRACTION = 0.50f

    const val MIN_WIDTH = 0.08f
    const val MAX_WIDTH = 0.45f
    const val MIN_HEIGHT = 0.16f
    const val MAX_HEIGHT = 0.65f

    /** Extra image-width fraction added around each button circle. */
    const val CUTOUT_MARGIN = 0.012f

    enum class Side { UP, DOWN }

    enum class Handle { BODY, INNER, TOP, BOTTOM }

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

    fun clamp(spec: ShifterSpec): ShifterSpec {
        val width = spec.width.coerceIn(MIN_WIDTH, MAX_WIDTH)
        val height = spec.height.coerceIn(MIN_HEIGHT, MAX_HEIGHT)
        val half = height / 2f
        val centerY = spec.centerY.coerceIn(half, 1f - half)
        return ShifterSpec(width, height, centerY)
    }

    /** Places [spec] on [bounds]. [spec] is relative to the wheel plate, not the whole picture. */
    fun place(side: Side, spec: ShifterSpec, bounds: WheelBounds = WheelBounds.FULL): Zone {
        val fitted = clamp(spec)
        val zoneW = fitted.width * bounds.width
        val zoneH = fitted.height * bounds.height
        val cy = bounds.top + fitted.centerY * bounds.height
        val top = cy - zoneH / 2f
        val bottom = cy + zoneH / 2f
        // The outer edge runs to the picture edge. Flippers often sit outside the
        // opaque plate, and the right one was missing the zone so it never lit.
        return if (side == Side.UP) {
            Zone(side, 0f, top, bounds.left + zoneW, bottom)
        } else {
            Zone(side, bounds.right - zoneW, top, 1f, bottom)
        }
    }

    fun zone(side: Side): Zone = place(side, ShifterSpec.DEFAULT, WheelBounds.FULL)

    val up: Zone get() = zone(Side.UP)
    val down: Zone get() = zone(Side.DOWN)

    /** Rectangle test only. Cut-outs are applied by [hit]. */
    fun contains(nx: Float, ny: Float): Side? {
        if (up.contains(nx, ny)) return Side.UP
        if (down.contains(nx, ny)) return Side.DOWN
        return null
    }

    fun contains(nx: Float, ny: Float, upZone: Zone, downZone: Zone): Side? {
        if (upZone.contains(nx, ny)) return Side.UP
        if (downZone.contains(nx, ny)) return Side.DOWN
        return null
    }

    /**
     * Shift side under a point in the wheel picture, or null when the point is outside
     * both rectangles or inside a button cut-out.
     */
    fun hit(
        nx: Float,
        ny: Float,
        upZone: Zone = up,
        downZone: Zone = down,
        controls: List<Control> = emptyList(),
        imageW: Int = 1,
        imageH: Int = 1,
        hitSlop: Float = 0f,
        extraPx: Float = 0f,
    ): Side? {
        val side = contains(nx, ny, upZone, downZone) ?: return null
        if (inCutout(nx, ny, controls, imageW, imageH, hitSlop, extraPx)) return null
        return side
    }

    fun inCutout(
        nx: Float,
        ny: Float,
        controls: List<Control>,
        imageW: Int,
        imageH: Int,
        hitSlop: Float,
        extraPx: Float,
    ): Boolean {
        if (imageW < 1 || imageH < 1) return false
        for (control in controls) {
            val limit = cutoutRadiusPx(control, imageW, hitSlop, extraPx)
            val dx = (nx - control.cx) * imageW
            val dy = (ny - control.cy) * imageH
            if (hypot(dx, dy) <= limit) return true
        }
        return false
    }

    /** Pixel radius of the circle removed from the paddle rectangle. Round in image pixels. */
    fun cutoutRadiusPx(control: Control, imageW: Int, hitSlop: Float, extraPx: Float): Float =
        (control.r + hitSlop + CUTOUT_MARGIN) * imageW + extraPx

    /** Kept so a point inside the default rectangle can be asserted without a button list. */
    fun claims(cx: Float, cy: Float): Boolean = contains(cx, cy) != null

    /**
     * Button circles that cross [zone]. The overlay subtracts these and nothing else,
     * so a paddle press cannot paint a disc on every other button.
     */
    fun cutoutsOn(
        zone: Zone,
        controls: List<Control>,
        imageW: Int,
        imageH: Int,
        hitSlop: Float,
        extraPx: Float,
    ): List<Control> = controls.filter { circleMeetsZone(zone, it, imageW, imageH, hitSlop, extraPx) }

    fun circleMeetsZone(
        zone: Zone,
        control: Control,
        imageW: Int,
        imageH: Int,
        hitSlop: Float,
        extraPx: Float,
    ): Boolean {
        if (imageW < 1 || imageH < 1) return false
        val cx = control.cx * imageW
        val cy = control.cy * imageH
        val radius = cutoutRadiusPx(control, imageW, hitSlop, extraPx)
        val nearestX = cx.coerceIn(zone.left * imageW, zone.right * imageW)
        val nearestY = cy.coerceIn(zone.top * imageH, zone.bottom * imageH)
        return hypot(cx - nearestX, cy - nearestY) <= radius
    }

    /**
     * The button whose cut-out contains [nx], [ny], when that point is also inside a
     * paddle rectangle. The margin ring is part of the notch, so this is wider than
     * the drawn hotspot. Null when the point belongs to the paddle itself.
     */
    fun notchControl(
        nx: Float,
        ny: Float,
        upZone: Zone,
        downZone: Zone,
        controls: List<Control>,
        imageW: Int,
        imageH: Int,
        hitSlop: Float,
        extraPx: Float,
    ): Control? {
        if (imageW < 1 || imageH < 1) return null
        if (!upZone.contains(nx, ny) && !downZone.contains(nx, ny)) return null
        var best: Control? = null
        var bestD = Float.MAX_VALUE
        for (control in controls) {
            val limit = cutoutRadiusPx(control, imageW, hitSlop, extraPx)
            val dx = (nx - control.cx) * imageW
            val dy = (ny - control.cy) * imageH
            val d = hypot(dx, dy)
            if (d <= limit && d < bestD) {
                best = control
                bestD = d
            }
        }
        return best
    }

    fun handleAt(
        nx: Float,
        ny: Float,
        zone: Zone,
        thicknessX: Float,
        thicknessY: Float,
    ): Handle? {
        if (nx < zone.left - thicknessX || nx > zone.right + thicknessX) return null
        if (ny < zone.top - thicknessY || ny > zone.bottom + thicknessY) return null
        val onTop = kotlin.math.abs(ny - zone.top) <= thicknessY && nx >= zone.left && nx <= zone.right
        val onBottom = kotlin.math.abs(ny - zone.bottom) <= thicknessY && nx >= zone.left && nx <= zone.right
        val innerX = if (zone.side == Side.UP) zone.right else zone.left
        val onInner = kotlin.math.abs(nx - innerX) <= thicknessX && ny >= zone.top && ny <= zone.bottom
        return when {
            onInner -> Handle.INNER
            onTop -> Handle.TOP
            onBottom -> Handle.BOTTOM
            zone.contains(nx, ny) -> Handle.BODY
            else -> null
        }
    }
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
