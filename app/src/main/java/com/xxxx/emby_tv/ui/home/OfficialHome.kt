package com.xxxx.emby_tv.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
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
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.xxxx.emby_tv.R
import com.xxxx.emby_tv.Utils
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.data.model.RecommendationDto
import kotlinx.coroutines.yield

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun OfficialHome(
    hero: BaseItemDto?,
    resumeItems: List<BaseItemDto>,
    libraries: List<BaseItemDto>,
    favorites: List<BaseItemDto>,
    recommendations: List<RecommendationDto>,
    serverUrl: String,
    onResume: (BaseItemDto) -> Unit,
    onOpenDetails: (BaseItemDto) -> Unit,
    onOpenLibrary: (BaseItemDto) -> Unit,
    onOpenItem: (BaseItemDto) -> Unit,
    onMenu: () -> Unit,
    requestInitialFocus: Boolean = true,
    focusedId: String? = null,
    fontFamily: FontFamily = FontFamily.Default,
    modifier: Modifier = Modifier,
) {
    val continueFocus = remember { FocusRequester() }
    val heroFocus = remember { FocusRequester() }
    val libraryRows = libraries.filter { !it.latestItems.isNullOrEmpty() }

    LaunchedEffect(requestInitialFocus, resumeItems.firstOrNull()?.id, hero?.id) {
        if (!requestInitialFocus) return@LaunchedEffect
        yield()
        val target = when {
            resumeItems.isNotEmpty() -> continueFocus
            hero != null -> heroFocus
            else -> null
        }
        try {
            target?.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }

    CompositionLocalProvider(LocalHomeFont provides fontFamily) {
        Box(modifier.fillMaxSize().background(HomeStyle.background)) {
            if (hero == null && resumeItems.isEmpty() && libraries.isEmpty() && favorites.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    HomeText(
                        text = stringResource(R.string.no_data),
                        fontSize = 18.sp,
                        color = HomeStyle.meta,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 28.dp),
                ) {
                    if (hero != null) {
                        item(key = "hero") {
                            HeroBanner(
                                item = hero,
                                serverUrl = serverUrl,
                                onDetails = { onOpenDetails(hero) },
                                onMenu = onMenu,
                                focusRequester = heroFocus,
                            )
                        }
                    }
                    if (resumeItems.isNotEmpty()) {
                        item(key = "resume") {
                            ContinueRow(
                                title = stringResource(R.string.continue_watching),
                                items = resumeItems,
                                serverUrl = serverUrl,
                                focusedId = focusedId,
                                onClick = onResume,
                                onMenu = onMenu,
                                focusRequester = continueFocus,
                            )
                        }
                    }
                    if (libraries.isNotEmpty()) {
                        item(key = "libraries") {
                            LibraryRow(
                                title = stringResource(R.string.home_libraries),
                                libraries = libraries,
                                serverUrl = serverUrl,
                                onClick = onOpenLibrary,
                                onMenu = onMenu,
                            )
                        }
                    }
                    items(
                        items = libraryRows,
                        key = { it.id ?: it.name ?: it.hashCode() },
                    ) { library ->
                        PosterRow(
                            title = library.name.orEmpty(),
                            items = library.latestItems.orEmpty(),
                            serverUrl = serverUrl,
                            focusedId = focusedId,
                            onClick = onOpenItem,
                            onMenu = onMenu,
                        )
                    }
                    if (favorites.isNotEmpty()) {
                        item(key = "favorites") {
                            PosterRow(
                                title = stringResource(R.string.favorite),
                                items = favorites,
                                serverUrl = serverUrl,
                                focusedId = focusedId,
                                onClick = onOpenItem,
                                onMenu = onMenu,
                            )
                        }
                    }
                    itemsIndexed(
                        items = recommendations,
                        key = { index, row -> row.baselineItemName ?: "rec-$index" },
                    ) { _, row ->
                        PosterRow(
                            title = recommendationLabel(row),
                            items = row.items.orEmpty(),
                            serverUrl = serverUrl,
                            focusedId = focusedId,
                            onClick = onOpenItem,
                            onMenu = onMenu,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun recommendationLabel(row: RecommendationDto): String {
    val name = row.baselineItemName.orEmpty()
    val liked = row.recommendationType == "SimilarToLikedItem" ||
            row.recommendationType == "HasLikedDirector" ||
            row.recommendationType == "HasLikedActor"
    return if (liked) {
        stringResource(R.string.because_you_liked, name)
    } else {
        stringResource(R.string.because_you_watched, name)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HeroBanner(
    item: BaseItemDto,
    serverUrl: String,
    onDetails: () -> Unit,
    onMenu: () -> Unit,
    focusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .padding(horizontal = HomeStyle.gutter)
            .padding(top = 10.dp)
            .fillMaxWidth()
            .height(HomeStyle.heroHeight),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(HomeStyle.heroCorner)),
        ) {
            HomeImage(
                itemId = item.id,
                fallbackArtId = item.seriesId,
                url = backdropUrl(serverUrl, item),
                role = ArtRole.Backdrop,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                        )
                    ),
            )
        }
        DetailsPill(
            onClick = onDetails,
            onMenu = onMenu,
            focusRequester = focusRequester,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 14.dp),
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ContinueRow(
    title: String,
    items: List<BaseItemDto>,
    serverUrl: String,
    focusedId: String?,
    onClick: (BaseItemDto) -> Unit,
    onMenu: () -> Unit,
    focusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        SectionTitle(title)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = HomeStyle.gutter, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(HomeStyle.rowGap),
        ) {
            itemsIndexed(items, key = { _, item -> item.id ?: item.hashCode() }) { index, item ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HomeFocusCard(
                        width = HomeStyle.continueWidth,
                        height = HomeStyle.continueHeight,
                        corner = HomeStyle.cardCorner,
                        forcedFocus = item.id != null && item.id == focusedId,
                        onClick = { onClick(item) },
                        onMenu = onMenu,
                        focusRequester = if (index == 0) focusRequester else null,
                    ) {
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
                                .height(48.dp)
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
                            ProgressBar(item.playbackProgress)
                        }
                    }
                    CardCaption(cardTitle(item), cardMeta(item), HomeStyle.continueWidth)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LibraryRow(
    title: String,
    libraries: List<BaseItemDto>,
    serverUrl: String,
    onClick: (BaseItemDto) -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        SectionTitle(title)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = HomeStyle.gutter, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(HomeStyle.rowGap),
        ) {
            items(libraries, key = { it.id ?: it.name ?: it.hashCode() }) { library ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HomeFocusCard(
                        width = HomeStyle.libraryWidth,
                        height = HomeStyle.libraryHeight,
                        corner = HomeStyle.libraryCorner,
                        forcedFocus = false,
                        onClick = { onClick(library) },
                        onMenu = onMenu,
                    ) {
                        val posters = library.latestItems.orEmpty().take(4)
                        if (posters.isEmpty()) {
                            Box(Modifier.fillMaxSize().background(Color(0xFF2A2422)))
                        } else {
                            Row(Modifier.fillMaxSize()) {
                                posters.forEach { child ->
                                    HomeImage(
                                        itemId = child.id,
                                        fallbackArtId = child.seriesId,
                                        url = primaryUrl(serverUrl, child),
                                        role = ArtRole.Primary,
                                        contentDescription = child.name,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                    )
                                }
                            }
                        }
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(28.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
                                    )
                                ),
                        )
                    }
                    CardCaption(library.name.orEmpty(), "", HomeStyle.libraryWidth)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PosterRow(
    title: String,
    items: List<BaseItemDto>,
    serverUrl: String,
    focusedId: String?,
    onClick: (BaseItemDto) -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        SectionTitle(title)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = HomeStyle.gutter, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(HomeStyle.rowGap),
        ) {
            items(items, key = { it.id ?: it.hashCode() }) { item ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HomeFocusCard(
                        width = HomeStyle.posterWidth,
                        height = HomeStyle.posterHeight,
                        corner = HomeStyle.cardCorner,
                        forcedFocus = item.id != null && item.id == focusedId,
                        onClick = { onClick(item) },
                        onMenu = onMenu,
                    ) {
                        HomeImage(
                            itemId = item.id,
                            fallbackArtId = item.seriesId,
                            url = primaryUrl(serverUrl, item),
                            role = ArtRole.Primary,
                            contentDescription = cardTitle(item),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    CardCaption(cardTitle(item), cardMeta(item), HomeStyle.posterWidth)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DetailsPill(
    onClick: () -> Unit,
    onMenu: () -> Unit,
    focusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val tint = if (focused) Color.Black else Color.White
    Surface(
        onClick = onClick,
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { menuKey(it, onMenu) },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF2B2B2B).copy(alpha = 0.94f),
            focusedContainerColor = Color.White,
            pressedContainerColor = Color.White,
            contentColor = Color.White,
            focusedContentColor = Color.Black,
            pressedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1.06f, pressedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(
            border = noBorder(),
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(50),
            ),
            pressedBorder = noBorder(),
        ),
        glow = ClickableSurfaceDefaults.glow(
            glow = Glow.None,
            focusedGlow = Glow.None,
            pressedGlow = Glow.None,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            HomeText(
                text = stringResource(R.string.details),
                fontSize = 14.sp,
                color = tint,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HomeFocusCard(
    width: Dp,
    height: Dp,
    corner: Dp,
    forcedFocus: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    focusRequester: FocusRequester? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    var tvFocused by remember { mutableStateOf(false) }
    val ring = forcedFocus || tvFocused
    val scale by animateFloatAsState(
        targetValue = if (ring) HomeStyle.focusScale else 1f,
        animationSpec = if (forcedFocus) snap() else tween(durationMillis = 160),
        label = "home-card-scale",
    )
    val shape = RoundedCornerShape(corner)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .padding(vertical = 4.dp)
            .width(width)
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { tvFocused = it.isFocused }
            .onKeyEvent { menuKey(it, onMenu) },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF221C19),
            focusedContainerColor = Color(0xFF221C19),
            pressedContainerColor = Color(0xFF221C19),
        ),
        scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f, pressedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            border = noBorder(),
            focusedBorder = noBorder(),
            pressedBorder = noBorder(),
        ),
        glow = ClickableSurfaceDefaults.glow(
            glow = Glow.None,
            focusedGlow = Glow.None,
            pressedGlow = Glow.None,
        ),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape),
                content = content,
            )
            if (ring) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(HomeStyle.focusStroke, Color.White, shape),
                )
            }
        }
    }
}

@Composable
private fun ProgressBar(progress: Float) {
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
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(Color.White),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    HomeText(
        text = text,
        fontSize = HomeStyle.sectionSize,
        color = HomeStyle.title,
        fontWeight = FontWeight.Normal,
        maxLines = 1,
        modifier = Modifier.padding(
            start = HomeStyle.gutter,
            end = HomeStyle.gutter,
            top = 8.dp,
            bottom = 2.dp,
        ),
    )
}

@Composable
private fun CardCaption(title: String, meta: String, width: Dp) {
    Column(
        modifier = Modifier
            .width(width)
            .padding(top = 6.dp),
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
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier.background(Color(0xFF241E1B)),
        )
        else -> Box(modifier.background(Color(0xFF241E1B)))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
private fun noBorder(): Border = Border(
    border = androidx.compose.foundation.BorderStroke(0.dp, Color.Transparent),
)

private fun menuKey(event: KeyEvent, onMenu: () -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (event.key != Key.Menu && event.key != Key.Bookmark) return false
    onMenu()
    return true
}

private fun cardTitle(item: BaseItemDto): String {
    return if (item.type == "Episode") {
        item.seriesName?.takeIf { it.isNotBlank() } ?: item.name.orEmpty()
    } else {
        item.name.orEmpty()
    }
}

private fun cardMeta(item: BaseItemDto): String {
    if (item.type == "Episode" && item.parentIndexNumber != null && item.indexNumber != null) {
        val episodeName = item.name?.takeIf { it.isNotBlank() }.orEmpty()
        val code = "S${item.parentIndexNumber}:E${item.indexNumber}"
        return if (episodeName.isBlank()) code else "$code - $episodeName"
    }
    return item.productionYear?.toString().orEmpty()
}

private fun thumbUrl(serverUrl: String, item: BaseItemDto): String {
    if (serverUrl.isBlank()) return ""
    return Utils.getImageUrl(serverUrl, item, true)
}

private fun primaryUrl(serverUrl: String, item: BaseItemDto): String {
    if (serverUrl.isBlank()) return ""
    return Utils.getImageUrl(serverUrl, item, false)
}

private fun backdropUrl(serverUrl: String, item: BaseItemDto): String {
    if (serverUrl.isBlank()) return ""
    return Utils.getBackdropUrl(serverUrl, item)
}
