package com.xxxx.emby_tv.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.ui.focus.navigationSound
import com.xxxx.emby_tv.ui.home.AmbientWash
import com.xxxx.emby_tv.ui.home.ArtworkWash
import com.xxxx.emby_tv.ui.home.HomeStyle
import com.xxxx.emby_tv.ui.home.HomeText
import com.xxxx.emby_tv.ui.motion.ProvideStageScroll
import com.xxxx.emby_tv.ui.motion.StageDock
import com.xxxx.emby_tv.ui.motion.rememberStageScroll
import com.xxxx.emby_tv.util.EmbyPresentation
import com.xxxx.emby_tv.util.LibraryFocus
import kotlinx.coroutines.yield

@Composable
fun CollectionListPage(
    title: String,
    serverUrl: String,
    items: List<BaseItemDto>?,
    onOpenItem: (BaseItemDto) -> Unit,
) {
    var byYear by rememberSaveable { mutableStateOf(false) }
    var restoreId by rememberSaveable { mutableStateOf<String?>(null) }
    var washUrl by remember { mutableStateOf<String?>(null) }
    val shown = items?.let { EmbyPresentation.sortFavorites(it, byYear) }.orEmpty()
    val rows = shown.chunked(6)
    val scroll = rememberScrollState()
    val stage = rememberStageScroll(scroll)
    val sortFocus = remember { FocusRequester() }
    val rowFocus = remember(rows.size) { List(rows.size) { FocusRequester() } }
    var gridRow by rememberSaveable { mutableIntStateOf(0) }
    var gridCol by rememberSaveable { mutableIntStateOf(0) }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(shown.map { it.id }, restoreId) {
        if (rows.isEmpty() || placed) return@LaunchedEffect
        val restoreIndex = if (restoreId == null) -1 else shown.indexOfFirst { it.id == restoreId }
        if (restoreId != null && restoreIndex < 0) return@LaunchedEffect
        if (restoreIndex >= 0) {
            val cell = LibraryFocus.cell(restoreIndex, 6)
            gridRow = cell.first
            gridCol = cell.second
        }
        placed = true
        yield()
        withFrameNanos { }
        try {
            if (restoreIndex >= 0) rowFocus.getOrNull(gridRow)?.requestFocus() else sortFocus.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }
    ProvideStageScroll {
        BoxWithConstraints(Modifier.fillMaxSize().background(HomeStyle.background)) {
            val viewport = constraints.maxHeight
            val density = LocalDensity.current
            stage.stagePx = (viewport * 0.26f).toInt().coerceIn(
                with(density) { 96.dp.roundToPx() },
                with(density) { 220.dp.roundToPx() },
            )
            val spacer = with(density) { StageDock.bottomSpacer(viewport, stage.stagePx).toDp() }
            var columnRoot by remember { mutableIntStateOf(0) }
            AmbientWash(
                url = washUrl,
                strength = ArtworkWash.strength(onHero = false, hasArtwork = !washUrl.isNullOrBlank()),
                modifier = Modifier.fillMaxSize(),
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { columnRoot = it.positionInRoot().y.toInt() }
                    .verticalScroll(scroll),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 36.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HomeText(
                        text = title.ifBlank { "专题影单" },
                        fontSize = 22.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    CollectionSortPill(
                        label = if (byYear) "排序：年份 ↓" else "排序：名称",
                        focus = sortFocus,
                        down = rowFocus.firstOrNull(),
                        onClick = { byYear = !byYear },
                    )
                }
                when {
                    items == null -> HomeText("加载中", 16.sp, HomeStyle.meta, modifier = Modifier.padding(start = 36.dp, top = 48.dp))
                    rows.isEmpty() -> HomeText("没有专题", 16.sp, HomeStyle.meta, modifier = Modifier.padding(start = 36.dp, top = 48.dp))
                    else -> rows.forEachIndexed { index, row ->
                        LibraryPosterRow(
                            cells = row,
                            selectedCol = if (gridRow == index) gridCol.coerceAtMost(row.lastIndex) else 0,
                            focused = gridRow == index,
                            focusRequester = rowFocus[index],
                            up = if (index == 0) sortFocus else rowFocus[index - 1],
                            down = rowFocus.getOrNull(index + 1),
                            modifier = Modifier.onGloballyPositioned {
                                val y = (it.positionInRoot().y - columnRoot + scroll.value).toInt()
                                stage.record(index + 1, y)
                            },
                            onArmRepeat = { stage.armRepeat(it) },
                            onFocus = {
                                gridRow = index
                                if (index == 0) stage.dock(0) else stage.dock(index + 1)
                                0
                            },
                            onCol = { gridCol = it },
                            onOpen = { item ->
                                restoreId = item.id
                                onOpenItem(item)
                            },
                            serverUrl = serverUrl,
                            onWash = { washUrl = it },
                        )
                    }
                }
                Spacer(Modifier.height(spacer))
            }
        }
    }
}

@Composable
private fun CollectionSortPill(
    label: String,
    focus: FocusRequester,
    down: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.08f))
            .focusRequester(focus)
            .focusProperties { down?.let { this.down = it } }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .navigationSound()
            .onKeyEvent { event ->
                if (LibraryActivation.opensOn(event.type, event.key)) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        HomeText(
            text = label,
            fontSize = 16.sp,
            color = if (focused) Color(0xFF161616) else Color.White,
            fontWeight = FontWeight.Medium,
        )
    }
}
