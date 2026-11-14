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
    fun bodyStaysUnderOneMegabyte() {
        val huge = "x".repeat(2_000_000)
        val body = ClientLogText.jsonBody(huge, "logcat " + "y".repeat(200_000))
        assertTrue(body.toByteArray(Charsets.UTF_8).size <= ClientLogText.MAX_BODY_BYTES)
        assertTrue(body.startsWith("{\"log\":"))
        assertTrue(body.contains("[truncated]"))
    }

    @Test
    fun quotesAndNewlinesAreEscaped() {
        val body = ClientLogText.jsonBody("say \"hi\"\nnext", null)
        assertTrue(body.contains("\\\"hi\\\""))
        assertTrue(body.contains("\\n"))
        assertFalse(body.contains("\n"))
    }
}
