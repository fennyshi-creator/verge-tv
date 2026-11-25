package com.xxxx.emby_tv.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VendorVideoRouteTest {
    @Test
    fun rtdAndDuneUseGles() {
        assertTrue(VendorVideoRoute.wantsGles("rtd1619dr", "exynos", "Google"))
        assertTrue(VendorVideoRoute.wantsGles("amlogic", "rtd1619", "Droid"))
        assertTrue(VendorVideoRoute.wantsGles("amlogic", "g12a", "Dune HD"))
    }

    @Test
    fun realtekTv794aUsesGles() {
        assertTrue(VendorVideoRoute.wantsGles("thor", "mjolnir", "Realtek", "tv794a"))
        assertTrue(VendorVideoRoute.wantsGles("thor", "meson", null, null))
        assertTrue(VendorVideoRoute.wantsGles(null, "mjolnir", null, null))
        assertTrue(VendorVideoRoute.wantsGles(null, null, "Realtek", null))
        assertTrue(VendorVideoRoute.wantsGles(null, null, null, "tv794a"))
    }

    @Test
    fun otherDevicesStayOnVulkan() {
        assertFalse(VendorVideoRoute.wantsGles("qcom", "msm8998", "Sony", "Pixel"))
        assertFalse(VendorVideoRoute.wantsGles(null, null, null, null))
        assertFalse(VendorVideoRoute.wantsGles("dune", "box", "Generic"))
        assertFalse(VendorVideoRoute.wantsGles("authority", "box", "Generic"))
    }
}
