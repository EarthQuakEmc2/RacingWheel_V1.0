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

    /** How much larger the wheel is drawn than the 0.6.2 center column, before the screen cap. */
    const val WHEEL_BOOST = 1.85f

    /**
     * One wheel size for Drive and Edit. Starts from the old center-column fit,
     * grows by [WHEEL_BOOST], then stops so the wheel plate still sits inside the
     * screen with a small margin. [contentW] and [contentH] are the plate's
     * fraction of the bitmap, so a short formula rim can grow without the
     * transparent padding deciding the size.
     */
    fun presentWheel(
        screenW: Float,
        screenH: Float,
        imgW: Int,
        imgH: Int,
        contentW: Float = 1f,
        contentH: Float = 1f,
        reserveLeft: Float = 0f,
        reserveRight: Float = 0f,
    ): ImgRect {
        if (screenW <= 1f || screenH <= 1f || imgW <= 0 || imgH <= 0) return ImgRect.EMPTY
        val margin = min(screenW, screenH) * 0.035f
        val legacyW = (screenW - reserveLeft - reserveRight).coerceAtLeast(1f) * 0.72f
        val legacyH = (screenH * 0.86f).coerceAtLeast(1f)
        val base = fitCenter(legacyW, legacyH, imgW, imgH)
        var w = base.width * WHEEL_BOOST
        var h = base.height * WHEEL_BOOST
        val cw = contentW.coerceIn(0.45f, 1f)
        val ch = contentH.coerceIn(0.45f, 1f)
        val boxLeft = reserveLeft + margin
        val boxTop = margin
        val maxW = (screenW - reserveLeft - reserveRight - margin * 2f).coerceAtLeast(1f)
        val maxH = (screenH - margin * 2f).coerceAtLeast(1f)
        if (w * cw > maxW) {
            val s = maxW / (w * cw)
            w *= s
            h *= s
        }
        if (h * ch > maxH) {
            val s = maxH / (h * ch)
            w *= s
            h *= s
        }
        if (w > maxW) {
            val s = maxW / w
            w *= s
            h *= s
        }
        if (h > maxH) {
            val s = maxH / h
            w *= s
            h *= s
        }
        return ImgRect(boxLeft + (maxW - w) / 2f, boxTop + (maxH - h) / 2f, w, h)
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
