package com.xxxx.emby_tv.ui.player

/**
 * What to tell Emby about the playhead.
 *
 * A report of 0 ticks clears Continue Watching. A report at or past the
 * runtime marks the item watched. The Emby runtime on this library can be
 * longer than the file, so it is not a duration we are allowed to invent a
 * position from. A playhead past the real file, or a jump with no known
 * duration, keeps the resume the user already had.
 */
object PlaybackReport {
    @Suppress("UNUSED_PARAMETER")
    fun safePositionMs(
        playerPositionMs: Long,
        resumeMs: Long,
        runtimeMs: Long,
        playerDurationMs: Long,
        naturalEnd: Boolean,
    ): Long {
        val fromPlayer = playerPositionMs.coerceAtLeast(0L)
        val saved = resumeMs.coerceAtLeast(0L)
        val fileMs = if (playerDurationMs > 5_000L) playerDurationMs else 0L
        // runtimeMs stays in the signature so callers can pass the library length.
        // It is not a file duration and is never written back as the playhead.

        if (!naturalEnd) {
            if (fileMs > 0L) {
                // A resume already past the file (the stored ~120s) is not a
                // position. Start at the beginning instead of inventing one
                // near the end. A resume inside the file is kept.
                val kept = if (saved in 1 until fileMs) saved else 0L
                if (fromPlayer <= 0L || fromPlayer >= fileMs) return kept
                return fromPlayer.coerceAtMost(fileMs - 1_000L)
            }
            if (fromPlayer <= 0L) return saved
            // No measured file length yet. A leap past the resume is the bad clock
            // that stored ~120s. A few seconds of real playback still reports.
            if (fromPlayer > saved + 30_000L) return saved
            return fromPlayer
        }
        if (fileMs <= 0L) return if (fromPlayer <= 0L) saved else fromPlayer
        return fromPlayer.coerceAtMost(fileMs)
    }
}
