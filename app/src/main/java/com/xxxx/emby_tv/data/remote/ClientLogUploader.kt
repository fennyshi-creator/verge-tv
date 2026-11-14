package com.xxxx.emby_tv.data.remote

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.xxxx.emby_tv.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * On the next cold start, POST the previous passthrough log. Playback does
 * not wait on this. After a few failures the file is dropped so it is not
 * sent forever.
 */
object ClientLogUploader {
    private const val PREFS = "emby_tv"
    private const val LIVE = "passthrough.log"
    private const val PENDING = "passthrough-pending.log"
    private const val ATTEMPTS = "passthrough-pending.attempts"
    private const val MAX_ATTEMPTS = 3

    fun rotate(context: Context) {
        val dir = context.filesDir
        val live = File(dir, LIVE)
        if (!live.exists() || live.length() <= 0L) return
        val pending = File(dir, PENDING)
        val text = buildString {
            if (pending.exists() && pending.length() > 0L) {
                append(pending.readText())
                append("\n---\n")
            }
            append(live.readText())
        }
        pending.writeText(text)
        live.delete()
    }

    fun uploadPendingAsync(context: Context) {
        val app = context.applicationContext
        Thread {
            val sent = uploadOnce(app)
            if (!sent && File(app.filesDir, PENDING).exists()) {
                Handler(Looper.getMainLooper()).postDelayed({
                    Thread { uploadOnce(app) }.start()
                }, 20_000L)
            }
        }.start()
    }

    private fun uploadOnce(context: Context): Boolean {
        val pending = File(context.filesDir, PENDING)
        if (!pending.exists() || pending.length() <= 0L) return true
        val attempts = readAttempts(context)
        if (attempts >= MAX_ATTEMPTS) {
            pending.delete()
            writeAttempts(context, 0)
            return true
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val server = prefs.getString("serverUrl", null)?.trim().orEmpty()
        val token = prefs.getString("apiKey", null)?.trim().orEmpty()
        if (server.isEmpty() || token.isEmpty()) return false
        val logcat = if (BuildConfig.DEBUG) readLogcat() else null
        val body = ClientLogText.jsonBody(pending.readText(), logcat)
        val deviceId = prefs.getString("deviceId", null).orEmpty()
        return try {
            val request = Request.Builder()
                .url(ClientLogText.endpoint(server))
                .addHeader("X-Emby-Token", token)
                .addHeader("X-Emby-Client", EmbyApi.CLIENT)
                .addHeader("X-Emby-Client-Version", EmbyApi.CLIENT_VERSION)
                .addHeader("X-Emby-Device-Name", EmbyApi.DEVICE_NAME)
                .addHeader("X-Emby-Device-Id", deviceId)
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            HttpClient.getClient(context).newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    pending.delete()
                    writeAttempts(context, 0)
                    true
                } else {
                    fail(context, attempts)
                    false
                }
            }
        } catch (_: Throwable) {
            fail(context, attempts)
            false
        }
    }

    private fun fail(context: Context, attempts: Int) {
        val next = attempts + 1
        writeAttempts(context, next)
        if (next >= MAX_ATTEMPTS) {
            File(context.filesDir, PENDING).delete()
            writeAttempts(context, 0)
        }
    }

    private fun readAttempts(context: Context): Int {
        val file = File(context.filesDir, ATTEMPTS)
        return file.takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull() ?: 0
    }

    private fun writeAttempts(context: Context, value: Int) {
        File(context.filesDir, ATTEMPTS).writeText(value.toString())
    }

    private fun readLogcat(): String? {
        return try {
            val process = ProcessBuilder("logcat", "-d", "-t", "400", "-v", "time")
                .redirectErrorStream(true)
                .start()
            val text = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor(2, TimeUnit.SECONDS)
            text.lineSequence().filter { line ->
                line.contains("FsPassthrough") ||
                    line.contains("FATAL") ||
                    line.contains("Fatal signal") ||
                    line.contains("tombstone") ||
                    (line.contains("DEBUG") && (line.contains("signal") || line.contains("backtrace")))
            }.take(200).joinToString("\n")
        } catch (_: Throwable) {
            null
        }
    }
}
