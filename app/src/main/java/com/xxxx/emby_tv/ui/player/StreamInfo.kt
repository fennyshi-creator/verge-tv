package com.xxxx.emby_tv.ui.player

import com.xxxx.emby_tv.data.model.MediaSourceInfoDto
import com.xxxx.emby_tv.data.model.MediaStreamDto
import com.xxxx.emby_tv.util.EmbyPresentation

data class StreamInfoLine(val label: String, val value: String)

/**
 * Facts taken from the source that is actually playing. A missing field is
 * left out instead of filled with a guess.
 */
object StreamInfo {
    fun lines(source: MediaSourceInfoDto?, audioIndex: Int): List<StreamInfoLine> {
        if (source == null) return emptyList()
        val video = source.mediaStreams.orEmpty().firstOrNull { it.type.equals("Video", true) }
        val lines = mutableListOf<StreamInfoLine>()
        resolution(video, source)?.let { lines += StreamInfoLine("分辨率", it) }
        color(video)?.let { lines += StreamInfoLine("色彩", it) }
        audio(source, audioIndex)?.let { lines += StreamInfoLine("音频", it) }
        return lines
    }

    fun resolution(video: MediaStreamDto?, source: MediaSourceInfoDto): String? {
        val width = video?.width
        val height = video?.height
        if (width != null && height != null && width > 0 && height > 0) {
            return "${width}×${height}"
        }
        return EmbyPresentation.resolutionChip(source)
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
        val space = video.colorSpace?.takeIf { it.isNotBlank() }
        return listOfNotNull(head, space).joinToString(" · ").ifBlank { null }
    }

    fun audio(source: MediaSourceInfoDto, audioIndex: Int): String? {
        val tracks = source.mediaStreams.orEmpty().filter { it.type.equals("Audio", true) }
        if (tracks.isEmpty()) return null
        val chosen = tracks.firstOrNull { audioIndex >= 0 && it.index == audioIndex }
            ?: tracks.firstOrNull { it.isDefault == true }
            ?: tracks.first()
        val title = chosen.displayTitle?.takeIf { it.isNotBlank() }
        if (title != null) return title
        return EmbyPresentation.audioChip(listOf(chosen))
    }
}
