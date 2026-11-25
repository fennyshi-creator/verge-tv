package com.xxxx.emby_tv.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ClientLogTextTest {
    @Test
    fun secretsAreRedacted() {
        val raw = "open X-Emby-Token: abcdef0123456789abcdef0123456789 Bearer secret-token api_key=abc password=hunter2"
        val cleaned = ClientLogText.redact(raw)
        assertFalse(cleaned.contains("abcdef0123456789abcdef0123456789"))
        assertFalse(cleaned.contains("secret-token"))
        assertFalse(cleaned.contains("hunter2"))
        assertTrue(cleaned.contains("[redacted]"))
    }

    @Test
    fun urlsAreRedacted() {
        val cleaned = ClientLogText.redact("play https://media.example/video.mkv?api_key=abc")
        assertFalse(cleaned.contains("media.example"))
        assertFalse(cleaned.contains("video.mkv"))
        assertTrue(cleaned.contains("[redacted-url]"))
    }

    @Test
    fun diagnosticNumbersAreNotRedacted() {
        val raw = "diag session=1 t=40931 raw_media_bytes=27525120 last_pts=1234567890 " +
            "last_serial=2 audio_qbytes=3080 " +
            "1234567890123456789012345678901234567890"
        val cleaned = ClientLogText.redact(raw)
        assertTrue(cleaned.contains("last_pts=1234567890"))
        assertTrue(cleaned.contains("raw_media_bytes=27525120"))
        assertTrue(cleaned.contains("1234567890123456789012345678901234567890"))
        assertFalse(cleaned.contains("[redacted]"))
    }

    @Test
    fun startupDoesNotUploadLogs() {
        val roots = listOf(File("app/src/main"), File("src/main"), File("../app/src/main"))
        val root = roots.firstOrNull { it.isDirectory } ?: error("missing app sources")
        val kotlin = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(kotlin.any { it.name == "App.kt" })
        assertFalse(kotlin.any { it.name == "ClientLogUploader.kt" })
        val joined = kotlin.joinToString("\n") { it.readText() }
        assertFalse(joined.contains("tv-client-logs"))
        assertFalse(joined.contains("uploadPending"))
        assertFalse(joined.contains("ClientLogUploader"))
        val app = kotlin.first { it.name == "App.kt" }.readText()
        assertTrue(app.contains("FsPassthroughLog.attach"))
        assertFalse(app.contains("upload"))
    }
}
