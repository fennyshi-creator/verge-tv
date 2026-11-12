/*
 * PCM channel count -> Android AudioFormat channel mask.
 * Encoded AC-3 / E-AC-3 passthrough does not use this; it stays stereo
 * because Android packetizes the bitstream to IEC 61937.
 *
 * Masks match AudioFormat: MONO 0x4, STEREO 0xc, QUAD 0xcc,
 * 5.1 0xfc, 7.1 0x3fc.
 */
#ifndef FS_ANDROID_CHANNELS_H
#define FS_ANDROID_CHANNELS_H

#define FS_CHANNEL_OUT_INVALID  0x0
#define FS_CHANNEL_OUT_MONO     0x4
#define FS_CHANNEL_OUT_STEREO   0xc
#define FS_CHANNEL_OUT_QUAD     0xcc
#define FS_CHANNEL_OUT_5POINT1  0xfc
#define FS_CHANNEL_OUT_7POINT1  0x3fc

static inline int fs_android_channel_mask(int channel_count)
{
    switch (channel_count) {
    case 1: return FS_CHANNEL_OUT_MONO;
    case 2: return FS_CHANNEL_OUT_STEREO;
    case 4: return FS_CHANNEL_OUT_QUAD;
    case 6: return FS_CHANNEL_OUT_5POINT1;
    case 8: return FS_CHANNEL_OUT_7POINT1;
    default: return FS_CHANNEL_OUT_INVALID;
    }
}

static inline int fs_android_channel_count(int mask)
{
    switch (mask) {
    case FS_CHANNEL_OUT_MONO: return 1;
    case FS_CHANNEL_OUT_STEREO: return 2;
    case FS_CHANNEL_OUT_QUAD: return 4;
    case FS_CHANNEL_OUT_5POINT1: return 6;
    case FS_CHANNEL_OUT_7POINT1: return 8;
    default: return 0;
    }
}

/*
 * open_ok == 0: AudioTrack rejected the mask. 5.1 / 7.1 / quad fall back to
 * stereo so playback continues. open_ok != 0 keeps the source mask.
 */
static inline int fs_android_channel_mask_after_open(int channel_count, int open_ok)
{
    int mask = fs_android_channel_mask(channel_count);
    if (mask == FS_CHANNEL_OUT_INVALID)
        return FS_CHANNEL_OUT_INVALID;
    if (!open_ok && channel_count > 2)
        return FS_CHANNEL_OUT_STEREO;
    return mask;
}

#endif
