package com.xxxx.emby_tv.ui.player

import android.os.Build

/**
 * Dune and Realtek RTD1619 boxes skip the Vulkan video output.
 * The same strings are implemented in native `verge_vendor_wants_gles`.
 * Other devices stay on Vulkan and hardware decode.
 */
object VendorVideoRoute {
    fun wantsGles(hardware: String?, boardPlatform: String?, manufacturer: String?): Boolean {
        if (chip(hardware) || chip(boardPlatform)) return true
        return manufacturer?.contains("dune", ignoreCase = true) == true
    }

    fun current(): Boolean {
        val hardware = first(systemProperty("ro.hardware"), Build.HARDWARE)
        val board = first(systemProperty("ro.board.platform"), Build.BOARD)
        val manufacturer = first(Build.MANUFACTURER, systemProperty("ro.product.manufacturer"))
        return wantsGles(hardware, board, manufacturer)
    }

    private fun chip(value: String?): Boolean {
        val text = value?.lowercase() ?: return false
        return text.contains("rtd1619") || text.contains("realtek")
    }

    private fun first(primary: String?, secondary: String?): String? {
        if (!primary.isNullOrBlank()) return primary
        return secondary
    }

    private fun systemProperty(name: String): String? {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            method.invoke(null, name) as? String
        } catch (_: Throwable) {
            null
        }
    }
}
