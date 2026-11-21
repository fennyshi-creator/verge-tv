#include "fs_aac_ac3_clock.h"

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
    if (fabs(actual - wanted) < 0.0005)
        return;
    fprintf(stderr, "FAIL %s actual=%f wanted=%f\n", label, actual, wanted);
    g_failed++;
}

static void clock_cases(void)
{
    FsAacAc3Clock clock;
    const char *source = "";
    int i;
    double presented;
    fs_aac_ac3_clock_init(&clock, 65536);
    for (i = 0; i < 32; i++) {
        fs_aac_ac3_clock_accept(&clock, 1536, (i + 1) * 0.032);
        fs_aac_ac3_clock_head(&clock, (uint32_t)((i + 1) * 1536), 1);
    }
    presented = fs_aac_ac3_clock_presented(&clock, &source);
    expect_near("32 frames", presented, 1.024);
    expect_int("head source", source[0] == 'h', 1);
    expect_int("queued after head", fs_aac_ac3_clock_queued_bytes(&clock), 0);

    fs_aac_ac3_clock_init(&clock, 65536);
    fs_aac_ac3_clock_accept(&clock, 1536, 0.032);
    presented = fs_aac_ac3_clock_presented(&clock, &source);
    expect_near("startup holds the anchor", presented, 0.0);
    expect_int("startup source", source[0] == 's', 1);

    fs_aac_ac3_clock_init(&clock, 65536);
    for (i = 0; i < 44; i++)
        fs_aac_ac3_clock_accept(&clock, 1536, (i + 1) * 0.032);
    fs_aac_ac3_clock_note_blocked(&clock);
    for (i = 0; i < 8; i++)
        fs_aac_ac3_clock_head(&clock, 0, 0);
    presented = fs_aac_ac3_clock_presented(&clock, &source);
    expect_int("queue source", source[0] == 'q', 1);
    expect_near("only the overflow has played", presented, (44 * 1536 - 65536) / 48000.0);

    fs_aac_ac3_clock_reset(&clock);
    fs_aac_ac3_clock_accept(&clock, 1536, 100.032);
    presented = fs_aac_ac3_clock_presented(&clock, &source);
    expect_near("seek reanchors", presented, 100.0);

    fs_aac_ac3_clock_init(&clock, 65536);
    fs_aac_ac3_clock_accept(&clock, 1536, 0.032);
    fs_aac_ac3_clock_head(&clock, 0xfffffff0u, 1);
    fs_aac_ac3_clock_accept(&clock, 1536, 0.064);
    fs_aac_ac3_clock_head(&clock, 16, 1);
    expect_int("head unwraps", clock.head_accum > 0xfffffff0ll, 1);
}

static void write_and_release_cases(void)
{
    int offset = 0;
    int complete = 0;
    int blocked = 0;
    int phase = 0;
    const char *name = "";
    int i;
    expect_int("partial", fs_encoded_write_step(&offset, 1536, 1000, &complete, &blocked), 0);
    expect_int("not complete", complete, 0);
    expect_int("offset", offset, 1000);
    expect_int("rest", fs_encoded_write_step(&offset, 1536, 536, &complete, &blocked), 0);
    expect_int("frame complete", complete, 1);
    expect_int("offset reset", offset, 0);
    expect_int("block", fs_encoded_write_step(&offset, 1536, 0, &complete, &blocked), 0);
    expect_int("blocked flag", blocked, 1);
    expect_int("error", fs_encoded_write_step(&offset, 1536, -1, &complete, &blocked), -1);
    for (i = 0; i < 4; i++)
        expect_int(name, fs_audio_release_step(&phase, &name), 0);
    expect_int("pause", name[0] == 'r' || phase == 4, 1);
    expect_int("second release", fs_audio_release_step(&phase, &name), 1);
}

int main(void)
{
    clock_cases();
    write_and_release_cases();
    return g_failed ? 1 : 0;
}
