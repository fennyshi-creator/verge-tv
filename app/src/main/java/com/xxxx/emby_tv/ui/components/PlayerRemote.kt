package com.xxxx.emby_tv.ui.components

/**
 * Remote behavior for the playback overlay.
 *
 * The picture stays clean whenever the bar is hidden. A center glyph exists
 * only as the frosted pause mark while the bar is showing a user pause.
 * Nothing is drawn in the center while content is playing.
 *
 * Hidden, playing:
 * - OK down / key-repeat: consumed, playback stays running.
 * - OK up: pause and show the bar. Focus stays off the progress bar.
 * - Left / Right: seek immediately and show the compact center readout.
 * - Down or Menu: show the bar. Playback keeps running.
 * - Back: leave playback.
 *
 * Hidden, paused:
 * - OK up: resume. No center glyph is shown.
 * - Left / Right: seek, and stay paused.
 * - Down or Menu: show the bar.
 *
 * Bar open, focus not on a menu icon:
 * - OK pauses or resumes. Resume dismisses the bar quickly.
 * - Left / Right seek with the same readout.
 * - Back hides the bar first.
 *
 * Bar open, focus on subtitles, audio, version, or settings:
 * - OK opens that sheet. Arrows move among the icons.
 *
 * A sheet, the episode strip, or the resume prompt keeps its own keys.
 * Dedicated media play, pause, and play/pause keys are not consumed.
 */
enum class PlayerKey {
    Ok,
    Left,
    Right,
    Down,
    Back,
    MediaPlay,
    MediaPause,
    MediaPlayPause,
    Other,
}

enum class KeyPhase { Down, Repeat, Up }

enum class RemoteCommand {
    Ignore,
    Pause,
    Resume,
    RevealBar,
    SeekBackward,
    SeekForward,
    HideOverlay,
}

enum class CenterGlyph { None, Pause }

data class RemoteContext(
    val controlsVisible: Boolean,
    val playing: Boolean,
    val sheetOpen: Boolean,
    val episodeStrip: Boolean,
    val resumePrompt: Boolean,
    val seeking: Boolean,
    val menuFocused: Boolean = false,
)

data class RemoteDecision(
    val command: RemoteCommand,
    val consume: Boolean,
)

fun decideRemote(context: RemoteContext, key: PlayerKey, phase: KeyPhase): RemoteDecision {
    if (key == PlayerKey.MediaPlay || key == PlayerKey.MediaPause || key == PlayerKey.MediaPlayPause) {
        return RemoteDecision(RemoteCommand.Ignore, consume = false)
    }
    if (context.resumePrompt || context.sheetOpen || context.episodeStrip) {
        return RemoteDecision(RemoteCommand.Ignore, consume = false)
    }
    if (key == PlayerKey.Back) {
        if (phase == KeyPhase.Down && (context.controlsVisible || context.seeking)) {
            return RemoteDecision(RemoteCommand.HideOverlay, consume = true)
        }
        return RemoteDecision(RemoteCommand.Ignore, consume = false)
    }
    val menuOwnsArrows = context.controlsVisible && context.menuFocused
    if (menuOwnsArrows && (key == PlayerKey.Ok || key == PlayerKey.Left || key == PlayerKey.Right || key == PlayerKey.Down)) {
        return RemoteDecision(RemoteCommand.Ignore, consume = false)
    }
    return when (key) {
        PlayerKey.Left -> when (phase) {
            KeyPhase.Up -> RemoteDecision(RemoteCommand.Ignore, consume = true)
            else -> RemoteDecision(RemoteCommand.SeekBackward, consume = true)
        }
        PlayerKey.Right -> when (phase) {
            KeyPhase.Up -> RemoteDecision(RemoteCommand.Ignore, consume = true)
            else -> RemoteDecision(RemoteCommand.SeekForward, consume = true)
        }
        PlayerKey.Down -> when {
            context.controlsVisible -> RemoteDecision(RemoteCommand.Ignore, consume = false)
            phase == KeyPhase.Down -> RemoteDecision(RemoteCommand.RevealBar, consume = true)
            else -> RemoteDecision(RemoteCommand.Ignore, consume = true)
        }
        PlayerKey.Ok -> when (phase) {
            KeyPhase.Up -> RemoteDecision(
                if (context.playing) RemoteCommand.Pause else RemoteCommand.Resume,
                consume = true,
            )
            else -> RemoteDecision(RemoteCommand.Ignore, consume = true)
        }
        else -> RemoteDecision(RemoteCommand.Ignore, consume = false)
    }
}

/** Pause mark only while the bar is up and the user has paused. Never a play glyph. */
fun centerGlyph(chromeVisible: Boolean, playing: Boolean, seeking: Boolean): CenterGlyph {
    if (!chromeVisible || playing || seeking) return CenterGlyph.None
    return CenterGlyph.Pause
}

const val CHROME_PLAYING_MS = 5_000L
const val CHROME_RESUME_MS = 650L

/** After an explicit resume the bar leaves quickly. Opening it while playing keeps the longer hold. */
fun chromeHoldMs(resumed: Boolean): Long = if (resumed) CHROME_RESUME_MS else CHROME_PLAYING_MS

/** Center seek type is readable and compact. 48sp was the oversized 2.0.44 readout. */
const val SEEK_READOUT_TARGET_SP = 26
const val SEEK_READOUT_CUE_SP = 13

/**
 * One OK press is a down followed by an up. Repeats between them must not
 * toggle playback a second time, and an up that never had a down is ignored.
 */
class OkLatch {
    private var armed = false

    fun consume(phase: KeyPhase): Boolean = when (phase) {
        KeyPhase.Down -> {
            armed = true
            false
        }
        KeyPhase.Repeat -> false
        KeyPhase.Up -> {
            val fire = armed
            armed = false
            fire
        }
    }

    fun cancel() {
        armed = false
    }
}

data class SeekGesture(
    val targetMs: Long,
    val originMs: Long,
    val holdStartMs: Long,
    val direction: Int,
)

/**
 * One pending target for a held direction. Each repeat moves that target;
 * the caller commits it once after the key goes quiet.
 * Changing direction starts a new hold so the step drops back to a tap.
 */
fun nextSeek(
    pendingTargetMs: Long?,
    positionMs: Long,
    originMs: Long,
    durationMs: Long,
    direction: Int,
    nowMs: Long,
    holdStartMs: Long,
    previousDirection: Int,
): SeekGesture {
    val fresh = pendingTargetMs == null || direction != previousDirection
    val started = if (fresh) nowMs else holdStartMs
    val step = seekStepMs((nowMs - started).coerceAtLeast(0L), durationMs)
    val base = pendingTargetMs ?: positionMs
    val span = durationMs.coerceAtLeast(0L)
    val target = (base + direction * step).coerceIn(0L, span)
    return SeekGesture(
        targetMs = target,
        originMs = if (pendingTargetMs == null) positionMs else originMs,
        holdStartMs = started,
        direction = direction,
    )
}

const val SEEK_QUIET_MS = 520L
const val SEEK_READOUT_HOLD_MS = 700L
const val SEEK_LATCH_MS = 2_500L

data class PositionLatch(val targetMs: Long, val untilMs: Long)

/**
 * After a seek is committed, keep showing the target until the player reports
 * a position near it. A late poll must not pull the timeline backward.
 * Returns null when the latch should be dropped.
 */
fun latchedDisplayMs(
    playerMs: Long,
    latch: PositionLatch?,
    nowMs: Long,
    toleranceMs: Long = 1_500L,
): Long? {
    latch ?: return null
    if (nowMs >= latch.untilMs) return null
    if (kotlin.math.abs(playerMs - latch.targetMs) <= toleranceMs) return null
    return latch.targetMs
}

data class SeekReadout(
    val direction: String,
    val target: String,
    val total: String,
)

fun seekReadout(targetMs: Long, durationMs: Long, direction: Int): SeekReadout {
    val directionLabel = when {
        direction < 0 -> "◀◀  快退"
        direction > 0 -> "▶▶  快进"
        else -> "定位"
    }
    return SeekReadout(
        direction = directionLabel,
        target = formatPlayerClock(targetMs),
        total = formatPlayerClock(durationMs),
    )
}
