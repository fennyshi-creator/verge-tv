package com.xxxx.emby_tv.ui.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import coil3.size.Size
import coil3.transform.Transformation

/**
 * Heavy blur that does not depend on [android.graphics.RenderEffect].
 * TV boxes below API 31 ignore Compose's Modifier.blur, which left backdrops sharp.
 */
class HeavyBlurTransformation : Transformation() {
    override val cacheKey: String = "heavy-blur-v1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap = heavyBlurBitmap(input)
}

fun heavyBlurBitmap(input: Bitmap): Bitmap = blurScaled(input, divisor = 9, radius = 6, passes = 3)

/**
 * Sample backdrops have no photo. Draw hard edges (a figure and a door) and then
 * run the same blur so a preview still shows a heavy smear instead of a flat wash.
 */
fun blurredSampleBackdrop(art: PosterArt): Bitmap {
    val width = 640
    val height = 360
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = art.top.toArgb()
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    paint.color = art.bottom.toArgb()
    canvas.drawRect(0f, height * 0.45f, width.toFloat(), height.toFloat(), paint)
    paint.color = art.glow.toArgb()
    canvas.drawCircle(width * art.glowX, height * art.glowY, width * 0.28f, paint)
    paint.color = Color(0xFFF4E4D4).toArgb()
    val figureX = width * 0.34f
    canvas.drawCircle(figureX, height * 0.42f, 28f, paint)
    canvas.drawRoundRect(RectF(figureX - 26f, height * 0.5f, figureX + 26f, height * 0.92f), 12f, 12f, paint)
    paint.color = Color(0xFF1A120E).toArgb()
    val door = RectF(width * 0.62f, height * 0.18f, width * 0.82f, height * 0.92f)
    canvas.drawRect(door, paint)
    paint.color = Color(0xFFE7D2B8).toArgb()
    canvas.drawRect(door.left + 8f, door.top + 8f, door.right - 8f, door.bottom, paint)
    paint.color = Color(0xFF1A120E).toArgb()
    canvas.drawRect(door.centerX() - 3f, door.top + 8f, door.centerX() + 3f, door.bottom, paint)
    canvas.drawRect(door.left + 8f, door.centerY(), door.right - 8f, door.centerY() + 6f, paint)
    // Milder than a full-bleed photo so the figure and door stay visible as soft shapes.
    return blurScaled(bitmap, divisor = 4, radius = 4, passes = 2)
}

private fun blurScaled(input: Bitmap, divisor: Int, radius: Int, passes: Int): Bitmap {
    val w = (input.width / divisor).coerceIn(20, 220)
    val h = (input.height / divisor).coerceIn(16, 140)
    val small = Bitmap.createScaledBitmap(input, w, h, true).copy(Bitmap.Config.ARGB_8888, true)
    var current = small
    repeat(passes) {
        val next = boxBlur(current, radius)
        if (next != current && current != small) current.recycle()
        current = next
    }
    val outW = input.width.coerceAtMost(1280).coerceAtLeast(1)
    val outH = ((outW.toFloat() / input.width.coerceAtLeast(1)) * input.height).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(current, outW, outH, true)
}

private fun boxBlur(source: Bitmap, radius: Int): Bitmap {
    val w = source.width
    val h = source.height
    val src = IntArray(w * h)
    source.getPixels(src, 0, w, 0, 0, w, h)
    val pixels = boxBlurPass(src, w, h, radius)
    val out = if (source.isMutable) source else source.copy(Bitmap.Config.ARGB_8888, true)
    out.setPixels(pixels, 0, w, 0, 0, w, h)
    return out
}

private fun boxBlurPass(src: IntArray, w: Int, h: Int, radius: Int): IntArray {
    val tmp = IntArray(src.size)
    val dst = IntArray(src.size)
    val div = radius * 2 + 1
    for (y in 0 until h) {
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        val row = y * w
        for (i in -radius..radius) {
            val c = src[row + i.coerceIn(0, w - 1)]
            a += c ushr 24 and 0xFF
            r += c ushr 16 and 0xFF
            g += c ushr 8 and 0xFF
            b += c and 0xFF
        }
        for (x in 0 until w) {
            tmp[row + x] = (a / div shl 24) or (r / div shl 16) or (g / div shl 8) or (b / div)
            val remove = src[row + (x - radius).coerceIn(0, w - 1)]
            val add = src[row + (x + radius + 1).coerceIn(0, w - 1)]
            a += (add ushr 24 and 0xFF) - (remove ushr 24 and 0xFF)
            r += (add ushr 16 and 0xFF) - (remove ushr 16 and 0xFF)
            g += (add ushr 8 and 0xFF) - (remove ushr 8 and 0xFF)
            b += (add and 0xFF) - (remove and 0xFF)
        }
    }
    for (x in 0 until w) {
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        for (i in -radius..radius) {
            val c = tmp[i.coerceIn(0, h - 1) * w + x]
            a += c ushr 24 and 0xFF
            r += c ushr 16 and 0xFF
            g += c ushr 8 and 0xFF
            b += c and 0xFF
        }
        for (y in 0 until h) {
            dst[y * w + x] = (a / div shl 24) or (r / div shl 16) or (g / div shl 8) or (b / div)
            val remove = tmp[(y - radius).coerceIn(0, h - 1) * w + x]
            val add = tmp[(y + radius + 1).coerceIn(0, h - 1) * w + x]
            a += (add ushr 24 and 0xFF) - (remove ushr 24 and 0xFF)
            r += (add ushr 16 and 0xFF) - (remove ushr 16 and 0xFF)
            g += (add ushr 8 and 0xFF) - (remove ushr 8 and 0xFF)
            b += (add and 0xFF) - (remove and 0xFF)
        }
    }
    return dst
}
