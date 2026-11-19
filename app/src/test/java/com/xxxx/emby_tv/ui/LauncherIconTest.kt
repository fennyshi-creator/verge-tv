package com.xxxx.emby_tv.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

class LauncherIconTest {
    private fun projectFile(relative: String): File {
        val direct = File(relative)
        if (direct.isFile) return direct
        val fromModule = File("../$relative")
        if (fromModule.isFile) return fromModule
        return direct
    }

    @Test
    fun launcherUsesWhiteAdaptiveIconAndLegacyPngs() {
        val adaptive = projectFile("app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml").readText()
        assertTrue(adaptive.contains("<adaptive-icon"))
        assertTrue(adaptive.contains("@color/ic_launcher_background"))
        assertTrue(adaptive.contains("@drawable/ic_launcher_foreground"))
        assertFalse(adaptive.contains(".png"))
        assertFalse(adaptive.contains("<bitmap"))

        val foregroundFile = projectFile("app/src/main/res/drawable-anydpi-v26/ic_launcher_foreground.xml")
        val foreground = foregroundFile.readText()
        assertTrue(foreground.contains("<vector"))
        assertTrue(foreground.contains("<gradient"))
        assertTrue(foreground.contains("android:pathData="))
        assertTrue(foreground.contains("A8.60,8.60"))
        assertFalse(foreground.contains("<bitmap"))
        assertFalse(foreground.contains(".png"))
        assertFalse(foreground.contains("#FF060817"))
        assertFalse(foreground.contains("#060817"))
        assertFalse(projectFile("app/src/main/res/drawable/ic_launcher_foreground.xml").exists())

        val background = projectFile("app/src/main/res/values/ic_launcher_colors.xml").readText()
        assertTrue(background.contains("ic_launcher_background\">#FFFFFF"))

        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:icon=\"@mipmap/ic_launcher\""))
        assertTrue(manifest.contains("android:roundIcon=\"@mipmap/ic_launcher\""))
        assertTrue(manifest.contains("android:banner=\"@drawable/banner\""))

        val expected = mapOf(
            "mipmap-mdpi" to 48,
            "mipmap-hdpi" to 72,
            "mipmap-xhdpi" to 96,
            "mipmap-xxhdpi" to 144,
            "mipmap-xxxhdpi" to 192,
        )
        for ((dir, size) in expected) {
            val file = projectFile("app/src/main/res/$dir/ic_launcher.png")
            val image = ImageIO.read(file)
            assertEquals(size, image.width)
            assertEquals(size, image.height)
            for (point in listOf(0 to 0, size - 1 to 0, 0 to size - 1, size - 1 to size - 1)) {
                val pixel = image.getRGB(point.first, point.second)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                assertTrue("navy frame at $dir $point ($r,$g,$b)", r > 245 && g > 245 && b > 245)
            }
            var minX = size
            var maxX = 0
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val pixel = image.getRGB(x, y)
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    if (maxOf(r, g, b) - minOf(r, g, b) > 24) {
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                    }
                }
            }
            val span = maxX - minX + 1
            assertTrue("glyph too small in $dir: $span / $size", span > size * 0.60)
        }
    }

    @Test
    fun bannerAndHomeMarkStayPut() {
        val banners = mapOf(
            "drawable/banner.png" to (320 to 180),
            "drawable-xhdpi/banner.png" to (640 to 360),
            "drawable-xxhdpi/banner.png" to (960 to 540),
            "drawable-xxxhdpi/banner.png" to (1280 to 720),
        )
        for ((path, size) in banners) {
            val image = ImageIO.read(projectFile("app/src/main/res/$path"))
            assertEquals(size.first, image.width)
            assertEquals(size.second, image.height)
        }
        val mark = ImageIO.read(projectFile("app/src/main/res/drawable-nodpi/emby_mark.png"))
        assertEquals(375, mark.width)
        assertEquals(377, mark.height)
        val corner = mark.getRGB(0, 0)
        assertEquals(0, corner shr 24 and 0xFF)
    }
}
