package com.xxxx.emby_tv.ui.home

import android.content.Context
import android.os.Build
import coil3.SingletonImageLoader
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.transformations

/**
 * Display sizes shared by the on-screen request and the prefetch request.
 * Coil's memory cache includes the pixel size, so the two have to match
 * or a hold on Right still decodes from scratch.
 */
object ArtworkPrefetch {
    fun pixels(role: ArtRole, density: Float = DisplayedImage.BASELINE_DENSITY): Pair<Int, Int> {
        val base = when (role) {
            ArtRole.Primary -> 400 to 600
            ArtRole.Thumb -> 480 to 270
            ArtRole.Backdrop -> 1280 to 720
        }
        if (role == ArtRole.Backdrop) return base
        val scale = DisplayedImage.scale(density)
        return (base.first * scale).toInt() to (base.second * scale).toInt()
    }

    fun enqueue(
        context: Context,
        urls: List<String>,
        role: ArtRole,
        blur: Boolean = false,
        density: Float = DisplayedImage.BASELINE_DENSITY,
    ): List<Disposable> {
        if (urls.isEmpty()) return emptyList()
        val (width, height) = pixels(role, density)
        val loader = SingletonImageLoader.get(context)
        val longest = maxOf(width, height)
        return urls.mapNotNull { url ->
            if (url.isBlank()) null
            else {
                val sized = if (role == ArtRole.Backdrop) url else DisplayedImage.raiseMaxWidth(url, longest, density)
                loader.enqueue(request(context, sized, width, height, blur))
            }
        }
    }

    /** Backdrop the details page will show. Hardware blur reuses the sharp bitmap. */
    fun enqueueBackdrops(context: Context, urls: List<String>): List<Disposable> {
        val hardware = Build.VERSION.SDK_INT >= 31
        return enqueue(context, urls, ArtRole.Backdrop, blur = !hardware)
    }

    fun request(
        context: Context,
        url: String,
        width: Int,
        height: Int,
        blur: Boolean,
    ): ImageRequest {
        return ImageRequest.Builder(context)
            .data(url)
            .size(width, height)
            .apply { if (blur) transformations(HeavyBlurTransformation()) }
            .build()
    }
}
