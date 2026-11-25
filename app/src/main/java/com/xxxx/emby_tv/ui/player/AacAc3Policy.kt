package com.xxxx.emby_tv.ui.player

/**
 * AAC multichannel is decoded, then optionally re-encoded to AC-3 for HDMI.
 * AC-3 and E-AC-3 sources never take this path. Auto enables it only for the
 * verified Realtek tv794a / thor / mjolnir profile when the route can carry AC-3.
 */
object AacAc3Policy {
    const val AUTO = 0
    const val ON = 1
    const val OFF = 2

    fun isVerifiedProfile(
        manufacturer: String?,
        hardware: String?,
        board: String?,
        model: String?,
    ): Boolean {
        val realtek = manufacturer?.contains("realtek", ignoreCase = true) == true
        val identity = model?.contains("tv794a", ignoreCase = true) == true ||
            hardware.equals("thor", ignoreCase = true) ||
            board?.contains("mjolnir", ignoreCase = true) == true
        return realtek && identity
    }

    /**
     * The verified profile's HDMI query reports 2 channels even when the AVR
     * accepts E-AC-3, so Auto still treats that HDMI as AC-3 capable.
     * Speakers, headphones, and every other device keep the reported route.
     */
    fun sinkAllows(profile: Boolean, allowBitstream: Boolean, hdmiFamily: Boolean): Boolean {
        if (allowBitstream) return true
        return profile && hdmiFamily
    }

    fun enable(
        mode: Int,
        profile: Boolean,
        codec: String?,
        channels: Int,
        speedIsOne: Boolean,
        forceStereo: Boolean,
        routeAllowsAc3: Boolean,
        preferFfmpeg: Boolean,
    ): Boolean {
        if (mode == OFF || forceStereo || !speedIsOne || !routeAllowsAc3) return false
        if (channels <= 2) return false
        if (!codec.equals("aac", ignoreCase = true)) return false
        if (mode == ON) return true
        if (preferFfmpeg) return false
        return mode == AUTO && profile
    }

    fun label(mode: Int): String = when (mode) {
        ON -> "开"
        OFF -> "关"
        else -> "自动"
    }
}
