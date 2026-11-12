package com.xxxx.emby_tv.ui.home

import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import java.io.File

private const val TV = "spec:width=960dp,height=540dp,dpi=320"

private fun previewFont(): FontFamily {
    val file = listOf(
        File("app/src/screenshotTest/res/font/home_cjk.otf"),
        File("src/screenshotTest/res/font/home_cjk.otf"),
        File("/workspace/app/src/screenshotTest/res/font/home_cjk.otf"),
    ).firstOrNull { it.exists() } ?: error("Missing app/src/screenshotTest/res/font/home_cjk.otf")
    return FontFamily(Typeface.createFromFile(file))
}

@PreviewTest
@Preview(name = "home-top", device = TV, locale = "zh")
@Composable
fun HomeTopScreenshot() {
    HomeTopPreview(fontFamily = previewFont())
}

@PreviewTest
@Preview(name = "home-movies", device = TV, locale = "zh")
@Composable
fun HomeMoviesScreenshot() {
    HomeMoviesPreview(fontFamily = previewFont())
}

@PreviewTest
@Preview(name = "home-recommended", device = TV, locale = "zh")
@Composable
fun HomeRecommendedScreenshot() {
    HomeRecommendedPreview(fontFamily = previewFont())
}
