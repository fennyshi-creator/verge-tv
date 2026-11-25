package com.xxxx.emby_tv.ui.player

import com.xxxx.emby_tv.data.model.MediaStreamDto

/**
 * Emby text subtitles for HLS/strm are often not marked external and are not
 * inside the CDN playlist. Ask the server for Stream.srt / Stream.vtt instead.
 */
object EmbySubtitles {
    fun shouldDeliverAsText(track: MediaStreamDto?, routedIndices: Set<Int> = emptySet()): Boolean {
        if (track == null) return false
        if (track.type != null && !track.type.equals("Subtitle", ignoreCase = true)) return false
        val index = track.index
        if (track.isTextSubtitleStream == true || track.supportsExternalStream == true || track.isExternal == true)
            return true
        if (index != null && index in routedIndices) return true
        return isTextCodec(track.codec)
    }

    fun streamUrl(
        serverUrl: String,
        mediaId: String,
        mediaSourceId: String,
        apiKey: String,
        track: MediaStreamDto?,
        routedIndices: Set<Int> = emptySet(),
    ): String? {
        if (!shouldDeliverAsText(track, routedIndices)) return null
        val index = track?.index ?: return null
        if (mediaSourceId.isBlank() || mediaId.isBlank()) return null
        val format = if (track.codec?.contains("vtt", ignoreCase = true) == true) "vtt" else "srt"
        val base = serverUrl.trimEnd('/')
        return "$base/emby/Videos/$mediaId/$mediaSourceId/Subtitles/$index/Stream.$format?api_key=$apiKey"
    }

    private fun isTextCodec(codec: String?): Boolean {
        val name = codec?.lowercase().orEmpty()
        if (name.isBlank()) return false
        return name == "srt" ||
            name == "subrip" ||
            name == "ass" ||
            name == "ssa" ||
            name == "vtt" ||
            name == "webvtt" ||
            name == "text" ||
            name == "mov_text" ||
            name.contains("subrip") ||
            name.contains("srt")
    }
}

data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)

object SubtitleCues {
    fun parse(body: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val blocks = body.replace("\r\n", "\n").replace('\r', '\n').split("\n\n")
        for (block in blocks) {
            val lines = block.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            if (lines.isEmpty()) continue
            val timingIndex = lines.indexOfFirst { it.contains("-->") }
            if (timingIndex < 0) continue
            val timing = lines[timingIndex]
            val parts = timing.split("-->")
            if (parts.size < 2) continue
            val start = parseTimestamp(parts[0]) ?: continue
            val end = parseTimestamp(parts[1]) ?: continue
            val text = lines.drop(timingIndex + 1)
                .joinToString("\n") { stripTags(it) }
                .trim()
            if (text.isEmpty() || end <= start) continue
            cues += SubtitleCue(start, end, text)
        }
        return cues
    }

    fun textAt(cues: List<SubtitleCue>, positionMs: Long): String? {
        return cues.lastOrNull { positionMs in it.startMs until it.endMs }?.text
    }

    fun cueStartAt(cues: List<SubtitleCue>, positionMs: Long): Long? {
        return cues.lastOrNull { positionMs in it.startMs until it.endMs }?.startMs
    }

    private fun parseTimestamp(raw: String): Long? {
        val token = raw.trim().substringBefore(' ')
        val match = Regex("""(\d+):(\d+):(\d+)[,.](\d+)""").find(token) ?: return null
        val hours = match.groupValues[1].toLongOrNull() ?: return null
        val minutes = match.groupValues[2].toLongOrNull() ?: return null
        val seconds = match.groupValues[3].toLongOrNull() ?: return null
        val fraction = match.groupValues[4].padEnd(3, '0').take(3).toLongOrNull() ?: return null
        return ((hours * 60 + minutes) * 60 + seconds) * 1000 + fraction
    }

    private fun stripTags(line: String): String {
        return line.replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }
}
