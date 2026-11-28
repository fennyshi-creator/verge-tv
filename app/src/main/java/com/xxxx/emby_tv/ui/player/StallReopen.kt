package com.xxxx.emby_tv.ui.player

/**
 * A frozen playback clock is not enough to tear the player down while
 * the display is still receiving frames.
 */
object StallReopen {
    fun shouldReopen(
        clockAdvanced: Boolean,
        framesAdvanced: Boolean,
        stalledLongEnough: Boolean,
        retriesRemaining: Boolean,
    ): Boolean = stalledLongEnough && retriesRemaining && !clockAdvanced && !framesAdvanced
}
