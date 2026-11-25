package com.xxxx.emby_tv.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xxxx.emby_tv.ui.home.HomePreviewChrome

private const val TV = "spec:width=960dp,height=540dp,dpi=320"

@Preview(name = "detail-hero", device = TV, locale = "zh")
@Composable
fun DetailHeroPreview(fontFamily: FontFamily = FontFamily.Default) {
    HomePreviewChrome(fontFamily) {
        MovieDetailPage(
            item = MovieDetailSample.movie,
            similar = MovieDetailSample.similar,
            serverUrl = "",
            onPlay = { _, _ -> },
            onToggleFavorite = {},
            onTogglePlayed = {},
            onOpenSimilar = {},
            forceFocus = DetailFocus.Play,
            requestInitialFocus = false,
            fontFamily = fontFamily,
        )
    }
}

@Preview(name = "detail-cast-similar", device = TV, locale = "zh")
@Composable
fun DetailCastPreview(fontFamily: FontFamily = FontFamily.Default) {
    HomePreviewChrome(fontFamily) {
        MovieDetailPage(
            item = MovieDetailSample.movie,
            similar = MovieDetailSample.similar,
            serverUrl = "",
            onPlay = { _, _ -> },
            onToggleFavorite = {},
            onTogglePlayed = {},
            onOpenSimilar = {},
            scrollOffset = (-430).dp,
            forceFocus = DetailFocus.Cast,
            requestInitialFocus = false,
            fontFamily = fontFamily,
        )
    }
}

@Preview(name = "movie-version-picker", device = TV, locale = "zh")
@Composable
fun DetailVersionPreview(fontFamily: FontFamily = FontFamily.Default) {
    HomePreviewChrome(fontFamily) {
        MovieDetailPage(
            item = MovieDetailSample.movie,
            similar = MovieDetailSample.similar,
            serverUrl = "",
            onPlay = { _, _ -> },
            onToggleFavorite = {},
            onTogglePlayed = {},
            onOpenSimilar = {},
            forceFocus = DetailFocus.Version,
            requestInitialFocus = false,
            fontFamily = fontFamily,
        )
    }
}
