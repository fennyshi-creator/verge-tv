package com.xxxx.emby_tv.ui.motion

/**
 * Selected-versus-focus chips. White only while focus is on that chip.
 * A selected season or tab that does not have focus stays gray.
 */
object FocusPill {
    enum class Face { Idle, Selected, Focused }

    fun face(rowFocused: Boolean, current: Boolean): Face = when {
        rowFocused && current -> Face.Focused
        !rowFocused && current -> Face.Selected
        else -> Face.Idle
    }

    /** Top menus have no selected-white state. White only on the focused item. */
    fun menuFace(menuFocused: Boolean, slot: Int, index: Int): Face {
        return if (menuFocused && slot == index) Face.Focused else Face.Idle
    }
}
