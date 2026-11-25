#include "fs_aac_ac3_clock.h"

#include <math.h>
#include <stdio.h>
#include <string.h>

static int g_failed;

static void expect_int(const char *label, long long actual, long long wanted)
{
    if (actual == wanted)
        return;
    fprintf(stderr, "FAIL %s actual=%lld wanted=%lld\n", label, actual, wanted);
    g_failed++;
}

static void expect_near(const char *label, double actual, double wanted)
{
    if (fabs(actual - wanted) < 0.002)
        return;
    fprintf(stderr, "FAIL %s actual=%f wanted=%f\n", label, actual, wanted);
    g_failed++;
}

static void feed_head(FsAacAc3Clock *clock, int frames, uint32_t *head, uint32_t step)
{
    int i;
    const char *source = "";
    for (i = 0; i < frames; i++) {
        *head += step;
        fs_aac_ac3_clock_accept(clock, 1536, clock->accepted / 48000.0 + 0.032);
        fs_aac_ac3_clock_head(clock, *head, 1);
        fs_aac_ac3_clock_presented(clock, &source);
    }
}

static void preroll_then_samples(void)
{
    FsAacAc3Clock clock;
    const char *source = "";
    uint32_t head = 0;
    int i;
    double presented;
    double before;
    fs_aac_ac3_clock_init(&clock, 65536);
    for (i = 0; i < 20; i++) {
        fs_aac_ac3_clock_accept(&clock, 1536, (i + 1) * 0.032);
        fs_aac_ac3_clock_head(&clock, 0, 1);
        fs_aac_ac3_clock_presented(&clock, &source);
    }
    expect_int("zeros do not freeze the head", clock.head_unit, 0);
    expect_int("still uncalibrated", clock.calibrated, 0);
    for (i = 0; i < 3; i++)
        fs_aac_ac3_clock_head(&clock, 0, 0);
    expect_int("invalid reads recover", clock.calibrated, 0);
    feed_head(&clock, 8, &head, 1536);
    expect_int("classifies samples", clock.head_unit, 1);
    expect_int("head is master", clock.calibrated, 1);
    before = fs_aac_ac3_clock_presented(&clock, &source);
    feed_head(&clock, 32, &head, 1536);
    presented = fs_aac_ac3_clock_presented(&clock, &source);
    expect_near("32 frames add 1.024s", presented - before, 1.024);
}

static void frame_unit_and_wrap(void)
{
    FsAacAc3Clock clock;
    const char *source = "";
    uint32_t head = 0xfffffff0u;
    int i;
    fs_aac_ac3_clock_init(&clock, 65536);
    fs_aac_ac3_clock_accept(&clock, 1536, 0.032);
    fs_aac_ac3_clock_head(&clock, head, 1);
    for (i = 0; i < 6; i++) {
        head += 1;
        fs_aac_ac3_clock_accept(&clock, 1536, (i + 2) * 0.032);
        fs_aac_ac3_clock_head(&clock, head, 1);
        fs_aac_ac3_clock_presented(&clock, &source);
    }
    expect_int("classifies syncframes", clock.head_unit, 2);
    expect_int("unwrap crossed 32 bits", clock.head_unwrap > 0xfffffff0ll, 1);
    fs_aac_ac3_clock_init(&clock, 65536);
    fs_aac_ac3_clock_accept(&clock, 1536, 0.032);
    fs_aac_ac3_clock_head(&clock, 1000, 1);
    for (i = 0; i < 6; i++) {
        fs_aac_ac3_clock_accept(&clock, 1536, (i + 2) * 0.032);
        fs_aac_ac3_clock_head(&clock, (uint32_t)(1001 + i), 1);
    }
    fs_aac_ac3_clock_head(&clock, 100, 1);
    expect_int("backward jump resets", clock.calibrated, 0);
    expect_int("backward jump clears advances", clock.head_advances, 0);
}

static void timestamp_filters(void)
{
    FsAacAc3Clock clock;
    const char *source = "";
    int64_t now = 10000000000LL;
    int i;
    double jumped;
    double first;
    fs_aac_ac3_clock_init(&clock, 65536);
    for (i = 0; i < 40; i++)
        fs_aac_ac3_clock_accept(&clock, 1536, (i + 1) * 0.032);
    fs_aac_ac3_clock_note_blocked(&clock);
    first = fs_aac_ac3_clock_presented(&clock, &source);
    fs_aac_ac3_clock_timestamp(&clock, 100000, now, now, 0);
    expect_int("unavailable timestamp", clock.ts_status, 2);
    fs_aac_ac3_clock_timestamp(&clock, 1000, now - 3000000000LL, now, 1);
    expect_int("stale timestamp", clock.ts_status, 3);
    fs_aac_ac3_clock_timestamp(&clock, 1000, now + 500000000LL, now, 1);
    expect_int("future timestamp", clock.ts_status, 4);
    for (i = 1; i <= 4; i++) {
        fs_aac_ac3_clock_accept(&clock, 1536, (40 + i) * 0.032);
        fs_aac_ac3_clock_timestamp(&clock, i * 1536, now + i * 32000000LL, now + i * 32000000LL, 1);
        fs_aac_ac3_clock_presented(&clock, &source);
    }
    expect_int("timestamp becomes master", clock.calibrated, 2);
    jumped = fs_aac_ac3_clock_presented(&clock, &source) - first;
    expect_int("handoff is bounded", jumped < 0.200 && jumped > -0.200, 1);
}

static void blocked_write_and_ledger(void)
{
    int offset = 0;
    int complete = 0;
    int blocked = 0;
    FsAacLedger ledger;
    expect_int("partial", fs_encoded_write_step(&offset, 1536, 1000, &complete, &blocked), 0);
    expect_int("rest", fs_encoded_write_step(&offset, 1536, 536, &complete, &blocked), 0);
    expect_int("one frame", complete, 1);
    expect_int("blocked", fs_encoded_write_step(&offset, 1536, 0, &complete, &blocked), 0);
    expect_int("blocked flag", blocked, 1);
    memset(&ledger, 0, sizeof(ledger));
    fs_aac_ledger_push(&ledger, 1024, 0.0);
    fs_aac_ledger_push(&ledger, 1024, 1024.0 / 48000.0);
    expect_near("first packet starts at 0", fs_aac_ledger_next_packet_pts(&ledger), 0.0);
    fs_aac_ledger_emit(&ledger);
    expect_near("second packet", fs_aac_ledger_next_packet_pts(&ledger), 1536.0 / 48000.0);
    fs_aac_ledger_prime(&ledger, 256);
    fs_aac_ledger_reset(&ledger);
    fs_aac_ledger_prime(&ledger, 256);
    fs_aac_ledger_push(&ledger, 1536, 1.0);
    expect_near("priming shifts the packet", fs_aac_ledger_next_packet_pts(&ledger), 1.0 - 256.0 / 48000.0);
}

static void timestamp_wrap_and_capacity(void)
{
    FsAacAc3Clock clock;
    const char *source = "";
    int64_t base = 0xfffffffeLL;
    int64_t now = 5000000000LL;
    int i;
    fs_aac_ac3_clock_init(&clock, 65536);
    fs_aac_ac3_clock_capacity_frames(&clock, 100, 1);
    expect_int("raw capacity is kept", clock.cap_frames_raw, 100);
    expect_int("implausible capacity is not applied", clock.cap_bytes, 65536);
    for (i = 0; i < 6; i++) {
        fs_aac_ac3_clock_accept(&clock, 1536, (i + 1) * 0.032);
        fs_aac_ac3_clock_timestamp(&clock, base + i, now + i * 32000000LL, now + i * 32000000LL, 1);
        fs_aac_ac3_clock_presented(&clock, &source);
    }
    expect_int("wrapped timestamp counts syncframes", clock.ts_unit, 2);
    expect_int("unwrap kept counting", clock.ts_unwrap > base, 1);
    fs_aac_ac3_clock_capacity_frames(&clock, 4, 1);
    expect_int("syncframe capacity becomes bytes", clock.cap_bytes, 4 * 1536);
}

static void long_run_and_seek_back(void)
{
    FsAacAc3Clock clock;
    const char *source = "";
    uint32_t head = 0;
    double start;
    double end;
    int i;
    fs_aac_ac3_clock_init(&clock, 65536);
    fs_aac_ac3_clock_accept(&clock, 1536, 0.032);
    fs_aac_ac3_clock_head(&clock, 0, 1);
    for (i = 0; i < 80; i++) {
        head += 1536;
        fs_aac_ac3_clock_accept(&clock, 1536, (i + 2) * 0.032);
        fs_aac_ac3_clock_head(&clock, head, 1);
        fs_aac_ac3_clock_presented(&clock, &source);
    }
    start = fs_aac_ac3_clock_presented(&clock, &source);
    for (i = 0; i < 10000; i++) {
        head += 1536;
        fs_aac_ac3_clock_accept(&clock, 1536, clock.anchor_pts + (clock.accepted + 1536) / 48000.0);
        fs_aac_ac3_clock_head(&clock, head, 1);
        fs_aac_ac3_clock_presented(&clock, &source);
    }
    end = fs_aac_ac3_clock_presented(&clock, &source);
    expect_near("long run stays with the head", end - start, 10000 * 1536 / 48000.0);
    fs_aac_ac3_clock_reset(&clock);
    fs_aac_ac3_clock_accept(&clock, 1536, 3.032);
    expect_near("seek backward reanchors", fs_aac_ac3_clock_presented(&clock, &source), 3.0);
}

static void seek_and_pause_hold(void)
{
    FsAacAc3Clock clock;
    const char *source = "";
    double held;
    int i;
    fs_aac_ac3_clock_init(&clock, 65536);
    for (i = 0; i < 50; i++) {
        fs_aac_ac3_clock_accept(&clock, 1536, (i + 1) * 0.032);
        fs_aac_ac3_clock_presented(&clock, &source);
    }
    held = fs_aac_ac3_clock_presented(&clock, &source);
    fs_aac_ac3_clock_note_blocked(&clock);
    fs_aac_ac3_clock_presented(&clock, &source);
    expect_near("stall does not add time", fs_aac_ac3_clock_presented(&clock, &source), held);
    fs_aac_ac3_clock_reset(&clock);
    fs_aac_ac3_clock_accept(&clock, 1536, 50.032);
    expect_near("seek reanchors", fs_aac_ac3_clock_presented(&clock, &source), 50.0);
    expect_int("seek clears calibration", clock.calibrated, 0);
}

int main(void)
{
    preroll_then_samples();
    frame_unit_and_wrap();
    timestamp_filters();
    timestamp_wrap_and_capacity();
    blocked_write_and_ledger();
    long_run_and_seek_back();
    seek_and_pause_hold();
    if (g_failed)
        fprintf(stderr, "%d failures\n", g_failed);
    return g_failed ? 1 : 0;
}
