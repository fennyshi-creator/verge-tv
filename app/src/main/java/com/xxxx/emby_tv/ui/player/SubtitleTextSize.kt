package com.xxxx.emby_tv.ui.player

/**
 * External text-subtitle size. The base fraction is of the subtitle view.
 * Many 4K panels still composite the app at 1080p, so the same fraction looks
 * small on a 4K panel and large on a 1080p panel. The bias uses the tallest
 * [android.view.Display.Mode], not display density.
 */
object SubtitleTextSize {
    const val BASE_FRACTION = 0.0533f
    const val PANEL_4K_BIAS = 1.10f
    const val PANEL_1080_BIAS = 0.90f

    fun fraction(scale: Float, currentHeightPx: Int, supportedHeightsPx: IntArray): Float {
        return BASE_FRACTION * scale * panelBias(currentHeightPx, supportedHeightsPx)
    }

    fun panelBias(currentHeightPx: Int, supportedHeightsPx: IntArray): Float {
        var height = currentHeightPx
        for (candidate in supportedHeightsPx) {
            if (candidate > height) height = candidate
        }
        return when {
            height >= 2000 -> PANEL_4K_BIAS
            height in 900..1400 -> PANEL_1080_BIAS
            else -> 1f
        }
    }
}
