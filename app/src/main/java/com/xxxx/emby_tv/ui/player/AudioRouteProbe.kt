package com.xxxx.emby_tv.ui.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build

/**
 * Reads the music route. The decision itself is [PassthroughRoute], so a
 * speaker or headphone route decodes and an HDMI route can still bitstream
 * when the direct-playback query is wrong.
 */
object AudioRouteProbe {
    fun allowsBitstream(context: Context): Boolean {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return true
        if (Build.VERSION.SDK_INT >= 33) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build()
            val routed = manager.getAudioDevicesForAttributes(attributes)
            val active = routed.firstOrNull()
            if (active != null) {
                return PassthroughRoute.allowBitstream(
                    active.type,
                    active.encodings?.toSet() ?: emptySet(),
                    directEac3(),
                )
            }
        }
        val devices = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { info ->
            PassthroughRoute.Device(
                type = info.type,
                encodings = info.encodings?.toSet() ?: emptySet(),
            )
        }
        return PassthroughRoute.allowActive(devices, streamMask = 0, directQuery = directEac3())
    }

    private fun directEac3(): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        return try {
            val format = AudioFormat.Builder()
                .setEncoding(DolbyPassthrough.ENCODING_E_AC3)
                .setSampleRate(48000)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build()
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build()
            AudioTrack.isDirectPlaybackSupported(format, attributes)
        } catch (_: Throwable) {
            false
        }
    }
}
