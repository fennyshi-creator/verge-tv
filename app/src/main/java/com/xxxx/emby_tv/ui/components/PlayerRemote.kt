package com.xxxx.emby_tv.ui.components

/**
 * Remote behavior for the playback overlay.
 *
 * The picture keeps the remote until the bar is open. Once the bar is open,
 * arrows and OK belong to whichever control is focused. Sheets, the episode
 * strip, and the resume prompt are never intercepted here.
 *
 * Hidden, playing:
 * - OK down / key-repeat: consumed, playback stays running.
 * - OK up, after that down: pause, open the bar, focus the central Play button.
 * - Left / Right down and key-repeat: seek immediately, open the bar on the
 *   timeline, and show the central target-time readout.
 * - Down: open the bar on the timeline. Playback keeps running.
 * - Back: leave playback.
 *
 * Hidden, paused:
 * - OK up: open the bar on the central Play button. Playback stays paused.
 * - Left / Right: seek, and stay paused.
 * - Down: open the bar on the timeline.
 *
 * Bar open, central Play focused (paused):
 * - OK: resume. The bar hides itself after the normal playing timeout.
 * - Down: timeline. Up: subtitle, audio, version, settings.
 * - Back: hide the bar and the seek readout. A second Back leaves playback.
 *
 * Bar open, timeline focused:
 * - Left / Right: the same seek and the same central readout.
 * - OK while playing: pause, then move focus to the central Play button.
 * - OK while paused: resume.
 * - Up while paused: central Play. Up while playing: the secondary buttons.
 * - Down, on an episode: the episode strip.
 * - Back: hide the overlay.
 *
 * Bar open, a secondary button focused:
 * - Left / Right move between those buttons and do not seek.
 * - OK opens that button's sheet.
 * - Down while paused: central Play. Down while playing: timeline.
 *
 * A sheet, the episode strip, or the resume prompt:
 * - arrows and OK pass through.
 * - Back closes that layer first.
 *
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

enum class PlayerLanding { Hold, Timeline, CentralPlay }

enum class RemoteCommand {
    Ignore,
    PauseAndFocusPlay,
    RevealPausedPlay,
    RevealTimeline,
    SeekBackward,
    SeekForward,
    HideOverlay,
}

data class RemoteContext(
    val controlsVisible: Boolean,
    val playing: Boolean,
    val sheetOpen: Boolean,
    val episodeStrip: Boolean,
    val resumePrompt: Boolean,
    val seeking: Boolean,
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
    if (context.controlsVisible) {
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
        PlayerKey.Down -> when (phase) {
            KeyPhase.Down -> RemoteDecision(RemoteCommand.RevealTimeline, consume = true)
            else -> RemoteDecision(RemoteCommand.Ignore, consume = true)
        }
        PlayerKey.Ok -> when (phase) {
            KeyPhase.Up -> {
                val command = if (context.playing) {
                    RemoteCommand.PauseAndFocusPlay
                } else {
                    RemoteCommand.RevealPausedPlay
                }
                RemoteDecision(command, consume = true)
            }
            else -> RemoteDecision(RemoteCommand.Ignore, consume = true)
        }
        else -> RemoteDecision(RemoteCommand.Ignore, consume = false)
    }
}

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
