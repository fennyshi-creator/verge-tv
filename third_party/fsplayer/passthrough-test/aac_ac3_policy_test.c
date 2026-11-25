#include "fs_aac_ac3.h"

#include <stdio.h>

static int expect(int actual, int wanted, const char *label)
{
    if (actual == wanted)
        return 0;
    fprintf(stderr, "FAIL %s actual=%d wanted=%d\n", label, actual, wanted);
    return 1;
}

int main(void)
{
    int failed = 0;
    failed += expect(fs_aac_ac3_wanted(0, 1, 6, 1, 1, 0, 1, 0), 1, "auto profile");
    failed += expect(fs_aac_ac3_wanted(0, 0, 6, 1, 1, 0, 1, 0), 0, "auto other device");
    failed += expect(fs_aac_ac3_wanted(0, 1, 6, 0, 1, 0, 1, 0), 0, "eac3 is not aac");
    failed += expect(fs_aac_ac3_wanted(1, 0, 6, 1, 1, 0, 1, 1), 1, "explicit on");
    failed += expect(fs_aac_ac3_wanted(2, 1, 6, 1, 1, 0, 1, 0), 0, "off");
    failed += expect(fs_aac_ac3_wanted(0, 1, 6, 1, 1, 1, 1, 0), 0, "stereo");
    failed += expect(fs_aac_ac3_wanted(0, 1, 6, 1, 0, 0, 1, 0), 0, "speed");
    failed += expect(fs_aac_ac3_wanted(0, 1, 2, 1, 1, 0, 1, 0), 0, "aac stereo");
    failed += expect(fs_aac_ac3_wanted(0, 1, 6, 1, 1, 0, 0, 0), 0, "no ac3 route");
    failed += expect(fs_aac_ac3_wanted(0, 1, 6, 1, 1, 0, 1, 1), 0, "auto ffmpeg preference");
    return failed ? 1 : 0;
}
