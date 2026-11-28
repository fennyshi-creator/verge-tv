package com.xxxx.emby_tv.ui.player

/**
 * Optional picture hold while a bitstream is actually open.
 * PCM stays at 0. The value is a negative audio extra-delay so the
 * picture waits for speakers that are still decoding, instead of
 * delaying the bitstream further.
 */
object PassthroughSync {
    const val STEP_MS = 10
    const val MAX_MS = 120

    fun normalize(ms: Int): Int {
        val clamped = ms.coerceIn(0, MAX_MS)
        return (clamped / STEP_MS) * STEP_MS
    }

    /** Each press adds 10 ms. 120 wraps back to 0. */
    fun stepUp(ms: Int): Int {
        val current = normalize(ms)
        return if (current >= MAX_MS) 0 else current + STEP_MS
    }

    fun isOpen(encoding: Int, fellBackToPcm: Boolean): Boolean =
        encoding != 0 && !fellBackToPcm

    fun extraDelaySeconds(compensationMs: Int, passthroughOpen: Boolean): Float {
        if (!passthroughOpen) return 0f
        return -normalize(compensationMs) / 1000f
    }
}
