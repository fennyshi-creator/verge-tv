#ifndef FS_AAC_AC3_CLOCK_H
#define FS_AAC_AC3_CLOCK_H

#include <stdint.h>

/*
 * Audio master for client AC-3. It is never copied from the video clock.
 * 1536 compressed bytes are 32 ms at 48 kHz. While no playback position has
 * advanced, the clock stays at most one configured buffer behind the newest
 * accepted sample (a bound, not a measured occupancy). A head or timestamp
 * that keeps advancing takes over, and the handoff moves at most two AC-3
 * frames per accepted frame so the master does not jump.
 */
typedef struct FsAacAc3Clock {
    int64_t accepted;
    int cap_bytes;
    double anchor_pts;
    int anchored;
    int64_t published;
    int have_published;
    int slew_arm;
    int buffer_full;
    uint32_t head_raw;
    int head_raw_seen;
    int head_valid;
    int64_t head_unwrap;
    int head_advances;
    int head_unit; /* 0 unknown, 1 samples, 2 syncframes */
    int head_votes_samples;
    int head_votes_frames;
    int64_t accepted_at_advance;
    int invalid_reads;
    int ts_status; /* 0 none, 1 ok, 2 unavailable, 3 stale, 4 future, 5 implausible */
    int64_t ts_frame;
    int64_t ts_unwrap;
    int ts_seen;
    int64_t ts_nano;
    int ts_advances;
    int ts_unit;
    int ts_pending;
    int64_t cap_frames_raw;
    int cap_frames_valid;
    int calibrated; /* 0 none, 1 head, 2 timestamp */
    int64_t measured;
    char reason[32];
} FsAacAc3Clock;

typedef struct FsAacLedger {
    double origin;
    int set;
    int64_t in_count;
    int64_t out_count;
    int priming;
} FsAacLedger;

void fs_aac_ac3_clock_init(FsAacAc3Clock *clock, int buffer_bytes);
void fs_aac_ac3_clock_reset(FsAacAc3Clock *clock);
void fs_aac_ac3_clock_accept(FsAacAc3Clock *clock, int bytes, double content_end_pts);
void fs_aac_ac3_clock_note_blocked(FsAacAc3Clock *clock);
void fs_aac_ac3_clock_head(FsAacAc3Clock *clock, uint32_t head, int valid);
/* now_nano is System.nanoTime. valid is the boolean getTimestamp returned. */
void fs_aac_ac3_clock_timestamp(FsAacAc3Clock *clock, int64_t frame_position,
                                int64_t nano_time, int64_t now_nano, int valid);
/* Ignored until the position unit is known. frames is getBufferSizeInFrames. */
void fs_aac_ac3_clock_capacity_frames(FsAacAc3Clock *clock, int64_t frames, int valid);
double fs_aac_ac3_clock_presented(FsAacAc3Clock *clock, const char **source);
int64_t fs_aac_ac3_clock_queued_bytes(const FsAacAc3Clock *clock);

void fs_aac_ledger_reset(FsAacLedger *ledger);
void fs_aac_ledger_prime(FsAacLedger *ledger, int samples);
void fs_aac_ledger_push(FsAacLedger *ledger, int samples, double first_pts);
double fs_aac_ledger_next_packet_pts(const FsAacLedger *ledger);
void fs_aac_ledger_emit(FsAacLedger *ledger);

int fs_encoded_write_step(int *offset, int payload, int result, int *complete, int *blocked);
int fs_audio_release_step(int *phase, const char **name);

void fs_ac3_client_on_written(void *player, int bytes, int head, int head_valid,
                              int64_t ts_frame, int64_t ts_nano, int64_t now_nano, int ts_valid,
                              int cap_frames, int cap_valid);
void fs_ac3_client_hold(void *player);
void fs_ac3_note_display(void *player, double frame_pts);

#endif
