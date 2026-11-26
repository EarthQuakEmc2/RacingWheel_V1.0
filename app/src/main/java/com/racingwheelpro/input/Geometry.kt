package com.racingwheelpro.input

import com.racingwheelpro.model.Control
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

data class ImgRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val cx: Float get() = left + width / 2f
    val cy: Float get() = top + height / 2f
    val right: Float get() = left + width
    val bottom: Float get() = top + height

    companion object {
        val EMPTY = ImgRect(0f, 0f, 1f, 1f)
    }
}

object Geometry {
    fun fitCenter(viewW: Float, viewH: Float, imgW: Int, imgH: Int): ImgRect {
        if (viewW <= 0f || viewH <= 0f || imgW <= 0 || imgH <= 0) return ImgRect.EMPTY
        val s = min(viewW / imgW, viewH / imgH)
        val w = imgW * s
        val h = imgH * s
        return ImgRect((viewW - w) / 2f, (viewH - h) / 2f, w, h)
    }

    /**
     * Scale the wheel bitmap into the open rectangle between the pedal columns
     * and between the top of the drive area and the bottom status row.
     * Aspect ratio is kept, with [marginFraction] of that rectangle left empty.
     * The result is centred in the rectangle and does not leave it.
     */
    fun fitWheel(
        freeLeft: Float,
        freeTop: Float,
        freeRight: Float,
        freeBottom: Float,
        imgW: Int,
        imgH: Int,
        marginFraction: Float = 0.02f,
    ): ImgRect {
        val freeW = (freeRight - freeLeft).coerceAtLeast(1f)
        val freeH = (freeBottom - freeTop).coerceAtLeast(1f)
        val marginX = freeW * marginFraction
        val marginY = freeH * marginFraction
        val fitted = fitCenter(freeW - marginX * 2f, freeH - marginY * 2f, imgW, imgH)
        return ImgRect(freeLeft + marginX + fitted.left, freeTop + marginY + fitted.top, fitted.width, fitted.height)
    }

    /**
     * v0.5.0 (tag v0.5.0, commit 45cce61) drew the wheel with [fitCenter] inside the
     * centre column: between the brake and throttle, above the 28dp status row.
     * Pedal columns were `(width * zoneFraction).coerceIn(72dp, 190dp)`.
     * That rectangle is the default size. 1.0 means this size.
     */
    const val LEGACY_ZONE_MIN_DP = 72f
    const val LEGACY_ZONE_MAX_DP = 190f
    const val CURRENT_ZONE_MIN_DP = 56f
    const val CURRENT_ZONE_MAX_DP = 120f
    const val STATUS_ROW_DP = 28f
    const val MIN_WHEEL_SCALE = 0.4f
    const val MAX_WHEEL_SCALE = 3.0f

    fun sizeWheel(
        screenW: Float,
        screenH: Float,
        imgW: Int,
        imgH: Int,
        zoneFraction: Float,
        brakeInset: Float,
        throttleInset: Float,
        density: Float,
        scale: Float,
    ): WheelSizing {
        val legacyZone = (screenW * zoneFraction).coerceIn(LEGACY_ZONE_MIN_DP * density, LEGACY_ZONE_MAX_DP * density)
        val currentZone = (screenW * zoneFraction).coerceIn(CURRENT_ZONE_MIN_DP * density, CURRENT_ZONE_MAX_DP * density)
        val legacy = fitInGap(screenW, screenH, imgW, imgH, legacyZone, brakeInset, throttleInset, density, margin = false)
        val maxFit = fitInGap(screenW, screenH, imgW, imgH, currentZone, brakeInset, throttleInset, density, margin = true)
        val applied = scale.coerceIn(MIN_WHEEL_SCALE, MAX_WHEEL_SCALE)
        val w = legacy.width * applied
        val h = legacy.height * applied
        val placed = ImgRect(maxFit.cx - w / 2f, maxFit.cy - h / 2f, w, h)
        return WheelSizing(legacy, maxFit, placed, MAX_WHEEL_SCALE, MIN_WHEEL_SCALE)
    }

    private fun fitInGap(
        screenW: Float,
        screenH: Float,
        imgW: Int,
        imgH: Int,
        zonePx: Float,
        brakeInset: Float,
        throttleInset: Float,
        density: Float,
        margin: Boolean,
    ): ImgRect {
        val left = screenW * PedalInsets.clamp(brakeInset) + zonePx
        val right = screenW - screenW * PedalInsets.clamp(throttleInset) - zonePx
        val bottom = screenH - STATUS_ROW_DP * density
        if (!margin) {
            val fitted = fitCenter((right - left).coerceAtLeast(1f), (bottom).coerceAtLeast(1f), imgW, imgH)
            return ImgRect(left + fitted.left, fitted.top, fitted.width, fitted.height)
        }
        return fitWheel(left, 0f, right, bottom, imgW, imgH)
    }

    /**
     * Forward of Android's clockwise canvas rotation (Y grows downward):
     * x' = cx + dx cos θ − dy sin θ
     * y' = cy + dx sin θ + dy cos θ
     */
    fun localToScreen(lx: Float, ly: Float, cx: Float, cy: Float, clockwiseDeg: Float): Pair<Float, Float> {
        val t = Math.toRadians(clockwiseDeg.toDouble())
        val c = cos(t)
        val s = sin(t)
        val dx = lx - cx
        val dy = ly - cy
        return (cx + (dx * c - dy * s)).toFloat() to (cy + (dx * s + dy * c)).toFloat()
    }

    fun screenToLocal(tx: Float, ty: Float, cx: Float, cy: Float, clockwiseDeg: Float): Pair<Float, Float> {
        val t = Math.toRadians(clockwiseDeg.toDouble())
        val c = cos(t)
        val s = sin(t)
        val dx = tx - cx
        val dy = ty - cy
        return (cx + (dx * c + dy * s)).toFloat() to (cy + (-dx * s + dy * c)).toFloat()
    }

    /**
     * Hit-test in image space. [slopScreenPx] grows every target so a fingertip
     * lands on the drawn button; the closest center wins when targets overlap.
     */
    fun hit(
        controls: List<Control>,
        touchX: Float,
        touchY: Float,
        rect: ImgRect,
        imageW: Int,
        imageH: Int,
        clockwiseDeg: Float,
        hitSlopFrac: Float,
        slopScreenPx: Float = 22f,
    ): Control? {
        if (rect.width < 2f || imageW < 2) return null
        val (lx, ly) = screenToLocal(touchX, touchY, rect.cx, rect.cy, clockwiseDeg)
        val nx = (lx - rect.left) / rect.width
        val ny = (ly - rect.top) / rect.height
        val px = nx * imageW
        val py = ny * imageH
        val scale = rect.width / imageW
        val extra = slopScreenPx / scale + hitSlopFrac * imageW
        var best: Control? = null
        var bestD = Float.MAX_VALUE
        for (c in controls) {
            val ccx = c.cx * imageW
            val ccy = c.cy * imageH
            val limit = c.r * imageW + extra
            val d = hypot(px - ccx, py - ccy)
            if (d <= limit && d < bestD) {
                best = c
                bestD = d
            }
        }
        return best
    }
}

data class WheelSizing(
    val legacy: ImgRect,
    val maxFit: ImgRect,
    val placed: ImgRect,
    val scaleCap: Float,
    val scaleFloor: Float,
)

object WheelPinch {
    fun allows(editor: Boolean, pointers: Int): Boolean = editor && pointers >= 2

    fun nextScale(current: Float, factor: Float, cap: Float, floor: Float): Float {
        val limit = cap.coerceAtLeast(0.01f)
        val low = min(floor, limit)
        val base = if (current.isFinite()) current.coerceIn(low, limit) else 1f
        val step = if (factor.isFinite() && factor > 0f) factor else 1f
        return (base * step).coerceIn(low, limit)
    }
}
