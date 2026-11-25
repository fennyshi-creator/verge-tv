/*
 * Bounded passthrough diagnostics. Counters and formatting only.
 * Nothing here changes packet flow, clocks, or the AudioTrack.
 */
#ifndef FS_DIAG_H
#define FS_DIAG_H

#include <stdint.h>

/* A 10 ms sleep continues the same episode when the next one starts within this. */
#define FS_DIAG_SLEEP_CHAIN_GAP_US 40000
/* Episodes shorter than this are not logged. */
#define FS_DIAG_SLEEP_MIN_US 20000
#define FS_DIAG_READ_SLOW_US 50000

#define FS_DIAG_SLEEP_BYTE_CAP 1
#define FS_DIAG_SLEEP_ENOUGH   2
#define FS_DIAG_SLEEP_BOTH     3
#define FS_DIAG_SLEEP_HARD_CAP 4

/* About one quarter second of this E-AC-3 stream. Below this, IEC playback
 * does not sleep just because the soft queue cap is full. */
#define FS_READER_AUDIO_LOW_PACKETS 8

/*
 * 1 = the demuxer waits. Seeking never waits. A full soft cap still waits
 * for AAC, for pause, when audio is absent or finished, and when IEC already
 * has 8 audio packets. Fewer than 8 packets during IEC playback waits only
 * at the hard cap, or for the enough_packets branch.
 */
int fs_reader_should_sleep(int infinite_buffer, int seek_req, int paused,
                           int iec_on, int has_audio, int audio_finished,
                           int audio_packets, int64_t queue_sum,
                           int64_t soft_cap, int64_t hard_cap,
                           int enough_packets);

typedef struct FsDiagSleep {
    int active;
    int reason;
    int64_t start_us;
    int64_t last_us;
    int sleeps;
    int audio_pkts0, audio_qbytes0, video_pkts0, video_qbytes0;
    int audio_pkts1, audio_qbytes1, video_pkts1, video_qbytes1;
    int cap;
    int sub_added;
    int64_t queue_sum;
    int64_t soft_cap;
    int64_t hard_cap;
    int closed_reason;
    int closed_audio_end;
    int closed_span_ms;
} FsDiagSleep;

typedef struct FsDiagAudio {
    int64_t dequeued_bytes;
    int64_t raw_media_bytes;
    int independent_frames;
    int dependent_frames;
    int independent_samples;
    int dependent_samples;
    int residual_gather;
    int residual_period;
    int skip_seek_packets;
    int64_t skip_seek_bytes;
    int skip_seek_flush_packets;
    int64_t skip_seek_flush_bytes;
    int64_t skip_invalid_bytes;
    int skip_eof_packets;
    int skip_orphan_frames;
    int64_t skip_orphan_bytes;
    int bursts;
    int64_t pd_total;
    int64_t accepted_write_bytes;
    int64_t last_pts;
    int last_pts_valid;
    int last_serial;
    int seek_resets;
} FsDiagAudio;

int64_t fs_diag_mono_us(void);

void fs_diag_publish_session(int session, int64_t epoch_us);
int fs_diag_session_id(void);
int64_t fs_diag_session_ms(int64_t now_us);

void fs_diag_sleep_reset(FsDiagSleep *st);
/* 1 = out holds a closed episode that lasted at least 20 ms. */
int fs_diag_sleep_observe(FsDiagSleep *st, int session, int64_t epoch_us,
                          int reason, int64_t now_us,
                          int audio_pkts, int audio_qbytes,
                          int video_pkts, int video_qbytes,
                          int cap, int sub_added,
                          int64_t queue_sum, int64_t soft_cap, int64_t hard_cap,
                          char *out, int out_cap);

/* 1 = the refresh would pin the current picture. IEC playback must not. */
int fs_video_nan_hold_would(int audio_master, int stepping, int seek_step,
                            int accurate_seek, int clock_nan, int audio_finished,
                            double frame_pts, int64_t audio_duration);
int fs_video_wait_for_audio_clock(int audio_master, int stepping, int seek_step,
                                  int accurate_seek, int clock_nan, int iec_on,
                                  int audio_finished, double frame_pts,
                                  int64_t audio_duration);

/*
 * burst_end_sec is the content PTS at the end of the burst just removed
 * from the frame queue and accepted by AudioTrack (start + 1536/48000).
 * Frames still in that queue are later than this burst, so they are not
 * subtracted. latency_sec is the configured output latency, an estimate.
 * A short or failed write does not advance. Returns milliseconds.
 */
int fs_iec_clock_anchor_ms(double burst_end_sec, int pts_valid, int written,
                           int full_bytes, double latency_sec, long long *out_ms);
/* 1 = no accepted write for longer than the latency plus the 128 ms track buffer. */
int fs_iec_underrun_hold(int64_t age_us, double latency_sec);
/* User pause wins. Otherwise freeze only after a real gap with a published clock. */
int fs_iec_audclk_paused(int user_paused, int have_write, int64_t age_us, double latency_sec);
int fs_clock_serial_matches(int clock_serial, int queue_serial);
/* 1 = a full burst may publish the clock. Pause and a stale serial must not. */
int fs_iec_should_publish_clock(int user_paused, int written, int full_bytes,
                                int pts_valid, int burst_serial, int queue_serial);
/* eof when playback has finished and the queues are empty. starvation is a live gap. */
const char *fs_iec_drain_label(int audio_finished, int audio_packets, int pictq, int holding);
/* Routine soft-cap backpressure. Anomalies stay as their own lines. */
int fs_diag_sleep_is_routine(int reason, int audio_packets_end);
int64_t fs_diag_wall_s(void);
void fs_iec_audclk_on_write(void *player, int written,
                           int64_t ts_frame, int64_t ts_nano, int64_t now_nano, int ts_valid);
int fs_diag_sleep_close(FsDiagSleep *st, int session, int64_t epoch_us,
                        int64_t now_us, char *out, int out_cap);

const char *fs_diag_sleep_reason(int reason);

void fs_diag_audio_reset(FsDiagAudio *a);
void fs_diag_audio_add_raw(FsDiagAudio *a, int bytes);
void fs_diag_audio_add_seek_packet(FsDiagAudio *a, int bytes);
void fs_diag_audio_add_seek_flush(FsDiagAudio *a, int packets, int media_bytes);
void fs_diag_audio_add_invalid(FsDiagAudio *a, int bytes);
void fs_diag_audio_add_eof(FsDiagAudio *a);
void fs_diag_audio_add_frame(FsDiagAudio *a, int dependent, int samples);
void fs_diag_audio_add_orphan(FsDiagAudio *a, int bytes);
void fs_diag_audio_add_burst(FsDiagAudio *a, int pd);
void fs_diag_audio_set_residual(FsDiagAudio *a, int gather, int period);
void fs_diag_audio_set_pts(FsDiagAudio *a, int64_t pts, int valid, int serial);
void fs_diag_audio_add_accepted(FsDiagAudio *a, int64_t bytes);
void fs_diag_audio_set_accepted(FsDiagAudio *a, int64_t bytes);
int64_t fs_diag_accepted_load(void);
int fs_diag_audio_format(const FsDiagAudio *a, char *out, int out_cap,
                         int session, int64_t t_ms, const char *kind);

/* Sink write stamps. begin/end are fs_diag_mono_us values. */
void fs_diag_mark_write(int64_t begin_us, int64_t end_us, int64_t accepted_bytes);
/* 0 if no write has completed. */
int fs_diag_since_write_end_ms(int64_t now_us, long long *out_ms);

int fs_diag_format_clocks(char *out, int out_cap,
                          int aud_valid, long long aud_ms,
                          int vid_valid, long long vid_ms);
int fs_diag_format_read(char *out, int out_cap, int session, int64_t t_ms,
                        int64_t dur_us, int result, const char *stream, int pkt_bytes);

/*
 * Implemented by the player. Reads clocks and the audio frame-queue depth.
 * Does not take the queue mutex and does not log. sampq_frames is not the
 * number of bytes sitting in AudioTrack.
 */
void fs_diag_read_player_clock(void *player, int *sampq_frames,
                               int *aud_valid, long long *aud_ms,
                               int *vid_valid, long long *vid_ms);

#endif
