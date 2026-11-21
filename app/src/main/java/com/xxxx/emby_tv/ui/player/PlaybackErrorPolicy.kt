package com.xxxx.emby_tv.ui.player

/**
 * An engine-create failure means the video output never existed.
 * Reopening for PCM or software decode calls the same native setup and spins.
 */
object PlaybackErrorPolicy {
    const val ENGINE_CREATE = "engine-create"
    const val VIDEO_DECODE = "video-decode"
    const val AUDIO_RELEASE = "audio-release"

    fun stopsWithoutReopen(message: String): Boolean =
        message.startsWith(ENGINE_CREATE) ||
            message.startsWith(VIDEO_DECODE) ||
            message.startsWith(AUDIO_RELEASE)
}
