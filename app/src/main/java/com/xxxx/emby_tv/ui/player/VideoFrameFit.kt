package com.xxxx.emby_tv.ui.player

/**
 * Where a video frame sits inside the screen. Used only to size the direct HDR
 * window. GLES and Vulkan keep a full-screen surface and fit inside it.
 */
object VideoFrameFit {
    const val FIT = 0
    const val FILL = 1
    const val STRETCH = 2

    data class Placed(val x: Int, val y: Int, val width: Int, val height: Int)

    fun place(
        viewW: Int,
        viewH: Int,
        codedW: Int,
        codedH: Int,
        sarNum: Int,
        sarDen: Int,
        rotationDegrees: Int,
        mode: Int,
    ): Placed {
        if (viewW <= 0 || viewH <= 0)
            return Placed(0, 0, 1, 1)
        if (codedW <= 0 || codedH <= 0 || mode == STRETCH)
            return Placed(0, 0, viewW, viewH)
        var frameW = codedW.toDouble()
        var frameH = codedH.toDouble()
        if (sarNum > 0 && sarDen > 0)
            frameW = frameW * sarNum / sarDen
        val quarter = ((rotationDegrees % 360) + 360) % 360
        if (quarter == 90 || quarter == 270) {
            val swap = frameW
            frameW = frameH
            frameH = swap
        }
        if (frameW <= 0.0 || frameH <= 0.0)
            return Placed(0, 0, viewW, viewH)
        val scale = if (mode == FILL) {
            maxOf(viewW / frameW, viewH / frameH)
        } else {
            minOf(viewW / frameW, viewH / frameH)
        }
        val width = (frameW * scale).toInt().coerceIn(1, if (mode == FILL) Int.MAX_VALUE else viewW)
        val height = (frameH * scale).toInt().coerceIn(1, if (mode == FILL) Int.MAX_VALUE else viewH)
        val x = ((viewW - width) / 2).coerceAtLeast(if (mode == FILL) Int.MIN_VALUE else 0)
        val y = ((viewH - height) / 2).coerceAtLeast(if (mode == FILL) Int.MIN_VALUE else 0)
        return Placed(x, y, width, height)
    }
}
