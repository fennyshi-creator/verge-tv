package com.xxxx.emby_tv.ui.player

import com.xxxx.emby_tv.data.model.MediaStreamDto

/**
 * Codec to Android AudioFormat mapping for AC-3 / E-AC-3 / E-AC-3 JOC.
 * Numbers match third_party/fsplayer/ijkmedia/ijkplayer/fs_passthrough.h.
 * TrueHD is never passthrough. A rejected AudioTrack open is PCM decode.
 */
object DolbyPassthrough {
    const val ENCODING_PCM_16BIT = 2
    const val ENCODING_AC3 = 5
    const val ENCODING_E_AC3 = 6
    const val ENCODING_E_AC3_JOC = 18

    fun wantsJoc(stream: MediaStreamDto?): Boolean {
        if (stream == null) return false
        val codec = stream.codec?.lowercase().orEmpty()
        if (codec == "truehd" || codec == "mlp") return false
        if (codec != "eac3" && codec != "e-ac3" && codec != "eac-3") return false
        val text = listOf(stream.profile, stream.title, stream.displayTitle, stream.codecTag)
            .joinToString(" ") { it.orEmpty() }
            .lowercase()
        return text.contains("joc") || text.contains("atmos")
    }

    /**
     * Encoding we will ask AudioTrack to open. 0 means decode to PCM.
     * [endpointOpenOk] is -1 before the open is attempted, 1 if it succeeded, 0 if it failed.
     */
    fun encodingFor(
        codec: String?,
        joc: Boolean,
        enabled: Boolean,
        speedIsOne: Boolean,
        endpointOpenOk: Int = -1,
    ): Decision {
        if (!enabled || !speedIsOne) {
            return Decision(encoding = 0, joc = false, fallbackPcm = false)
        }
        val wanted = when (codec?.lowercase()) {
            "ac3", "ac-3" -> ENCODING_AC3
            "eac3", "e-ac3", "eac-3" -> if (joc) ENCODING_E_AC3_JOC else ENCODING_E_AC3
            else -> 0
        }
        if (wanted != 0 && endpointOpenOk == 0) {
            return Decision(encoding = 0, joc = false, fallbackPcm = true)
        }
        return Decision(encoding = wanted, joc = wanted == ENCODING_E_AC3_JOC, fallbackPcm = false)
    }

    fun statusLine(stream: MediaStreamDto?, openedEncoding: Int, fellBackToPcm: Boolean): String {
        val codec = stream?.codec?.lowercase().orEmpty()
        val joc = wantsJoc(stream)
        val name = when {
            codec == "truehd" || codec == "mlp" -> "TrueHD"
            codec == "ac3" || codec == "ac-3" -> "AC3"
            codec == "eac3" || codec == "e-ac3" || codec == "eac-3" ->
                if (joc || openedEncoding == ENCODING_E_AC3_JOC) "E-AC-3 JOC" else "E-AC-3"
            codec == "aac" -> "AAC"
            codec.isBlank() -> "音频"
            else -> codec.uppercase()
        }
        val passthrough = when (openedEncoding) {
            ENCODING_AC3, ENCODING_E_AC3, ENCODING_E_AC3_JOC -> true
            else -> false
        }
        val mode = if (passthrough && !fellBackToPcm) "透传" else "解码"
        val layout = if (passthrough) null else pcmLayout(stream?.channels)
        val label = if (layout == null) name else "$name $layout"
        return "「$label · $mode」"
    }

    /** Speaker layout for decoded PCM. Passthrough lines do not use this. */
    fun pcmLayout(channels: Int?): String? = when (channels) {
        null, 0 -> null
        1 -> "1.0"
        2 -> "2.0"
        3 -> "2.1"
        4 -> "4.0"
        5 -> "5.0"
        6 -> "5.1"
        7 -> "6.1"
        8 -> "7.1"
        else -> "${channels}ch"
    }

    data class Decision(val encoding: Int, val joc: Boolean, val fallbackPcm: Boolean)
}
