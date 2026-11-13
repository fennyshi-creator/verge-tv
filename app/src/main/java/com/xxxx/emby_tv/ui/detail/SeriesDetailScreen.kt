package com.xxxx.emby_tv.ui.detail

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import coil3.request.transformations
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.xxxx.emby_tv.R
import com.xxxx.emby_tv.Utils
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.ui.home.AmbientWash
import com.xxxx.emby_tv.ui.home.ArtRole
import com.xxxx.emby_tv.ui.home.HomeImage
import com.xxxx.emby_tv.ui.home.HomeText
import com.xxxx.emby_tv.ui.home.LocalArtworkSoften
import com.xxxx.emby_tv.ui.focus.ProvideTvBringIntoView
import com.xxxx.emby_tv.ui.focus.TvBringIntoView
import com.xxxx.emby_tv.ui.home.LocalHomeFont
import com.xxxx.emby_tv.ui.viewmodel.DetailViewModel
import com.xxxx.emby_tv.util.EmbyPresentation
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

enum class SeriesFocus { Hero, Seasons, Episodes, Cast, Similar }

@Composable
fun SeriesDetailConnected(
    detailViewModel: DetailViewModel,
    serverUrl: String,
    apiKey: String = "",
    onPlay: (BaseItemDto) -> Unit,
    onOpenSeries: (String) -> Unit,
    onOpenMovie: (String) -> Unit,
) {
    val item = detailViewModel.mediaInfo ?: return
    LaunchedEffect(item.id) {
        item.id?.let { detailViewModel.loadSeriesDetail(it) }
    }
    val seasons = detailViewModel.seasons.orEmpty()
    var seasonIndex by remember(seasons.map { it.id }, detailViewModel.resumeItem?.seasonId) {
        mutableIntStateOf(initialSeasonIndex(seasons, detailViewModel.resumeItem))
    }
    val season = seasons.getOrNull(seasonIndex)
    LaunchedEffect(item.id, season?.id) {
        val seriesId = item.id
        val seasonId = season?.id
        if (!seriesId.isNullOrBlank() && !seasonId.isNullOrBlank()) {
            detailViewModel.loadEpisodes(seriesId, seasonId)
        }
    }
    val episodes = season?.id?.let { detailViewModel.episodes[it] }.orEmpty()
    SeriesDetailPage(
        series = item,
        seasons = seasons,
        episodes = episodes,
        resume = detailViewModel.resumeItem,
        similar = detailViewModel.similarItems,
        collections = detailViewModel.collections,
        selectedSeason = seasonIndex,
        serverUrl = serverUrl,
        apiKey = apiKey,
        onSelectSeason = { seasonIndex = it },
        onPlay = {
            val target = detailViewModel.resumeItem ?: episodes.firstOrNull() ?: return@SeriesDetailPage
            onPlay(target)
        },
        onOpenEpisode = onPlay,
        onToggleFavorite = {
            val id = item.id ?: return@SeriesDetailPage
            detailViewModel.toggleFavorite(id, item.userData?.isFavorite != true)
        },
        onTogglePlayed = {
            val id = item.id ?: return@SeriesDetailPage
            detailViewModel.togglePlayed(id, item.userData?.played != true)
        },
        onOpenSimilar = { similar ->
            val id = similar.id ?: return@SeriesDetailPage
            if (similar.isSeries || similar.type == "Series") onOpenSeries(id) else onOpenMovie(id)
        },
    )
}

@Composable
fun SeriesDetailPage(
    series: BaseItemDto,
    seasons: List<BaseItemDto>,
    episodes: List<BaseItemDto>,
    resume: BaseItemDto?,
    similar: List<BaseItemDto>,
    collections: List<Pair<BaseItemDto, List<BaseItemDto>>> = emptyList(),
    selectedSeason: Int,
    serverUrl: String,
    apiKey: String = "",
    onSelectSeason: (Int) -> Unit,
    onPlay: () -> Unit,
    onOpenEpisode: (BaseItemDto) -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePlayed: () -> Unit,
    onOpenSimilar: (BaseItemDto) -> Unit,
    scrollOffset: Dp = 0.dp,
    expandBelowHero: Boolean = true,
    forceFocus: SeriesFocus? = null,
    requestInitialFocus: Boolean = true,
    fontFamily: FontFamily = FontFamily.Default,
    modifier: Modifier = Modifier,
) {
    val heroFocus = remember { FocusRequester() }
    val seasonFocus = remember { FocusRequester() }
    val episodeFocus = remember { FocusRequester() }
    val castFocus = remember { FocusRequester() }
    val similarFocus = remember { FocusRequester() }
    val collectionFocus = remember(collections.size) { List(collections.size) { FocusRequester() } }
    val people = series.people.orEmpty().filter { it.name?.isNotBlank() == true }
    val season = seasons.getOrNull(selectedSeason)
    val resumeIndex = episodes.indexOfFirst { it.id != null && it.id == resume?.id }
        .let { if (it >= 0) it else episodes.indexOfFirst { ep -> ep.indexNumber == resume?.indexNumber && ep.parentIndexNumber == resume?.parentIndexNumber } }
        .coerceAtLeast(0)
    val blurForLower = forceFocus != null && forceFocus != SeriesFocus.Hero
    var blurBackdrop by remember(series.id) { mutableStateOf(blurForLower) }
    val scrollState = rememberScrollState()
    val stage = com.xxxx.emby_tv.ui.motion.rememberStageScroll(scrollState)
    var artWash by remember(series.id) { mutableStateOf<String?>(null) }
    fun revealHero() {
        blurBackdrop = false
        stage.dock(0)
    }
    fun showEpisodes() {
        blurBackdrop = true
        stage.dock(1)
    }
    fun dockRow(index: Int) {
        blurBackdrop = true
        stage.dock(index)
    }
    val dim by animateFloatAsState(if (blurBackdrop) 0.18f else 0f, com.xxxx.emby_tv.ui.motion.Motion.fade(), label = "series-dim")
    LaunchedEffect(requestInitialFocus, series.id) {
        if (!requestInitialFocus) return@LaunchedEffect
        yield()
        try {
            heroFocus.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }
    val backdrop = if (serverUrl.isBlank()) "" else Utils.getBackdropUrl(serverUrl, series, 1920)
    val backdropContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(backdrop) {
        if (backdrop.isBlank()) return@LaunchedEffect
        com.xxxx.emby_tv.ui.home.ArtworkPrefetch.enqueueBackdrops(backdropContext, listOf(backdrop))
    }
    val featured = resume ?: episodes.getOrNull(resumeIndex)
    CompositionLocalProvider(LocalHomeFont provides fontFamily) {
        com.xxxx.emby_tv.ui.motion.ProvideStageScroll {
        BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF12110F)).clipToBounds()) {
            val viewport = maxHeight
            CompositionLocalProvider(LocalArtworkSoften provides blurBackdrop) {
                HomeImage(
                    itemId = series.id,
                    fallbackArtId = null,
                    url = backdrop,
                    role = ArtRole.Backdrop,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (dim > 0.01f) Box(Modifier.fillMaxSize().background(Color.Black.copy(dim)))
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(if (blurBackdrop) 0.35f else 0.55f),
                        0.55f to Color.Transparent,
                    )
                )
            )
            stage.stagePx = (constraints.maxHeight * 0.08f).toInt().coerceIn(
                with(LocalDensity.current) { 36.dp.roundToPx() },
                with(LocalDensity.current) { 84.dp.roundToPx() },
            )
            val peekPx = with(LocalDensity.current) { com.xxxx.emby_tv.ui.motion.StageDock.FIRST_PEEK_DP.dp.roundToPx() }
            val heroHeight = with(LocalDensity.current) {
                com.xxxx.emby_tv.ui.motion.StageDock.heroBlockPx(constraints.maxHeight, peekPx).toDp()
            }
            var columnRoot by remember { mutableIntStateOf(0) }
            AmbientWash(
                url = artWash,
                strength = com.xxxx.emby_tv.ui.home.ArtworkWash.strength(
                    onHero = !blurBackdrop,
                    hasArtwork = !artWash.isNullOrBlank(),
                ),
                modifier = Modifier.fillMaxSize(),
            )
            val stageSpacer = with(LocalDensity.current) {
                com.xxxx.emby_tv.ui.motion.StageDock.bottomSpacer(constraints.maxHeight, stage.stagePx).toDp()
            }
            val titleStart = viewport * 0.35f
            val buttonBottom = viewport * 0.79f
            val actionHeight = 52.dp
            var titleHeight by remember(series.id) { mutableStateOf(180.dp) }
            val density = LocalDensity.current
            val beforeButtons = (buttonBottom - actionHeight - titleStart - titleHeight).coerceAtLeast(8.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { columnRoot = it.positionInRoot().y.toInt() }
                    .verticalScroll(scrollState, enabled = scrollOffset == 0.dp)
                    .offset(y = scrollOffset)
                    .padding(bottom = 24.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(if (expandBelowHero && scrollOffset == 0.dp) heroHeight else titleStart + beforeButtons + 160.dp)
                        .onGloballyPositioned {
                            val y = (it.positionInRoot().y - columnRoot + scrollState.value).toInt()
                            stage.record(0, y)
                        },
                ) {
                Column(Modifier.align(Alignment.BottomStart).padding(bottom = 8.dp)) {
                SeriesTitleBlock(
                    series,
                    featured,
                    serverUrl,
                    Modifier
                        .padding(horizontal = 48.dp)
                        .onSizeChanged { titleHeight = with(density) { it.height.toDp() } },
                )
                Spacer(Modifier.height(16.dp))
                ActionRow(
                    item = series,
                    playbackItem = featured ?: series,
                    focusRequester = heroFocus,
                    down = if (episodes.isNotEmpty()) episodeFocus else if (seasons.isNotEmpty()) seasonFocus else null,
                    forceFocused = forceFocus == SeriesFocus.Hero,
                    onFocused = {
                        artWash = null
                        revealHero()
                    },
                    onPlay = onPlay,
                    onFavorite = onToggleFavorite,
                    onPlayed = onTogglePlayed,
                    showVersions = false,
                    modifier = Modifier.padding(horizontal = 48.dp),
                )
                }
                }
                if (seasons.isNotEmpty()) {
                    SeasonTabs(
                        seasons = seasons,
                        selected = selectedSeason,
                        focusRequester = seasonFocus,
                        up = heroFocus,
                        down = episodeFocus,
                        onSelect = onSelectSeason,
                        onFocused = { dockRow(1) },
                        forceFocused = forceFocus == SeriesFocus.Seasons,
                        modifier = Modifier.onGloballyPositioned { coords ->
                            val y = (coords.positionInRoot().y - columnRoot + scrollState.value).toInt()
                            stage.record(1, y)
                        },
                    )
                }
                var seasonDim by remember(series.id) { mutableFloatStateOf(1f) }
                var seenSeason by remember(series.id) { mutableStateOf<String?>(null) }
                LaunchedEffect(season?.id) {
                    val id = season?.id
                    if (seenSeason != null && seenSeason != id) {
                        seasonDim = 0.45f
                        kotlinx.coroutines.delay(60)
                        seasonDim = 1f
                    }
                    seenSeason = id
                }
                val seasonAlpha by animateFloatAsState(seasonDim, com.xxxx.emby_tv.ui.motion.Motion.focus(), label = "season-alpha")
                androidx.compose.runtime.key(season?.id ?: "episodes") {
                if (episodes.isNotEmpty()) {
                    Box(
                        Modifier
                            .graphicsLayer { alpha = seasonAlpha }
                            .onGloballyPositioned {
                                if (seasons.isEmpty()) {
                                    val y = (it.positionInRoot().y - columnRoot + scrollState.value).toInt()
                                    stage.record(1, y)
                                }
                            },
                    ) {
                    EpisodeRow(
                        episodes = episodes,
                        season = season,
                        series = series,
                        apiKey = apiKey,
                        serverUrl = serverUrl,
                        focusRequester = episodeFocus,
                        up = if (seasons.isNotEmpty()) seasonFocus else heroFocus,
                        down = if (people.isNotEmpty()) castFocus else collectionFocus.firstOrNull()
                            ?: if (similar.isNotEmpty()) similarFocus else null,
                        initialWindowStart = resumeIndex,
                        forceFocused = forceFocus == SeriesFocus.Episodes,
                        onFocused = { showEpisodes() },
                        onOpen = onOpenEpisode,
                    )
                    }
                }
                }
                if (people.isNotEmpty()) {
                    Column(Modifier.onGloballyPositioned {
                        val y = (it.positionInRoot().y - columnRoot + scrollState.value).toInt()
                        stage.record(2, y)
                    }) {
                    SectionLabel(stringResource(R.string.detail_cast))
                    DetailSlotRow(
                        count = people.size,
                        cardWidth = 108.dp,
                        cardHeight = 108.dp,
                        gap = 22.dp,
                        cornerIsCircle = true,
                        focusRequester = castFocus,
                        up = episodeFocus,
                        down = collectionFocus.firstOrNull() ?: if (similar.isNotEmpty()) similarFocus else null,
                        forceFocused = forceFocus == SeriesFocus.Cast,
                        peekPrevious = true,
                        onActivate = {},
                        onFocused = { dockRow(2) },
                        caption = { index ->
                            val person = people[index]
                            Column(Modifier.width(108.dp).padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                HomeText(person.name.orEmpty(), 14.sp, Color.White, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                val role = person.role.orEmpty()
                                if (role.isNotBlank()) {
                                    HomeText(role, 12.sp, Color(0xFFA3A3A3), maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        },
                    ) { index ->
                        val person = people[index]
                        val url = EmbyPresentation.personImageUrl(serverUrl, person, apiKey)
                        if (url.isNotBlank()) {
                            coil3.compose.AsyncImage(
                                model = coil3.request.ImageRequest.Builder(
                                    androidx.compose.ui.platform.LocalContext.current,
                                ).data(url).build(),
                                contentDescription = person.name,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            AvatarPlaceholder(person.name.orEmpty(), index, Modifier.fillMaxSize())
                        }
                    }
                    HomeText(
                        stringResource(R.string.more_options_hint),
                        11.sp,
                        Color.White.copy(0.55f),
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp, vertical = 2.dp),
                    )
                    }
                }
                collections.forEachIndexed { index, (box, works) ->
                    Column(Modifier.onGloballyPositioned {
                        val y = (it.positionInRoot().y - columnRoot + scrollState.value).toInt()
                        stage.record(3 + index, y)
                    }) {
                    SectionLabel(EmbyPresentation.collectionRowTitle(box.name.orEmpty()))
                    DetailSlotRow(
                        count = works.size,
                        cardWidth = 220.dp,
                        cardHeight = 124.dp,
                        gap = 16.dp,
                        cornerIsCircle = false,
                        focusRequester = collectionFocus[index],
                        up = when {
                            index > 0 -> collectionFocus[index - 1]
                            people.isNotEmpty() -> castFocus
                            else -> episodeFocus
                        },
                        down = collectionFocus.getOrNull(index + 1)
                            ?: if (similar.isNotEmpty()) similarFocus else null,
                        forceFocused = false,
                        peekPrevious = true,
                        onActivate = { onOpenSimilar(works[it]) },
                        onFocused = { dockRow(3 + index) },
                        onIndex = { i ->
                            artWash = if (serverUrl.isBlank()) null else Utils.getImageUrl(serverUrl, works[i], true)
                        },
                        caption = { i ->
                            HomeText(works[i].name.orEmpty(), 14.sp, Color.White, maxLines = 1, modifier = Modifier.width(220.dp).padding(top = 8.dp))
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
                        val y = (it.positionInRoot().y - columnRoot + scrollState.value).toInt()
                        stage.record(3 + collections.size, y)
                    }) {
                    SectionLabel(stringResource(R.string.detail_more_like))
                    DetailSlotRow(
                        count = similar.size,
                        cardWidth = 220.dp,
                        cardHeight = 124.dp,
                        gap = 16.dp,
                        cornerIsCircle = false,
                        focusRequester = similarFocus,
                        up = collectionFocus.lastOrNull() ?: if (people.isNotEmpty()) castFocus else episodeFocus,
                        down = null,
                        forceFocused = forceFocus == SeriesFocus.Similar,
                        peekPrevious = true,
                        onActivate = { onOpenSimilar(similar[it]) },
                        onFocused = { dockRow(3 + collections.size) },
                        onIndex = { i ->
                            artWash = if (serverUrl.isBlank()) null else Utils.getImageUrl(serverUrl, similar[i], true)
                        },
                        caption = { index ->
                            val show = similar[index]
                            Column(Modifier.width(220.dp).padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                HomeText(show.name.orEmpty(), 14.sp, Color.White, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                val years = EmbyPresentation.yearLabel(show)
                                if (years.isNotBlank()) {
                                    HomeText(years, 12.sp, Color(0xFFA3A3A3), maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        },
                    ) { index ->
                        val show = similar[index]
                        HomeImage(
                            itemId = show.id,
                            fallbackArtId = null,
                            url = if (serverUrl.isBlank()) "" else Utils.getImageUrl(serverUrl, show, true),
                            role = ArtRole.Thumb,
                            contentDescription = show.name,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    }
                }
                Spacer(Modifier.height(stageSpacer))
            }
        }
        }
    }
}

@Composable
private fun SeriesTitleBlock(series: BaseItemDto, episode: BaseItemDto?, serverUrl: String, modifier: Modifier = Modifier) {
    val genres = EmbyPresentation.genresLine(series)
    val rating = series.officialRating.orEmpty()
    val years = EmbyPresentation.yearLabel(series)
    val score = EmbyPresentation.starLabel(series.communityRating).orEmpty()
    Column(modifier.width(520.dp)) {
        val logo = EmbyPresentation.logoUrl(serverUrl, series)
        if (logo.isNotBlank()) {
            coil3.compose.AsyncImage(
                model = coil3.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current).data(logo).build(),
                contentDescription = EmbyPresentation.displayName(series),
                alignment = Alignment.CenterStart,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.height(56.dp).fillMaxWidth(),
            )
        } else {
            Text(
                text = EmbyPresentation.displayName(series),
                color = Color(0xFFD64532),
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
            if (years.isNotBlank()) HomeText(years, 12.sp, Color(0xFFE4E4E4))
            if (score.isNotBlank()) {
                HomeText("  ·  ", 12.sp, Color(0xFFBDBDBD))
                HomeText(score, 12.sp, Color(0xFFE4E4E4))
            }
        }
        if (episode != null) {
            val code = EmbyPresentation.episodeHeading(episode)
            Spacer(Modifier.height(8.dp))
            HomeText(code, 13.sp, Color.White, fontWeight = FontWeight.Medium)
            val overview = episode.overview?.takeIf { it.isNotBlank() } ?: series.overview.orEmpty()
            if (overview.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                HomeText(overview, 13.sp, Color(0xFFE8E8E8), maxLines = 2)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val runtime = EmbyPresentation.runtimeLabel(episode.runTimeTicks).orEmpty()
                if (runtime.isNotBlank()) HomeText(runtime, 13.sp, Color.White)
                val badge = EmbyPresentation.qualityBadge(episode)
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
}

@Composable
private fun SeasonTabs(
    seasons: List<BaseItemDto>,
    selected: Int,
    focusRequester: FocusRequester,
    up: FocusRequester?,
    down: FocusRequester?,
    onSelect: (Int) -> Unit,
    onFocused: () -> Unit,
    forceFocused: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (seasons.isEmpty()) return
    var slot by remember { mutableIntStateOf(selected.coerceIn(0, seasons.lastIndex)) }
    var rowFocused by remember { mutableStateOf(forceFocused) }
    val chipLeft = remember(seasons.size) { mutableStateMapOf<Int, Int>() }
    val chipWidth = remember(seasons.size) { mutableStateMapOf<Int, Int>() }
    var rowLeft by remember { mutableIntStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val targetLeft = chipLeft[slot] ?: 0
    val targetWidth = chipWidth[slot] ?: 0
    val pillX by animateDpAsState(
        with(density) { targetLeft.toDp() },
        com.xxxx.emby_tv.ui.motion.Motion.focus(),
        label = "season-pill-x",
    )
    val pillW by animateDpAsState(
        with(density) { targetWidth.toDp() },
        com.xxxx.emby_tv.ui.motion.Motion.focus(),
        label = "season-pill-w",
    )
    val pillOnTabs = rowFocused || forceFocused
    Box(
        modifier
            .padding(start = 48.dp, top = 22.dp, bottom = 8.dp)
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
            .onGloballyPositioned { rowLeft = it.positionInWindow().x.roundToInt() }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionRight -> {
                        if (slot < seasons.lastIndex) {
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
    ) {
        if (targetWidth > 0) {
            Box(
                Modifier
                    .offset(x = pillX)
                    .width(pillW)
                    .height(32.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (pillOnTabs) Color.White else Color(0xFF4A4A4A)),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            seasons.forEachIndexed { index, season ->
                val face = com.xxxx.emby_tv.ui.motion.FocusPill.face(pillOnTabs, index == slot)
                val on = face == com.xxxx.emby_tv.ui.motion.FocusPill.Face.Focused
                Box(
                    Modifier
                        .onGloballyPositioned { coords ->
                            chipLeft[index] = coords.positionInWindow().x.roundToInt() - rowLeft
                            chipWidth[index] = coords.size.width
                        }
                        .clip(RoundedCornerShape(50))
                        .background(if (on && targetWidth == 0) Color.White else Color.Transparent)
                        .clickable {
                            slot = index
                            onSelect(index)
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    HomeText(
                        EmbyPresentation.seasonTabLabel(season).ifBlank { seasonLabel(season, index) },
                        15.sp,
                        if (on) Color(0xFF161616) else Color.White,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episodes: List<BaseItemDto>,
    season: BaseItemDto?,
    series: BaseItemDto,
    apiKey: String,
    serverUrl: String,
    focusRequester: FocusRequester,
    up: FocusRequester?,
    down: FocusRequester?,
    initialWindowStart: Int,
    forceFocused: Boolean,
    onFocused: () -> Unit,
    onOpen: (BaseItemDto) -> Unit,
) {
    DetailSlotRow(
        count = episodes.size,
        cardWidth = 248.dp,
        cardHeight = 140.dp,
        gap = 16.dp,
        cornerIsCircle = false,
        focusRequester = focusRequester,
        up = up,
        down = down,
        forceFocused = forceFocused,
        onActivate = { onOpen(episodes[it]) },
        initialWindowStart = initialWindowStart,
        peekPrevious = true,
        onFocused = onFocused,
        caption = { index ->
            val episode = episodes[index]
            Column(Modifier.width(248.dp).padding(top = 8.dp)) {
                HomeText(episode.name.orEmpty(), 15.sp, Color.White, maxLines = 1)
                HomeText(
                    EmbyPresentation.episodeMeta(episode),
                    12.sp,
                    Color(0xFFB5B5B5),
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
                val overview = episode.overview.orEmpty()
                if (overview.isNotBlank()) {
                    HomeText(overview, 12.sp, Color(0xFFD0D0D0), maxLines = 2, modifier = Modifier.padding(top = 4.dp))
                }
            }
        },
    ) { index ->
        val episode = episodes[index]
        Box(Modifier.fillMaxSize()) {
            HomeImage(
                itemId = episode.id,
                fallbackArtId = null,
                url = EmbyPresentation.episodeStillUrl(serverUrl, episode, season, series, apiKey),
                role = ArtRole.Thumb,
                contentDescription = episode.name,
                modifier = Modifier.fillMaxSize(),
            )
            if (episode.playbackProgress > 0.02f) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.Black.copy(0.45f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(episode.playbackProgress.coerceIn(0f, 1f))
                            .background(Color.White),
                    )
                }
            }
        }
    }
}

private fun initialSeasonIndex(seasons: List<BaseItemDto>, resume: BaseItemDto?): Int {
    if (seasons.isEmpty()) return 0
    val byId = seasons.indexOfFirst { it.id != null && it.id == resume?.seasonId }
    if (byId >= 0) return byId
    val number = resume?.parentIndexNumber
    if (number != null) {
        val byNumber = seasons.indexOfFirst { it.indexNumber == number }
        if (byNumber >= 0) return byNumber
        return (number - 1).coerceIn(0, seasons.lastIndex)
    }
    return 0
}

private fun seasonLabel(season: BaseItemDto, index: Int): String {
    val number = season.indexNumber ?: (index + 1)
    return "第${number}季"
}

private fun episodeCode(episode: BaseItemDto): String {
    val season = episode.parentIndexNumber
    val number = episode.indexNumber
    val title = episode.name.orEmpty()
    return if (season != null && number != null) "S$season:E$number - $title" else title
}

private fun yearRange(item: BaseItemDto): String {
    val start = item.productionYear?.toString().orEmpty()
    if (start.isBlank()) return ""
    val ongoing = item.status.equals("Continuing", true) || item.status == "现在"
    val end = when {
        ongoing -> "现在"
        !item.endDate.isNullOrBlank() -> item.endDate.take(4)
        else -> ""
    }
    return if (end.isBlank() || end == start) start else "$start - $end"
}

private fun qualityLabel(item: BaseItemDto): String {
    val height = item.height ?: item.mediaSources.orEmpty()
        .flatMap { it.mediaStreams.orEmpty() }
        .maxOfOrNull { it.height ?: 0 } ?: 0
    return if (height >= 2000) "4K" else "1080p"
}

private fun shortRuntime(ticks: Long): String {
    val minutes = (ticks / 10_000_000L / 60L).coerceAtLeast(0L)
    return "${minutes}m"
}
