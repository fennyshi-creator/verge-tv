#include "fs_aac_ac3_clock.h"

#include <math.h>
#include <stdio.h>
#include <string.h>

enum { FS_AC3_RATE = 48000, FS_AC3_FRAME = 1536, FS_AC3_STEP = FS_AC3_FRAME * 2 };

static void set_reason(FsAacAc3Clock *clock, const char *reason)
{
    snprintf(clock->reason, sizeof(clock->reason), "%s", reason);
}

void fs_aac_ac3_clock_init(FsAacAc3Clock *clock, int buffer_bytes)
{
    memset(clock, 0, sizeof(*clock));
    clock->cap_bytes = buffer_bytes > 0 ? buffer_bytes : 65536;
    clock->anchor_pts = 0.0 / 0.0;
    set_reason(clock, "preroll");
}

void fs_aac_ac3_clock_reset(FsAacAc3Clock *clock)
{
    int cap = clock->cap_bytes;
    fs_aac_ac3_clock_init(clock, cap);
}

void fs_aac_ac3_clock_accept(FsAacAc3Clock *clock, int bytes, double content_end_pts)
{
    if (!clock || bytes <= 0 || isnan(content_end_pts))
        return;
    if (!clock->anchored) {
        clock->anchor_pts = content_end_pts - (double)bytes / FS_AC3_RATE;
        clock->anchored = 1;
    }
    clock->accepted += bytes;
    clock->slew_arm = 1;
}

void fs_aac_ac3_clock_note_blocked(FsAacAc3Clock *clock)
{
    if (clock)
        clock->buffer_full = 1;
}

static int ratio_unit(double per_frame)
{
    if (per_frame > 768.0 && per_frame < 3072.0)
        return 1;
    if (per_frame > 0.5 && per_frame < 3.0)
        return 2;
    return 0;
}

void fs_aac_ac3_clock_head(FsAacAc3Clock *clock, uint32_t head, int valid)
{
    uint32_t delta;
    uint32_t previous;
    double per_frame;
    if (!clock)
        return;
    clock->head_valid = valid ? 1 : 0;
    previous = clock->head_raw;
    if (!valid) {
        clock->invalid_reads++;
        set_reason(clock, "head-unread");
        return;
    }
    if (!clock->head_raw_seen) {
        clock->head_unwrap = head;
        clock->head_raw = head;
        clock->head_raw_seen = 1;
        set_reason(clock, "head-preroll");
        return;
    }
    delta = head - previous;
    clock->head_raw = head;
    /* A backward jump or a multi-second leap is a reset, not playback. */
    if (delta > (uint32_t)(FS_AC3_RATE * 2)) {
        clock->head_unwrap = head;
        clock->head_advances = 0;
        clock->head_votes_samples = 0;
        clock->head_votes_frames = 0;
        clock->head_unit = 0;
        if (clock->calibrated == 1)
            clock->calibrated = 0;
        set_reason(clock, "head-reset");
        return;
    }
    if (delta > 0) {
        int64_t dbytes = clock->accepted - clock->accepted_at_advance;
        clock->head_unwrap += delta;
        clock->accepted_at_advance = clock->accepted;
        clock->head_advances++;
        if (dbytes > 0) {
            per_frame = (double)delta / ((double)dbytes / FS_AC3_FRAME);
            if (ratio_unit(per_frame) == 1)
                clock->head_votes_samples++;
            else if (ratio_unit(per_frame) == 2)
                clock->head_votes_frames++;
        }
    }
    if (clock->head_advances < 4)
        return;
    if (clock->head_votes_samples >= 3)
        clock->head_unit = 1;
    else if (clock->head_votes_frames >= 3)
        clock->head_unit = 2;
    else {
        set_reason(clock, "head-unit-open");
        return;
    }
    if (clock->calibrated != 2)
        clock->calibrated = 1;
    set_reason(clock, clock->head_unit == 1 ? "head-samples" : "head-frames");
}

void fs_aac_ac3_clock_timestamp(FsAacAc3Clock *clock, int64_t frame_position,
                                int64_t nano_time, int64_t now_nano, int valid)
{
    int64_t delta_frame;
    int64_t delta_nano;
    double per_sec;
    int unit;
    if (!clock)
        return;
    if (!valid) {
        clock->ts_status = 2;
        return;
    }
    if (frame_position < 0 || nano_time <= 0 || now_nano <= 0) {
        clock->ts_status = 5;
        return;
    }
    if (nano_time > now_nano + 100000000LL) {
        clock->ts_status = 4;
        return;
    }
    if (now_nano > nano_time + 2000000000LL) {
        clock->ts_status = 3;
        return;
    }
    if (!clock->ts_seen) {
        clock->ts_frame = frame_position;
        clock->ts_unwrap = frame_position;
        clock->ts_nano = nano_time;
        clock->ts_seen = 1;
        clock->ts_status = 1;
        return;
    }
    if (frame_position >= clock->ts_frame)
        delta_frame = frame_position - clock->ts_frame;
    else if (frame_position >= 0 && clock->ts_frame <= 0xffffffffLL &&
             (uint32_t)frame_position - (uint32_t)clock->ts_frame <= (uint32_t)(FS_AC3_RATE * 2))
        delta_frame = (uint32_t)frame_position - (uint32_t)clock->ts_frame;
    else {
        clock->ts_frame = frame_position;
        clock->ts_unwrap = frame_position;
        clock->ts_nano = nano_time;
        clock->ts_advances = 0;
        clock->ts_pending = 0;
        if (clock->calibrated == 2)
            clock->calibrated = 0;
        clock->ts_status = 1;
        set_reason(clock, "ts-reset");
        return;
    }
    delta_nano = nano_time - clock->ts_nano;
    clock->ts_frame = frame_position;
    clock->ts_unwrap += delta_frame;
    clock->ts_nano = nano_time;
    clock->ts_status = 1;
    if (delta_frame > 0 && delta_nano > 1000000LL && delta_nano < 2000000000LL) {
        per_sec = (double)delta_frame * 1e9 / (double)delta_nano;
        unit = 0;
        if (per_sec > 8000.0 && per_sec < 96000.0)
            unit = 1;
        else if (per_sec > 5.0 && per_sec < 80.0)
            unit = 2;
        if (unit != 0) {
            if (unit == clock->ts_pending)
                clock->ts_advances++;
            else {
                clock->ts_pending = unit;
                clock->ts_advances = 1;
            }
        }
    }
    if (clock->ts_advances >= 3 && clock->ts_pending != 0) {
        clock->ts_unit = clock->ts_pending;
        clock->calibrated = 2;
        set_reason(clock, clock->ts_unit == 1 ? "ts-samples" : "ts-frames");
    }
}

void fs_aac_ac3_clock_capacity_frames(FsAacAc3Clock *clock, int64_t frames, int valid)
{
    int64_t bytes;
    int unit;
    if (!clock)
        return;
    clock->cap_frames_raw = frames;
    clock->cap_frames_valid = valid ? 1 : 0;
    if (!valid || frames <= 0)
        return;
    unit = clock->calibrated == 2 ? clock->ts_unit : clock->head_unit;
    if (unit == 1)
        bytes = frames;
    else if (unit == 2)
        bytes = frames * FS_AC3_FRAME;
    else
        return;
    if (bytes < FS_AC3_FRAME || bytes > FS_AC3_RATE * 5)
        return;
    clock->cap_bytes = (int)bytes;
}

static int64_t measured_samples(const FsAacAc3Clock *clock)
{
    int64_t raw;
    if (clock->calibrated == 2)
        raw = clock->ts_unit == 2 ? clock->ts_unwrap * FS_AC3_FRAME : clock->ts_unwrap;
    else if (clock->calibrated == 1)
        raw = clock->head_unit == 2 ? clock->head_unwrap * FS_AC3_FRAME : clock->head_unwrap;
    else
        return -1;
    if (raw < 0)
        raw = 0;
    if (clock->accepted > 0 && raw > clock->accepted)
        raw = clock->accepted;
    return raw;
}

static int64_t uncalibrated_samples(const FsAacAc3Clock *clock)
{
    if (clock->accepted <= clock->cap_bytes)
        return 0;
    return clock->accepted - clock->cap_bytes;
}

double fs_aac_ac3_clock_presented(FsAacAc3Clock *clock, const char **source)
{
    int64_t target;
    int64_t error;
    int64_t step;
    const char *kind = "uncalibrated";
    if (!clock || !clock->anchored) {
        if (source)
            *source = "startup";
        return 0.0 / 0.0;
    }
    target = measured_samples(clock);
    if (target >= 0) {
        kind = clock->reason[0] ? clock->reason : "measured";
    } else {
        target = uncalibrated_samples(clock);
        kind = clock->buffer_full ? "uncalibrated-bound" : "uncalibrated";
        if (!clock->calibrated)
            set_reason(clock, kind);
    }
    if (!clock->have_published) {
        clock->published = target;
        clock->have_published = 1;
    } else if (clock->slew_arm) {
        error = target - clock->published;
        step = error;
        if (step > FS_AC3_STEP)
            step = FS_AC3_STEP;
        if (step < -FS_AC3_STEP)
            step = -FS_AC3_STEP;
        clock->published += step;
        clock->slew_arm = 0;
    }
    if (clock->published < 0)
        clock->published = 0;
    if (clock->calibrated == 0) {
        if (clock->published > target)
            clock->published = target;
    } else if (clock->published > clock->accepted) {
        clock->published = clock->accepted;
    }
    clock->measured = target;
    if (source)
        *source = kind;
    return clock->anchor_pts + (double)clock->published / FS_AC3_RATE;
}

int64_t fs_aac_ac3_clock_queued_bytes(const FsAacAc3Clock *clock)
{
    int64_t queued;
    if (!clock)
        return 0;
    queued = clock->accepted - clock->published;
    return queued > 0 ? queued : 0;
}

void fs_aac_ledger_reset(FsAacLedger *ledger)
{
    int priming;
    if (!ledger)
        return;
    priming = ledger->priming;
    memset(ledger, 0, sizeof(*ledger));
    ledger->priming = priming;
    ledger->origin = 0.0 / 0.0;
}

void fs_aac_ledger_prime(FsAacLedger *ledger, int samples)
{
    if (!ledger)
        return;
    if (samples < 0 || samples > FS_AC3_RATE)
        samples = 0;
    ledger->priming = samples;
}

void fs_aac_ledger_push(FsAacLedger *ledger, int samples, double first_pts)
{
    double expected;
    if (!ledger || samples <= 0 || isnan(first_pts))
        return;
    if (!ledger->set) {
        ledger->origin = first_pts;
        ledger->set = 1;
        ledger->in_count = 0;
    } else {
        expected = ledger->origin + (double)ledger->in_count / FS_AC3_RATE;
        if (fabs(first_pts - expected) > 0.100) {
            ledger->origin = first_pts - (double)ledger->in_count / FS_AC3_RATE;
        }
    }
    ledger->in_count += samples;
}

double fs_aac_ledger_next_packet_pts(const FsAacLedger *ledger)
{
    if (!ledger || !ledger->set)
        return 0.0 / 0.0;
    return ledger->origin + (double)(ledger->out_count - ledger->priming) / FS_AC3_RATE;
}

void fs_aac_ledger_emit(FsAacLedger *ledger)
{
    if (ledger)
        ledger->out_count += FS_AC3_FRAME;
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
