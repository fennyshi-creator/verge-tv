package com.xxxx.emby_tv.ui

import com.xxxx.emby_tv.ui.home.HomeCapsule
import com.xxxx.emby_tv.ui.library.LibraryActivation
import com.xxxx.emby_tv.ui.library.OpenAction
import com.xxxx.emby_tv.ui.library.OpenLatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min

class Release103Test {
    private fun projectFile(relative: String): File {
        val direct = File(relative)
        if (direct.isFile) return direct
        val fromModule = File("../$relative")
        if (fromModule.isFile) return fromModule
        return direct
    }

    @Test
    fun launcherTipStaysAmberInsteadOfWhite() {
        val vector = projectFile("app/src/main/res/drawable-anydpi-v26/ic_launcher_foreground.xml").readText()
        assertTrue(vector.contains("#FFF08E25"))
        assertFalse(vector.contains("#FFFFF43C"))
        assertFalse(vector.contains("#FFF9E568"))
        val image = ImageIO.read(projectFile("app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"))
        var minX = image.width
        var maxX = 0
        var minY = image.height
        var maxY = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val pixel = image.getRGB(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                if (max(r, max(g, b)) - min(r, min(g, b)) > 50) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        val tipX = minX + ((maxX - minX) * 0.9f).toInt()
        var samples = 0
        var amber = 0
        var nearWhite = 0
        for (y in minY..maxY) {
            for (x in tipX..maxX) {
                val pixel = image.getRGB(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val chroma = max(r, max(g, b)) - min(r, min(g, b))
                if (chroma <= 50) continue
                samples++
                if (r > 200 && g in 70..190 && b < 120 && chroma > 100) amber++
                if (max(r, max(g, b)) > 242 && chroma < 48) nearWhite++
            }
        }
        assertTrue("tip samples $samples", samples > 20)
        assertTrue("amber $amber of $samples", amber * 2 > samples)
        assertEquals(0, nearWhite)
        assertEquals(0xFFFFFF, image.getRGB(0, 0) and 0xFFFFFF)
    }

    @Test
    fun homeCapsuleDoesNotGrowWhenFocused() {
        assertTrue(HomeCapsule.trackStable())
        assertTrue(HomeCapsule.pillFitsInTrack())
        assertEquals(HomeCapsule.trackDp(), HomeCapsule.itemContentDp() + HomeCapsule.TRACK_PADDING_DP * 2)
        assertTrue(HomeCapsule.PILL_DP < 36)
        val bar = projectFile("app/src/main/java/com/xxxx/emby_tv/ui/home/OfficialHome.kt").readText()
        assertTrue(bar.contains("HomeCapsule.trackDp()"))
        assertTrue(bar.contains("HomeCapsule.PILL_DP"))
        assertFalse(bar.contains(".height(36.dp)"))
    }

    @Test
    fun oneOkOpensFavoritesAndSearchOnce() {
        val latch = OpenLatch()
        assertEquals(OpenAction.Ignore, latch.onKey(androidx.compose.ui.input.key.KeyEventType.KeyUp, androidx.compose.ui.input.key.Key.DirectionCenter))
        assertEquals(OpenAction.Open, latch.onKey(androidx.compose.ui.input.key.KeyEventType.KeyDown, androidx.compose.ui.input.key.Key.DirectionCenter))
        assertFalse(latch.onClick())
        assertEquals(OpenAction.Consume, latch.onKey(androidx.compose.ui.input.key.KeyEventType.KeyDown, androidx.compose.ui.input.key.Key.Enter, repeat = true))
        assertTrue(latch.onClick())
        assertTrue(LibraryActivation.opensOn(androidx.compose.ui.input.key.KeyEventType.KeyDown, androidx.compose.ui.input.key.Key.Enter))
        assertFalse(LibraryActivation.opensOn(androidx.compose.ui.input.key.KeyEventType.KeyUp, androidx.compose.ui.input.key.Key.Enter))
        val favorites = projectFile("app/src/main/java/com/xxxx/emby_tv/ui/library/FavoritesPage.kt").readText()
        val search = projectFile("app/src/main/java/com/xxxx/emby_tv/ui/SearchScreen.kt").readText()
        assertTrue(favorites.contains("OpenLatch"))
        assertTrue(search.contains("OpenLatch"))
        assertTrue(favorites.contains("OpenAction.Open"))
        assertTrue(search.contains("OpenAction.Open"))
    }
}
