package com.xxxx.emby_tv.ui.motion

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import com.xxxx.emby_tv.ui.home.HeavyBlurTransformation

/**
 * Poster, thumb, or backdrop. Fades in, requests the same pixel size the
 * prefetcher uses, and blurs on the GPU from API 31. Older boxes use the
 * cached software blur when [soften] is set.
 */
@Composable
fun FadeInImage(
    url: String,
    widthPx: Int,
    heightPx: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    soften: Boolean = false,
    placeholder: Color = Color(0xFF241E1B),
) {
    if (url.isBlank()) {
        Box(modifier.background(placeholder))
        return
    }
    val hardwareBlur = soften && Build.VERSION.SDK_INT >= 31
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .size(widthPx, heightPx)
            .crossfade(Motion.imageMs)
            .apply { if (soften && !hardwareBlur) transformations(HeavyBlurTransformation()) }
            .build(),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .background(placeholder)
            .then(if (hardwareBlur) Modifier.blur(32.dp) else Modifier),
    )
}
