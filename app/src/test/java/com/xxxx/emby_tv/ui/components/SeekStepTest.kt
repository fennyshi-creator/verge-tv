package com.xxxx.emby_tv.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SeekStepTest {
    private val feature = 100 * 60 * 1000L

    @Test
    fun tapAndHoldRamp() {
        assertEquals(10_000L, seekStepMs(0L, feature))
        assertEquals(10_000L, seekStepMs(499L, feature))
        assertEquals(30_000L, seekStepMs(500L, feature))
        assertEquals(60_000L, seekStepMs(1_500L, feature))
    }

    @Test
    fun longHoldUsesAboutOnePercentClampedToTwoThroughFiveMinutes() {
        val ninetyMinutes = 90 * 60 * 1000L
        assertEquals(120_000L, seekStepMs(3_000L, ninetyMinutes))
        val sixHours = 6 * 60 * 60 * 1000L
        assertEquals(216_000L, seekStepMs(3_000L, sixHours))
        val tenHours = 10 * 60 * 60 * 1000L
        assertEquals(300_000L, seekStepMs(3_000L, tenHours))
    }

    @Test
    fun offsetLabelShowsSignAndClock() {
        assertEquals("+0:00:10", formatSeekOffset(10_000L))
        assertEquals("+0:01:10", formatSeekOffset(70_000L))
        assertEquals("-0:00:30", formatSeekOffset(-30_000L))
    }

    @Test
    fun episodeLabelAndCreditsPrompt() {
        assertEquals("S2:E7 · 生存支援", episodeBarLabel(2, 7, "生存支援"))
        assertEquals(90_000L, nextEpisodePromptMs(120_000L, null))
    }
}
