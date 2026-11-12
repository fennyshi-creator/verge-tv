package com.xxxx.emby_tv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.ColorFilter
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

@Composable
fun PlayerTransportBar(
    visible: Boolean,
    serverUrl: String,
    mediaInfo: BaseItemDto,
    positionMs: Long,
    durationMs: Long,
    showVersionButton: Boolean,
    onSeekBy: (Long) -> Unit,
    onTogglePause: () -> Unit,
    onOpenSheet: (PlayerSheet) -> Unit,
    onInteract: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progressFocus = remember { FocusRequester() }
    val iconFocus = remember { FocusRequester() }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 3 },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { it / 3 },
        modifier = modifier,
    ) {
        LaunchedEffect(Unit) {
            try {
                progressFocus.requestFocus()
            } catch (_: Exception) {
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to Color.Black.copy(0.35f),
                        1f to Color.Black.copy(0.78f),
                    ),
                )
                .padding(start = 36.dp, end = 28.dp, top = 36.dp, bottom = 22.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                LogoOrTitle(serverUrl, mediaInfo)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatPlayerClock(positionMs), color = Color.White, fontSize = 13.sp)
                    Spacer(Modifier.width(12.dp))
                    ProgressScrubber(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        focusRequester = progressFocus,
                        downTarget = iconFocus,
                        onSeekBy = {
                            onInteract()
                            onSeekBy(it)
                        },
                        onTogglePause = {
                            onInteract()
                            onTogglePause()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        formatPlayerRemaining((durationMs - positionMs).coerceAtLeast(0L)),
                        color = Color.White.copy(0.9f),
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.width(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RoundAction(Icons.Default.ClosedCaption, "字幕", onInteract, Modifier.focusRequester(iconFocus)) {
                            onOpenSheet(PlayerSheet.Subtitles)
                        }
                        RoundAction(Icons.Default.GraphicEq, "音频", onInteract) { onOpenSheet(PlayerSheet.Audio) }
                        if (showVersionButton) {
                            RoundAction(Icons.Default.VideoLibrary, "片源", onInteract) { onOpenSheet(PlayerSheet.Versions) }
                        }
                        RoundAction(Icons.Default.Settings, "设置", onInteract) { onOpenSheet(PlayerSheet.Settings) }
                    }
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
            colorFilter = ColorFilter.tint(Color.White),
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
private fun ProgressScrubber(
    positionMs: Long,
    durationMs: Long,
    focusRequester: FocusRequester,
    downTarget: FocusRequester,
    onSeekBy: (Long) -> Unit,
    onTogglePause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val fraction = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    Surface(
        onClick = onTogglePause,
        modifier = modifier
            .height(28.dp)
            .focusRequester(focusRequester)
            .focusProperties { down = downTarget }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        onSeekBy(-10_000L)
                        true
                    }
                    Key.DirectionRight -> {
                        onSeekBy(10_000L)
                        true
                    }
                    else -> false
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
        Box(Modifier.fillMaxSize().padding(vertical = 10.dp)) {
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
private fun RoundAction(
    icon: ImageVector,
    label: String,
    onInteract: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = {
            onInteract()
            onClick()
        },
        modifier = modifier.size(40.dp),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(0.16f),
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
        enter = fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it / 4 },
        exit = fadeOut(tween(140)) + slideOutHorizontally(tween(180)) { it / 4 },
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
                .background(Color(0xE61A1C1F)),
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
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(0.12f) else Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!value.isNullOrBlank()) {
                Spacer(Modifier.width(12.dp))
                Text(value, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                color = Color.White.copy(0.55f),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 14.dp, top = 2.dp, bottom = 8.dp),
            )
        }
        item {
            SheetRow(
                "优先使用 FFmpeg 音频解码",
                if (decodeAudio) "开" else "关",
            ) { onDecodeAudio(!decodeAudio) }
            Text(
                "开启后关闭音频直通",
                color = Color.White.copy(0.55f),
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
            Text("更多", color = Color.White.copy(0.55f), fontSize = 12.sp, modifier = Modifier.padding(start = 14.dp, bottom = 4.dp))
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
