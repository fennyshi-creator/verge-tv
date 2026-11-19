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

/**
 * One press opens once. The key-down opens immediately; a clickable that
 * also sees that same press must not open a second time. A later pointer
 * click, with no key, still opens.
 */
enum class OpenAction { Ignore, Consume, Open }

class OpenLatch {
    private var keyOpened = false

    fun onKey(type: KeyEventType, key: Key, repeat: Boolean = false): OpenAction {
        if (type != KeyEventType.KeyDown) return OpenAction.Ignore
        if (key != Key.DirectionCenter && key != Key.Enter && key != Key.NumPadEnter) {
            return OpenAction.Ignore
        }
        if (repeat) return OpenAction.Consume
        keyOpened = true
        return OpenAction.Open
    }

    fun onClick(): Boolean {
        if (keyOpened) {
            keyOpened = false
            return false
        }
        return true
    }
}
