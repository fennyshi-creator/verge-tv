package com.xxxx.emby_tv.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

enum class ArtRole { Primary, Thumb, Backdrop }

data class PosterArt(
    val top: Color,
    val bottom: Color,
    val glow: Color,
    val glowX: Float = 0.7f,
    val glowY: Float = 0.58f,
    val glowRadius: Float = 0.62f,
    val spark: Color = Color.Transparent,
    val sparkX: Float = 0.22f,
    val sparkY: Float = 0.28f,
    val kicker: String? = null,
    val kickerColor: Color = Color(0xFFF4E7C8),
    val kickerSize: TextUnit = 15.sp,
    val script: Boolean = false,
    val align: Alignment = Alignment.CenterStart,
    val tracking: TextUnit = 1.2.sp,
    val pad: Dp = 14.dp,
    val showHorizon: Boolean = false,
)

data class ArtSet(
    val primary: PosterArt,
    val thumb: PosterArt? = null,
    val backdrop: PosterArt? = null,
)

object SampleArt {
    private val catalog = mapOf(
        "sample-featured" to ArtSet(
            primary = nightInterior,
            backdrop = nightInterior,
        ),
        "sample-odyssey" to ArtSet(
            primary = portrait("奥德赛", Color(0xFF10284A), Color(0xFF3A1408), Color(0xFFE36A22), 0.78f, 0.72f),
            thumb = PosterArt(
                top = Color(0xFF0C1C38),
                bottom = Color(0xFF2A0C06),
                glow = Color(0xFFE85D04),
                glowX = 0.82f,
                glowY = 0.78f,
                glowRadius = 0.7f,
                spark = Color(0xFF7EC8FF),
                sparkX = 0.18f,
                sparkY = 0.32f,
                kicker = "THE ODYSSEY",
                kickerSize = 13.sp,
                tracking = 1.4.sp,
                align = Alignment.TopStart,
                pad = 12.dp,
            ),
        ),
        "sample-malena" to ArtSet(
            primary = portrait("美丽传说", Color(0xFF3A2416), Color(0xFF120C09), Color(0xFFC9844A), 0.3f, 0.4f),
            thumb = PosterArt(
                top = Color(0xFF2A1A12),
                bottom = Color(0xFF0E0B09),
                glow = Color(0xFFD7A15A),
                glowX = 0.28f,
                glowY = 0.55f,
                kicker = "Malèna",
                kickerColor = Color(0xFFF3E0C0),
                kickerSize = 22.sp,
                script = true,
                tracking = 0.4.sp,
                align = Alignment.TopCenter,
                pad = 18.dp,
            ),
        ),
        "sample-lincoln" to ArtSet(
            primary = portrait("林肯律师", Color(0xFFC6A15A), Color(0xFF3E3424), Color(0xFFF0D59A), 0.5f, 0.35f),
            thumb = PosterArt(
                top = Color(0xFFD4B36A),
                bottom = Color(0xFF5C4A28),
                glow = Color(0xFFFFF1C9),
                glowX = 0.62f,
                glowY = 0.4f,
                spark = Color(0xFF2C2418),
                kicker = "THE LINCOLN\nLAWYER",
                kickerColor = Color(0xFF1C160E),
                kickerSize = 13.sp,
                tracking = 0.8.sp,
                align = Alignment.TopStart,
                pad = 12.dp,
            ),
        ),
        "sample-affair" to ArtSet(
            primary = portrait("出轨", Color(0xFF4A3040), Color(0xFF1A1218), Color(0xFFE7B7C4), 0.7f, 0.3f),
            thumb = PosterArt(
                top = Color(0xFF3A2A32),
                bottom = Color(0xFF161014),
                glow = Color(0xFFE8C4A8),
                glowX = 0.34f,
                glowY = 0.42f,
                spark = Color(0xFF8E6A78),
                showHorizon = true,
            ),
        ),
        "sample-dune" to ArtSet(
            primary = portrait("沙丘", Color(0xFFC47A3A), Color(0xFF2A1408), Color(0xFFFFC27A), 0.5f, 0.7f),
            thumb = PosterArt(
                top = Color(0xFF8C4E24),
                bottom = Color(0xFF1A0E08),
                glow = Color(0xFFFFB15A),
                glowX = 0.5f,
                glowY = 0.8f,
                kicker = "DUNE",
                tracking = 3.sp,
                align = Alignment.TopStart,
                pad = 12.dp,
            ),
        ),
        "sample-love" to ArtSet(primary = portrait("可能的爱", Color(0xFF6E2438), Color(0xFF1A0C10), Color(0xFFFF8AA8), 0.4f, 0.35f)),
        "sample-bio" to ArtSet(primary = portrait("爆发夜", Color(0xFF143028), Color(0xFF06110E), Color(0xFF3DDC97), 0.72f, 0.68f)),
        "sample-gun" to ArtSet(primary = portrait("空枪", Color(0xFF2A2E34), Color(0xFF0C0E12), Color(0xFF9AA4B2), 0.3f, 0.25f)),
        "sample-hero-film" to ArtSet(primary = portrait("英雄", Color(0xFF6E1C1C), Color(0xFF1A0808), Color(0xFFE8C56B), 0.5f, 0.42f)),
        "sample-assassin" to ArtSet(primary = portrait("聂隐娘", Color(0xFF1C2430), Color(0xFF07080C), Color(0xFFD7D2C8), 0.62f, 0.3f)),
        "sample-mood" to ArtSet(primary = portrait("花样年华", Color(0xFF7A2E2A), Color(0xFF24100E), Color(0xFFE7C27A), 0.28f, 0.55f)),
        "sample-kekexili" to ArtSet(primary = portrait("可可西里", Color(0xFF8A7048), Color(0xFF2A2218), Color(0xFFE6D2A8), 0.55f, 0.3f)),
        "sample-rurouni" to ArtSet(primary = portrait("浪客", Color(0xFF1E3A5C), Color(0xFF0A1018), Color(0xFF7EB6E6), 0.75f, 0.4f)),
        "sample-mummy2" to ArtSet(primary = portrait("归来", Color(0xFF8A6A32), Color(0xFF241808), Color(0xFFF0D090), 0.4f, 0.62f)),
        "sample-mummy3" to ArtSet(primary = portrait("龙帝", Color(0xFF3A2412), Color(0xFF100C08), Color(0xFFC45A28), 0.66f, 0.48f)),
        "sample-green" to ArtSet(primary = portrait("绿区", Color(0xFF1A3324), Color(0xFF0A120C), Color(0xFF8FBF6A), 0.35f, 0.4f)),
        "sample-rurouni-end" to ArtSet(primary = portrait("完结篇", Color(0xFF243044), Color(0xFF0C1018), Color(0xFFD0B07A), 0.7f, 0.62f)),
        "sample-season" to ArtSet(primary = portrait("漫长", Color(0xFF3A3428), Color(0xFF14110C), Color(0xFFD8C4A0), 0.4f, 0.5f)),
        "sample-wind" to ArtSet(primary = portrait("有风", Color(0xFF1E4A44), Color(0xFF0C1816), Color(0xFF9ED9C8), 0.6f, 0.35f)),
        "sample-blossom" to ArtSet(primary = portrait("繁花", Color(0xFF5A2030), Color(0xFF16080C), Color(0xFFE8A0B4), 0.48f, 0.4f)),
        "sample-joy" to ArtSet(primary = portrait("庆余年", Color(0xFF2A2418), Color(0xFF100E0A), Color(0xFFC8A45A), 0.32f, 0.55f)),
        "sample-set-a" to ArtSet(primary = portrait("经典", Color(0xFF243018), Color(0xFF101408), Color(0xFFB7C87A), 0.4f, 0.6f)),
        "sample-set-b" to ArtSet(primary = portrait("特辑", Color(0xFF14283A), Color(0xFF080E16), Color(0xFF7EB0D8), 0.7f, 0.4f)),
        "sample-set-c" to ArtSet(primary = portrait("影单", Color(0xFF3A2418), Color(0xFF140E0A), Color(0xFFE0A878), 0.3f, 0.45f)),
        "sample-set-d" to ArtSet(primary = portrait("武侠", Color(0xFF2A1818), Color(0xFF100808), Color(0xFFC84A3A), 0.6f, 0.55f)),
    )

    fun lookup(id: String?, role: ArtRole): PosterArt? {
        val set = catalog[id] ?: return null
        return when (role) {
            ArtRole.Primary -> set.primary
            ArtRole.Thumb -> set.thumb ?: set.primary
            ArtRole.Backdrop -> set.backdrop ?: set.thumb ?: set.primary
        }
    }
}

private val nightInterior = PosterArt(
    top = Color(0xFF3A2C24),
    bottom = Color(0xFF0A0908),
    glow = Color(0xFFE0B88A),
    glowX = 0.22f,
    glowY = 0.62f,
    glowRadius = 0.55f,
    spark = Color(0xFF6A5344),
    sparkX = 0.68f,
    sparkY = 0.36f,
)

private fun portrait(
    caption: String,
    top: Color,
    bottom: Color,
    glow: Color,
    glowX: Float,
    glowY: Float,
) = PosterArt(
    top = top,
    bottom = bottom,
    glow = glow,
    glowX = glowX,
    glowY = glowY,
    kicker = caption,
    kickerSize = 13.sp,
    tracking = 0.6.sp,
    align = Alignment.BottomStart,
    pad = 10.dp,
    kickerColor = Color.White.copy(alpha = 0.92f),
)

@Composable
fun SampleArtwork(art: PosterArt, modifier: Modifier = Modifier) {
    val font = LocalHomeFont.current
    Box(modifier.background(art.bottom)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(art.top, art.bottom),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height),
                )
            )
            val center = Offset(size.width * art.glowX, size.height * art.glowY)
            val radius = size.maxDimension * art.glowRadius
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(art.glow.copy(alpha = 0.9f), art.glow.copy(alpha = 0f)),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
            if (art.spark.alpha > 0f) {
                drawCircle(
                    color = art.spark.copy(alpha = 0.28f),
                    radius = size.minDimension * 0.12f,
                    center = Offset(size.width * art.sparkX, size.height * art.sparkY),
                )
            }
            if (art.showHorizon) {
                val y = size.height * 0.62f
                drawLine(
                    color = Color.White.copy(alpha = 0.16f),
                    start = Offset(size.width * 0.08f, y),
                    end = Offset(size.width * 0.92f, y),
                    strokeWidth = 1.5f,
                )
            }
            drawRect(
                brush = Brush.verticalGradient(
                    0.55f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.45f),
                )
            )
        }
        if (!art.kicker.isNullOrBlank()) {
            Text(
                text = art.kicker,
                color = art.kickerColor,
                fontSize = art.kickerSize,
                fontWeight = if (art.script) FontWeight.Normal else FontWeight.Medium,
                fontStyle = if (art.script) FontStyle.Italic else FontStyle.Normal,
                fontFamily = if (art.script) FontFamily.Serif else font,
                letterSpacing = art.tracking,
                lineHeight = art.kickerSize * 1.15f,
                modifier = Modifier
                    .align(art.align)
                    .padding(art.pad),
            )
        }
    }
}
