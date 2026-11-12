#include "fs_passthrough.h"

static int fs_bit_get(const uint8_t *data, int size, int *bitpos, int nbits)
{
    int value = 0;
    int i;
    if (nbits <= 0 || nbits > 24)
        return -1;
    for (i = 0; i < nbits; i++) {
        int pos = *bitpos;
        int byte = pos >> 3;
        int shift = 7 - (pos & 7);
        if (byte < 0 || byte >= size)
            return -1;
        value = (value << 1) | ((data[byte] >> shift) & 1);
        *bitpos = pos + 1;
    }
    return value;
}

int fs_eac3_frame_is_dependent(const uint8_t *data, int size)
{
    if (!data || size < 3)
        return 0;
    /* syncword 0x0B77, then strmtyp is the next 2 bits. 1 = dependent. */
    if (data[0] != 0x0B || data[1] != 0x77)
        return 0;
    return ((data[2] >> 6) & 0x3) == 1;
}

static int fs_dec3_payload_has_dependent(const uint8_t *data, int size)
{
    int bit = 0;
    int num_ind_sub;
    int i;
    if (!data || size < 3)
        return 0;
    if (fs_bit_get(data, size, &bit, 13) < 0)
        return 0;
    num_ind_sub = fs_bit_get(data, size, &bit, 3);
    if (num_ind_sub < 0)
        return 0;
    for (i = 0; i < num_ind_sub + 1; i++) {
        int num_dep;
        if (fs_bit_get(data, size, &bit, 2) < 0) return 0; /* fscod */
        if (fs_bit_get(data, size, &bit, 5) < 0) return 0; /* bsid */
        if (fs_bit_get(data, size, &bit, 1) < 0) return 0; /* reserved */
        if (fs_bit_get(data, size, &bit, 1) < 0) return 0; /* asvc */
        if (fs_bit_get(data, size, &bit, 3) < 0) return 0; /* bsmod */
        if (fs_bit_get(data, size, &bit, 3) < 0) return 0; /* acmod */
        if (fs_bit_get(data, size, &bit, 1) < 0) return 0; /* lfeon */
        if (fs_bit_get(data, size, &bit, 3) < 0) return 0; /* reserved */
        num_dep = fs_bit_get(data, size, &bit, 4);
        if (num_dep < 0)
            return 0;
        if (num_dep > 0)
            return 1;
        if (fs_bit_get(data, size, &bit, 1) < 0) return 0; /* reserved */
    }
    return 0;
}

int fs_eac3_extradata_has_dependent(const uint8_t *data, int size)
{
    int i;
    if (!data || size <= 0)
        return 0;
    if (fs_eac3_frame_is_dependent(data, size))
        return 1;
    /* Full box: size(4) + 'dec3'. Some muxers hand us the payload only. */
    if (size >= 8 && data[4] == 'd' && data[5] == 'e' && data[6] == 'c' && data[7] == '3') {
        if (fs_dec3_payload_has_dependent(data + 8, size - 8))
            return 1;
    }
    if (size >= 4 && data[0] == 'd' && data[1] == 'e' && data[2] == 'c' && data[3] == '3') {
        if (fs_dec3_payload_has_dependent(data + 4, size - 4))
            return 1;
    }
    if (fs_dec3_payload_has_dependent(data, size))
        return 1;
    for (i = 0; i + 2 < size; i++) {
        if (data[i] == 0x0B && data[i + 1] == 0x77 && ((data[i + 2] >> 6) & 0x3) == 1)
            return 1;
    }
    return 0;
}

static int fs_wanted_encoding(int codec_id, int profile, int joc_hint,
                              const uint8_t *extradata, int extradata_size)
{
    int joc;
    if (codec_id == FS_CODEC_ID_AC3)
        return FS_ENCODING_AC3;
    if (codec_id != FS_CODEC_ID_EAC3)
        return 0; /* TrueHD and everything else stay on the PCM decoder. */
    joc = joc_hint || profile == FS_PROFILE_EAC3_DDP_ATMOS ||
          fs_eac3_extradata_has_dependent(extradata, extradata_size);
    return joc ? FS_ENCODING_E_AC3_JOC : FS_ENCODING_E_AC3;
}

FsPassthroughDecision fs_passthrough_decide(
    int enabled,
    int speed_is_one,
    int codec_id,
    int profile,
    int joc_hint,
    int endpoint_open_ok,
    const uint8_t *extradata,
    int extradata_size)
{
    FsPassthroughDecision d;
    d.encoding = 0;
    d.joc = 0;
    d.fallback_pcm = 0;
    if (!enabled || !speed_is_one)
        return d;
    d.encoding = fs_wanted_encoding(codec_id, profile, joc_hint, extradata, extradata_size);
    d.joc = d.encoding == FS_ENCODING_E_AC3_JOC;
    if (d.encoding != 0 && endpoint_open_ok == 0) {
        d.encoding = 0;
        d.joc = 0;
        d.fallback_pcm = 1;
    }
    return d;
}
