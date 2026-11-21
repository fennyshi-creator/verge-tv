package com.xxxx.emby_tv.data.remote

/**
 * Shapes the passthrough log for POST /api/tv-client-logs.
 * The path is under /api/, not /emby/. Tokens never stay in the body.
 */
object ClientLogText {
    const val MAX_BODY_BYTES = 1024 * 1024

    fun endpoint(serverUrl: String): String {
        return serverUrl.trim().trimEnd('/') + "/api/tv-client-logs"
    }

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

    /**
     * Keep FATAL / tombstone context and the frames that follow "backtrace:".
     * A filter that stops on the header drops every "#00 pc" line.
     */
    fun sliceCrashLog(raw: String): String {
        val kept = ArrayList<String>()
        var framesLeft = 0
        for (line in raw.lineSequence()) {
            if (framesLeft > 0) {
                val frameLine = line.contains(" pc ") || line.trimStart().startsWith("#")
                if (frameLine) {
                    kept.add(line)
                    framesLeft--
                    continue
                }
                framesLeft = 0
            }
            val backtrace = line.contains("backtrace:")
            val interesting = line.contains("FsPassthrough") ||
                line.contains("FATAL") ||
                line.contains("Fatal signal") ||
                line.contains("tombstone") ||
                line.contains("crash_dump") ||
                (line.contains("DEBUG") &&
                    (line.contains("signal") || line.contains("backtrace") || line.contains(" pc "))) ||
                (line.contains("#") && line.contains(" pc "))
            if (backtrace) {
                kept.add(line)
                framesLeft = 48
                continue
            }
            if (interesting) kept.add(line)
        }
        return kept.take(300).joinToString("\n")
    }

    fun jsonBody(log: String, logcat: String?): String {
        var fileLog = redact(log)
        var cat = logcat?.let { redact(it) } ?: ""
        var body = encode(fileLog, cat)
        if (body.toByteArray(Charsets.UTF_8).size <= MAX_BODY_BYTES) return body
        cat = ""
        body = encode(fileLog, cat)
        while (body.toByteArray(Charsets.UTF_8).size > MAX_BODY_BYTES && fileLog.isNotEmpty()) {
            val drop = (fileLog.length / 8).coerceAtLeast(1)
            fileLog = fileLog.dropLast(drop)
            body = encode(fileLog + "\n[truncated]\n", "")
        }
        return body
    }

    private fun encode(log: String, logcat: String): String {
        return "{\"log\":${jsonString(log)},\"logcat\":${jsonString(logcat)}}"
    }

    private fun jsonString(value: String): String {
        val out = StringBuilder(value.length + 2)
        out.append('"')
        for (ch in value) {
            when (ch) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (ch.code < 0x20) {
                    out.append("\\u")
                    out.append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    out.append(ch)
                }
            }
        }
        out.append('"')
        return out.toString()
    }
}
