package com.xxxx.emby_tv.ui.home

import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.xxxx.emby_tv.ui.detail.DetailCastPreview
import com.xxxx.emby_tv.ui.detail.DetailHeroPreview
import com.xxxx.emby_tv.ui.detail.SeriesCastPreview
import com.xxxx.emby_tv.ui.detail.SeriesEpisodesPreview
import com.xxxx.emby_tv.ui.detail.SeriesHeroPreview
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
@Preview(name = "home-scrolled", device = TV, locale = "zh")
@Composable
fun HomeScrolledScreenshot() {
    HomeScrolledPreview(fontFamily = previewFont())
}

@PreviewTest
@Preview(name = "detail-hero", device = TV, locale = "zh")
@Composable
fun DetailHeroScreenshot() {
    DetailHeroPreview(fontFamily = previewFont())
}

@PreviewTest
@Preview(name = "detail-cast-similar", device = TV, locale = "zh")
@Composable
fun DetailCastScreenshot() {
    DetailCastPreview(fontFamily = previewFont())
}

@PreviewTest
@Preview(name = "series-hero", device = TV, locale = "zh")
@Composable
fun SeriesHeroScreenshot() {
    SeriesHeroPreview(fontFamily = previewFont())
}

@PreviewTest
@Preview(name = "series-seasons-episodes", device = TV, locale = "zh")
@Composable
fun SeriesEpisodesScreenshot() {
    SeriesEpisodesPreview(fontFamily = previewFont())
}

@PreviewTest
@Preview(name = "series-cast-similar", device = TV, locale = "zh")
@Composable
fun SeriesCastScreenshot() {
    SeriesCastPreview(fontFamily = previewFont())
}
