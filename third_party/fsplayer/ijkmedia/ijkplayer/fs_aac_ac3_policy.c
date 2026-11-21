#include "fs_aac_ac3.h"

int fs_aac_ac3_wanted(int mode, int profile, int channels, int codec_is_aac,
                      int speed_is_one, int force_stereo, int route_allows_ac3,
                      int prefer_ffmpeg)
{
    if (mode == 2 || force_stereo || !speed_is_one || !route_allows_ac3)
        return 0;
    if (!codec_is_aac || channels <= 2)
        return 0;
    if (mode == 1)
        return 1;
    if (prefer_ffmpeg)
        return 0;
    return mode == 0 && profile;
}
