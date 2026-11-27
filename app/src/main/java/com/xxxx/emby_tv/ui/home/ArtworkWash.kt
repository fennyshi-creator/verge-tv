package com.xxxx.emby_tv.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
     * Bottom-up mask. 0 is the top of the still. The hero title and the
     * area above 继续观看 stay clear. Darkening is already underway at the
     * poster row, and the bottom edge is the opaque page wash.
     */
    const val CLEAR_UNTIL = 0.58f

    /**
     * From here to the bottom of the still, the scrim is the page color.
     * The still layer outlives the hero and its hard edge would otherwise
     * show a tinted strip under 继续观看.
     */
    const val LAND_AT = 0.90f

    fun firstScreenDarken(fraction: Float): Float {
        val t = fraction.coerceIn(0f, 1f)
        if (t <= CLEAR_UNTIL) return 0f
        val u = (t - CLEAR_UNTIL) / (1f - CLEAR_UNTIL)
        return 1f - (1f - u) * (1f - u)
    }

    /**
     * Same dark page wash as the rows below. A light hero swatch is not
     * painted over the still; it falls back to the page background.
     */
    fun scrimColor(swatch: Color?): Color {
        val page = HomeStyle.background
        if (swatch == null) return page
        val lum = swatch.red * 0.2126f + swatch.green * 0.7152f + swatch.blue * 0.0722f
        if (lum > 0.34f) return page
        return Color(
            red = page.red * 0.78f + swatch.red * 0.22f,
            green = page.green * 0.78f + swatch.green * 0.22f,
            blue = page.blue * 0.78f + swatch.blue * 0.22f,
            alpha = 1f,
        )
    }

    fun horizontalHeroCutoff(): Boolean = false

    /**
     * Left-edge black on the home hero. Strong enough to read a bright still,
     * then gone by about 70% of the width so the right side stays full brightness.
     * It is not a hard cutoff, and it does not replace the bottom scrim.
     */
    const val HERO_SHADE_LEFT = 0.68f

    val heroSideStops: List<Pair<Float, Float>> = listOf(
        0f to HERO_SHADE_LEFT,
        0.42f to 0.22f,
        0.70f to 0f,
        1f to 0f,
    )

    fun heroSideAlpha(fraction: Float): Float {
        val t = fraction.coerceIn(0f, 1f)
        val stops = heroSideStops
        if (t <= stops.first().first) return stops.first().second
        for (i in 1 until stops.size) {
            val (start, startAlpha) = stops[i - 1]
            val (end, endAlpha) = stops[i]
            if (t <= end) {
                val span = end - start
                if (span <= 0f) return endAlpha
                val u = (t - start) / span
                return startAlpha + (endAlpha - startAlpha) * u
            }
        }
        return stops.last().second
    }

    fun heroSideBrush(): Brush =
        Brush.horizontalGradient(
            *heroSideStops.map { (at, alpha) -> at to Color.Black.copy(alpha = alpha) }.toTypedArray(),
        )

    fun firstScreenStops(tint: Color): List<Pair<Float, Color>> {
        val page = HomeStyle.background
        return listOf(
            0f to tint.copy(alpha = firstScreenDarken(0f)),
            CLEAR_UNTIL to tint.copy(alpha = firstScreenDarken(CLEAR_UNTIL)),
            0.72f to tint.copy(alpha = firstScreenDarken(0.72f)),
            0.84f to tint.copy(alpha = firstScreenDarken(0.84f)),
            LAND_AT to page,
            1f to page,
        )
    }

    fun firstScreenScrim(color: Color): Brush {
        return Brush.verticalGradient(*firstScreenStops(color).toTypedArray())
    }

    /** Scroll of one viewport carries the still fully off the second screen. */
    fun stillCleared(scrollPx: Int, viewportPx: Int): Boolean =
        viewportPx > 0 && scrollPx >= viewportPx
}

/** First-screen title block. Smaller than the previous 28/15/3-line hero. */
object HomeHeroCopy {
    const val TITLE_SP = 24
    const val OVERVIEW_SP = 13
    const val OVERVIEW_LINES = 2
    const val COLUMN_FRACTION = 0.42f
    const val LOGO_DP = 56
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

    suspend fun loadMuted(context: android.content.Context, url: String): Color? =
        withContext(Dispatchers.IO) {
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
                val bitmap = if (needsSoftwareCopy(raw.config?.name)) {
                    raw.copy(android.graphics.Bitmap.Config.ARGB_8888, false) ?: return@withContext null
                } else {
                    raw
                }
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                sampleMuted(pixels)?.let { Color(it) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
}

/** Covered pages stay composed through the fade. They should not keep sampling art. */
object PageWork {
    fun runs(resumed: Boolean): Boolean = resumed
}

@Composable
fun rememberPageResumed(): Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { source, _ ->
            resumed = source.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return resumed
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
    val resumed = rememberPageResumed()
    var swatch by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(url, resumed) {
        if (!PageWork.runs(resumed)) return@LaunchedEffect
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

/** Slight left shade over the hero still. The right side stays clear. */
@Composable
fun HeroSideShade(modifier: Modifier = Modifier) {
    Box(modifier.background(HomeMask.heroSideBrush()))
}

/**
 * Bottom-up wash for the first-screen still. The color is this visit's hero
 * swatch. The page background is used until that sample is ready.
 */
@Composable
fun HeroBottomScrim(url: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resumed = rememberPageResumed()
    var swatch by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(url, resumed) {
        if (!PageWork.runs(resumed)) return@LaunchedEffect
        swatch = if (url.isNullOrBlank()) null else ArtworkWash.loadMuted(context, url)
    }
    Box(modifier.background(HomeMask.firstScreenScrim(HomeMask.scrimColor(swatch))))
}
