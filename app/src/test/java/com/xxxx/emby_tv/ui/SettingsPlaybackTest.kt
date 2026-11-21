package com.xxxx.emby_tv.ui

import com.xxxx.emby_tv.data.local.PlaybackMemory
import com.xxxx.emby_tv.data.model.MediaSourceInfoDto
import com.xxxx.emby_tv.data.model.MediaStreamDto
import com.xxxx.emby_tv.data.remote.EmbyApi
import com.xxxx.emby_tv.ui.components.TransportSlots
import com.xxxx.emby_tv.ui.player.StreamInfo
import com.xxxx.emby_tv.ui.player.VersionCatalog
import com.xxxx.emby_tv.ui.update.ReleaseVersions
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
        assertEquals(TransportSlots.SUBTITLES, TransportSlots.neighbor(showVersion = true, index = TransportSlots.AUDIO, delta = -1))
        assertEquals(2, TransportSlots.neighbor(showVersion = true, index = TransportSlots.AUDIO, delta = 1))
        assertNull(TransportSlots.neighbor(showVersion = false, index = TransportSlots.SUBTITLES, delta = -1))
        assertEquals(18, EmbyApi.LATEST_ROW_LIMIT)
        assertFalse(ReleaseVersions.isNewer("v1.0.5", "1.0.7"))
        assertFalse(ReleaseVersions.isNewer("v1.0.7", "v1.0.7"))
        assertTrue(ReleaseVersions.isNewer("v1.0.8", "1.0.7"))
    }

    @Test
    fun streamInfoOmitsUnknownFields() {
        val video = MediaStreamDto(
            type = "Video",
            width = 3840,
            height = 2160,
            codec = "hevc",
            profile = "Main 10",
            realFrameRate = 23.976,
            bitRate = 12_500_000,
            videoRange = "HDR10",
            colorSpace = "bt2020nc",
            bitDepth = 10,
        )
        val audio = MediaStreamDto(
            type = "Audio",
            index = 1,
            codec = "eac3",
            channels = 6,
            sampleRate = 48000,
            language = "chi",
            isDefault = true,
        )
        val source = MediaSourceInfoDto(
            id = "a",
            name = "4K",
            container = "mkv",
            protocol = "File",
            mediaStreams = listOf(video, audio),
        )
        val lines = StreamInfo.lines(source, audioIndex = 1).associate { it.label to it.value }
        assertEquals("3840×2160", lines["分辨率"])
        assertTrue(lines["视频编码"]!!.contains("HEVC"))
        assertTrue(lines["视频编码"]!!.contains("Main 10"))
        assertEquals("23.976 fps", lines["帧率"])
        assertEquals("12.5 Mbps", lines["码率"])
        assertTrue(lines["色彩"]!!.contains("HDR10"))
        assertTrue(lines["色彩"]!!.contains("bt2020nc"))
        assertTrue(lines["色彩"]!!.contains("10 bit"))
        assertEquals("mkv", lines["容器"])
        assertTrue(lines["音频"]!!.contains("DD+ 5.1"))
        assertTrue(lines["音频"]!!.contains("48 kHz"))
        assertEquals("4K", lines["片源"])
        assertEquals("直接播放", lines["方式"])
        val quiet = MediaSourceInfoDto(id = "b", name = "Feature")
        assertEquals(listOf("片源" to "Feature"), StreamInfo.lines(quiet, audioIndex = -1).map { it.label to it.value })
        val sdr = MediaStreamDto(type = "Video", videoRange = "SDR", width = 1920, height = 1080)
        assertTrue(StreamInfo.color(sdr)!!.startsWith("SDR"))
        assertNull(StreamInfo.color(MediaStreamDto(type = "Video", width = 1280, height = 720)))
        assertNull(StreamInfo.bitrate(192))
        assertEquals("转码", StreamInfo.delivery(MediaSourceInfoDto(transcodingUrl = "/videos/t")))
    }
}
