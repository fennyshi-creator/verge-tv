package com.debugly.fsplayer.audio;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Private-file log of the HDMI passthrough path. Each line is flushed and
 * synced before the caller writes to AudioTrack, so a process death inside
 * the HAL still leaves the last step on disk.
 */
public final class FsPassthroughLog {
    private static final int MAX_BYTES = 768 * 1024;
    private static final Object LOCK = new Object();
    private static File file;
    private static int bytes;

    private FsPassthroughLog() {
    }

    public static void attach(Context context, String version) {
        if (context == null) {
            return;
        }
        synchronized (LOCK) {
            file = new File(context.getFilesDir(), "passthrough.log");
            bytes = file.exists() ? (int) Math.min(file.length(), MAX_BYTES) : 0;
        }
        note("version=" + (version == null ? "" : version));
    }

    public static void note(String line) {
        if (line == null) {
            return;
        }
        String text = line.endsWith("\n") ? line : line + "\n";
        synchronized (LOCK) {
            if (file == null || bytes >= MAX_BYTES) {
                return;
            }
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(file, true);
                byte[] data = (text + "\n").getBytes(StandardCharsets.UTF_8);
                out.write(data);
                out.flush();
                out.getFD().sync();
                bytes += data.length;
            } catch (Throwable ignored) {
                // A log failure must not take down playback.
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }
}
