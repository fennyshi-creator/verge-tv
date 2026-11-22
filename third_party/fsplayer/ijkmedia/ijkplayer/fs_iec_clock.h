#ifndef FS_IEC_CLOCK_H
#define FS_IEC_CLOCK_H

#include <stdint.h>

/*
 * Master clock for native E-AC-3 on the 192 kHz IEC carrier.
 * This is not the AAC-to-AC-3 clock and must not call it.
 * 24576 carrier bytes are one 32 ms period (6144 frames at 192 kHz).
 * Until a timestamp's own rate stays near 192 kHz, the published time is
 * the content end minus the existing two-period latency. hw_buf is not read
 * here: it is configured capacity, not a delay.
 */
typedef struct FsIecClock {
    int64_t bursts;
    int64_t unwrap;
    int64_t ts_frame;
    int64_t ts_nano;
    int ts_seen;
    int ts_advances;
    int ts_status; /* 0 none, 1 ok, 2 unavailable, 3 stale, 4 future, 5 bad-unit, 6 reset */
    int calibrated;
    double rate_hz;
    double published;
    int have_published;
    char source[24];
} FsIecClock;

void fs_iec_clock_init(FsIecClock *clock);
void fs_iec_clock_reset(FsIecClock *clock);
/* paused or a non-finite content end leaves the last position unchanged. */
double fs_iec_clock_on_burst(FsIecClock *clock, double content_end, double latency_sec,
                             int paused, int64_t frame_position, int64_t nano_time,
                             int64_t now_nano, int ts_valid, const char **source);
double fs_iec_clock_hold(const FsIecClock *clock);
/* 1 only for the IEC61937 track (0x150D), never the PCM carrier 0x150E. */
int fs_iec_timestamp_allowed(unsigned format);

#endif
