#include "fs_aac_ac3_clock.h"

#include <string.h>

enum { FS_AC3_BYTES_PER_SEC = 48000, FS_AC3_FRAME_BYTES = 1536 };

void fs_aac_ac3_clock_init(FsAacAc3Clock *clock, int buffer_bytes)
{
    memset(clock, 0, sizeof(*clock));
    clock->buffer_bytes = buffer_bytes > 0 ? buffer_bytes : 65536;
    clock->anchor_pts = 0.0 / 0.0;
}

void fs_aac_ac3_clock_reset(FsAacAc3Clock *clock)
{
    int buffer = clock->buffer_bytes;
    fs_aac_ac3_clock_init(clock, buffer);
}

void fs_aac_ac3_clock_accept(FsAacAc3Clock *clock, int bytes, double frame_end_pts)
{
    if (!clock || bytes <= 0)
        return;
    if (!clock->anchored) {
        clock->anchor_pts = frame_end_pts - (double)bytes / FS_AC3_BYTES_PER_SEC;
        clock->anchored = 1;
    }
    clock->accepted += bytes;
    clock->frames++;
}

void fs_aac_ac3_clock_note_blocked(FsAacAc3Clock *clock)
{
    if (clock)
        clock->buffer_full = 1;
}

void fs_aac_ac3_clock_head(FsAacAc3Clock *clock, uint32_t head, int valid)
{
    uint32_t delta;
    double per_frame;
    if (!clock)
        return;
    if (!valid) {
        if (clock->frames >= 8 && clock->head_unit == 0)
            clock->head_unit = -1;
        return;
    }
    if (!clock->head_seen) {
        clock->head_accum = head;
        clock->head_last = head;
        clock->head_seen = 1;
    } else {
        delta = head - clock->head_last;
        clock->head_accum += delta;
        clock->head_last = head;
    }
    if (clock->head_unit != 0 || clock->frames < 4 || clock->accepted < FS_AC3_FRAME_BYTES * 4)
        return;
    if (clock->head_accum <= 0) {
        clock->head_unit = -1;
        return;
    }
    per_frame = (double)clock->head_accum / ((double)clock->accepted / FS_AC3_FRAME_BYTES);
    if (per_frame > 768.0 && per_frame < 3072.0)
        clock->head_unit = 1;
    else if (per_frame > 0.5 && per_frame < 3.0)
        clock->head_unit = 2;
    else
        clock->head_unit = -1;
}

static int64_t played_bytes(const FsAacAc3Clock *clock)
{
    int64_t played = 0;
    if (clock->head_unit == 1)
        played = clock->head_accum;
    else if (clock->head_unit == 2)
        played = clock->head_accum * FS_AC3_FRAME_BYTES;
    else if (clock->buffer_full || clock->accepted > clock->buffer_bytes)
        played = clock->accepted > clock->buffer_bytes ? clock->accepted - clock->buffer_bytes : 0;
    if (played < 0)
        played = 0;
    if (played > clock->accepted)
        played = clock->accepted;
    return played;
}

int64_t fs_aac_ac3_clock_queued_bytes(const FsAacAc3Clock *clock)
{
    int64_t queued;
    if (!clock)
        return 0;
    queued = clock->accepted - played_bytes(clock);
    return queued > 0 ? queued : 0;
}

double fs_aac_ac3_clock_presented(const FsAacAc3Clock *clock, const char **source)
{
    const char *kind = "startup";
    if (!clock || !clock->anchored) {
        if (source)
            *source = "startup";
        return 0.0 / 0.0;
    }
    if (clock->head_unit > 0)
        kind = "head";
    else if (played_bytes(clock) > 0)
        kind = "queue";
    if (source)
        *source = kind;
    return clock->anchor_pts + (double)played_bytes(clock) / FS_AC3_BYTES_PER_SEC;
}

int fs_encoded_write_step(int *offset, int payload, int result, int *complete, int *blocked)
{
    if (complete)
        *complete = 0;
    if (blocked)
        *blocked = 0;
    if (!offset || payload <= 0)
        return -1;
    if (result < 0)
        return -1;
    if (result == 0) {
        if (blocked)
            *blocked = 1;
        return 0;
    }
    if (*offset < 0)
        *offset = 0;
    if (*offset + result > payload)
        result = payload - *offset;
    *offset += result;
    if (*offset >= payload) {
        *offset = 0;
        if (complete)
            *complete = 1;
    }
    return 0;
}

int fs_audio_release_step(int *phase, const char **name)
{
    static const char *steps[] = { "pause", "flush", "stop", "release" };
    int at;
    if (!phase)
        return -1;
    if (*phase < 0)
        *phase = 0;
    if (*phase >= 4) {
        if (name)
            *name = "done";
        return 1;
    }
    at = *phase;
    if (name)
        *name = steps[at];
    *phase = at + 1;
    return 0;
}
