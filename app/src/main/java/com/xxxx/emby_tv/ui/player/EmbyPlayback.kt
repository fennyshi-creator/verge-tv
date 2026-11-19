package com.xxxx.emby_tv.ui.player

import com.xxxx.emby_tv.data.model.MediaSourceInfoDto
import java.util.Locale

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
            reachable(source.directStreamUrl)?.let { return join(serverUrl, it) }
            val path = reachable(source.path)
            if (!path.isNullOrBlank() && isPublishedHls(source.container, path)) {
                return path
            }
        }
        val transcode = source.transcodingUrl?.takeIf { it.isNotBlank() } ?: return null
        return join(serverUrl, transcode)
    }

    /**
     * A strm gateway on the server is `http://127.0.0.1:3000/api/play?url=...`.
     * That host is the server itself. Opening it from the TV drops the player
     * as soon as the connection fails. Use the published target inside `url`.
     */
    fun reachable(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        if (raw.startsWith("/")) return raw
        val uri = try {
            java.net.URI(raw)
        } catch (_: Exception) {
            return raw
        }
        val host = uri.host?.lowercase(Locale.US) ?: return raw
        val loopback = host == "127.0.0.1" || host == "localhost" || host == "::1" || host == "0.0.0.0"
        if (!loopback) return raw
        val query = uri.rawQuery ?: return null
        val encoded = query.split('&')
            .firstOrNull { it.startsWith("url=") }
            ?.substringAfter("url=")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return try {
            java.net.URLDecoder.decode(encoded, Charsets.UTF_8.name())
        } catch (_: Exception) {
            encoded
        }
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
