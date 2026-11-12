#include "fs_android_channels.h"

#include <stdio.h>

static int g_failed;

static void expect_int(const char *name, int got, int want)
{
    if (got != want) {
        fprintf(stderr, "FAIL %s: got 0x%x want 0x%x\n", name, got, want);
        g_failed++;
    }
}

int main(void)
{
    expect_int("mono", fs_android_channel_mask(1), FS_CHANNEL_OUT_MONO);
    expect_int("stereo", fs_android_channel_mask(2), FS_CHANNEL_OUT_STEREO);
    expect_int("quad", fs_android_channel_mask(4), FS_CHANNEL_OUT_QUAD);
    expect_int("5.1", fs_android_channel_mask(6), FS_CHANNEL_OUT_5POINT1);
    expect_int("7.1", fs_android_channel_mask(8), FS_CHANNEL_OUT_7POINT1);
    expect_int("unknown", fs_android_channel_mask(5), FS_CHANNEL_OUT_INVALID);
    expect_int("count 5.1", fs_android_channel_count(FS_CHANNEL_OUT_5POINT1), 6);
    expect_int("count 7.1", fs_android_channel_count(FS_CHANNEL_OUT_7POINT1), 8);

    expect_int("5.1 open", fs_android_channel_mask_after_open(6, 1), FS_CHANNEL_OUT_5POINT1);
    expect_int("7.1 open", fs_android_channel_mask_after_open(8, 1), FS_CHANNEL_OUT_7POINT1);
    expect_int("5.1 rejected falls back to stereo",
               fs_android_channel_mask_after_open(6, 0), FS_CHANNEL_OUT_STEREO);
    expect_int("7.1 rejected falls back to stereo",
               fs_android_channel_mask_after_open(8, 0), FS_CHANNEL_OUT_STEREO);
    expect_int("stereo rejected stays stereo",
               fs_android_channel_mask_after_open(2, 0), FS_CHANNEL_OUT_STEREO);

    if (g_failed) {
        fprintf(stderr, "%d assertion(s) failed\n", g_failed);
        return 1;
    }
    printf("fs_android_channels_test ok\n");
    return 0;
}
