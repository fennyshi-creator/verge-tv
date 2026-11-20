package com.xxxx.emby_tv.ui

import com.xxxx.emby_tv.data.local.PlaybackMemory
import com.xxxx.emby_tv.data.model.MediaSourceInfoDto
import com.xxxx.emby_tv.data.model.MediaStreamDto
import com.xxxx.emby_tv.ui.components.TransportSlots
import com.xxxx.emby_tv.ui.player.StreamInfo
import com.xxxx.emby_tv.ui.player.VersionCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsPlaybackTest {
    private fun projectFile(relative: String): File {
        val direct = File(relative)
        if (direct.isFile) return direct
        val fromModule = File("../$relative")
        if (fromModule.isFile) return fromModule
        return direct
    }

    @Test
    fun settingsDropThemeAndKeepOnePlaybackStore() {
        assertFalse(SettingsCatalog.categories.any { it.contains("主题") || it.contains("界面") })
        assertTrue(SettingsCatalog.allows("检查更新"))
        assertTrue(SettingsCatalog.allows("播放"))
        assertTrue(SettingsCatalog.categories.none { it.contains("弹幕") })
        val screen = projectFile("app/src/main/java/com/xxxx/emby_tv/ui/SettingsScreen.kt").readText()
        assertFalse(screen.contains("ThemeColorManager"))
        assertTrue(screen.contains("prefs.hdrToSdr"))
        assertTrue(screen.contains("prefs.decoderPreference"))
        assertTrue(screen.contains("prefs.decodeToStereo"))
        assertTrue(screen.contains("onCheckUpdate"))
        val prefs = projectFile("app/src/main/java/com/xxxx/emby_tv/data/local/PreferencesManager.kt").readText()
        assertTrue(prefs.contains("getBoolean(KEY_HDR_TO_SDR, false)"))
        assertFalse(PlaybackMemory.Session().hdrToSdr)
        assertEquals(PlaybackMemory.DECODER_AUTO, PlaybackMemory.Session().decoderPreference)
    }

    @Test
    fun versionButtonUsesTheLongerCatalog() {
        val playing = MediaSourceInfoDto(id = "a", name = "1080p")
        val other = MediaSourceInfoDto(id = "b", name = "4K")
        val chosen = VersionCatalog.choose(
            playback = listOf(playing),
            item = listOf(playing, other),
            cached = emptyList(),
        )
        assertEquals(listOf("a", "b"), chosen.map { it.id })
        val kept = VersionCatalog.choose(
            playback = listOf(playing),
            item = listOf(playing),
            cached = listOf(playing, other),
        )
        assertEquals(2, kept.size)
        assertEquals(5, TransportSlots.count(showVersion = true))
        assertEquals(4, TransportSlots.count(showVersion = false))
        assertEquals(2, TransportSlots.version(showVersion = true))
        assertNull(TransportSlots.version(showVersion = false))
        assertEquals(3, TransportSlots.info(showVersion = true))
        assertEquals(2, TransportSlots.info(showVersion = false))
    }

    @Test
    fun streamInfoOmitsUnknownFields() {
        val video = MediaStreamDto(
            type = "Video",
            width = 3840,
            height = 2160,
            videoRange = "HDR10",
            colorSpace = "bt2020nc",
        )
        val audio = MediaStreamDto(
            type = "Audio",
            index = 1,
            codec = "eac3",
            channels = 6,
            isDefault = true,
        )
        val source = MediaSourceInfoDto(id = "a", mediaStreams = listOf(video, audio))
        val lines = StreamInfo.lines(source, audioIndex = 1)
        assertEquals("3840×2160", lines.first { it.label == "分辨率" }.value)
        assertTrue(lines.first { it.label == "色彩" }.value.contains("HDR10"))
        assertTrue(lines.first { it.label == "色彩" }.value.contains("bt2020nc"))
        assertEquals("DD+ 5.1", lines.first { it.label == "音频" }.value)
        val quiet = MediaSourceInfoDto(id = "b", name = "Feature")
        assertTrue(StreamInfo.lines(quiet, audioIndex = -1).isEmpty())
        val sdr = MediaStreamDto(type = "Video", videoRange = "SDR", width = 1920, height = 1080)
        assertEquals("SDR", StreamInfo.color(sdr))
        assertNull(StreamInfo.color(MediaStreamDto(type = "Video", width = 1280, height = 720)))
    }
}
