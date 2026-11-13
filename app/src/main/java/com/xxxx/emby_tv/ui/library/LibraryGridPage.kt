package com.xxxx.emby_tv.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xxxx.emby_tv.Utils
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.ui.home.AmbientWash
import com.xxxx.emby_tv.ui.home.ArtRole
import com.xxxx.emby_tv.ui.home.HomeImage
import com.xxxx.emby_tv.ui.home.HomeStyle
import com.xxxx.emby_tv.ui.home.HomeText
import com.xxxx.emby_tv.ui.motion.StageDock
import com.xxxx.emby_tv.ui.motion.TvFocusHighlight
import com.xxxx.emby_tv.ui.motion.rememberStageScroll
import com.xxxx.emby_tv.ui.viewmodel.LibraryBrowseViewModel
import com.xxxx.emby_tv.util.EmbyPresentation
import kotlinx.coroutines.yield

private val sorts = listOf(
    Triple("更新时间", "DateLastContentAdded", "Descending"),
    Triple("名称", "SortName", "Ascending"),
    Triple("首播日期", "PremiereDate", "Descending"),
)

private val ratings = listOf("G", "PG", "PG-13", "R", "NC-17", "TV-14", "TV-MA")

private data class YearBand(val label: String, val min: Int?, val max: Int?)

private val yearBands = listOf(
    YearBand("2020年代", 2020, 2029),
    YearBand("2010年代", 2010, 2019),
    YearBand("2000年代", 2000, 2009),
    YearBand("更早", null, 1999),
)

@Composable
fun LibraryGridPage(
    libraryName: String,
    itemType: String,
    serverUrl: String,
    viewModel: LibraryBrowseViewModel,
    onOpenItem: (BaseItemDto) -> Unit,
) {
    val items = viewModel.items.orEmpty()
    val rows = items.chunked(6)
    val scroll = rememberScrollState()
    val stage = rememberStageScroll(scroll)
    val filterFocus = remember { FocusRequester() }
    val sortFocus = remember { FocusRequester() }
    val rowFocus = remember(rows.size) { List(rows.size) { FocusRequester() } }
    var gridRow by remember { mutableIntStateOf(0) }
    var gridCol by remember { mutableIntStateOf(0) }
    var showFilter by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var filterPage by remember { mutableStateOf<String?>(null) }
    var washUrl by remember { mutableStateOf<String?>(null) }
    val panelFocus = remember { FocusRequester() }

    LaunchedEffect(rows.size) {
        if (rows.isNotEmpty()) {
            yield()
            withFrameNanos { }
            try {
                rowFocus.getOrNull(gridRow.coerceAtMost(rows.lastIndex))?.requestFocus()
            } catch (_: IllegalStateException) {
            }
        }
    }
    BackHandler(enabled = showFilter || showSort) {
        when {
            filterPage != null -> filterPage = null
            showFilter -> showFilter = false
            else -> showSort = false
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(HomeStyle.background)) {
        val viewport = constraints.maxHeight
        val density = LocalDensity.current
        stage.stagePx = (viewport * 0.26f).toInt().coerceIn(
            with(density) { 96.dp.roundToPx() },
            with(density) { 220.dp.roundToPx() },
        )
        val spacer = with(density) { StageDock.bottomSpacer(viewport, stage.stagePx).toDp() }
        var columnRoot by remember { mutableIntStateOf(0) }
        val washFade = with(density) { 120.dp.roundToPx() }.coerceAtLeast(1)
        AmbientWash(
            url = washUrl,
            strength = if (washUrl.isNullOrBlank()) 0f else 1f,
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
                    .padding(horizontal = 36.dp, vertical = 18.dp)
                    .onGloballyPositioned {
                        val y = (it.positionInRoot().y - columnRoot + scroll.value).toInt()
                        stage.record(0, y)
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeText(
                    text = libraryName,
                    fontSize = 22.sp,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                HeaderPill(text = "筛选", focus = filterFocus, right = sortFocus, down = rowFocus.firstOrNull()) {
                    viewModel.ensureGenres()
                    filterPage = null
                    showFilter = true
                }
                Spacer(Modifier.padding(start = 12.dp))
                val sortLabel = sorts.firstOrNull { it.second == viewModel.sortBy }?.first ?: "更新时间"
                val arrow = if (viewModel.sortOrder == "Descending") "↓" else "↑"
                HeaderPill(
                    text = "排序：$sortLabel $arrow",
                    focus = sortFocus,
                    left = filterFocus,
                    down = rowFocus.firstOrNull(),
                ) { showSort = true }
            }
            if (viewModel.items == null) {
                Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                    HomeText("加载中", 16.sp, HomeStyle.meta)
                }
            } else if (rows.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                    HomeText("没有符合的内容", 16.sp, HomeStyle.meta)
                }
            } else {
                rows.forEachIndexed { index, row ->
                    GridRow(
                        cells = row,
                        selectedCol = if (gridRow == index) gridCol.coerceAtMost(row.lastIndex) else 0,
                        focused = gridRow == index,
                        focusRequester = rowFocus[index],
                        up = if (index == 0) filterFocus else rowFocus[index - 1],
                        down = rowFocus.getOrNull(index + 1),
                        modifier = Modifier.onGloballyPositioned {
                            val y = (it.positionInRoot().y - columnRoot + scroll.value).toInt()
                            stage.record(index + 1, y)
                        },
                        onFocus = {
                            gridRow = index
                            val dest = StageDock.libraryRowOffset(
                                stage.tops.getOrElse(index + 1) { 0 },
                                index,
                                stage.stagePx,
                                scroll.maxValue,
                            )
                            if (index == 0) stage.dock(0) else {
                                stage.dock(index + 1)
                            }
                            if (index >= rows.lastIndex - 1) viewModel.loadMore()
                            dest
                        },
                        onCol = { gridCol = it },
                        onOpen = onOpenItem,
                        serverUrl = serverUrl,
                        onWash = { washUrl = it },
                    )
                }
            }
            Spacer(Modifier.height(spacer))
        }
        if (showFilter) {
            FilterPanel(
                itemType = itemType,
                viewModel = viewModel,
                page = filterPage,
                onPage = { filterPage = it },
                focus = panelFocus,
                onClose = { showFilter = false },
            )
            LaunchedEffect(filterPage) {
                yield()
                try {
                    panelFocus.requestFocus()
                } catch (_: IllegalStateException) {
                }
            }
        }
        if (showSort) {
            SortPanel(viewModel = viewModel, focus = panelFocus, onClose = { showSort = false })
            LaunchedEffect(Unit) {
                yield()
                try {
                    panelFocus.requestFocus()
                } catch (_: IllegalStateException) {
                }
            }
        }
    }
}

@Composable
private fun HeaderPill(
    text: String,
    focus: FocusRequester,
    left: FocusRequester? = null,
    right: FocusRequester? = null,
    down: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color.White else Color(0xFF2C2C2C))
            .focusRequester(focus)
            .focusProperties {
                left?.let { this.left = it }
                right?.let { this.right = it }
                down?.let { this.down = it }
            }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                if (event.key == Key.DirectionCenter || event.key == Key.Enter) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        HomeText(text, 15.sp, if (focused) Color(0xFF161616) else Color.White, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun GridRow(
    cells: List<BaseItemDto>,
    selectedCol: Int,
    focused: Boolean,
    focusRequester: FocusRequester,
    up: FocusRequester?,
    down: FocusRequester?,
    modifier: Modifier,
    onFocus: () -> Int,
    onCol: (Int) -> Unit,
    onOpen: (BaseItemDto) -> Unit,
    serverUrl: String,
    onWash: (String) -> Unit,
) {
    val col = selectedCol.coerceIn(0, cells.lastIndex)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 8.dp)
            .focusRequester(focusRequester)
            .onFocusChanged {
                if (it.isFocused) {
                    onFocus()
                    val item = cells[col]
                    if (serverUrl.isNotBlank()) onWash(Utils.getImageUrl(serverUrl, item, false))
                }
            }
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionRight -> {
                        if (col < cells.lastIndex) onCol(col + 1)
                        true
                    }
                    Key.DirectionLeft -> {
                        if (col > 0) onCol(col - 1) else false
                        col > 0
                    }
                    Key.DirectionDown -> {
                        if (down != null) {
                            onFocus()
                            try {
                                down.requestFocus()
                            } catch (_: IllegalStateException) {
                            }
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionUp -> {
                        if (up != null) {
                            try {
                                up.requestFocus()
                            } catch (_: IllegalStateException) {
                            }
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        onOpen(cells[col])
                        true
                    }
                    else -> false
                }
            },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        cells.forEachIndexed { index, item ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(14.dp)),
                ) {
                    HomeImage(
                        itemId = item.id,
                        fallbackArtId = item.seriesId,
                        url = if (serverUrl.isBlank()) "" else Utils.getImageUrl(serverUrl, item, false),
                        role = ArtRole.Primary,
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (focused && index == col) {
                        TvFocusHighlight(Modifier.matchParentSize(), RoundedCornerShape(14.dp), stroke = HomeStyle.focusStroke)
                    }
                }
                HomeText(
                    item.name.orEmpty(),
                    14.sp,
                    Color.White,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                val years = EmbyPresentation.yearLabel(item)
                if (years.isNotBlank()) {
                    HomeText(
                        years,
                        12.sp,
                        HomeStyle.meta,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        repeat(6 - cells.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun FilterPanel(
    itemType: String,
    viewModel: LibraryBrowseViewModel,
    page: String?,
    onPage: (String?) -> Unit,
    focus: FocusRequester,
    onClose: () -> Unit,
) {
    val statusCount = (if (viewModel.playedFilter != null) 1 else 0) + (if (viewModel.seriesStatus != null) 1 else 0)
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(24.dp)
                .fillMaxWidth(0.34f)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xF0181818))
                .padding(22.dp),
        ) {
            HomeText(if (page == null) "筛选" else page, 26.sp, Color.White, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            if (page == null) {
                FilterRow("状态", "已选 $statusCount 项", true, focus) { onPage("状态") }
                FilterRow("类型", "已选 ${viewModel.chosenGenres.size} 项", true) { onPage("类型") }
                FilterRow("家长分级", "已选 ${if (viewModel.ratingFilter.isNullOrBlank()) 0 else viewModel.ratingFilter!!.split(",").size} 项", true) { onPage("家长分级") }
                val yearCount = if (viewModel.yearMin != null || viewModel.yearMax != null) 1 else 0
                FilterRow("年份", "已选 $yearCount 项", true) { onPage("年份") }
                FilterRow("清除筛选", "", false) {
                    viewModel.clearFilters()
                    onClose()
                }
            } else {
                when (page) {
                    "状态" -> {
                        OptionRow("未观看", viewModel.playedFilter == "IsUnplayed", focus) {
                            viewModel.setPlayed(if (viewModel.playedFilter == "IsUnplayed") null else "IsUnplayed")
                        }
                        OptionRow("已观看", viewModel.playedFilter == "IsPlayed") {
                            viewModel.setPlayed(if (viewModel.playedFilter == "IsPlayed") null else "IsPlayed")
                        }
                        if (itemType.equals("Series", true)) {
                            OptionRow("连载中", viewModel.seriesStatus == "Continuing") {
                                viewModel.chooseSeriesStatus(if (viewModel.seriesStatus == "Continuing") null else "Continuing")
                            }
                            OptionRow("已完结", viewModel.seriesStatus == "Ended") {
                                viewModel.chooseSeriesStatus(if (viewModel.seriesStatus == "Ended") null else "Ended")
                            }
                        }
                    }
                    "类型" -> {
                        val genres = viewModel.genres.orEmpty()
                        if (genres.isEmpty()) {
                            HomeText("正在加载类型", 14.sp, HomeStyle.meta)
                        } else {
                            genres.forEachIndexed { index, genre ->
                                val name = genre.name.orEmpty()
                                OptionRow(name, name in viewModel.chosenGenres, if (index == 0) focus else null) {
                                    viewModel.toggleGenre(name)
                                }
                            }
                        }
                    }
                    "家长分级" -> ratings.forEachIndexed { index, rating ->
                        val selected = viewModel.ratingFilter?.split(",")?.contains(rating) == true
                        OptionRow(rating, selected, if (index == 0) focus else null) {
                            val current = viewModel.ratingFilter?.split(",")?.filter { it.isNotBlank() }?.toMutableSet() ?: mutableSetOf()
                            if (rating in current) current.remove(rating) else current.add(rating)
                            viewModel.setRating(current.takeIf { it.isNotEmpty() }?.joinToString(","))
                        }
                    }
                    "年份" -> yearBands.forEachIndexed { index, band ->
                        val on = viewModel.yearMin == band.min && viewModel.yearMax == band.max
                        OptionRow(band.label, on, if (index == 0) focus else null) {
                            if (on) viewModel.setYears(null, null) else viewModel.setYears(band.min, band.max)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortPanel(
    viewModel: LibraryBrowseViewModel,
    focus: FocusRequester,
    onClose: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(24.dp)
                .fillMaxWidth(0.34f)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xF0181818))
                .padding(22.dp),
        ) {
            HomeText("排序", 26.sp, Color.White, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            sorts.forEachIndexed { index, sort ->
                val on = viewModel.sortBy == sort.second
                OptionRow(sort.first, on, if (index == 0) focus else null) {
                    viewModel.setSort(sort.second, sort.third)
                    onClose()
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    title: String,
    subtitle: String,
    chevron: Boolean,
    focus: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) Color(0xFFE6E6E6) else Color.Transparent)
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            HomeText(title, 18.sp, if (focused) Color(0xFF161616) else Color.White, fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank()) {
                HomeText(subtitle, 13.sp, if (focused) Color(0xFF4A4A4A) else HomeStyle.meta)
            }
        }
        if (chevron) {
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = if (focused) Color(0xFF161616) else Color.White)
        }
    }
}

@Composable
private fun OptionRow(
    title: String,
    selected: Boolean,
    focus: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Color.White else Color.Transparent)
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeText(title, 16.sp, if (focused) Color(0xFF161616) else Color.White)
        if (selected) {
            Spacer(Modifier.weight(1f))
            HomeText("已选", 13.sp, if (focused) Color(0xFF4A4A4A) else HomeStyle.meta)
        }
    }
}
