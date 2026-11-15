package com.xxxx.emby_tv.ui.library

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType

/**
 * One OK opens the focused poster. Key-up and repeats do not open again.
 * The library grid and the collection list both use this.
 */
object LibraryActivation {
    fun opensOn(type: KeyEventType, key: Key): Boolean {
        if (type != KeyEventType.KeyDown) return false
        return key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter
    }
}
