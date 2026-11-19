package com.xxxx.emby_tv.ui.home

/**
 * Android TV layouts in this app are designed at 960dp. A 4K panel that
 * reports a much wider dp width makes fixed-dp cards look small. When that
 * happens, Compose density is raised so the layout width is 960dp again.
 * Devices already near 960dp are left on the system density.
 */
object TvDensity {
    const val BASELINE_DP = 960f
    const val TRIGGER_DP = 1040f

    fun overrideDensity(widthPx: Int, density: Float): Float? {
        if (widthPx <= 0 || density <= 0f) return null
        val widthDp = widthPx / density
        if (widthDp <= TRIGGER_DP) return null
        return widthPx / BASELINE_DP
    }

    /** Continue-watching tiles across a row, using the fixed 186dp card and 20dp gap. */
    fun continueTiles(widthDp: Float, gutterDp: Float = 32f, cardDp: Float = 186f, gapDp: Float = 20f): Float {
        val stride = cardDp + gapDp
        if (stride <= 0f) return 0f
        return (widthDp - gutterDp) / stride
    }
}
