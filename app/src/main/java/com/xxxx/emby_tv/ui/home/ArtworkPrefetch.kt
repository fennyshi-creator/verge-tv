package com.xxxx.emby_tv.ui.home

import android.content.Context
import android.os.Build
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.transformations

/**
 * Display sizes shared by the on-screen request and the prefetch request.
 * Coil's memory cache includes the pixel size, so the two have to match
 * or a hold on Right still decodes from scratch.
 */
object ArtworkPrefetch {
    fun pixels(role: ArtRole): Pair<Int, Int> = when (role) {
        ArtRole.Primary -> 400 to 600
        ArtRole.Thumb -> 480 to 270
        ArtRole.Backdrop -> 1280 to 720
    }

    fun enqueue(context: Context, urls: List<String>, role: ArtRole, blur: Boolean = false) {
        if (urls.isEmpty()) return
        val (width, height) = pixels(role)
        val loader = SingletonImageLoader.get(context)
        for (url in urls) {
            if (url.isBlank()) continue
            loader.enqueue(request(context, url, width, height, blur))
        }
    }

    /** Backdrop the details page will show. Hardware blur reuses the sharp bitmap. */
    fun enqueueBackdrops(context: Context, urls: List<String>) {
        val hardware = Build.VERSION.SDK_INT >= 31
        enqueue(context, urls, ArtRole.Backdrop, blur = !hardware)
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
