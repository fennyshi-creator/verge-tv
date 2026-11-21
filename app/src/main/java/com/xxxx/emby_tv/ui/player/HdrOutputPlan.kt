package com.xxxx.emby_tv.ui.player

/**
 * Mapping off stays on hardware decode and direct HDR.
 * A GLES device does not turn an explicit shader request into a hidden software video decode.
 * API 31+ hardware color-transfer-request is unchanged when the user turns mapping on.
 */
object HdrOutputPlan {
    data class Choice(
        val mode: String,
        val mapHdrToSdr: Boolean,
        val requestHardwareSdr: Boolean,
        val windowSurface: Boolean,
        val softwareVideo: Boolean,
    )

    fun choose(
        manualOn: Boolean,
        sourceHdr: Boolean,
        vendorGles: Boolean,
        api: Int,
        hardwareRequestAvailable: Boolean,
    ): Choice {
        val mode = HdrMap.mode(
            manualOn = manualOn,
            displayHdr = false,
            sourceHdr = sourceHdr,
            api = api,
            hardwareRequestAvailable = hardwareRequestAvailable,
        )
        val shader = mode == HdrMap.SHADER
        val map = shader && !vendorGles
        val request = mode == HdrMap.HARDWARE
        val window = vendorGles && sourceHdr && !map && !request
        val applied = if (vendorGles && shader) "direct-unmapped" else mode
        return Choice(
            mode = applied,
            mapHdrToSdr = map,
            requestHardwareSdr = request,
            windowSurface = window,
            softwareVideo = map,
        )
    }
}
