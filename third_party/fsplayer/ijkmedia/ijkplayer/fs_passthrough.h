/*
 * Dolby AC-3 / E-AC-3 / E-AC-3 JOC bitstream passthrough decisions.
 *
 * This file is intentionally free of FFmpeg and Android headers so the same
 * decision can be compiled into libfsplayer.so and into a host unit test.
 * Codec ids match FFmpeg's AV_CODEC_ID_* values (see libavcodec/codec_id.h).
 * ff_ffplay.c static-asserts that match.
 *
 * TrueHD is not a passthrough target. ENCODING_E_AC3_JOC is Android's
 * AudioFormat constant 18 (API 28+). JOC is still E-AC-3 packets; the
 * dependent substream is what distinguishes it from plain E-AC-3.
 */
#ifndef FS_PASSTHROUGH_H
#define FS_PASSTHROUGH_H

#include <stddef.h>
#include <stdint.h>

#define FS_CODEC_ID_AC3     0x15003
#define FS_CODEC_ID_EAC3    0x15028
#define FS_CODEC_ID_TRUEHD  0x1502C

#define FS_PROFILE_EAC3_DDP_ATMOS 30

#define FS_ENCODING_PCM_16BIT  2
#define FS_ENCODING_AC3        5
#define FS_ENCODING_E_AC3      6
#define FS_ENCODING_E_AC3_JOC  18

typedef struct FsPassthroughDecision {
    int encoding;     /* 0 = do not passthrough; else AudioFormat.ENCODING_* */
    int joc;          /* 1 when Atmos should open as ENCODING_E_AC3_JOC first */
    int fallback_pcm; /* 1 when a Dolby encoding was wanted but the endpoint rejected it */
} FsPassthroughDecision;

/* 1 if this E-AC-3 syncframe is a dependent substream (strmtyp == 1). */
int fs_eac3_frame_is_dependent(const uint8_t *data, int size);

/*
 * Bytes of one E-AC-3 syncframe, or 0 if [data] is not a syncframe.
 * frmsiz counts 16-bit words minus one.
 */
int fs_eac3_frame_bytes(const uint8_t *data, int size);

/*
 * Copy exactly one E-AC-3 syncframe, independent or dependent.
 * AudioTrack / the HDMI HAL is given that frame alone. Bytes that are not
 * a syncframe return 0. IEC 61937 is not added here: ENCODING_E_AC3 and
 * ENCODING_E_AC3_JOC are wrapped by AudioFlinger, and a second preamble
 * is what kills this HAL.
 */
int fs_eac3_passthrough_payload(const uint8_t *data, int size, uint8_t *out, int cap);

/*
 * Next syncframe at or after [start]. Returns its size, or 0.
 * *at receives the byte offset. Independent and dependent frames are
 * both returned, one at a time, so a glued Atmos packet is not one write.
 */
int fs_eac3_next_frame(const uint8_t *data, int size, int start, int *at);

/*
 * Channel count AudioTrack should open with. 6 and 8 are trusted.
 * Anything else (including a container that claims stereo for 5.1 E-AC-3)
 * opens as 5.1. Stereo remains a later retry in the caller, not the first mask.
 */
int fs_passthrough_open_channels(int container_channels);

/*
 * 1 if ISO/IEC 14496-3 EC3SpecificBox (dec3) or a leading syncframe says a
 * dependent substream is present. A dependent E-AC-3 substream is how JOC
 * objects ride along with the independent frame.
 */
int fs_eac3_extradata_has_dependent(const uint8_t *data, int size);

/*
 * enabled:        player option audio-passthrough
 * speed_is_one:   encoded AudioTrack cannot time-stretch; any other speed is PCM
 * joc_hint:       app/container said Atmos / JOC (profile may still be unknown)
 * endpoint_open_ok: 1 opened, 0 open failed, -1 not attempted yet
 */
/*
 * Encodings to try. Atmos opens as ENCODING_E_AC3_JOC first, the same
 * AudioFormat ExoPlayer uses, then plain E-AC-3. Returns how many were written.
 */
int fs_passthrough_open_order(int encoding, int joc, int *out, int cap);

FsPassthroughDecision fs_passthrough_decide(
    int enabled,
    int speed_is_one,
    int codec_id,
    int profile,
    int joc_hint,
    int endpoint_open_ok,
    const uint8_t *extradata,
    int extradata_size);

#endif
