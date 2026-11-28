package com.xxxx.emby_tv.ui.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.os.Build
import com.debugly.fsplayer.audio.FsPassthroughLog
import com.debugly.fsplayer.player.FSPlayer
import com.xxxx.emby_tv.data.remote.ClientLogText
import com.debugly.fsplayer.player.FSSubtitlePreference
import com.debugly.fsplayer.player.IMediaPlayer
import com.debugly.fsplayer.player.misc.FSTrackInfo
import com.debugly.fsplayer.player.misc.ITrackInfo
import com.xxxx.emby_tv.data.remote.HttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * FSPlayer wrapper used by the player screen. Video is MediaCodec. AC-3 / E-AC-3
 * audio is bitstream passthrough unless speed is not 1.0 or the endpoint rejects
 * the encoded AudioTrack.
 */
private data class AudioReleaseWait(val idle: Boolean, val waitedMs: Long)

class FsPlayback(private val appContext: Context) {
    interface Listener {
        fun onPlayingChanged(playing: Boolean) {}
        fun onPlaybackStateChanged(state: Int) {}
        fun onError(message: String) {}
        fun onVideoDecoder(name: String) {}
        fun onVideoGeometry(
            width: Int,
            height: Int,
            sarNum: Int,
            sarDen: Int,
            rotation: Int,
            scaleMode: Int,
        ) {}
    }

    companion object {
        const val STATE_IDLE = 1
        const val STATE_BUFFERING = 2
        const val STATE_READY = 3
        const val STATE_ENDED = 4
        private const val TAG = "FsPlayback"
        private const val ENGINE_THREAD = "verge-fs-cmd"
        /**
         * How long the engine thread waits for the previous audio device to
         * finish closing before it refuses the next open. The main thread
         * never waits. 1.8s was short enough that a slow release showed
         * "previous audio still closing" and skipped the next title.
         */
        const val BACKGROUND_RELEASE_WAIT_MS = 12_000L
        const val STALL_REOPEN_LIMIT = 1

        /** The main thread must never wait on `ffp_wait_stop_l`. */
        fun joinsNativeRelease(onMainThread: Boolean): Boolean = !onMainThread

        const val AUDIO_RELEASE = "audio-release"
    }

    private val listeners = CopyOnWriteArrayList<Listener>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val subtitleIo = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "verge-subtitle-io").apply { isDaemon = true }
    }
    private val commands = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, ENGINE_THREAD).apply { isDaemon = true }
    }
    @Volatile private var engine: FSPlayer? = null
    @Volatile private var surface: Surface? = null
    @Volatile private var released = false
    @Volatile private var seekInFlight = false
    @Volatile private var subtitleResolved = true
    private val gate = Any()

    val isReleased: Boolean get() = released

    var url: String? = null
        private set
    var headers: Map<String, String> = emptyMap()
        private set
    var passthroughEnabled: Boolean = true
    /** Saved 音频直通 switch: true is 自动. Not cleared by a temporary speed reopen. */
    var passthroughRequested: Boolean = true
    /** Compatible decode. The next open asks native for 2-channel PCM. */
    var downmixStereo: Boolean = false
    var mapHdrToSdr: Boolean = false
    /** Vendor GLES path only. HDR on that path, or one video-decode retry. */
    var preferSoftwareVideo: Boolean = false
        private set
    /** GLES devices send HDR MediaCodec to the window surface instead of the SDR OES texture. */
    var hdrWindowOutput: Boolean = false
    /** Decode multichannel AAC and re-encode AC-3. Never set for AC-3 or E-AC-3 sources. */
    var aacAc3: Boolean = false
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
        if (released) return
        this.surface = surface
        onEngine {
            try {
                engine?.setSurface(surface)
                engine?.setAllowHDRDirectDisplay(!mapHdrToSdr)
            } catch (error: Exception) {
                Log.w(TAG, "setSurface failed", error)
            }
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
        onEngine {
            val wait = awaitPreviousAudio()
            if (!wait.idle) {
                noteEngine("audio release still busy after ${wait.waitedMs}ms; refusing overlapping open")
                dispatchError("${AUDIO_RELEASE}: previous output still closing waitedMs=${wait.waitedMs}")
                return@onEngine
            }
            openOnEngine(url, headers, startMs, passthrough, joc, speed)
        }
    }

    private fun openOnEngine(
        url: String,
        headers: Map<String, String>,
        startMs: Long,
        passthrough: Boolean,
        joc: Boolean,
        speed: Float,
    ) {
        if (released) return
        this.url = url
        this.headers = headers
        this.startMs = startMs
        this.playbackSpeed = speed
        this.jocHint = joc && !downmixStereo
        this.passthroughEnabled = passthrough && speed == 1f && !downmixStereo
        noteEngine(
            "playback-open softwareVideo=$preferSoftwareVideo passthrough=${this.passthroughEnabled} " +
                deviceSnapshot()
        )
        swapEngine()
    }

    /**
     * HDR on the Dune GLES output, or the one video-decode retry.
     * Does not change passthrough.
     */
    fun useSoftwareVideo(enabled: Boolean) {
        preferSoftwareVideo = enabled
    }

    /** Reopen the current item after playback stops advancing. */
    fun reopen(atMs: Long) {
        val current = url ?: return
        open(current, headers, atMs, passthroughEnabled, jocHint, playbackSpeed)
    }

    fun setPlaybackSpeed(speed: Float) {
        val previous = playbackSpeed
        playbackSpeed = speed
        val crossesOne = (previous == 1f) != (speed == 1f)
        val currentUrl = url
        if (crossesOne && currentUrl != null) {
            Log.i(TAG, "speed ${previous} -> $speed reopens audio (passthrough only at 1.0)")
            open(
                currentUrl,
                headers,
                currentPosition,
                passthrough = passthroughRequested && !downmixStereo,
                joc = jocHint && !downmixStereo,
            )
            return
        }
        onEngine {
            try {
                engine?.setSpeed(speed)
            } catch (error: Exception) {
                Log.w(TAG, "setSpeed failed", error)
            }
        }
    }

    fun forcePcm(resumeMs: Long) {
        val currentUrl = url ?: return
        passthroughRequested = false
        FsPassthroughLog.note("audio format switch to pcm reason=player-reopen")
        open(currentUrl, headers, resumeMs, passthrough = false, joc = false)
    }

    fun setSubtitleDelayMs(delayMs: Long) {
        onEngine {
            try {
                engine?.setSubtitleExtraDelay(delayMs / 1000f)
            } catch (error: Exception) {
                Log.w(TAG, "subtitle delay failed", error)
            }
        }
    }

    fun setVolume(left: Float, right: Float) {
        onEngine {
            try {
                engine?.setVolume(left, right)
            } catch (error: Exception) {
                Log.w(TAG, "setVolume failed", error)
            }
        }
    }

    fun play() {
        playWhenReady = true
        onEngine {
            if (released) return@onEngine
            try {
                val current = engine ?: return@onEngine
                current.start()
                // Do not claim playback started until the engine says it is playing.
                dispatchPlaying(current.isPlaying)
            } catch (error: Exception) {
                Log.w(TAG, "start failed", error)
            }
        }
    }

    fun pause() {
        playWhenReady = false
        onEngine {
            try {
                engine?.pause()
            } catch (error: Exception) {
                Log.w(TAG, "pause failed", error)
            }
            dispatchPlaying(false)
        }
    }

    fun stop() {
        playWhenReady = false
        onEngine {
            try {
                engine?.stop()
            } catch (error: Exception) {
                Log.w(TAG, "stop failed", error)
            }
            setState(STATE_IDLE)
        }
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceAtLeast(0L)
        onEngine {
            if (released) return@onEngine
            seekInFlight = true
            setState(STATE_BUFFERING)
            try {
                engine?.seekTo(target)
            } catch (error: Exception) {
                seekInFlight = false
                Log.w(TAG, "seek failed", error)
            }
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
            subtitleResolved = true
            onEngine { if (generation == subtitleGeneration) applyTracks(null) }
            return
        }
        if (sameSubtitle && subtitleResolved) {
            onEngine { if (generation == subtitleGeneration) applyTracks(localSubtitlePath) }
            return
        }
        subtitleResolved = false
        subtitleCues = emptyList()
        onEngine { if (generation == subtitleGeneration) applyTracks(null) }
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
            subtitleResolved = true
            Log.i(TAG, "subtitle cues=${cues.size} local=${local != null}")
            onEngine {
                if (generation == subtitleGeneration) applyTracks(local)
            }
        }
    }

    fun subtitleTextAt(positionMs: Long): String? = SubtitleCues.textAt(subtitleCues, positionMs)

    fun subtitleCueStartAt(positionMs: Long): Long? = SubtitleCues.cueStartAt(subtitleCues, positionMs)

    fun passthroughEncoding(): Int {
        return try {
            engine?.getPropertyLong(FSPlayer.FFP_PROP_INT64_AUDIO_PASSTHROUGH_ENCODING, 0L)?.toInt() ?: 0
        } catch (_: Exception) {
            0
        }
    }

    fun clientAc3(): Boolean {
        return try {
            (engine?.getPropertyLong(FSPlayer.FFP_PROP_INT64_AUDIO_CLIENT_AC3, 0L) ?: 0L) == 3L
        } catch (_: Exception) {
            false
        }
    }

    fun passthroughFallback(): Boolean {
        return try {
            (engine?.getPropertyLong(FSPlayer.FFP_PROP_INT64_AUDIO_PASSTHROUGH_FALLBACK, 0L) ?: 0L) != 0L
        } catch (_: Exception) {
            false
        }
    }

    /** 0 until a build exposes the counter. A moving count means video is still on screen. */
    fun displayedFrameCount(): Long {
        return try {
            engine?.getPropertyLong(FSPlayer.FFP_PROP_INT64_VIDEO_DISPLAYED_FRAMES, 0L) ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Negative holds the picture back. Callers pass 0 on the PCM path.
     * Does not change how the bitstream is opened or packed.
     */
    fun setAudioExtraDelaySeconds(seconds: Float) {
        onEngine {
            try {
                engine?.setAudioExtraDelay(seconds)
            } catch (error: Exception) {
                Log.w(TAG, "audio extra delay failed", error)
            }
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
        synchronized(gate) {
            if (released) return
            released = true
            try {
                commands.execute { detachAndStop() }
            } catch (error: RejectedExecutionException) {
                Log.w(TAG, "release queue closed", error)
                detachNative(engine)
                engine = null
            }
        }
    }

    private fun detachAndStop() {
        val current = engine
        engine = null
        surface = null
        detachNative(current)
        subtitleIo.shutdownNow()
        commands.shutdown()
    }

    private fun swapEngine() {
        if (released) return
        val previous = engine
        engine = null
        val releasedAudio = detachNative(previous)
        if (!releasedAudio.idle) {
            dispatchError("${AUDIO_RELEASE}: previous output still closing waitedMs=${releasedAudio.waitedMs}")
            return
        }
        if (released) return
        val next = try {
            FSPlayer()
        } catch (error: UnsatisfiedLinkError) {
            Log.e(TAG, "libfsplayer.so failed to load", error)
            noteEngine("fatal engine-create stage=native-setup UnsatisfiedLinkError: ${error.stackTraceToString()}")
            dispatchError("${PlaybackErrorPolicy.ENGINE_CREATE}: ${error.message ?: "libfsplayer.so"}")
            return
        } catch (error: Error) {
            Log.e(TAG, "engine create failed", error)
            noteEngine("fatal engine-create stage=native-setup ${error.javaClass.name}: ${error.stackTraceToString()}")
            dispatchError("${PlaybackErrorPolicy.ENGINE_CREATE}: ${error.message ?: error.javaClass.simpleName}")
            return
        }
        if (released) {
            detachNative(next)
            return
        }
        engine = next
        configure(next)
        if (released || engine !== next) return
        wire(next)
        if (released || engine !== next) return
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

    /**
     * Native release joins demux and refresh threads. That join never runs
     * on the main looper. The engine thread waits until the device is idle,
     * and only gives up after [BACKGROUND_RELEASE_WAIT_MS]. The next engine
     * is not created while this one is still closing, so two encoded tracks
     * cannot be open together. Surface clear stays before [FSPlayer.release].
     */
    private fun detachNative(player: FSPlayer?): AudioReleaseWait {
        if (player == null) return AudioReleaseWait(idle = true, waitedMs = 0L)
        noteEngine("audio release begin")
        AudioDeviceGate.begin {
            try {
                player.setSurface(null)
            } catch (error: Exception) {
                Log.w(TAG, "detach surface failed", error)
            }
            try {
                player.release()
            } catch (error: Exception) {
                Log.w(TAG, "release failed", error)
            }
            noteEngine("audio release worker done")
        }
        val onMain = Looper.myLooper() == Looper.getMainLooper()
        if (!joinsNativeRelease(onMain))
            return AudioReleaseWait(idle = false, waitedMs = 0L)
        val wait = awaitPreviousAudio()
        if (!wait.idle)
            noteEngine("audio release still busy after ${wait.waitedMs}ms")
        else
            noteEngine("audio release done after ${wait.waitedMs}ms")
        return wait
    }

    /** Blocks the caller. [open] only calls this from the engine thread. */
    private fun awaitPreviousAudio(): AudioReleaseWait {
        val started = System.nanoTime()
        val idle = AudioDeviceGate.awaitIdle(BACKGROUND_RELEASE_WAIT_MS)
        val waitedMs = ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(0L)
        if (!idle)
            AudioDeviceGate.forceIdle()
        return AudioReleaseWait(idle, waitedMs)
    }

    private fun onEngine(block: () -> Unit) {
        if (Thread.currentThread().name == ENGINE_THREAD) {
            if (released) return
            try {
                block()
            } catch (error: Exception) {
                Log.w(TAG, "engine command failed", error)
            }
            return
        }
        synchronized(gate) {
            if (released) return
            try {
                commands.execute {
                    if (released) return@execute
                    try {
                        block()
                    } catch (error: Exception) {
                        Log.w(TAG, "engine command failed", error)
                    }
                }
            } catch (error: RejectedExecutionException) {
                Log.w(TAG, "engine command dropped", error)
            }
        }
    }

    private fun configure(player: FSPlayer) {
        val pass = if (passthroughEnabled && !downmixStereo) 1L else 0L
        // Shader fallback only. Hardware HDR-to-SDR stays on MediaCodec and
        // the configure hook sends color-transfer-request.
        val videoHardware = if (mapHdrToSdr || preferSoftwareVideo) 0L else 1L
        noteEngine(
            "video-decoder mediacodec=$videoHardware hdrShader=$mapHdrToSdr software=$preferSoftwareVideo " +
                "hdrWindow=$hdrWindowOutput aacAc3=$aacAc3"
        )
        player.setOption(
            FSPlayer.OPT_CATEGORY_PLAYER,
            "hdr-window-output",
            if (hdrWindowOutput) 1L else 0L,
        )
        player.setOption(
            FSPlayer.OPT_CATEGORY_PLAYER,
            "aac-ac3",
            if (aacAc3 && !downmixStereo) 1L else 0L,
        )
        player.setOption(
            FSPlayer.OPT_CATEGORY_PLAYER,
            "pcm-downmix-stereo",
            if (downmixStereo) 1L else 0L,
        )
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
        // Kernel subtitles stay off until applyTracks chooses that renderer.
        player.setOption(FSPlayer.OPT_CATEGORY_PLAYER, "subtitle", 1)
        player.setOption(FSPlayer.OPT_CATEGORY_PLAYER, "subtitle_mix", 0)
        try {
            player.setAllowHDRDirectDisplay(!mapHdrToSdr)
        } catch (error: Exception) {
            Log.w(TAG, "hdr display flag failed", error)
        }
    }

    private fun wire(player: FSPlayer) {
        player.setOnVideoSizeChangedListener { _, width, height, sarNum, sarDen ->
            dispatch {
                it.onVideoGeometry(
                    width,
                    height,
                    sarNum,
                    sarDen,
                    player.zRotateDegrees.toInt(),
                    player.scalingMode,
                )
            }
        }
        player.setOnPreparedListener {
            onEngine {
                if (released || engine !== player) return@onEngine
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
                    dispatchPlaying(player.isPlaying)
                }
            }
        }
        player.setOnCompletionListener {
            onEngine {
                if (released || engine !== player) return@onEngine
                dispatchPlaying(false)
                setState(STATE_ENDED)
            }
        }
        player.setOnErrorListener { _, what, extra ->
            onEngine {
                if (released || engine !== player) return@onEngine
                Log.e(TAG, "FSPlayer error what=$what extra=$extra")
                dispatchPlaying(false)
                setState(STATE_IDLE)
                val message = when (extra) {
                    11011 -> "video-decode: mediacodec-hdr"
                    11012 -> "audio-passthrough: iec-unavailable"
                    else -> "FSPlayer $what/$extra"
                }
                dispatchError(message)
            }
            true
        }
        player.setOnInfoListener { _, what, _ ->
            onEngine {
                if (released || engine !== player) return@onEngine
                when (what) {
                    IMediaPlayer.MEDIA_INFO_BUFFERING_START -> setState(STATE_BUFFERING)
                    IMediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                        seekInFlight = false
                        setState(STATE_READY)
                        dispatchPlaying(player.isPlaying)
                    }
                    IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> {
                        seekInFlight = false
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
            }
            true
        }
        player.setOnSeekCompleteListener {
            onEngine {
                if (released || engine !== player) return@onEngine
                seekInFlight = false
                setState(STATE_READY)
                dispatchPlaying(player.isPlaying)
            }
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
        val cuesReady = subtitleCues.isNotEmpty()
        val kernel = SubtitleRoute.allowKernel(
            hasTextCues = cuesReady,
            externalRequested = !externalSubtitleUrl.isNullOrBlank(),
            downloadSettled = subtitleResolved,
        )
        if (!kernel) {
            // Text that SubtitleView can draw must not also be composited by libass.
            quietKernelSubtitles(player, tracks)
            return
        }
        val requested = externalSubtitleUrl
        if (requested.isNullOrBlank()) {
            player.setOption(FSPlayer.OPT_CATEGORY_PLAYER, "subtitle_mix", 1)
            if (subtitleOrdinal < 0) {
                quietKernelSubtitles(player, tracks)
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
        player.setOption(FSPlayer.OPT_CATEGORY_PLAYER, "subtitle_mix", 1)
    }

    private fun quietKernelSubtitles(player: FSPlayer, tracks: List<FSTrackInfo>) {
        player.setOption(FSPlayer.OPT_CATEGORY_PLAYER, "subtitle_mix", 0)
        tracks.filter { it.trackType == ITrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT }.forEach { track ->
            val index = track.streamIndex
            if (index < 0) return@forEach
            try {
                player.deselectTrack(index)
            } catch (error: Exception) {
                Log.w(TAG, "subtitle deselect $index failed", error)
            }
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
        dispatch { it.onPlaybackStateChanged(state) }
    }

    private fun dispatchPlaying(playing: Boolean) {
        dispatch { it.onPlayingChanged(playing) }
    }

    private fun dispatchError(message: String) {
        dispatch { it.onError(message) }
    }

    private fun dispatchVideoDecoder(name: String) {
        dispatch { it.onVideoDecoder(name) }
    }

    private fun deviceSnapshot(): String {
        val abis = Build.SUPPORTED_ABIS.joinToString(",")
        return "manufacturer=${Build.MANUFACTURER} model=${Build.MODEL} abi=$abis " +
            "hardware=${Build.HARDWARE} board=${Build.BOARD} sdk=${Build.VERSION.SDK_INT} " +
            "release=${Build.VERSION.RELEASE} glesRoute=${VendorVideoRoute.current()}"
    }

    private fun noteEngine(line: String) {
        FsPassthroughLog.note(ClientLogText.redact(line))
    }

    private fun dispatch(block: (Listener) -> Unit) {
        val snapshot = listeners.toList()
        if (snapshot.isEmpty()) return
        mainHandler.post { snapshot.forEach(block) }
    }
}
