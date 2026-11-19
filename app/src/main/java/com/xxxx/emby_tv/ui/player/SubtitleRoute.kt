package com.xxxx.emby_tv.ui.player

/**
 * Text cues and FSPlayer/libass are mutually exclusive. Bitmap tracks
 * (PGS) and text that could not be parsed stay in the kernel, and the
 * Compose overlay stays empty.
 */
object SubtitleRoute {
    fun useOverlay(hasTextCues: Boolean): Boolean = hasTextCues

    fun allowKernel(
        hasTextCues: Boolean,
        externalRequested: Boolean,
        downloadSettled: Boolean,
    ): Boolean {
        if (useOverlay(hasTextCues)) return false
        if (externalRequested && !downloadSettled) return false
        return true
    }
}
