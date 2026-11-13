package com.xxxx.emby_tv.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.transformations
import com.xxxx.emby_tv.R
import com.xxxx.emby_tv.Utils
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.data.model.MediaSourceInfoDto
import com.xxxx.emby_tv.data.model.PersonInfo
import com.xxxx.emby_tv.ui.home.ArtRole
import com.xxxx.emby_tv.ui.motion.TvFocusHighlight
import com.xxxx.emby_tv.ui.home.HomeImage
import com.xxxx.emby_tv.ui.home.HomeStyle
import com.xxxx.emby_tv.ui.home.HomeText
import com.xxxx.emby_tv.ui.focus.ProvideTvBringIntoView
import com.xxxx.emby_tv.ui.focus.TvBringIntoView
import com.xxxx.emby_tv.ui.home.LocalArtworkSoften
import com.xxxx.emby_tv.ui.home.LocalHomeFont
import com.xxxx.emby_tv.ui.viewmodel.DetailViewModel
import com.xxxx.emby_tv.util.EmbyPresentation
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.math.min

enum class DetailFocus { Play, Version, Cast, Similar }

@Composable
fun MovieDetailConnected(
    detailViewModel: DetailViewModel,
    serverUrl: String,
    apiKey: String = "",
    onPlay: (item: BaseItemDto, sourceId: String?) -> Unit,
    onOpenSimilar: (BaseItemDto) -> Unit,
) {
    val item = detailViewModel.mediaInfo ?: return
    LaunchedEffect(item.id) {
        item.id?.let { detailViewModel.loadMovieExtras(it) }
    }
    MovieDetailPage(
        item = item,
        similar = detailViewModel.similarItems,
        collections = detailViewModel.collections,
        serverUrl = serverUrl,
        apiKey = apiKey,
        onPlay = { onPlay(item, null) },
        onPickSource = { sourceId -> onPlay(item, sourceId) },
        onToggleFavorite = {
            val id = item.id ?: return@MovieDetailPage
            detailViewModel.toggleFavorite(id, item.userData?.isFavorite != true)
        },
        onTogglePlayed = {
            val id = item.id ?: return@MovieDetailPage
            detailViewModel.togglePlayed(id, item.userData?.played != true)
        },
        onOpenSimilar = onOpenSimilar,
    )
}

@Composable
fun MovieDetailPage(
    item: BaseItemDto,
    similar: List<BaseItemDto>,
    collections: List<Pair<BaseItemDto, List<BaseItemDto>>> = emptyList(),
    serverUrl: String,
    apiKey: String = "",
    onPlay: () -> Unit,
    onPickSource: (String?) -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePlayed: () -> Unit,
    onOpenSimilar: (BaseItemDto) -> Unit,
    scrollOffset: Dp = 0.dp,
    forceFocus: DetailFocus? = null,
    requestInitialFocus: Boolean = true,
    fontFamily: FontFamily = FontFamily.Default,
    modifier: Modifier = Modifier,
) {
    val playFocus = remember { FocusRequester() }
    val castFocus = remember { FocusRequester() }
    val similarFocus = remember { FocusRequester() }
    val collectionFocus = remember(collections.size) { List(collections.size) { FocusRequester() } }
    val people = item.people.orEmpty().filter { it.name?.isNotBlank() == true }
    LaunchedEffect(requestInitialFocus, item.id) {
        if (!requestInitialFocus) return@LaunchedEffect
        yield()
        try {
            playFocus.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }
    val backdrop = if (serverUrl.isBlank()) "" else Utils.getBackdropUrl(serverUrl, item, 1920)
    val backdropContext = LocalContext.current
    LaunchedEffect(backdrop) {
        if (backdrop.isBlank()) return@LaunchedEffect
        com.xxxx.emby_tv.ui.home.ArtworkPrefetch.enqueueBackdrops(backdropContext, listOf(backdrop))
    }
    val blurForLower = forceFocus == DetailFocus.Cast || forceFocus == DetailFocus.Similar
    var blurBackdrop by remember(item.id) { mutableStateOf(blurForLower) }
    val scrollState = rememberScrollState()
    val stage = com.xxxx.emby_tv.ui.motion.rememberStageScroll(scrollState)
    val bringIntoView = remember { TvBringIntoView() }
    fun revealHero() {
        blurBackdrop = false
        stage.dock(0)
    }
    fun dockRow(index: Int) {
        blurBackdrop = index > 0
        stage.dock(index)
    }
    val dim by animateFloatAsState(if (blurBackdrop) 0.18f else 0f, com.xxxx.emby_tv.ui.motion.Motion.fade(), label = "backdrop-dim")
    val sources = item.mediaSources.orEmpty()
    var versionMenu by remember(item.id) { mutableStateOf(false) }
    var versionIndex by remember(item.id) { mutableIntStateOf(0) }
    BackHandler(enabled = versionMenu) { versionMenu = false }
    CompositionLocalProvider(LocalHomeFont provides fontFamily) {
        ProvideTvBringIntoView(
            controller = bringIntoView,
            viewportScroll = { scrollState.value.toFloat() },
            scrolledPastStart = { scrollState.value > 0 },
        ) {
        BoxWithConstraints(
            modifier
                .fillMaxSize()
                .background(Color(0xFF100E0C))
                .clipToBounds(),
        ) {
            CompositionLocalProvider(LocalArtworkSoften provides blurBackdrop) {
                HomeImage(
                    itemId = item.id,
                    fallbackArtId = null,
                    url = backdrop,
                    role = ArtRole.Backdrop,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (dim > 0.01f) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(dim)))
            }
            com.xxxx.emby_tv.ui.home.AmbientWash(
                url = backdrop,
                strength = 0.28f,
                maxAlpha = 0.2f,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(0.72f),
                            0.42f to Color.Black.copy(0.28f),
                            1f to Color.Transparent,
                        )
                    ),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xFF100E0C).copy(0.92f)),
                        )
                    ),
            )
            val viewport = maxHeight
            stage.stagePx = (constraints.maxHeight * 0.18f).toInt().coerceIn(
                with(LocalDensity.current) { 72.dp.roundToPx() },
                with(LocalDensity.current) { 180.dp.roundToPx() },
            )
            val stageSpacer = with(LocalDensity.current) {
                com.xxxx.emby_tv.ui.motion.StageDock.bottomSpacer(constraints.maxHeight, stage.stagePx).toDp()
            }
            val titleStart = viewport * 0.35f
            val buttonBottom = viewport * 0.79f
            val actionHeight = 52.dp
            var titleHeight by remember(item.id) { mutableStateOf(170.dp) }
            val density = LocalDensity.current
            val beforeButtons = (buttonBottom - actionHeight - titleStart - titleHeight).coerceAtLeast(8.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState, enabled = scrollOffset == 0.dp)
                    .offset(y = scrollOffset)
                    .padding(bottom = 28.dp),
            ) {
                Spacer(Modifier.height(if (scrollOffset == 0.dp) titleStart else 48.dp))
                TitleBlockSpaced(
                    item,
                    serverUrl,
                    Modifier
                        .padding(horizontal = 48.dp)
                        .onSizeChanged { titleHeight = with(density) { it.height.toDp() } },
                )
                Spacer(Modifier.height(if (scrollOffset == 0.dp) beforeButtons else 18.dp))
                ActionRow(
                    item = item,
                    focusRequester = playFocus,
                    down = if (people.isNotEmpty()) castFocus else collectionFocus.firstOrNull()
                        ?: if (similar.isNotEmpty()) similarFocus else null,
                    forceFocused = forceFocus == DetailFocus.Play || forceFocus == DetailFocus.Version,
                    initialSlot = if (forceFocus == DetailFocus.Version) 1 else 0,
                    versionIndex = versionIndex,
                    onVersionIndex = { versionIndex = it },
                    onFocused = { revealHero() },
                    onPlay = onPlay,
                    onPickVersion = {
                        if (!versionMenu) versionMenu = true
                        else onPickSource(sources.getOrNull(versionIndex)?.id)
                    },
                    versionMenuOpen = versionMenu,
                    showVersions = sources.size > 1,
                    onFavorite = onToggleFavorite,
                    onPlayed = onTogglePlayed,
                    modifier = Modifier.padding(horizontal = 48.dp),
                )
                if (people.isNotEmpty()) {
                    Column(Modifier.onGloballyPositioned {
                        stage.record(1, it.positionInParent().y.toInt() + scrollState.value)
                    }) {
                    SectionLabel(stringResource(R.string.detail_cast))
                    PersonRow(
                        people = people,
                        serverUrl = serverUrl,
                        apiKey = apiKey,
                        focusRequester = castFocus,
                        up = playFocus,
                        down = collectionFocus.firstOrNull() ?: if (similar.isNotEmpty()) similarFocus else null,
                        forceFocused = forceFocus == DetailFocus.Cast,
                        onFocused = { dockRow(1) },
                    )
                    }
                }
                collections.forEachIndexed { index, (box, works) ->
                    val row = 2 + index
                    Column(Modifier.onGloballyPositioned {
                        stage.record(row, it.positionInParent().y.toInt() + scrollState.value)
                    }) {
                        SectionLabel(EmbyPresentation.collectionRowTitle(box.name.orEmpty()))
                        DetailSlotRow(
                            count = works.size,
                            cardWidth = 220.dp,
                            cardHeight = 124.dp,
                            gap = 16.dp,
                            cornerIsCircle = false,
                            focusRequester = collectionFocus[index],
                            up = if (index == 0) castFocus else collectionFocus[index - 1],
                            down = collectionFocus.getOrNull(index + 1) ?: if (similar.isNotEmpty()) similarFocus else null,
                            forceFocused = false,
                            peekPrevious = true,
                            onActivate = { onOpenSimilar(works[it]) },
                            onFocused = { dockRow(row) },
                            caption = { i ->
                                HomeText(works[i].name.orEmpty(), 14.sp, Color.White, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.width(220.dp).padding(top = 8.dp))
                            },
                        ) { i ->
                            HomeImage(
                                itemId = works[i].id,
                                fallbackArtId = null,
                                url = if (serverUrl.isBlank()) "" else Utils.getImageUrl(serverUrl, works[i], true),
                                role = ArtRole.Thumb,
                                contentDescription = works[i].name,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
                if (similar.isNotEmpty()) {
                    Column(Modifier.onGloballyPositioned {
                        stage.record(2 + collections.size, it.positionInParent().y.toInt() + scrollState.value)
                    }) {
                    SectionLabel(stringResource(R.string.detail_more_like))
                    SimilarRow(
                        items = similar,
                        serverUrl = serverUrl,
                        focusRequester = similarFocus,
                        up = collectionFocus.lastOrNull() ?: if (people.isNotEmpty()) castFocus else playFocus,
                        forceFocused = forceFocus == DetailFocus.Similar,
                        onFocused = { dockRow(2 + collections.size) },
                        onOpen = onOpenSimilar,
                    )
                    }
                }
                Spacer(Modifier.height(stageSpacer))
            }
            if (versionMenu && sources.size > 1) {
                VersionPanel(
                    sources = sources,
                    selected = versionIndex.coerceIn(0, sources.lastIndex),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 36.dp),
                )
            }
        }
        }
    }
}

@Composable
private fun TitleBlockSpaced(item: BaseItemDto, serverUrl: String, modifier: Modifier = Modifier) {
    val raw = EmbyPresentation.displayName(item)
    val genres = EmbyPresentation.genresLine(item)
    val year = EmbyPresentation.yearLabel(item)
    val score = EmbyPresentation.starLabel(item.communityRating).orEmpty()
    val rating = item.officialRating.orEmpty()
    val logo = EmbyPresentation.logoUrl(serverUrl, item)
    Column(modifier.width(520.dp), horizontalAlignment = Alignment.Start) {
        if (logo.isNotBlank()) {
            coil3.compose.AsyncImage(
                model = coil3.request.ImageRequest.Builder(LocalContext.current).data(logo).build(),
                contentDescription = raw,
                alignment = Alignment.CenterStart,
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(56.dp).fillMaxWidth(),
            )
        } else {
            Text(
                text = raw,
                color = Color(0xFFD64532),
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (genres.isNotBlank()) {
                HomeText(genres, 12.sp, Color(0xFFE4E4E4))
                HomeText("  ·  ", 12.sp, Color(0xFFBDBDBD))
            }
            if (rating.isNotBlank()) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(0.16f))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    HomeText(rating, 10.sp, Color.White, fontWeight = FontWeight.Medium)
                }
                HomeText("  ·  ", 12.sp, Color(0xFFBDBDBD))
            }
            if (year.isNotBlank()) HomeText(year, 12.sp, Color(0xFFE4E4E4))
            if (score.isNotBlank()) {
                HomeText("  ·  ", 12.sp, Color(0xFFBDBDBD))
                HomeText(score, 12.sp, Color(0xFFE4E4E4))
            }
        }
        val overview = item.overview.orEmpty()
        if (overview.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            HomeText(
                text = overview,
                fontSize = 13.sp,
                color = Color(0xFFE8E8E8),
                maxLines = 2,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val runtime = EmbyPresentation.runtimeLabel(item.runTimeTicks).orEmpty()
            if (runtime.isNotBlank()) {
                HomeText(runtime, 13.sp, Color.White)
            }
            val badge = EmbyPresentation.qualityBadge(item)
            if (!badge.isNullOrBlank()) {
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF2C2C2C))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    HomeText(badge, 11.sp, Color.White, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun Dot() {
    HomeText("  ·  ", 13.sp, Color(0xFFBDBDBD))
}

@Composable
internal fun ActionRow(
    item: BaseItemDto,
    focusRequester: FocusRequester,
    down: FocusRequester?,
    forceFocused: Boolean,
    onFocused: () -> Unit = {},
    onPlay: () -> Unit,
    onPickVersion: () -> Unit = {},
    onFavorite: () -> Unit,
    onPlayed: () -> Unit,
    showVersions: Boolean = true,
    playbackItem: BaseItemDto = item,
    initialSlot: Int = 0,
    versionIndex: Int = 0,
    onVersionIndex: (Int) -> Unit = {},
    onAction: (String) -> Unit = {},
    versionMenuOpen: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val sources = item.mediaSources.orEmpty()
    val actions = if (showVersions && EmbyPresentation.showVersionButton(sources.size)) {
        listOf("play", "version", "favorite", "played")
    } else {
        listOf("play", "favorite", "played")
    }
    var slot by remember { mutableIntStateOf(initialSlot.coerceIn(0, actions.lastIndex)) }
    var rowFocused by remember { mutableStateOf(forceFocused) }
    val label = EmbyPresentation.playActionLabel(playbackItem)
    val favorite = item.userData?.isFavorite == true
    val played = item.userData?.played == true
    val versionLabel = sources.getOrNull(versionIndex)?.let { EmbyPresentation.versionLabel(it) }.orEmpty()
    val highlighted = rowFocused || forceFocused
    LaunchedEffect(slot, highlighted) {
        if (highlighted) onAction(actions[slot])
    }
    Row(
        modifier
            .focusRequester(focusRequester)
            .focusProperties { down?.let { this.down = it } }
            .onFocusChanged {
                rowFocused = it.isFocused || forceFocused
                if (it.isFocused) onFocused()
            }
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionRight -> {
                        if (slot < actions.lastIndex) slot++
                        true
                    }
                    Key.DirectionLeft -> {
                        if (slot == 0) false else {
                            slot--
                            true
                        }
                    }
                    Key.DirectionDown -> {
                        if (versionMenuOpen && actions[slot] == "version" && versionIndex < sources.lastIndex) {
                            onVersionIndex(versionIndex + 1)
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionUp -> {
                        if (versionMenuOpen && actions[slot] == "version" && versionIndex > 0) {
                            onVersionIndex(versionIndex - 1)
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        when (actions[slot]) {
                            "play" -> onPlay()
                            "version" -> onPickVersion()
                            "favorite" -> onFavorite()
                            "played" -> onPlayed()
                        }
                        true
                    }
                    else -> false
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val playOn = highlighted && actions[slot] == "play"
        val versionOn = highlighted && actions[slot] == "version"
        val favoriteOn = highlighted && actions[slot] == "favorite"
        val playedOn = highlighted && actions[slot] == "played"
        PlayPill(label, focused = playOn, onClick = onPlay)
        if (showVersions) {
            VersionPill(
                label = versionLabel,
                focused = versionOn,
                onClick = onPickVersion,
            )
        }
        IconBubble(focused = favoriteOn, onClick = onFavorite) {
            Icon(
                imageVector = if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = null,
                tint = if (favoriteOn) Color(0xFF161616) else Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        IconBubble(focused = playedOn, onClick = onPlayed, filled = played) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = if (playedOn) Color(0xFF161616) else Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun PlayPill(label: String, focused: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val fg = if (focused) Color(0xFF161616) else Color.White
    Box(
        Modifier
            .clip(shape)
            .background(if (focused) Color.White else Color.White.copy(0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PlayArrow, null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(4.dp))
            HomeText(label, 15.sp, fg, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun VersionPill(label: String, focused: Boolean, onClick: () -> Unit) {
    val fg = if (focused) Color(0xFF161616) else Color.White
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color.White else Color.White.copy(0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DiamondIcon(Modifier.size(16.dp), color = fg)
        if (focused && EmbyPresentation.versionShowsLabel(true) && label.isNotBlank()) {
            Spacer(Modifier.width(6.dp))
            HomeText(label, 15.sp, fg, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun IconBubble(
    focused: Boolean,
    onClick: () -> Unit,
    filled: Boolean = false,
    icon: @Composable BoxScope.() -> Unit,
) {
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(
                when {
                    focused -> Color.White
                    filled -> Color.White.copy(0.28f)
                    else -> Color.White.copy(0.16f)
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

@Composable
private fun DiamondIcon(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val path = Path().apply {
            moveTo(size.width / 2f, 0f)
            lineTo(size.width, size.height / 2f)
            lineTo(size.width / 2f, size.height)
            lineTo(0f, size.height / 2f)
            close()
        }
        drawPath(path, color.copy(alpha = 0.18f))
        drawPath(
            path,
            color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
        )
    }
}

@Composable
internal fun SectionLabel(text: String) {
    HomeText(
        text = text,
        fontSize = 22.sp,
        color = Color.White,
        modifier = Modifier.padding(start = 48.dp, top = 22.dp, bottom = 10.dp),
    )
}

@Composable
private fun PersonRow(
    people: List<PersonInfo>,
    serverUrl: String,
    apiKey: String,
    focusRequester: FocusRequester,
    up: FocusRequester?,
    down: FocusRequester?,
    forceFocused: Boolean,
    onFocused: () -> Unit = {},
) {
    DetailSlotRow(
        count = people.size,
        cardWidth = 108.dp,
        cardHeight = 108.dp,
        gap = 22.dp,
        cornerIsCircle = true,
        focusRequester = focusRequester,
        up = up,
        down = down,
        forceFocused = forceFocused,
        onFocused = onFocused,
        peekPrevious = true,
        onActivate = {},
        caption = { index ->
            val person = people[index]
            Column(
                Modifier
                    .width(108.dp)
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HomeText(
                    person.name.orEmpty(),
                    14.sp,
                    Color.White,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                val role = person.role.orEmpty()
                if (role.isNotBlank()) {
                    HomeText(
                        role,
                        12.sp,
                        Color(0xFFA3A3A3),
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) { index ->
        val person = people[index]
        val url = EmbyPresentation.personImageUrl(serverUrl, person, apiKey)
        if (url.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(url).build(),
                contentDescription = person.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
            AvatarPlaceholder(person.name.orEmpty(), index, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun SimilarRow(
    items: List<BaseItemDto>,
    serverUrl: String,
    focusRequester: FocusRequester,
    up: FocusRequester?,
    forceFocused: Boolean,
    onFocused: () -> Unit = {},
    onOpen: (BaseItemDto) -> Unit,
) {
    DetailSlotRow(
        count = items.size,
        cardWidth = 220.dp,
        cardHeight = 124.dp,
        gap = 16.dp,
        cornerIsCircle = false,
        focusRequester = focusRequester,
        up = up,
        down = null,
        forceFocused = forceFocused,
        onFocused = onFocused,
        peekPrevious = true,
        onActivate = { onOpen(items[it]) },
        caption = { index ->
            HomeText(
                items[index].name.orEmpty(),
                14.sp,
                Color.White,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .width(220.dp)
                    .padding(top = 8.dp),
            )
        },
    ) { index ->
        val item = items[index]
        HomeImage(
            itemId = item.id,
            fallbackArtId = null,
            url = if (serverUrl.isBlank()) "" else Utils.getImageUrl(serverUrl, item, true),
            role = ArtRole.Thumb,
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
internal fun DetailSlotRow(
    count: Int,
    cardWidth: Dp,
    cardHeight: Dp,
    gap: Dp,
    cornerIsCircle: Boolean,
    focusRequester: FocusRequester,
    up: FocusRequester?,
    down: FocusRequester?,
    forceFocused: Boolean,
    onActivate: (Int) -> Unit,
    initialWindowStart: Int = 0,
    peekPrevious: Boolean = false,
    onFocused: () -> Unit = {},
    caption: @Composable (Int) -> Unit,
    art: @Composable BoxScope.(Int) -> Unit,
) {
    if (count == 0) return
    val shape: Shape = if (cornerIsCircle) CircleShape else RoundedCornerShape(14.dp)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stride = cardWidth + gap
        val fullSlots = (((maxWidth - 48.dp) + gap) / stride).toInt().coerceAtLeast(1)
        val maxStart = (count - fullSlots).coerceAtLeast(0)
        var windowStart by remember(count) {
            mutableIntStateOf(initialWindowStart.coerceIn(0, maxStart))
        }
        var slot by remember { mutableIntStateOf(0) }
        var rowFocused by remember { mutableStateOf(forceFocused) }
        if (windowStart > maxStart) windowStart = maxStart
        val lastSlot = (min(fullSlots, count - windowStart) - 1).coerceAtLeast(0)
        if (slot > lastSlot) slot = lastSlot
        val density = androidx.compose.ui.platform.LocalDensity.current
        val insetPx = with(density) { 48.dp.roundToPx() }
        val stridePx = with(density) { stride.roundToPx() }
        val slidePx = if (peekPrevious) {
            com.xxxx.emby_tv.ui.motion.LeadingPeek.slide(windowStart, insetPx, stridePx)
        } else {
            -windowStart * stridePx
        }
        val slide by animateDpAsState(
            with(density) { slidePx.toDp() },
            com.xxxx.emby_tv.ui.motion.Motion.row(),
            label = "detail-slide",
        )
        val ringX by animateDpAsState(
            with(density) { com.xxxx.emby_tv.ui.motion.LeadingPeek.focusedX(0, slot, stridePx).toDp() },
            com.xxxx.emby_tv.ui.motion.Motion.row(),
            label = "detail-ring",
        )
        val focusIndex = (windowStart + slot).coerceIn(0, count - 1)
        Box(
            Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .focusProperties {
                    up?.let { this.up = it }
                    down?.let { this.down = it }
                }
                .onFocusChanged {
                    rowFocused = it.isFocused || forceFocused
                    if (it.isFocused) onFocused()
                }
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionRight -> {
                            val more = count - windowStart - fullSlots
                            val end = (min(fullSlots, count - windowStart) - 1).coerceAtLeast(0)
                            if (slot == 0 && more > 0) windowStart++ else if (slot < end) slot++
                            true
                        }
                        Key.DirectionLeft -> {
                            if (slot > 0) slot-- else if (windowStart > 0) windowStart-- else return@onKeyEvent false
                            true
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            onActivate(focusIndex)
                            true
                        }
                        else -> false
                    }
                },
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = if (peekPrevious) 0.dp else 48.dp)
                    .clipToBounds(),
            ) {
                Row(
                    Modifier
                        .wrapContentWidth(align = Alignment.Start, unbounded = true)
                        .offset(x = slide),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    repeat(count) { index ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .size(cardWidth, cardHeight)
                                    .clip(shape)
                                    .clickable { onActivate(index) },
                            ) {
                                art(index)
                            }
                            caption(index)
                        }
                    }
                }
            }
            if (rowFocused || forceFocused) {
                TvFocusHighlight(
                    modifier = Modifier
                        .padding(start = 48.dp)
                        .offset(x = ringX)
                        .size(cardWidth, cardHeight),
                    shape = shape,
                    solid = forceFocused && !rowFocused,
                    stroke = HomeStyle.focusStroke,
                )
            }
        }
    }
}

@Composable
internal fun AvatarPlaceholder(name: String, index: Int, modifier: Modifier) {
    val colors = listOf(
        Color(0xFF8C5A3C), Color(0xFFC4A574), Color(0xFF6E5344),
        Color(0xFF3E4C63), Color(0xFF2F2A28), Color(0xFFD8C3A5), Color(0xFF5C6B73),
    )
    Box(modifier.background(colors[index % colors.size]), contentAlignment = Alignment.Center) {
        HomeText(name.take(1), 28.sp, Color.White.copy(0.9f))
    }
}

@Composable
private fun VersionPanel(
    sources: List<MediaSourceInfoDto>,
    selected: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(320.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xF01A1A1A))
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        HomeText(stringResource(R.string.choose_version), 20.sp, Color.White, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        sources.forEachIndexed { index, source ->
            val on = index == selected
            val title = EmbyPresentation.versionLabel(source)
            val detail = EmbyPresentation.versionDetail(source)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) Color.White else Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    HomeText(
                        title,
                        16.sp,
                        if (on) Color(0xFF161616) else Color.White,
                        fontWeight = FontWeight.Medium,
                    )
                    if (detail.isNotBlank()) {
                        HomeText(
                            detail,
                            12.sp,
                            if (on) Color(0xFF4A4A4A) else Color(0xFFB5B5B5),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                if (on) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color(0xFF161616),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun formatClock(ticks: Long): String {
    val totalMinutes = (ticks / 10_000_000L / 60L).coerceAtLeast(0L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0) "${hours}h ${minutes}min" else "${minutes}min"
}

private fun is4k(item: BaseItemDto): Boolean {
    val width = item.width ?: 0
    val height = item.height ?: 0
    if (width >= 3800 || height >= 2000) return true
    return item.mediaSources.orEmpty().any { source ->
        source.mediaStreams.orEmpty().any { stream ->
            (stream.width ?: 0) >= 3800 || (stream.height ?: 0) >= 2000
        }
    }
}

private fun personImageUrl(serverUrl: String, person: PersonInfo): String {
    if (serverUrl.isBlank() || person.id.isNullOrBlank() || person.primaryImageTag.isNullOrBlank()) return ""
    return "$serverUrl/emby/Items/${person.id}/Images/Primary?maxWidth=400&tag=${person.primaryImageTag}&quality=80"
}
