#include "fs_iec_clock.h"

#include <math.h>
#include <stdio.h>
#include <string.h>

enum {
    FS_IEC_RATE = 192000,
    FS_IEC_PERIOD_FRAMES = 6144,
    FS_IEC_NEED = 3
};

static const double FS_IEC_STEP = 0.064;
static const int64_t FS_IEC_STALE_NS = 2000000000LL;
static const int64_t FS_IEC_FUTURE_NS = 100000000LL;

static void set_source(FsIecClock *clock, const char *source)
{
    snprintf(clock->source, sizeof(clock->source), "%s", source);
}

int fs_iec_timestamp_allowed(unsigned format)
{
    return format == 0x150Du;
}

void fs_iec_clock_init(FsIecClock *clock)
{
    if (!clock)
        return;
    memset(clock, 0, sizeof(*clock));
    clock->published = 0.0 / 0.0;
    set_source(clock, "uncalibrated-iec");
}

void fs_iec_clock_reset(FsIecClock *clock)
{
    fs_iec_clock_init(clock);
}

double fs_iec_clock_hold(const FsIecClock *clock)
{
    if (!clock || !clock->have_published)
        return 0.0 / 0.0;
    return clock->published;
}

static void note_reset(FsIecClock *clock, int64_t frame, int64_t nano)
{
    clock->unwrap = frame;
    clock->ts_frame = frame;
    clock->ts_nano = nano;
    clock->ts_seen = 1;
    clock->ts_advances = 0;
    clock->calibrated = 0;
    clock->rate_hz = 0;
    clock->ts_status = 6;
}

static void observe(FsIecClock *clock, int64_t frame, int64_t nano, int64_t now, int valid)
{
    int64_t delta;
    int64_t delta_nano;
    double rate;
    if (!valid) {
        clock->ts_status = 2;
        return;
    }
    if (frame < 0 || nano <= 0 || now <= 0) {
        clock->ts_status = 5;
        return;
    }
    if (nano > now + FS_IEC_FUTURE_NS) {
        clock->ts_status = 4;
        return;
    }
    if (now > nano + FS_IEC_STALE_NS) {
        clock->ts_status = 3;
        return;
    }
    if (!clock->ts_seen) {
        clock->unwrap = frame;
        clock->ts_frame = frame;
        clock->ts_nano = nano;
        clock->ts_seen = 1;
        clock->ts_status = 1;
        return;
    }
    if (frame >= clock->ts_frame)
        delta = frame - clock->ts_frame;
    else if (clock->ts_frame <= 0xffffffffLL &&
             (uint32_t)frame - (uint32_t)clock->ts_frame <= (uint32_t)(FS_IEC_RATE * 2))
        delta = (uint32_t)frame - (uint32_t)clock->ts_frame;
    else {
        note_reset(clock, frame, nano);
        return;
    }
    if (delta > (int64_t)FS_IEC_RATE * 2) {
        note_reset(clock, frame, nano);
        return;
    }
    delta_nano = nano - clock->ts_nano;
    clock->unwrap += delta;
    clock->ts_frame = frame;
    clock->ts_nano = nano;
    clock->ts_status = 1;
    if (delta > 0 && delta_nano > 1000000LL && delta_nano < FS_IEC_STALE_NS) {
        rate = (double)delta * 1e9 / (double)delta_nano;
        /* ~192000/s, which is ~6144 carrier frames per 32 ms period. */
        if (rate >= 144000.0 && rate <= 240000.0) {
            clock->rate_hz = rate;
            if (clock->ts_advances < FS_IEC_NEED)
                clock->ts_advances++;
            if (clock->ts_advances >= FS_IEC_NEED)
                clock->calibrated = 1;
        } else {
            clock->ts_advances = 0;
            clock->rate_hz = rate;
            clock->ts_status = 5;
        }
    }
}

double fs_iec_clock_on_burst(FsIecClock *clock, double content_end, double latency_sec,
                             int paused, int64_t frame_position, int64_t nano_time,
                             int64_t now_nano, int ts_valid, const char **source)
{
    double uncal;
    double target;
    double step;
    int64_t accepted;
    int64_t played;
    int64_t extra;
    int64_t queue;
    const char *kind;
    if (!clock) {
        if (source)
            *source = "uncalibrated-iec";
        return 0.0 / 0.0;
    }
    if (paused || isnan(content_end)) {
        if (source)
            *source = clock->source;
        return fs_iec_clock_hold(clock);
    }
    if (latency_sec < 0.0 || latency_sec > 1.0)
        latency_sec = 0.0;
    clock->bursts++;
    observe(clock, frame_position, nano_time, now_nano, ts_valid);
    uncal = content_end - latency_sec;
    target = uncal;
    kind = "uncalibrated-iec";
    if (clock->calibrated && clock->ts_status == 1) {
        accepted = clock->bursts * (int64_t)FS_IEC_PERIOD_FRAMES;
        played = clock->unwrap;
        if (now_nano > clock->ts_nano) {
            extra = (int64_t)((double)(now_nano - clock->ts_nano) * (double)FS_IEC_RATE / 1e9);
            if (extra > (int64_t)FS_IEC_PERIOD_FRAMES)
                extra = FS_IEC_PERIOD_FRAMES;
            if (extra > 0)
                played += extra;
        }
        queue = accepted - played;
        if (played >= 0 && queue >= 0 && queue <= (int64_t)FS_IEC_RATE * 5) {
            target = content_end - (double)queue / (double)FS_IEC_RATE;
            if (target > content_end)
                target = content_end;
            kind = "ts-iec";
        } else if (played >= 0 && queue < 0 && clock->have_published) {
            /* Ahead of the bytes we wrote. Do not treat that as an empty buffer. */
            kind = "ts-iec";
            target = clock->published;
        } else {
            clock->calibrated = 0;
            clock->ts_advances = 0;
        }
    } else if (clock->calibrated) {
        /* A rejected sample does not invent a new position. */
        kind = "ts-iec";
        target = clock->published;
    }
    if (!clock->have_published || kind[0] != 't')
        clock->published = target;
    else if (clock->ts_status == 1) {
        step = target - clock->published;
        if (step > FS_IEC_STEP)
            step = FS_IEC_STEP;
        if (step < -FS_IEC_STEP)
            step = -FS_IEC_STEP;
        clock->published += step;
    }
    clock->have_published = 1;
    set_source(clock, kind);
    if (source)
        *source = clock->source;
    return clock->published;
}
