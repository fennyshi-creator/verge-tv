package com.xxxx.emby_tv.ui.player

/**
 * An engine-create failure means the video output never existed.
 * Reopening for PCM or software decode calls the same native setup and spins.
 */
object PlaybackErrorPolicy {
    const val ENGINE_CREATE = "engine-create"
    const val VIDEO_DECODE = "video-decode"
    const val AUDIO_RELEASE = "audio-release"
    const val AUDIO_PASSTHROUGH = "audio-passthrough"

    fun stopsWithoutReopen(message: String): Boolean =
        message.startsWith(ENGINE_CREATE) ||
            message.startsWith(VIDEO_DECODE) ||
            message.startsWith(AUDIO_RELEASE) ||
            message.startsWith(AUDIO_PASSTHROUGH)

    /** Milliseconds the engine thread actually waited, when the message recorded it. */
    fun waitedMs(message: String): Long? {
        val marker = "waitedMs="
        val start = message.indexOf(marker)
        if (start < 0) return null
        val digits = message.substring(start + marker.length).takeWhile { it.isDigit() }
        return digits.toLongOrNull()
    }
}
