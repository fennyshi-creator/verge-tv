#include "fs_diag.h"

#include <stdio.h>
#include <string.h>
#include <time.h>

static int g_session;
static int64_t g_epoch_us;
static int64_t g_write_end_us;
static int g_have_write;
static int64_t g_accepted;

int64_t fs_diag_mono_us(void)
{
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t) ts.tv_sec * 1000000LL + ts.tv_nsec / 1000;
}

void fs_diag_publish_session(int session, int64_t epoch_us)
{
    __atomic_store_n(&g_session, session, __ATOMIC_RELAXED);
    __atomic_store_n(&g_epoch_us, epoch_us, __ATOMIC_RELAXED);
    __atomic_store_n(&g_have_write, 0, __ATOMIC_RELAXED);
    __atomic_store_n(&g_write_end_us, 0, __ATOMIC_RELAXED);
    __atomic_store_n(&g_accepted, 0, __ATOMIC_RELAXED);
}

int fs_diag_session_id(void)
{
    return __atomic_load_n(&g_session, __ATOMIC_RELAXED);
}

int64_t fs_diag_session_ms(int64_t now_us)
{
    int64_t epoch = __atomic_load_n(&g_epoch_us, __ATOMIC_RELAXED);
    if (epoch <= 0 || now_us < epoch)
        return 0;
    return (now_us - epoch) / 1000;
}

int fs_reader_should_sleep(int infinite_buffer, int seek_req, int paused,
                           int iec_on, int has_audio, int audio_finished,
                           int audio_packets, int64_t queue_sum,
                           int64_t soft_cap, int64_t hard_cap,
                           int enough_packets)
{
    int playing_low;
    if (infinite_buffer >= 1 || seek_req)
        return 0;
    if (enough_packets)
        return 1;
    if (hard_cap > 0 && queue_sum >= hard_cap)
        return 1;
    if (queue_sum <= soft_cap)
        return 0;
    playing_low = iec_on && !paused && has_audio && !audio_finished
        && audio_packets < FS_READER_AUDIO_LOW_PACKETS;
    return playing_low ? 0 : 1;
}

const char *fs_diag_sleep_reason(int reason)
{
    switch (reason) {
    case FS_DIAG_SLEEP_BYTE_CAP: return "byte_cap";
    case FS_DIAG_SLEEP_ENOUGH: return "enough_packets";
    case FS_DIAG_SLEEP_BOTH: return "both";
    case FS_DIAG_SLEEP_HARD_CAP: return "hard_cap";
    default: return "none";
    }
}

int fs_video_nan_hold_would(int audio_master, int stepping, int seek_step,
                            int accurate_seek, int clock_nan, int audio_finished,
                            double frame_pts, int64_t audio_duration)
{
    if (seek_step || stepping || !audio_master || accurate_seek)
        return 0;
    if (clock_nan && !audio_finished && frame_pts > 0.0 && audio_duration > 1)
        return 1;
    return 0;
}

int fs_video_wait_for_audio_clock(int audio_master, int stepping, int seek_step,
                                  int accurate_seek, int clock_nan, int iec_on,
                                  int audio_finished, double frame_pts,
                                  int64_t audio_duration)
{
    if (iec_on)
        return 0;
    return fs_video_nan_hold_would(audio_master, stepping, seek_step, accurate_seek,
                                   clock_nan, audio_finished, frame_pts, audio_duration);
}

int fs_iec_clock_anchor_ms(double burst_end_sec, int pts_valid, int written,
                           int full_bytes, double latency_sec, long long *out_ms)
{
    double played;
    if (!out_ms || !pts_valid || full_bytes <= 0 || written != full_bytes)
        return 0;
    if (latency_sec < 0.0 || latency_sec > 1.0)
        latency_sec = 0.0;
    played = burst_end_sec - latency_sec;
    *out_ms = (long long) (played * 1000.0);
    return 1;
}

int fs_iec_underrun_hold(int64_t age_us, double latency_sec)
{
    double limit;
    if (age_us < 0)
        return 0;
    if (latency_sec < 0.0)
        latency_sec = 0.0;
    /* Configured latency plus the 98304-byte track (4 bursts at 768000 B/s). */
    limit = latency_sec + (4.0 * 24576.0 / 768000.0);
    return age_us > (int64_t) (limit * 1000000.0);
}

int fs_iec_audclk_paused(int user_paused, int have_write, int64_t age_us, double latency_sec)
{
    if (user_paused)
        return 1;
    if (!have_write)
        return 0;
    return fs_iec_underrun_hold(age_us, latency_sec);
}

int fs_clock_serial_matches(int clock_serial, int queue_serial)
{
    return clock_serial == queue_serial;
}

void fs_diag_sleep_reset(FsDiagSleep *st)
{
    if (st)
        memset(st, 0, sizeof(*st));
}

static int fs_diag_sleep_format(const FsDiagSleep *st, int session, int64_t epoch_us,
                                int64_t end_us, char *out, int out_cap)
{
    int64_t span_us;
    int64_t t_ms;
    if (!st || !out || out_cap < 32 || !st->active)
        return 0;
    span_us = end_us - st->start_us;
    if (span_us < FS_DIAG_SLEEP_MIN_US)
        return 0;
    t_ms = epoch_us > 0 && end_us >= epoch_us ? (end_us - epoch_us) / 1000 : 0;
    snprintf(out, (size_t) out_cap,
             "diag session=%d t=%lld epoch=session read_sleep reason=%s span_ms=%lld sleeps=%d "
             "audio_pkts=%d->%d audio_qbytes=%d->%d video_pkts=%d->%d video_qbytes=%d->%d "
             "cap=%d sub_added=%d sum=%lld soft=%lld hard=%lld",
             session, (long long) t_ms, fs_diag_sleep_reason(st->reason),
             (long long) (span_us / 1000), st->sleeps,
             st->audio_pkts0, st->audio_pkts1, st->audio_qbytes0, st->audio_qbytes1,
             st->video_pkts0, st->video_pkts1, st->video_qbytes0, st->video_qbytes1,
             st->cap, st->sub_added,
             (long long) st->queue_sum, (long long) st->soft_cap, (long long) st->hard_cap);
    return 1;
}

static void fs_diag_sleep_begin(FsDiagSleep *st, int reason, int64_t now_us,
                                int audio_pkts, int audio_qbytes,
                                int video_pkts, int video_qbytes,
                                int cap, int sub_added,
                                int64_t queue_sum, int64_t soft_cap, int64_t hard_cap)
{
    memset(st, 0, sizeof(*st));
    st->active = 1;
    st->reason = reason;
    st->start_us = now_us;
    st->last_us = now_us;
    st->sleeps = 1;
    st->audio_pkts0 = st->audio_pkts1 = audio_pkts;
    st->audio_qbytes0 = st->audio_qbytes1 = audio_qbytes;
    st->video_pkts0 = st->video_pkts1 = video_pkts;
    st->video_qbytes0 = st->video_qbytes1 = video_qbytes;
    st->cap = cap;
    st->sub_added = sub_added;
    st->queue_sum = queue_sum;
    st->soft_cap = soft_cap;
    st->hard_cap = hard_cap;
}

int fs_diag_sleep_observe(FsDiagSleep *st, int session, int64_t epoch_us,
                          int reason, int64_t now_us,
                          int audio_pkts, int audio_qbytes,
                          int video_pkts, int video_qbytes,
                          int cap, int sub_added,
                          int64_t queue_sum, int64_t soft_cap, int64_t hard_cap,
                          char *out, int out_cap)
{
    int produced = 0;
    if (!st)
        return 0;
    if (st->active && (st->reason != reason || now_us - st->last_us > FS_DIAG_SLEEP_CHAIN_GAP_US)) {
        produced = fs_diag_sleep_format(st, session, epoch_us, st->last_us, out, out_cap);
        st->active = 0;
    }
    if (!st->active) {
        fs_diag_sleep_begin(st, reason, now_us, audio_pkts, audio_qbytes,
                            video_pkts, video_qbytes, cap, sub_added,
                            queue_sum, soft_cap, hard_cap);
        return produced;
    }
    st->sleeps++;
    st->last_us = now_us;
    st->audio_pkts1 = audio_pkts;
    st->audio_qbytes1 = audio_qbytes;
    st->video_pkts1 = video_pkts;
    st->video_qbytes1 = video_qbytes;
    st->cap = cap;
    st->sub_added = sub_added;
    st->queue_sum = queue_sum;
    st->soft_cap = soft_cap;
    st->hard_cap = hard_cap;
    return produced;
}

int fs_diag_sleep_close(FsDiagSleep *st, int session, int64_t epoch_us,
                        int64_t now_us, char *out, int out_cap)
{
    int produced;
    if (!st || !st->active)
        return 0;
    produced = fs_diag_sleep_format(st, session, epoch_us, now_us, out, out_cap);
    st->active = 0;
    return produced;
}

static void add64(int64_t *p, int64_t n)
{
    __atomic_fetch_add(p, n, __ATOMIC_RELAXED);
}

static int64_t load64(const int64_t *p)
{
    return __atomic_load_n(p, __ATOMIC_RELAXED);
}

static void add32(int *p, int n)
{
    __atomic_fetch_add(p, n, __ATOMIC_RELAXED);
}

static int load32(const int *p)
{
    return __atomic_load_n(p, __ATOMIC_RELAXED);
}

void fs_diag_audio_reset(FsDiagAudio *a)
{
    if (a)
        memset(a, 0, sizeof(*a));
}

void fs_diag_audio_add_raw(FsDiagAudio *a, int bytes)
{
    if (!a || bytes <= 0)
        return;
    add64(&a->dequeued_bytes, bytes);
    add64(&a->raw_media_bytes, bytes);
}

void fs_diag_audio_add_seek_packet(FsDiagAudio *a, int bytes)
{
    if (!a)
        return;
    add32(&a->skip_seek_packets, 1);
    if (bytes > 0) {
        add64(&a->dequeued_bytes, bytes);
        add64(&a->skip_seek_bytes, bytes);
    }
}

void fs_diag_audio_add_seek_flush(FsDiagAudio *a, int packets, int media_bytes)
{
    if (!a)
        return;
    add32(&a->seek_resets, 1);
    if (packets > 0)
        add32(&a->skip_seek_flush_packets, packets);
    if (media_bytes > 0)
        add64(&a->skip_seek_flush_bytes, media_bytes);
}

void fs_diag_audio_add_invalid(FsDiagAudio *a, int bytes)
{
    if (!a || bytes <= 0)
        return;
    add64(&a->skip_invalid_bytes, bytes);
}

void fs_diag_audio_add_eof(FsDiagAudio *a)
{
    if (!a)
        return;
    add32(&a->skip_eof_packets, 1);
}

void fs_diag_audio_add_frame(FsDiagAudio *a, int dependent, int samples)
{
    if (!a)
        return;
    if (dependent) {
        add32(&a->dependent_frames, 1);
        if (samples > 0)
            add32(&a->dependent_samples, samples);
    } else {
        add32(&a->independent_frames, 1);
        if (samples > 0)
            add32(&a->independent_samples, samples);
    }
}

void fs_diag_audio_add_orphan(FsDiagAudio *a, int bytes)
{
    if (!a)
        return;
    add32(&a->skip_orphan_frames, 1);
    if (bytes > 0)
        add64(&a->skip_orphan_bytes, bytes);
}

void fs_diag_audio_add_burst(FsDiagAudio *a, int pd)
{
    if (!a)
        return;
    add32(&a->bursts, 1);
    if (pd > 0)
        add64(&a->pd_total, pd);
}

void fs_diag_audio_set_residual(FsDiagAudio *a, int gather, int period)
{
    if (!a)
        return;
    __atomic_store_n(&a->residual_gather, gather, __ATOMIC_RELAXED);
    __atomic_store_n(&a->residual_period, period, __ATOMIC_RELAXED);
}

void fs_diag_audio_set_pts(FsDiagAudio *a, int64_t pts, int valid, int serial)
{
    if (!a)
        return;
    __atomic_store_n(&a->last_pts, pts, __ATOMIC_RELAXED);
    __atomic_store_n(&a->last_pts_valid, valid ? 1 : 0, __ATOMIC_RELAXED);
    __atomic_store_n(&a->last_serial, serial, __ATOMIC_RELAXED);
}

void fs_diag_audio_add_accepted(FsDiagAudio *a, int64_t bytes)
{
    if (!a || bytes <= 0)
        return;
    add64(&a->accepted_write_bytes, bytes);
}

void fs_diag_audio_set_accepted(FsDiagAudio *a, int64_t bytes)
{
    if (!a)
        return;
    __atomic_store_n(&a->accepted_write_bytes, bytes, __ATOMIC_RELAXED);
}

int fs_diag_audio_format(const FsDiagAudio *a, char *out, int out_cap,
                         int session, int64_t t_ms, const char *kind)
{
    if (!a || !out || out_cap < 64)
        return 0;
    if (!kind)
        kind = "sec";
    if (load32(&a->last_pts_valid)) {
        snprintf(out, (size_t) out_cap,
                 "diag session=%d t=%lld epoch=session kind=%s "
                 "dequeued_bytes=%lld raw_media_bytes=%lld "
                 "ind_frames=%d ind_samples=%d dep_frames=%d dep_samples=%d "
                 "residual_gather=%d residual_period=%d "
                 "skip_seek_packets=%d skip_seek_bytes=%lld "
                 "skip_seek_flush_packets=%d skip_seek_flush_bytes=%lld seek_resets=%d "
                 "skip_invalid_bytes=%lld skip_eof_packets=%d "
                 "skip_orphan_frames=%d skip_orphan_bytes=%lld "
                 "bursts=%d pd_total=%lld accepted_write_bytes=%lld "
                 "last_pts=%lld last_serial=%d",
                 session, (long long) t_ms, kind,
                 (long long) load64(&a->dequeued_bytes),
                 (long long) load64(&a->raw_media_bytes),
                 load32(&a->independent_frames), load32(&a->independent_samples),
                 load32(&a->dependent_frames), load32(&a->dependent_samples),
                 load32(&a->residual_gather), load32(&a->residual_period),
                 load32(&a->skip_seek_packets), (long long) load64(&a->skip_seek_bytes),
                 load32(&a->skip_seek_flush_packets),
                 (long long) load64(&a->skip_seek_flush_bytes),
                 load32(&a->seek_resets),
                 (long long) load64(&a->skip_invalid_bytes), load32(&a->skip_eof_packets),
                 load32(&a->skip_orphan_frames), (long long) load64(&a->skip_orphan_bytes),
                 load32(&a->bursts), (long long) load64(&a->pd_total),
                 (long long) load64(&a->accepted_write_bytes),
                 (long long) load64(&a->last_pts), load32(&a->last_serial));
    } else {
        snprintf(out, (size_t) out_cap,
                 "diag session=%d t=%lld epoch=session kind=%s "
                 "dequeued_bytes=%lld raw_media_bytes=%lld "
                 "ind_frames=%d ind_samples=%d dep_frames=%d dep_samples=%d "
                 "residual_gather=%d residual_period=%d "
                 "skip_seek_packets=%d skip_seek_bytes=%lld "
                 "skip_seek_flush_packets=%d skip_seek_flush_bytes=%lld seek_resets=%d "
                 "skip_invalid_bytes=%lld skip_eof_packets=%d "
                 "skip_orphan_frames=%d skip_orphan_bytes=%lld "
                 "bursts=%d pd_total=%lld accepted_write_bytes=%lld "
                 "last_pts=nan last_serial=%d",
                 session, (long long) t_ms, kind,
                 (long long) load64(&a->dequeued_bytes),
                 (long long) load64(&a->raw_media_bytes),
                 load32(&a->independent_frames), load32(&a->independent_samples),
                 load32(&a->dependent_frames), load32(&a->dependent_samples),
                 load32(&a->residual_gather), load32(&a->residual_period),
                 load32(&a->skip_seek_packets), (long long) load64(&a->skip_seek_bytes),
                 load32(&a->skip_seek_flush_packets),
                 (long long) load64(&a->skip_seek_flush_bytes),
                 load32(&a->seek_resets),
                 (long long) load64(&a->skip_invalid_bytes), load32(&a->skip_eof_packets),
                 load32(&a->skip_orphan_frames), (long long) load64(&a->skip_orphan_bytes),
                 load32(&a->bursts), (long long) load64(&a->pd_total),
                 (long long) load64(&a->accepted_write_bytes),
                 load32(&a->last_serial));
    }
    return 1;
}

void fs_diag_mark_write(int64_t begin_us, int64_t end_us, int64_t accepted_bytes)
{
    (void) begin_us;
    __atomic_store_n(&g_write_end_us, end_us, __ATOMIC_RELAXED);
    __atomic_store_n(&g_have_write, 1, __ATOMIC_RELAXED);
    if (accepted_bytes > 0)
        __atomic_fetch_add(&g_accepted, accepted_bytes, __ATOMIC_RELAXED);
}

int64_t fs_diag_accepted_load(void)
{
    return __atomic_load_n(&g_accepted, __ATOMIC_RELAXED);
}

int fs_diag_since_write_end_ms(int64_t now_us, long long *out_ms)
{
    int64_t end;
    if (!out_ms)
        return 0;
    if (!__atomic_load_n(&g_have_write, __ATOMIC_RELAXED)) {
        *out_ms = -1;
        return 0;
    }
    end = __atomic_load_n(&g_write_end_us, __ATOMIC_RELAXED);
    *out_ms = (long long) ((now_us - end) / 1000);
    return 1;
}

int fs_diag_format_clocks(char *out, int out_cap,
                          int aud_valid, long long aud_ms,
                          int vid_valid, long long vid_ms)
{
    if (!out || out_cap < 16)
        return 0;
    if (aud_valid && vid_valid) {
        snprintf(out, (size_t) out_cap, "aud=%lld vid=%lld diff=%lld",
                 aud_ms, vid_ms, vid_ms - aud_ms);
    } else if (aud_valid) {
        snprintf(out, (size_t) out_cap, "aud=%lld vid=nan diff=nan", aud_ms);
    } else if (vid_valid) {
        snprintf(out, (size_t) out_cap, "aud=nan vid=%lld diff=nan", vid_ms);
    } else {
        snprintf(out, (size_t) out_cap, "aud=nan vid=nan diff=nan");
    }
    return 1;
}

int fs_diag_format_read(char *out, int out_cap, int session, int64_t t_ms,
                        int64_t dur_us, int result, const char *stream, int pkt_bytes)
{
    if (!out || out_cap < 32)
        return 0;
    if (dur_us < FS_DIAG_READ_SLOW_US)
        return 0;
    if (!stream)
        stream = "none";
    snprintf(out, (size_t) out_cap,
             "diag session=%d t=%lld epoch=session read_block ms=%lld result=%d stream=%s bytes=%d",
             session, (long long) t_ms, (long long) (dur_us / 1000), result, stream, pkt_bytes);
    return 1;
}
