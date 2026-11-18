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

/**
 * Whether the active output should receive an E-AC-3 bitstream.
 * IEC 61937 (encoding 13) is still packed bitstream, not a PCM fallback.
 * A missing device list does not turn passthrough off: the Dune query is
 * unreliable, and a failed probe must not silence a working AVR.
 */
object PassthroughRoute {
    const val TYPE_BUILTIN_SPEAKER = 2
    const val TYPE_WIRED_HEADSET = 3
    const val TYPE_WIRED_HEADPHONES = 4
    const val TYPE_BUILTIN_EARPIECE = 1
    const val TYPE_BLUETOOTH_A2DP = 8
    const val TYPE_HDMI = 9
    const val TYPE_HDMI_ARC = 10
    const val TYPE_USB_DEVICE = 11
    const val TYPE_USB_HEADSET = 22
    const val TYPE_BLE_HEADSET = 26
    const val TYPE_BLE_SPEAKER = 27
    const val TYPE_HDMI_EARC = 29
    const val ENCODING_IEC61937 = 13

    const val MASK_HDMI = 0x400
    const val MASK_HDMI_ARC = 0x40000
    const val MASK_SPDIF = 0x80000
    const val MASK_SPEAKER = 0x2
    const val MASK_WIRED_HEADSET = 0x4
    const val MASK_WIRED_HEADPHONE = 0x8
    const val MASK_A2DP = 0x80

    private val digitalMasks = MASK_HDMI or MASK_HDMI_ARC or MASK_SPDIF
    private val localMasks = MASK_SPEAKER or MASK_WIRED_HEADSET or MASK_WIRED_HEADPHONE or MASK_A2DP

    fun isHdmiFamily(type: Int): Boolean =
        type == TYPE_HDMI || type == TYPE_HDMI_ARC || type == TYPE_HDMI_EARC

    fun isLocalSink(type: Int): Boolean = when (type) {
        TYPE_BUILTIN_SPEAKER, TYPE_WIRED_HEADSET, TYPE_WIRED_HEADPHONES,
        TYPE_BUILTIN_EARPIECE, TYPE_BLUETOOTH_A2DP, TYPE_USB_HEADSET,
        TYPE_BLE_HEADSET, TYPE_BLE_SPEAKER -> true
        else -> false
    }

    fun encodingsCarryBitstream(encodings: Set<Int>): Boolean =
        encodings.any {
            it == DolbyPassthrough.ENCODING_AC3 ||
                it == DolbyPassthrough.ENCODING_E_AC3 ||
                it == DolbyPassthrough.ENCODING_E_AC3_JOC ||
                it == ENCODING_IEC61937
        }

    /**
     * Empty [encodings] means the sink did not report a list. On HDMI that is
     * not treated as "PCM only", because some boxes omit E-AC-3 there while
     * the AVR still accepts the stream. A non-empty list with no Dolby or
     * IEC encoding is an endpoint that said it cannot take the bitstream.
     */
    fun allowBitstream(
        type: Int,
        encodings: Set<Int>,
        @Suppress("UNUSED_PARAMETER") directQuery: Boolean,
        maxChannels: Int = -1,
    ): Boolean {
        if (isLocalSink(type)) return false
        if (isHdmiFamily(type)) {
            if (encodings.isEmpty()) return true
            if (!encodingsCarryBitstream(encodings)) return false
            // A stereo HDMI sink that still lists E-AC-3 cannot play the bitstream.
            // ARC and eARC stay on the passthrough path for an AVR.
            if (type == TYPE_HDMI && maxChannels in 1..2) return false
            return true
        }
        if (encodings.isEmpty()) return true
        return encodingsCarryBitstream(encodings)
    }

    fun maxChannelCount(counts: List<Int>, maskBitCounts: List<Int>): Int {
        val fromCounts = counts.maxOrNull() ?: 0
        if (fromCounts > 0) return fromCounts
        val fromMasks = maskBitCounts.maxOrNull() ?: 0
        return if (fromMasks > 0) fromMasks else -1
    }

    data class Device(val type: Int, val encodings: Set<Int>, val maxChannels: Int = -1)

    /** Null when the route cannot be told apart. Callers keep the current mode. */
    fun selectActive(devices: List<Device>, streamMask: Int): Device? {
        if (devices.isEmpty()) return null
        if (streamMask and digitalMasks != 0) {
            return devices.firstOrNull { isHdmiFamily(it.type) }
        }
        if (streamMask and localMasks != 0) {
            return devices.firstOrNull { isLocalSink(it.type) }
        }
        val digital = devices.filter { isHdmiFamily(it.type) }
        val local = devices.filter { isLocalSink(it.type) }
        if (digital.isNotEmpty() && local.isEmpty()) return digital.first()
        if (local.isNotEmpty() && digital.isEmpty()) return local.first()
        return null
    }

    /** Unknown route stays on bitstream so a probe miss does not drop a working AVR. */
    fun allowActive(devices: List<Device>, streamMask: Int, directQuery: Boolean): Boolean {
        val active = selectActive(devices, streamMask) ?: return true
        return allowBitstream(active.type, active.encodings, directQuery, active.maxChannels)
    }
}

/**
 * Auto uses the route. Prefer-FFmpeg and 解码为立体声 both take the FFmpeg
 * stereo decode path. A failed passthrough open is the same path.
 */
object AudioPolicy {
    const val AUTO = 0
    const val FFMPEG = 1

    fun usePassthrough(
        decoderPreference: Int,
        forceStereo: Boolean,
        speedIsOne: Boolean,
        recoveryStage: Int,
        routeAllows: Boolean,
    ): Boolean {
        if (forceStereo || !speedIsOne || recoveryStage > 0) return false
        if (decoderPreference == FFMPEG) return false
        return routeAllows
    }
}

/**
 * HDR to SDR only when the user turns the switch on. Off keeps hardware
 * decode and direct HDR, including on an SDR display. On prefers the
 * MediaCodec color-transfer-request path; the Vulkan shader is the fallback.
 */
object HdrMap {
    const val SHADER = "vulkan-hdr2sdr"
    const val HARDWARE = "mediacodec-sdr"
    const val DIRECT = "hdr-direct"
    const val SDR = "sdr"
    const val HARDWARE_MIN_API = 31
    const val COLOR_TRANSFER_SDR_VIDEO = 3

    fun needsMap(
        manualOn: Boolean,
        @Suppress("UNUSED_PARAMETER") displayHdr: Boolean,
        sourceHdr: Boolean,
    ): Boolean = sourceHdr && manualOn

    fun mode(
        manualOn: Boolean,
        displayHdr: Boolean,
        sourceHdr: Boolean,
        api: Int = 30,
        hardwareRequestAvailable: Boolean = false,
    ): String {
        if (!sourceHdr) return SDR
        if (!needsMap(manualOn, displayHdr, sourceHdr)) return DIRECT
        return if (api >= HARDWARE_MIN_API && hardwareRequestAvailable) HARDWARE else SHADER
    }

    fun forceSoftwareDecode(
        manualOn: Boolean,
        displayHdr: Boolean,
        sourceHdr: Boolean,
        api: Int = 30,
        hardwareRequestAvailable: Boolean = false,
    ): Boolean = mode(manualOn, displayHdr, sourceHdr, api, hardwareRequestAvailable) == SHADER

    fun allowDirectHdr(
        manualOn: Boolean,
        displayHdr: Boolean = true,
        sourceHdr: Boolean = true,
        api: Int = 30,
        hardwareRequestAvailable: Boolean = false,
    ): Boolean = mode(manualOn, displayHdr, sourceHdr, api, hardwareRequestAvailable) != SHADER

    fun output(
        manualOn: Boolean,
        displayHdr: Boolean,
        sourceHdr: Boolean,
        api: Int = 30,
        hardwareRequestAvailable: Boolean = false,
    ): String = mode(manualOn, displayHdr, sourceHdr, api, hardwareRequestAvailable)
}
