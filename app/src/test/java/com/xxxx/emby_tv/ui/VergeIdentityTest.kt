package com.xxxx.emby_tv.ui

import com.xxxx.emby_tv.data.local.PlaybackMemory
import com.xxxx.emby_tv.data.remote.EmbyApi
import com.xxxx.emby_tv.ui.home.HomeMask
import com.xxxx.emby_tv.ui.player.HdrMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VergeIdentityTest {
    private fun projectFile(relative: String): File {
        val direct = File(relative)
        if (direct.isFile) return direct
        val fromModule = File("../$relative")
        if (fromModule.isFile) return fromModule
        return direct
    }

    @Test
    fun installIdentityIsVergeOne() {
        val gradle = projectFile("app/build.gradle.kts").readText()
        assertTrue(gradle.contains("applicationId = \"com.verge.tv\""))
        assertTrue(gradle.contains("namespace = \"com.xxxx.emby_tv\""))
        assertTrue(gradle.contains("versionName = \"1.0.5\""))
        assertTrue(gradle.contains("versionCode = 6"))
        assertFalse(gradle.contains("applicationId = \"com.xxxx.emby_tv\""))
        val en = projectFile("app/src/main/res/values/strings.xml").readText()
        val zh = projectFile("app/src/main/res/values-zh/strings.xml").readText()
        assertTrue(en.contains("<string name=\"app_name\">Verge</string>"))
        assertTrue(zh.contains("<string name=\"app_name\">Verge</string>"))
        assertFalse(en.contains("OpenEmby"))
        assertFalse(zh.contains("OpenEmby"))
        assertEquals("verge_tv", EmbyApi.CLIENT)
        val api = projectFile("app/src/main/java/com/xxxx/emby_tv/data/remote/EmbyApi.kt").readText()
        assertTrue(api.contains("https://api.github.com/repos/fennyshi-creator/verge-tv/releases/latest"))
        assertFalse(api.contains("shareven/openemby_tv"))
        assertTrue(en.contains("https://github.com/fennyshi-creator/verge-tv"))
        assertTrue(zh.contains("https://github.com/fennyshi-creator/verge-tv"))
        assertFalse(en.contains("shareven/openemby_tv"))
        assertFalse(zh.contains("shareven/openemby_tv"))
        assertFalse(PlaybackMemory.Session().hdrToSdr)
        assertEquals(
            HdrMap.DIRECT,
            HdrMap.mode(manualOn = false, displayHdr = false, sourceHdr = true, api = 31, hardwareRequestAvailable = true),
        )
        assertEquals(0.58f, HomeMask.CLEAR_UNTIL)
        assertEquals(1f, HomeMask.firstScreenDarken(1f))
    }
}
