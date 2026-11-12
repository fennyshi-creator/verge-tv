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
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import kotlinx.coroutines.yield
import kotlin.math.min

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
    val contentFocus = if (resumeItems.isNotEmpty()) continueFocus else libraryFocus
    val belowLibraries = posterFocus.firstOrNull()

    LaunchedEffect(requestInitialFocus, resumeItems.firstOrNull()?.id, libraries.firstOrNull()?.id) {
        if (!requestInitialFocus) return@LaunchedEffect
        yield()
        val target = when {
            resumeItems.isNotEmpty() -> continueFocus
            libraries.isNotEmpty() -> libraryFocus
            else -> homeTabFocus
        }
        try {
            target.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }

    CompositionLocalProvider(LocalHomeFont provides fontFamily) {
        Box(modifier.fillMaxSize().background(HomeStyle.background)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 36.dp),
            ) {
                item(key = "top-bar") {
                    HomeTopBar(
                        searchFocus = searchFocus,
                        homeTabFocus = homeTabFocus,
                        favoritesFocus = favoritesFocus,
                        settingsFocus = settingsFocus,
                        down = contentFocus,
                        onSearch = onSearch,
                        onFavorites = onFavorites,
                        onSettings = onSettings,
                    )
                }
                if (resumeItems.isNotEmpty()) {
                    item(key = "resume") {
                        SectionTitle(stringResource(R.string.continue_watching))
                        LeadingSlotRow(
                            items = resumeItems,
                            cardWidth = HomeStyle.continueWidth,
                            cardHeight = HomeStyle.continueHeight,
                            corner = HomeStyle.cardCorner,
                            focusRequester = continueFocus,
                            up = homeTabFocus,
                            down = if (libraries.isNotEmpty()) libraryFocus else null,
                            initialWindowStart = continueWindowStart,
                            forceFocused = forceContinueFocus,
                            onActivate = onResume,
                            onMenu = onSettings,
                            caption = { item ->
                                CardCaption(cardTitle(item), cardMeta(item), HomeStyle.continueWidth)
                            },
                        ) { item ->
                            ContinueArt(item, serverUrl)
                        }
                    }
                }
                if (libraries.isNotEmpty()) {
                    item(key = "libraries") {
                        SectionTitle(stringResource(R.string.home_libraries))
                        LeadingSlotRow(
                            items = libraries,
                            cardWidth = HomeStyle.libraryWidth,
                            cardHeight = HomeStyle.libraryHeight,
                            corner = HomeStyle.libraryCorner,
                            focusRequester = libraryFocus,
                            up = if (resumeItems.isNotEmpty()) continueFocus else homeTabFocus,
                            down = belowLibraries,
                            onActivate = onOpenLibrary,
                            onMenu = onSettings,
                            caption = { library ->
                                CardCaption(library.name.orEmpty(), "", HomeStyle.libraryWidth)
                            },
                        ) { library ->
                            LibraryCollage(library, serverUrl)
                        }
                    }
                }
                posterRows.forEachIndexed { index, library ->
                    item(key = "latest-${library.id ?: index}") {
                        SectionTitle(library.name.orEmpty())
                        LeadingSlotRow(
                            items = library.latestItems.orEmpty(),
                            cardWidth = HomeStyle.posterWidth,
                            cardHeight = HomeStyle.posterHeight,
                            corner = HomeStyle.cardCorner,
                            focusRequester = posterFocus[index],
                            up = if (index == 0) libraryFocus else posterFocus[index - 1],
                            down = posterFocus.getOrNull(index + 1),
                            onActivate = onOpenItem,
                            onMenu = onSettings,
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
                    item(key = "empty") {
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
            }
        }
    }
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
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = HomeStyle.gutter, vertical = 12.dp),
    ) {
        EmbyMark(Modifier.align(Alignment.CenterStart))
        Row(
            modifier = Modifier.align(Alignment.Center),
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
        }
        NavIconButton(
            modifier = Modifier.align(Alignment.CenterEnd),
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
            BreathingStroke(Modifier.matchParentSize(), CircleShape)
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
            BreathingStroke(Modifier.matchParentSize(), shape)
        }
    }
}

@Composable
private fun LeadingSlotRow(
    items: List<BaseItemDto>,
    cardWidth: Dp,
    cardHeight: Dp,
    corner: Dp,
    focusRequester: FocusRequester,
    up: FocusRequester?,
    down: FocusRequester?,
    onActivate: (BaseItemDto) -> Unit,
    onMenu: () -> Unit,
    initialWindowStart: Int = 0,
    forceFocused: Boolean = false,
    caption: @Composable (BaseItemDto) -> Unit,
    art: @Composable BoxScope.(BaseItemDto) -> Unit,
) {
    if (items.isEmpty()) return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stride = cardWidth + HomeStyle.rowGap
        val usable = maxWidth - HomeStyle.gutter
        val fullSlots = ((usable + HomeStyle.rowGap) / stride).toInt().coerceAtLeast(1)
        val maxStart = (items.size - fullSlots).coerceAtLeast(0)
        var windowStart by remember(items.size, fullSlots) {
            mutableIntStateOf(initialWindowStart.coerceIn(0, maxStart))
        }
        var slot by remember(items.size) { mutableIntStateOf(0) }
        var rowFocused by rememberFocusState(forceFocused)
        if (windowStart > maxStart) windowStart = maxStart
        val lastSlot = (min(fullSlots, items.size - windowStart) - 1).coerceAtLeast(0)
        if (slot > lastSlot) slot = lastSlot

        fun moveRight() {
            val moreRight = items.size - windowStart - fullSlots
            val endSlot = (min(fullSlots, items.size - windowStart) - 1).coerceAtLeast(0)
            if (slot == 0 && moreRight > 0) {
                windowStart++
            } else if (slot < endSlot) {
                slot++
            }
        }

        fun moveLeft() {
            if (slot > 0) {
                slot--
            } else if (windowStart > 0) {
                windowStart--
            }
        }

        val slide by animateDpAsState(
            targetValue = -(stride * windowStart),
            animationSpec = tween(durationMillis = 220),
            label = "leading-slot-slide",
        )
        val ringX by animateDpAsState(
            targetValue = stride * slot,
            animationSpec = tween(durationMillis = 220),
            label = "leading-slot-ring",
        )
        val shape = RoundedCornerShape(corner)
        val focusedIndex = (windowStart + slot).coerceIn(0, items.lastIndex)

        Box(
            Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .focusProperties {
                    up?.let { this.up = it }
                    down?.let { this.down = it }
                }
                .onFocusChanged { rowFocused = it.isFocused || forceFocused }
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionRight -> {
                            moveRight()
                            true
                        }
                        Key.DirectionLeft -> {
                            if (slot == 0 && windowStart == 0) false else {
                                moveLeft()
                                true
                            }
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            onActivate(items[focusedIndex])
                            true
                        }
                        Key.Menu, Key.Bookmark -> {
                            onMenu()
                            true
                        }
                        else -> false
                    }
                },
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = HomeStyle.gutter)
                    .clipToBounds(),
            ) {
            Row(
                modifier = Modifier
                    .wrapContentWidth(align = Alignment.Start, unbounded = true)
                    .offset(x = slide),
                horizontalArrangement = Arrangement.spacedBy(HomeStyle.rowGap),
            ) {
                items.forEach { item ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .width(cardWidth)
                                .height(cardHeight)
                                .clip(shape)
                                .focusProperties { canFocus = false }
                                .clickable { onActivate(item) },
                        ) {
                            art(item)
                        }
                        caption(item)
                    }
                }
            }
            }
            if (rowFocused || forceFocused) {
                BreathingStroke(
                    modifier = Modifier
                        .padding(start = HomeStyle.gutter)
                        .offset(x = ringX)
                        .width(cardWidth)
                        .height(cardHeight),
                    shape = shape,
                    solid = forceFocused && !rowFocused,
                )
            }
        }
    }
}

@Composable
private fun BreathingStroke(
    modifier: Modifier,
    shape: Shape,
    solid: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "focus-breathe")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.58f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "focus-alpha",
    )
    Box(
        modifier.border(
            width = HomeStyle.focusStroke,
            color = Color.White.copy(alpha = if (solid) 1f else alpha),
            shape = shape,
        )
    )
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
    when {
        art != null -> SampleArtwork(art, modifier)
        url.isNotBlank() -> AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(url)
                .crossfade(180)
                .apply { if (LocalArtworkSoften.current) transformations(HeavyBlurTransformation()) }
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier.background(Color(0xFF241E1B)),
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
