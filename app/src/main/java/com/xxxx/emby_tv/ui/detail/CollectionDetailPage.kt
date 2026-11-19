package com.xxxx.emby_tv.ui.detail

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.xxxx.emby_tv.Utils
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.ui.home.ArtRole
import com.xxxx.emby_tv.ui.home.ArtworkWash
import com.xxxx.emby_tv.ui.home.HomeImage
import com.xxxx.emby_tv.ui.home.HomeStyle
import com.xxxx.emby_tv.ui.home.HomeText
import com.xxxx.emby_tv.ui.home.PosterRowFit
import com.xxxx.emby_tv.ui.motion.FadeInImage
import com.xxxx.emby_tv.ui.motion.Motion
import com.xxxx.emby_tv.ui.motion.TvLeadingRow
import com.xxxx.emby_tv.util.EmbyPresentation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CollectionDetailPage(
    box: BaseItemDto?,
    children: List<BaseItemDto>?,
    serverUrl: String,
    onOpenItem: (BaseItemDto) -> Unit,
) {
    val items = children.orEmpty()
    var restoreId by rememberSaveable(box?.id) { mutableStateOf<String?>(null) }
    var focusedId by remember(box?.id) { mutableStateOf(restoreId ?: items.firstOrNull()?.id) }
    val current = items.firstOrNull { it.id == focusedId } ?: items.firstOrNull()
    val backdrop = when {
        current != null && serverUrl.isNotBlank() -> Utils.getBackdropUrl(serverUrl, current, 1280)
        box != null && serverUrl.isNotBlank() -> Utils.getBackdropUrl(serverUrl, box, 1280)
        else -> ""
    }
    val rowFocus = remember { FocusRequester() }
    val restoreIndex = items.indexOfFirst { it.id == restoreId }
    BoxWithConstraints(Modifier.fillMaxSize().background(HomeStyle.background)) {
        val density = LocalDensity.current
        val card = with(density) {
            val px = PosterRowFit.cardWidthPx(
                viewportPx = constraints.maxWidth,
                insetPx = HomeStyle.gutter.roundToPx(),
                gapPx = HomeStyle.rowGap.roundToPx(),
            )
            (px / density.density).dp
        }
        PaletteStill(url = backdrop, modifier = Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Column(Modifier.padding(start = HomeStyle.gutter, end = 280.dp, bottom = 28.dp)) {
                HomeText(
                    text = box?.name.orEmpty(),
                    fontSize = 34.sp,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
                val meta = EmbyPresentation.collectionMetaLine(box?.genres, box?.officialRating)
                if (meta.isNotBlank()) {
                    HomeText(
                        text = meta,
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.82f),
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
            when {
                children == null -> HomeText("加载中", 16.sp, HomeStyle.meta, modifier = Modifier.padding(start = HomeStyle.gutter))
                items.isEmpty() -> HomeText("这个专题还没有影片", 16.sp, HomeStyle.meta, modifier = Modifier.padding(start = HomeStyle.gutter))
                else -> {
                    LaunchedEffect(items.firstOrNull()?.id, restoreId) {
                        try {
                            rowFocus.requestFocus()
                        } catch (_: IllegalStateException) {
                        }
                    }
                    TvLeadingRow(
                        items = items,
                        cardWidth = card,
                        cardHeight = card * 3f / 2f,
                        corner = HomeStyle.cardCorner,
                        focusRequester = rowFocus,
                        up = null,
                        down = null,
                        initialIndex = restoreIndex,
                        forceFocused = restoreIndex >= 0,
                        imageUrls = items.map { if (serverUrl.isBlank()) "" else Utils.getImageUrl(serverUrl, it, false) },
                        backdropUrls = items.map { if (serverUrl.isBlank()) "" else Utils.getBackdropUrl(serverUrl, it, 1280) },
                        prefetchRole = ArtRole.Primary,
                        onActivate = { item ->
                            restoreId = item.id
                            focusedId = item.id
                            onOpenItem(item)
                        },
                        onMenu = {},
                        onFocusedIndex = { index ->
                            items.getOrNull(index)?.id?.let { focusedId = it }
                        },
                        caption = { item ->
                            Column(Modifier.padding(top = 8.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                HomeText(item.name.orEmpty(), 14.sp, Color.White, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                val year = item.productionYear?.toString().orEmpty()
                                if (year.isNotBlank()) {
                                    HomeText(year, 12.sp, HomeStyle.meta, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        },
                    ) { item ->
                        HomeImage(
                            itemId = item.id,
                            fallbackArtId = null,
                            url = if (serverUrl.isBlank()) "" else Utils.getImageUrl(serverUrl, item, false),
                            role = ArtRole.Primary,
                            contentDescription = item.name,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(HomeStyle.cardCorner)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaletteStill(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var sampled by remember { mutableStateOf(HomeStyle.background) }
    LaunchedEffect(url) {
        if (url.isBlank()) {
            sampled = HomeStyle.background
            return@LaunchedEffect
        }
        val next = withContext(Dispatchers.IO) {
            try {
                val result = SingletonImageLoader.get(context).execute(
                    ImageRequest.Builder(context).data(url).size(64, 36).build(),
                )
                val image = (result as? SuccessResult)?.image ?: return@withContext null
                val raw = image.toBitmap()
                val bitmap = if (ArtworkWash.needsSoftwareCopy(raw.config?.name)) {
                    raw.copy(android.graphics.Bitmap.Config.ARGB_8888, false) ?: return@withContext null
                } else {
                    raw
                }
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                ArtworkWash.sampleMuted(pixels)?.let { Color(it) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
        if (next != null) sampled = next
    }
    val color by animateColorAsState(sampled, Motion.fade(), label = "collection-palette")
    Box(modifier.background(color)) {
        if (url.isNotBlank()) {
            FadeInImage(
                url = url,
                widthPx = 1280,
                heightPx = 720,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to color.copy(alpha = 0.18f),
                        0.42f to color.copy(alpha = 0.34f),
                        0.72f to color.copy(alpha = 0.78f),
                        1f to color,
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to color.copy(alpha = 0.72f),
                        0.46f to color.copy(alpha = 0.28f),
                        1f to Color.Transparent,
                    ),
                ),
        )
    }
}
