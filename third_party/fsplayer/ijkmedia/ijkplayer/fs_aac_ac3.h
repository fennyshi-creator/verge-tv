#ifndef FS_AAC_AC3_H
#define FS_AAC_AC3_H

#include <stdint.h>

/*
 * Optional client-side AAC multichannel to AC-3 for HDMI receivers that
 * only light a Dolby indicator on a compressed bitstream. AC-3 / E-AC-3
 * source packets never use this. The encoder is FFmpeg's LGPL AC-3 encoder.
 *
 * mode: 0 auto, 1 on, 2 off.
 * profile: verified Realtek tv794a / thor / mjolnir only.
 */
int fs_aac_ac3_wanted(int mode, int profile, int channels, int codec_is_aac,
                      int speed_is_one, int force_stereo, int route_allows_ac3,
                      int prefer_ffmpeg);

typedef struct FsAacAc3 FsAacAc3;

FsAacAc3 *fs_aac_ac3_open(void);
void fs_aac_ac3_close(FsAacAc3 *st);
void fs_aac_ac3_reset(FsAacAc3 *st);

/*
 * Interleaved S16, 6 channels, 48 kHz, already resampled. 0 ok, -1 failed.
 * A full AC-3 frame is 1536 samples. take() returns one frame, 0 if none.
 */
int fs_aac_ac3_push(FsAacAc3 *st, const int16_t *interleaved, int samples);
int fs_aac_ac3_take(FsAacAc3 *st, unsigned char *out, int cap);

#endif
