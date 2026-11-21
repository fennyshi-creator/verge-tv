package com.xxxx.emby_tv.ui.player

import android.os.Build

/**
 * Devices that must not create a Vulkan video output.
 * The same strings are implemented in native `verge_vendor_wants_gles`.
 * Realtek tv794a reports manufacturer Realtek, hardware thor, and board mjolnir.
 * Other devices stay on Vulkan and hardware decode.
 */
object VendorVideoRoute {
    fun wantsGles(
        hardware: String?,
        boardPlatform: String?,
        manufacturer: String?,
        model: String? = null,
    ): Boolean {
        if (vendorName(manufacturer)) return true
        return boardToken(hardware) || boardToken(boardPlatform) || boardToken(model)
    }

    fun current(): Boolean {
        if (wantsGles(Build.HARDWARE, Build.BOARD, Build.MANUFACTURER, Build.MODEL)) return true
        if (wantsGles(Build.BRAND, Build.DEVICE, systemProperty("ro.product.manufacturer"), systemProperty("ro.product.model"))) {
            return true
        }
        return wantsGles(
            systemProperty("ro.hardware"),
            first(systemProperty("ro.board.platform"), systemProperty("ro.product.board")),
            systemProperty("ro.product.brand"),
            systemProperty("ro.product.device"),
        )
    }

    private fun vendorName(value: String?): Boolean {
        val text = value?.lowercase() ?: return false
        return text.contains("dune") || text.contains("realtek")
    }

    private fun boardToken(value: String?): Boolean {
        val text = value?.lowercase() ?: return false
        return text.contains("rtd1619") ||
            text.contains("realtek") ||
            text.contains("tv794a") ||
            text.contains("mjolnir") ||
            text == "thor"
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
