package com.xxxx.emby_tv.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xxxx.emby_tv.R
import com.xxxx.emby_tv.Utils
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.data.model.RecommendationDto
import com.xxxx.emby_tv.ui.home.ArtworkPrefetch
import com.xxxx.emby_tv.ui.motion.ContentCrossfade
import com.xxxx.emby_tv.ui.motion.RestoreFocus
import com.xxxx.emby_tv.ui.motion.TvFocusHighlight
import com.xxxx.emby_tv.ui.motion.TvLazyGrid
import com.xxxx.emby_tv.ui.motion.TvLeadingRow
import com.xxxx.emby_tv.ui.motion.rememberRowGlide
import com.xxxx.emby_tv.ui.focus.ProvideTvBringIntoView
import com.xxxx.emby_tv.ui.focus.TvBringIntoView
import com.xxxx.emby_tv.ui.focus.scrolledPastStart
import com.xxxx.emby_tv.ui.focus.viewportScroll
import com.xxxx.emby_tv.ui.home.ArtRole
import com.xxxx.emby_tv.ui.home.HomeImage
import com.xxxx.emby_tv.ui.home.HomeStyle
import com.xxxx.emby_tv.ui.home.HomeText
import com.xxxx.emby_tv.ui.home.LocalHomeFont
import com.xxxx.emby_tv.ui.viewmodel.LibraryBrowseViewModel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

enum class LibraryTab { Items, Suggestions, Genres }

@Composable
fun LibraryBrowseConnected(
    parentId: String,
    libraryName: String,
    itemType: String,
    serverUrl: String,
    viewModel: LibraryBrowseViewModel,
    onOpenItem: (BaseItemDto) -> Unit,
) {
    LaunchedEffect(parentId, itemType) {
        viewModel.load(parentId, itemType)
    }
    var returnId by rememberSaveable(parentId) { mutableStateOf<String?>(null) }
    val tabState = remember(parentId) { mutableStateOf(LibraryTab.Items) }
    var tab by tabState
    LaunchedEffect(tab) {
        when (tab) {
            LibraryTab.Suggestions -> viewModel.ensureSuggestions()
            LibraryTab.Genres -> viewModel.ensureGenres()
            LibraryTab.Items -> Unit
        }
    }
    BackHandler(enabled = viewModel.selectedGenre != null) {
        viewModel.clearGenre()
    }
    LibraryGridPage(
        libraryName = libraryName,
        itemType = itemType,
        serverUrl = serverUrl,
        viewModel = viewModel,
        onOpenItem = { item ->
            returnId = item.id
            onOpenItem(item)
        },
    )
}

@Composable
fun LibraryBrowsePage(
    libraryName: String,
    itemType: String,
    tab: LibraryTab,
    onTab: (LibraryTab) -> Unit,
    items: List<BaseItemDto>,
    itemsLoading: Boolean,
    suggestions: List<RecommendationDto>?,
    genres: List<BaseItemDto>?,
    selectedGenre: String?,
    genreItems: List<BaseItemDto>,
    genreLoading: Boolean,
    serverUrl: String,
    restoreItemId: String? = null,
    onOpenItem: (BaseItemDto) -> Unit,
    onOpenGenre: (String) -> Unit,
    onLoadMore: () -> Unit,
    requestInitialFocus: Boolean = true,
    fontFamily: FontFamily = FontFamily.Default,
    modifier: Modifier = Modifier,
) {
    val tabFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }
    val tabs = LibraryTab.entries
    LaunchedEffect(requestInitialFocus, items.firstOrNull()?.id, restoreItemId) {
        if (!requestInitialFocus || restoreItemId != null) return@LaunchedEffect
        yield()
        val target = if (tab == LibraryTab.Items && items.isNotEmpty()) contentFocus else tabFocus
        try {
            target.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }
    CompositionLocalProvider(LocalHomeFont provides fontFamily) {
        Column(
            modifier
                .fillMaxSize()
                .background(HomeStyle.background),
        ) {
            LibraryHeader(
                libraryName = libraryName,
                itemType = itemType,
                selected = tab,
                tabs = tabs,
                tabFocus = tabFocus,
                down = contentFocus,
                onTab = onTab,
            )
            Box(Modifier.fillMaxSize()) {
                ContentCrossfade(targetState = tab, label = "library-tab") { current ->
                when (current) {
                    LibraryTab.Items -> {
                        if (itemsLoading && items.isEmpty()) {
                            BrowseLoading()
                        } else if (items.isEmpty()) {
                            BrowseEmpty()
                        } else {
                            PosterGrid(
                                items = items,
                                serverUrl = serverUrl,
                                firstFocus = contentFocus,
                                up = tabFocus,
                                restoreItemId = restoreItemId,
                                onOpen = onOpenItem,
                                onLoadMore = onLoadMore,
                            )
                        }
                    }
                    LibraryTab.Suggestions -> {
                        when {
                            suggestions == null -> BrowseLoading()
                            suggestions.isEmpty() -> BrowseEmpty()
                            else -> SuggestionRows(
                                rows = suggestions,
                                serverUrl = serverUrl,
                                firstFocus = contentFocus,
                                up = tabFocus,
                                restoreItemId = restoreItemId,
                                onOpen = onOpenItem,
                            )
                        }
                    }
                    LibraryTab.Genres -> {
                        when {
                            genres == null || genreLoading -> BrowseLoading()
                            selectedGenre != null -> {
                                if (genreItems.isEmpty()) BrowseEmpty()
                                else PosterGrid(
                                    items = genreItems,
                                    serverUrl = serverUrl,
                                    firstFocus = contentFocus,
                                    up = tabFocus,
                                    restoreItemId = restoreItemId,
                                    onOpen = onOpenItem,
                                    onLoadMore = onLoadMore,
                                )
                            }
                            genres.isEmpty() -> BrowseEmpty()
                            else -> GenreGrid(
                                genres = genres,
                                firstFocus = contentFocus,
                                up = tabFocus,
                                onOpen = onOpenGenre,
                            )
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(
    libraryName: String,
    itemType: String,
    selected: LibraryTab,
    tabs: List<LibraryTab>,
    tabFocus: FocusRequester,
    down: FocusRequester,
    onTab: (LibraryTab) -> Unit,
) {
    val labels = tabs.map { tabLabel(it, itemType) }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = HomeStyle.gutter, vertical = 8.dp),
    ) {
        HomeText(
            text = libraryName,
            fontSize = 22.sp,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        TabBar(
            labels = labels,
            selected = selected.ordinal.coerceIn(0, tabs.lastIndex),
            focusRequester = tabFocus,
            down = down,
            onSelect = { onTab(tabs[it]) },
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun tabLabel(tab: LibraryTab, itemType: String): String {
    return when (tab) {
        LibraryTab.Items -> if (itemType == "Series") {
            stringResource(R.string.library_tab_series)
        } else {
            stringResource(R.string.library_tab_movies)
        }
        LibraryTab.Suggestions -> stringResource(R.string.library_tab_suggestions)
        LibraryTab.Genres -> stringResource(R.string.library_tab_genres)
    }
}

@Composable
private fun TabBar(
    labels: List<String>,
    selected: Int,
    focusRequester: FocusRequester,
    down: FocusRequester,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var slot by remember(labels) { mutableIntStateOf(selected.coerceIn(0, labels.lastIndex)) }
    var rowFocused by remember { mutableStateOf(false) }
    val active = if (rowFocused) slot else selected
    androidx.compose.foundation.layout.Row(
        modifier
            .focusRequester(focusRequester)
            .focusProperties { this.down = down }
            .onFocusChanged {
                rowFocused = it.isFocused
                if (it.isFocused) slot = selected
            }
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionRight -> {
                        if (slot < labels.lastIndex) {
                            slot++
                            onSelect(slot)
                        }
                        true
                    }
                    Key.DirectionLeft -> {
                        if (slot == 0) false else {
                            slot--
                            onSelect(slot)
                            true
                        }
                    }
                    else -> false
                }
            },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            val on = index == active
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (on) Color.White else Color.Transparent)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                HomeText(
                    text = label,
                    fontSize = 16.sp,
                    color = if (on) Color(0xFF161616) else Color.White,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun PosterGrid(
    items: List<BaseItemDto>,
    serverUrl: String,
    firstFocus: FocusRequester,
    up: FocusRequester,
    onOpen: (BaseItemDto) -> Unit,
    onLoadMore: () -> Unit,
    restoreItemId: String? = null,
) {
    val restoreIndex = items.indexOfFirst { it.id == restoreItemId }
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = if (restoreIndex > 0) (restoreIndex / 6) * 6 else 0,
    )
    val bringIntoView = remember { TvBringIntoView() }
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(12.dp)
    val prefetchContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(gridState, items.size, serverUrl) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last ->
                val urls = (last + 1 until (last + 13).coerceAtMost(items.size)).map { index ->
                    if (serverUrl.isBlank()) "" else Utils.getImageUrl(serverUrl, items[index], false)
                }
                ArtworkPrefetch.enqueue(prefetchContext, urls, ArtRole.Primary)
            }
    }
    RestoreFocus(
        active = restoreIndex >= 0,
        index = restoreIndex,
        requester = firstFocus,
        scrollTo = { gridState.scrollToItem((it / 6) * 6) },
    )
    LaunchedEffect(gridState, items.size) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 8
        }.collect { if (it) onLoadMore() }
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
        items(items.size, key = { items[it].id ?: it }, contentType = { "poster" }) { index ->
            val item = items[index]
            PosterCell(
                item = item,
                serverUrl = serverUrl,
                shape = shape,
                focusRequester = when {
                    restoreIndex >= 0 && index == restoreIndex -> firstFocus
                    restoreIndex < 0 && index == 0 -> firstFocus
                    else -> null
                },
                up = if (index < 6) up else null,
                onOpen = { onOpen(item) },
                onFocused = {
                    if (index < 6) {
                        bringIntoView.pinToStart = true
                        scope.launch {
                            gridState.scrollToItem(0, 0)
                            withFrameNanos { }
                            gridState.scrollToItem(0, 0)
                        }
                    } else {
                        bringIntoView.pinToStart = false
                    }
                },
            )
        }
    }
}

@Composable
private fun PosterCell(
    item: BaseItemDto,
    serverUrl: String,
    shape: Shape,
    focusRequester: FocusRequester?,
    up: FocusRequester?,
    onOpen: () -> Unit,
    onFocused: () -> Unit = {},
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
                    fallbackArtId = item.seriesId,
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
            modifier = Modifier
                .padding(top = 4.dp)
                .fillMaxWidth(),
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

@Composable
private fun SuggestionRows(
    rows: List<RecommendationDto>,
    serverUrl: String,
    firstFocus: FocusRequester,
    up: FocusRequester,
    onOpen: (BaseItemDto) -> Unit,
    restoreItemId: String? = null,
) {
    val visible = rows.filter { !it.items.isNullOrEmpty() }
    val focus = remember(visible.size) { List(visible.size) { FocusRequester() } }
    val restoreRow = if (restoreItemId == null) -1 else visible.indexOfFirst { row ->
        row.items.orEmpty().any { it.id == restoreItemId }
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = restoreRow.coerceAtLeast(0),
    )
    val bringIntoView = remember { TvBringIntoView() }
    val glide = rememberRowGlide(listState, bringIntoView)
    val scope = rememberCoroutineScope()
    val latestLabel = stringResource(R.string.library_recently_added)
    LaunchedEffect(restoreItemId, visible.size) {
        if (restoreRow < 0) return@LaunchedEffect
        listState.scrollToItem(restoreRow)
        withFrameNanos { }
        val requester = if (restoreRow == 0) firstFocus else focus[restoreRow]
        try {
            requester.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }
    fun focusSuggestion(index: Int) {
        val pin = index == 0
        bringIntoView.pinToStart = pin
        val requester = if (index == 0) firstFocus else focus[index]
        glide.to(requester, index, pin)
    }
    ProvideTvBringIntoView(
        controller = bringIntoView,
        viewportScroll = { listState.viewportScroll() },
        scrolledPastStart = { listState.scrolledPastStart() },
    ) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        items(visible.size, key = { index ->
            val row = visible[index]
            (row.recommendationType ?: "") + (row.baselineItemName ?: index.toString())
        }) { index ->
            val row = visible[index]
            val title = if (row.recommendationType == "Latest" || row.baselineItemName.isNullOrBlank()) {
                latestLabel
            } else {
                stringResource(R.string.because_you_watched, row.baselineItemName)
            }
            HomeText(
                text = title,
                fontSize = HomeStyle.sectionSize,
                color = HomeStyle.title,
                maxLines = 1,
                modifier = Modifier.padding(start = HomeStyle.gutter, top = 8.dp, bottom = 6.dp, end = HomeStyle.gutter),
            )
            val cards = row.items.orEmpty()
            TvLeadingRow(
                items = cards,
                cardWidth = 248.dp,
                cardHeight = 140.dp,
                corner = HomeStyle.cardCorner,
                focusRequester = if (index == 0) firstFocus else focus[index],
                up = if (index == 0) up else if (index == 1) firstFocus else focus[index - 1],
                down = focus.getOrNull(index + 1),
                onActivate = onOpen,
                onMenu = {},
                initialIndex = cards.indexOfFirst { it.id == restoreItemId },
                imageUrls = cards.map { if (serverUrl.isBlank()) "" else Utils.getBackdropUrl(serverUrl, it, 800) },
                prefetchRole = ArtRole.Thumb,
                onFocused = { if (index == 0) focusSuggestion(0) },
                onExitUp = {
                    if (index == 0) {
                        bringIntoView.pinToStart = true
                        scope.launch {
                            listState.scrollToItem(0, 0)
                            try {
                                up.requestFocus()
                            } catch (_: IllegalStateException) {
                            }
                        }
                    } else {
                        focusSuggestion(index - 1)
                    }
                },
                onExitDown = {
                    if (index < visible.lastIndex) focusSuggestion(index + 1)
                },
                caption = { item ->
                    Column(
                        Modifier
                            .width(248.dp)
                            .padding(top = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        HomeText(
                            text = item.name.orEmpty(),
                            fontSize = 14.sp,
                            color = Color.White,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
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
                },
            ) { item ->
                HomeImage(
                    itemId = item.id,
                    fallbackArtId = null,
                    url = if (serverUrl.isBlank()) "" else Utils.getBackdropUrl(serverUrl, item, 800),
                    role = ArtRole.Thumb,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
    }
}

@Composable
private fun GenreGrid(
    genres: List<BaseItemDto>,
    firstFocus: FocusRequester,
    up: FocusRequester,
    onOpen: (String) -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(genres.size, key = { genres[it].id ?: genres[it].name ?: it }, contentType = { "genre" }) { index ->
            val genre = genres[index]
            var focused by remember { mutableStateOf(false) }
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                    .focusProperties { if (index < 4) this.up = up }
                    .onFocusChanged { focused = it.isFocused }
                    .focusable()
                    .clip(shape)
                    .background(Color.White.copy(0.08f))
                    .clickable { genre.name?.let(onOpen) }
                    .padding(horizontal = 12.dp, vertical = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                HomeText(
                    text = genre.name.orEmpty(),
                    fontSize = 18.sp,
                    color = Color.White,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
                if (focused) TvFocusHighlight(Modifier.matchParentSize(), shape, stroke = HomeStyle.focusStroke)
            }
        }
    }
}

@Composable
private fun BrowseLoading() {
    com.xxxx.emby_tv.ui.motion.MotionFade(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun BrowseEmpty() {
    com.xxxx.emby_tv.ui.motion.MotionFade(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            HomeText(text = stringResource(R.string.no_data), fontSize = 18.sp, color = HomeStyle.meta)
        }
    }
}
