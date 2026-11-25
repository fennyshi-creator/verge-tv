/*****************************************************************************
 * android_audiotrack.c
 *****************************************************************************
 *
 * Copyright (c) 2013 Bilibili
 * copyright (c) 2013 Zhang Rui <bbcallen@gmail.com>
 *
 * This file is part of ijkPlayer.
 *
 * ijkPlayer is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * ijkPlayer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with ijkPlayer; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

#include "android_audiotrack.h"
#include "fs_android_channels.h"

#include <assert.h>
#include "j4au/class/android/media/AudioTrack.util.h"
#include "ijksdl_android_jni.h"
#include "../ijksdl_inc_internal.h"
#include "../ijksdl_audio.h"

#ifdef SDLTRACE
#undef SDLTRACE
#define SDLTRACE(...)
#endif

_Static_assert(CHANNEL_OUT_MONO == FS_CHANNEL_OUT_MONO, "mono mask");
_Static_assert(CHANNEL_OUT_STEREO == FS_CHANNEL_OUT_STEREO, "stereo mask");
_Static_assert(CHANNEL_OUT_QUAD == FS_CHANNEL_OUT_QUAD, "quad mask");
_Static_assert(CHANNEL_OUT_5POINT1 == FS_CHANNEL_OUT_5POINT1, "5.1 mask");
_Static_assert(CHANNEL_OUT_7POINT1 == FS_CHANNEL_OUT_7POINT1, "7.1 mask");

typedef struct AudioFormatMapEntry {
    SDL_AudioFormat sdl_format;
    int android_format;
    const char *sdl_name;
    const char *android_name;
} AudioFormatMapEntry;
static AudioFormatMapEntry g_audio_format_map[] = {
    { AUDIO_S16SYS, ENCODING_PCM_16BIT, "AUDIO_S16SYS", "ENCODING_PCM_16BIT" },
    { AUDIO_U8,     ENCODING_PCM_8BIT,  "AUDIO_U8",     "ENCODING_PCM_8BIT" },
    { AUDIO_F32,    ENCODING_PCM_FLOAT, "AUDIO_F32",    "ENCODING_PCM_FLOAT" },
    { AUDIO_AC3,    ENCODING_AC3,       "AUDIO_AC3",    "ENCODING_AC3" },
    { AUDIO_EAC3,   ENCODING_E_AC3,     "AUDIO_EAC3",   "ENCODING_E_AC3" },
    { AUDIO_EAC3_JOC, ENCODING_E_AC3_JOC, "AUDIO_EAC3_JOC", "ENCODING_E_AC3_JOC" },
    { AUDIO_IEC61937, ENCODING_IEC61937, "AUDIO_IEC61937", "ENCODING_IEC61937" },
    { AUDIO_IEC61937_PCM, ENCODING_PCM_16BIT, "AUDIO_IEC61937_PCM", "ENCODING_PCM_16BIT" },
};

static jclass g_encoded_audiotrack_class;
static jmethodID g_encoded_audiotrack_open;
static jclass g_passthrough_log_class;
static jmethodID g_passthrough_log_note;
static jmethodID g_passthrough_log_buffered;
static jmethodID g_passthrough_log_sync;
static jmethodID g_write_burst;

static int audiotrack_format_is_encoded(int format)
{
    return format == ENCODING_AC3 || format == ENCODING_E_AC3 || format == ENCODING_E_AC3_JOC
        || format == ENCODING_IEC61937;
}

static Uint8 find_sdl_channel(int android_channel)
{
    return (Uint8) fs_android_channel_count(android_channel);
}

static int find_android_channel(int sdl_channel)
{
    int mask = fs_android_channel_mask(sdl_channel);
    return mask == FS_CHANNEL_OUT_INVALID ? CHANNEL_OUT_INVALID : mask;
}

static SDL_AudioFormat find_sdl_format(int android_format)
{
    for (int i = 0; i < NELEM(g_audio_format_map); ++i) {
        AudioFormatMapEntry *entry = &g_audio_format_map[i];
        if (entry->android_format == android_format)
            return entry->sdl_format;
    }
    return AUDIO_INVALID;
}

static int find_android_format(int sdl_format)
{
    for (int i = 0; i < NELEM(g_audio_format_map); ++i) {
        AudioFormatMapEntry *entry = &g_audio_format_map[i];
        if (entry->sdl_format == sdl_format)
            return entry->android_format;
    }
    return ENCODING_INVALID;
}

typedef struct SDL_Android_AudioTrack {
    jobject thiz;

    SDL_Android_AudioTrack_Spec spec;

    jbyteArray  byte_buffer;
    int         byte_buffer_capacity;
    int         min_buffer_size;
    float       max_volume;
    float       min_volume;
    int         encoded;
    int         obtained_valid;
    int         obtained_encoding;
    int         obtained_rate;
    int         obtained_channels;
    int         obtained_mask;
} SDL_Android_AudioTrack;

void SDL_Android_AudioTrack_global_init(JNIEnv *env)
{
    jclass local;

    if (!env || g_encoded_audiotrack_class)
        return;
    local = (*env)->FindClass(env, "com/debugly/fsplayer/audio/FsEncodedAudioTrack");
    if ((*env)->ExceptionCheck(env) || !local) {
        (*env)->ExceptionClear(env);
        ALOGE("FsEncodedAudioTrack class not found; Dolby passthrough will fall back to PCM");
        return;
    }
    g_encoded_audiotrack_class = (*env)->NewGlobalRef(env, local);
    (*env)->DeleteLocalRef(env, local);
    g_encoded_audiotrack_open = (*env)->GetStaticMethodID(
        env, g_encoded_audiotrack_class, "open", "(III)Landroid/media/AudioTrack;");
    if ((*env)->ExceptionCheck(env) || !g_encoded_audiotrack_open) {
        (*env)->ExceptionClear(env);
        ALOGE("FsEncodedAudioTrack.open not found");
        g_encoded_audiotrack_open = NULL;
    }
    g_write_burst = (*env)->GetStaticMethodID(
        env, g_encoded_audiotrack_class, "writeBurst",
        "(Landroid/media/AudioTrack;[BI)I");
    if ((*env)->ExceptionCheck(env) || !g_write_burst) {
        (*env)->ExceptionClear(env);
        ALOGE("FsEncodedAudioTrack.writeBurst not found");
        g_write_burst = NULL;
    }
    local = (*env)->FindClass(env, "com/debugly/fsplayer/audio/FsPassthroughLog");
    if ((*env)->ExceptionCheck(env) || !local) {
        (*env)->ExceptionClear(env);
        ALOGE("FsPassthroughLog class not found");
        return;
    }
    g_passthrough_log_class = (*env)->NewGlobalRef(env, local);
    (*env)->DeleteLocalRef(env, local);
    g_passthrough_log_note = (*env)->GetStaticMethodID(
        env, g_passthrough_log_class, "note", "(Ljava/lang/String;)V");
    if ((*env)->ExceptionCheck(env) || !g_passthrough_log_note) {
        (*env)->ExceptionClear(env);
        ALOGE("FsPassthroughLog.note not found");
        g_passthrough_log_note = NULL;
    }
    g_passthrough_log_buffered = (*env)->GetStaticMethodID(
        env, g_passthrough_log_class, "noteBuffered", "(Ljava/lang/String;)V");
    if ((*env)->ExceptionCheck(env) || !g_passthrough_log_buffered) {
        (*env)->ExceptionClear(env);
        g_passthrough_log_buffered = NULL;
    }
    g_passthrough_log_sync = (*env)->GetStaticMethodID(
        env, g_passthrough_log_class, "sync", "()V");
    if ((*env)->ExceptionCheck(env) || !g_passthrough_log_sync) {
        (*env)->ExceptionClear(env);
        g_passthrough_log_sync = NULL;
    }
}

static void fs_passthrough_call(JNIEnv *env, jmethodID method, const char *line)
{
    jstring text;
    if (!env || !method || !line || !g_passthrough_log_class)
        return;
    text = (*env)->NewStringUTF(env, line);
    if ((*env)->ExceptionCheck(env) || !text) {
        (*env)->ExceptionClear(env);
        return;
    }
    (*env)->CallStaticVoidMethod(env, g_passthrough_log_class, method, text);
    (*env)->DeleteLocalRef(env, text);
    if ((*env)->ExceptionCheck(env))
        (*env)->ExceptionClear(env);
}

void fs_passthrough_log_line(const char *line)
{
    JNIEnv *env = NULL;
    if (!line)
        return;
    if (JNI_OK != SDL_JNI_SetupThreadEnv(&env))
        return;
    SDL_Android_AudioTrack_log(env, line);
}

void fs_passthrough_log_line_buffered(const char *line)
{
    JNIEnv *env = NULL;
    if (!line)
        return;
    if (JNI_OK != SDL_JNI_SetupThreadEnv(&env))
        return;
    if (g_passthrough_log_buffered)
        fs_passthrough_call(env, g_passthrough_log_buffered, line);
    else
        SDL_Android_AudioTrack_log(env, line);
}

void fs_passthrough_log_sync(void)
{
    JNIEnv *env = NULL;
    if (JNI_OK != SDL_JNI_SetupThreadEnv(&env))
        return;
    if (!env || !g_passthrough_log_class || !g_passthrough_log_sync)
        return;
    (*env)->CallStaticVoidMethod(env, g_passthrough_log_class, g_passthrough_log_sync);
    if ((*env)->ExceptionCheck(env))
        (*env)->ExceptionClear(env);
}

void SDL_Android_AudioTrack_log(JNIEnv *env, const char *line)
{
    jstring text;
    if (!env || !line || !g_passthrough_log_class || !g_passthrough_log_note)
        return;
    text = (*env)->NewStringUTF(env, line);
    if ((*env)->ExceptionCheck(env) || !text) {
        (*env)->ExceptionClear(env);
        return;
    }
    (*env)->CallStaticVoidMethod(env, g_passthrough_log_class, g_passthrough_log_note, text);
    (*env)->DeleteLocalRef(env, text);
    if ((*env)->ExceptionCheck(env))
        (*env)->ExceptionClear(env);
}

static jobject audiotrack_open_encoded(JNIEnv *env, int encoding, int sample_rate, int channel_mask)
{
    jobject local;
    jobject global;

    if (!g_encoded_audiotrack_class || !g_encoded_audiotrack_open) {
        ALOGE("encoded AudioTrack opener is not initialized");
        return NULL;
    }
    local = (*env)->CallStaticObjectMethod(
        env, g_encoded_audiotrack_class, g_encoded_audiotrack_open,
        encoding, sample_rate, channel_mask);
    if ((*env)->ExceptionCheck(env) || !local) {
        (*env)->ExceptionClear(env);
        ALOGE("AudioTrack.Builder rejected encoding %d", encoding);
        return NULL;
    }
    global = (*env)->NewGlobalRef(env, local);
    (*env)->DeleteLocalRef(env, local);
    return global;
}

static void SDL_Android_AudioTrack_get_default_spec(SDL_Android_AudioTrack_Spec *spec)
{
    assert(spec);
    spec->stream_type = STREAM_MUSIC;
    spec->sample_rate_in_hz = 0;
    spec->channel_config = CHANNEL_OUT_STEREO;
    spec->audio_format = ENCODING_PCM_16BIT;
    spec->burst = 0;
    spec->buffer_size_in_bytes = 0;
    spec->mode = MODE_STREAM;
}

#define STREAM_MUSIC 3
int audiotrack_get_native_output_sample_rate(JNIEnv *env)
{
    if (!env) {
        if (JNI_OK != SDL_JNI_SetupThreadEnv(&env)) {
            ALOGE("%s: SetupThreadEnv failed", __func__);
            return -1;
        }
    }

    jint retval = J4AC_AudioTrack__getNativeOutputSampleRate(env, STREAM_MUSIC);
    if (J4A_ExceptionCheck__catchAll(env) || retval <= 0)
        return -1;

    return retval;
}

void SDL_Android_AudioTrack_set_volume(JNIEnv *env, SDL_Android_AudioTrack *atrack, float left_volume, float right_volume)
{
    J4AC_AudioTrack__setStereoVolume__catchAll(env, atrack->thiz, left_volume, right_volume);
}

static int audiotrack_read_int(JNIEnv *env, jobject thiz, const char *name)
{
    jclass cls;
    jmethodID method;
    int value;
    if (!env || !thiz || !name)
        return -1;
    cls = (*env)->GetObjectClass(env, thiz);
    if (!cls || (*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        return -1;
    }
    method = (*env)->GetMethodID(env, cls, name, "()I");
    (*env)->DeleteLocalRef(env, cls);
    if (!method || (*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        return -1;
    }
    value = (*env)->CallIntMethod(env, thiz, method);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        return -1;
    }
    return value;
}

void SDL_Android_AudioTrack_get_obtained(SDL_Android_AudioTrack *atrack, int *valid,
                                         int *encoding, int *rate, int *channels, int *mask)
{
    if (valid)
        *valid = 0;
    if (!atrack || !atrack->obtained_valid)
        return;
    if (valid)
        *valid = 1;
    if (encoding)
        *encoding = atrack->obtained_encoding;
    if (rate)
        *rate = atrack->obtained_rate;
    if (channels)
        *channels = atrack->obtained_channels;
    if (mask)
        *mask = atrack->obtained_mask;
}

SDL_Android_AudioTrack *SDL_Android_AudioTrack_new_from_spec(JNIEnv *env, SDL_Android_AudioTrack_Spec *spec)
{
    assert(spec);

    switch (spec->channel_config) {
    case CHANNEL_OUT_MONO:
        ALOGI("SDL_Android_AudioTrack: %s", "CHANNEL_OUT_MONO");
        break;
    case CHANNEL_OUT_STEREO:
        ALOGI("SDL_Android_AudioTrack: %s", "CHANNEL_OUT_STEREO");
        break;
    case CHANNEL_OUT_QUAD:
        ALOGI("SDL_Android_AudioTrack: %s", "CHANNEL_OUT_QUAD");
        break;
    case CHANNEL_OUT_5POINT1:
        ALOGI("SDL_Android_AudioTrack: %s", "CHANNEL_OUT_5POINT1");
        break;
    case CHANNEL_OUT_7POINT1:
        ALOGI("SDL_Android_AudioTrack: %s", "CHANNEL_OUT_7POINT1");
        break;
    default:
        ALOGE("%s: invalid channel %d", __func__, spec->channel_config);
        return NULL;
    }

    switch (spec->audio_format) {
    case ENCODING_PCM_16BIT:
        ALOGI("SDL_Android_AudioTrack: %s", "ENCODING_PCM_16BIT");
        break;
    case ENCODING_PCM_8BIT:
        ALOGI("SDL_Android_AudioTrack: %s", "ENCODING_PCM_8BIT");
        break;
    case ENCODING_AC3:
        ALOGI("SDL_Android_AudioTrack: %s", "ENCODING_AC3");
        break;
    case ENCODING_E_AC3:
        ALOGI("SDL_Android_AudioTrack: %s", "ENCODING_E_AC3");
        break;
    case ENCODING_E_AC3_JOC:
        ALOGI("SDL_Android_AudioTrack: %s", "ENCODING_E_AC3_JOC");
        break;
    case ENCODING_IEC61937:
        ALOGI("SDL_Android_AudioTrack: %s", "ENCODING_IEC61937");
        break;
#if 0
    case ENCODING_PCM_FLOAT:
        ALOGI("SDL_Android_AudioTrack: %s", "ENCODING_PCM_FLOAT");
        if (sdk_int < IJK_API_21_LOLLIPOP) {
            ALOGI("SDL_Android_AudioTrack: %s need API 21 or above", "ENCODING_PCM_FLOAT");
            return NULL;
        }
        break;
#endif
    default:
        ALOGE("%s: invalid format %d", __func__, spec->audio_format);
        return NULL;
    }

    if (spec->sample_rate_in_hz <= 0) {
        ALOGE("%s: invalid sample rate %d", __func__, spec->sample_rate_in_hz);
        return NULL;
    }

    SDL_Android_AudioTrack *atrack = (SDL_Android_AudioTrack*) mallocz(sizeof(SDL_Android_AudioTrack));
    if (!atrack) {
        ALOGE("%s: mallocz faild.\n", __func__);
        return NULL;
    }
    atrack->spec = *spec;
    atrack->encoded = audiotrack_format_is_encoded(atrack->spec.audio_format) || spec->burst;

    /*
     * Encoded AC-3 / E-AC-3 rates are the bitstream rate (usually 48000).
     * Do not fold them into the PCM resampler range. PCM still does, because
     * libswresample is used to land on a rate AudioTrack will accept.
     */
    if (!atrack->encoded) {
        while (atrack->spec.sample_rate_in_hz < 4000) {
            atrack->spec.sample_rate_in_hz *= 2;
        }
        while (atrack->spec.sample_rate_in_hz > 48000) {
            atrack->spec.sample_rate_in_hz /= 2;
        }
    }

    int min_buffer_size = J4AC_AudioTrack__getMinBufferSize(env,
        atrack->spec.sample_rate_in_hz,
        atrack->spec.channel_config,
        atrack->spec.audio_format);
    if (J4A_ExceptionCheck__catchAll(env) || min_buffer_size <= 0) {
        if (!atrack->encoded && fs_android_channel_count(atrack->spec.channel_config) > 2) {
            ALOGE("%s: multichannel PCM getMinBufferSize failed mask=0x%x (%d); falling back",
                  __func__, atrack->spec.channel_config, min_buffer_size);
        } else {
            ALOGE("%s: J4AC_AudioTrack__getMinBufferSize: return %d:", __func__, min_buffer_size);
        }
        free(atrack);
        return NULL;
    }

    if (atrack->encoded) {
        if (min_buffer_size < 65536)
            min_buffer_size = 65536;
        atrack->thiz = audiotrack_open_encoded(env,
            atrack->spec.audio_format,
            atrack->spec.sample_rate_in_hz,
            atrack->spec.channel_config);
    } else {
        /* PCM can be time-stretched, so keep headroom for 2x playback. */
        min_buffer_size *= AUDIOTRACK_PLAYBACK_MAXSPEED;
        atrack->thiz = J4AC_AudioTrack__AudioTrack__asGlobalRef__catchAll(env,
            atrack->spec.stream_type,
            atrack->spec.sample_rate_in_hz,
            atrack->spec.channel_config,
            atrack->spec.audio_format,
            min_buffer_size,
            atrack->spec.mode);
    }
    if (!atrack->thiz) {
        if (!atrack->encoded && fs_android_channel_count(atrack->spec.channel_config) > 2) {
            ALOGE("%s: multichannel PCM AudioTrack open failed mask=0x%x; falling back",
                  __func__, atrack->spec.channel_config);
        } else {
            ALOGE("%s: failed to open AudioTrack format %d", __func__, atrack->spec.audio_format);
        }
        free(atrack);
        return NULL;
    }

    atrack->obtained_encoding = audiotrack_read_int(env, atrack->thiz, "getAudioFormat");
    atrack->obtained_rate = audiotrack_read_int(env, atrack->thiz, "getSampleRate");
    atrack->obtained_channels = audiotrack_read_int(env, atrack->thiz, "getChannelCount");
    atrack->obtained_mask = audiotrack_read_int(env, atrack->thiz, "getChannelMask");
    atrack->obtained_valid = atrack->obtained_encoding > 0 && atrack->obtained_rate > 0 &&
        atrack->obtained_channels > 0 && atrack->obtained_mask > 0;
    atrack->min_buffer_size = min_buffer_size;
    atrack->spec.buffer_size_in_bytes = min_buffer_size;
    // atrack->max_volume = J4AC_AudioTrack__getMaxVolume__catchAll(env);
    // atrack->min_volume = J4AC_AudioTrack__getMinVolume__catchAll(env);
    atrack->max_volume = 1.0f;
    atrack->min_volume = 0.0f;

    // extra init
    float init_volume = 1.0f;
    init_volume = IJKMIN(init_volume, atrack->max_volume);
    init_volume = IJKMAX(init_volume, atrack->min_volume);
    ALOGI("%s: init volume as %f/(%f,%f) encoded=%d", __func__, init_volume, atrack->min_volume, atrack->max_volume, atrack->encoded);
    if (!atrack->encoded)
        J4AC_AudioTrack__setStereoVolume__catchAll(env, atrack->thiz, init_volume, init_volume);

    return atrack;
}

SDL_Android_AudioTrack *SDL_Android_AudioTrack_new_from_sdl_spec(JNIEnv *env, const SDL_AudioSpec *sdl_spec)
{
    SDL_Android_AudioTrack_Spec atrack_spec;

    SDL_Android_AudioTrack_get_default_spec(&atrack_spec);
    atrack_spec.sample_rate_in_hz = sdl_spec->freq;
    atrack_spec.channel_config = find_android_channel(sdl_spec->channels);
    atrack_spec.audio_format = find_android_format(sdl_spec->format);
    atrack_spec.burst = sdl_spec->format == AUDIO_IEC61937 || sdl_spec->format == AUDIO_IEC61937_PCM;
    atrack_spec.buffer_size_in_bytes = sdl_spec->size;

    return SDL_Android_AudioTrack_new_from_spec(env, &atrack_spec);
}

void SDL_Android_AudioTrack_free(JNIEnv *env, SDL_Android_AudioTrack* atrack)
{
    J4A_DeleteGlobalRef__p(env, &atrack->byte_buffer);
    atrack->byte_buffer_capacity = 0;

    if (atrack->thiz) {
        J4AC_AudioTrack__release(env, atrack->thiz);
        J4A_DeleteGlobalRef__p(env, &atrack->thiz);
    }

    free(atrack);
}

void SDL_Android_AudioTrack_get_target_spec(SDL_Android_AudioTrack *atrack, SDL_AudioSpec *sdl_spec)
{
    SDL_Android_AudioTrack_Spec *atrack_spec = &atrack->spec;

    sdl_spec->freq = atrack_spec->sample_rate_in_hz;
    sdl_spec->channels = find_sdl_channel(atrack_spec->channel_config);
    sdl_spec->format = find_sdl_format(atrack_spec->audio_format);
    sdl_spec->size = atrack_spec->buffer_size_in_bytes;
    sdl_spec->silence = 0;
    sdl_spec->padding = 0;
}

int SDL_Android_AudioTrack_get_min_buffer_size(SDL_Android_AudioTrack* atrack)
{
    return atrack->min_buffer_size;
}

void SDL_Android_AudioTrack_play(JNIEnv *env, SDL_Android_AudioTrack *atrack)
{
    SDLTRACE("%s", __func__);
    if (!env || !atrack || !atrack->thiz)
        return;
    J4AC_AudioTrack__play__catchAll(env, atrack->thiz);
}

void SDL_Android_AudioTrack_pause(JNIEnv *env, SDL_Android_AudioTrack *atrack)
{
    SDLTRACE("%s", __func__);
    J4AC_AudioTrack__pause__catchAll(env, atrack->thiz);
}

void SDL_Android_AudioTrack_flush(JNIEnv *env, SDL_Android_AudioTrack *atrack)
{
    SDLTRACE("%s", __func__);
    J4AC_AudioTrack__flush__catchAll(env, atrack->thiz);
}

void SDL_Android_AudioTrack_stop(JNIEnv *env, SDL_Android_AudioTrack *atrack)
{
    SDLTRACE("%s", __func__);
    J4AC_AudioTrack__stop__catchAll(env, atrack->thiz);
}

int SDL_Android_AudioTrack_reserve_byte_buffer(JNIEnv *env, SDL_Android_AudioTrack *atrack, int size_in_byte)
{
    if (atrack->byte_buffer && size_in_byte <= atrack->byte_buffer_capacity)
        return size_in_byte;

    J4A_DeleteGlobalRef__p(env, &atrack->byte_buffer);
    atrack->byte_buffer_capacity = 0;

    int capacity = IJKMAX(size_in_byte, atrack->min_buffer_size);
    atrack->byte_buffer = J4A_NewByteArray__asGlobalRef__catchAll(env, capacity);
    if (!atrack->byte_buffer)
        return -1;

    atrack->byte_buffer_capacity = capacity;
    return capacity;
}

static jmethodID g_write_nonblock;
static jmethodID g_playback_head;
static jmethodID g_get_timestamp;
static jmethodID g_buffer_frames;
static jmethodID g_nano_time;
static jmethodID g_ts_ctor;
static jfieldID g_ts_frame;
static jfieldID g_ts_nano;
static jclass g_ts_class;
static jclass g_system_class;
static int g_audio_methods_ready;

static void audiotrack_cache_methods(JNIEnv *env, jobject track)
{
    jclass cls;
    if (g_audio_methods_ready || !env || !track)
        return;
    cls = (*env)->GetObjectClass(env, track);
    if (cls) {
        g_write_nonblock = (*env)->GetMethodID(env, cls, "write", "([BIII)I");
        if ((*env)->ExceptionCheck(env))
            (*env)->ExceptionClear(env);
        g_playback_head = (*env)->GetMethodID(env, cls, "getPlaybackHeadPosition", "()I");
        if ((*env)->ExceptionCheck(env))
            (*env)->ExceptionClear(env);
        g_get_timestamp = (*env)->GetMethodID(env, cls, "getTimestamp", "(Landroid/media/AudioTimestamp;)Z");
        if ((*env)->ExceptionCheck(env))
            (*env)->ExceptionClear(env);
        g_buffer_frames = (*env)->GetMethodID(env, cls, "getBufferSizeInFrames", "()I");
        if ((*env)->ExceptionCheck(env))
            (*env)->ExceptionClear(env);
        (*env)->DeleteLocalRef(env, cls);
    }
    if (!g_ts_class) {
        jclass local = (*env)->FindClass(env, "android/media/AudioTimestamp");
        if (local && !(*env)->ExceptionCheck(env)) {
            g_ts_class = (*env)->NewGlobalRef(env, local);
            g_ts_ctor = (*env)->GetMethodID(env, g_ts_class, "<init>", "()V");
            g_ts_frame = (*env)->GetFieldID(env, g_ts_class, "framePosition", "J");
            g_ts_nano = (*env)->GetFieldID(env, g_ts_class, "nanoTime", "J");
            (*env)->DeleteLocalRef(env, local);
        }
        if ((*env)->ExceptionCheck(env))
            (*env)->ExceptionClear(env);
    }
    if (!g_system_class) {
        jclass local = (*env)->FindClass(env, "java/lang/System");
        if (local && !(*env)->ExceptionCheck(env)) {
            g_system_class = (*env)->NewGlobalRef(env, local);
            g_nano_time = (*env)->GetStaticMethodID(env, g_system_class, "nanoTime", "()J");
            (*env)->DeleteLocalRef(env, local);
        }
        if ((*env)->ExceptionCheck(env))
            (*env)->ExceptionClear(env);
    }
    g_audio_methods_ready = 1;
}

int SDL_Android_AudioTrack_write_nonblock(JNIEnv *env, SDL_Android_AudioTrack *atrack,
                                          uint8_t *data, int total, int offset)
{
    int accepted;
    if (!env || !atrack || !atrack->thiz || !data || total <= 0 || offset < 0 || offset >= total)
        return -1;
    audiotrack_cache_methods(env, atrack->thiz);
    if (offset == 0) {
        if (SDL_Android_AudioTrack_reserve_byte_buffer(env, atrack, total) < total)
            return -1;
        (*env)->SetByteArrayRegion(env, atrack->byte_buffer, 0, total, (jbyte *)data);
        if (J4A_ExceptionCheck__catchAll(env))
            return -1;
    }
    if (!g_write_nonblock)
        return J4AC_AudioTrack__write(env, atrack->thiz, atrack->byte_buffer, offset, total - offset);
    /* AudioTrack.WRITE_NON_BLOCKING == 1. */
    accepted = (*env)->CallIntMethod(env, atrack->thiz, g_write_nonblock,
                                     atrack->byte_buffer, offset, total - offset, 1);
    if (J4A_ExceptionCheck__catchAll(env))
        return -1;
    return accepted;
}

int SDL_Android_AudioTrack_playback_head(JNIEnv *env, SDL_Android_AudioTrack *atrack, int *valid)
{
    int head;
    if (valid)
        *valid = 0;
    if (!env || !atrack || !atrack->thiz)
        return 0;
    audiotrack_cache_methods(env, atrack->thiz);
    if (!g_playback_head)
        return 0;
    head = (*env)->CallIntMethod(env, atrack->thiz, g_playback_head);
    if (J4A_ExceptionCheck__catchAll(env))
        return 0;
    if (valid)
        *valid = 1;
    return head;
}

int SDL_Android_AudioTrack_timestamp(JNIEnv *env, SDL_Android_AudioTrack *atrack,
                                     int64_t *frame_position, int64_t *nano_time, int *valid)
{
    jobject stamp;
    jboolean ok;
    if (valid)
        *valid = 0;
    if (frame_position)
        *frame_position = 0;
    if (nano_time)
        *nano_time = 0;
    if (!env || !atrack || !atrack->thiz)
        return 0;
    audiotrack_cache_methods(env, atrack->thiz);
    if (!g_get_timestamp || !g_ts_class || !g_ts_ctor || !g_ts_frame || !g_ts_nano)
        return 0;
    stamp = (*env)->NewObject(env, g_ts_class, g_ts_ctor);
    if ((*env)->ExceptionCheck(env) || !stamp) {
        (*env)->ExceptionClear(env);
        return 0;
    }
    ok = (*env)->CallBooleanMethod(env, atrack->thiz, g_get_timestamp, stamp);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        (*env)->DeleteLocalRef(env, stamp);
        return 0;
    }
    if (ok) {
        if (frame_position)
            *frame_position = (*env)->GetLongField(env, stamp, g_ts_frame);
        if (nano_time)
            *nano_time = (*env)->GetLongField(env, stamp, g_ts_nano);
        if (valid)
            *valid = 1;
    }
    (*env)->DeleteLocalRef(env, stamp);
    return ok ? 1 : 0;
}

int SDL_Android_AudioTrack_buffer_frames(JNIEnv *env, SDL_Android_AudioTrack *atrack, int *valid)
{
    int frames;
    if (valid)
        *valid = 0;
    if (!env || !atrack || !atrack->thiz)
        return 0;
    audiotrack_cache_methods(env, atrack->thiz);
    if (!g_buffer_frames)
        return 0;
    frames = (*env)->CallIntMethod(env, atrack->thiz, g_buffer_frames);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        return 0;
    }
    if (valid)
        *valid = 1;
    return frames;
}

int64_t SDL_Android_nano_time(JNIEnv *env)
{
    int64_t now;
    if (!env)
        return 0;
    if (!g_audio_methods_ready)
        return 0;
    if (!g_system_class || !g_nano_time)
        return 0;
    now = (*env)->CallStaticLongMethod(env, g_system_class, g_nano_time);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        return 0;
    }
    return now;
}

int SDL_Android_AudioTrack_write(JNIEnv *env, SDL_Android_AudioTrack *atrack, uint8_t *data, int size_in_byte)
{
    if (size_in_byte <= 0)
        return size_in_byte;

    int reserved = SDL_Android_AudioTrack_reserve_byte_buffer(env, atrack, size_in_byte);
    if (reserved < size_in_byte) {
        ALOGE("%s failed %d < %d\n", __func__, reserved, size_in_byte);
        return -1;
    }

    (*env)->SetByteArrayRegion(env, atrack->byte_buffer, 0, (int)size_in_byte, (jbyte*) data);
    if (J4A_ExceptionCheck__catchAll(env))
        return -1;

    if (atrack->spec.burst && g_encoded_audiotrack_class && g_write_burst) {
        /* short[] write. AudioTrack.write(short[]) returns a sample count. */
        int samples = (*env)->CallStaticIntMethod(
            env, g_encoded_audiotrack_class, g_write_burst,
            atrack->thiz, atrack->byte_buffer, (int) size_in_byte);
        if (J4A_ExceptionCheck__catchAll(env))
            return -1;
        if (samples < 0)
            return samples;
        if (samples == size_in_byte / 2)
            return samples * 2;
        return samples;
    }

    int retval = J4AC_AudioTrack__write(env, atrack->thiz, atrack->byte_buffer, 0, (int)size_in_byte);
    if (J4A_ExceptionCheck__catchAll(env))
        return -1;

    return retval;
}

int SDL_Android_AudioTrack_getAudioSessionId(JNIEnv *env, SDL_Android_AudioTrack *atrack)
{
    SDLTRACE("%s", __func__);
    int audioSessionId = J4AC_AudioTrack__getAudioSessionId(env, atrack->thiz);
    if (J4A_ExceptionCheck__catchAll(env))
        return 0;

    return audioSessionId;
}

void SDL_Android_AudioTrack_setSpeed(JNIEnv *env, SDL_Android_AudioTrack *atrack, float speed)
{
    /* Encoded AudioTrack does not time-stretch. Speed != 1 uses the PCM path. */
    if (!atrack || atrack->encoded)
        return;
    J4AC_AudioTrack__setSpeed(env, atrack->thiz, speed);
    if (J4A_ExceptionCheck__catchAll(env))
        return;

    return;
}
