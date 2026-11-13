package com.xxxx.emby_tv.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.isActive
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import com.xxxx.emby_tv.R
import com.xxxx.emby_tv.Utils
import com.xxxx.emby_tv.util.EmbyPresentation
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.ui.motion.FadeInImage
import com.xxxx.emby_tv.ui.motion.Motion
import com.xxxx.emby_tv.ui.motion.StageDock
import com.xxxx.emby_tv.ui.motion.TvFocusHighlight
import com.xxxx.emby_tv.ui.motion.TvLeadingRow
import com.xxxx.emby_tv.ui.motion.rememberStageScroll
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.math.min

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OfficialHome(
    resumeItems: List<BaseItemDto>,
    libraries: List<BaseItemDto>,
    serverUrl: String,
    onResume: (BaseItemDto) -> Unit,
    onOpenLibrary: (BaseItemDto) -> Unit,
    onOpenItem: (BaseItemDto) -> Unit,
    onSearch: () -> Unit,
    onFavorites: () -> Unit,
    onSettings: () -> Unit,
    onRememberFocus: (rowKey: String, itemId: String) -> Unit = { _, _ -> },
    restoreRowKey: String? = null,
    restoreItemId: String? = null,
    requestInitialFocus: Boolean = true,
    continueWindowStart: Int = 0,
    forceContinueFocus: Boolean = false,
    fontFamily: FontFamily = FontFamily.Default,
    modifier: Modifier = Modifier,
) {
    val searchFocus = remember { FocusRequester() }
    val homeTabFocus = remember { FocusRequester() }
    val favoritesFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }
    val continueFocus = remember { FocusRequester() }
    val libraryFocus = remember { FocusRequester() }
    val posterRows = libraries.filter { library ->
        EmbyPresentation.isPosterRowLibrary(library) && library.latestItems.orEmpty().isNotEmpty()
    }
    val posterFocus = remember(posterRows.size) { List(posterRows.size) { FocusRequester() } }
    val heroFocus = remember { FocusRequester() }
    val heroItem = posterRows.firstNotNullOfOrNull { row ->
        row.latestItems?.firstOrNull { !it.id.isNullOrBlank() }
    } ?: resumeItems.firstOrNull { !it.id.isNullOrBlank() }
        ?: libraries.firstNotNullOfOrNull { it.latestItems?.firstOrNull { item -> !item.id.isNullOrBlank() } }
    val contentFocus = if (resumeItems.isNotEmpty()) continueFocus else libraryFocus
    val menuDown = if (heroItem != null) heroFocus else contentFocus
    var cursor = 1
    val continueIndex = if (resumeItems.isNotEmpty()) cursor++ else -1
    val libraryIndex = if (libraries.isNotEmpty()) cursor++ else -1
    val posterIndex = posterFocus.indices.map { cursor++ }
    fun indexOfId(items: List<BaseItemDto>): Int = items.indexOfFirst { it.id == restoreItemId }.coerceAtLeast(0)
    val posterRestore = posterRows.indexOfFirst { "latest-${it.id}" == restoreRowKey }
    val scroll = rememberScrollState()
    val stage = rememberStageScroll(scroll)
    val scope = rememberCoroutineScope()
    fun focusRow(requester: FocusRequester, index: Int, pin: Boolean) {
        stage.dock(if (pin) 0 else index)
        scope.launch {
            withFrameNanos { }
            try {
                requester.requestFocus()
            } catch (_: IllegalStateException) {
                withFrameNanos { }
                try {
                    requester.requestFocus()
                } catch (_: IllegalStateException) {
                }
            }
        }
    }
    fun restoredIndex(): Int = when (restoreRowKey) {
        "resume" -> continueIndex
        "libraries" -> libraryIndex
        else -> if (posterRestore >= 0) posterIndex.getOrElse(posterRestore) { 0 } else 0
    }

    LaunchedEffect(requestInitialFocus, restoreRowKey, restoreItemId) {
        if (!requestInitialFocus && restoreRowKey == null) return@LaunchedEffect
        yield()
        repeat(2) { withFrameNanos { } }
        val rowIndex = restoredIndex()
        val target = when (restoreRowKey) {
            "search" -> searchFocus
            "favorites" -> favoritesFocus
            "resume" -> continueFocus
            "libraries" -> libraryFocus
            "hero" -> heroFocus
            else -> posterFocus.getOrNull(posterRestore)
                ?: if (heroItem != null && restoreRowKey == null) heroFocus else when {
                    resumeItems.isNotEmpty() -> continueFocus
                    libraries.isNotEmpty() -> libraryFocus
                    else -> homeTabFocus
                }
        }
        if (rowIndex > 0 && restoreRowKey != null && restoreRowKey != "search" && restoreRowKey != "favorites" && restoreRowKey != "hero") {
            stage.jump(rowIndex)
            withFrameNanos { }
        }
        try {
            target.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }

    val keepStill = remember {
        object : BringIntoViewSpec {
            override val scrollAnimationSpec: AnimationSpec<Float> = Motion.pageFloat
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = 0f
        }
    }
    CompositionLocalProvider(LocalHomeFont provides fontFamily, LocalBringIntoViewSpec provides keepStill) {
        BoxWithConstraints(modifier.fillMaxSize().background(HomeStyle.background)) {
            val viewport = constraints.maxHeight
            val density = LocalDensity.current
            stage.stagePx = (viewport * 0.08f).toInt().coerceIn(
                with(density) { 36.dp.roundToPx() },
                with(density) { 84.dp.roundToPx() },
            )
            val heroHeight = with(density) {
                (viewport.toDp() - 176.dp).coerceAtLeast(280.dp)
            }
            val spacer = with(density) { StageDock.bottomSpacer(viewport, stage.stagePx).toDp() }
            var columnRoot by remember { mutableIntStateOf(0) }
            fun Modifier.stageAnchor(index: Int): Modifier = onGloballyPositioned { coords ->
                val y = (coords.positionInRoot().y - columnRoot + scroll.value).toInt()
                stage.record(index, y)
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { columnRoot = it.positionInRoot().y.toInt() }
                    .verticalScroll(scroll),
            ) {
                if (heroItem != null) {
                    HomeHero(
                        item = heroItem,
                        serverUrl = serverUrl,
                        searchFocus = searchFocus,
                        homeTabFocus = homeTabFocus,
                        favoritesFocus = favoritesFocus,
                        settingsFocus = settingsFocus,
                        heroFocus = heroFocus,
                        downRow = contentFocus,
                        onSearch = onSearch,
                        onFavorites = onFavorites,
                        onSettings = onSettings,
                        onOpen = {
                            heroItem.id?.let { onRememberFocus("hero", it) }
                            if (heroItem.type == "Episode") onResume(heroItem) else onOpenItem(heroItem)
                        },
                        onDown = { focusRow(contentFocus, if (continueIndex >= 0) continueIndex else libraryIndex, pin = false) },
                        onUp = { focusRow(homeTabFocus, 0, pin = true) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(heroHeight)
                            .stageAnchor(0),
                    )
                } else {
                    HomeTopBar(
                        searchFocus = searchFocus,
                        homeTabFocus = homeTabFocus,
                        favoritesFocus = favoritesFocus,
                        settingsFocus = settingsFocus,
                        down = menuDown,
                        onSearch = onSearch,
                        onFavorites = onFavorites,
                        onSettings = onSettings,
                        modifier = Modifier.stageAnchor(0),
                    )
                }
                if (resumeItems.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().stageAnchor(continueIndex)) {
                        StageTitle(stringResource(R.string.continue_watching), continueIndex, stage, scroll)
                        TvLeadingRow(
                            items = resumeItems,
                            cardWidth = HomeStyle.continueWidth,
                            cardHeight = HomeStyle.continueHeight,
                            corner = HomeStyle.cardCorner,
                            focusRequester = continueFocus,
                            up = heroFocus,
                            down = if (libraries.isNotEmpty()) libraryFocus else null,
                            initialWindowStart = continueWindowStart,
                            initialIndex = if (restoreRowKey == "resume") indexOfId(resumeItems) else -1,
                            forceFocused = forceContinueFocus,
                            imageUrls = resumeItems.map { thumbUrl(serverUrl, it) },
                            backdropUrls = resumeItems.map { if (serverUrl.isBlank()) "" else Utils.getBackdropUrl(serverUrl, it, 1280) },
                            prefetchRole = ArtRole.Thumb,
                            onActivate = { item ->
                                item.id?.let { onRememberFocus("resume", it) }
                                onResume(item)
                            },
                            onMenu = onSettings,
                            onFocused = { stage.dock(continueIndex) },
                            onExitUp = {
                                if (heroItem != null) focusRow(heroFocus, 0, pin = true)
                                else focusRow(homeTabFocus, 0, pin = true)
                            },
                            onExitDown = {
                                when {
                                    libraryIndex >= 0 -> focusRow(libraryFocus, libraryIndex, pin = false)
                                    posterIndex.isNotEmpty() -> focusRow(posterFocus[0], posterIndex[0], pin = false)
                                }
                            },
                            caption = { item ->
                                CardCaption(cardTitle(item), cardMeta(item), HomeStyle.continueWidth)
                            },
                        ) { item ->
                            ContinueArt(item, serverUrl)
                        }
                    }
                }
                if (libraries.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().stageAnchor(libraryIndex)) {
                        StageTitle(stringResource(R.string.home_libraries), libraryIndex, stage, scroll)
                        TvLeadingRow(
                            items = libraries,
                            cardWidth = HomeStyle.libraryWidth,
                            cardHeight = HomeStyle.libraryHeight,
                            corner = HomeStyle.libraryCorner,
                            focusRequester = libraryFocus,
                            up = if (resumeItems.isNotEmpty()) continueFocus else heroFocus,
                            down = posterFocus.firstOrNull(),
                            initialIndex = if (restoreRowKey == "libraries") indexOfId(libraries) else -1,
                            imageUrls = libraries.map { primaryUrl(serverUrl, it) },
                            prefetchRole = ArtRole.Thumb,
                            onActivate = { item ->
                                item.id?.let { onRememberFocus("libraries", it) }
                                onOpenLibrary(item)
                            },
                            onMenu = onSettings,
                            onFocused = { stage.dock(libraryIndex) },
                            onExitUp = {
                                if (continueIndex >= 0) focusRow(continueFocus, continueIndex, pin = false)
                                else if (heroItem != null) focusRow(heroFocus, 0, pin = true)
                                else focusRow(homeTabFocus, 0, pin = true)
                            },
                            onExitDown = {
                                if (posterIndex.isNotEmpty()) focusRow(posterFocus[0], posterIndex[0], pin = false)
                            },
                            caption = { library ->
                                CardCaption(library.name.orEmpty(), "", HomeStyle.libraryWidth)
                            },
                        ) { library ->
                            LibraryCollage(library, serverUrl)
                        }
                    }
                }
                posterRows.forEachIndexed { index, library ->
                    Column(Modifier.fillMaxWidth().stageAnchor(posterIndex[index])) {
                        StageTitle(library.name.orEmpty(), posterIndex[index], stage, scroll)
                        TvLeadingRow(
                            items = library.latestItems.orEmpty(),
                            cardWidth = HomeStyle.posterWidth,
                            cardHeight = HomeStyle.posterHeight,
                            corner = HomeStyle.cardCorner,
                            focusRequester = posterFocus[index],
                            up = if (index == 0) {
                                if (libraryIndex >= 0) libraryFocus else if (continueIndex >= 0) continueFocus else heroFocus
                            } else {
                                posterFocus[index - 1]
                            },
                            down = posterFocus.getOrNull(index + 1),
                            initialIndex = if (restoreRowKey == "latest-${library.id}") indexOfId(library.latestItems.orEmpty()) else -1,
                            imageUrls = library.latestItems.orEmpty().map { primaryUrl(serverUrl, it) },
                            backdropUrls = library.latestItems.orEmpty().map {
                                if (serverUrl.isBlank()) "" else Utils.getBackdropUrl(serverUrl, it, 1280)
                            },
                            prefetchRole = ArtRole.Primary,
                            onActivate = { item ->
                                item.id?.let { onRememberFocus("latest-${library.id}", it) }
                                onOpenItem(item)
                            },
                            onMenu = onSettings,
                            onFocused = { stage.dock(posterIndex[index]) },
                            onExitUp = {
                                if (index == 0) {
                                    if (libraryIndex >= 0) focusRow(libraryFocus, libraryIndex, pin = false)
                                    else if (continueIndex >= 0) focusRow(continueFocus, continueIndex, pin = false)
                                    else if (heroItem != null) focusRow(heroFocus, 0, pin = true)
                                    else focusRow(homeTabFocus, 0, pin = true)
                                } else {
                                    focusRow(posterFocus[index - 1], posterIndex[index - 1], pin = false)
                                }
                            },
                            onExitDown = {
                                if (index < posterFocus.lastIndex) {
                                    focusRow(posterFocus[index + 1], posterIndex[index + 1], pin = false)
                                }
                            },
                            caption = { item ->
                                CardCaption(
                                    item.name.orEmpty(),
                                    item.productionYear?.toString().orEmpty(),
                                    HomeStyle.posterWidth,
                                )
                            },
                        ) { item ->
                            HomeImage(
                                itemId = item.id,
                                fallbackArtId = item.seriesId,
                                url = primaryUrl(serverUrl, item),
                                role = ArtRole.Primary,
                                contentDescription = item.name,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
                if (resumeItems.isEmpty() && libraries.isEmpty()) {
                    com.xxxx.emby_tv.ui.motion.MotionFade(Modifier.fillMaxWidth()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 80.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            HomeText(
                                text = stringResource(R.string.no_data),
                                fontSize = 18.sp,
                                color = HomeStyle.meta,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(spacer))
            }
        }
    }
}

@Composable
private fun StageTitle(
    text: String,
    index: Int,
    stage: com.xxxx.emby_tv.ui.motion.StageScroll,
    scroll: androidx.compose.foundation.ScrollState,
) {
    val span = with(LocalDensity.current) { 240.dp.roundToPx() }
    HomeText(
        text = text,
        fontSize = HomeStyle.sectionSize,
        color = HomeStyle.title,
        modifier = Modifier
            .padding(start = HomeStyle.gutter, end = HomeStyle.gutter, top = 8.dp, bottom = 6.dp)
            .graphicsLayer {
                val top = stage.tops.getOrElse(index) { 0 }
                val distance = (top - scroll.value) - stage.stagePx
                val scale = StageDock.titleScale(distance, span)
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0.5f)
            },
    )
}

@Composable
private fun HomeHero(
    item: BaseItemDto,
    serverUrl: String,
    searchFocus: FocusRequester,
    homeTabFocus: FocusRequester,
    favoritesFocus: FocusRequester,
    settingsFocus: FocusRequester,
    heroFocus: FocusRequester,
    downRow: FocusRequester,
    onSearch: () -> Unit,
    onFavorites: () -> Unit,
    onSettings: () -> Unit,
    onOpen: () -> Unit,
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backdrop = if (serverUrl.isBlank()) "" else Utils.getBackdropUrl(serverUrl, item, 1280)
    val logo = EmbyPresentation.logoUrl(serverUrl, item, maxWidth = 640)
    Box(modifier) {
        FadeInImage(
            url = backdrop,
            widthPx = 1280,
            heightPx = 720,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(0.72f),
                        0.46f to Color.Black.copy(0.28f),
                        1f to Color.Transparent,
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, HomeStyle.background),
                    ),
                ),
        )
        HomeTopBar(
            searchFocus = searchFocus,
            homeTabFocus = homeTabFocus,
            favoritesFocus = favoritesFocus,
            settingsFocus = settingsFocus,
            down = heroFocus,
            onSearch = onSearch,
            onFavorites = onFavorites,
            onSettings = onSettings,
            modifier = Modifier.align(Alignment.TopStart),
        )
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = HomeStyle.gutter, end = 280.dp, bottom = 24.dp)
                .fillMaxWidth(0.46f)
                .focusRequester(heroFocus)
                .focusProperties {
                    this.up = homeTabFocus
                    this.down = downRow
                }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> {
                            onDown()
                            true
                        }
                        Key.DirectionUp -> {
                            onUp()
                            true
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            onOpen()
                            true
                        }
                        else -> false
                    }
                }
                .focusable()
                .clickable(onClick = onOpen),
        ) {
            if (logo.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(logo)
                        .crossfade(Motion.imageMs)
                        .build(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    modifier = Modifier.height(46.dp).fillMaxWidth(),
                )
            } else {
                HomeText(
                    text = item.name.orEmpty(),
                    fontSize = 28.sp,
                    color = HomeStyle.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
            }
            val meta = heroMeta(item)
            if (meta.isNotBlank()) {
                HomeText(
                    text = meta,
                    fontSize = 14.sp,
                    color = HomeStyle.meta,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            val overview = item.overview?.trim().orEmpty()
            if (overview.isNotBlank()) {
                HomeText(
                    text = overview,
                    fontSize = 15.sp,
                    color = Color(0xFFE4E4E4),
                    maxLines = 2,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

private fun heroMeta(item: BaseItemDto): String {
    val parts = ArrayList<String>()
    val genres = item.genres?.filter { it.isNotBlank() }?.take(2)?.joinToString(" / ").orEmpty()
    if (genres.isNotBlank()) parts += genres
    item.productionYear?.let { parts += it.toString() }
    item.communityRating?.takeIf { it > 0.0 }?.let { rating ->
        val tenths = kotlin.math.round(rating * 10.0).toInt()
        parts += "${tenths / 10}.${kotlin.math.abs(tenths % 10)}"
    }
    return parts.joinToString("  ·  ")
}

@Composable
private fun HomeTopBar(
    searchFocus: FocusRequester,
    homeTabFocus: FocusRequester,
    favoritesFocus: FocusRequester,
    settingsFocus: FocusRequester,
    down: FocusRequester,
    onSearch: () -> Unit,
    onFavorites: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = HomeStyle.gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        EmbyMark()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            NavIconButton(
                focusRequester = searchFocus,
                left = null,
                right = homeTabFocus,
                down = down,
                onClick = onSearch,
                contentDescription = stringResource(R.string.search),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            NavPill(
                text = stringResource(R.string.home_tab),
                selected = true,
                focusRequester = homeTabFocus,
                left = searchFocus,
                right = favoritesFocus,
                down = down,
                onClick = {},
            )
            NavPill(
                text = stringResource(R.string.favorite),
                selected = false,
                focusRequester = favoritesFocus,
                left = homeTabFocus,
                right = settingsFocus,
                down = down,
                onClick = onFavorites,
            )
            NavIconButton(
            focusRequester = settingsFocus,
            left = favoritesFocus,
            right = null,
            down = down,
            onClick = onSettings,
            contentDescription = stringResource(R.string.settings),
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        }
    }
}

@Composable
private fun EmbyMark(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(HomeStyle.logoGreen),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(13.dp).offset(x = 1.dp)) {
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, size.height / 2f)
                lineTo(0f, size.height)
                close()
            }
            drawPath(path, Color.White)
        }
    }
}

@Composable
private fun NavIconButton(
    focusRequester: FocusRequester,
    left: FocusRequester?,
    right: FocusRequester?,
    down: FocusRequester,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    icon: @Composable BoxScope.() -> Unit,
) {
    var focused by rememberFocusState()
    Box(
        modifier
            .size(36.dp)
            .focusRequester(focusRequester)
            .focusProperties {
                left?.let { this.left = it }
                right?.let { this.right = it }
                this.down = down
            }
            .onFocusChanged { focused = it.isFocused }
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        icon()
        if (focused) {
            TvFocusHighlight(Modifier.matchParentSize(), CircleShape, stroke = HomeStyle.focusStroke)
        }
    }
}

@Composable
private fun NavPill(
    text: String,
    selected: Boolean,
    focusRequester: FocusRequester,
    left: FocusRequester?,
    right: FocusRequester?,
    down: FocusRequester,
    onClick: () -> Unit,
) {
    var focused by rememberFocusState()
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .focusRequester(focusRequester)
            .focusProperties {
                left?.let { this.left = it }
                right?.let { this.right = it }
                this.down = down
            }
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(if (selected) HomeStyle.navPill else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        HomeText(
            text = text,
            fontSize = HomeStyle.navSize,
            color = if (selected) HomeStyle.navPillText else Color.White,
            fontWeight = FontWeight.Medium,
        )
        if (focused) {
            TvFocusHighlight(Modifier.matchParentSize(), shape, stroke = HomeStyle.focusStroke)
        }
    }
}

@Composable
private fun ContinueArt(item: BaseItemDto, serverUrl: String) {
    Box(Modifier.fillMaxSize()) {
        HomeImage(
            itemId = item.id,
            fallbackArtId = item.seriesId,
            url = thumbUrl(serverUrl, item),
            role = ArtRole.Thumb,
            contentDescription = cardTitle(item),
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(46.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                    )
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, end = 8.dp, bottom = 7.dp),
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(HomeStyle.chip)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                HomeText(
                    text = stringResource(R.string.continue_playing),
                    fontSize = HomeStyle.chipSize,
                    color = Color.White,
                )
            }
            Spacer(Modifier.height(5.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(HomeStyle.progressTrack),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(item.playbackProgress.coerceIn(0f, 1f))
                        .background(Color.White),
                )
            }
        }
    }
}

@Composable
private fun LibraryCollage(library: BaseItemDto, serverUrl: String) {
    val posters = library.latestItems.orEmpty()
        .filter { hasPrimaryArt(serverUrl, it) }
        .take(4)
    val viewPrimary = EmbyPresentation.viewPrimaryImageUrl(serverUrl, library)
    Box(Modifier.fillMaxSize()) {
        when {
            posters.isNotEmpty() -> {
                Row(Modifier.fillMaxSize()) {
                    posters.forEach { child ->
                        HomeImage(
                            itemId = child.id,
                            fallbackArtId = child.seriesId,
                            url = primaryUrl(serverUrl, child),
                            role = ArtRole.Primary,
                            contentDescription = null,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
            viewPrimary.isNotBlank() || SampleArt.lookup(library.id, ArtRole.Primary) != null -> {
                HomeImage(
                    itemId = library.id,
                    fallbackArtId = null,
                    url = viewPrimary,
                    role = ArtRole.Primary,
                    contentDescription = library.name,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> Box(Modifier.fillMaxSize().background(Color(0xFF2A2422)))
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(28.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                    )
                ),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    HomeText(
        text = text,
        fontSize = HomeStyle.sectionSize,
        color = HomeStyle.title,
        modifier = Modifier.padding(
            start = HomeStyle.gutter,
            end = HomeStyle.gutter,
            top = 8.dp,
            bottom = 6.dp,
        ),
    )
}

@Composable
private fun CardCaption(title: String, meta: String, width: Dp) {
    Column(
        modifier = Modifier
            .width(width)
            .padding(top = 6.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HomeText(
            text = title,
            fontSize = HomeStyle.cardTitle,
            color = HomeStyle.title,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (meta.isNotBlank()) {
            HomeText(
                text = meta,
                fontSize = HomeStyle.metaSize,
                color = HomeStyle.meta,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
fun HomeText(
    text: String,
    fontSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Normal,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = LocalHomeFont.current,
            textAlign = textAlign ?: TextAlign.Unspecified,
            lineHeight = fontSize * 1.25f,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
    )
}

@Composable
fun HomeImage(
    itemId: String?,
    fallbackArtId: String?,
    url: String,
    role: ArtRole,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val artId = when {
        url.isNotBlank() -> null
        SampleArt.lookup(itemId, role) != null -> itemId
        SampleArt.lookup(fallbackArtId, role) != null -> fallbackArtId
        else -> null
    }
    val art = SampleArt.lookup(artId, role)
    val soften = LocalArtworkSoften.current
    val (pxW, pxH) = ArtworkPrefetch.pixels(role)
    when {
        art != null -> SampleArtwork(art, modifier)
        url.isNotBlank() -> FadeInImage(
            url = url,
            widthPx = pxW,
            heightPx = pxH,
            contentDescription = contentDescription,
            modifier = modifier,
            soften = soften,
        )
        else -> Box(modifier.background(Color(0xFF241E1B)))
    }
}

@Composable
private fun rememberFocusState(initial: Boolean = false): androidx.compose.runtime.MutableState<Boolean> {
    return androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(initial) }
}

private fun cardTitle(item: BaseItemDto): String = EmbyPresentation.resumeCardTitle(item)

private fun cardMeta(item: BaseItemDto): String = EmbyPresentation.resumeCardSubtitle(item)

private fun thumbUrl(serverUrl: String, item: BaseItemDto): String {
    return EmbyPresentation.resumeImageUrl(serverUrl, item)
}

private fun primaryUrl(serverUrl: String, item: BaseItemDto): String {
    if (serverUrl.isBlank()) return ""
    return EmbyPresentation.viewPrimaryImageUrl(serverUrl, item).ifBlank {
        Utils.getImageUrl(serverUrl, item, false)
    }
}

private fun hasPrimaryArt(serverUrl: String, item: BaseItemDto): Boolean {
    if (SampleArt.lookup(item.id, ArtRole.Primary) != null) return true
    return primaryUrl(serverUrl, item).isNotBlank()
}
