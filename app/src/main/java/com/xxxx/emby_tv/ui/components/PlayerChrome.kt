package com.xxxx.emby_tv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import com.xxxx.emby_tv.ui.motion.Motion
import com.xxxx.emby_tv.ui.motion.ScreenTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Info
import com.xxxx.emby_tv.ui.player.StreamInfoLine
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.data.model.MediaSourceInfoDto
import com.xxxx.emby_tv.data.model.MediaStreamDto
import com.xxxx.emby_tv.util.EmbyPresentation

enum class PlayerSheet { None, Subtitles, Audio, Versions, Settings }

fun playerLogoUrl(serverUrl: String, item: BaseItemDto): String {
    val own = EmbyPresentation.logoUrl(serverUrl, item, maxWidth = 480)
    if (own.isNotBlank()) return own
    val seriesId = item.seriesId?.takeIf { it.isNotBlank() } ?: return ""
    return "${serverUrl.trimEnd('/')}/emby/Items/$seriesId/Images/Logo?maxWidth=480&quality=90"
}

fun formatPlayerClock(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L).toInt()
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return "%d:%02d:%02d".format(hours, minutes, seconds)
}

fun formatPlayerRemaining(ms: Long): String = "-${formatPlayerClock(ms)}"

/**
 * Step size while L/R is held. A tap is 10s. The ramp follows how long the
 * gesture has been going, not how many repeats arrived. After 3s the step is
 * about 1% of the runtime, kept between 2 and 5 minutes.
 */
fun seekStepMs(heldMs: Long, durationMs: Long): Long = when {
    heldMs < 500L -> 10_000L
    heldMs < 1_500L -> 30_000L
    heldMs < 3_000L -> 60_000L
    else -> (durationMs.coerceAtLeast(0L) / 100L).coerceIn(120_000L, 300_000L)
}

fun formatSeekOffset(deltaMs: Long): String {
    val sign = if (deltaMs >= 0L) "+" else "-"
    return sign + formatPlayerClock(kotlin.math.abs(deltaMs))
}

fun episodeBarLabel(season: Int?, episode: Int?, name: String?): String? {
    if (season == null || episode == null) return name?.takeIf { it.isNotBlank() }
    val code = "S$season:E$episode"
    val title = name?.takeIf { it.isNotBlank() } ?: return code
    return "$code · $title"
}

fun episodeStripMeta(item: BaseItemDto): String {
    val number = item.indexNumber
    val minutes = EmbyPresentation.runtimeMinutes(item.runTimeTicks)
    val head = if (number != null) "第 $number 集" else "剧集"
    return if (minutes == null) head else "$head · ${minutes}m"
}

/**
 * When to offer the next episode. A credits chapter wins; otherwise the last 30 seconds.
 */
fun nextEpisodePromptMs(durationMs: Long, chapters: List<com.xxxx.emby_tv.data.model.ChapterInfo>?): Long {
    val credit = chapters?.firstOrNull { chapter ->
        val marker = chapter.markerType.orEmpty()
        val name = chapter.name.orEmpty()
        marker.equals("CreditsStart", true) ||
            name.contains("片尾") ||
            name.contains("credit", true)
    }
    val fromChapter = credit?.startPositionTicks?.div(10_000)
    val fromEnd = (durationMs - 30_000L).coerceAtLeast(0L)
    return fromChapter ?: fromEnd
}


@Composable
fun PlayerTransportBar(
    visible: Boolean,
    serverUrl: String,
    apiKey: String,
    mediaInfo: BaseItemDto,
    positionMs: Long,
    durationMs: Long,
    showVersionButton: Boolean,
    streamLines: List<StreamInfoLine> = emptyList(),
    episodes: List<BaseItemDto>,
    restoreButton: Int,
    timelineFocus: FocusRequester,
    pictureFocus: FocusRequester,
    buttonFocus: List<FocusRequester>,
    onOpenSheet: (PlayerSheet) -> Unit,
    onOpenEpisodes: () -> Unit,
    onButtonFocused: (Int) -> Unit,
    onMenuFocus: (Int, Boolean) -> Unit,
    onInteract: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEpisode = mediaInfo.type == "Episode" && mediaInfo.seriesId != null
    AnimatedVisibility(
        visible = visible,
        enter = ScreenTransition.barEnter(),
        exit = ScreenTransition.barExit(),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Black.copy(0.28f),
                        1f to Color.Black.copy(0.72f),
                    ),
                )
                .padding(start = 36.dp, end = 28.dp, top = 28.dp, bottom = if (isEpisode) 0.dp else 18.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                LogoOrTitle(serverUrl, mediaInfo)
                if (isEpisode) {
                    val line = episodeBarLabel(
                        mediaInfo.parentIndexNumber,
                        mediaInfo.indexNumber,
                        mediaInfo.name,
                    )
                    if (!line.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(line, color = Color.White.copy(0.92f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(8.dp))
                val infoIndex = TransportSlots.info(showVersionButton)
                val settingsIndex = TransportSlots.settings(showVersionButton)
                var infoFocused by remember { mutableStateOf(false) }
                val relayMenu: (Int, Boolean) -> Unit = { index, focused ->
                    if (index == infoIndex) infoFocused = focused
                    onMenuFocus(index, focused)
                }
                if (infoFocused && streamLines.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        StreamInfoCard(streamLines)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RoundAction(Icons.Default.ClosedCaption, "字幕", onInteract, Modifier.focusRequester(buttonFocus[TransportSlots.SUBTITLES]), pictureFocus, TransportSlots.SUBTITLES, onButtonFocused, relayMenu) {
                            onOpenSheet(PlayerSheet.Subtitles)
                        }
                        RoundAction(Icons.Default.GraphicEq, "音频", onInteract, Modifier.focusRequester(buttonFocus[TransportSlots.AUDIO]), pictureFocus, TransportSlots.AUDIO, onButtonFocused, relayMenu) {
                            onOpenSheet(PlayerSheet.Audio)
                        }
                        if (showVersionButton) {
                            val versionIndex = TransportSlots.version(true)!!
                            RoundAction(Icons.Filled.VideoLibrary, "片源", onInteract, Modifier.focusRequester(buttonFocus[versionIndex]), pictureFocus, versionIndex, onButtonFocused, relayMenu) {
                                onOpenSheet(PlayerSheet.Versions)
                            }
                        }
                        RoundAction(Icons.Outlined.Info, "信息", onInteract, Modifier.focusRequester(buttonFocus[infoIndex]), pictureFocus, infoIndex, onButtonFocused, relayMenu) {}
                        RoundAction(Icons.Default.Settings, "设置", onInteract, Modifier.focusRequester(buttonFocus[settingsIndex]), pictureFocus, settingsIndex, onButtonFocused, relayMenu) {
                            onOpenSheet(PlayerSheet.Settings)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatPlayerClock(positionMs), color = Color.White, fontSize = 14.sp)
                    Spacer(Modifier.width(14.dp))
                    ProgressScrubber(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        focusRequester = timelineFocus,
                        upTarget = buttonFocus[restoreButton.coerceAtLeast(0).coerceAtMost(buttonFocus.lastIndex)],
                        onOpenEpisodes = if (isEpisode) onOpenEpisodes else null,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (isEpisode && episodes.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    EpisodePeek(serverUrl, apiKey, episodes)
                }
            }
        }
    }
}

@Composable
private fun LogoOrTitle(serverUrl: String, item: BaseItemDto) {
    val logo = playerLogoUrl(serverUrl, item)
    var failed by remember(logo) { mutableStateOf(false) }
    val title = item.seriesName?.takeIf { item.type == "Episode" && it.isNotBlank() } ?: item.name.orEmpty()
    if (logo.isNotBlank() && !failed) {
        AsyncImage(
            model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current).data(logo).build(),
            contentDescription = title,
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            onError = { failed = true },
            modifier = Modifier.height(36.dp).widthIn(max = 220.dp),
        )
    } else {
        Text(
            text = title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EpisodePeek(serverUrl: String, apiKey: String, episodes: List<BaseItemDto>) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(22.dp)
            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        episodes.take(8).forEach { episode ->
            AsyncImage(
                model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(EmbyPresentation.episodeStillUrl(serverUrl, episode, apiKey = apiKey, maxWidth = 240))
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(72.dp).height(40.dp).clip(RoundedCornerShape(4.dp)),
            )
        }
    }
}

@Composable
private fun ProgressScrubber(
    positionMs: Long,
    durationMs: Long,
    focusRequester: FocusRequester,
    upTarget: FocusRequester,
    onOpenEpisodes: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val fraction = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    Surface(
        onClick = {},
        modifier = modifier
            .height(28.dp)
            .focusRequester(focusRequester)
            .focusProperties { up = upTarget }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                if (event.key == Key.DirectionDown && onOpenEpisodes != null) {
                    onOpenEpisodes()
                    true
                } else {
                    false
                }
            }
            .onFocusChanged { focused = it.isFocused },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(0.28f)),
            )
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(if (focused) 4.dp else 3.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White),
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(fraction),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .size(if (focused) 12.dp else 0.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
        }
    }
}

@Composable
fun PlayerPauseMark(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.38f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Pause,
            contentDescription = "暂停",
            tint = Color.White,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
fun PlayerSeekReadout(
    targetMs: Long,
    durationMs: Long,
    direction: Int,
    modifier: Modifier = Modifier,
) {
    val readout = seekReadout(targetMs, durationMs, direction)
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.46f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(readout.direction, color = Color.White.copy(alpha = 0.9f), fontSize = SEEK_READOUT_CUE_SP.sp, fontWeight = FontWeight.Medium)
        Text(readout.target, color = Color.White, fontSize = SEEK_READOUT_TARGET_SP.sp, fontWeight = FontWeight.SemiBold)
        Text(readout.total, color = Color.White.copy(alpha = 0.72f), fontSize = SEEK_READOUT_CUE_SP.sp)
    }
}

@Composable
private fun StreamInfoCard(lines: List<StreamInfoLine>) {
    Column(
        Modifier
            .widthIn(max = 360.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xF223262C))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        lines.forEach { line ->
            Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(line.label, color = Color.White.copy(0.62f), fontSize = 12.sp, modifier = Modifier.width(48.dp))
                Text(line.value, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun RoundAction(
    icon: ImageVector,
    label: String,
    onInteract: () -> Unit,
    modifier: Modifier = Modifier,
    downTarget: FocusRequester,
    index: Int,
    onFocused: (Int) -> Unit,
    onMenuFocus: (Int, Boolean) -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        onClick = {
            onInteract()
            onClick()
        },
        modifier = modifier
            .size(40.dp)
            .focusProperties { down = downTarget }
            .onFocusChanged {
                if (it.isFocused) onFocused(index)
                onMenuFocus(index, it.isFocused)
            },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(0.34f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerSideSheet(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = ScreenTransition.sheetEnter(),
        exit = ScreenTransition.sheetExit(),
        modifier = modifier,
    ) {
        val sheetFocus = remember { FocusRequester() }
        LaunchedEffect(visible) {
            if (!visible) return@LaunchedEffect
            kotlinx.coroutines.delay(80)
            try {
                sheetFocus.requestFocus()
            } catch (_: Exception) {
            }
        }
        Box(
            Modifier
                .padding(end = 28.dp, top = 48.dp, bottom = 48.dp)
                .width(340.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xF223262C)),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .focusRequester(sheetFocus)
                    .focusGroup()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
            ) {
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}

@Composable
fun SheetRow(
    title: String,
    value: String? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(0.14f) else Color.Transparent,
            contentColor = Color.White.copy(0.92f),
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF141414),
        ),
    ) {
        val titleColor = if (focused) Color(0xFF141414) else Color.White.copy(0.92f)
        val valueColor = if (focused) Color(0xFF141414).copy(0.72f) else Color.White.copy(0.72f)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = titleColor, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!value.isNullOrBlank()) {
                Spacer(Modifier.width(12.dp))
                Text(value, color = valueColor, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun TrackSheet(
    title: String,
    tracks: List<MediaStreamDto>,
    selectedIndex: Int,
    offLabel: String,
    onPick: (Int) -> Unit,
) {
    LazyColumn {
        item {
            SheetRow(offLabel, selected = selectedIndex < 0) { onPick(-1) }
        }
        items(tracks) { track ->
            val index = track.index ?: -1
            val label = track.displayTitle?.takeIf { it.isNotBlank() }
                ?: listOfNotNull(track.language, track.codec).joinToString(" · ").ifBlank { "音轨 $index" }
            SheetRow(label, selected = index == selectedIndex) { onPick(index) }
        }
    }
}

@Composable
fun SettingsSheetBody(
    playbackSpeed: Float,
    onSpeed: (Float) -> Unit,
    subtitleScale: Float,
    onScale: (Float) -> Unit,
    subtitlePadding: Float,
    onPadding: (Float) -> Unit,
    subtitleInk: Color,
    onInk: (Color) -> Unit,
    decodeAudio: Boolean,
    onDecodeAudio: (Boolean) -> Unit,
    decoderPreference: Int = 0,
    onDecoderPreference: (Int) -> Unit = {},
    hdrToSdr: Boolean = false,
    onHdrToSdr: (Boolean) -> Unit = {},
    diagnostics: Boolean,
    onDiagnostics: (Boolean) -> Unit,
    audioStatusLine: String,
    playMode: Int,
    onPlayMode: (Int) -> Unit,
    playbackCorrection: Int,
    onCorrection: (Int) -> Unit,
    onLaunchExternal: () -> Unit,
) {
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    val scales = listOf(0.8f to "小", 1f to "标准", 1.25f to "大")
    val pads = listOf(0.04f to "偏低", 0.08f to "标准", 0.16f to "偏高")
    val inks = listOf(Color.White to "白色", Color(0xFFFFE14A) to "黄色", Color(0xFF7FDBFF) to "青色")
    val modes = listOf("列表循环", "单集循环", "不循环")
    LazyColumn {
        item {
            SheetRow("播放速度", speeds.minBy { kotlin.math.abs(it - playbackSpeed) }.let { if (it == 1f) "1x" else "${it}x" }) {
                val index = speeds.indexOfFirst { kotlin.math.abs(it - playbackSpeed) < 0.01f }.let { if (it < 0) 2 else it }
                onSpeed(speeds[(index + 1) % speeds.size])
            }
        }
        item {
            val label = scales.minBy { kotlin.math.abs(it.first - subtitleScale) }.second
            SheetRow("字幕字号", label) {
                val index = scales.indexOfFirst { kotlin.math.abs(it.first - subtitleScale) < 0.01f }.let { if (it < 0) 1 else it }
                onScale(scales[(index + 1) % scales.size].first)
            }
        }
        item {
            val label = pads.minBy { kotlin.math.abs(it.first - subtitlePadding) }.second
            SheetRow("字幕位置", label) {
                val index = pads.indexOfFirst { kotlin.math.abs(it.first - subtitlePadding) < 0.01f }.let { if (it < 0) 1 else it }
                onPadding(pads[(index + 1) % pads.size].first)
            }
        }
        item {
            val label = when (subtitleInk) {
                Color(0xFFFFE14A) -> "黄色"
                Color(0xFF7FDBFF) -> "青色"
                else -> "白色"
            }
            SheetRow("字幕颜色", label) {
                val index = inks.indexOfFirst { it.second == label }.let { if (it < 0) 0 else it }
                onInk(inks[(index + 1) % inks.size].first)
            }
        }
        item {
            Text(
                "仅影响 SRT/VTT 等文字字幕",
                color = Color.White.copy(0.72f),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 14.dp, top = 2.dp, bottom = 8.dp),
            )
        }
        item {
            SheetRow(
                "解码为立体声",
                if (decodeAudio) "开" else "自动",
            ) { onDecodeAudio(!decodeAudio) }
            Text(
                "自动时 HDMI 能收杜比才透传，电视扬声器解码",
                color = Color.White.copy(0.72f),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 14.dp, bottom = 6.dp),
            )
        }
        item {
            SheetRow(
                "音频解码",
                if (decoderPreference == 1) "优先 FFmpeg" else "自动",
            ) { onDecoderPreference(if (decoderPreference == 1) 0 else 1) }
            Text(
                "优先 FFmpeg 与「解码为立体声」走同一条立体声解码",
                color = Color.White.copy(0.72f),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 14.dp, bottom = 6.dp),
            )
        }
        item {
            SheetRow("HDR-SDR", if (hdrToSdr) "开" else "关") { onHdrToSdr(!hdrToSdr) }
            Text(
                "关：不映射，硬解直出。开：优先硬解映射，不行再用着色器",
                color = Color.White.copy(0.72f),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 14.dp, bottom = 6.dp),
            )
        }
        item {
            SheetRow("播放诊断叠层", if (diagnostics) "开" else "关") { onDiagnostics(!diagnostics) }
            if (diagnostics && audioStatusLine.isNotBlank()) {
                Text(
                    audioStatusLine,
                    color = Color.White.copy(0.8f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 14.dp, bottom = 8.dp),
                )
            }
        }
        item {
            Spacer(Modifier.height(10.dp))
            Text("更多", color = Color.White.copy(0.72f), fontSize = 12.sp, modifier = Modifier.padding(start = 14.dp, bottom = 4.dp))
        }
        item {
            SheetRow("播放模式", modes.getOrElse(playMode) { modes[0] }) {
                onPlayMode((playMode + 1) % modes.size)
            }
        }
        item {
            SheetRow("播放校正", if (playbackCorrection == 1) "转码" else "关闭") {
                onCorrection(if (playbackCorrection == 1) 0 else 1)
            }
        }
        item {
            SheetRow("外部播放器", null) { onLaunchExternal() }
        }
    }
}

@Composable
fun VersionSheet(
    sources: List<MediaSourceInfoDto>,
    selectedId: String?,
    onPick: (MediaSourceInfoDto) -> Unit,
) {
    LazyColumn {
        items(sources) { source ->
            SheetRow(
                title = EmbyPresentation.versionLabel(source),
                value = EmbyPresentation.versionDetail(source).takeIf { it.isNotBlank() },
                selected = source.id != null && source.id == selectedId,
            ) { onPick(source) }
        }
    }
}

@Composable
fun EpisodeStrip(
    visible: Boolean,
    serverUrl: String,
    apiKey: String,
    episodes: List<BaseItemDto>,
    currentId: String,
    progressFocus: FocusRequester,
    onPlay: (BaseItemDto) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = episodes.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    val requesters = remember(episodes.size) { List(episodes.size) { FocusRequester() } }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    AnimatedVisibility(
        visible = visible,
        enter = ScreenTransition.stripEnter(),
        exit = ScreenTransition.stripExit(),
        modifier = modifier,
    ) {
        LaunchedEffect(current, episodes.size) {
            if (episodes.isEmpty()) return@LaunchedEffect
            listState.scrollToItem((current - 1).coerceAtLeast(0))
            kotlinx.coroutines.delay(60)
            try {
                requesters.getOrNull(current)?.requestFocus()
            } catch (_: Exception) {
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(0.82f)),
                    ),
                )
                .padding(start = 36.dp, end = 36.dp, top = 18.dp, bottom = 16.dp),
        ) {
            androidx.compose.foundation.lazy.LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(episodes.size) { index ->
                    val episode = episodes[index]
                    EpisodeCard(
                        serverUrl = serverUrl,
                        apiKey = apiKey,
                        episode = episode,
                        playing = episode.id == currentId,
                        focusRequester = requesters[index],
                        up = progressFocus,
                        onPlay = { onPlay(episode) },
                        onUp = onClose,
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeCard(
    serverUrl: String,
    apiKey: String,
    episode: BaseItemDto,
    playing: Boolean,
    focusRequester: FocusRequester,
    up: FocusRequester,
    onPlay: () -> Unit,
    onUp: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(168.dp)) {
        Surface(
            onClick = onPlay,
            modifier = Modifier
                .focusRequester(focusRequester)
                .focusProperties { this.up = up }
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                        onUp()
                        true
                    } else {
                        false
                    }
                }
                .onFocusChanged { focused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
            border = ClickableSurfaceDefaults.border(
                border = androidx.tv.material3.Border(
                    border = androidx.compose.foundation.BorderStroke(if (focused || playing) 2.dp else 0.dp, Color.White),
                ),
                focusedBorder = androidx.tv.material3.Border(
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                ),
            ),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
            ),
        ) {
            Box(Modifier.fillMaxWidth().height(94.dp)) {
                AsyncImage(
                    model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(EmbyPresentation.episodeStillUrl(serverUrl, episode, apiKey = apiKey, maxWidth = 400))
                        .build(),
                    contentDescription = episode.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (playing) {
                    Text(
                        "正在播放",
                        color = Color.Black,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White)
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(episode.name.orEmpty(), color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(episodeStripMeta(episode), color = Color.White.copy(0.72f), fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
fun NextEpisodeCard(
    visible: Boolean,
    episode: BaseItemDto?,
    countdownSec: Int,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible && episode != null,
        enter = ScreenTransition.cardEnter(),
        exit = ScreenTransition.cardExit(),
        modifier = modifier,
    ) {
        val item = episode ?: return@AnimatedVisibility
        Surface(
            onClick = onPlay,
            modifier = Modifier.width(220.dp),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0xE623262C),
                contentColor = Color.White,
                focusedContainerColor = Color.White,
                focusedContentColor = Color(0xFF141414),
            ),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("下一集", fontSize = 12.sp)
                Text(item.name.orEmpty(), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(episodeStripMeta(item), fontSize = 12.sp)
                Text("${countdownSec}s", fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
