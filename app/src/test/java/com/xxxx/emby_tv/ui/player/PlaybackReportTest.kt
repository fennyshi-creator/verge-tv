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
    fun aPositionPastTheFileKeepsTheResume() {
        val ms = PlaybackReport.safePositionMs(
            playerPositionMs = 140_000L,
            resumeMs = 60_000L,
            runtimeMs = 149_500L,
            playerDurationMs = 115_000L,
            naturalEnd = false,
        )
        assertEquals(60_000L, ms)
    }

    @Test
    fun aPlayheadAheadOfTheSavedResumeIsKeptWhenFileLengthIsUnknown() {
        // The library runtime is not a file length and is not written back.
        // A real playhead is kept even when it is well ahead of the resume
        // the user came in with.
        val ms = PlaybackReport.safePositionMs(
            playerPositionMs = 120_000L,
            resumeMs = 60_000L,
            runtimeMs = 149_500L,
            playerDurationMs = 0L,
            naturalEnd = false,
        )
        assertEquals(120_000L, ms)
    }

    @Test
    fun playbackInsideAnUnknownDurationStillReports() {
        val ms = PlaybackReport.safePositionMs(
            playerPositionMs = 69_000L,
            resumeMs = 60_000L,
            runtimeMs = 149_500L,
            playerDurationMs = 0L,
            naturalEnd = false,
        )
        assertEquals(69_000L, ms)
    }

    @Test
    fun aResumePastTheMeasuredFileStartsAtTheBeginning() {
        val fromZero = PlaybackReport.safePositionMs(
            playerPositionMs = 0L,
            resumeMs = 120_000L,
            runtimeMs = 149_500L,
            playerDurationMs = 115_000L,
            naturalEnd = false,
        )
        val fromPast = PlaybackReport.safePositionMs(
            playerPositionMs = 120_000L,
            resumeMs = 120_000L,
            runtimeMs = 149_500L,
            playerDurationMs = 115_000L,
            naturalEnd = false,
        )
        assertEquals(0L, fromZero)
        assertEquals(0L, fromPast)
    }

    @Test
    fun aRealPositionInsideTheFileIsKept() {
        val ms = PlaybackReport.safePositionMs(
            playerPositionMs = 70_000L,
            resumeMs = 60_000L,
            runtimeMs = 149_500L,
            playerDurationMs = 115_000L,
            naturalEnd = false,
        )
        assertEquals(70_000L, ms)
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

    @Test
    fun aFinishedPlayerDoesNotRestoreTheOldResume() {
        val ms = PlaybackReport.safePositionMs(
            playerPositionMs = 0L,
            resumeMs = 60_000L,
            runtimeMs = 149_500L,
            playerDurationMs = 115_000L,
            naturalEnd = true,
        )
        assertEquals(115_000L, ms)
    }
}
