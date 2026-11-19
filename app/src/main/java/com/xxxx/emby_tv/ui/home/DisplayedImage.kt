package com.xxxx.emby_tv.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlin.math.max

/**
 * Card and avatar requests follow the density used for layout, so a 4K
 * panel fetches the pixels that are actually on screen. Backdrop stills
 * stay on their existing sizes.
 */
object DisplayedImage {
    const val BASELINE_DENSITY = 2f

    fun scale(density: Float): Float {
        if (density <= BASELINE_DENSITY) return 1f
        return density / BASELINE_DENSITY
    }

    fun raiseMaxWidth(url: String, widthPx: Int, density: Float): String {
        if (url.isBlank() || widthPx <= 0 || scale(density) == 1f) return url
        if (!url.contains("maxWidth=")) return url
        return Regex("maxWidth=(\\d+)").replace(url) { match ->
            val current = match.groupValues[1].toIntOrNull() ?: return@replace match.value
            "maxWidth=${max(current, widthPx)}"
        }
    }
}

@Composable
fun CastAvatar(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 108.dp,
) {
    if (url.isBlank()) return
    val density = LocalDensity.current
    val px = with(density) { size.roundToPx() }.coerceAtLeast(1)
    val sized = DisplayedImage.raiseMaxWidth(url, px, density.density)
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(sized)
            .size(px, px)
            .build(),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}
