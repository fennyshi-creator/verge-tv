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
    fun launcherUsesSharpPrecompositedRaster() {
        assertFalse(
            "TV drawers composite mipmap-anydpi-v26 adaptive icons into a blur",
            projectFile("app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml").exists(),
        )
        assertFalse(projectFile("app/src/main/res/drawable/ic_launcher_foreground.xml").exists())

        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:icon=\"@mipmap/ic_launcher_tile\""))
        assertTrue(manifest.contains("android:roundIcon=\"@mipmap/ic_launcher_tile\""))
        assertFalse(manifest.contains("android:icon=\"@mipmap/ic_launcher\""))
        assertTrue(manifest.contains("android:banner=\"@drawable/banner\""))

        val tile = ImageIO.read(projectFile("app/src/main/res/mipmap-nodpi/ic_launcher_tile.png"))
        assertTrue("nodpi tile ${tile.width}", tile.width >= 512 && tile.width > 192)
        assertEquals(tile.width, tile.height)
        assertSharpWhiteMark(tile, "nodpi")

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
            assertTrue("glyph not the draft A mark in $dir: $span / $size", span > size * 0.64 && span < size * 0.78)
            assertTrue("glyph flush in $dir: left $minX", minX > size * 0.15)
            assertTrue("glyph clipped in $dir: right ${size - 1 - maxX}", size - 1 - maxX > size * 0.07)
        }
    }

    private fun assertSharpWhiteMark(image: java.awt.image.BufferedImage, label: String) {
        val size = image.width
        for (point in listOf(0 to 0, size - 1 to 0, 0 to size - 1, size - 1 to size - 1)) {
            val pixel = image.getRGB(point.first, point.second)
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            assertTrue("white frame at $label $point ($r,$g,$b)", r > 245 && g > 245 && b > 245)
        }
        var minX = size
        var maxX = 0
        var edgeY = size / 2
        for (y in 0 until size) {
            for (x in 0 until size) {
                val pixel = image.getRGB(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                if (maxOf(r, g, b) - minOf(r, g, b) > 24) {
                    if (x < minX) {
                        minX = x
                        edgeY = y
                    }
                    if (x > maxX) maxX = x
                }
            }
        }
        val span = maxX - minX + 1
        assertTrue("glyph span $label $span / $size", span > size * 0.64 && span < size * 0.78)
        var ramp = 0
        var seen = false
        for (x in 0 until size) {
            val pixel = image.getRGB(x, edgeY)
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val chroma = maxOf(r, g, b) - minOf(r, g, b)
            if (!seen && chroma > 12) seen = true
            if (seen) {
                ramp++
                if (chroma > 100) break
            }
        }
        assertTrue("soft edge at $label: $ramp px", ramp in 1..8)
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
