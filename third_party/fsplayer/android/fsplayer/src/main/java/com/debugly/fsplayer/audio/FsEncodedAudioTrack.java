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

    public static final int ENCODING_AC3 = 5;
    public static final int ENCODING_E_AC3 = 6;
    public static final int ENCODING_E_AC3_JOC = 18;

    private FsEncodedAudioTrack() {
    }

    /**
     * @return an initialized track, or null if this output cannot carry the encoding
     */
    public static AudioTrack open(int encoding, int sampleRate, int channelMask) {
        if (!isEncoded(encoding) || sampleRate <= 0 || channelMask == 0) {
            Log.w(TAG, "refuse encoded open encoding=" + encoding + " rate=" + sampleRate);
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
            return null;
        }

        if (Build.VERSION.SDK_INT >= 29) {
            boolean direct = AudioTrack.isDirectPlaybackSupported(format, attributes);
            Log.i(TAG, "isDirectPlaybackSupported encoding=" + encoding + " -> " + direct);
            if (!direct) {
                Log.w(TAG, "passthrough refused; caller should decode PCM");
                return null;
            }
        }

        int min = AudioTrack.getMinBufferSize(sampleRate, channelMask, encoding);
        if (min <= 0) {
            Log.w(TAG, "getMinBufferSize failed encoding=" + encoding + " -> " + min);
            return null;
        }
        int buffer = Math.max(min, 65536);
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
                return null;
            }
            Log.i(TAG, "opened encoded AudioTrack encoding=" + encoding
                    + " rate=" + sampleRate + " buffer=" + buffer);
            return track;
        } catch (Throwable error) {
            Log.w(TAG, "AudioTrack.Builder failed encoding=" + encoding, error);
            return null;
        }
    }

    private static boolean isEncoded(int encoding) {
        return encoding == ENCODING_AC3 || encoding == ENCODING_E_AC3 || encoding == ENCODING_E_AC3_JOC;
    }
}
