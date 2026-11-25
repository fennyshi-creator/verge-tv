package com.xxxx.emby_tv.data.remote

/**
 * Redacts secrets before a line is written to the local audio log.
 * The log stays on the device. Nothing here sends it.
 */
object ClientLogText {
    fun redact(text: String): String {
        var cleaned = text
        cleaned = Regex("(?i)(X-Emby-Token|api[_-]?key|access[_-]?token|authorization|password|passwd|cookie)\\s*[:=]\\s*\\S+")
            .replace(cleaned) { "${it.groupValues[1]}=[redacted]" }
        cleaned = Regex("(?i)Bearer\\s+\\S+").replace(cleaned, "Bearer [redacted]")
        cleaned = Regex("(?i)https?://\\S+").replace(cleaned, "[redacted-url]")
        /* A token is hex and contains a letter. Decimal PTS and counters stay. */
        cleaned = Regex("\\b(?=[0-9a-fA-F]*[a-fA-F])[0-9a-fA-F]{32,}\\b").replace(cleaned, "[redacted]")
        return cleaned
    }
}
