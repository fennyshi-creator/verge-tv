package com.xxxx.emby_tv.ui.player

/**
 * Text for the corner playback overlay. HDR tone-map is reported here.
 * The MediaCodec configure call lives in the prebuilt FFmpeg decoder, so
 * KEY_COLOR_TRANSFER_REQUEST cannot be set from the app. API 31+ is noted
 * when a request would have been made.
 */
object PlaybackDebug {
    const val COLOR_TRANSFER_SDR_VIDEO = 3

    fun deviceName(type: Int): String = when (type) {
        PassthroughRoute.TYPE_BUILTIN_SPEAKER -> "内置扬声器"
        PassthroughRoute.TYPE_WIRED_HEADSET, PassthroughRoute.TYPE_WIRED_HEADPHONES -> "有线耳机"
        PassthroughRoute.TYPE_USB_HEADSET -> "USB 耳机"
        PassthroughRoute.TYPE_BLUETOOTH_A2DP -> "蓝牙"
        PassthroughRoute.TYPE_HDMI -> "HDMI"
        PassthroughRoute.TYPE_HDMI_ARC -> "HDMI ARC"
        PassthroughRoute.TYPE_HDMI_EARC -> "HDMI eARC"
        0 -> "未知路由"
        else -> "设备 $type"
    }

    fun encodingNames(encodings: Set<Int>): String {
        if (encodings.isEmpty()) return "未上报"
        return encodings.sorted().joinToString(",") { code ->
            when (code) {
                DolbyPassthrough.ENCODING_PCM_16BIT -> "PCM"
                DolbyPassthrough.ENCODING_AC3 -> "AC3"
                DolbyPassthrough.ENCODING_E_AC3 -> "EAC3"
                DolbyPassthrough.ENCODING_E_AC3_JOC -> "JOC"
                PassthroughRoute.ENCODING_IEC61937 -> "IEC"
                else -> code.toString()
            }
        }
    }

    fun openedOutput(encoding: Int, fellBack: Boolean, forcedDecode: Boolean): String {
        if (forcedDecode || fellBack || encoding == 0 || encoding == DolbyPassthrough.ENCODING_PCM_16BIT) {
            return "PCM 立体声"
        }
        return if (encoding == PassthroughRoute.ENCODING_IEC61937) "IEC 192 kHz" else "透传 $encoding"
    }

    fun modeLine(opened: String): String =
        if (opened.startsWith("PCM")) "解码 PCM 立体声" else "透传 IEC"

    fun sourceIsHdr(videoRange: String?, rangeType: String?, transfer: String?, codec: String?): Boolean {
        val blob = listOf(videoRange, rangeType, transfer, codec).joinToString(" ").lowercase()
        return blob.contains("hdr") || blob.contains("hlg") || blob.contains("dolby") ||
            blob.contains("dovi") || blob.contains("smpte2084") || blob.contains("arib")
    }

    fun sourceLabel(videoRange: String?, rangeType: String?, transfer: String?, codec: String?): String {
        val blob = listOf(videoRange, rangeType, transfer, codec).joinToString(" ").lowercase()
        return when {
            blob.contains("dolby") || blob.contains("dovi") -> "片源 Dolby Vision"
            blob.contains("hlg") || blob.contains("arib") -> "片源 HLG"
            blob.contains("hdr") || blob.contains("smpte2084") -> "片源 HDR10"
            else -> "片源 SDR"
        }
    }

    fun displayLabel(supportsHdr: Boolean): String = if (supportsHdr) "屏幕支持HDR" else "仅SDR"

    fun hdrOutput(
        sourceHdr: Boolean,
        displayHdr: Boolean,
        @Suppress("UNUSED_PARAMETER") api: Int,
        manualOn: Boolean = false,
    ): String = when (HdrMap.output(manualOn, displayHdr, sourceHdr)) {
        HdrMap.DIRECT -> "直出 HDR"
        HdrMap.SHADER -> "映射 SDR（Vulkan 着色器）"
        else -> "直出 SDR"
    }

    fun lines(
        deviceType: Int,
        encodings: Set<Int>,
        codec: String?,
        channels: Int?,
        sampleRate: Int?,
        openedEncoding: Int,
        fellBack: Boolean,
        forcedDecode: Boolean,
        videoRange: String?,
        rangeType: String?,
        transfer: String?,
        videoCodec: String?,
        displayHdr: Boolean,
        api: Int,
        manualHdrToSdr: Boolean = false,
    ): List<String> {
        val opened = openedOutput(openedEncoding, fellBack, forcedDecode)
        val source = listOfNotNull(codec, channels?.let { "${it}ch" }, sampleRate?.let { "${it} Hz" })
            .joinToString(" ")
            .ifBlank { "未知" }
        return listOf(
            modeLine(opened),
            "设备 ${deviceName(deviceType)}",
            "编码 ${encodingNames(encodings)}",
            "音源 $source",
            "输出 $opened",
            sourceLabel(videoRange, rangeType, transfer, videoCodec),
            displayLabel(displayHdr),
            hdrOutput(
                sourceIsHdr(videoRange, rangeType, transfer, videoCodec),
                displayHdr,
                api,
                manualHdrToSdr,
            ),
        )
    }
}
