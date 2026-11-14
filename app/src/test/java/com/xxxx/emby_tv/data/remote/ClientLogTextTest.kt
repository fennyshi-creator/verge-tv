package com.xxxx.emby_tv.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientLogTextTest {
    @Test
    fun endpointStaysUnderApi() {
        assertEquals(
            "https://example.test/api/tv-client-logs",
            ClientLogText.endpoint("https://example.test/"),
        )
        assertFalse(ClientLogText.endpoint("https://example.test").contains("/emby/"))
    }

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
    fun bodyStaysUnderOneMegabyte() {
        val huge = "x".repeat(2_000_000)
        val body = ClientLogText.jsonBody(huge, "logcat " + "y".repeat(200_000))
        assertTrue(body.toByteArray(Charsets.UTF_8).size <= ClientLogText.MAX_BODY_BYTES)
        assertTrue(body.startsWith("{\"log\":"))
        assertTrue(body.contains("[truncated]"))
    }

    @Test
    fun tombstoneFramesAfterBacktraceAreKept() {
        val raw = """
            I/FsPassthrough: opened encoded AudioTrack encoding=13
            F/libc: Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0
            F/DEBUG: signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0
            F/DEBUG: backtrace:
            F/DEBUG: #00 pc 000000000001234 libaudioclient.so (android::AudioTrack::start+32)
            F/DEBUG: #01 pc 00000000000abcd libfsplayer.so (aout_thread_n+80)
            I/Other: noise that is not a frame
        """.trimIndent()
        val slice = ClientLogText.sliceCrashLog(raw)
        assertTrue(slice.contains("#00 pc 000000000001234 libaudioclient.so"))
        assertTrue(slice.contains("#01 pc 00000000000abcd libfsplayer.so"))
        assertTrue(slice.contains("Fatal signal 11"))
        assertFalse(slice.contains("noise that is not a frame"))
    }

    @Test
    fun quotesAndNewlinesAreEscaped() {
        val body = ClientLogText.jsonBody("say \"hi\"\nnext", null)
        assertTrue(body.contains("\\\"hi\\\""))
        assertTrue(body.contains("\\n"))
        assertFalse(body.contains("\n"))
    }
}
