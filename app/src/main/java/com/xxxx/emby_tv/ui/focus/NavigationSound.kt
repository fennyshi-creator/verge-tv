package com.xxxx.emby_tv.ui.focus

import android.view.SoundEffectConstants
import android.view.View
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.yield

/**
 * The platform key-click. [View.playSoundEffect] follows the system
 * sound-effects setting and stays silent when that setting is off.
 */
fun playNavigationSound(view: View) {
    view.playSoundEffect(SoundEffectConstants.CLICK)
}

/**
 * One click when this control gains focus after the screen has settled.
 * The first frames cover focus restoration, so coming back does not click.
 */
fun Modifier.navigationSound(): Modifier = composed {
    val view = LocalView.current
    var skipRestore by remember { mutableStateOf(true) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        yield()
        withFrameNanos { }
        withFrameNanos { }
        skipRestore = false
    }
    onFocusChanged { state ->
        val now = state.isFocused
        if (now && !focused && !skipRestore) playNavigationSound(view)
        focused = now
    }
}
