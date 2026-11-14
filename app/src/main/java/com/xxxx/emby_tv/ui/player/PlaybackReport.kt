package com.xxxx.emby_tv.ui.player

/**
 * What to tell Emby about the playhead.
 *
 * A report of 0 ticks clears Continue Watching. A report at or past the
 * runtime marks the item watched. A failed session must do neither: keep the
 * position the user already had, and never claim the title finished.
 */
object PlaybackReport {
    fun safePositionMs(
        playerPositionMs: Long,
        resumeMs: Long,
        runtimeMs: Long,
        playerDurationMs: Long,
        naturalEnd: Boolean,
    ): Long {
        val fromPlayer = playerPositionMs.coerceAtLeast(0L)
        val saved = resumeMs.coerceAtLeast(0L)
        val knownDuration = when {
            playerDurationMs > 5_000L -> playerDurationMs
            runtimeMs > 5_000L -> runtimeMs
            else -> 0L
        }
        if (!naturalEnd) {
            val kept = if (fromPlayer <= 0L) saved else fromPlayer
            if (knownDuration <= 0L) return kept
            return kept.coerceAtMost(knownDuration * 4L / 5L)
        }
        if (knownDuration <= 0L) return fromPlayer
        return fromPlayer.coerceAtMost(knownDuration)
    }
}
