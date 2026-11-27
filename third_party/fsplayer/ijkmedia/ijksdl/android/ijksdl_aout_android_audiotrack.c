/*****************************************************************************
 * ijksdl_aout_android_audiotrack.c
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

#include "ijksdl_aout_android_audiotrack.h"

#include <stdbool.h>
#include <stdio.h>
#include <time.h>
#include <assert.h>
#include <jni.h>
#include "../ijksdl_inc_internal.h"
#include "../ijksdl_thread.h"
#include "../ijksdl_timer.h"
#include "../ijksdl_aout_internal.h"
#include "ijksdl_android_jni.h"
#include "android_audiotrack.h"
#include "fs_diag.h"
#include "fs_aac_ac3_clock.h"
#include "fs_audio_plan.h"
#include "fs_iec_clock.h"

#ifdef SDLTRACE
#undef SDLTRACE
#define SDLTRACE(...)
//#define SDLTRACE ALOGE
#endif

static SDL_Class g_audiotrack_class = {
    .name = "AudioTrack",
};

typedef struct SDL_Aout_Opaque {
    SDL_cond *wakeup_cond;
    SDL_mutex *wakeup_mutex;

    SDL_AudioSpec spec;
    SDL_Android_AudioTrack* atrack;
    uint8_t *buffer;
    int buffer_size;

    volatile bool need_flush;
    volatile bool pause_on;
    volatile bool abort_request;

    volatile bool need_set_volume;
    volatile float left_volume;
    volatile float right_volume;

    SDL_Thread *audio_tid;
    SDL_Thread _audio_tid;

    int audio_session_id;

    volatile float speed;
    volatile bool speed_changed;
    int encoded;
    /* 1 only while AudioTrack.write is inside the HAL. Close may stop and
     * release in that window. Seek must not. */
    volatile int pcm_blocking_write;
    volatile int encoded_write_in_progress;
    volatile int thread_done;
    volatile int abandoned;
    volatile int track_released;
    int pcm_seek_epoch;
    int pcm_seek_pending;
    int pcm_seek_pause_logged;
    int device_pause;
    int device_held;
    int played;
    int encoded_stop;
    int callback_noted;
    int empty_logs;
    int write_n;
    int prev_log_ms;
    int64_t play_ms;
    int64_t cum_bytes;
    int64_t gap_begin_us;
    int64_t gap_end_us;
    int have_gap;
    int generation;
    int release_phase;
    int write_offset;
} SDL_Aout_Opaque;

static int g_audio_generation;

static int64_t aout_mono_ms(void)
{
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t) ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

static int aout_log_timed(JNIEnv *env, const char *line)
{
    int64_t t0 = aout_mono_ms();
    SDL_Android_AudioTrack_log(env, line);
    return (int) (aout_mono_ms() - t0);
}

/* One E-AC-3 syncframe: 0x0B77 and frmsiz covering this buffer and nothing else. */
static int eac3_write_is_one_syncframe(const uint8_t *data, int size)
{
    int frmsiz;
    int bytes;
    if (!data || size < 4 || size > 4096)
        return 0;
    if (data[0] != 0x0B || data[1] != 0x77)
        return 0;
    frmsiz = ((data[2] & 0x07) << 8) | data[3];
    bytes = (frmsiz + 1) * 2;
    return bytes == size;
}

static int aout_format_is_encoded(SDL_AudioFormat format)
{
    return format == AUDIO_AC3 || format == AUDIO_EAC3 || format == AUDIO_EAC3_JOC
        || format == AUDIO_IEC61937 || format == AUDIO_IEC61937_PCM;
}

static int iec_burst_is_one_period(const uint8_t *data, int size)
{
    /* 24576 bytes: IEC 61937-3 E-AC-3 repetition period. */
    if (!data || size != 24576)
        return 0;
    /* Pa Pb, little-endian, data-type 21. */
    if (data[0] != 0x72 || data[1] != 0xF8 || data[2] != 0x1F || data[3] != 0x4E)
        return 0;
    return data[4] == 21 && data[5] == 0;
}

static void aout_log_step(int generation, unsigned format, const char *step, const char *edge)
{
    char line[160];
    snprintf(line, sizeof(line), "audio %s %s encoding=0x%x gen=%d",
             step, edge, format, generation);
    fs_passthrough_log_line(line);
}

static void aout_shutdown_track(JNIEnv *env, SDL_Aout *aout)
{
    SDL_Aout_Opaque *opaque = aout->opaque;
    SDL_Android_AudioTrack *atrack;
    const char *name = NULL;
    unsigned format = opaque->spec.format;
    SDL_LockMutex(opaque->wakeup_mutex);
    atrack = opaque->track_released ? NULL : opaque->atrack;
    if (atrack)
        opaque->atrack = NULL;
    SDL_UnlockMutex(opaque->wakeup_mutex);
    if (!atrack) {
        fs_passthrough_log_line("audio thread exit");
        return;
    }
    while (fs_audio_release_step(&opaque->release_phase, &name) == 0) {
        aout_log_step(opaque->generation, format, name, "begin");
        if (name[0] == 'p')
            SDL_Android_AudioTrack_pause(env, atrack);
        else if (name[0] == 'f')
            SDL_Android_AudioTrack_flush(env, atrack);
        else if (name[0] == 's')
            SDL_Android_AudioTrack_stop(env, atrack);
        else
            SDL_Android_AudioTrack_free(env, atrack);
        aout_log_step(opaque->generation, format, name, "result=ok");
        if (name[0] == 'r') {
            fs_passthrough_log_line("audio thread exit");
            return;
        }
    }
}

/* Seeks keep the track. Old PCM and encoded bursts are dropped by serial.
 * AudioTrack.flush stays off this path. */
static void aout_consume_flush(JNIEnv *env, SDL_Aout *aout)
{
    SDL_Aout_Opaque *opaque = aout->opaque;
    char line[180];
    if (!opaque->need_flush || !opaque->atrack)
        return;
    opaque->need_flush = 0;
    if (fs_seek_flushes_track(opaque->encoded)) {
        SDL_Android_AudioTrack_flush(env, opaque->atrack);
        SDL_Android_AudioTrack_log(env, "audio seek-apply action=encoded-flush");
        return;
    }
    opaque->pcm_seek_epoch++;
    opaque->pcm_seek_pending = 1;
    opaque->pcm_seek_pause_logged = 0;
    snprintf(line, sizeof(line), "audio seek-apply gen=%d epoch=%d action=%s",
             opaque->generation, opaque->pcm_seek_epoch,
             opaque->encoded ? "encoded-drop" : "pcm-keep-track");
    SDL_Android_AudioTrack_log(env, line);
}

static void aout_note_pcm_write(JNIEnv *env, SDL_Aout_Opaque *opaque, int written, int size)
{
    char line[200];
    if (!opaque->pcm_seek_pending)
        return;
    snprintf(line, sizeof(line),
             "audio write after-seek gen=%d epoch=%d bytes=%d written=%d",
             opaque->generation, opaque->pcm_seek_epoch, size, written);
    opaque->pcm_seek_pending = 0;
    SDL_Android_AudioTrack_log(env, line);
}

static int aout_write_encoded(JNIEnv *env, SDL_Aout *aout, const uint8_t *payload, int size)
{
    SDL_Aout_Opaque *opaque = aout->opaque;
    int complete = 0;
    int blocked = 0;
    while (!complete && !opaque->abort_request && !opaque->need_flush) {
        int result;
        opaque->encoded_write_in_progress = 1;
        __sync_synchronize();
        result = SDL_Android_AudioTrack_write_nonblock(
            env, opaque->atrack, (uint8_t *)payload, size, opaque->write_offset);
        opaque->encoded_write_in_progress = 0;
        if (fs_encoded_write_step(&opaque->write_offset, size, result, &complete, &blocked) < 0) {
            ALOGW("AudioTrack: encoded write failed %d/%d", result, size);
            opaque->encoded_stop = 1;
            return -1;
        }
        if (blocked) {
            if (opaque->spec.format == AUDIO_AC3)
                fs_ac3_client_hold(opaque->spec.userdata);
            SDL_Delay(5);
        }
    }
    if (!complete)
        return 0;
    return size;
}

static int aout_thread_n(JNIEnv *env, SDL_Aout *aout)
{
    SDL_Aout_Opaque *opaque = aout->opaque;
    SDL_Android_AudioTrack *atrack = opaque->atrack;
    SDL_AudioCallback audio_cblk = opaque->spec.callback;
    void *userdata = opaque->spec.userdata;
    uint8_t *buffer = opaque->buffer;
    int bytes_per_frame = opaque->encoded ? 1 : (int) opaque->spec.channels * 2;
    if (bytes_per_frame < 1)
        bytes_per_frame = 1;
    /* 5.1 S16 is 12 bytes/frame. A 256-byte chunk would rotate the channels. */
    int copy_size = (256 / bytes_per_frame) * bytes_per_frame;
    if (copy_size < bytes_per_frame)
        copy_size = bytes_per_frame;
    if (!opaque->encoded && opaque->buffer_size > bytes_per_frame && copy_size > opaque->buffer_size)
        copy_size = (opaque->buffer_size / bytes_per_frame) * bytes_per_frame;

    assert(atrack);
    assert(buffer);

    SDL_SetThreadPriority(SDL_THREAD_PRIORITY_HIGH);

    if (!opaque->abort_request && !opaque->pause_on) {
        char line[320];
        if (opaque->play_ms == 0)
            opaque->play_ms = aout_mono_ms();
        {
            int valid = 0, encoding = 0, rate = 0, channels = 0, mask = 0;
            SDL_Android_AudioTrack_get_obtained(atrack, &valid, &encoding, &rate, &channels, &mask);
            snprintf(line, sizeof(line),
                     "audio play requested_sdl=0x%x requested_rate=%d requested_channels=%d "
                     "obtained_valid=%d obtained_encoding=%d obtained_rate=%d obtained_channels=%d obtained_mask=0x%x",
                     (unsigned) opaque->spec.format, opaque->spec.freq, opaque->spec.channels,
                     valid, encoding, rate, channels, mask);
        }
        opaque->prev_log_ms = aout_log_timed(env, line);
        SDL_Android_AudioTrack_play(env, atrack);
        opaque->played = 1;
        opaque->prev_log_ms = aout_log_timed(env, "audio play returned");
    }

    while (!opaque->abort_request) {
        SDL_LockMutex(opaque->wakeup_mutex);
        if (!opaque->abort_request && opaque->pause_on) {
            int hold_device = opaque->encoded || opaque->device_pause;
            int feed_logged = 0;
            if (hold_device && !opaque->device_held) {
                SDL_Android_AudioTrack_pause(env, atrack);
                opaque->device_held = 1;
                SDL_Android_AudioTrack_log(env, opaque->encoded
                    ? "audio pause device reason=encoded" : "audio pause device reason=user");
            } else if (!hold_device && opaque->played && !feed_logged) {
                SDL_Android_AudioTrack_log(env, "audio pause feed-only reason=buffer-or-seek");
                feed_logged = 1;
            }
            while (!opaque->abort_request && opaque->pause_on) {
                hold_device = opaque->encoded || opaque->device_pause;
                if (hold_device && !opaque->device_held) {
                    SDL_Android_AudioTrack_pause(env, atrack);
                    opaque->device_held = 1;
                    SDL_Android_AudioTrack_log(env, "audio pause device reason=user");
                }
                SDL_CondWaitTimeout(opaque->wakeup_cond, opaque->wakeup_mutex, 200);
            }
            if (!opaque->abort_request && !opaque->pause_on) {
                aout_consume_flush(env, aout);
                if (!opaque->played || opaque->device_held) {
                    SDL_Android_AudioTrack_log(env, "audio play resume");
                    SDL_Android_AudioTrack_play(env, atrack);
                    opaque->played = 1;
                    opaque->device_held = 0;
                    SDL_Android_AudioTrack_log(env, "audio play resume returned");
                } else {
                    SDL_Android_AudioTrack_log(env, "audio feed resume track-kept");
                }
            }
        }
        if (opaque->need_flush && !opaque->abort_request)
            aout_consume_flush(env, aout);
        if (opaque->need_set_volume) {
            opaque->need_set_volume = 0;
            SDL_Android_AudioTrack_set_volume(env, atrack, opaque->left_volume, opaque->right_volume);
        }
        if (opaque->speed_changed) {
            opaque->speed_changed = 0;
            SDL_Android_AudioTrack_setSpeed(env, atrack, opaque->speed);
        }
        SDL_UnlockMutex(opaque->wakeup_mutex);

        /*
         * IEC mode writes one app-packed 24576-byte burst. The callback
         * prefixes a little-endian length. ENCODING_E_AC3 is not used here:
         * a preamble on that encoding would be wrapped twice.
         */
        int request = opaque->encoded ? opaque->buffer_size : copy_size;
        if (opaque->encoded && !opaque->callback_noted) {
            if (audio_cblk)
                SDL_Android_AudioTrack_log(env, "audio callback entered");
            else
                SDL_Android_AudioTrack_log(env, "audio drop reason=callback-missing");
            opaque->callback_noted = 1;
        }
        if (!audio_cblk || !buffer) {
            SDL_Delay(10);
            continue;
        }
        /* A paused PCM track must not be filled. The next loop applies
         * pause, and any pending flush, then play. */
        if (!opaque->encoded && (opaque->abort_request || opaque->pause_on)) {
            SDL_Delay(10);
            continue;
        }
        audio_cblk(userdata, buffer, request);
        int drop_burst = 0;
        if (opaque->need_flush) {
            aout_consume_flush(env, aout);
            drop_burst = 1;
        }
        if (opaque->need_flush) {
            aout_consume_flush(env, aout);
            drop_burst = 1;
        }
        if (drop_burst) {
            /* The callback buffer is from before this seek. Do not write it. */
        } else if (opaque->encoded) {
            int payload = 0;
            int iec = opaque->spec.format == AUDIO_IEC61937 || opaque->spec.format == AUDIO_IEC61937_PCM;
            int eac3 = opaque->spec.format == AUDIO_EAC3 || opaque->spec.format == AUDIO_EAC3_JOC;
            int packed_ok;
            char line[640];
            if (opaque->encoded_stop) {
                SDL_Delay(10);
            } else if (request >= 4) {
                payload = (int)buffer[0] | ((int)buffer[1] << 8) |
                          ((int)buffer[2] << 16) | ((int)buffer[3] << 24);
            }
            packed_ok = payload > 0 && payload <= request - 4 &&
                (iec ? iec_burst_is_one_period(buffer + 4, payload)
                     : (!eac3 || eac3_write_is_one_syncframe(buffer + 4, payload)));
            if (opaque->encoded_stop) {
                /* already waiting */
            } else if (packed_ok) {
                int pc = 0;
                int pd = 0;
                int written;
                int sample;
                int64_t t0;
                int write_ms;
                if (opaque->play_ms == 0)
                    opaque->play_ms = aout_mono_ms();
                if (iec && payload >= 8) {
                    pc = (int)buffer[8] | ((int)buffer[9] << 8);
                    pd = (int)buffer[10] | ((int)buffer[11] << 8);
                }
                opaque->write_n++;
                sample = opaque->write_n <= 4 || (opaque->write_n % 32) == 0;
                {
                    int64_t begin_us = fs_diag_mono_us();
                    long long ss_gap = -1;
                    long long since_end = -1;
                    if (opaque->have_gap) {
                        ss_gap = (long long) ((begin_us - opaque->gap_begin_us) / 1000);
                        since_end = (long long) ((begin_us - opaque->gap_end_us) / 1000);
                    }
                    if (sample) {
                        snprintf(line, sizeof(line),
                                 "audio write begin n=%d elapsed=%lld epoch=write prev_log_ms=%d bytes=%d "
                                 "ss_gap_ms=%lld since_end_ms=%lld t=%lld",
                                 opaque->write_n,
                                 (long long) (aout_mono_ms() - opaque->play_ms),
                                 opaque->prev_log_ms, payload,
                                 ss_gap, since_end,
                                 (long long) fs_diag_session_ms(begin_us));
                        opaque->prev_log_ms = aout_log_timed(env, line);
                    }
                    t0 = aout_mono_ms();
                    written = aout_write_encoded(env, aout, buffer + 4, payload);
                    if (written == payload && opaque->spec.format == AUDIO_AC3) {
                        int head_valid = 0;
                        int ts_valid = 0;
                        int cap_valid = 0;
                        int64_t ts_frame = 0;
                        int64_t ts_nano = 0;
                        int head = SDL_Android_AudioTrack_playback_head(env, atrack, &head_valid);
                        int cap_frames = SDL_Android_AudioTrack_buffer_frames(env, atrack, &cap_valid);
                        SDL_Android_AudioTrack_timestamp(env, atrack, &ts_frame, &ts_nano, &ts_valid);
                        fs_ac3_client_on_written(userdata, payload, head, head_valid,
                                                 ts_frame, ts_nano, SDL_Android_nano_time(env),
                                                 ts_valid, cap_frames, cap_valid);
                    }
                    write_ms = (int) (aout_mono_ms() - t0);
                    opaque->gap_begin_us = begin_us;
                    opaque->gap_end_us = fs_diag_mono_us();
                    opaque->have_gap = 1;
                    if (written > 0) {
                        opaque->cum_bytes += written;
                        fs_diag_mark_write(begin_us, opaque->gap_end_us, written);
                        if (written == payload &&
                            (opaque->spec.format == AUDIO_IEC61937 ||
                             opaque->spec.format == AUDIO_IEC61937_PCM)) {
                            int iec_ts_valid = 0;
                            int64_t iec_ts_frame = 0;
                            int64_t iec_ts_nano = 0;
                            if (fs_iec_timestamp_allowed(opaque->spec.format))
                                SDL_Android_AudioTrack_timestamp(env, atrack, &iec_ts_frame, &iec_ts_nano, &iec_ts_valid);
                            fs_iec_audclk_on_write(userdata, written, iec_ts_frame, iec_ts_nano,
                                                   SDL_Android_nano_time(env), iec_ts_valid);
                        }
                    }
                }
                if (sample || written < 0 || written != payload) {
                    char hex[48];
                    hex[0] = 0;
                    if (opaque->write_n == 1 && payload >= 16) {
                        int b;
                        int used = 0;
                        for (b = 0; b < 16 && used + 3 < (int) sizeof(hex); b++)
                            used += snprintf(hex + used, sizeof(hex) - used, "%02x", buffer[4 + b]);
                    }
                    {
                        char clocks[96];
                        int sampq = -1;
                        int aud_valid = 0;
                        int vid_valid = 0;
                        long long aud_ms = 0;
                        long long vid_ms = 0;
                        fs_diag_read_player_clock(userdata, &sampq, &aud_valid, &aud_ms,
                                                  &vid_valid, &vid_ms);
                        fs_diag_format_clocks(clocks, (int) sizeof(clocks),
                                              aud_valid, aud_ms, vid_valid, vid_ms);
                        snprintf(line, sizeof(line),
                                 "audio write n=%d elapsed=%lld epoch=write write_ms=%d prev_log_ms=%d "
                                 "bytes=%d pc=%d pd=%d result=%d/%d cum_bursts=%d cum_bytes=%lld "
                                 "t=%lld sampq_frames=%d %s hex=%s",
                                 opaque->write_n,
                                 (long long) (aout_mono_ms() - opaque->play_ms),
                                 write_ms, opaque->prev_log_ms, payload, pc, pd,
                                 written, payload, opaque->write_n,
                                 (long long) opaque->cum_bytes,
                                 (long long) fs_diag_session_ms(fs_diag_mono_us()),
                                 sampq, clocks, hex);
                    }
                    opaque->prev_log_ms = aout_log_timed(env, line);
                }
                if (written < 0) {
                    ALOGW("AudioTrack: encoded write failed %d/%d; stopping writes", written, payload);
                    opaque->encoded_stop = 1;
                } else if (written != payload) {
                    ALOGW("AudioTrack: encoded write %d/%d", written, payload);
                }
            } else if (payload > 0) {
                snprintf(line, sizeof(line), "audio write refused bytes=%d", payload);
                SDL_Android_AudioTrack_log(env, line);
                ALOGW("AudioTrack: refuse mispacked encoded buffer %d", payload);
            } else {
                if (opaque->empty_logs < 4) {
                    opaque->empty_logs++;
                    SDL_Android_AudioTrack_log(env, "audio callback bytes=0");
                }
                SDL_Delay(5);
            }
        } else if (opaque->abort_request || opaque->pause_on || opaque->need_flush) {
            if (opaque->pcm_seek_pending && opaque->pause_on && !opaque->pcm_seek_pause_logged) {
                char line[160];
                opaque->pcm_seek_pause_logged = 1;
                snprintf(line, sizeof(line),
                         "audio write after-seek deferred gen=%d epoch=%d reason=paused",
                         opaque->generation, opaque->pcm_seek_epoch);
                SDL_Android_AudioTrack_log(env, line);
            }
        } else {
            int written;
            /* Publish the flag before the write so close can stop() this
             * call if it blocks. Re-check pause and abort after the flag is
             * visible, and do not write into a paused track. */
            opaque->pcm_blocking_write = 1;
            __sync_synchronize();
            if (opaque->abort_request || opaque->pause_on || opaque->need_flush) {
                opaque->pcm_blocking_write = 0;
            } else {
                written = SDL_Android_AudioTrack_write(env, atrack, buffer, copy_size);
                opaque->pcm_blocking_write = 0;
                aout_note_pcm_write(env, opaque, written, copy_size);
                if (written != copy_size) {
                    char line[160];
                    snprintf(line, sizeof(line), "audio write short gen=%d bytes=%d written=%d",
                             opaque->generation, copy_size, written);
                    SDL_Android_AudioTrack_log(env, line);
                    ALOGW("AudioTrack: not all data copied %d/%d", (int)written, (int)copy_size);
                }
            }
        }

        // TODO: 1 if callback return -1 or 0
    }

    aout_shutdown_track(env, aout);
    return 0;
}

static void aout_finish_thread(SDL_Aout *aout)
{
    SDL_Aout_Opaque *opaque = aout->opaque;
    SDL_LockMutex(opaque->wakeup_mutex);
    opaque->thread_done = 1;
    SDL_UnlockMutex(opaque->wakeup_mutex);
}

static int aout_thread(void *arg)
{
    SDL_Aout *aout = arg;
    JNIEnv *env = NULL;
    int ret = -1;

    if (JNI_OK != SDL_JNI_SetupThreadEnv(&env)) {
        ALOGE("aout_thread: SDL_AndroidJni_SetupEnv: failed");
    } else {
        ret = aout_thread_n(env, aout);
    }
    aout_finish_thread(aout);
    return ret;
}

static int aout_open_audio_n(JNIEnv *env, SDL_Aout *aout, const SDL_AudioSpec *desired, SDL_AudioSpec *obtained)
{
    assert(desired);
    SDL_Aout_Opaque *opaque = aout->opaque;

    opaque->spec = *desired;
    opaque->encoded = aout_format_is_encoded(desired->format);
    opaque->atrack = SDL_Android_AudioTrack_new_from_sdl_spec(env, desired);
    if (!opaque->atrack) {
        ALOGE("aout_open_audio_n: failed to new AudioTrcak() format=0x%x", desired->format);
        return -1;
    }

    opaque->buffer_size = SDL_Android_AudioTrack_get_min_buffer_size(opaque->atrack);
    if (opaque->encoded && opaque->buffer_size < 65536)
        opaque->buffer_size = 65536;
    if (opaque->buffer_size <= 0) {
        ALOGE("aout_open_audio_n: failed to getMinBufferSize()");
        SDL_Android_AudioTrack_free(env, opaque->atrack);
        opaque->atrack = NULL;
        return -1;
    }

    opaque->buffer = malloc(opaque->buffer_size);
    if (!opaque->buffer) {
        ALOGE("aout_open_audio_n: failed to allocate buffer");
        SDL_Android_AudioTrack_free(env, opaque->atrack);
        opaque->atrack = NULL;
        return -1;
    }

    if (obtained) {
        SDL_Android_AudioTrack_get_target_spec(opaque->atrack, obtained);
        SDLTRACE("audio target format fmt:0x%x, channel:0x%x", (int)obtained->format, (int)obtained->channels);
    }

    opaque->audio_session_id = SDL_Android_AudioTrack_getAudioSessionId(env, opaque->atrack);
    ALOGI("audio_session_id = %d\n", opaque->audio_session_id);

    opaque->pause_on = 1;
    opaque->abort_request = 0;
    opaque->pcm_blocking_write = 0;
    opaque->encoded_write_in_progress = 0;
    opaque->thread_done = 0;
    opaque->abandoned = 0;
    opaque->track_released = 0;
    opaque->pcm_seek_epoch = 0;
    opaque->pcm_seek_pending = 0;
    opaque->pcm_seek_pause_logged = 0;
    opaque->device_pause = 0;
    opaque->device_held = 0;
    opaque->played = 0;
    opaque->generation = ++g_audio_generation;
    opaque->release_phase = 0;
    opaque->write_offset = 0;
    opaque->callback_noted = 0;
    opaque->encoded_stop = 0;
    opaque->empty_logs = 0;
    opaque->write_n = 0;
    opaque->prev_log_ms = 0;
    opaque->play_ms = 0;
    opaque->cum_bytes = 0;
    opaque->audio_tid = SDL_CreateThreadEx(&opaque->_audio_tid, aout_thread, aout, "ff_aout_android");
    if (!opaque->audio_tid) {
        ALOGE("aout_open_audio_n: failed to create audio thread");
        SDL_Android_AudioTrack_free(env, opaque->atrack);
        opaque->atrack = NULL;
        return -1;
    }

    return 0;
}

static int aout_open_audio(SDL_Aout *aout, const SDL_AudioSpec *desired, SDL_AudioSpec *obtained)
{
    // SDL_Aout_Opaque *opaque = aout->opaque;
    JNIEnv *env = NULL;
    if (JNI_OK != SDL_JNI_SetupThreadEnv(&env)) {
        ALOGE("aout_open_audio: AttachCurrentThread: failed");
        return -1;
    }

    return aout_open_audio_n(env, aout, desired, obtained);
}

static void aout_pause_audio(SDL_Aout *aout, int pause_on)
{
    SDL_Aout_Opaque *opaque = aout->opaque;

    SDL_LockMutex(opaque->wakeup_mutex);
    SDLTRACE("aout_pause_audio(%d)", pause_on);
    opaque->pause_on = pause_on;
    SDL_CondSignal(opaque->wakeup_cond);
    SDL_UnlockMutex(opaque->wakeup_mutex);
}

static void aout_set_device_pause(SDL_Aout *aout, int pause_on)
{
    SDL_Aout_Opaque *opaque = aout->opaque;
    SDL_LockMutex(opaque->wakeup_mutex);
    opaque->device_pause = pause_on;
    SDL_CondSignal(opaque->wakeup_cond);
    SDL_UnlockMutex(opaque->wakeup_mutex);
}

static void aout_flush_audio(SDL_Aout *aout)
{
    SDL_Aout_Opaque *opaque = aout->opaque;
    SDL_LockMutex(opaque->wakeup_mutex);
    SDLTRACE("aout_flush_audio()");
    opaque->need_flush = 1;
    SDL_CondSignal(opaque->wakeup_cond);
    SDL_UnlockMutex(opaque->wakeup_mutex);
}

static void aout_set_volume(SDL_Aout *aout, float left_volume, float right_volume)
{
    SDL_Aout_Opaque *opaque = aout->opaque;
    SDL_LockMutex(opaque->wakeup_mutex);
    SDLTRACE("aout_flush_audio()");
    opaque->left_volume = left_volume;
    opaque->right_volume = right_volume;
    opaque->need_set_volume = 1;
    SDL_CondSignal(opaque->wakeup_cond);
    SDL_UnlockMutex(opaque->wakeup_mutex);
}

static int aout_wait_done(SDL_Aout_Opaque *opaque, int ms)
{
    int left = ms;
    while (left > 0) {
        if (opaque->thread_done)
            return 1;
        SDL_Delay(20);
        left -= 20;
    }
    return opaque->thread_done ? 1 : 0;
}

/* Close only. Stop, then release, a write the audio thread did not leave.
 * Seek never calls this. */
static void aout_unstick_track(SDL_Aout *aout)
{
    SDL_Aout_Opaque *opaque = aout->opaque;
    SDL_Android_AudioTrack *track = NULL;
    JNIEnv *env = NULL;
    int stuck = opaque->encoded_write_in_progress || opaque->pcm_blocking_write || !opaque->thread_done;
    if (!fs_close_unstick(1, stuck))
        return;
    if (JNI_OK != SDL_JNI_SetupThreadEnv(&env) || !env)
        return;
    SDL_LockMutex(opaque->wakeup_mutex);
    track = opaque->track_released ? NULL : opaque->atrack;
    SDL_UnlockMutex(opaque->wakeup_mutex);
    if (track)
        SDL_Android_AudioTrack_stop(env, track);
    if (aout_wait_done(opaque, 400))
        return;
    SDL_LockMutex(opaque->wakeup_mutex);
    track = (!opaque->thread_done && !opaque->track_released) ? opaque->atrack : NULL;
    if (track) {
        opaque->atrack = NULL;
        opaque->track_released = 1;
    }
    SDL_UnlockMutex(opaque->wakeup_mutex);
    if (!track)
        return;
    fs_passthrough_log_line("audio close unstick release");
    SDL_Android_AudioTrack_free(env, track);
}

static void aout_close_audio(SDL_Aout *aout)
{
    SDL_Aout_Opaque *opaque = aout->opaque;
    int pcm = 0;
    int join = 0;
    SDL_Android_AudioTrack *track = NULL;
    if (NULL == opaque->audio_tid) {
        return;
    }
    SDL_LockMutex(opaque->wakeup_mutex);
    opaque->abort_request = true;
    pcm = !opaque->encoded;
    track = opaque->atrack;
    SDL_CondSignal(opaque->wakeup_cond);
    SDL_UnlockMutex(opaque->wakeup_mutex);

    /* Unblock a PCM write that is already inside the HAL. Encoded writes
     * get the same stop only if they are still stuck after the short wait. */
    __sync_synchronize();
    if (pcm && track && opaque->pcm_blocking_write) {
        JNIEnv *env = NULL;
        if (JNI_OK == SDL_JNI_SetupThreadEnv(&env) && env)
            SDL_Android_AudioTrack_stop(env, track);
    }

    if (!aout_wait_done(opaque, fs_close_wait_ms()))
        aout_unstick_track(aout);

    SDL_LockMutex(opaque->wakeup_mutex);
    if (opaque->thread_done)
        join = 1;
    else
        opaque->abandoned = 1;
    SDL_UnlockMutex(opaque->wakeup_mutex);
    if (join) {
        SDL_WaitThread(opaque->audio_tid, NULL);
    } else {
        fs_passthrough_log_line("audio close detach stuck thread");
        SDL_DetachThread(opaque->audio_tid);
    }
    opaque->audio_tid = NULL;
}

static int aout_get_audio_session_id(SDL_Aout *aout)
{
    SDL_Aout_Opaque *opaque = aout->opaque;

    return opaque->audio_session_id;
}

static void aout_free_l(SDL_Aout *aout)
{
    if (!aout)
        return;

    aout_close_audio(aout);

    SDL_Aout_Opaque *opaque = aout->opaque;
    /* The audio thread is still inside a write. It owns this object. */
    if (opaque && opaque->abandoned)
        return;
    if (opaque) {
        free(opaque->buffer);
        opaque->buffer = NULL;
        opaque->buffer_size = 0;

        SDL_DestroyCond(opaque->wakeup_cond);
        SDL_DestroyMutex(opaque->wakeup_mutex);
    }

    SDL_Aout_FreeInternal(aout);
}

static void func_set_playback_rate(SDL_Aout *aout, float speed)
{
    if (!aout)
        return;

    SDL_Aout_Opaque *opaque = aout->opaque;
    SDL_LockMutex(opaque->wakeup_mutex);
    SDLTRACE("%s %f", __func__, (double)speed);
    opaque->speed = speed;
    opaque->speed_changed = 1;
    SDL_CondSignal(opaque->wakeup_cond);
    SDL_UnlockMutex(opaque->wakeup_mutex);
}

SDL_Aout *SDL_AoutAndroid_CreateForAudioTrack()
{
    SDL_Aout *aout = SDL_Aout_CreateInternal(sizeof(SDL_Aout_Opaque));
    if (!aout)
        return NULL;

    SDL_Aout_Opaque *opaque = aout->opaque;
    opaque->wakeup_cond  = SDL_CreateCond();
    opaque->wakeup_mutex = SDL_CreateMutex();
    opaque->speed        = 1.0f;

    aout->opaque_class = &g_audiotrack_class;
    aout->free_l       = aout_free_l;
    aout->open_audio   = aout_open_audio;
    aout->pause_audio  = aout_pause_audio;
    aout->set_device_pause = aout_set_device_pause;
    aout->flush_audio  = aout_flush_audio;
    aout->set_volume   = aout_set_volume;
    aout->close_audio  = aout_close_audio;
    aout->func_get_audio_session_id = aout_get_audio_session_id;
    aout->func_set_playback_rate    = func_set_playback_rate;

    return aout;
}

bool SDL_AoutAndroid_IsObjectOfAudioTrack(SDL_Aout *aout)
{
    if (aout)
        return false;

    return aout->opaque_class == &g_audiotrack_class;
}

void SDL_Init_AoutAndroid(JNIEnv *env)
{

}
