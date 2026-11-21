package com.xxxx.emby_tv.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VendorVideoRouteTest {
    @Test
    fun rtdAndRealtekUseGles() {
        assertTrue(VendorVideoRoute.wantsGles("rtd1619dr", "exynos", "Google"))
        assertTrue(VendorVideoRoute.wantsGles("amlogic", "rtd1619", "Droid"))
        assertTrue(VendorVideoRoute.wantsGles("Realtek", "meson", "TCL"))
    }

    @Test
    fun duneManufacturerUsesGles() {
        assertTrue(VendorVideoRoute.wantsGles("amlogic", "g12a", "Dune HD"))
    }

    @Test
    fun otherDevicesStayOnVulkan() {
        assertFalse(VendorVideoRoute.wantsGles("qcom", "msm8998", "Sony"))
        assertFalse(VendorVideoRoute.wantsGles(null, null, null))
        assertFalse(VendorVideoRoute.wantsGles("dune", "box", "Generic"))
    }
}
