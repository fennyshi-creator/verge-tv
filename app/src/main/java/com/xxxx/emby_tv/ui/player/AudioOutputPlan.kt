package com.xxxx.emby_tv.ui.player

/**
 * Three playback choices. Saved switches are not rewritten here.
 *
 * Compatible decode wins: the box decodes and opens 2-channel PCM.
 * Passthrough and AAC-to-Dolby stay off for that open.
 *
 * Compatible off and passthrough automatic: supported compressed tracks
 * go to the receiver. AAC-to-Dolby follows its own switch. A route that
 * cannot carry the bitstream decodes instead, and that PCM is not forced
 * to stereo. TrueHD, DTS and other non-AC-3 codecs on the TV's own
 * speakers are the exception: they use the stereo downmix. HDMI keeps
 * their channel count.
 *
 * Compatible off and passthrough off: the box decodes PCM and may keep
 * the source channel count. AAC-to-Dolby does not run.
 */
object AudioOutputPlan {
    data class Result(
        val passthrough: Boolean,
        val aacAc3: Boolean,
        val downmixStereo: Boolean,
    )

    fun choose(
        compatDecode: Boolean,
        passthroughAuto: Boolean,
        speedIsOne: Boolean,
        recoveryStage: Int,
        routeAllowsBitstream: Boolean,
        aacMode: Int,
        aacProfile: Boolean,
        codec: String?,
        channels: Int,
        routeAllowsAc3: Boolean,
        builtInSpeakers: Boolean = false,
    ): Result {
        if (compatDecode) {
            return Result(passthrough = false, aacAc3 = false, downmixStereo = true)
        }
        if (forceStereoOnSpeakers(codec, builtInSpeakers)) {
            return Result(passthrough = false, aacAc3 = false, downmixStereo = true)
        }
        val passthrough = AudioPolicy.usePassthrough(
            decoderPreference = AudioPolicy.AUTO,
            forceStereo = !passthroughAuto,
            speedIsOne = speedIsOne,
            recoveryStage = recoveryStage,
            routeAllows = routeAllowsBitstream,
        )
        val aacAc3 = passthroughAuto && AacAc3Policy.enable(
            mode = aacMode,
            profile = aacProfile,
            codec = codec,
            channels = channels,
            speedIsOne = speedIsOne,
            forceStereo = false,
            routeAllowsAc3 = routeAllowsAc3,
            preferFfmpeg = false,
        )
        return Result(passthrough = passthrough, aacAc3 = aacAc3, downmixStereo = false)
    }

    /**
     * TrueHD, DTS and anything else that is not AC-3 / E-AC-3. On the TV's own
     * speakers this opens the existing stereo PCM downmix. HDMI to an AVR
     * keeps the source channel count. A successful E-AC-3 passthrough is not
     * this case.
     */
    fun forceStereoOnSpeakers(codec: String?, builtInSpeakers: Boolean): Boolean {
        if (!builtInSpeakers) return false
        val name = codec?.trim()?.lowercase().orEmpty()
        if (name.isEmpty()) return false
        return name != "ac3" && name != "ac-3" && name != "eac3" && name != "e-ac3" && name != "eac-3"
    }
}
