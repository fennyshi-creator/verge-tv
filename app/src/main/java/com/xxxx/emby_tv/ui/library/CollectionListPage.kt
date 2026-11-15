package com.xxxx.emby_tv.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xxxx.emby_tv.Utils
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.ui.focus.TvBringIntoView
import com.xxxx.emby_tv.ui.home.AmbientWash
import com.xxxx.emby_tv.ui.home.ArtRole
import com.xxxx.emby_tv.ui.home.ArtworkWash
import com.xxxx.emby_tv.ui.home.HomeImage
import com.xxxx.emby_tv.ui.home.HomeStyle
import com.xxxx.emby_tv.ui.home.HomeText
import com.xxxx.emby_tv.ui.motion.TvFocusHighlight
import com.xxxx.emby_tv.ui.motion.TvLazyGrid
import com.xxxx.emby_tv.util.EmbyPresentation
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
    val sortFocus = remember { FocusRequester() }
    val firstFocus = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    val bringIntoView = remember { TvBringIntoView() }
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.fillMaxSize().background(HomeStyle.background)) {
        AmbientWash(
            url = washUrl,
            strength = ArtworkWash.strength(onHero = false, hasArtwork = !washUrl.isNullOrBlank()),
            modifier = Modifier.fillMaxSize(),
        )
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = HomeStyle.gutter, vertical = 18.dp),
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
                    down = if (shown.isEmpty()) null else firstFocus,
                    onClick = { byYear = !byYear },
                )
            }
            when {
                items == null -> CollectionMessage("加载中")
                shown.isEmpty() -> CollectionMessage("没有专题")
                else -> {
                    val restoreIndex = shown.indexOfFirst { it.id == restoreId }
                    LaunchedEffect(shown.map { it.id }, restoreId) {
                        if (restoreIndex >= 0) gridState.scrollToItem(restoreIndex)
                        yield()
                        try {
                            firstFocus.requestFocus()
                        } catch (_: IllegalStateException) {
                        }
                    }
                    TvLazyGrid(
                        columns = GridCells.Fixed(6),
                        state = gridState,
                        bringIntoView = bringIntoView,
                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(shown, key = { index, item -> item.id ?: index }) { index, item ->
                            CollectionPoster(
                                item = item,
                                serverUrl = serverUrl,
                                shape = shape,
                                focusRequester = when {
                                    restoreIndex >= 0 && index == restoreIndex -> firstFocus
                                    restoreIndex < 0 && index == 0 -> firstFocus
                                    else -> null
                                },
                                up = if (index < 6) sortFocus else null,
                                onOpen = {
                                    restoreId = item.id
                                    onOpenItem(item)
                                },
                                onFocused = {
                                    washUrl = if (serverUrl.isBlank()) null
                                    else Utils.getImageUrl(serverUrl, item, false)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionMessage(text: String) {
    Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
        HomeText(text, 16.sp, HomeStyle.meta)
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
            .clickable(onClick = onClick)
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

@Composable
private fun CollectionPoster(
    item: BaseItemDto,
    serverUrl: String,
    shape: RoundedCornerShape,
    focusRequester: FocusRequester?,
    up: FocusRequester?,
    onOpen: () -> Unit,
    onFocused: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .focusProperties { up?.let { this.up = it } }
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }
                .focusable()
                .clickable(onClick = onOpen),
        ) {
            Box(Modifier.fillMaxSize().clip(shape)) {
                HomeImage(
                    itemId = item.id,
                    fallbackArtId = null,
                    url = if (serverUrl.isBlank()) "" else Utils.getImageUrl(serverUrl, item, false),
                    role = ArtRole.Primary,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (focused) TvFocusHighlight(Modifier.matchParentSize(), shape, stroke = HomeStyle.focusStroke)
        }
        HomeText(
            text = item.name.orEmpty(),
            fontSize = 14.sp,
            color = Color.White,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
        )
        val year = item.productionYear?.toString().orEmpty()
        if (year.isNotBlank()) {
            HomeText(
                text = year,
                fontSize = 12.sp,
                color = HomeStyle.meta,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
