package com.xxxx.emby_tv.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerRemoteTest {
    private fun hidden(playing: Boolean, seeking: Boolean = false) = RemoteContext(
        controlsVisible = false,
        playing = playing,
        sheetOpen = false,
        episodeStrip = false,
        resumePrompt = false,
        seeking = seeking,
    )

    @Test
    fun hiddenOkArmsOnDownAndPausesOnlyOnTheMatchingUp() {
        val down = decideRemote(hidden(playing = true), PlayerKey.Ok, KeyPhase.Down)
        val repeat = decideRemote(hidden(playing = true), PlayerKey.Ok, KeyPhase.Repeat)
        val up = decideRemote(hidden(playing = true), PlayerKey.Ok, KeyPhase.Up)
        assertEquals(RemoteCommand.Ignore, down.command)
        assertTrue(down.consume)
        assertEquals(RemoteCommand.Ignore, repeat.command)
        assertTrue(repeat.consume)
        assertEquals(RemoteCommand.PauseAndFocusPlay, up.command)
        assertTrue(up.consume)
    }

    @Test
    fun onePressFiresOnceEvenWithRepeats() {
        val latch = OkLatch()
        assertFalse(latch.consume(KeyPhase.Down))
        assertFalse(latch.consume(KeyPhase.Repeat))
        assertFalse(latch.consume(KeyPhase.Repeat))
        assertTrue(latch.consume(KeyPhase.Up))
        assertFalse(latch.consume(KeyPhase.Up))
        assertFalse(latch.consume(KeyPhase.Down))
        latch.cancel()
        assertFalse(latch.consume(KeyPhase.Up))
        assertFalse(latch.consume(KeyPhase.Down))
        assertTrue(latch.consume(KeyPhase.Up))
    }

    @Test
    fun hiddenOkWhilePausedRevealsThePlayButtonWithoutResuming() {
        val decision = decideRemote(hidden(playing = false), PlayerKey.Ok, KeyPhase.Up)
        assertEquals(RemoteCommand.RevealPausedPlay, decision.command)
    }

    @Test
    fun visibleOkIsLeftToTheFocusedControl() {
        val visible = hidden(playing = false).copy(controlsVisible = true)
        val decision = decideRemote(visible, PlayerKey.Ok, KeyPhase.Down)
        assertEquals(RemoteCommand.Ignore, decision.command)
        assertFalse(decision.consume)
    }

    @Test
    fun hiddenLeftAndRightSeekOnDownAndRepeatButNotOnUp() {
        assertEquals(
            RemoteCommand.SeekBackward,
            decideRemote(hidden(playing = true), PlayerKey.Left, KeyPhase.Down).command,
        )
        assertEquals(
            RemoteCommand.SeekForward,
            decideRemote(hidden(playing = true), PlayerKey.Right, KeyPhase.Repeat).command,
        )
        val up = decideRemote(hidden(playing = true), PlayerKey.Left, KeyPhase.Up)
        assertEquals(RemoteCommand.Ignore, up.command)
        assertTrue(up.consume)
    }

    @Test
    fun visibleArrowsAreNotIntercepted() {
        val visible = hidden(playing = true).copy(controlsVisible = true)
        val decision = decideRemote(visible, PlayerKey.Right, KeyPhase.Down)
        assertFalse(decision.consume)
        assertEquals(RemoteCommand.Ignore, decision.command)
    }

    @Test
    fun dialogsEpisodeStripAndResumePromptPassArrowsThrough() {
        val sheet = hidden(playing = true).copy(sheetOpen = true)
        val strip = hidden(playing = true).copy(episodeStrip = true, controlsVisible = true)
        val resume = hidden(playing = false).copy(resumePrompt = true)
        assertFalse(decideRemote(sheet, PlayerKey.Left, KeyPhase.Down).consume)
        assertFalse(decideRemote(strip, PlayerKey.Down, KeyPhase.Down).consume)
        assertFalse(decideRemote(resume, PlayerKey.Ok, KeyPhase.Up).consume)
    }

    @Test
    fun hiddenDownRevealsTheTimelineAndDoesNotPause() {
        val decision = decideRemote(hidden(playing = true), PlayerKey.Down, KeyPhase.Down)
        assertEquals(RemoteCommand.RevealTimeline, decision.command)
        val repeat = decideRemote(hidden(playing = true), PlayerKey.Down, KeyPhase.Repeat)
        assertEquals(RemoteCommand.Ignore, repeat.command)
        assertTrue(repeat.consume)
    }

    @Test
    fun backHidesAVisibleBarOrSeekReadoutThenLetsTheNextBackThrough() {
        val bar = hidden(playing = true).copy(controlsVisible = true)
        assertEquals(RemoteCommand.HideOverlay, decideRemote(bar, PlayerKey.Back, KeyPhase.Down).command)
        val readout = hidden(playing = true, seeking = true)
        assertEquals(RemoteCommand.HideOverlay, decideRemote(readout, PlayerKey.Back, KeyPhase.Down).command)
        val clear = hidden(playing = true)
        assertFalse(decideRemote(clear, PlayerKey.Back, KeyPhase.Down).consume)
    }

    @Test
    fun mediaKeysAreNeverConsumed() {
        val hiddenPlaying = hidden(playing = true)
        val visible = hiddenPlaying.copy(controlsVisible = true)
        listOf(PlayerKey.MediaPlay, PlayerKey.MediaPause, PlayerKey.MediaPlayPause).forEach { key ->
            assertFalse(decideRemote(hiddenPlaying, key, KeyPhase.Down).consume)
            assertFalse(decideRemote(visible, key, KeyPhase.Up).consume)
        }
    }

    @Test
    fun repeatsShareOnePendingTargetAndClampAtTheEnds() {
        var pending: Long? = null
        var hold = 0L
        var direction = 0
        var last: SeekGesture? = null
        listOf(1_000L, 1_040L, 1_080L, 1_120L, 1_400L).forEach { now ->
            last = nextSeek(
                pendingTargetMs = pending,
                positionMs = 60_000L,
                originMs = 60_000L,
                durationMs = 600_000L,
                direction = 1,
                nowMs = now,
                holdStartMs = hold,
                previousDirection = direction,
            )
            pending = last!!.targetMs
            hold = last!!.holdStartMs
            direction = last!!.direction
        }
        assertEquals(110_000L, last!!.targetMs)
        assertEquals(60_000L, last!!.originMs)
        assertEquals(1_000L, last!!.holdStartMs)

        val start = nextSeek(5_000L, 5_000L, 5_000L, 600_000L, -1, 0L, 0L, -1)
        assertEquals(0L, start.targetMs)
        val end = nextSeek(595_000L, 595_000L, 500_000L, 600_000L, 1, 0L, 0L, 1)
        assertEquals(600_000L, end.targetMs)
    }

    @Test
    fun reversingDirectionStartsAFreshTapSizedStep() {
        val held = nextSeek(90_000L, 60_000L, 60_000L, 600_000L, 1, 200L, 0L, 1)
        assertEquals(100_000L, held.targetMs)
        assertEquals(60_000L, held.originMs)
        val reversed = nextSeek(held.targetMs, 60_000L, held.originMs, 600_000L, -1, 400L, held.holdStartMs, held.direction)
        assertEquals(90_000L, reversed.targetMs)
        assertEquals(400L, reversed.holdStartMs)
        assertEquals(60_000L, reversed.originMs)
        val longHold = nextSeek(90_000L, 60_000L, 60_000L, 600_000L, 1, 3_000L, 0L, 1)
        assertEquals(210_000L, longHold.targetMs)
    }

    @Test
    fun readoutShowsDirectionTargetAndDuration() {
        val forward = seekReadout(12 * 60_000L + 40_000L, 102 * 60_000L, 1)
        assertEquals("▶▶  快进", forward.direction)
        assertEquals("0:12:40", forward.target)
        assertEquals("1:42:00", forward.total)
        val backward = seekReadout(5_000L, 60_000L, -1)
        assertEquals("◀◀  快退", backward.direction)
        assertEquals("0:00:05", backward.target)
    }

    @Test
    fun latchHoldsTheTargetUntilThePlayerCatchesUp() {
        val latch = PositionLatch(targetMs = 110_000L, untilMs = 10_000L)
        assertEquals(110_000L, latchedDisplayMs(playerMs = 60_000L, latch, nowMs = 8_000L))
        assertNull(latchedDisplayMs(playerMs = 110_400L, latch, nowMs = 8_000L))
        assertNull(latchedDisplayMs(playerMs = 60_000L, latch, nowMs = 10_000L))
    }
}
