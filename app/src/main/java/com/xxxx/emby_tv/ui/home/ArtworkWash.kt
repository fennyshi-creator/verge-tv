package com.xxxx.emby_tv.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.xxxx.emby_tv.ui.motion.Motion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min

/**
 * Picks a muted swatch for the page wash. Null means "keep the dark background".
 */
/**
 * Draw order for the page tint. The wash has to sit after the near-black fill
 * and after any full-screen scrim, and before the cards. 2.0.39 painted the
 * details wash under those scrims, so the bloom never reached the glass.
 */
object PageLayers {
    val home = listOf("fill", "wash", "content")
    val library = listOf("fill", "wash", "content")
    val details = listOf("fill", "backdrop", "scrim", "wash", "content")

    fun washAboveFill(layers: List<String>): Boolean {
        val wash = layers.indexOf("wash")
        val content = layers.indexOf("content")
        if (wash < 0 || content <= wash) return false
        val cover = listOf("fill", "scrim").map { layers.indexOf(it) }.filter { it >= 0 }
        return cover.all { it < wash }
    }
}

/**
 * The still belongs to the hero. Its bottom seam fades the picture out so the
 * one page wash shows through. Continue watching, the libraries, and the
 * poster rows do not paint their own fill.
 */
object HomeMask {
    val seam = 240.dp
    private const val TITLE_SHIFT_DP = 36f

    fun shiftDp(target: String): Float = if (target == "hero-title") TITLE_SHIFT_DP else 0f

    /**
     * 1 keeps the still. The drop is slow at first and reaches 0 at the hero
     * edge, so the landing color is whatever sits under the page, not a line.
     */
    fun stillAlpha(alongSeam: Float): Float {
        val t = alongSeam.coerceIn(0f, 1f)
        return 1f - t * t
    }

    fun seamStops(): List<Pair<Float, Float>> =
        listOf(0f, 0.22f, 0.5f, 0.78f, 1f).map { it to stillAlpha(it) }

    /** DstIn mask. Black keeps the picture; transparent reveals the wash. */
    fun fadeBrush(heightPx: Float, seamPx: Float): Brush {
        val keep = if (heightPx <= 0f) 1f else stillKeepUntil(heightPx.toInt(), seamPx.toInt())
        val stops = ArrayList<Pair<Float, Color>>(seamStops().size + 1)
        stops += 0f to Color.Black
        stops += keep to Color.Black
        for ((t, alpha) in seamStops()) {
            if (t <= 0f) continue
            stops += (keep + (1f - keep) * t) to Color.Black.copy(alpha = alpha)
        }
        return Brush.verticalGradient(*stops.toTypedArray())
    }

    /**
     * Scroll that puts [index] on the stage and, once the hero is left behind,
     * pushes the still fully above the viewport.
     */
    fun homeScroll(rowTop: Int, index: Int, stagePx: Int, heroBottom: Int, maxScroll: Int): Int {
        if (index <= 0) return 0
        val limit = maxScroll.coerceAtLeast(0)
        val docked = (rowTop - stagePx).coerceIn(0, limit)
        val clearStill = heroBottom.coerceIn(0, limit)
        return maxOf(docked, clearStill)
    }

    /**
     * Fraction of the hero that stays fully opaque before the seam begins.
     * The seam itself runs from this point to the hero bottom.
     */
    fun stillKeepUntil(heroPx: Int, seamPx: Int): Float {
        if (heroPx <= 0) return 1f
        val seam = seamPx.coerceIn(0, heroPx)
        return (heroPx - seam).toFloat() / heroPx
    }

    /** Continue, libraries, and poster rows share the wash. None paints a slab. */
    fun paintsOwnFill(row: String): Boolean = false

    fun sharesWash(row: String): Boolean = row == "continue" || row == "library" || row == "posters"

    data class Bands(
        val stillTop: Int,
        val stillBottom: Int,
        val seamTop: Int,
        val seamBottom: Int,
        val opaqueTop: Int,
    )

    fun bands(heroHeight: Int, seamPx: Int): Bands {
        val seam = seamPx.coerceIn(0, heroHeight.coerceAtLeast(0))
        return Bands(
            stillTop = 0,
            stillBottom = heroHeight,
            seamTop = heroHeight - seam,
            seamBottom = heroHeight,
            opaqueTop = heroHeight,
        )
    }

    fun stillBehindContinue(bands: Bands, continueTitleTop: Int): Boolean =
        bands.stillBottom > continueTitleTop

    fun opaqueFromContinue(bands: Bands, continueTitleTop: Int): Boolean =
        bands.opaqueTop <= continueTitleTop && bands.seamBottom <= continueTitleTop

    /** No history: the seam ends on the library title. Nothing is left between them. */
    fun emptyHistoryMeetsLibrary(bands: Bands, libraryTop: Int): Boolean =
        bands.opaqueTop == libraryTop && bands.seamBottom == libraryTop

    /** The first-screen still covers the viewport. It is not a short slab. */
    fun coversFirstScreen(): Boolean = true

    fun hardBlackSlab(): Boolean = false

    /**
     * Page-color alpha laid over the full-bleed still. The top stays a photo.
     * The ease reaches a dark field by the continue-watching posters.
     */
    fun firstScreenDarken(fraction: Float): Float {
        val t = fraction.coerceIn(0f, 1f)
        val start = 0.36f
        if (t <= start) return 0f
        val u = (t - start) / (1f - start)
        return u * u * (3f - 2f * u)
    }

    fun firstScreenScrim(color: Color): Brush {
        val stops = floatArrayOf(0f, 0.36f, 0.52f, 0.68f, 0.82f, 1f).map { t ->
            t to color.copy(alpha = firstScreenDarken(t))
        }
        return Brush.verticalGradient(*stops.toTypedArray())
    }

    /** Scroll of one viewport carries the still fully off the second screen. */
    fun stillCleared(scrollPx: Int, viewportPx: Int): Boolean =
        viewportPx > 0 && scrollPx >= viewportPx
}

object ArtworkWash {
    /**
     * 1 once a poster row is on screen and has art. 0 on the hero, where the
     * backdrop is the picture. The dark page fill stays underneath this wash.
     */
    fun strength(onHero: Boolean, hasArtwork: Boolean): Float =
        blend(if (onHero) 1f else 0f, hasArtwork)

    /** 1 keeps the hero picture. 0 is the focused-art wash. Values between crossfade. */
    fun blend(heroVisible: Float, hasArtwork: Boolean): Float {
        if (!hasArtwork) return 0f
        return (1f - heroVisible.coerceIn(0f, 1f))
    }

    fun argb(darkMuted: Int?, muted: Int?, darkVibrant: Int?): Int? {
        return darkMuted ?: muted ?: darkVibrant
    }

    /**
     * Average of mid-tone, slightly saturated pixels. Skips near-black and
     * near-white so a poster does not flood the page.
     */
    /** Coil on a TV often hands back a HARDWARE bitmap. getPixels throws on those. */
    fun needsSoftwareCopy(configName: String?): Boolean =
        configName.equals("HARDWARE", ignoreCase = true)

    /** Column changes must retarget the wash. Row-focus alone keeps the first poster. */
    fun artUrlForFocus(focused: Boolean, url: String?): String? =
        if (focused && !url.isNullOrBlank()) url else null

    fun sampleMuted(pixels: IntArray): Int? {
        if (pixels.isEmpty()) return null
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        for (color in pixels) {
            val rr = (color shr 16) and 0xFF
            val gg = (color shr 8) and 0xFF
            val bb = color and 0xFF
            val hi = max(rr, max(gg, bb))
            val lo = min(rr, min(gg, bb))
            val light = (hi + lo) / 2
            if (light < 12 || light > 220) continue
            if (hi == 0 || (hi - lo) * 255 / hi < 12) continue
            r += rr
            g += gg
            b += bb
            n++
        }
        if (n == 0) return null
        return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
    }
}

/**
 * A low, directional tint. [strength] is 0 on the hero and 1 once that
 * backdrop has slid away. A missing bitmap leaves the color transparent.
 */
@Composable
fun AmbientWash(
    url: String?,
    strength: Float,
    modifier: Modifier = Modifier,
    maxAlpha: Float = 0.42f,
) {
    val context = LocalContext.current
    var swatch by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            swatch = null
            return@LaunchedEffect
        }
        val next = withContext(Dispatchers.IO) {
            try {
                val result = SingletonImageLoader.get(context).execute(
                    ImageRequest.Builder(context)
                        .data(url)
                        .memoryCacheKey("wash:$url")
                        .size(64, 96)
                        .build(),
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
        if (coroutineContext.isActive) swatch = next
    }
    val target = swatch?.copy(alpha = maxAlpha) ?: Color.Transparent
    val color by animateColorAsState(target, Motion.fade(), label = "ambient-wash")
    val shown = color.copy(alpha = color.alpha * strength.coerceIn(0f, 1f))
    if (shown.alpha <= 0.01f) return
    Canvas(modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    shown.copy(alpha = shown.alpha * 0.55f),
                    shown,
                    shown.copy(alpha = shown.alpha * 0.28f),
                    Color.Transparent,
                ),
                start = Offset(size.width * 0.08f, 0f),
                end = Offset(size.width * 0.9f, size.height),
            ),
        )
    }
}
