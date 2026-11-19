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
    data class Snapshot(
        val type: Int,
        val encodings: Set<Int>,
        val allowBitstream: Boolean,
    )

    fun read(context: Context): Snapshot {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return Snapshot(0, emptySet(), true)
        val direct = directEac3()
        if (Build.VERSION.SDK_INT >= 33) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build()
            val active = manager.getAudioDevicesForAttributes(attributes).firstOrNull()
            if (active != null) {
                val encodings = active.encodings?.toSet() ?: emptySet()
                val channels = channelCount(active)
                return Snapshot(
                    active.type,
                    encodings,
                    PassthroughRoute.allowBitstream(active.type, encodings, direct, channels),
                )
            }
        }
        val devices = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { info ->
            PassthroughRoute.Device(
                info.type,
                info.encodings?.toSet() ?: emptySet(),
                channelCount(info),
            )
        }
        val active = PassthroughRoute.selectActive(devices, 0)
        val encodings = active?.encodings ?: emptySet()
        val type = active?.type ?: 0
        return Snapshot(type, encodings, PassthroughRoute.allowActive(devices, 0, direct))
    }

    fun allowsBitstream(context: Context): Boolean = read(context).allowBitstream

    private fun channelCount(info: android.media.AudioDeviceInfo): Int =
        PassthroughRoute.maxChannelCount(
            info.channelCounts?.toList() ?: emptyList(),
            info.channelMasks?.map { Integer.bitCount(it) } ?: emptyList(),
        )

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
