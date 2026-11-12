package com.xxxx.emby_tv.ui.player

import com.xxxx.emby_tv.data.model.MediaSourceInfoDto

/**
 * PlaybackInfo should DirectPlay the original HLS/fMP4 when the server can,
 * and copy AC-3 / E-AC-3 if video has to be transcoded. AAC-only transcode
 * leaves nothing to bitstream.
 */
object EmbyPlayback {
    const val DIRECT_PLAY_CONTAINERS = "mp4,m4v,mkv,mov,hls,m3u8,ts,mpegts"
    const val HLS_TRANSCODE_AUDIO = "eac3,ac3,aac,mp3"

    fun ensureDolbyAudioCodecs(deviceAudioCodecs: String): String {
        val have = deviceAudioCodecs.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toMutableList()
        for (codec in listOf("eac3", "ac3")) {
            if (codec !in have) have.add(codec)
        }
        return have.joinToString(",")
    }

    fun resolvePlaybackUrl(
        serverUrl: String,
        source: MediaSourceInfoDto?,
        forceTranscode: Boolean,
    ): String? {
        if (source == null) return null
        if (!forceTranscode) {
            source.directStreamUrl?.takeIf { it.isNotBlank() }?.let { return join(serverUrl, it) }
            val path = source.path
            if (!path.isNullOrBlank() && isPublishedHls(source.container, path)) {
                return path
            }
        }
        val transcode = source.transcodingUrl?.takeIf { it.isNotBlank() } ?: return null
        return join(serverUrl, transcode)
    }

    private fun isPublishedHls(container: String?, path: String): Boolean {
        if (!(path.startsWith("http://") || path.startsWith("https://"))) return false
        val box = container?.lowercase().orEmpty()
        return box.contains("hls") || box.contains("m3u8") || path.contains(".m3u8", ignoreCase = true)
    }

    private fun join(serverUrl: String, path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val base = serverUrl.trimEnd('/')
        val relative = if (path.startsWith("/")) path else "/$path"
        return if (relative.startsWith("/emby/") || base.endsWith("/emby")) {
            base + relative
        } else {
            "$base/emby$relative"
        }
    }
}
