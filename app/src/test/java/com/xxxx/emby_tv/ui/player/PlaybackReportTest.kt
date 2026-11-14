package com.xxxx.emby_tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackReportTest {
    @Test
    fun aZeroPlayheadKeepsTheSavedResumePoint() {
        val ms = PlaybackReport.safePositionMs(
            playerPositionMs = 0L,
            resumeMs = 60_000L,
            runtimeMs = 149_500L,
            playerDurationMs = 0L,
            naturalEnd = false,
        )
        assertEquals(60_000L, ms)
    }

    @Test
    fun aShortSessionCannotBeReportedAsFinished() {
        val ms = PlaybackReport.safePositionMs(
            playerPositionMs = 140_000L,
            resumeMs = 60_000L,
            runtimeMs = 149_500L,
            playerDurationMs = 115_000L,
            naturalEnd = false,
        )
        assertEquals(92_000L, ms)
    }

    @Test
    fun aRealEndingMayReachThePlayerDuration() {
        val ms = PlaybackReport.safePositionMs(
            playerPositionMs = 114_000L,
            resumeMs = 60_000L,
            runtimeMs = 149_500L,
            playerDurationMs = 115_000L,
            naturalEnd = true,
        )
        assertEquals(114_000L, ms)
    }
}
