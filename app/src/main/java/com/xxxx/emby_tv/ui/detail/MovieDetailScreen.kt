package com.xxxx.emby_tv.ui.detail

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.xxxx.emby_tv.R
import com.xxxx.emby_tv.Utils
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.data.model.MediaSourceInfoDto
import com.xxxx.emby_tv.data.model.PersonInfo
import com.xxxx.emby_tv.ui.home.ArtRole
import com.xxxx.emby_tv.ui.home.HomeImage
import com.xxxx.emby_tv.ui.home.HomeStyle
import com.xxxx.emby_tv.ui.home.HomeText
import com.xxxx.emby_tv.ui.home.LocalArtworkSoften
import com.xxxx.emby_tv.ui.home.LocalHomeFont
import com.xxxx.emby_tv.ui.viewmodel.DetailViewModel
import com.xxxx.emby_tv.util.EmbyPresentation
import kotlinx.coroutines.yield
import kotlin.math.min

enum class DetailFocus { Play, Cast, Similar }

@Composable
fun MovieDetailConnected(
    detailViewModel: DetailViewModel,
    serverUrl: String,
    onPlay: (item: BaseItemDto, sourceId: String?) -> Unit,
    onOpenSimilar: (BaseItemDto) -> Unit,
) {
    val item = detailViewModel.mediaInfo ?: return
    LaunchedEffect(item.id) {
        item.id?.let { detailViewModel.loadMovieExtras(it) }
    }
    var showVersions by remember { mutableStateOf(false) }
    MovieDetailPage(
        item = item,
        similar = detailViewModel.similarItems,
        serverUrl = serverUrl,
        onPlay = { onPlay(item, null) },
        onPickSource = { sourceId ->
            showVersions = false
            onPlay(item, sourceId)
        },
        onToggleFavorite = {
            val id = item.id ?: return@MovieDetailPage
            detailViewModel.toggleFavorite(id, item.userData?.isFavorite != true)
        },
        onTogglePlayed = {
            val id = item.id ?: return@MovieDetailPage
            detailViewModel.togglePlayed(id, item.userData?.played != true)
        },
        onOpenSimilar = onOpenSimilar,
        onShowVersions = { showVersions = true },
    )
    if (showVersions) {
        VersionDialog(
            sources = item.mediaSources.orEmpty(),
            onDismiss = { showVersions = false },
            onPick = { sourceId ->
                showVersions = false
                onPlay(item, sourceId)
            },
        )
    }
}

@Composable
fun MovieDetailPage(
    item: BaseItemDto,
    similar: List<BaseItemDto>,
    serverUrl: String,
    onPlay: () -> Unit,
    onPickSource: (String?) -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePlayed: () -> Unit,
    onOpenSimilar: (BaseItemDto) -> Unit,
    onShowVersions: () -> Unit,
    scrollOffset: Dp = 0.dp,
    forceFocus: DetailFocus? = null,
    requestInitialFocus: Boolean = true,
    fontFamily: FontFamily = FontFamily.Default,
    modifier: Modifier = Modifier,
) {
    val playFocus = remember { FocusRequester() }
    val castFocus = remember { FocusRequester() }
    val similarFocus = remember { FocusRequester() }
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
    val blurForLower = forceFocus == DetailFocus.Cast || forceFocus == DetailFocus.Similar
    var blurBackdrop by remember(item.id) { mutableStateOf(blurForLower) }
    val blurRadius by animateDpAsState(if (blurBackdrop) 26.dp else 0.dp, tween(280), label = "backdrop-blur")
    val dim by animateFloatAsState(if (blurBackdrop) 0.5f else 0f, tween(280), label = "backdrop-dim")
    CompositionLocalProvider(LocalHomeFont provides fontFamily) {
        Box(
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
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (blurRadius > 0.5.dp) Modifier.blur(blurRadius) else Modifier),
                )
            }
            if (dim > 0.01f) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(dim)))
            }
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
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState(), enabled = scrollOffset == 0.dp)
                    .offset(y = scrollOffset)
                    .padding(bottom = 28.dp),
            ) {
                Spacer(Modifier.height(236.dp))
                TitleBlockSpaced(item, serverUrl, Modifier.padding(horizontal = 48.dp))
                Spacer(Modifier.height(18.dp))
                ActionRow(
                    item = item,
                    focusRequester = playFocus,
                    down = if (people.isNotEmpty()) castFocus else if (similar.isNotEmpty()) similarFocus else null,
                    forceFocused = forceFocus == DetailFocus.Play,
                    onFocused = { blurBackdrop = false },
                    onPlay = onPlay,
                    onVersions = onShowVersions,
                    onFavorite = onToggleFavorite,
                    onPlayed = onTogglePlayed,
                    modifier = Modifier.padding(horizontal = 48.dp),
                )
                if (people.isNotEmpty()) {
                    SectionLabel(stringResource(R.string.detail_cast))
                    PersonRow(
                        people = people,
                        serverUrl = serverUrl,
                        focusRequester = castFocus,
                        up = playFocus,
                        down = if (similar.isNotEmpty()) similarFocus else null,
                        forceFocused = forceFocus == DetailFocus.Cast,
                        onFocused = { blurBackdrop = true },
                    )
                }
                if (similar.isNotEmpty()) {
                    SectionLabel(stringResource(R.string.detail_more_like))
                    SimilarRow(
                        items = similar,
                        serverUrl = serverUrl,
                        focusRequester = similarFocus,
                        up = if (people.isNotEmpty()) castFocus else playFocus,
                        forceFocused = forceFocus == DetailFocus.Similar,
                        onFocused = { blurBackdrop = true },
                        onOpen = onOpenSimilar,
                    )
                }
            }
        }
    }
}

@Composable
private fun TitleBlockSpaced(item: BaseItemDto, serverUrl: String, modifier: Modifier = Modifier) {
    val raw = EmbyPresentation.displayName(item)
    val cjk = raw.isNotEmpty() && raw.all { it.code > 0x2E80 }
    val title = if (cjk && raw.length <= 8) raw.toCharArray().joinToString("\u2003") else raw
    val genres = EmbyPresentation.genresLine(item)
    val year = EmbyPresentation.yearLabel(item)
    val score = EmbyPresentation.starLabel(item.communityRating).orEmpty()
    val rating = item.officialRating.orEmpty()
    val logo = EmbyPresentation.logoUrl(serverUrl, item)
    Column(modifier.width(520.dp)) {
        if (logo.isNotBlank()) {
            coil3.compose.AsyncImage(
                model = coil3.request.ImageRequest.Builder(LocalContext.current).data(logo).build(),
                contentDescription = raw,
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(72.dp).width(520.dp),
            )
        } else {
            HomeText(
                text = title,
                fontSize = 40.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (genres.isNotBlank()) {
                HomeText(genres, 13.sp, Color(0xFFE4E4E4))
                Dot()
            }
            if (rating.isNotBlank()) {
                Box(
                    Modifier
                        .border(1.dp, Color.White.copy(0.8f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 0.dp),
                ) {
                    HomeText(rating, 11.sp, Color.White, fontWeight = FontWeight.Medium)
                }
                Dot()
            }
            if (year.isNotBlank()) {
                HomeText(year, 13.sp, Color(0xFFE4E4E4))
            }
            if (score.isNotBlank()) {
                Dot()
                HomeText(score, 13.sp, Color(0xFFE4E4E4))
            }
        }
        val overview = item.overview.orEmpty()
        if (overview.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            HomeText(
                text = overview,
                fontSize = 14.sp,
                color = Color(0xFFF0F0F0),
                maxLines = 2,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val runtime = EmbyPresentation.runtimeLabel(item.runTimeTicks).orEmpty()
            if (runtime.isNotBlank()) {
                HomeText(runtime, 14.sp, Color.White)
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
    onVersions: () -> Unit,
    onFavorite: () -> Unit,
    onPlayed: () -> Unit,
    showVersions: Boolean = true,
    playbackItem: BaseItemDto = item,
    modifier: Modifier = Modifier,
) {
    val actions = if (showVersions) listOf("play", "version", "favorite", "played") else listOf("play", "favorite", "played")
    var slot by remember { mutableIntStateOf(0) }
    var rowFocused by remember { mutableStateOf(forceFocused) }
    val label = EmbyPresentation.playActionLabel(playbackItem)
    val favorite = item.userData?.isFavorite == true
    val played = item.userData?.played == true
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
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        when (actions[slot]) {
                            "play" -> onPlay()
                            "version" -> onVersions()
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
        PlayPill(label, showRing = (rowFocused || forceFocused) && actions[slot] == "play", onClick = onPlay)
        if (showVersions) {
            IconBubble(showRing = (rowFocused || forceFocused) && actions[slot] == "version", onClick = onVersions) {
                DiamondIcon(Modifier.size(18.dp))
            }
        }
        IconBubble(showRing = (rowFocused || forceFocused) && actions[slot] == "favorite", onClick = onFavorite) {
            Icon(
                imageVector = if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        IconBubble(
            showRing = (rowFocused || forceFocused) && actions[slot] == "played",
            onClick = onPlayed,
            filled = played,
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = if (played) Color.Black else Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun PlayPill(label: String, showRing: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box {
        Box(
            Modifier
                .padding(3.dp)
                .clip(shape)
                .background(Color(0xFFE6E6E8))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color(0xFF161616), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                HomeText(label, 15.sp, Color(0xFF161616), fontWeight = FontWeight.Medium)
            }
        }
        if (showRing) BreathingStroke(Modifier.matchParentSize(), shape)
    }
}

@Composable
private fun IconBubble(
    showRing: Boolean,
    onClick: () -> Unit,
    filled: Boolean = false,
    icon: @Composable BoxScope.() -> Unit,
) {
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(if (filled) Color.White else Color.White.copy(0.16f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon()
        if (showRing) BreathingStroke(Modifier.matchParentSize(), CircleShape)
    }
}

@Composable
private fun DiamondIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val path = Path().apply {
            moveTo(size.width / 2f, 0f)
            lineTo(size.width, size.height / 2f)
            lineTo(size.width / 2f, size.height)
            lineTo(0f, size.height / 2f)
            close()
        }
        drawPath(path, Color.White.copy(0.15f))
        drawPath(
            path,
            Color.White,
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
        val url = EmbyPresentation.personImageUrl(serverUrl, person)
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
    val shape: Shape = if (cornerIsCircle) CircleShape else RoundedCornerShape(12.dp)
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
        val peek = if (peekPrevious && windowStart > 0) 36.dp else 0.dp
        val slide by animateDpAsState(-(stride * windowStart) + peek, tween(220), label = "detail-slide")
        val ringX by animateDpAsState(stride * slot, tween(220), label = "detail-ring")
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
                    .padding(start = 48.dp - peek)
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
                BreathingStroke(
                    Modifier
                        .padding(start = 48.dp)
                        .offset(x = ringX)
                        .size(cardWidth, cardHeight),
                    shape,
                    solid = forceFocused && !rowFocused,
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
private fun VersionDialog(
    sources: List<MediaSourceInfoDto>,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1C1C1C))
                .padding(20.dp),
        ) {
            HomeText(stringResource(R.string.versions), 20.sp, Color.White)
            Spacer(Modifier.height(12.dp))
            val rows = sources.ifEmpty { listOf(MediaSourceInfoDto(name = stringResource(R.string.play))) }
            rows.forEach { source ->
                val label = if (source.id.isNullOrBlank() && source.mediaStreams.isNullOrEmpty() && sources.isEmpty()) {
                    source.name ?: stringResource(R.string.play)
                } else {
                    EmbyPresentation.versionLabel(source)
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(0.08f))
                        .clickable { onPick(source.id) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    HomeText(label, 16.sp, Color.White)
                }
            }
        }
    }
}

@Composable
internal fun BreathingStroke(modifier: Modifier, shape: Shape, solid: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "detail-breathe")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.58f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "detail-alpha",
    )
    Box(
        modifier.border(
            2.5.dp,
            Color.White.copy(alpha = if (solid) 1f else alpha),
            shape,
        )
    )
}

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
