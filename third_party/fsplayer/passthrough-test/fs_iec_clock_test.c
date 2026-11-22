#include "fs_iec_clock.h"

#include <math.h>
#include <stdio.h>

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

static double burst(FsIecClock *clock, int n, int64_t frame, int64_t now, int valid, const char **source)
{
    return fs_iec_clock_on_burst(clock, 1.0 + (n - 1) * 0.032, 0.064, 0,
                                 frame, now, now, valid, source);
}

static void qualifies_after_three_and_caps_follow(void)
{
    FsIecClock clock;
    const char *source = "";
    double before;
    double presented;
    int i;
    int64_t now = 10000000000LL;
    fs_iec_clock_init(&clock);
    for (i = 1; i <= 3; i++) {
        presented = burst(&clock, i, (int64_t)i * 6144, now + (int64_t)i * 32000000LL, 1, &source);
        expect_near("still the 64 ms clock", presented, 1.0 + (i - 1) * 0.032 - 0.064);
        expect_int("not qualified yet", clock.calibrated, 0);
    }
    before = presented;
    presented = burst(&clock, 4, 4 * 6144, now + 4 * 32000000LL, 1, &source);
    expect_int("carrier rate qualifies", clock.calibrated, 1);
    expect_int("source is the timestamp", source[0] == 't' && source[1] == 's', 1);
    expect_int("step is at most 64 ms", fabs(presented - before) <= 0.064 + 0.0001, 1);
    expect_int("does not jump to the raw target", fabs((1.0 + 3 * 0.032) - presented) > 0.02, 1);
}

static void rejects_bad_time_and_wrong_unit(void)
{
    FsIecClock clock;
    const char *source = "";
    double presented;
    int64_t now = 5000000000LL;
    fs_iec_clock_init(&clock);
    presented = fs_iec_clock_on_burst(&clock, 1.0, 0.064, 0, 6144, now - 3000000000LL, now, 1, &source);
    expect_int("stale", clock.ts_status, 3);
    expect_near("stale stays at 64 ms", presented, 1.0 - 0.064);
    fs_iec_clock_init(&clock);
    presented = fs_iec_clock_on_burst(&clock, 1.0, 0.064, 0, 6144, now + 200000000LL, now, 1, &source);
    expect_int("future", clock.ts_status, 4);
    expect_near("future stays at 64 ms", presented, 1.0 - 0.064);
    fs_iec_clock_init(&clock);
    burst(&clock, 1, 1536, now + 32000000LL, 1, &source);
    burst(&clock, 2, 3072, now + 64000000LL, 1, &source);
    burst(&clock, 3, 4608, now + 96000000LL, 1, &source);
    presented = burst(&clock, 4, 6144, now + 128000000LL, 1, &source);
    expect_int("content-rate unit stays uncalibrated", clock.calibrated, 0);
    expect_int("source names the fallback", source[0] == 'u', 1);
    expect_near("wrong unit keeps 64 ms", presented, 1.0 + 3 * 0.032 - 0.064);
}

static void wrap_and_leap(void)
{
    FsIecClock clock;
    const char *source = "";
    int64_t now = 8000000000LL;
    int i;
    uint32_t frame = 0;
    const int steps = (int)(4294967296LL / 6144) + 3;
    fs_iec_clock_init(&clock);
    for (i = 1; i <= steps; i++) {
        frame += 6144;
        fs_iec_clock_on_burst(&clock, 1.0 + (i - 1) * 0.032, 0.064, 0,
                              frame, now + (int64_t)i * 32000000LL, now + (int64_t)i * 32000000LL,
                              1, &source);
    }
    expect_int("wrap still counts carrier frames", clock.calibrated, 1);
    expect_int("unwrap crossed 32 bits", clock.unwrap > 4294967295LL, 1);
    fs_iec_clock_init(&clock);
    for (i = 1; i <= 5; i++)
        burst(&clock, i, (int64_t)i * 6144, now + (int64_t)i * 32000000LL, 1, &source);
    burst(&clock, 6, 100, now + 6 * 32000000LL, 1, &source);
    expect_int("leap clears classification", clock.calibrated, 0);
    expect_int("leap status", clock.ts_status, 6);
    expect_near("leap returns to 64 ms", fs_iec_clock_hold(&clock), 1.0 + 5 * 0.032 - 0.064);
}

static void pause_seek_and_partial(void)
{
    FsIecClock clock;
    const char *source = "";
    double held;
    int64_t bursts;
    fs_iec_clock_init(&clock);
    held = burst(&clock, 1, 0, 1000, 0, &source);
    bursts = clock.bursts;
    fs_iec_clock_on_burst(&clock, 5.0, 0.064, 1, 99999, 5000, 5000, 1, &source);
    expect_near("pause freezes", fs_iec_clock_hold(&clock), held);
    expect_int("pause does not accept a burst", clock.bursts, bursts);
    fs_iec_clock_on_burst(&clock, 0.0 / 0.0, 0.064, 0, 0, 0, 0, 0, &source);
    expect_near("partial write invents nothing", fs_iec_clock_hold(&clock), held);
    expect_int("partial does not count", clock.bursts, bursts);
    fs_iec_clock_reset(&clock);
    held = fs_iec_clock_on_burst(&clock, 50.032, 0.064, 0, 0, 0, 0, 0, &source);
    expect_int("seek clears calibration", clock.calibrated, 0);
    expect_near("seek reanchors on the 64 ms clock", held, 50.032 - 0.064);
}

int main(void)
{
    qualifies_after_three_and_caps_follow();
    rejects_bad_time_and_wrong_unit();
    wrap_and_leap();
    pause_seek_and_partial();
    if (g_failed)
        fprintf(stderr, "%d failures\n", g_failed);
    return g_failed ? 1 : 0;
}
