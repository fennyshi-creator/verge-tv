package com.xxxx.emby_tv.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xxxx.emby_tv.R
import java.util.Locale

private const val TV = "spec:width=960dp,height=540dp,dpi=320"

@Composable
fun HomePreviewChrome(fontFamily: FontFamily = FontFamily.Default, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val wrapped = remember(base) {
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.SIMPLIFIED_CHINESE)
        base.createConfigurationContext(config)
    }
    CompositionLocalProvider(
        LocalContext provides wrapped,
        LocalHomeFont provides fontFamily,
    ) {
        content()
    }
}

@Preview(name = "home-top", device = TV, locale = "zh", showBackground = true)
@Composable
fun HomeTopPreview(fontFamily: FontFamily = FontFamily.Default) {
    HomePreviewChrome(fontFamily) { HomeTopBody() }
}

@Preview(name = "home-movies", device = TV, locale = "zh", showBackground = true)
@Composable
fun HomeMoviesPreview(fontFamily: FontFamily = FontFamily.Default) {
    HomePreviewChrome(fontFamily) { HomeMoviesBody() }
}

@Preview(name = "home-recommended", device = TV, locale = "zh", showBackground = true)
@Composable
fun HomeRecommendedPreview(fontFamily: FontFamily = FontFamily.Default) {
    HomePreviewChrome(fontFamily) { HomeRecommendedBody() }
}

@Composable
fun HomeTopBody() {
    Column(
        Modifier
            .fillMaxSize()
            .background(HomeStyle.background),
    ) {
        HeroBanner(
            item = HomeSampleData.hero,
            serverUrl = "",
            onDetails = {},
            onMenu = {},
            focusRequester = null,
        )
        ContinueRow(
            title = stringResource(R.string.continue_watching),
            items = HomeSampleData.resume,
            serverUrl = "",
            focusedId = "sample-odyssey",
            onClick = {},
            onMenu = {},
            focusRequester = null,
        )
        LibraryRow(
            title = stringResource(R.string.home_libraries),
            libraries = HomeSampleData.libraries,
            serverUrl = "",
            onClick = {},
            onMenu = {},
        )
    }
}

@Composable
fun HomeMoviesBody() {
    Column(
        Modifier
            .fillMaxSize()
            .background(HomeStyle.background)
            .padding(top = 28.dp),
    ) {
        PosterRow(
            title = "电影Movies",
            items = HomeSampleData.movies,
            serverUrl = "",
            focusedId = "sample-odyssey",
            onClick = {},
            onMenu = {},
        )
        PosterRow(
            title = "剧集Series",
            items = HomeSampleData.series,
            serverUrl = "",
            focusedId = null,
            onClick = {},
            onMenu = {},
        )
    }
}

@Composable
fun HomeRecommendedBody() {
    Box(
        Modifier
            .fillMaxSize()
            .background(HomeStyle.background)
            .clipToBounds(),
    ) {
        Column(
            Modifier
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .offset(y = (-250).dp),
        ) {
            PosterRow(
                title = "电影Movies",
                items = HomeSampleData.movies,
                serverUrl = "",
                focusedId = null,
                onClick = {},
                onMenu = {},
            )
            PosterRow(
                title = recommendationLabel(HomeSampleData.becauseGrandmaster),
                items = HomeSampleData.becauseGrandmaster.items.orEmpty(),
                serverUrl = "",
                focusedId = "sample-hero-film",
                onClick = {},
                onMenu = {},
            )
            PosterRow(
                title = recommendationLabel(HomeSampleData.becauseOdyssey),
                items = HomeSampleData.becauseOdyssey.items.orEmpty(),
                serverUrl = "",
                focusedId = null,
                onClick = {},
                onMenu = {},
            )
        }
    }
}
