package com.xxxx.emby_tv.ui.detail

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.xxxx.emby_tv.R
import com.xxxx.emby_tv.Utils
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.ui.home.ArtRole
import com.xxxx.emby_tv.ui.home.HomeImage
import com.xxxx.emby_tv.ui.home.HomeText
import com.xxxx.emby_tv.ui.home.LocalArtworkSoften
import com.xxxx.emby_tv.ui.home.LocalHomeFont
import com.xxxx.emby_tv.ui.viewmodel.DetailViewModel
import kotlinx.coroutines.yield

enum class SeriesFocus { Hero, Episodes, Cast, Similar }

@Composable
fun SeriesDetailConnected(
    detailViewModel: DetailViewModel,
    serverUrl: String,
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
    LaunchedEffect(season?.id) {
        season?.id?.let { detailViewModel.loadEpisodes(it) }
    }
    val episodes = season?.id?.let { detailViewModel.episodes[it] }.orEmpty()
    SeriesDetailPage(
        series = item,
        seasons = seasons,
        episodes = episodes,
        resume = detailViewModel.resumeItem,
        similar = detailViewModel.similarItems,
        selectedSeason = seasonIndex,
        serverUrl = serverUrl,
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
    selectedSeason: Int,
    serverUrl: String,
    onSelectSeason: (Int) -> Unit,
    onPlay: () -> Unit,
    onOpenEpisode: (BaseItemDto) -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePlayed: () -> Unit,
    onOpenSimilar: (BaseItemDto) -> Unit,
    scrollOffset: Dp = 0.dp,
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
    val people = series.people.orEmpty().filter { it.name?.isNotBlank() == true }
    val resumeIndex = episodes.indexOfFirst { it.id != null && it.id == resume?.id }
        .let { if (it >= 0) it else episodes.indexOfFirst { ep -> ep.indexNumber == resume?.indexNumber && ep.parentIndexNumber == resume?.parentIndexNumber } }
        .coerceAtLeast(0)
    val blurForLower = forceFocus != null && forceFocus != SeriesFocus.Hero
    var blurBackdrop by remember(series.id) { mutableStateOf(blurForLower) }
    val blurRadius by animateDpAsState(if (blurBackdrop) 26.dp else 0.dp, tween(280), label = "series-blur")
    val dim by animateFloatAsState(if (blurBackdrop) 0.52f else 0f, tween(280), label = "series-dim")
    LaunchedEffect(requestInitialFocus, series.id) {
        if (!requestInitialFocus) return@LaunchedEffect
        yield()
        try {
            heroFocus.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }
    val backdrop = if (serverUrl.isBlank()) "" else Utils.getBackdropUrl(serverUrl, series, 1920)
    val featured = resume ?: episodes.getOrNull(resumeIndex)
    CompositionLocalProvider(LocalHomeFont provides fontFamily) {
        Box(modifier.fillMaxSize().background(Color(0xFF12110F)).clipToBounds()) {
            CompositionLocalProvider(LocalArtworkSoften provides blurBackdrop) {
                HomeImage(
                    itemId = series.id,
                    fallbackArtId = null,
                    url = backdrop,
                    role = ArtRole.Backdrop,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().then(if (blurRadius > 0.5.dp) Modifier.blur(blurRadius) else Modifier),
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
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState(), enabled = scrollOffset == 0.dp)
                    .offset(y = scrollOffset)
                    .padding(bottom = 24.dp),
            ) {
                Spacer(Modifier.height(96.dp))
                SeriesTitleBlock(series, featured, Modifier.padding(horizontal = 48.dp))
                Spacer(Modifier.height(16.dp))
                ActionRow(
                    item = series.copy(
                        runTimeTicks = featured?.runTimeTicks ?: series.runTimeTicks,
                        userData = featured?.userData ?: series.userData,
                    ),
                    focusRequester = heroFocus,
                    down = if (seasons.isNotEmpty()) seasonFocus else episodeFocus,
                    forceFocused = forceFocus == SeriesFocus.Hero,
                    onFocused = { blurBackdrop = false },
                    onPlay = onPlay,
                    onVersions = {},
                    onFavorite = onToggleFavorite,
                    onPlayed = onTogglePlayed,
                    showVersions = false,
                    modifier = Modifier.padding(horizontal = 48.dp),
                )
                if (seasons.isNotEmpty()) {
                    SeasonTabs(
                        seasons = seasons,
                        selected = selectedSeason,
                        focusRequester = seasonFocus,
                        up = heroFocus,
                        down = episodeFocus,
                        onSelect = onSelectSeason,
                        onFocused = { blurBackdrop = true },
                    )
                }
                if (episodes.isNotEmpty()) {
                    EpisodeRow(
                        episodes = episodes,
                        serverUrl = serverUrl,
                        focusRequester = episodeFocus,
                        up = if (seasons.isNotEmpty()) seasonFocus else heroFocus,
                        down = if (people.isNotEmpty()) castFocus else if (similar.isNotEmpty()) similarFocus else null,
                        initialWindowStart = resumeIndex,
                        forceFocused = forceFocus == SeriesFocus.Episodes,
                        onFocused = { blurBackdrop = true },
                        onOpen = onOpenEpisode,
                    )
                }
                if (people.isNotEmpty()) {
                    SectionLabel(stringResource(R.string.detail_cast))
                    DetailSlotRow(
                        count = people.size,
                        cardWidth = 108.dp,
                        cardHeight = 108.dp,
                        gap = 22.dp,
                        cornerIsCircle = true,
                        focusRequester = castFocus,
                        up = episodeFocus,
                        down = if (similar.isNotEmpty()) similarFocus else null,
                        forceFocused = forceFocus == SeriesFocus.Cast,
                        onActivate = {},
                        onFocused = { blurBackdrop = true },
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
                        AvatarPlaceholder(person.name.orEmpty(), index, Modifier.fillMaxSize())
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
                if (similar.isNotEmpty()) {
                    SectionLabel(stringResource(R.string.detail_more_like))
                    DetailSlotRow(
                        count = similar.size,
                        cardWidth = 220.dp,
                        cardHeight = 124.dp,
                        gap = 16.dp,
                        cornerIsCircle = false,
                        focusRequester = similarFocus,
                        up = if (people.isNotEmpty()) castFocus else episodeFocus,
                        down = null,
                        forceFocused = forceFocus == SeriesFocus.Similar,
                        onActivate = { onOpenSimilar(similar[it]) },
                        onFocused = { blurBackdrop = true },
                        caption = { index ->
                            val show = similar[index]
                            Column(Modifier.width(220.dp).padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                HomeText(show.name.orEmpty(), 14.sp, Color.White, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                val years = yearRange(show)
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
        }
    }
}

@Composable
private fun SeriesTitleBlock(series: BaseItemDto, episode: BaseItemDto?, modifier: Modifier = Modifier) {
    val genres = series.genres.orEmpty().take(2).joinToString(" / ")
    val rating = series.officialRating.orEmpty()
    val years = yearRange(series)
    val score = series.communityRating?.let { "★ ${"%.1f".format(it)}" }.orEmpty()
    Column(modifier.width(520.dp)) {
        Text(
            text = series.name.orEmpty(),
            color = Color(0xFFD64532),
            fontSize = 52.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (genres.isNotBlank()) {
                HomeText(genres, 13.sp, Color(0xFFE4E4E4))
                HomeText("  ·  ", 13.sp, Color(0xFFBDBDBD))
            }
            if (rating.isNotBlank()) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(0.16f))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    HomeText(rating, 11.sp, Color.White, fontWeight = FontWeight.Medium)
                }
                HomeText("  ·  ", 13.sp, Color(0xFFBDBDBD))
            }
            if (years.isNotBlank()) HomeText(years, 13.sp, Color(0xFFE4E4E4))
            if (score.isNotBlank()) {
                HomeText("  ·  ", 13.sp, Color(0xFFBDBDBD))
                HomeText(score, 13.sp, Color(0xFFE4E4E4))
            }
        }
        if (episode != null) {
            val code = episodeCode(episode)
            Spacer(Modifier.height(12.dp))
            HomeText(code, 15.sp, Color.White, fontWeight = FontWeight.Medium)
            val overview = episode.overview?.takeIf { it.isNotBlank() } ?: series.overview.orEmpty()
            if (overview.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(0.38f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    HomeText(overview, 14.sp, Color(0xFFF2F2F2), maxLines = 2)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val runtime = episode.runTimeTicks?.let { formatClock(it) }.orEmpty()
                if (runtime.isNotBlank()) HomeText(runtime, 14.sp, Color.White)
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF2C2C2C))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    HomeText(qualityLabel(episode), 11.sp, Color.White, fontWeight = FontWeight.Medium)
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
) {
    var slot by remember { mutableIntStateOf(selected.coerceIn(0, seasons.lastIndex)) }
    var rowFocused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .padding(start = 48.dp, top = 22.dp, bottom = 8.dp)
            .focusRequester(focusRequester)
            .focusProperties {
                up?.let { this.up = it }
                down?.let { this.down = it }
            }
            .onFocusChanged {
                rowFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionRight -> {
                        if (slot < seasons.lastIndex) slot++
                        true
                    }
                    Key.DirectionLeft -> {
                        if (slot == 0) false else {
                            slot--
                            true
                        }
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        onSelect(slot)
                        true
                    }
                    else -> false
                }
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        seasons.forEachIndexed { index, season ->
            val shape = RoundedCornerShape(50)
            val isSelected = index == selected
            val showRing = rowFocused && index == slot
            Box(
                Modifier
                    .clip(shape)
                    .background(if (isSelected) Color(0xFFE6E6E8) else Color.Transparent)
                    .clickable {
                        slot = index
                        onSelect(index)
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                HomeText(
                    seasonLabel(season, index),
                    15.sp,
                    if (isSelected) Color(0xFF161616) else Color.White,
                    fontWeight = FontWeight.Medium,
                )
                if (showRing) BreathingStroke(Modifier.matchParentSize(), shape)
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episodes: List<BaseItemDto>,
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
                val number = episode.indexNumber ?: (index + 1)
                val runtime = episode.runTimeTicks?.let { shortRuntime(it) }.orEmpty()
                HomeText(
                    stringResource(R.string.episode_line, number, runtime),
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
                url = if (serverUrl.isBlank()) "" else Utils.getImageUrl(serverUrl, episode, true),
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
