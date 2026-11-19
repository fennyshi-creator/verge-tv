package com.xxxx.emby_tv.data.local

/**
 * Playback choices that must survive leaving the player. A new [Session]
 * is the next playback. [restore] copies what the previous session stored.
 */
object PlaybackMemory {
    const val DECODER_AUTO = 0
    const val DECODER_FFMPEG = 1
    const val DEFAULT_SUBTITLE_SCALE = 1f
    const val DEFAULT_SUBTITLE_INK = 0xFFFFFFFF.toInt()

    class Session {
        var decodeToStereo: Boolean = false
        var hdrToSdr: Boolean = false
        var decoderPreference: Int = DECODER_AUTO
        var subtitleScale: Float = DEFAULT_SUBTITLE_SCALE
        var subtitleInk: Int = DEFAULT_SUBTITLE_INK
        var showDiagnostics: Boolean = false
        var playbackCorrection: Int = 0
        var playMode: Int = 0

        fun restore(): Session {
            val next = Session()
            next.decodeToStereo = decodeToStereo
            next.hdrToSdr = hdrToSdr
            next.decoderPreference = decoderPreference
            next.subtitleScale = subtitleScale
            next.subtitleInk = subtitleInk
            next.showDiagnostics = showDiagnostics
            next.playbackCorrection = playbackCorrection
            next.playMode = playMode
            return next
        }
    }
}
