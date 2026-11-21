package com.xxxx.emby_tv.ui.player

import com.xxxx.emby_tv.data.model.MediaSourceInfoDto
import com.xxxx.emby_tv.data.model.MediaStreamDto
import com.xxxx.emby_tv.util.EmbyPresentation
import java.util.Locale
import kotlin.math.roundToInt

data class StreamInfoLine(val label: String, val value: String)

/**
 * Facts taken from the source that is actually playing. A missing or zero
 * field is left out instead of filled with a guess.
 */
object StreamInfo {
    fun lines(source: MediaSourceInfoDto?, audioIndex: Int): List<StreamInfoLine> {
        if (source == null) return emptyList()
        val video = source.mediaStreams.orEmpty().firstOrNull { it.type.equals("Video", true) }
        val lines = mutableListOf<StreamInfoLine>()
        resolution(video)?.let { lines += StreamInfoLine("分辨率", it) }
        videoCodec(video)?.let { lines += StreamInfoLine("视频编码", it) }
        frameRate(video)?.let { lines += StreamInfoLine("帧率", it) }
        bitrate(video?.bitRate ?: source.bitrate)?.let { lines += StreamInfoLine("码率", it) }
        color(video)?.let { lines += StreamInfoLine("色彩", it) }
        source.container?.takeIf { it.isNotBlank() }?.let { lines += StreamInfoLine("容器", it) }
        audio(source, audioIndex)?.let { lines += StreamInfoLine("音频", it) }
        source.name?.takeIf { it.isNotBlank() }?.let { lines += StreamInfoLine("片源", it) }
        delivery(source)?.let { lines += StreamInfoLine("方式", it) }
        return lines
    }

    fun resolution(video: MediaStreamDto?): String? {
        val width = video?.width
        val height = video?.height
        if (width != null && height != null && width > 0 && height > 0) {
            return "${width}×${height}"
        }
        return null
    }

    fun videoCodec(video: MediaStreamDto?): String? {
        if (video == null) return null
        val codec = video.codec?.takeIf { it.isNotBlank() }?.uppercase(Locale.US) ?: return null
        val profile = video.profile?.takeIf { it.isNotBlank() }
        return if (profile == null) codec else "$codec · $profile"
    }

    fun frameRate(video: MediaStreamDto?): String? {
        if (video == null) return null
        val raw = video.realFrameRate?.takeIf { it > 0.0 } ?: video.averageFrameRate?.takeIf { it > 0.0 } ?: return null
        val text = if (raw % 1.0 == 0.0) {
            raw.roundToInt().toString()
        } else {
            String.format(Locale.US, "%.3f", raw).trimEnd('0').trimEnd('.')
        }
        return "$text fps"
    }

    /** Bits per second. Values under 1000 are probe noise, not a rate. */
    fun bitrate(bitsPerSecond: Int?): String? {
        if (bitsPerSecond == null || bitsPerSecond < 1000) return null
        val kbps = bitsPerSecond / 1000.0
        return if (kbps >= 1000) {
            val mbps = kbps / 1000.0
            val nearest = mbps.roundToInt()
            val text = if (kotlin.math.abs(mbps - nearest) < 0.05) nearest.toString() else String.format(Locale.US, "%.1f", mbps)
            "$text Mbps"
        } else {
            "${kbps.roundToInt()} kbps"
        }
    }

    fun color(video: MediaStreamDto?): String? {
        if (video == null) return null
        val hdr = EmbyPresentation.hdrLabel(
            video.videoRangeType,
            video.extendedVideoType,
            video.videoRange,
            video.colorTransfer,
            video.displayTitle,
        )
        val explicitSdr = hdr == null && video.videoRange?.equals("SDR", ignoreCase = true) == true
        val head = hdr ?: if (explicitSdr) "SDR" else null
        val depth = video.bitDepth?.takeIf { it > 0 }?.let { "$it bit" }
        return listOfNotNull(
            head,
            video.colorSpace?.takeIf { it.isNotBlank() },
            video.colorTransfer?.takeIf { it.isNotBlank() },
            video.colorPrimaries?.takeIf { it.isNotBlank() },
            depth,
        ).joinToString(" · ").ifBlank { null }
    }

    fun audio(source: MediaSourceInfoDto, audioIndex: Int): String? {
        val tracks = source.mediaStreams.orEmpty().filter { it.type.equals("Audio", true) }
        if (tracks.isEmpty()) return null
        val chosen = tracks.firstOrNull { audioIndex >= 0 && it.index == audioIndex }
            ?: tracks.firstOrNull { it.isDefault == true }
            ?: tracks.first()
        val parts = mutableListOf<String>()
        EmbyPresentation.audioChip(listOf(chosen))?.let { parts += it }
        chosen.sampleRate?.takeIf { it > 0 }?.let { parts += sampleRate(it) }
        bitrate(chosen.bitRate)?.let { parts += it }
        chosen.language?.takeIf { it.isNotBlank() }?.let { parts += it }
        val title = chosen.title?.takeIf { it.isNotBlank() }
            ?: chosen.displayTitle?.takeIf { it.isNotBlank() && parts.isEmpty() }
        title?.let { parts += it }
        return parts.joinToString(" · ").ifBlank { null }
    }

    fun delivery(source: MediaSourceInfoDto): String? {
        if (!source.transcodingUrl.isNullOrBlank()) return "转码"
        if (!source.protocol.isNullOrBlank() || !source.directStreamUrl.isNullOrBlank()) return "直接播放"
        return null
    }

    private fun sampleRate(hz: Int): String {
        return if (hz >= 1000) {
            val khz = hz / 1000.0
            val text = if (khz % 1.0 == 0.0) khz.roundToInt().toString() else String.format(Locale.US, "%.1f", khz)
            "$text kHz"
        } else {
            "$hz Hz"
        }
    }
}
