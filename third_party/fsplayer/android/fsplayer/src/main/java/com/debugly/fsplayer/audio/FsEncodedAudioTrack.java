package com.debugly.fsplayer.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Build;
import android.util.Log;

/**
 * Opens an AudioTrack that writes compressed AC-3 / E-AC-3 / E-AC-3 JOC
 * packets. The stock FSPlayer wrapper only knows the PCM constructor.
 *
 * ENCODING_AC3 = 5, ENCODING_E_AC3 = 6, ENCODING_E_AC3_JOC = 18.
 * A failed open returns null so native code can fall back to PCM decode.
 */
public final class FsEncodedAudioTrack {
    private static final String TAG = "FsPassthrough";

    public static final int ENCODING_PCM_16BIT = 2;
    public static final int ENCODING_AC3 = 5;
    public static final int ENCODING_E_AC3 = 6;
    public static final int ENCODING_IEC61937 = 13;
    public static final int ENCODING_E_AC3_JOC = 18;

    private FsEncodedAudioTrack() {
    }

    /**
     * @return an initialized track, or null if this output cannot carry the encoding
     */
    public static AudioTrack open(int encoding, int sampleRate, int channelMask) {
        if (!isOpenable(encoding) || sampleRate <= 0 || channelMask == 0) {
            Log.w(TAG, "refuse encoded open encoding=" + encoding + " rate=" + sampleRate);
            FsPassthroughLog.note("audio open refuse encoding=" + encoding
                    + " rate=" + sampleRate + " mask=0x" + Integer.toHexString(channelMask));
            return null;
        }
        if (encoding == ENCODING_IEC61937 && Build.VERSION.SDK_INT < 24) {
            FsPassthroughLog.note("audio open encoding=13 needs api 24");
            return null;
        }
        AudioFormat format;
        AudioAttributes attributes;
        try {
            format = new AudioFormat.Builder()
                    .setEncoding(encoding)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .build();
            attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build();
        } catch (Throwable error) {
            Log.w(TAG, "AudioFormat rejected encoding=" + encoding, error);
            FsPassthroughLog.note("audio open encoding=" + encoding
                    + " rate=" + sampleRate + " mask=0x" + Integer.toHexString(channelMask)
                    + " format-rejected");
            return null;
        }

        if (Build.VERSION.SDK_INT >= 29) {
            boolean direct = AudioTrack.isDirectPlaybackSupported(format, attributes);
            Log.i(TAG, "isDirectPlaybackSupported encoding=" + encoding + " -> " + direct);
            /*
             * Do not refuse the open here. On HDMI devices this query often
             * reports false for E-AC-3 / JOC even when the AVR accepts the
             * bitstream. A failed Builder returns null and the caller falls
             * through to the next encoding, then to PCM.
             */
        }

        int min = AudioTrack.getMinBufferSize(sampleRate, channelMask, encoding);
        if (min <= 0) {
            Log.w(TAG, "getMinBufferSize failed encoding=" + encoding + " -> " + min);
            FsPassthroughLog.note("audio open encoding=" + encoding
                    + " rate=" + sampleRate + " mask=0x" + Integer.toHexString(channelMask)
                    + " minBuffer=" + min + " fail");
            return null;
        }
        int buffer = Math.max(min, encoding == ENCODING_IEC61937 || sampleRate >= 128000 ? 49152 : 65536);
        try {
            AudioTrack track = new AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(buffer)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
            if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                Log.w(TAG, "AudioTrack not initialized encoding=" + encoding);
                track.release();
                FsPassthroughLog.note("audio open encoding=" + encoding
                        + " rate=" + sampleRate + " mask=0x" + Integer.toHexString(channelMask)
                        + " buffer=" + buffer + " not-initialized");
                return null;
            }
            Log.i(TAG, "opened encoded AudioTrack encoding=" + encoding
                    + " rate=" + sampleRate + " mask=0x" + Integer.toHexString(channelMask)
                    + " buffer=" + buffer);
            FsPassthroughLog.note("audio open encoding=" + encoding
                    + " rate=" + sampleRate + " mask=0x" + Integer.toHexString(channelMask)
                    + " buffer=" + buffer + " ok");
            return track;
        } catch (Throwable error) {
            Log.w(TAG, "AudioTrack.Builder failed encoding=" + encoding, error);
            FsPassthroughLog.note("audio open encoding=" + encoding
                    + " rate=" + sampleRate + " mask=0x" + Integer.toHexString(channelMask)
                    + " builder-failed");
            return null;
        }
    }

    private static boolean isOpenable(int encoding) {
        return encoding == ENCODING_PCM_16BIT || encoding == ENCODING_AC3
                || encoding == ENCODING_E_AC3 || encoding == ENCODING_E_AC3_JOC
                || encoding == ENCODING_IEC61937;
    }
}
