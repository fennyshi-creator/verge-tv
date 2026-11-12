package com.xxxx.emby_tv.ui.home

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
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
    HomePreviewChrome(fontFamily) {
        OfficialHome(
            resumeItems = HomeSampleData.resume,
            libraries = HomeSampleData.libraries,
            serverUrl = "",
            onResume = {},
            onOpenLibrary = {},
            onSearch = {},
            onFavorites = {},
            onSettings = {},
            requestInitialFocus = false,
            forceContinueFocus = true,
            fontFamily = fontFamily,
        )
    }
}

@Preview(name = "home-scrolled", device = TV, locale = "zh", showBackground = true)
@Composable
fun HomeScrolledPreview(fontFamily: FontFamily = FontFamily.Default) {
    HomePreviewChrome(fontFamily) {
        OfficialHome(
            resumeItems = HomeSampleData.resume,
            libraries = HomeSampleData.libraries,
            serverUrl = "",
            onResume = {},
            onOpenLibrary = {},
            onSearch = {},
            onFavorites = {},
            onSettings = {},
            requestInitialFocus = false,
            continueWindowStart = 3,
            forceContinueFocus = true,
            fontFamily = fontFamily,
        )
    }
}
