package com.xxxx.emby_tv.ui.components

/** Playback-bar icon order. Version is present only for a multi-version title. */
object TransportSlots {
    const val SUBTITLES = 0
    const val AUDIO = 1

    fun version(showVersion: Boolean): Int? = if (showVersion) 2 else null

    fun info(showVersion: Boolean): Int = if (showVersion) 3 else 2

    fun settings(showVersion: Boolean): Int = if (showVersion) 4 else 3

    fun count(showVersion: Boolean): Int = settings(showVersion) + 1
}
