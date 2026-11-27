package com.xxxx.emby_tv.ui

import com.xxxx.emby_tv.ui.components.BackGuard
import com.xxxx.emby_tv.ui.home.DisplayedImage
import com.xxxx.emby_tv.ui.home.TvDensity
import com.xxxx.emby_tv.ui.player.FsPlayback
import com.xxxx.emby_tv.ui.player.SubtitleRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min

class Release105Test {
    private fun projectFile(relative: String): File {
        val direct = File(relative)
        if (direct.isFile) return direct
        val fromModule = File("../$relative")
        if (fromModule.isFile) return fromModule
        return direct
    }

    @Test
    fun nativeReleaseDoesNotJoinOnTheMainThread() {
        assertFalse(FsPlayback.joinsNativeRelease(onMainThread = true))
        assertTrue(FsPlayback.joinsNativeRelease(onMainThread = false))
        assertTrue(FsPlayback.BACKGROUND_RELEASE_WAIT_MS >= 8_000L)
        assertEquals(1, FsPlayback.STALL_REOPEN_LIMIT)
        val source = projectFile("app/src/main/java/com/xxxx/emby_tv/ui/player/FsPlayback.kt").readText()
        assertTrue(source.contains("verge-fs-cmd"))
        assertTrue(source.contains("dispatchPlaying(player.isPlaying)"))
        assertFalse(source.contains("dispatchPlaying(true)"))
        assertTrue(source.contains("joinsNativeRelease"))
    }

    @Test
    fun backLeavesThePlayerWithoutFinishingHome() {
        BackGuard.clear()
        assertFalse(BackGuard.armed(1_000L))
        BackGuard.arm(nowMs = 1_000L, windowMs = BackGuard.RESIDUAL_MS)
        assertTrue(BackGuard.armed(1_200L))
        assertFalse(BackGuard.armed(1_000L + BackGuard.RESIDUAL_MS))
        val handler = projectFile("app/src/main/java/com/xxxx/emby_tv/ui/components/DoubleBackExitHandler.kt").readText()
        assertTrue(handler.contains("BackGuard.armed"))
        val player = projectFile("app/src/main/java/com/xxxx/emby_tv/ui/PlayerScreen.kt").readText()
        assertFalse(player.contains("BackHandler { dispatchPlayerBack"))
        assertTrue(player.contains("withContext(Dispatchers.IO) { MkvCompressedSubtitleDetector.detect"))
        val exit = projectFile("app/src/main/java/com/xxxx/emby_tv/MainActivity.kt").readText()
        assertTrue(exit.contains("BackGuard.arm"))
        assertTrue(exit.contains("navController.popBackStack()"))
        assertFalse(exit.contains("finish()"))
    }

    @Test
    fun textCuesAndKernelSubtitlesAreExclusive() {
        assertTrue(SubtitleRoute.useOverlay(hasTextCues = true))
        assertFalse(
            SubtitleRoute.allowKernel(
                hasTextCues = true,
                externalRequested = true,
                downloadSettled = true,
            ),
        )
        assertFalse(
            SubtitleRoute.allowKernel(
                hasTextCues = false,
                externalRequested = true,
                downloadSettled = false,
            ),
        )
        assertTrue(
            SubtitleRoute.allowKernel(
                hasTextCues = false,
                externalRequested = true,
                downloadSettled = true,
            ),
        )
        assertTrue(
            SubtitleRoute.allowKernel(
                hasTextCues = false,
                externalRequested = false,
                downloadSettled = true,
            ),
        )
        val source = projectFile("app/src/main/java/com/xxxx/emby_tv/ui/player/FsPlayback.kt").readText()
        val kernel = source.substringAfter("if (!kernel)").substringBefore("val requested")
        assertFalse(kernel.contains("loadThenActiveSubtitle"))
        assertTrue(source.contains("quietKernelSubtitles"))
    }

    @Test
    fun widePanelsNormalizeToTheTvBaseline() {
        assertNull(TvDensity.overrideDensity(widthPx = 1920, density = 2f))
        val fourK = TvDensity.overrideDensity(widthPx = 3840, density = 3f)
        assertEquals(4f, fourK!!, 0.001f)
        val reportedDp = 3840f / 3f
        assertTrue(TvDensity.continueTiles(reportedDp) > 6f)
        assertEquals(4.5f, TvDensity.continueTiles(TvDensity.BASELINE_DP), 0.05f)
        val activity = projectFile("app/src/main/java/com/xxxx/emby_tv/MainActivity.kt").readText()
        assertTrue(activity.contains("LocalDensity provides Density"))
        assertEquals(
            "https://emby/Items/1/Images/Thumb?maxWidth=960",
            DisplayedImage.raiseMaxWidth("https://emby/Items/1/Images/Thumb?maxWidth=480", 960, 4f),
        )
        assertEquals(
            "https://emby/Items/1/Images/Thumb?maxWidth=480",
            DisplayedImage.raiseMaxWidth("https://emby/Items/1/Images/Thumb?maxWidth=480", 960, 2f),
        )
    }

    @Test
    fun launcherMarkIsInsetOnTheWhiteTile() {
        assertFalse(projectFile("app/src/main/res/drawable-anydpi-v26/ic_launcher_foreground.xml").exists())
        val image = ImageIO.read(projectFile("app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"))
        var minX = image.width
        var maxX = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val pixel = image.getRGB(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                if (max(r, max(g, b)) - min(r, min(g, b)) > 40) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                }
            }
        }
        assertTrue("left inset $minX of ${image.width}", minX > image.width * 0.16f)
        assertTrue("right inset ${image.width - 1 - maxX}", image.width - 1 - maxX > image.width * 0.07f)
        assertEquals(0xFFFFFF, image.getRGB(0, 0) and 0xFFFFFF)
    }
}
