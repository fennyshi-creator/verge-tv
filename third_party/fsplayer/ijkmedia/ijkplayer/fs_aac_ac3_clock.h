#ifndef FS_AAC_AC3_CLOCK_H
#define FS_AAC_AC3_CLOCK_H

#include <stdint.h>

/*
 * Presented time for client-encoded AC-3.
 * 1536 compressed bytes are one 32 ms frame at 48 kHz, 384 kbps.
 * One compressed byte counts as one 48 kHz sample in this timeline.
 * The playback head, when it moves, is preferred. Otherwise only bytes that
 * cannot fit in the known AudioTrack buffer are treated as consumed.
 */
typedef struct FsAacAc3Clock {
    int64_t accepted;
    int buffer_bytes;
    double anchor_pts;
    int anchored;
    int buffer_full;
    int head_unit; /* 0 unknown, 1 samples, 2 AC-3 frames, -1 unusable */
    int64_t head_accum;
    uint32_t head_last;
    int head_seen;
    int frames;
} FsAacAc3Clock;

void fs_aac_ac3_clock_init(FsAacAc3Clock *clock, int buffer_bytes);
void fs_aac_ac3_clock_reset(FsAacAc3Clock *clock);
void fs_aac_ac3_clock_accept(FsAacAc3Clock *clock, int bytes, double frame_end_pts);
void fs_aac_ac3_clock_head(FsAacAc3Clock *clock, uint32_t head, int valid);
void fs_aac_ac3_clock_note_blocked(FsAacAc3Clock *clock);
double fs_aac_ac3_clock_presented(const FsAacAc3Clock *clock, const char **source);
int64_t fs_aac_ac3_clock_queued_bytes(const FsAacAc3Clock *clock);

/* One non-blocking write result. complete is set when this payload is fully in. */
int fs_encoded_write_step(int *offset, int payload, int result, int *complete, int *blocked);

/* pause, flush, stop, release. A fifth call reports already finished. */
int fs_audio_release_step(int *phase, const char **name);

void fs_ac3_client_on_written(void *player, int bytes, int head, int head_valid);
void fs_ac3_client_hold(void *player);

#endif
