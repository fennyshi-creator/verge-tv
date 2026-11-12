package com.xxxx.emby_tv.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xxxx.emby_tv.ui.home.HomePreviewChrome

private const val TV = "spec:width=960dp,height=540dp,dpi=320"

@Composable
private fun SeriesFrame(
    fontFamily: FontFamily,
    scrollOffset: androidx.compose.ui.unit.Dp = 0.dp,
    focus: SeriesFocus,
    expandBelowHero: Boolean = focus == SeriesFocus.Hero,
) {
    HomePreviewChrome(fontFamily) {
        SeriesDetailPage(
            series = SeriesDetailSample.series,
            seasons = SeriesDetailSample.seasons,
            episodes = SeriesDetailSample.episodes,
            resume = SeriesDetailSample.resume,
            similar = SeriesDetailSample.similar,
            selectedSeason = 1,
            serverUrl = "",
            onSelectSeason = {},
            onPlay = {},
            onOpenEpisode = {},
            onToggleFavorite = {},
            onTogglePlayed = {},
            onOpenSimilar = {},
            scrollOffset = scrollOffset,
            expandBelowHero = expandBelowHero,
            forceFocus = focus,
            requestInitialFocus = false,
            fontFamily = fontFamily,
        )
    }
}

@Preview(name = "series-detail-first", device = TV, locale = "zh")
@Composable
fun SeriesDetailFirstPreview(fontFamily: FontFamily = FontFamily.Default) {
    SeriesFrame(fontFamily, focus = SeriesFocus.Hero, expandBelowHero = true)
}

@Preview(name = "series-detail-second", device = TV, locale = "zh")
@Composable
fun SeriesDetailSecondPreview(fontFamily: FontFamily = FontFamily.Default) {
    SeriesFrame(fontFamily, scrollOffset = (-210).dp, focus = SeriesFocus.Episodes, expandBelowHero = false)
}

@Preview(name = "series-hero", device = TV, locale = "zh")
@Composable
fun SeriesHeroPreview(fontFamily: FontFamily = FontFamily.Default) {
    SeriesFrame(fontFamily, focus = SeriesFocus.Hero)
}

@Preview(name = "series-seasons-episodes", device = TV, locale = "zh")
@Composable
fun SeriesEpisodesPreview(fontFamily: FontFamily = FontFamily.Default) {
    SeriesFrame(fontFamily, scrollOffset = (-300).dp, focus = SeriesFocus.Episodes)
}

@Preview(name = "series-season-focused", device = TV, locale = "zh")
@Composable
fun SeriesSeasonFocusedPreview(fontFamily: FontFamily = FontFamily.Default) {
    HomePreviewChrome(fontFamily) {
        SeriesDetailPage(
            series = SeriesDetailSample.series,
            seasons = SeriesDetailSample.seasons,
            episodes = SeriesDetailSample.seasonThree,
            resume = null,
            similar = SeriesDetailSample.similar,
            selectedSeason = 2,
            serverUrl = "",
            onSelectSeason = {},
            onPlay = {},
            onOpenEpisode = {},
            onToggleFavorite = {},
            onTogglePlayed = {},
            onOpenSimilar = {},
            scrollOffset = (-200).dp,
            forceFocus = SeriesFocus.Seasons,
            requestInitialFocus = false,
            fontFamily = fontFamily,
        )
    }
}

@Preview(name = "series-cast-similar", device = TV, locale = "zh")
@Composable
fun SeriesCastPreview(fontFamily: FontFamily = FontFamily.Default) {
    SeriesFrame(fontFamily, scrollOffset = (-700).dp, focus = SeriesFocus.Cast)
}
