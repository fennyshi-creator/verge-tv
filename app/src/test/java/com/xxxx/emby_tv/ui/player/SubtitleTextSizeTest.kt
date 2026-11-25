package com.xxxx.emby_tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleTextSizeTest {
    @Test
    fun fourKPanelIsSlightlyLargerAnd1080IsSlightlySmaller() {
        val scale = 1f
        val fourK = SubtitleTextSize.fraction(scale, currentHeightPx = 2160, supportedHeightsPx = intArrayOf(2160))
        val hd = SubtitleTextSize.fraction(scale, currentHeightPx = 1080, supportedHeightsPx = intArrayOf(1080))
        assertEquals(SubtitleTextSize.BASE_FRACTION * SubtitleTextSize.PANEL_4K_BIAS, fourK, 0.0001f)
        assertEquals(SubtitleTextSize.BASE_FRACTION * SubtitleTextSize.PANEL_1080_BIAS, hd, 0.0001f)
        assertEquals(fourK > SubtitleTextSize.BASE_FRACTION, true)
        assertEquals(hd < SubtitleTextSize.BASE_FRACTION, true)
    }

    @Test
    fun a1080ModeOnA4kPanelUsesThe4kBias() {
        val bias = SubtitleTextSize.panelBias(
            currentHeightPx = 1080,
            supportedHeightsPx = intArrayOf(1080, 2160),
        )
        assertEquals(SubtitleTextSize.PANEL_4K_BIAS, bias, 0.0001f)
    }

    @Test
    fun savedScaleStillMultipliesTheFraction() {
        val standard = SubtitleTextSize.fraction(1f, 1080, intArrayOf(1080))
        val large = SubtitleTextSize.fraction(1.25f, 1080, intArrayOf(1080))
        assertEquals(standard * 1.25f, large, 0.0001f)
    }
}
