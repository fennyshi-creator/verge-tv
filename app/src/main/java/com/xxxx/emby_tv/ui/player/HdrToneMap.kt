package com.xxxx.emby_tv.ui.player

import android.os.Build

/**
 * Arms the NDK MediaCodec configure hook. The hook writes
 * KEY_COLOR_TRANSFER_REQUEST = COLOR_TRANSFER_SDR_VIDEO (3) onto video
 * formats. API 31+ is required for decoders to honor that key.
 */
object HdrToneMap {
    const val MIN_API = 31

    private val nativeOk: Boolean = try {
        System.loadLibrary("fsmediacodec_sdr")
        nativeLoaded()
    } catch (_: Throwable) {
        false
    }

    fun hardwareRequestAvailable(api: Int = Build.VERSION.SDK_INT): Boolean =
        api >= MIN_API && nativeOk

    fun setRequestSdr(enabled: Boolean) {
        if (!nativeOk) return
        nativeSetRequestSdr(enabled && hardwareRequestAvailable())
    }

    private external fun nativeLoaded(): Boolean
    private external fun nativeSetRequestSdr(enabled: Boolean)
}
