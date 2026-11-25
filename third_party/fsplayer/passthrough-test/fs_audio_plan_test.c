#include "fs_audio_plan.h"

#include <stdio.h>

static int g_failed;

static void expect_int(const char *name, int got, int want)
{
    if (got != want) {
        fprintf(stderr, "FAIL %s: got %d want %d\n", name, got, want);
        g_failed++;
    }
}

int main(void)
{
    expect_int("compat 6 to 2", fs_downmix_open_channels(6, 1), 2);
    expect_int("compat 8 to 2", fs_downmix_open_channels(8, 1), 2);
    expect_int("compat stereo stays", fs_downmix_open_channels(2, 1), 2);
    expect_int("pcm keeps 6", fs_downmix_open_channels(6, 0), 6);
    expect_int("pcm keeps 2", fs_downmix_open_channels(2, 0), 2);

    expect_int("pcm seek does not flush", fs_seek_flushes_track(0), 0);
    expect_int("encoded seek flushes", fs_seek_flushes_track(1), 1);
    expect_int("paused seek stays paused", fs_seek_keeps_user_pause(1), 1);
    expect_int("playing seek may resume", fs_seek_keeps_user_pause(0), 0);

    {
        FsSeekCoalesce slot;
        int64_t pos = 0;
        int id = 0;
        slot.req = 0;
        slot.id = 0;
        slot.pos = 0;
        fs_seek_coalesce_put(&slot, 1000);
        fs_seek_coalesce_put(&slot, 9000);
        expect_int("second seek still pending", slot.req, 1);
        expect_int("take latest", fs_seek_coalesce_take(&slot, &pos, &id), 1);
        expect_int("latest position", (int) pos, 9000);
        expect_int("two puts", id, 2);
        expect_int("taken clears", slot.req, 0);
        expect_int("empty take", fs_seek_coalesce_take(&slot, &pos, &id), 0);
        fs_seek_coalesce_put(&slot, 4000);
        expect_int("seek during previous", slot.req, 1);
        expect_int("take the one that arrived late", fs_seek_coalesce_take(&slot, &pos, &id), 1);
        expect_int("late position", (int) pos, 4000);
        expect_int("backward id", id, 3);
    }

    if (g_failed) {
        fprintf(stderr, "%d assertion(s) failed\n", g_failed);
        return 1;
    }
    printf("fs_audio_plan_test ok\n");
    return 0;
}
