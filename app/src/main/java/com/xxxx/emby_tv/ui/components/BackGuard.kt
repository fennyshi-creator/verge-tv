package com.xxxx.emby_tv.ui.components

/**
 * A Back that leaves the player can still be delivered to Home.
 * While this window is open, [com.xxxx.emby_tv.ui.components.DoubleBackExitHandler]
 * must not treat that residual press as a request to finish the Activity.
 */
object BackGuard {
    const val RESIDUAL_MS = 450L

    @Volatile
    private var ignoreUntilMs = 0L

    fun arm(nowMs: Long, windowMs: Long = RESIDUAL_MS) {
        ignoreUntilMs = nowMs + windowMs
    }

    fun armed(nowMs: Long): Boolean = nowMs < ignoreUntilMs

    fun clear() {
        ignoreUntilMs = 0L
    }
}
