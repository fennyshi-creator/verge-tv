package com.xxxx.emby_tv.ui

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.tv.material3.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import com.xxxx.emby_tv.ui.components.KeyPhase
import com.xxxx.emby_tv.ui.components.OkLatch
import com.xxxx.emby_tv.ui.components.CenterGlyph
import com.xxxx.emby_tv.ui.components.PlayerBack
import com.xxxx.emby_tv.ui.components.PlayerBackAction
import com.xxxx.emby_tv.ui.components.PlayerKey
import com.xxxx.emby_tv.ui.components.PlayerPauseMark
import com.xxxx.emby_tv.ui.components.PlayerSeekReadout
import com.xxxx.emby_tv.ui.components.PositionLatch
import com.xxxx.emby_tv.ui.components.RemoteCommand
import com.xxxx.emby_tv.ui.components.RemoteContext
import com.xxxx.emby_tv.ui.components.SEEK_LATCH_MS
import com.xxxx.emby_tv.ui.components.SEEK_QUIET_MS
import com.xxxx.emby_tv.ui.components.SEEK_READOUT_HOLD_MS
import com.xxxx.emby_tv.ui.components.centerGlyph
import com.xxxx.emby_tv.ui.components.chromeHoldMs
import com.xxxx.emby_tv.ui.components.remoteIsPlaying
import com.xxxx.emby_tv.ui.components.decideRemote
import com.xxxx.emby_tv.ui.components.latchedDisplayMs
import com.xxxx.emby_tv.ui.components.nextSeek
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.MimeTypes
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import androidx.compose.ui.res.stringResource
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.BandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.xxxx.emby_tv.R
import com.xxxx.emby_tv.Utils
import com.xxxx.emby_tv.pickPreferredMediaSource
import com.xxxx.emby_tv.data.repository.EmbyRepository
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.data.model.MediaDto
import com.xxxx.emby_tv.data.model.MediaStreamDto
import com.xxxx.emby_tv.data.model.SessionDto
import androidx.compose.ui.graphics.toArgb
import com.xxxx.emby_tv.ui.components.PlayerMenu
import com.xxxx.emby_tv.ui.components.PlayerOverlay
import com.xxxx.emby_tv.ui.components.EpisodeStrip
import com.xxxx.emby_tv.ui.components.NextEpisodeCard
import com.xxxx.emby_tv.ui.components.PlayerSideSheet
import com.xxxx.emby_tv.ui.components.PlayerTransportBar
import com.xxxx.emby_tv.ui.components.SettingsSheetBody
import com.xxxx.emby_tv.ui.components.TrackSheet
import com.xxxx.emby_tv.ui.components.VersionSheet
import com.xxxx.emby_tv.ui.components.ResumePlaybackButtons
import com.xxxx.emby_tv.ui.components.SkipIntroButton
import com.xxxx.emby_tv.ui.components.getAudioTrack
import com.xxxx.emby_tv.ui.components.getVideoTrack

import android.view.SurfaceHolder
import android.view.SurfaceView
import com.xxxx.emby_tv.ui.player.DolbyPassthrough
import com.xxxx.emby_tv.ui.player.EmbyPlayback
import com.xxxx.emby_tv.ui.player.EmbySubtitles
import com.xxxx.emby_tv.ui.player.FsPlayback
import com.xxxx.emby_tv.ui.player.MkvCompressedSubtitleDetector
import com.xxxx.emby_tv.ui.viewmodel.PlayerViewModel
import com.xxxx.emby_tv.util.ErrorHandler
import com.xxxx.emby_tv.util.ExternalPlayerHelper
import com.xxxx.emby_tv.util.IntroSkipHelper
import com.xxxx.emby_tv.util.ZidooHelper
import com.xxxx.emby_tv.data.local.PreferencesManager
import com.xxxx.emby_tv.data.remote.EmbyApi
import com.xxxx.emby_tv.data.remote.EmbyApi.CLIENT_VERSION
import com.xxxx.emby_tv.data.remote.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 播放器界面（Screen）- 使用 PlayerViewModel
 */
// 字幕顶部预留比例：UI 100% 映射到实际 88%，保证字幕不出屏
private const val SUBTITLE_TOP_RESERVED_FRACTION = 0.12f

// 音频安全模式（进程级）：
// 部分电视固件在"多声道 PCM → 立体声"系统降混路径上存在 bug，触发后音频 HAL 卡死，
// AudioTrack 创建持续返回 DEAD_OBJECT（有画面无声音、一直转圈加载），只能重启电视恢复。
// 触发过一次故障后，本进程内后续播放器一律直接以立体声安全模式构建，从源头避开该路径；
// 重启 APP 后自动重试原生多声道。
private var audioSafeModeChannels = -1
private const val AUDIO_RECOVERY_TAG = "AudioRecovery"
private const val SPEED_TUNNEL_TAG = "SpeedTunnel"

/**
 * 手写声道混合矩阵。不使用 ChannelMixingMatrix.create()：
 * 它只实现了少数组合（如 6->2、8->2），对 3->2、4->2、5->2、7->2 等会直接抛
 * UnsupportedOperationException（曾导致进入播放页即闪退）。
 *
 * 一维系数数组布局（由 media3 源码确认）：coefficients[inputChannel * outputChannelCount + outputChannel]
 *
 * @param inputChannels 输入声道数 (1..8)
 * @param outputChannels 输出声道数，小于输入声道数时执行降混
 */
private fun buildChannelMixingMatrix(inputChannels: Int, outputChannels: Int): ChannelMixingMatrix {
    val coefficients: FloatArray = if (inputChannels == outputChannels) {
        // 恒等矩阵
        FloatArray(inputChannels * outputChannels).also { c ->
            for (ch in 0 until inputChannels) c[ch * outputChannels + ch] = 1f
        }
    } else if (outputChannels == 2) {
        // 降混到立体声。各声道数的标准 WAV 声道序：
        // 3:FL,FR,FC  4:FL,FR,BL,BR  5:FL,FR,FC,BL,BR  6:FL,FR,FC,LFE,BL,BR
        // 7:FL,FR,FC,LFE,BC,SL,SR  8:FL,FR,FC,LFE,BL,BR,SL,SR
        // 约定（同 ITU-R/系统降混）：FL/FR 直通，FC 双侧 0.707，LFE 丢弃，
        // BL/SL 折叠到 FL，BR/SR 折叠到 FR，BC 双侧 0.707
        val g = 0.70710678f
        val toStereo: Array<Pair<Float, Float>> = when (inputChannels) {
            1 -> arrayOf(1f to 1f)
            3 -> arrayOf(1f to 0f, 0f to 1f, g to g)
            4 -> arrayOf(1f to 0f, 0f to 1f, g to 0f, 0f to g)
            5 -> arrayOf(1f to 0f, 0f to 1f, g to g, g to 0f, 0f to g)
            6 -> arrayOf(1f to 0f, 0f to 1f, g to g, 0f to 0f, g to 0f, 0f to g)
            7 -> arrayOf(1f to 0f, 0f to 1f, g to g, 0f to 0f, g to g, g to 0f, 0f to g)
            8 -> arrayOf(1f to 0f, 0f to 1f, g to g, 0f to 0f, g to 0f, 0f to g, g to 0f, 0f to g)
            else -> Array(inputChannels) { i ->
                when (i) {
                    0 -> 1f to 0f
                    1 -> 0f to 1f
                    2 -> g to g   // FC
                    3 -> 0f to 0f // LFE
                    else -> if (i % 2 == 0) g to 0f else 0f to g
                }
            }
        }
        FloatArray(inputChannels * 2).also { c ->
            toStereo.forEachIndexed { i, pair ->
                c[i * 2] = pair.first      // 输入声道 i → 输出 FL
                c[i * 2 + 1] = pair.second // 输入声道 i → 输出 FR
            }
        }
    } else {
        // 罕见组合（输入 > 输出 >= 3）：前 outputChannels 路恒等，多余声道丢弃
        FloatArray(inputChannels * outputChannels).also { c ->
            for (ch in 0 until outputChannels) c[ch * outputChannels + ch] = 1f
        }
    }
    return ChannelMixingMatrix(inputChannels, outputChannels, coefficients)
}

/**
 * 倍速模式下屏蔽编码直通的 AudioSink 包装器。
 *
 * 部分电视（如 TCL MT5879）对 TrueHD/AC3/EAC3 走音频直通（OFFLOAD bitstream）输出，
 * media3 对编码流无法应用 Sonic 软件变速，表现为倍速参数被接受但实际播放恒为 1.0x。
 * 非 1.0 倍速时用本包装器拒绝所有非 PCM 格式，强制渲染器走解码（FFmpeg 扩展）→ PCM →
 * Sonic 变速路径；恢复 1.0 倍速后重建回原生直通能力的播放器。
 *
 * 注：media3 的 setAudioCapabilities 在 Builder 携带 context 时会被忽略（内部传 null），
 * 无法通过该 API 动态收窄直通能力，只能在构建时用包装器实现。
 */
@androidx.annotation.OptIn(UnstableApi::class)
private class SpeedPassthroughBlockingSink(
    wrapped: AudioSink,
    private val blockPassthrough: Boolean
) : ForwardingAudioSink(wrapped) {

    override fun supportsFormat(format: Format): Boolean {
        if (blockPassthrough && !Util.isEncodingLinearPcm(format.pcmEncoding)) {
            return false
        }
        return super.supportsFormat(format)
    }

    override fun getFormatSupport(format: Format): Int {
        if (blockPassthrough && !Util.isEncodingLinearPcm(format.pcmEncoding)) {
            return AudioSink.SINK_FORMAT_UNSUPPORTED
        }
        return super.getFormatSupport(format)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    playerViewModel: PlayerViewModel,
    mediaId: String,
    playbackPositionTicks: Long = 0L,
    initialMediaSourceId: String? = null,
    onPlaybackStateChanged: (isPlaying: Boolean) -> Unit = {},
    onNavigateToPlayer: (BaseItemDto) -> Unit = {},
    onRePlayer: (BaseItemDto, Long) -> Unit = { _, _ -> },
    onExit: () -> Unit = {},  // 添加退出回调
) {
    val context = LocalContext.current
    val view = LocalView.current
    val preferencesManager = remember { PreferencesManager(context) }
    val displayHdr = remember {
        if (android.os.Build.VERSION.SDK_INT < 24) false
        else {
            val window = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            @Suppress("DEPRECATION")
            val types = window.defaultDisplay.hdrCapabilities?.supportedHdrTypes
            types != null && types.isNotEmpty()
        }
    }

    // 获取 Repository 用于直接访问
    val repository = remember { EmbyRepository.getInstance(context) }
    val serverUrl = repository.serverUrl ?: ""
    val apiKey = repository.apiKey ?: ""

    // 使用 rememberCoroutineScope() 替代 GlobalScope，确保协程可取消
    val scope = rememberCoroutineScope()

    // 状态变量
    var isPlaying by remember { mutableStateOf(false) }
    var isShowInfo by remember { mutableStateOf(false) }
    var media by remember { mutableStateOf<MediaDto>(MediaDto()) }
    var mediaInfo by remember { mutableStateOf<BaseItemDto>(BaseItemDto()) }
    var seriesRating by remember { mutableStateOf<String?>(null) }
    var seasonRating by remember { mutableStateOf<String?>(null) }
    var session by remember { mutableStateOf<SessionDto?>(null) }
    var hasReportedPlaying by remember { mutableStateOf(false) }

    // 收藏状态 - 在播放页层面管理，菜单打开/关闭时保持状态
    var isFavorite by remember { mutableStateOf(false) }

    var subtitleTracks by remember { mutableStateOf<List<MediaStreamDto>>(emptyList()) }
    var selectedSubtitleIndex by remember { mutableStateOf(-99) }
    var audioTracks by remember { mutableStateOf<List<MediaStreamDto>>(emptyList()) }
    var selectedAudioIndex by remember { mutableStateOf(-1) }
    var videoUrl by remember { mutableStateOf<String?>(null) }

    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var resumePastFileClamped by remember { mutableStateOf(false) }
    var buffered by remember { mutableStateOf(0L) }
    var audioStatusLine by remember { mutableStateOf("") }

    var playbackCorrection by remember { mutableIntStateOf(preferencesManager.playbackCorrection) }
    var playMode by remember { mutableStateOf(0) } // 0: list loop, 1: single loop, 2: no loop
    var endedHandled by remember { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(false) }
    var exitHintAt by remember { mutableStateOf<Long?>(null) }
    var lastBackMs by remember { mutableLongStateOf(0L) }
    var playerSheet by remember { mutableStateOf(com.xxxx.emby_tv.ui.components.PlayerSheet.None) }
    var chromeTick by remember { mutableLongStateOf(0L) }
    var restoreButton by remember { mutableIntStateOf(-1) }
    var episodeStrip by remember { mutableStateOf(false) }
    var seriesEpisodes by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var dismissNextEpisode by remember { mutableStateOf(false) }
    var hasPlayed by remember { mutableStateOf(false) }
    var seekTarget by remember { mutableStateOf<Long?>(null) }
    var seekOrigin by remember { mutableLongStateOf(0L) }
    var seekHoldStart by remember { mutableLongStateOf(0L) }
    var seekDir by remember { mutableIntStateOf(0) }
    var seekPulse by remember { mutableLongStateOf(0L) }
    var seekFeedback by remember { mutableStateOf(false) }
    var positionLatch by remember { mutableStateOf<PositionLatch?>(null) }
    var overlayEpoch by remember { mutableIntStateOf(0) }
    var userPaused by remember { mutableStateOf(false) }
    var chromeHold by remember { mutableLongStateOf(chromeHoldMs(resumed = false)) }
    var menuFocused by remember { mutableStateOf(false) }
    val menuOwners = remember { mutableStateMapOf<Int, Boolean>() }
    val okLatch = remember { OkLatch() }
    val timelineFocus = remember { FocusRequester() }
    val transportButtons = remember { List(com.xxxx.emby_tv.ui.components.TransportSlots.count(showVersion = true)) { FocusRequester() } }
    var showDiagnostics by remember { mutableStateOf(preferencesManager.showDiagnostics) }
    var decodeAudio by remember { mutableStateOf(preferencesManager.decodeToStereo) }
    var decoderPreference by remember { mutableIntStateOf(preferencesManager.decoderPreference) }
    var hdrToSdr by remember { mutableStateOf(preferencesManager.hdrToSdr) }
    val initialRoute = remember { com.xxxx.emby_tv.ui.player.AudioRouteProbe.read(context) }
    var routeSnapshot by remember { mutableStateOf(initialRoute) }
    var routeAllowsBitstream by remember { mutableStateOf(initialRoute.allowBitstream) }
    var ratingStartMs by remember(mediaId) { mutableStateOf<Long?>(null) }
    var ratingNowMs by remember { mutableLongStateOf(0L) }
    var appliedBitstream by remember { mutableStateOf<Boolean?>(null) }
    var subtitleScale by remember { mutableFloatStateOf(preferencesManager.subtitleScale) }
    var subtitleInk by remember { mutableStateOf(Color(preferencesManager.subtitleInk)) }
    var showStats by remember { mutableStateOf(false) }

    // 继续播放/从头开始 按钮状态
    var showResumeButtons by remember { mutableStateOf(playbackPositionTicks > 0) }
    var resumeButtonsShownOnce by remember { mutableStateOf(false) }

    // 用于跟踪是否已经尝试过转码回退
    var hasTriedTranscodeFallback by remember { mutableStateOf(false) }
    var currentTracks by remember { mutableStateOf<Tracks?>(null) }

    // 因 MKV zlib 压缩等原因改由 Emby 服务器提取外挂 SRT 的字幕索引集合
    var configRoutedSubtitleIndices by remember { mutableStateOf(setOf<Int>()) }

    // 多版本片源切换：null 表示使用服务器返回的默认源
    var selectedMediaSourceId by remember { mutableStateOf(initialMediaSourceId) }
    var currentMediaSourceId by remember { mutableStateOf<String?>(null) }
    // 完整片源列表：指定 MediaSourceId 重新请求时服务器只返回单个源，
    // 需保留首次获取的完整列表供菜单切换使用
    var allMediaSources by remember { mutableStateOf<List<com.xxxx.emby_tv.data.model.MediaSourceInfoDto>>(emptyList()) }

    // 片头跳过相关状态
    var introStartMs by remember { mutableStateOf<Long?>(null) }
    var introEndMs by remember { mutableStateOf<Long?>(null) }
    var showSkipIntroButton by remember { mutableStateOf(false) }
    var autoSkipIntro by remember { mutableStateOf(preferencesManager.autoSkipIntro) }
    var hasAutoSkipped by remember { mutableStateOf(false) }

    // 缓冲设置 - 从 PreferencesManager 加载保存的值，使用 preferencesManager 作为 key 确保重新加载
    var minBufferMs by remember(preferencesManager.minBufferMs) { mutableIntStateOf(preferencesManager.minBufferMs) }
    var maxBufferMs by remember(preferencesManager.maxBufferMs) { mutableIntStateOf(preferencesManager.maxBufferMs) }
    var playbackBufferMs by remember(preferencesManager.playbackBufferMs) { mutableIntStateOf(preferencesManager.playbackBufferMs) }
    var rebufferMs by remember(preferencesManager.rebufferMs) { mutableIntStateOf(preferencesManager.rebufferMs) }
    var bufferSizeBytes by remember(preferencesManager.bufferSizeBytes) { mutableIntStateOf(preferencesManager.bufferSizeBytes) }

    // 倍速设置 - 从 PreferencesManager 加载保存的值
    var playbackSpeed by remember(preferencesManager.playbackSpeed) { mutableFloatStateOf(preferencesManager.playbackSpeed) }

    // 倍速专用 PCM 模式：非 1.0 倍速时屏蔽编码直通（直通流无法 Sonic 变速），
    // 需用仅 PCM 的音频管线构建播放器；初值与已保存倍速一致，避免进入时立即重建
    var speedPcmMode by remember { mutableStateOf(preferencesManager.playbackSpeed != 1.0f) }

    // 字幕位置设置 - 从 PreferencesManager 加载保存的值
    var subtitleBottomPadding by remember(preferencesManager.subtitleBottomPadding) { mutableFloatStateOf(preferencesManager.subtitleBottomPadding) }

    // 字幕时间偏移 - 仅当前播放会话生效
    var subtitleTimeOffsetMs by remember { mutableLongStateOf(0L) }
    val overlaySubtitleView = remember { mutableStateOf<SubtitleView?>(null) }

    // 收集设备支持的杜比视界profile
    val supportedDvProfiles by playerViewModel.supportedDvProfiles.collectAsState()


    val playbackInfoFailText = stringResource(R.string.failed_get_playback_info)
    var playbackTrigger by remember { mutableStateOf(0) }
    var currentVideoDecoderName by remember { mutableStateOf("") }
    var isTunnelingSafe by remember { mutableStateOf(false) }

    /**
     * 2026 深度探测：查询底层 MediaCodec 列表，确认硬件是否声明了隧道能力
     */
    fun checkActualHardwareTunnelingSupport(): Boolean {
        return try {
            // 检查最常用的两种 4K 格式
            val mimes = listOf(MimeTypes.VIDEO_DOLBY_VISION, MimeTypes.VIDEO_H265)
            val isMinesOK = mimes.any { mime ->
                val decoderInfos = MediaCodecUtil.getDecoderInfos(mime, false, false)
                decoderInfos.any { info ->
                    // 核心判断：硬件必须显式声明 it.tunneling 为 true
                    info.hardwareAccelerated && info.tunneling && !info.softwareOnly
                }
            }
            if (!isMinesOK) return false
            supportedDvProfiles.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    // TrackSelector 构建函数（播放器重建时复用；copiedParameters 非空时直接继承原播放器参数）
    fun createTrackSelector(copiedParameters: TrackSelectionParameters? = null): DefaultTrackSelector {
        return DefaultTrackSelector(context).apply {
            if (copiedParameters != null) {
                setParameters(copiedParameters)
            } else {
                // 1. 获取基础参数
                val baseParameters = buildUponParameters()
                    .setPreferredTextLanguage("zh")
                    // 开启这个：允许尝试超出硬件声明能力的解码（对杜比 P7 至关重要）
                    .setExceedRendererCapabilitiesIfNecessary(true)
                    .build()

                // 2. 动态判断隧道模式：仅在电视支持且非音频软解时开启
                isTunnelingSafe = checkActualHardwareTunnelingSupport()

                setParameters(
                    baseParameters.buildUpon()
                        // 倍速与隧道模式互斥（见下方 LaunchedEffect 注释），非 1.0 倍速时禁用隧道
                        .setTunnelingEnabled(isTunnelingSafe && playbackSpeed == 1.0f)
                        .build()
                )
            }
        }
    }


    /**
     * 构建渲染器工厂。
     * 核心：把多声道 PCM 在应用内降混到设备实际支持的输出声道数（如 5.1 → 2.0）再创建 AudioTrack，
     * 避免触发部分电视固件在"系统级多声道降混"路径上的 AudioTrack 创建失败 bug（HAL 卡死需重启电视）。
     *
     * 注意：AudioCapabilities.maxChannelCount 会把直通（DD+/Atmos bitstream）能力也算进去，
     * 在部分电视上严重虚高（实测 TCL 报 10），因此以 AudioDeviceInfo 上各输出设备的
     * 实际 PCM 声道数为准，两者取小。
     */
    fun createRenderersFactory(blockPassthrough: Boolean = false): DefaultRenderersFactory {
        val reportedMaxChannels =
            AudioCapabilities.getCapabilities(context).maxChannelCount.coerceIn(2, 8)
        val audioManager =
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devicePcmChannels = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .flatMap { device -> device.channelCounts.toList() }
            .filter { it in 2..8 }
            .distinct()
            .sorted()
        val capableChannels = if (devicePcmChannels.isNotEmpty()) {
            minOf(reportedMaxChannels, devicePcmChannels.last())
        } else {
            reportedMaxChannels
        }
        val targetChannels = if (audioSafeModeChannels > 0) audioSafeModeChannels else capableChannels
        Log.i(
            AUDIO_RECOVERY_TAG,
            "音频输出配置: 上报最大声道数=$reportedMaxChannels, 设备实测PCM声道=$devicePcmChannels, " +
                "可用声道数=$capableChannels, 目标输出声道数=$targetChannels"
        )
        return object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return try {
                    val channelMixer = ChannelMixingAudioProcessor()
                    // 全量注册 1..8 声道矩阵：超出目标的降混，未超出的恒等通过。
                    // 必须保证任何 PCM 输入都有矩阵，否则 media3 会抛 UnhandledAudioFormatException
                    // 并升级为致命播放错误（黑屏）。
                    for (inputChannels in 1..8) {
                        val outputChannels =
                            if (inputChannels > targetChannels) targetChannels else inputChannels
                        channelMixer.putChannelMixingMatrix(
                            buildChannelMixingMatrix(inputChannels, outputChannels)
                        )
                    }
                    SpeedPassthroughBlockingSink(
                        DefaultAudioSink.Builder(context)
                            .setEnableFloatOutput(enableFloatOutput)
                            // 关闭 AudioTrack 系统变速路径：部分电视 HAL（实测 TCL/MStar）设置成功后
                            // PCM 写入异常，系统会把倍速异步重置回 1.0（表现为倍速失效）。
                            // 强制走 Sonic 软件变速，不依赖 HAL。
                            .setEnableAudioTrackPlaybackParams(false)
                            // 保留 Sonic（倍速支持）并前置声道降混处理器
                            .setAudioProcessors(arrayOf(channelMixer, SonicAudioProcessor()))
                            .build(),
                        blockPassthrough
                    )
                } catch (e: Exception) {
                    // 兜底：任何矩阵/构建异常都回落到原生 sink，绝不影响进入播放页
                    Log.e(AUDIO_RECOVERY_TAG, "构建降混音频管线失败，回落原生输出", e)
                    SpeedPassthroughBlockingSink(
                        DefaultAudioSink.Builder(context)
                            .setEnableFloatOutput(enableFloatOutput)
                            .setEnableAudioTrackPlaybackParams(false)
                            .build(),
                        blockPassthrough
                    )
                }
            }
        }.apply {
            // 1. 核心：增加解码器自动降级判断
            setMediaCodecSelector { mimeType, requiresSecure, requiresTunneling ->
                // 1. 获取默认解码器（如果是杜比视频，首选通常是 DV 解码器）
                val dvDecoders =
                    MediaCodecUtil.getDecoderInfos(mimeType, requiresSecure, requiresTunneling)

                if (mimeType == MimeTypes.VIDEO_DOLBY_VISION) {
                    // 2. 获取 HEVC 备选解码器
                    val hevcDecoders = MediaCodecUtil.getDecoderInfos(
                        MimeTypes.VIDEO_H265,
                        requiresSecure,
                        requiresTunneling
                    )

                    // 3. 智能判断排序
                    val combined = ArrayList<MediaCodecInfo>()

                    // 检查是否有任何一个杜比解码器明确声称支持当前 Level/Profile
                    // Media3 会自动过滤掉完全不支持的，但对于 P7，很多电视报的是 "SUPPORT_UNKNOWN" 或功能受限
                    // 而且必须要有杜比视界的profile
                    val isHardwareLikelyToHandleDV =
                        dvDecoders.any { it.hardwareAccelerated && !it.softwareOnly } && supportedDvProfiles.isNotEmpty()

                    if (isHardwareLikelyToHandleDV) {
                        // 高性能电视：杜比优先，HEVC 垫后
                        combined.addAll(dvDecoders)
                        combined.addAll(hevcDecoders)
                    } else {
                        // 低性能电视（或 DV 解码器缺失）：HEVC 优先，确保能播
                        combined.addAll(hevcDecoders)
                        combined.addAll(dvDecoders)
                    }
                    combined
                } else {
                    dvDecoders
                }
            }

            // 逻辑：ExoPlayer 会先扫描系统 MediaCodecList。
            // 1. 如果电视硬件报支持该 Codec，优先用硬解。
            // 2. 如果电视硬件不支持（如 TrueHD/DTS），则自动切换到你的 FFmpeg 扩展。
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
//        setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

            // 这确保了渲染器能够处理复杂的字幕样式
            setEnableDecoderFallback(true)
        }
    }

    // 同步检查并修复约束条件 - 在 loadControl 创建之前执行
    if (maxBufferMs < minBufferMs || minBufferMs < rebufferMs || rebufferMs < playbackBufferMs) {
        val defaults = preferencesManager.getBufferDefaults()
        minBufferMs = defaults.minBufferMs
        maxBufferMs = defaults.maxBufferMs
        playbackBufferMs = defaults.playbackBufferMs
        rebufferMs = defaults.rebufferMs
        bufferSizeBytes = defaults.bufferSizeBytes
        preferencesManager.resetBufferDefaults()
    }

    // 缓存控制配置 - 针对 TV 端视频流媒体优化（构建函数，播放器重建时复用）
    fun createLoadControl(): DefaultLoadControl {
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryClass = activityManager.memoryClass
        val largeHeap = context.applicationInfo.flags and ApplicationInfo.FLAG_LARGE_HEAP != 0

        val targetBytes = when {
            largeHeap || memoryClass >= 512 -> bufferSizeBytes.coerceAtLeast(256 * 1024 * 1024)
            memoryClass >= 256 -> bufferSizeBytes.coerceAtLeast(128 * 1024 * 1024)
            memoryClass >= 128 -> bufferSizeBytes.coerceAtLeast(64 * 1024 * 1024)
            else -> bufferSizeBytes.coerceAtLeast(32 * 1024 * 1024)
        }

        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                minBufferMs,
                maxBufferMs,
                playbackBufferMs,
                rebufferMs
            )
            .setTargetBufferBytes(targetBytes)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

    val bandwidthMeter = remember {
        DefaultBandwidthMeter.getSingletonInstance(context)
    }

    val okHttpClient = HttpClient.getClient(context)

// 创建支持 OkHttp 的工厂
    val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        .setUserAgent(EmbyApi.CLIENT + "/" + CLIENT_VERSION)
        .setDefaultRequestProperties(
            mapOf(
                "X-Emby-Client" to EmbyApi.CLIENT,
                "X-Emby-Client-Version" to CLIENT_VERSION,
                "X-Emby-Device-Name" to EmbyApi.DEVICE_NAME,
            )
        )

    val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

    // ExoPlayer 构建函数（音频自动恢复时需要整体重建播放器）
    fun buildPlayer(): ExoPlayer {
        return ExoPlayer.Builder(context, createRenderersFactory(blockPassthrough = speedPcmMode))
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(createTrackSelector())
            .setLoadControl(createLoadControl())
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                playWhenReady = true // Ensure it tries to play immediately
            }
    }

    val player = remember { FsPlayback(context.applicationContext) }

    fun safeReportMs(rawMs: Long, naturalEnd: Boolean = false): Long {
        val runtimeMs = (media.mediaSources?.firstOrNull()?.runTimeTicks ?: 0L) / 10_000L
        return com.xxxx.emby_tv.ui.player.PlaybackReport.safePositionMs(
            playerPositionMs = rawMs,
            resumeMs = playbackPositionTicks / 10_000L,
            runtimeMs = runtimeMs,
            playerDurationMs = duration,
            naturalEnd = naturalEnd,
        )
    }

    fun playbackHeaders(): Map<String, String> {
        return mapOf(
            "User-Agent" to "${EmbyApi.CLIENT}/${CLIENT_VERSION}",
            "X-Emby-Token" to apiKey,
            "X-Emby-Client" to EmbyApi.CLIENT,
            "X-Emby-Client-Version" to CLIENT_VERSION,
            "X-Emby-Device-Name" to EmbyApi.DEVICE_NAME,
        ).filterValues { it.isNotBlank() }
    }

    // ===== 外部播放器统一拉起 + 进度回传 =====
    // pkg 语义：null=使用偏好设置的默认播放器；""=系统选择器；非空=指定包名
    // Zidoo 设备上走官方控制 API（直链推送 + 断点续播 + 进度轮询回传）
    var zidooMonitorActive by remember { mutableStateOf(false) }

    fun launchZidooPlayer(url: String): Boolean {
        if (zidooMonitorActive) return true
        zidooMonitorActive = true
        try {
            player.pause()
            player.stop()
        } catch (_: Exception) {
        }
        scope.launch(Dispatchers.IO) {
            var lastPosition = position
            try {
                // 上报拉起时进度
                playerViewModel.reportProgress(
                    mediaId = mediaId, media = media, position = position,
                    selectedSubtitleIndex = selectedSubtitleIndex,
                    selectedAudioIndex = selectedAudioIndex, isPaused = true
                )
                if (!ZidooHelper.openFile(url)) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context, context.getString(R.string.external_player_launch_failed),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }
                // 等待播放器就绪后跳到续播位置
                if (position > 0) {
                    kotlinx.coroutines.delay(3000)
                    ZidooHelper.seekTo(position)
                }
                // 轮询 Zidoo 播放状态，实时回传进度；播放会话结束（用户退出播放器）时上报停止
                while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                    kotlinx.coroutines.delay(5000)
                    val st = ZidooHelper.getPlayStatus() ?: break
                    if (st.positionMs > 0) {
                        lastPosition = st.positionMs
                        playerViewModel.reportProgress(
                            mediaId = mediaId, media = media, position = st.positionMs,
                            selectedSubtitleIndex = selectedSubtitleIndex,
                            selectedAudioIndex = selectedAudioIndex
                        )
                    }
                    if (st.durationMs > 0 && st.positionMs >= st.durationMs - 5000) {
                        break // 播放完成
                    }
                }
            } catch (e: Exception) {
                ErrorHandler.logError("PlayerScreen", "Zidoo 播放监控异常", e)
            } finally {
                zidooMonitorActive = false
                if (lastPosition > 0) {
                    playerViewModel.reportStopped(
                        mediaId = mediaId, media = media, position = lastPosition,
                        selectedSubtitleIndex = selectedSubtitleIndex,
                        selectedAudioIndex = selectedAudioIndex
                    )
                }
            }
        }
        return true
    }

    // Intent 方式拉起外部播放器的结果回传（VLC/MX Player 退出时回传播放位置）
    val externalPlayerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // VLC/MX Player 等退出时会回传播放位置，同步到服务器保证"继续观看"进度正确
        val returnedPos = ExternalPlayerHelper.extractReturnedPosition(result.data)
        if (returnedPos != null && returnedPos > 0) {
            position = returnedPos
            playerViewModel.reportProgress(
                mediaId = mediaId,
                media = media,
                position = returnedPos,
                selectedSubtitleIndex = selectedSubtitleIndex,
                selectedAudioIndex = selectedAudioIndex
            )
        }
    }

    fun launchViaIntent(url: String, targetPkg: String?): Boolean {
        val intent = ExternalPlayerHelper.buildLaunchIntent(url, targetPkg, position)
        // 指定包名在本机不可用时回退系统选择器
        if (!targetPkg.isNullOrBlank()) {
            try {
                if (intent.resolveActivity(context.packageManager) == null) intent.setPackage(null)
            } catch (_: Exception) {
                intent.setPackage(null)
            }
        }
        return try {
            // 暂停内置播放并上报当前进度，交由外部播放器接管（经 launcher 拉起以接收退出位置回传）
            player.pause()
            playerViewModel.reportProgress(
                mediaId = mediaId,
                media = media,
                position = position,
                selectedSubtitleIndex = selectedSubtitleIndex,
                selectedAudioIndex = selectedAudioIndex,
                isPaused = true
            )
            externalPlayerLauncher.launch(intent)
            true
        } catch (e: Exception) {
            ErrorHandler.logError("PlayerScreen", "拉起外部播放器失败", e)
            scope.launch {
                android.widget.Toast.makeText(
                    context, context.getString(R.string.external_player_launch_failed),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            false
        }
    }

    /**
     * 外部播放器统一入口：
     * Zidoo 设备走官方控制 API（DV 双层直通 + 进度完整回传），其余走 Intent 方式
     */
    fun launchExternalPlayer(pkg: String?): Boolean {
        val url = ExternalPlayerHelper.buildPlayUrl(
            media, mediaId, currentMediaSourceId, serverUrl, apiKey
        )
        if (url == null) {
            scope.launch {
                android.widget.Toast.makeText(
                    context, context.getString(R.string.external_player_launch_failed),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            return false
        }
        if (ZidooHelper.isZidooDevice() && ZidooHelper.isApiAvailable()) {
            return launchZidooPlayer(url)
        }
        return launchViaIntent(url, pkg ?: preferencesManager.preferredExternalPlayerPackage)
    }

    // ===== 音频故障自动恢复（全程自动，无用户交互）=====
    // 0: 正常；1: 已用立体声安全模式重建过播放器；2: 已降级为无声播放
    var audioRecoveryStage by remember { mutableIntStateOf(0) }
    fun bitstreamAllowed(): Boolean = com.xxxx.emby_tv.ui.player.AudioPolicy.usePassthrough(
        decoderPreference = decoderPreference,
        forceStereo = decodeAudio,
        speedIsOne = playbackSpeed == 1f,
        recoveryStage = audioRecoveryStage,
        routeAllows = routeAllowsBitstream,
    )

    fun hdrMode(video: com.xxxx.emby_tv.data.model.MediaStreamDto?): String =
        com.xxxx.emby_tv.ui.player.HdrMap.mode(
            manualOn = hdrToSdr,
            displayHdr = displayHdr,
            sourceHdr = com.xxxx.emby_tv.ui.player.PlaybackDebug.sourceIsHdr(
                video?.videoRange,
                video?.videoRangeType,
                video?.colorTransfer,
                video?.codec,
            ),
            api = android.os.Build.VERSION.SDK_INT,
            hardwareRequestAvailable = com.xxxx.emby_tv.ui.player.HdrToneMap.hardwareRequestAvailable(),
        )

    fun armHdr() {
        val video = media.mediaSources?.firstOrNull { it.id == currentMediaSourceId }
            ?.mediaStreams?.firstOrNull { it.type.equals("Video", true) }
            ?: media.mediaSources?.firstOrNull()?.mediaStreams?.firstOrNull { it.type.equals("Video", true) }
        val mode = hdrMode(video)
        player.mapHdrToSdr = mode == com.xxxx.emby_tv.ui.player.HdrMap.SHADER
        com.xxxx.emby_tv.ui.player.HdrToneMap.setRequestSdr(mode == com.xxxx.emby_tv.ui.player.HdrMap.HARDWARE)
    }
    // 当前播放器实例是否出现过音频输出错误（播放器重建时自动重置）
    var currentPlayerAudioError by remember { mutableStateOf(false) }

    fun handleAudioSinkFailure() {
        when (audioRecoveryStage) {
            0 -> {
                audioRecoveryStage = 1
                Log.w(AUDIO_RECOVERY_TAG, "音频输出初始化失败，关闭直通并以 PCM 重开")
                player.forcePcm(player.currentPosition)
            }
            1 -> {
                audioRecoveryStage = 2
                Log.w(AUDIO_RECOVERY_TAG, "PCM 仍无音频，自动转为无声播放（视频继续）")
                player.setVolume(0f, 0f)
                scope.launch {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.audio_output_degraded),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    var isBuffering by remember { mutableStateOf(true) } // Start true assuming we wait for load
    var downloadSpeed by remember { mutableStateOf(0L) }
    var lastSpeedUpdate by remember { mutableStateOf(0L) }

    // Long press state
    var leftKeyDownTime by remember { mutableStateOf(0L) }
    var rightKeyDownTime by remember { mutableStateOf(0L) }

    // 即使暂停播放也不会熄屏
    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = previous
        }
    }

    // Encoded AudioTrack cannot time-stretch. Speed other than 1.0 reopens on the PCM
    // decoder; returning to 1.0 reopens passthrough.
    LaunchedEffect(subtitleTimeOffsetMs) {
        player.setSubtitleDelayMs(subtitleTimeOffsetMs)
    }

    LaunchedEffect(player) {
        while (isActive) {
            val text = player.subtitleTextAt(position + subtitleTimeOffsetMs)
            overlaySubtitleView.value?.setCues(
                if (text.isNullOrBlank()) {
                    emptyList()
                } else {
                    listOf(
                        Cue.Builder()
                            .setText(text)
                            .setTextAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
                            .build()
                    )
                }
            )
            delay(200)
        }
    }

    LaunchedEffect(playbackSpeed) {
        if (player.url == null) return@LaunchedEffect
        Log.i(SPEED_TUNNEL_TAG, "倍速 ${playbackSpeed}x")
        player.setPlaybackSpeed(playbackSpeed)
    }

    LaunchedEffect(leftKeyDownTime) {
        if (leftKeyDownTime > 0) {
            delay(500)
            while (isActive) {
                val newPos = (player.currentPosition - 30000).coerceAtLeast(0)
                player.seekTo(newPos)
                position = newPos // Update progress bar
                if (!player.isPlaying) {
                    player.play() // Auto-play if paused
                }
                delay(200)
            }
        }
    }

    LaunchedEffect(rightKeyDownTime) {
        if (rightKeyDownTime > 0) {
            delay(500)
            while (isActive) {
                val newPos = (player.currentPosition + 30000).coerceAtMost(duration)
                player.seekTo(newPos)
                position = newPos // Update progress bar
                if (!player.isPlaying) {
                    player.play() // Auto-play if paused
                }
                delay(200)
            }
        }
    }


    // 实现转码回退逻辑
    fun fallbackToServerTranscode() {
        if (hasTriedTranscodeFallback) {
            return
        }

        hasTriedTranscodeFallback = true
        Log.d("PlayerScreen", "开始执行转码回退逻辑")

        // 使用 rememberCoroutineScope() 替代 GlobalScope，确保协程可取消
        scope.launch(Dispatchers.IO) {
            val requestAudioIndex = if (selectedAudioIndex <= -1) null else selectedAudioIndex
            val requestSubtitleIndex =
                if (selectedSubtitleIndex <= -1) null else selectedSubtitleIndex

            try {
                if (media.mediaSources?.firstOrNull()?.transcodingUrl != null) {
                    repository.stopActiveEncodings(
                        media.playSessionId
                    )
                }
                val mediaResult = repository.getPlaybackInfo(
                    mediaId,
                    if (position > 0) position * 10000 else playbackPositionTicks,
                    requestAudioIndex,
                    requestSubtitleIndex,
                    true,
                    selectedMediaSourceId
                )

                if (mediaResult.mediaSources.isNullOrEmpty()) {
                    Log.e("PlayerScreen", "转码回退失败：无法获取播放信息")
                    return@launch
                }

                // 直接访问mediaSources属性
                val source = mediaResult.mediaSources.firstOrNull()

                val newVideoUrl = EmbyPlayback.resolvePlaybackUrl(
                    serverUrl,
                    source,
                    forceTranscode = true,
                )

                if (newVideoUrl != null) {
                    withContext(Dispatchers.Main) {
                        Log.d("PlayerScreen", "转码回退成功，更新视频URL")
                        videoUrl = newVideoUrl
                        media = mediaResult
                        val audio = audioTracks.firstOrNull { it.index == selectedAudioIndex }
                        armHdr()
                        player.open(
                            url = newVideoUrl,
                            headers = playbackHeaders(),
                            startMs = position,
                            passthrough = bitstreamAllowed(),
                            joc = DolbyPassthrough.wantsJoc(audio),
                            speed = playbackSpeed,
                        )
                        player.playWhenReady = true
                    }
                } else {
                    Log.e("PlayerScreen", "转码回退失败：没有找到转码URL")
                }
            } catch (e: Exception) {
                Log.e("PlayerScreen", "转码回退过程中出现异常: ${e.message}", e)
            }
        }
    }


    LaunchedEffect(mediaInfo.type, mediaInfo.seriesId) {
        try {
            val series = mediaInfo.type == "Episode" || mediaInfo.seriesId != null
            if (mediaInfo.type.isNullOrBlank() && mediaInfo.seriesId == null) {
                playMode = preferencesManager.playMode
                return@LaunchedEffect
            }
            val key = if (series) "play_mode" else "play_mode_movie"
            val prefs = context.getSharedPreferences("emby_tv_prefs", Context.MODE_PRIVATE)
            playMode = if (prefs.contains(key)) prefs.getInt(key, preferencesManager.playMode) else preferencesManager.playMode
        } catch (e: Exception) {
            ErrorHandler.logError("PlayerScreen", "操作失败", e)
        }
    }

    // 提供一个函数供 UI 调用切换（例如点击列表时调用）
    fun changeTrack(audioIndex: Int, subIndex: Int) {
        val needChange =
            (selectedAudioIndex != audioIndex && audioIndex > -1) || (selectedSubtitleIndex != subIndex && subIndex > -1)
        selectedAudioIndex = audioIndex
        selectedSubtitleIndex = subIndex
        hasTriedTranscodeFallback = false
        if (needChange && !(media.mediaSources?.firstOrNull()?.supportsDirectPlay
                ?: false)
        ) playbackTrigger++ // 只有手动修改时，才递增触发器，重启协程
    }

    // 数据加载逻辑
    LaunchedEffect(mediaId, playbackCorrection, playbackTrigger) {
        //  这里的逻辑只会运行一次（初始化时）或者在手动递增 trigger 时运行
        val requestAudioIndex = if (selectedAudioIndex <= -1) null else selectedAudioIndex
        val requestSubtitleIndex = if (selectedSubtitleIndex <= -1) null else selectedSubtitleIndex

        try {
            if (media.mediaSources?.firstOrNull()?.transcodingUrl != null) {
                repository.stopActiveEncodings(
                    media.playSessionId
                )
            }
            val mediaResult = repository.getPlaybackInfo(
                mediaId,
                if (position > 0) position * 10000 else playbackPositionTicks,
                requestAudioIndex,
                requestSubtitleIndex,
                hasTriedTranscodeFallback || playbackCorrection == 1,
                selectedMediaSourceId
            )

            if (mediaResult.mediaSources.isNullOrEmpty()) {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        playbackInfoFailText,
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                return@LaunchedEffect
            }

            // 直接使用 mediaResult 对象，赋值给状态
            media = mediaResult
            val mediaInfoResult = repository.getMediaInfo(mediaId)
            mediaInfo = mediaInfoResult

            // 更新收藏状态
            isFavorite = mediaInfoResult.userData?.isFavorite == true

            // 加载播放模式（剧集与电影分开保存，电影默认不循环）
            val isSeriesContent =
                mediaInfoResult.type == "Episode" || mediaInfoResult.seriesId != null
            val modePrefs = context.getSharedPreferences("emby_tv_prefs", Context.MODE_PRIVATE)
            playMode = if (isSeriesContent) {
                modePrefs.getInt("play_mode", 0)
            } else {
                modePrefs.getInt("play_mode_movie", 2)
            }

            // PlaybackInfo often returns only the source about to play. The item
            // catalog is the same MediaSources list the details page uses.
            val itemSources = withContext(Dispatchers.IO) {
                runCatching { repository.getMovieItem(mediaId).mediaSources.orEmpty() }
                    .getOrDefault(emptyList())
            }
            allMediaSources = com.xxxx.emby_tv.ui.player.VersionCatalog.choose(
                playback = mediaResult.mediaSources.orEmpty(),
                item = itemSources,
                cached = allMediaSources,
            )

            // 初次进入：自动选择片源。优先级：偏好策略（杜比视界/HDR/流畅优先）> 上次手动选择的片源名（仅默认排序时生效）
            if (selectedMediaSourceId == null && allMediaSources.size > 1) {
                val sourcePref = preferencesManager.sourcePreference
                val preferredName = preferencesManager.preferredMediaSourceName
                val autoPick = if (sourcePref != 3) {
                    // 用户显式配置了策略时，策略优先，忽略历史手动选择记忆
                    pickPreferredMediaSource(allMediaSources, sourcePref)
                } else if (preferredName.isNotBlank()) {
                    allMediaSources.firstOrNull { !it.name.isNullOrBlank() && it.name == preferredName }
                } else null

                val defaultId = mediaResult.mediaSources?.firstOrNull()?.id
                if (autoPick?.id != null && autoPick.id != defaultId) {
                    selectedMediaSourceId = autoPick.id
                    playbackTrigger++
                    return@LaunchedEffect
                }
            }

            // 优先使用用户选择的媒体源（多版本切换），否则取服务器返回的第一个
            val source = mediaResult.mediaSources.firstOrNull { it.id == selectedMediaSourceId }
                ?: mediaResult.mediaSources.firstOrNull()
            currentMediaSourceId = source?.id
            // 切换片源后重新匹配会话信息
            session = null
            hasReportedPlaying = false
            val streams = source?.mediaStreams ?: emptyList()
            // 检测片头信息
            val introRange = IntroSkipHelper.detectIntroRange(source?.chapters)
            if (introRange != null) {
                introStartMs = introRange.first
                introEndMs = introRange.second
                hasAutoSkipped = false // 重置自动跳过标志
            } else {
                introStartMs = null
                introEndMs = null
                showSkipIntroButton = false
                hasAutoSkipped = false
            }

            subtitleTracks = streams.filter { s ->
                s.type == "Subtitle"
            }

            audioTracks = streams.filter { s ->
                s.type == "Audio"
            }

            if (selectedAudioIndex == -1) {
                selectedAudioIndex = source?.defaultAudioStreamIndex ?: -1
            }
            if (selectedSubtitleIndex == -99) {
                selectedSubtitleIndex = source?.defaultSubtitleStreamIndex ?: -1
            }

            videoUrl = EmbyPlayback.resolvePlaybackUrl(
                serverUrl,
                source,
                forceTranscode = playbackCorrection == 1,
            )
            hasReportedPlaying = false
        } catch (e: Throwable) {
            Log.e("PlayerScreen", "加载播放信息失败", e)
        }
    }

    // 设置 MediaItem 和 字幕
    fun externalSubtitleUrl(mediaSourceId: String, routedIndices: Set<Int>): String? {
        if (selectedSubtitleIndex < 0) return null
        val track = subtitleTracks.firstOrNull { it.index == selectedSubtitleIndex } ?: return null
        return EmbySubtitles.streamUrl(
            serverUrl = serverUrl,
            mediaId = mediaId,
            mediaSourceId = mediaSourceId,
            apiKey = apiKey,
            track = track,
            routedIndices = routedIndices,
        )
    }

    LaunchedEffect(videoUrl) {
        if (videoUrl != null) {
            // 外部播放器偏好：1=总是 2=仅杜比视界，命中时不启动内置播放器，直接交给外部播放器
            val externalMode = preferencesManager.externalPlayerMode
            if (externalMode == 1 || externalMode == 2) {
                val isDv = externalMode == 2 && ExternalPlayerHelper.isDolbyVision(
                    ExternalPlayerHelper.getVideoStream(media, currentMediaSourceId)
                )
                if (externalMode == 1 || isDv) {
                    val launched = launchExternalPlayer(null)
                    if (launched) {
                        player.stop()
                        isBuffering = false
                        return@LaunchedEffect
                    }
                    // 拉起失败则回落到内置播放器继续播
                }
            }

            val source = media.mediaSources?.firstOrNull { it.id == currentMediaSourceId }
                ?: media.mediaSources?.firstOrNull()
            val mediaSourceId = source?.id ?: ""

            // 压缩过的 MKV 文本字幕仍交给 Emby 抽成 SRT。FSPlayer 自己画内嵌字幕。
            var routedIndices = configRoutedSubtitleIndices
            val playbackUrl: String = videoUrl ?: return@LaunchedEffect
            val isMkv = playbackUrl.contains(".mkv", ignoreCase = true)
            val hasEmbeddedTextSubs = subtitleTracks.any { it.isExternal != true }
            if (isMkv && hasEmbeddedTextSubs) {
                val compressedOrdinals = withContext(Dispatchers.IO) { MkvCompressedSubtitleDetector.detect(playbackUrl, okHttpClient) }
                if (compressedOrdinals.isNotEmpty()) {
                    val embeddedSubs = subtitleTracks.filter { it.isExternal != true }
                    routedIndices = compressedOrdinals
                        .mapNotNull { o -> embeddedSubs.getOrNull(o)?.index }
                        .toSet()
                    configRoutedSubtitleIndices = routedIndices
                    Log.w("PlayerScreen", "compressed subtitle tracks routed to server srt: $routedIndices")
                }
            }

            val startPositionMs = if (position > 0) {
                position
            } else if (playbackPositionTicks > 0) {
                playbackPositionTicks / 10000
            } else {
                0L
            }

            if (startPositionMs > 0) {
                Log.d("Player", "Setting initial position: $startPositionMs ms")
            }

            val audio = audioTracks.firstOrNull { it.index == selectedAudioIndex }
            armHdr()
            player.open(
                url = playbackUrl,
                headers = playbackHeaders(),
                startMs = startPositionMs,
                passthrough = bitstreamAllowed(),
                joc = DolbyPassthrough.wantsJoc(audio),
                speed = playbackSpeed,
            )
            player.playWhenReady = true
            player.selectEmbyTracks(
                audioOrdinal = audioTracks.indexOfFirst { it.index == selectedAudioIndex },
                subtitleOrdinal = subtitleTracks.indexOfFirst { it.index == selectedSubtitleIndex },
                externalSubtitleUrl = externalSubtitleUrl(mediaSourceId, routedIndices),
            )
        }
    }

    LaunchedEffect(selectedSubtitleIndex, selectedAudioIndex, subtitleTracks, audioTracks, configRoutedSubtitleIndices) {
        val sourceId = currentMediaSourceId ?: media.mediaSources?.firstOrNull()?.id ?: ""
        player.selectEmbyTracks(
            audioOrdinal = audioTracks.indexOfFirst { it.index == selectedAudioIndex },
            subtitleOrdinal = subtitleTracks.indexOfFirst { it.index == selectedSubtitleIndex },
            externalSubtitleUrl = externalSubtitleUrl(sourceId, configRoutedSubtitleIndices),
        )
    }


    // Handle Ended Logic for List Loop
    LaunchedEffect(endedHandled) {
        if (endedHandled && playMode == 0) {
            val seriesId = mediaInfo.seriesId
            if (seriesId != null) {
                try {
                    val list = repository.getSeriesList(seriesId)

                    @Suppress("UNCHECKED_CAST")
                    val episodes = list

                    val currentId = mediaId
                    val currentIndex = episodes.indexOfFirst { it.id == currentId }

                    if (currentIndex >= 0 && currentIndex < episodes.size - 1) {
                        val nextEpisode = episodes[currentIndex + 1]
                        onNavigateToPlayer(nextEpisode)
                    } else {

                    }
                } catch (e: Exception) {
                    ErrorHandler.logError("PlayerScreen", "操作失败", e)

                }
            } else {

            }
        }
    }

    // Session Reporting & Updates - 使用 playerViewModel 定期报告进度
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            var tickCount = 0
            while (isActive) {
                try {
                    // 每 9 秒报告一次进度
                    if (tickCount % 9 == 0) {
                        playerViewModel.reportProgress(
                            mediaId = mediaId,
                            media = media,
                            position = safeReportMs(position),
                            selectedSubtitleIndex = selectedSubtitleIndex,
                            selectedAudioIndex = selectedAudioIndex
                        )
                    }
                    tickCount++
                } catch (e: Exception) {
                    ErrorHandler.logError("PlayerScreen", "报告进度失败", e)
                }
                delay(1000)
            }
        }
    }

    // Initial Playing Report - 使用 playerViewModel 报告播放开始
    LaunchedEffect(isPlaying, videoUrl) {
        if (isPlaying && videoUrl != null && !hasReportedPlaying) {
            try {
                playerViewModel.reportPlaying(
                    mediaId = mediaId,
                    media = media,
                    position = safeReportMs(position),
                    selectedSubtitleIndex = selectedSubtitleIndex,
                    selectedAudioIndex = selectedAudioIndex,
                    playbackRate = playbackSpeed
                )
                hasReportedPlaying = true
            } catch (e: Exception) {
                ErrorHandler.logError("PlayerScreen", "报告播放开始失败", e)
            }
        }
    }

    // 自动隐藏继续播放按钮（3秒后）
    LaunchedEffect(showResumeButtons) {
        if (showResumeButtons && !resumeButtonsShownOnce) {
            resumeButtonsShownOnce = true
            delay(3000)
            showResumeButtons = false
        }
    }

    // Load session once after reporting playing
    // Load session once after reporting playing (with retry)
    LaunchedEffect(hasReportedPlaying, media.playSessionId) {
        if (!hasReportedPlaying) return@LaunchedEffect

        val currentId = media.playSessionId ?: return@LaunchedEffect
        val retries = 4

        repeat(retries) { attempt ->
            try {
                // Delay waiting for server to process playing report (500ms initial + retry interval)
                delay(1200)

                val sessions = repository.getPlayingSessions()
                val source =
                    media.mediaSources?.firstOrNull()
                val mediaSourceId = source?.id

                val found = sessions
                    .find { s ->
                        // Match by NowPlayingItem.Id
                        val nowPlayingId = s.nowPlayingItem?.id
                        if (nowPlayingId == mediaId || (mediaSourceId != null && nowPlayingId == mediaSourceId)) {
                            return@find true
                        }
                        false
                    }

                if (found != null) {
                    // 检查是否需要继续等待转码信息
                    val playMethod = found.playState?.playMethod
                    val transcodingInfo = found.transcodingInfo

                    if (playMethod == "Transcode" && transcodingInfo == null) {
                        android.util.Log.d(
                            "PlayerSession",
                            "Found session but transcodingInfo is null for Transcode mode, retrying..."
                        )
                        session = null  // 清空，继续重试
                    } else {

                        session = found
                        android.util.Log.d(
                            "PlayerSession",
                            "Matched session on attempt ${5 - retries}, playMethod=$playMethod"
                        )

                        return@LaunchedEffect
                    }
                }


            } catch (e: Exception) {
                ErrorHandler.logError("PlayerScreen", "操作失败", e)

            }
            // 如果运行到这里，说明本次没找到，repeat 会继续下一次
            if (attempt == retries - 1) {
                Log.w("Session", "达到最大重试次数，未能找到 Session: $currentId")
            }
        }
    }


    // 使用 LaunchedEffect 监听 player 实例
    // 当 player 变化或 Composable 销毁时，这个协程会自动重启或取消
    LaunchedEffect(player) {
        while (true) {
            if (player.isPlaying || player.playbackState == FsPlayback.STATE_BUFFERING) {
                // 1. 更新当前播放位置。快进提交后先停在目标时间，等播放器追上再放开。
                val rawPosition = player.currentPosition.coerceAtLeast(0L)
                val held = latchedDisplayMs(rawPosition, positionLatch, System.currentTimeMillis())
                if (positionLatch != null && held == null) positionLatch = null
                if (seekTarget == null) position = held ?: rawPosition

                // 2. 更新缓存进度
                buffered = player.bufferedPosition.coerceAtLeast(0L)
                val speedSample = player.tcpSpeed()
                if (speedSample > 0L) downloadSpeed = speedSample
                audioStatusLine = DolbyPassthrough.statusLine(
                    getAudioTrack(media, selectedAudioIndex),
                    player.passthroughEncoding(),
                    player.passthroughFallback(),
                )

            }

            // 每 800 毫秒更新一次进度
            delay(800)
        }
    }

    LaunchedEffect(mediaId, player.playbackState, player.isPlaying) {
        if (ratingStartMs == null &&
            (player.playbackState == FsPlayback.STATE_READY || player.isPlaying)
        ) {
            val now = android.os.SystemClock.uptimeMillis()
            ratingStartMs = now
            ratingNowMs = now
        }
    }
    LaunchedEffect(ratingStartMs) {
        val start = ratingStartMs ?: return@LaunchedEffect
        while (android.os.SystemClock.uptimeMillis() - start < 4_600L) {
            ratingNowMs = android.os.SystemClock.uptimeMillis()
            delay(40)
        }
        ratingNowMs = android.os.SystemClock.uptimeMillis()
    }

    // 缓冲卡死看门狗：长时间缓冲且缓冲进度零增长时，若当前播放器存在音频输出错误则触发自动恢复；
    // 同时把停滞的网速显示归零，避免一直显示冻结的加载速度
    LaunchedEffect(player) {
        var lastBufferedPosition = -1L
        var lastChangeTime = System.currentTimeMillis()
        while (isActive) {
            delay(1000)
            val p = player
            val currentBuffered = p.bufferedPosition
            if (currentBuffered != lastBufferedPosition) {
                lastBufferedPosition = currentBuffered
                lastChangeTime = System.currentTimeMillis()
            } else {
                downloadSpeed = 0
                if (p.playbackState == FsPlayback.STATE_BUFFERING && !p.isPlaying &&
                    currentBuffered > 0 &&
                    System.currentTimeMillis() - lastChangeTime >= 15_000 &&
                    currentPlayerAudioError && audioRecoveryStage < 2
                ) {
                    Log.w(AUDIO_RECOVERY_TAG, "检测到音频故障导致的缓冲卡死，触发自动恢复")
                    handleAudioSinkFailure()
                    lastChangeTime = System.currentTimeMillis()
                }
            }
        }
    }

    // Play was requested and the clock is not moving. Reopen once; a second
    // stall waits until the position actually advances before trying again.
    LaunchedEffect(player) {
        var lastPos = player.currentPosition
        var stillSince = 0L
        var retries = 0
        while (isActive) {
            delay(500)
            if (player.isReleased) break
            if (!player.playWhenReady || userPaused || seekFeedback) {
                lastPos = player.currentPosition
                stillSince = 0L
                continue
            }
            val pos = player.currentPosition
            val now = System.currentTimeMillis()
            if (pos != lastPos) {
                lastPos = pos
                stillSince = 0L
                retries = 0
                continue
            }
            val state = player.playbackState
            if (state == FsPlayback.STATE_IDLE || state == FsPlayback.STATE_ENDED) {
                stillSince = 0L
                continue
            }
            if (stillSince == 0L) stillSince = now
            if (now - stillSince >= 2_500L && retries < FsPlayback.STALL_REOPEN_LIMIT) {
                retries++
                Log.w("PlayerScreen", "playback stalled at $pos, reopening once")
                player.reopen(pos)
                stillSince = now
            }
        }
    }

    // 片头检测和自动跳过逻辑
    LaunchedEffect(isPlaying, position, introStartMs, introEndMs, autoSkipIntro, hasAutoSkipped) {
        if (introStartMs != null && introEndMs != null && isPlaying) {
            // 检查是否在片头范围内
            val inIntroRange = position >= introStartMs!! && position < introEndMs!!

            if (inIntroRange) {
                // 如果启用了自动跳过且还未跳过，则自动跳过
                if (autoSkipIntro && !hasAutoSkipped) {
                    player.seekTo(introEndMs!!)
                    hasAutoSkipped = true
                    showSkipIntroButton = false
                } else if (!autoSkipIntro) {
                    // 如果未启用自动跳过，显示跳过按钮
                    showSkipIntroButton = true
                }
            } else {
                // 不在片头范围内，隐藏跳过按钮
                if (position >= introEndMs!!) {
                    showSkipIntroButton = false
                }
            }
        } else {
            showSkipIntroButton = false
        }
    }

    // FSPlayer 状态。Media3 不再占用 Surface。
    DisposableEffect(player) {
        val listener = object : FsPlayback.Listener {
            override fun onPlayingChanged(playing: Boolean) {
                isPlaying = playing
                onPlaybackStateChanged(playing)
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    FsPlayback.STATE_BUFFERING -> isBuffering = true
                    FsPlayback.STATE_READY -> {
                        isBuffering = false
                        val rawDuration = player.duration
                        if (rawDuration > 0) duration = rawDuration
                        // HLS often learns the real length after prepare. A resume
                        // past that length (the ~120s point stored by 2.0.48) seeks
                        // off the end of a ~115s file.
                        val resumeMs = playbackPositionTicks / 10_000L
                        if (!resumePastFileClamped &&
                            rawDuration > 30_000L &&
                            resumeMs >= rawDuration - 1_500L
                        ) {
                            resumePastFileClamped = true
                            player.seekTo(0)
                        }
                    }
                    FsPlayback.STATE_IDLE -> isBuffering = false
                    FsPlayback.STATE_ENDED -> {
                        isBuffering = false
                        onPlaybackStateChanged(false)
                        if (playMode == 1) {
                            player.seekTo(0)
                            player.play()
                        } else if (playMode == 0 && mediaInfo.seriesId != null) {
                            if (!endedHandled) endedHandled = true
                        } else {
                            val finished = duration > 0L && position >= duration - 5_000L
                            playerViewModel.reportStopped(
                                mediaId = mediaId,
                                media = media,
                                position = safeReportMs(position, naturalEnd = finished),
                                selectedSubtitleIndex = selectedSubtitleIndex,
                                selectedAudioIndex = selectedAudioIndex,
                                playbackRate = playbackSpeed
                            )
                        }
                    }
                }
            }

            override fun onError(message: String) {
                Log.e("PlayerScreen", "播放器错误: $message")
                if (message.contains("429") || message.contains("限流")) {
                    return
                }
                // Stay on this screen. An audio open/decode failure reopens
                // without passthrough, then continues with video if PCM
                // cannot be opened either. Server transcode is not a way out:
                // this title reports SupportsTranscoding=false.
                when (audioRecoveryStage) {
                    0 -> {
                        audioRecoveryStage = 1
                        Log.w(AUDIO_RECOVERY_TAG, "播放器错误，关闭直通并以 PCM 重开")
                        player.forcePcm(player.currentPosition.coerceAtLeast(0L))
                    }
                    else -> {
                        if (audioRecoveryStage < 2) {
                            audioRecoveryStage = 2
                            Log.w(AUDIO_RECOVERY_TAG, "PCM 重开仍失败，保持画面并静音")
                            player.setVolume(0f, 0f)
                        }
                    }
                }
            }

            override fun onVideoDecoder(name: String) {
                currentVideoDecoderName = name
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.setVideoSurface(null)
            player.release()
        }
    }

    // 退出播放页时上报停止（与播放器实例重建解耦，音频自动恢复重建时不会误报）
    DisposableEffect(Unit) {
        onDispose {
            player.release()
            playerViewModel.reportStopped(
                mediaId = mediaId,
                media = media,
                position = safeReportMs(position),
                selectedSubtitleIndex = selectedSubtitleIndex,
                selectedAudioIndex = selectedAudioIndex,
                playbackRate = playbackSpeed
            )
        }
    }

    // 监听按键显示菜单
    val focusRequester = remember { FocusRequester() }

    // UI 结构 - 最外层纯黑背景
    LaunchedEffect(mediaInfo.seriesId, mediaInfo.seasonId) {
        val seriesId = mediaInfo.seriesId
        val seasonId = mediaInfo.seasonId
        seriesRating = if (seriesId.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { repository.getMediaInfo(seriesId).officialRating }.getOrNull()
            }
        }
        seasonRating = if (seasonId.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { repository.getMediaInfo(seasonId).officialRating }.getOrNull()
            }
        }
    }

    LaunchedEffect(mediaInfo.seriesId) {
        val seriesId = mediaInfo.seriesId ?: return@LaunchedEffect
        seriesEpisodes = withContext(Dispatchers.IO) {
            runCatching { repository.getSeriesList(seriesId) }.getOrDefault(emptyList())
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) hasPlayed = true
    }

    LaunchedEffect(seekPulse) {
        if (seekTarget == null) return@LaunchedEffect
        val generation = overlayEpoch
        seekFeedback = true
        delay(SEEK_QUIET_MS)
        val target = seekTarget ?: return@LaunchedEffect
        position = target
        positionLatch = PositionLatch(target, System.currentTimeMillis() + SEEK_LATCH_MS)
        seekTarget = null
        player.seekTo(target)
        delay(SEEK_READOUT_HOLD_MS)
        if (seekTarget == null && overlayEpoch == generation) seekFeedback = false
    }

    fun applyRoute(allow: Boolean) {
        val changed = allow != routeAllowsBitstream
        routeAllowsBitstream = allow
        val next = com.xxxx.emby_tv.ui.player.AudioPolicy.usePassthrough(
            decoderPreference, decodeAudio, playbackSpeed == 1f, audioRecoveryStage, allow,
        )
        val url = player.url
        if (!changed || url == null || appliedBitstream == next) {
            appliedBitstream = next
            return
        }
        appliedBitstream = next
        armHdr()
        player.open(
            url = url,
            headers = playbackHeaders(),
            startMs = player.currentPosition,
            passthrough = next,
            joc = DolbyPassthrough.wantsJoc(audioTracks.firstOrNull { it.index == selectedAudioIndex }),
            speed = playbackSpeed,
        )
    }

    DisposableEffect(context) {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
        val callback = object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) {
                val snap = com.xxxx.emby_tv.ui.player.AudioRouteProbe.read(context)
                routeSnapshot = snap
                applyRoute(snap.allowBitstream)
            }
            override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>?) {
                val snap = com.xxxx.emby_tv.ui.player.AudioRouteProbe.read(context)
                routeSnapshot = snap
                applyRoute(snap.allowBitstream)
            }
        }
        manager?.registerAudioDeviceCallback(callback, android.os.Handler(android.os.Looper.getMainLooper()))
        onDispose { manager?.unregisterAudioDeviceCallback(callback) }
    }

    fun hideOverlay() {
        overlayEpoch++
        seekFeedback = false
        chromeVisible = false
    }

    fun showChrome(resumed: Boolean) {
        chromeHold = chromeHoldMs(resumed)
        chromeVisible = true
        chromeTick = System.currentTimeMillis()
    }

    fun pauseFromRemote() {
        userPaused = true
        player.pause()
        showChrome(resumed = false)
    }

    fun resumeFromRemote() {
        userPaused = false
        player.play()
        showChrome(resumed = true)
    }

    fun nudgeSeek(dir: Int) {
        val now = System.currentTimeMillis()
        val gesture = nextSeek(
            pendingTargetMs = seekTarget,
            positionMs = position,
            originMs = seekOrigin,
            durationMs = duration,
            direction = dir,
            nowMs = now,
            holdStartMs = seekHoldStart,
            previousDirection = seekDir,
        )
        if (seekTarget == null) seekOrigin = gesture.originMs
        seekHoldStart = gesture.holdStartMs
        seekDir = gesture.direction
        seekTarget = gesture.targetMs
        seekPulse = now
        seekFeedback = true
        chromeHold = chromeHoldMs(resumed = false)
        chromeVisible = true
        chromeTick = now
        episodeStrip = false
        okLatch.cancel()
    }

    val shownPosition = seekTarget ?: position
    val nextEpisode = seriesEpisodes.getOrNull(seriesEpisodes.indexOfFirst { it.id == mediaId } + 1)
    val nextPromptAt = com.xxxx.emby_tv.ui.components.nextEpisodePromptMs(
        duration,
        media.mediaSources?.firstOrNull { it.id == currentMediaSourceId }?.chapters
            ?: media.mediaSources?.firstOrNull()?.chapters,
    )
    val showNextEpisode = nextEpisode != null && !dismissNextEpisode && duration > 0L &&
        shownPosition >= nextPromptAt && !episodeStrip

    LaunchedEffect(chromeVisible, playerSheet, chromeTick, chromeHold, userPaused, episodeStrip, seekFeedback) {
        if (!chromeVisible || userPaused || seekFeedback || episodeStrip ||
            playerSheet != com.xxxx.emby_tv.ui.components.PlayerSheet.None
        ) {
            return@LaunchedEffect
        }
        delay(chromeHold)
        hideOverlay()
    }

    LaunchedEffect(exitHintAt) {
        val armed = exitHintAt ?: return@LaunchedEffect
        delay(PlayerBack.WINDOW_MS)
        if (exitHintAt == armed) exitHintAt = null
    }

    fun dispatchPlayerBack(phase: KeyPhase) {
        val now = System.currentTimeMillis()
        if (phase == KeyPhase.Down) {
            if (now - lastBackMs < 80L) return
            lastBackMs = now
        }
        val readoutVisible = seekFeedback && (seekTarget != null || positionLatch?.targetMs != null)
        val overlay = playerSheet != com.xxxx.emby_tv.ui.components.PlayerSheet.None ||
            episodeStrip ||
            showNextEpisode ||
            (showResumeButtons && playbackPositionTicks > 0) ||
            (chromeVisible && !episodeStrip) ||
            readoutVisible ||
            showSkipIntroButton
        val step = PlayerBack.step(phase, overlay, exitHintAt, now)
        when (step.action) {
            PlayerBackAction.DismissOverlay -> {
                exitHintAt = null
                when {
                    showNextEpisode -> dismissNextEpisode = true
                    episodeStrip -> {
                        episodeStrip = false
                        restoreButton = -1
                    }
                    playerSheet != com.xxxx.emby_tv.ui.components.PlayerSheet.None -> {
                        playerSheet = com.xxxx.emby_tv.ui.components.PlayerSheet.None
                    }
                    showResumeButtons && playbackPositionTicks > 0 -> {
                        showResumeButtons = false
                        resumeButtonsShownOnce = true
                    }
                    showSkipIntroButton -> showSkipIntroButton = false
                    else -> hideOverlay()
                }
            }
            PlayerBackAction.ShowHint -> {
                if (seekFeedback && !readoutVisible) seekFeedback = false
                exitHintAt = step.hintAtMs
            }
            PlayerBackAction.ExitPlayer -> {
                exitHintAt = null
                onExit()
            }
            PlayerBackAction.Ignore -> Unit
        }
    }

    LaunchedEffect(episodeStrip, playerSheet, showResumeButtons) {
        if (episodeStrip || showResumeButtons ||
            playerSheet != com.xxxx.emby_tv.ui.components.PlayerSheet.None
        ) {
            return@LaunchedEffect
        }
        delay(32)
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (remoteKey(event) == PlayerKey.Back) {
                    dispatchPlayerBack(remotePhase(event))
                    return@onPreviewKeyEvent true
                }
                if (chromeVisible && menuFocused && remotePhase(event) != KeyPhase.Up) {
                    chromeTick = System.currentTimeMillis()
                    chromeHold = chromeHoldMs(resumed = false)
                }
                val decision = decideRemote(
                    RemoteContext(
                        controlsVisible = chromeVisible,
                        playing = remoteIsPlaying(userPaused, isPlaying, hasPlayed),
                        sheetOpen = playerSheet != com.xxxx.emby_tv.ui.components.PlayerSheet.None,
                        episodeStrip = episodeStrip,
                        resumePrompt = showResumeButtons && playbackPositionTicks > 0,
                        seeking = seekFeedback,
                        menuFocused = menuFocused,
                    ),
                    remoteKey(event),
                    remotePhase(event),
                )
                if (!decision.consume && decision.command == RemoteCommand.Ignore) return@onPreviewKeyEvent false
                when (decision.command) {
                    RemoteCommand.SeekBackward -> nudgeSeek(-1)
                    RemoteCommand.SeekForward -> nudgeSeek(1)
                    RemoteCommand.RevealBar -> {
                        okLatch.cancel()
                        showChrome(resumed = false)
                    }
                    RemoteCommand.Pause -> {
                        if (okLatch.consume(KeyPhase.Up)) pauseFromRemote()
                    }
                    RemoteCommand.Resume -> {
                        if (okLatch.consume(KeyPhase.Up)) resumeFromRemote()
                    }
                    RemoteCommand.Ignore -> {
                        if (remoteKey(event) == PlayerKey.Ok) okLatch.consume(remotePhase(event))
                    }
                    RemoteCommand.HideOverlay -> hideOverlay()
                }
                true
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(enabled = !chromeVisible, onClick = { showChrome(resumed = false) })
                .focusRequester(focusRequester)
                .focusProperties {
                    if (chromeVisible) {
                        up = transportButtons[0]
                        down = timelineFocus
                    }
                }
                .focusable(
                    enabled = playerSheet == com.xxxx.emby_tv.ui.components.PlayerSheet.None &&
                        !episodeStrip &&
                        !(showResumeButtons && playbackPositionTicks > 0),
                )
        ) {
            // 1. Video Layer
            AndroidView(
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                player.setVideoSurface(holder.surface)
                            }

                            override fun surfaceChanged(
                                holder: SurfaceHolder,
                                format: Int,
                                width: Int,
                                height: Int,
                            ) {
                                player.setVideoSurface(holder.surface)
                            }

                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                player.setVideoSurface(null)
                            }
                        })
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            val ratingLabel = com.xxxx.emby_tv.util.EmbyPresentation.ratingLine(
                com.xxxx.emby_tv.util.EmbyPresentation.playbackRating(
                    mediaInfo.officialRating,
                    seriesRating,
                    seasonRating,
                ),
            )
            val ratingAlpha = ratingStartMs?.let {
                com.xxxx.emby_tv.util.EmbyPresentation.ratingAlpha(ratingNowMs - it)
            } ?: 0f
            if (!ratingLabel.isNullOrBlank() && ratingAlpha > 0.01f) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 36.dp, top = 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .width(4.dp)
                            .height(26.dp)
                            .background(Color(0xFF52B54B).copy(alpha = ratingAlpha)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = ratingLabel,
                        color = Color.White.copy(alpha = ratingAlpha),
                        fontSize = 22.sp,
                    )
                }
            }
            if (showDiagnostics && audioStatusLine.isNotBlank()) {
                Text(
                    text = audioStatusLine,
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 28.dp, top = 18.dp)
                )
            }

            // 1.5 Subtitle Layer - FSPlayer 把内嵌字幕画在画面上。外挂 SRT 走 loadThenActiveSubtitle。
            AndroidView(
                factory = { ctx ->
                    SubtitleView(ctx).apply {
                        // 1. 允许应用字幕文件内置的样式（颜色、字体、定位等）
                        setApplyEmbeddedStyles(true)

                        // 2. 允许应用字幕文件内置的字体大小
                        setApplyEmbeddedFontSizes(true)

                        // 3. 关键：将渲染模式设为 BITMAP（位图模式）
                        // 只有在这种模式下，复杂的 ASS/SSA 特效和 PGS 图形字幕才能精准还原
                        // 默认的层次模式（VIEW_TYPE_TEXT）会丢失很多高级特效
                        // VIEW_TYPE_CANVAS = 2
                        setViewType(SubtitleView.VIEW_TYPE_CANVAS)

                        // 4. 强制设置透明背景，避免默认样式的黑色背景遮挡
                        val transparentStyle = CaptionStyleCompat(
                            android.graphics.Color.WHITE,
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            android.graphics.Color.BLACK,
                            null
                        )
                        setStyle(transparentStyle)

                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        overlaySubtitleView.value = this
                    }
                },
                update = { view ->
                    view.setFractionalTextSize(0.0533f * subtitleScale)
                    view.setStyle(
                        CaptionStyleCompat(
                            subtitleInk.toArgb(),
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            android.graphics.Color.BLACK,
                            null,
                        )
                    )
                    val lift = if (chromeVisible || episodeStrip) 0.22f else 0f
                    view.setBottomPaddingFraction(
                        (subtitleBottomPadding + lift).coerceAtMost(0.42f) * (1f - SUBTITLE_TOP_RESERVED_FRACTION)
                    )
                },
                modifier = Modifier.fillMaxSize()
            )


            if (isBuffering && !userPaused) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }

            // 4. Resume Buttons (从头开始 / 继续播放)
            if (showResumeButtons && playbackPositionTicks > 0) {
                ResumePlaybackButtons(
                    countdownSeconds = 3,
                    onPlayFromStart = {
                        showResumeButtons = false
                        resumeButtonsShownOnce = true
                        player.seekTo(0)
                        player.play()
                    },
                    onContinue = {
                        showResumeButtons = false
                        resumeButtonsShownOnce = true
                    },
                    onTimeout = {
                        showResumeButtons = false
                        resumeButtonsShownOnce = true
                    }
                )
            }

            // 4.5. Skip Intro Button (跳过片头)
            if (showSkipIntroButton && introEndMs != null && !showResumeButtons) {
                SkipIntroButton(
                    introEndMs = introEndMs!!,
                    onSkip = {
                        showSkipIntroButton = false
                        player.seekTo(introEndMs!!)
                    },
                    onTimeout = {
                        showSkipIntroButton = false
                    },
                    countdownSeconds = 5
                )
            }

            val glyph = centerGlyph(
                chromeVisible = chromeVisible && !episodeStrip &&
                    playerSheet == com.xxxx.emby_tv.ui.components.PlayerSheet.None,
                playing = !userPaused,
                seeking = seekFeedback,
            )
            if (glyph == CenterGlyph.Pause) {
                PlayerPauseMark(Modifier.align(Alignment.Center))
            }
            val readoutMs = seekTarget ?: positionLatch?.targetMs
            if (seekFeedback && readoutMs != null) {
                PlayerSeekReadout(
                    targetMs = readoutMs,
                    durationMs = duration,
                    direction = seekDir,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            val playingSource = media.mediaSources?.firstOrNull { it.id == currentMediaSourceId }
                ?: media.mediaSources?.firstOrNull()
            val streamLines = com.xxxx.emby_tv.ui.player.StreamInfo.lines(playingSource, selectedAudioIndex)
            PlayerTransportBar(
                visible = chromeVisible && !episodeStrip,
                serverUrl = serverUrl,
                apiKey = apiKey,
                mediaInfo = mediaInfo,
                positionMs = shownPosition,
                durationMs = duration,
                showVersionButton = com.xxxx.emby_tv.util.EmbyPresentation.showVersionButton(allMediaSources.size),
                episodes = if (episodeStrip) emptyList() else seriesEpisodes,
                restoreButton = restoreButton,
                timelineFocus = timelineFocus,
                pictureFocus = focusRequester,
                buttonFocus = transportButtons,
                onOpenSheet = {
                    playerSheet = it
                    chromeTick = System.currentTimeMillis()
                },
                onOpenEpisodes = {
                    episodeStrip = true
                    chromeTick = System.currentTimeMillis()
                },
                onButtonFocused = { restoreButton = it },
                onMenuFocus = { index, focused ->
                    if (focused) menuOwners[index] = true else menuOwners.remove(index)
                    menuFocused = menuOwners.isNotEmpty()
                },
                onInteract = { chromeTick = System.currentTimeMillis() },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            EpisodeStrip(
                visible = episodeStrip,
                serverUrl = serverUrl,
                apiKey = apiKey,
                episodes = seriesEpisodes,
                currentId = mediaId,
                progressFocus = focusRequester,
                onPlay = { episode -> onNavigateToPlayer(episode) },
                onClose = {
                    episodeStrip = false
                    restoreButton = -1
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            NextEpisodeCard(
                visible = showNextEpisode,
                episode = nextEpisode,
                countdownSec = ((duration - shownPosition) / 1000L).toInt().coerceAtLeast(0),
                onPlay = { nextEpisode?.let(onNavigateToPlayer) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = if (chromeVisible) 120.dp else 28.dp),
            )

            if (exitHintAt != null) {
                Text(
                    text = PlayerBack.HINT,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp)
                        .background(Color(0xCC141210), androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
            PlayerSideSheet(
                visible = playerSheet != com.xxxx.emby_tv.ui.components.PlayerSheet.None,
                title = when (playerSheet) {
                    com.xxxx.emby_tv.ui.components.PlayerSheet.Subtitles -> "字幕"
                    com.xxxx.emby_tv.ui.components.PlayerSheet.Audio -> "音频"
                    com.xxxx.emby_tv.ui.components.PlayerSheet.Versions -> "片源"
                    com.xxxx.emby_tv.ui.components.PlayerSheet.Info -> "信息"
                    else -> "播放设置"
                },
                onDismiss = { playerSheet = com.xxxx.emby_tv.ui.components.PlayerSheet.None },
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                when (playerSheet) {
                    com.xxxx.emby_tv.ui.components.PlayerSheet.Subtitles -> TrackSheet(
                        title = "字幕",
                        tracks = subtitleTracks,
                        selectedIndex = selectedSubtitleIndex,
                        offLabel = "关闭字幕",
                        onPick = { changeTrack(selectedAudioIndex, it) },
                    )
                    com.xxxx.emby_tv.ui.components.PlayerSheet.Audio -> TrackSheet(
                        title = "音频",
                        tracks = audioTracks,
                        selectedIndex = selectedAudioIndex,
                        offLabel = "默认",
                        onPick = { changeTrack(it, selectedSubtitleIndex) },
                    )
                    com.xxxx.emby_tv.ui.components.PlayerSheet.Versions -> VersionSheet(
                        sources = allMediaSources,
                        selectedId = currentMediaSourceId,
                        onPick = { source ->
                            if (source.id != null && source.id != currentMediaSourceId) {
                                selectedMediaSourceId = source.id
                                source.name?.takeIf { it.isNotBlank() }?.let {
                                    preferencesManager.preferredMediaSourceName = it
                                }
                                selectedAudioIndex = -1
                                selectedSubtitleIndex = -99
                                hasTriedTranscodeFallback = false
                                playbackTrigger++
                            }
                            playerSheet = com.xxxx.emby_tv.ui.components.PlayerSheet.None
                        },
                    )
                    com.xxxx.emby_tv.ui.components.PlayerSheet.Info -> {
                        com.xxxx.emby_tv.ui.components.StreamInfoSheet(streamLines)
                    }
                    com.xxxx.emby_tv.ui.components.PlayerSheet.Settings -> SettingsSheetBody(
                        playbackSpeed = playbackSpeed,
                        onSpeed = {
                            playbackSpeed = it
                            preferencesManager.playbackSpeed = it
                        },
                        subtitleScale = subtitleScale,
                        onScale = {
                            subtitleScale = it
                            preferencesManager.subtitleScale = it
                        },
                        subtitlePadding = subtitleBottomPadding,
                        onPadding = {
                            subtitleBottomPadding = it
                            preferencesManager.subtitleBottomPadding = it
                        },
                        subtitleInk = subtitleInk,
                        onInk = {
                            subtitleInk = it
                            preferencesManager.subtitleInk = it.toArgb()
                        },
                        decodeAudio = decodeAudio,
                        onDecodeAudio = { enabled ->
                            decodeAudio = enabled
                            preferencesManager.decodeToStereo = enabled
                            val url = player.url
                            if (url != null) {
                                armHdr()
                                player.open(
                                url = url,
                                headers = playbackHeaders(),
                                startMs = player.currentPosition,
                                passthrough = com.xxxx.emby_tv.ui.player.AudioPolicy.usePassthrough(
                                    decoderPreference, enabled, playbackSpeed == 1f, audioRecoveryStage, routeAllowsBitstream,
                                ),
                                joc = com.xxxx.emby_tv.ui.player.DolbyPassthrough.wantsJoc(
                                    audioTracks.firstOrNull { it.index == selectedAudioIndex }
                                ),
                                speed = playbackSpeed,
                            )
                            }
                        },
                        decoderPreference = decoderPreference,
                        onDecoderPreference = { next ->
                            decoderPreference = next
                            preferencesManager.decoderPreference = next
                            val url = player.url ?: return@SettingsSheetBody
                            armHdr()
                            player.open(
                                url = url,
                                headers = playbackHeaders(),
                                startMs = player.currentPosition,
                                passthrough = com.xxxx.emby_tv.ui.player.AudioPolicy.usePassthrough(
                                    next, decodeAudio, playbackSpeed == 1f, audioRecoveryStage, routeAllowsBitstream,
                                ),
                                joc = com.xxxx.emby_tv.ui.player.DolbyPassthrough.wantsJoc(
                                    audioTracks.firstOrNull { it.index == selectedAudioIndex }
                                ),
                                speed = playbackSpeed,
                            )
                        },
                        hdrToSdr = hdrToSdr,
                        onHdrToSdr = { enabled ->
                            hdrToSdr = enabled
                            preferencesManager.hdrToSdr = enabled
                            val url = player.url ?: return@SettingsSheetBody
                            armHdr()
                            player.open(
                                url = url,
                                headers = playbackHeaders(),
                                startMs = player.currentPosition,
                                passthrough = bitstreamAllowed(),
                                joc = com.xxxx.emby_tv.ui.player.DolbyPassthrough.wantsJoc(
                                    audioTracks.firstOrNull { it.index == selectedAudioIndex }
                                ),
                                speed = playbackSpeed,
                            )
                        },
                        diagnostics = showDiagnostics,
                        onDiagnostics = {
                            showDiagnostics = it
                            preferencesManager.showDiagnostics = it
                        },
                        audioStatusLine = audioStatusLine,
                        playMode = playMode,
                        onPlayMode = { mode ->
                            playMode = mode
                            preferencesManager.playMode = mode
                            val isSeriesContent = mediaInfo.type == "Episode" || mediaInfo.seriesId != null
                            val key = if (isSeriesContent) "play_mode" else "play_mode_movie"
                            context.getSharedPreferences("emby_tv_prefs", Context.MODE_PRIVATE)
                                .edit().putInt(key, mode).apply()
                        },
                        playbackCorrection = playbackCorrection,
                        onCorrection = {
                            playbackCorrection = it
                            preferencesManager.playbackCorrection = it
                        },
                        onLaunchExternal = { launchExternalPlayer(null) },
                    )
                    else -> Unit
                }
            }

            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        }
    } // 最外层纯黑背景 Box 闭合
}

private fun remoteKey(event: KeyEvent): PlayerKey {
    when (event.nativeKeyEvent.keyCode) {
        android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> return PlayerKey.MediaPlay
        android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> return PlayerKey.MediaPause
        android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> return PlayerKey.MediaPlayPause
        android.view.KeyEvent.KEYCODE_MENU -> return PlayerKey.Down
    }
    return when (event.key) {
        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> PlayerKey.Ok
        Key.DirectionLeft -> PlayerKey.Left
        Key.DirectionRight -> PlayerKey.Right
        Key.DirectionDown -> PlayerKey.Down
        Key.Back -> PlayerKey.Back
        else -> PlayerKey.Other
    }
}

private fun remotePhase(event: KeyEvent): KeyPhase {
    val repeated = event.nativeKeyEvent.repeatCount > 0
    return when {
        event.type == KeyEventType.KeyDown && repeated -> KeyPhase.Repeat
        event.type == KeyEventType.KeyDown -> KeyPhase.Down
        else -> KeyPhase.Up
    }
}

