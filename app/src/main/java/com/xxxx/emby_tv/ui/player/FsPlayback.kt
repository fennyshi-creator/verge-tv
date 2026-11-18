package com.xxxx.emby_tv.ui.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import com.debugly.fsplayer.audio.FsPassthroughLog
import com.debugly.fsplayer.player.FSPlayer
import com.debugly.fsplayer.player.FSSubtitlePreference
import com.debugly.fsplayer.player.IMediaPlayer
import com.debugly.fsplayer.player.misc.FSTrackInfo
import com.debugly.fsplayer.player.misc.ITrackInfo
import com.xxxx.emby_tv.data.remote.HttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * FSPlayer wrapper used by the player screen. Video is MediaCodec. AC-3 / E-AC-3
 * audio is bitstream passthrough unless speed is not 1.0 or the endpoint rejects
 * the encoded AudioTrack.
 */
class FsPlayback(private val appContext: Context) {
    interface Listener {
        fun onPlayingChanged(playing: Boolean) {}
        fun onPlaybackStateChanged(state: Int) {}
        fun onError(message: String) {}
        fun onVideoDecoder(name: String) {}
    }

    companion object {
        const val STATE_IDLE = 1
        const val STATE_BUFFERING = 2
        const val STATE_READY = 3
        const val STATE_ENDED = 4
        private const val TAG = "FsPlayback"
    }

    private val listeners = CopyOnWriteArrayList<Listener>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val subtitleIo = Executors.newSingleThreadExecutor()
    private var engine: FSPlayer? = null
    private var surface: Surface? = null
    private var released = false

    var url: String? = null
        private set
    var headers: Map<String, String> = emptyMap()
        private set
    var passthroughEnabled: Boolean = true
    var mapHdrToSdr: Boolean = false
    var jocHint: Boolean = false
        private set
    var playWhenReady: Boolean = true
    var playbackState: Int = STATE_IDLE
        private set
    var playbackSpeed: Float = 1f
        private set
    private var startMs: Long = 0
    private var audioOrdinal: Int = -1
    private var subtitleOrdinal: Int = -1
    private var externalSubtitleUrl: String? = null
    private var localSubtitlePath: String? = null
    private var subtitleGeneration = 0
    @Volatile private var subtitleCues: List<SubtitleCue> = emptyList()

    val isPlaying: Boolean
        get() = try {
            engine?.isPlaying == true
        } catch (_: Exception) {
            false
        }

    val currentPosition: Long
        get() = try {
            (engine?.currentPosition ?: 0L).coerceAtLeast(0L)
        } catch (_: Exception) {
            0L
        }

    val duration: Long
        get() = try {
            (engine?.duration ?: 0L).coerceAtLeast(0L)
        } catch (_: Exception) {
            0L
        }

    val bufferedPosition: Long
        get() = try {
            val playable = engine?.playableDuration ?: 0L
            if (playable > 0L) playable else currentPosition
        } catch (_: Exception) {
            currentPosition
        }

    val currentMediaItem: String?
        get() = url

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun setVideoSurface(surface: Surface?) {
        this.surface = surface
        try {
            engine?.setSurface(surface)
            engine?.setAllowHDRDirectDisplay(!mapHdrToSdr)
        } catch (error: Exception) {
            Log.w(TAG, "setSurface failed", error)
        }
    }

    fun open(
        url: String,
        headers: Map<String, String>,
        startMs: Long,
        passthrough: Boolean,
        joc: Boolean,
        speed: Float = playbackSpeed,
    ) {
        if (released) return
        this.url = url
        this.headers = headers
        this.startMs = startMs
        this.playbackSpeed = speed
        this.jocHint = joc
        this.passthroughEnabled = passthrough && speed == 1f
        swapEngine()
    }

    fun setPlaybackSpeed(speed: Float) {
        val previous = playbackSpeed
        playbackSpeed = speed
        val crossesOne = (previous == 1f) != (speed == 1f)
        val currentUrl = url
        if (crossesOne && currentUrl != null) {
            Log.i(TAG, "speed ${previous} -> $speed reopens audio (passthrough only at 1.0)")
            open(currentUrl, headers, currentPosition, passthrough = speed == 1f, joc = jocHint)
            return
        }
        try {
            engine?.setSpeed(speed)
        } catch (error: Exception) {
            Log.w(TAG, "setSpeed failed", error)
        }
    }

    fun forcePcm(resumeMs: Long) {
        val currentUrl = url ?: return
        passthroughEnabled = false
        FsPassthroughLog.note("audio format switch to pcm reason=player-reopen")
        open(currentUrl, headers, resumeMs, passthrough = false, joc = false)
    }

    fun setSubtitleDelayMs(delayMs: Long) {
        try {
            engine?.setSubtitleExtraDelay(delayMs / 1000f)
        } catch (error: Exception) {
            Log.w(TAG, "subtitle delay failed", error)
        }
    }

    fun setVolume(left: Float, right: Float) {
        try {
            engine?.setVolume(left, right)
        } catch (error: Exception) {
            Log.w(TAG, "setVolume failed", error)
        }
    }

    fun play() {
        playWhenReady = true
        try {
            val current = engine ?: return
            current.start()
            dispatchPlaying(true)
        } catch (error: Exception) {
            Log.w(TAG, "start failed", error)
        }
    }

    fun pause() {
        playWhenReady = false
        try {
            engine?.pause()
        } catch (error: Exception) {
            Log.w(TAG, "pause failed", error)
        }
        dispatchPlaying(false)
    }

    fun stop() {
        playWhenReady = false
        try {
            engine?.stop()
        } catch (error: Exception) {
            Log.w(TAG, "stop failed", error)
        }
        setState(STATE_IDLE)
    }

    fun seekTo(positionMs: Long) {
        try {
            engine?.seekTo(positionMs.coerceAtLeast(0L))
        } catch (error: Exception) {
            Log.w(TAG, "seek failed", error)
        }
    }

    fun seekBack() {
        seekTo(currentPosition - 10_000L)
    }

    fun seekForward() {
        val end = duration
        val target = currentPosition + 10_000L
        seekTo(if (end > 0L) target.coerceAtMost(end) else target)
    }

    fun prepare() {
        // open() already calls prepareAsync.
    }

    fun selectEmbyTracks(audioOrdinal: Int, subtitleOrdinal: Int, externalSubtitleUrl: String?) {
        this.audioOrdinal = audioOrdinal
        this.subtitleOrdinal = subtitleOrdinal
        val sameSubtitle = this.externalSubtitleUrl == externalSubtitleUrl
        this.externalSubtitleUrl = externalSubtitleUrl
        val generation = ++subtitleGeneration
        if (externalSubtitleUrl.isNullOrBlank()) {
            localSubtitlePath = null
            subtitleCues = emptyList()
            mainHandler.post { if (generation == subtitleGeneration) applyTracks(null) }
            return
        }
        if (sameSubtitle && (subtitleCues.isNotEmpty() || localSubtitlePath != null)) {
            mainHandler.post { if (generation == subtitleGeneration) applyTracks(localSubtitlePath) }
            return
        }
        subtitleIo.execute {
            var local: String? = null
            var cues = emptyList<SubtitleCue>()
            try {
                val file = downloadSubtitle(externalSubtitleUrl)
                if (file != null) {
                    local = file.absolutePath
                    cues = SubtitleCues.parse(file.readText())
                }
            } catch (error: Exception) {
                Log.w(TAG, "subtitle download failed", error)
            }
            if (generation != subtitleGeneration) return@execute
            localSubtitlePath = local
            subtitleCues = cues
            Log.i(TAG, "subtitle cues=${cues.size} local=${local != null}")
            mainHandler.post {
                if (generation == subtitleGeneration) applyTracks(local)
            }
        }
    }

    fun subtitleTextAt(positionMs: Long): String? = SubtitleCues.textAt(subtitleCues, positionMs)

    fun passthroughEncoding(): Int {
        return try {
            engine?.getPropertyLong(FSPlayer.FFP_PROP_INT64_AUDIO_PASSTHROUGH_ENCODING, 0L)?.toInt() ?: 0
        } catch (_: Exception) {
            0
        }
    }

    fun passthroughFallback(): Boolean {
        return try {
            (engine?.getPropertyLong(FSPlayer.FFP_PROP_INT64_AUDIO_PASSTHROUGH_FALLBACK, 0L) ?: 0L) != 0L
        } catch (_: Exception) {
            false
        }
    }

    fun tcpSpeed(): Long {
        return try {
            engine?.getPropertyLong(FSPlayer.FFP_PROP_INT64_TCP_SPEED, 0L) ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    fun release() {
        released = true
        val current = engine
        engine = null
        try {
            current?.setSurface(null)
            current?.stop()
            current?.release()
        } catch (error: Exception) {
            Log.w(TAG, "release failed", error)
        }
    }

    private fun swapEngine() {
        val previous = engine
        engine = null
        try {
            previous?.setSurface(null)
            previous?.release()
        } catch (error: Exception) {
            Log.w(TAG, "release previous player failed", error)
        }
        val next = try {
            FSPlayer()
        } catch (error: UnsatisfiedLinkError) {
            Log.e(TAG, "libfsplayer.so failed to load", error)
            dispatchError(error.message ?: "libfsplayer.so")
            return
        }
        engine = next
        configure(next)
        wire(next)
        try {
            next.setSurface(surface)
            val playUrl = url ?: return
            if (headers.isEmpty()) next.setDataSource(playUrl) else next.setDataSource(playUrl, headers)
            setState(STATE_BUFFERING)
            next.prepareAsync()
        } catch (error: Exception) {
            Log.e(TAG, "prepare failed", error)
            dispatchError(error.message ?: "prepare")
        }
    }

    private fun configure(player: FSPlayer) {
        val pass = if (passthroughEnabled) 1L else 0L
        // HDR-SDR On, or an SDR display playing HDR, leaves MediaCodec so the
        // Vulkan shader actually tone-maps. Off on an HDR display stays on
        // the hardware path.
        val videoHardware = if (mapHdrToSdr) 0L else 1L
        player.setOption(FSPlayer.OPT_CATEGORY_PLAYER, "mediacodec-all-videos", videoHardware)
        player.setOption(FSPlayer.OPT_CATEGORY_PLAYER, "mediacodec-avc", videoHardware)
        player.setOption(FSPlayer.OPT_CATEGORY_PLAYER, "mediacodec-hevc", videoHardware)
        player.setOption(FSPlayer.OPT_CATEGORY_PLAYER, "mediacodec-mpeg2", videoHardware)
        player.setOption(FSPlayer.OPT_CATEGORY_PLAYER, "mediacodec-mpeg4", videoHardware)
        player.setOption(FSPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1)
        player.setOption(FSPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1)
        player.setOption(FSPlayer.OPT_CATEGORY_PLAYER, "opensles", 0)
        player.setOption(FSPlayer.OPT_CATEGORY_PLAYER, "soundtouch", 0)
        player.setOption(FSPlayer.OPT_CATEGORY_PLAYER, "audio-passthrough", pass)
        player.setOption(FSPlayer.OPT_CATEGORY_PLAYER, "mediacodec-audio-passthrough", pass)
        player.setOption(FSPlayer.OPT_CATEGORY_PLAYER, "audio-passthrough-joc", if (jocHint && pass == 1L) 1L else 0L)
        player.setOption(FSPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1)
        player.setOption(FSPlayer.OPT_CATEGORY_FORMAT, "protocol_whitelist",
            "async,cache,crypto,file,http,https,tcp,tls,udp,data")
        player.setOption(FSPlayer.OPT_CATEGORY_FORMAT, "allowed_extensions", "ALL")
        player.setOption(FSPlayer.OPT_CATEGORY_FORMAT, "probesize", 1_000_000)
        player.setOption(FSPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 2_000_000)
        player.setOption(FSPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48)
        headers["User-Agent"]?.let { player.setOption(FSPlayer.OPT_CATEGORY_FORMAT, "user_agent", it) }
        if (playbackSpeed != 1f) player.setSpeed(playbackSpeed)
        try {
            player.setAllowHDRDirectDisplay(!mapHdrToSdr)
        } catch (error: Exception) {
            Log.w(TAG, "hdr display flag failed", error)
        }
    }

    private fun wire(player: FSPlayer) {
        player.setOnPreparedListener {
            if (engine !== player) return@setOnPreparedListener
            val mediaDuration = try {
                player.duration
            } catch (_: Exception) {
                0L
            }
            // A resume point past the real file (stale Emby runtime, or a
            // short HLS probe) seeks off the end: one frame, then the player
            // dies. Stay at the start instead.
            val resume = if (mediaDuration > 0L && startMs >= mediaDuration - 1_500L) 0L else startMs
            if (resume > 0L) player.seekTo(resume)
            setState(STATE_READY)
            dispatchVideoDecoder("mediacodec")
            applyTracks(localSubtitlePath)
            if (playWhenReady) {
                player.start()
                dispatchPlaying(true)
            }
        }
        player.setOnCompletionListener {
            if (engine !== player) return@setOnCompletionListener
            dispatchPlaying(false)
            setState(STATE_ENDED)
        }
        player.setOnErrorListener { _, what, extra ->
            if (engine !== player) return@setOnErrorListener true
            Log.e(TAG, "FSPlayer error what=$what extra=$extra")
            dispatchPlaying(false)
            setState(STATE_IDLE)
            dispatchError("FSPlayer $what/$extra")
            true
        }
        player.setOnInfoListener { _, what, _ ->
            if (engine !== player) return@setOnInfoListener true
            when (what) {
                IMediaPlayer.MEDIA_INFO_BUFFERING_START -> setState(STATE_BUFFERING)
                IMediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                    setState(STATE_READY)
                    dispatchPlaying(player.isPlaying)
                }
                IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> {
                    setState(STATE_READY)
                    dispatchPlaying(player.isPlaying)
                    dispatchVideoDecoder("mediacodec")
                }
                IMediaPlayer.MEDIA_INFO_AUDIO_RENDERING_START -> {
                    Log.i(
                        TAG,
                        "audio rendering encoding=${passthroughEncoding()} fallback=${passthroughFallback()}"
                    )
                }
                IMediaPlayer.MEDIA_INFO_VIDEO_DECODER_OPEN -> dispatchVideoDecoder("mediacodec")
            }
            true
        }
    }

    private fun applyTracks(subtitlePath: String?) {
        val player = engine ?: return
        val tracks = try {
            player.trackInfo?.filterIsInstance<FSTrackInfo>().orEmpty()
        } catch (error: Exception) {
            Log.w(TAG, "trackInfo failed", error)
            emptyList()
        }
        val audio = tracks.filter { it.trackType == ITrackInfo.MEDIA_TRACK_TYPE_AUDIO }
        if (audioOrdinal in audio.indices) {
            val index = audio[audioOrdinal].streamIndex
            if (index >= 0) player.selectTrack(index)
        }
        val requested = externalSubtitleUrl
        if (requested.isNullOrBlank()) {
            player.setOption(FSPlayer.OPT_CATEGORY_PLAYER, "subtitle_mix", 1)
            if (subtitleOrdinal < 0) {
                try {
                    player.deselectTrack(-1)
                } catch (error: Exception) {
                    Log.w(TAG, "subtitle off failed", error)
                }
                return
            }
            val embedded = tracks.filter { it.trackType == ITrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT }
            if (subtitleOrdinal in embedded.indices) {
                val index = embedded[subtitleOrdinal].streamIndex
                if (index >= 0) player.selectTrack(index)
            }
            return
        }
        val path = subtitlePath?.takeIf { it.isNotBlank() } ?: requested
        var loaded = false
        try {
            loaded = player.loadThenActiveSubtitle(path)
        } catch (error: Exception) {
            Log.w(TAG, "loadThenActiveSubtitle failed for $path", error)
        }
        if (!loaded && path != requested) {
            try {
                loaded = player.loadThenActiveSubtitle(requested)
            } catch (error: Exception) {
                Log.w(TAG, "loadThenActiveSubtitle http retry failed", error)
            }
        }
        Log.i(TAG, "subtitle loaded=$loaded path=$path cues=${subtitleCues.size}")
        applySubtitleFont(player)
        // Parsed text is drawn on the Android overlay. Leave GPU mix on only when
        // we have nothing to draw ourselves (embedded bitmap / failed download).
        val mix = if (subtitleCues.isEmpty()) 1L else 0L
        player.setOption(FSPlayer.OPT_CATEGORY_PLAYER, "subtitle_mix", mix)
        if (subtitleCues.isNotEmpty()) return
        val subs = tracks.filter { it.trackType == ITrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT }
        if (subtitleOrdinal < 0) {
            subs.forEach { track ->
                val index = track.streamIndex
                if (index >= 0) player.deselectTrack(index)
            }
        } else if (subtitleOrdinal in subs.indices) {
            val index = subs[subtitleOrdinal].streamIndex
            if (index >= 0) player.selectTrack(index)
        }
    }

    private fun downloadSubtitle(url: String): File? {
        val dir = File(appContext.cacheDir, "subtitles").apply { mkdirs() }
        val extension = if (url.contains("Stream.vtt", ignoreCase = true)) "vtt" else "srt"
        val file = File(dir, "${url.hashCode()}.$extension")
        if (file.length() > 16) return file
        val request = Request.Builder().url(url).apply {
            headers.forEach { (name, value) -> header(name, value) }
        }.build()
        HttpClient.getClient(appContext).newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "subtitle http ${response.code} $url")
                return null
            }
            val body = response.body.string()
            if (body.isBlank()) return null
            file.writeText(body)
            return file
        }
    }

    private fun applySubtitleFont(player: FSPlayer) {
        val fontsDir = ensureSubtitleFontDir() ?: return
        val preference = FSSubtitlePreference()
        preference.ForceOverride = 1
        preference.FontName = "Noto Sans SC"
        preference.FontsDir = fontsDir
        preference.PrimaryColour = 0xFFFFFF00.toInt()
        preference.OutlineColour = 0x00000000
        preference.Outline = 1.2f
        preference.BackColour = 0x00000080
        try {
            player.setSubtitlePreference(preference)
        } catch (error: Exception) {
            Log.w(TAG, "subtitle preference failed", error)
        }
    }

    private fun ensureSubtitleFontDir(): String? {
        return try {
            val dir = File(appContext.cacheDir, "subtitle-fonts").apply { mkdirs() }
            val out = File(dir, "NotoSansSC-Regular.otf")
            if (!out.exists() || out.length() < 1024) {
                appContext.assets.open("fonts/NotoSansSC-Regular.otf").use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
            }
            dir.absolutePath
        } catch (error: Exception) {
            Log.w(TAG, "subtitle font unavailable", error)
            null
        }
    }

    private fun setState(state: Int) {
        if (playbackState == state) return
        playbackState = state
        listeners.forEach { it.onPlaybackStateChanged(state) }
    }

    private fun dispatchPlaying(playing: Boolean) {
        listeners.forEach { it.onPlayingChanged(playing) }
    }

    private fun dispatchError(message: String) {
        listeners.forEach { it.onError(message) }
    }

    private fun dispatchVideoDecoder(name: String) {
        listeners.forEach { it.onVideoDecoder(name) }
    }
}
