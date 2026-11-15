package com.xxxx.emby_tv.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xxxx.emby_tv.Utils
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.ui.home.AmbientWash
import com.xxxx.emby_tv.ui.home.ArtRole
import com.xxxx.emby_tv.ui.home.ArtworkWash
import com.xxxx.emby_tv.ui.home.HomeImage
import com.xxxx.emby_tv.ui.home.HomeStyle
import com.xxxx.emby_tv.ui.home.HomeText
import com.xxxx.emby_tv.ui.home.PosterRowFit
import com.xxxx.emby_tv.ui.motion.TvLeadingRow
import com.xxxx.emby_tv.util.EmbyPresentation
import java.util.Calendar

@Composable
fun PersonDetailPage(
    person: BaseItemDto?,
    movies: List<BaseItemDto>,
    series: List<BaseItemDto>,
    serverUrl: String,
    onOpenItem: (BaseItemDto) -> Unit,
) {
    var restoreKey by rememberSaveable(person?.id) { mutableStateOf<String?>(null) }
    val movieFocus = remember { FocusRequester() }
    val seriesFocus = remember { FocusRequester() }
    val portrait = if (person == null || serverUrl.isBlank()) "" else EmbyPresentation.viewPrimaryImageUrl(serverUrl, person, 400)
    val today = Calendar.getInstance()
    val facts = EmbyPresentation.personFactLine(
        person?.premiereDate,
        person?.endDate,
        person?.productionLocations,
        today.get(Calendar.YEAR),
        today.get(Calendar.MONTH) + 1,
        today.get(Calendar.DAY_OF_MONTH),
    )
    Box(Modifier.fillMaxSize().background(HomeStyle.background)) {
        AmbientWash(
            url = portrait.ifBlank { movies.firstOrNull()?.let { Utils.getImageUrl(serverUrl, it, false) } },
            strength = ArtworkWash.strength(onHero = false, hasArtwork = portrait.isNotBlank() || movies.isNotEmpty()),
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 36.dp, bottom = 28.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = HomeStyle.gutter),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f).padding(end = 28.dp)) {
                    HomeText(
                        text = person?.name.orEmpty(),
                        fontSize = 36.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                    )
                    if (facts.isNotBlank()) {
                        HomeText(
                            text = facts,
                            fontSize = 16.sp,
                            color = Color.White.copy(alpha = 0.88f),
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    val bio = person?.overview?.trim().orEmpty()
                    if (bio.isNotBlank()) {
                        HomeText(
                            text = bio,
                            fontSize = 15.sp,
                            color = HomeStyle.meta,
                            maxLines = 4,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                    }
                }
                if (portrait.isNotBlank()) {
                    HomeImage(
                        itemId = person?.id,
                        fallbackArtId = null,
                        url = portrait,
                        role = ArtRole.Primary,
                        contentDescription = person?.name,
                        modifier = Modifier
                            .size(148.dp)
                            .clip(CircleShape),
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            if (person == null) {
                HomeText("加载中", 16.sp, HomeStyle.meta, modifier = Modifier.padding(start = HomeStyle.gutter))
            } else if (movies.isEmpty() && series.isEmpty()) {
                HomeText("还没有作品", 16.sp, HomeStyle.meta, modifier = Modifier.padding(start = HomeStyle.gutter))
            } else {
                if (movies.isNotEmpty()) {
                    WorkRow(
                        label = "电影",
                        items = movies,
                        serverUrl = serverUrl,
                        focus = movieFocus,
                        up = null,
                        down = if (series.isNotEmpty()) seriesFocus else null,
                        restoreKey = restoreKey,
                        rowKey = "movie",
                        onOpen = { item ->
                            restoreKey = "movie:${item.id}"
                            onOpenItem(item)
                        },
                    )
                }
                if (series.isNotEmpty()) {
                    WorkRow(
                        label = "剧集",
                        items = series,
                        serverUrl = serverUrl,
                        focus = seriesFocus,
                        up = if (movies.isNotEmpty()) movieFocus else null,
                        down = null,
                        restoreKey = restoreKey,
                        rowKey = "series",
                        onOpen = { item ->
                            restoreKey = "series:${item.id}"
                            onOpenItem(item)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkRow(
    label: String,
    items: List<BaseItemDto>,
    serverUrl: String,
    focus: FocusRequester,
    up: FocusRequester?,
    down: FocusRequester?,
    restoreKey: String?,
    rowKey: String,
    onOpen: (BaseItemDto) -> Unit,
) {
    val restoreIndex = if (restoreKey?.startsWith("$rowKey:") == true) {
        items.indexOfFirst { it.id == restoreKey.removePrefix("$rowKey:") }
    } else {
        -1
    }
    if (restoreIndex >= 0) {
        LaunchedEffect(restoreKey) {
            try {
                focus.requestFocus()
            } catch (_: IllegalStateException) {
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        HomeText(
            text = label,
            fontSize = 22.sp,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = HomeStyle.gutter, bottom = 8.dp),
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val card = with(density) {
                val px = PosterRowFit.cardWidthPx(
                    viewportPx = constraints.maxWidth,
                    insetPx = HomeStyle.gutter.roundToPx(),
                    gapPx = HomeStyle.rowGap.roundToPx(),
                )
                (px / density.density).dp
            }
            TvLeadingRow(
                items = items,
                cardWidth = card,
                cardHeight = card * 3f / 2f,
                corner = HomeStyle.cardCorner,
                focusRequester = focus,
                up = up,
                down = down,
                initialIndex = restoreIndex,
                forceFocused = restoreIndex >= 0,
                imageUrls = items.map { if (serverUrl.isBlank()) "" else Utils.getImageUrl(serverUrl, it, false) },
                prefetchRole = ArtRole.Primary,
                onActivate = onOpen,
                onMenu = {},
                caption = { item ->
                    Column(
                        Modifier.width(card).padding(top = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        HomeText(item.name.orEmpty(), 14.sp, Color.White, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        val year = item.productionYear?.let { year ->
                            val end = item.endDate?.take(4)?.takeIf { it != year.toString() }
                            if (end != null) "$year - $end" else year.toString()
                        }.orEmpty()
                        if (year.isNotBlank()) {
                            HomeText(year, 12.sp, HomeStyle.meta, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                    }
                },
            ) { item ->
                HomeImage(
                    itemId = item.id,
                    fallbackArtId = item.seriesId,
                    url = if (serverUrl.isBlank()) "" else Utils.getImageUrl(serverUrl, item, false),
                    role = ArtRole.Primary,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.RoundedCornerShape(HomeStyle.cardCorner)),
                )
            }
        }
    }
}
