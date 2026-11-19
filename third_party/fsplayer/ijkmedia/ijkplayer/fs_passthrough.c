#include "fs_passthrough.h"

#include <stdlib.h>
#include <string.h>

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

int fs_eac3_frame_bytes(const uint8_t *data, int size)
{
    int frmsiz;
    int bytes;
    if (!data || size < 4)
        return 0;
    if (data[0] != 0x0B || data[1] != 0x77)
        return 0;
    frmsiz = ((data[2] & 0x07) << 8) | data[3];
    bytes = (frmsiz + 1) * 2;
    if (bytes < 4 || bytes > size)
        return 0;
    return bytes;
}

int fs_eac3_next_frame(const uint8_t *data, int size, int start, int *at)
{
    int i;
    if (!data || size < 4 || start < 0)
        return 0;
    for (i = start; i + 4 <= size; i++) {
        int n = fs_eac3_frame_bytes(data + i, size - i);
        if (n < 4)
            continue;
        if (at)
            *at = i;
        return n;
    }
    return 0;
}

int fs_eac3_gather_append(uint8_t *buf, int *len, int cap, const uint8_t *data, int size)
{
    int have;
    int copy;
    if (!buf || !len || cap <= 0 || size < 0)
        return -1;
    if (!data || size == 0)
        return 0;
    have = *len;
    if (have < 0 || have > cap)
        have = 0;
    /* Keep the tail that can still start a syncframe. */
    if (have + size > cap) {
        int drop = have + size - cap;
        if (drop >= have) {
            have = 0;
        } else {
            memmove(buf, buf + drop, (size_t) (have - drop));
            have -= drop;
        }
    }
    copy = size;
    if (have + copy > cap)
        copy = cap - have;
    if (copy > 0)
        memcpy(buf + have, data + (size - copy), (size_t) copy);
    *len = have + copy;
    return 0;
}

int fs_eac3_gather_next(uint8_t *buf, int *len, uint8_t *out, int out_cap)
{
    int size;
    int i;
    if (!buf || !len || !out || out_cap < 4)
        return 0;
    size = *len;
    if (size < 4)
        return 0;
    for (i = 0; i + 4 <= size; i++) {
        int n;
        if (buf[i] != 0x0B || buf[i + 1] != 0x77)
            continue;
        n = fs_eac3_frame_bytes(buf + i, size - i);
        if (n < 4) {
            /* Syncword is here but the frame continues into the next packet. */
            if (i > 0) {
                memmove(buf, buf + i, (size_t) (size - i));
                *len = size - i;
            }
            return 0;
        }
        if (n > out_cap)
            return -1;
        memcpy(out, buf + i, (size_t) n);
        {
            int rest = size - (i + n);
            if (rest > 0)
                memmove(buf, buf + i + n, (size_t) rest);
            *len = rest > 0 ? rest : 0;
        }
        return n;
    }
    /* No syncword. Keep one byte in case it is the first half of 0x0B77. */
    if (size > 1) {
        buf[0] = buf[size - 1];
        *len = 1;
    }
    return 0;
}

int fs_eac3_passthrough_payload(const uint8_t *data, int size, uint8_t *out, int cap)
{
    int at = 0;
    int n;
    if (!out || cap < 4)
        return 0;
    n = fs_eac3_next_frame(data, size, 0, &at);
    if (n < 4 || n > cap)
        return 0;
    memcpy(out, data + at, (size_t) n);
    return n;
}

int fs_passthrough_open_channels(int container_channels)
{
    if (container_channels == 8)
        return 8;
    /* 5.1 is the HDMI layout for a raw compressed open. IEC 61937 does not use it. */
    return 6;
}

int fs_iec61937_carrier_rate(int content_rate)
{
    if (content_rate != 32000 && content_rate != 44100 && content_rate != 48000)
        content_rate = 48000;
    return content_rate * 4;
}

int fs_iec61937_open_rate(int encoding, int content_rate)
{
    /* encoding selects IEC61937 vs the PCM carrier. Both use one 4× step.
     * 2.0.52 opened IEC61937 at the content rate, so each 24576-byte burst
     * took 128 ms on a 48 kHz track while the bitstream inside it was 32 ms. */
    (void) encoding;
    return fs_iec61937_carrier_rate(content_rate);
}

int fs_iec61937_transport_bytes_per_sec(int carrier_rate)
{
    if (carrier_rate <= 0)
        return 0;
    return carrier_rate * 2 * 2;
}

int fs_iec61937_period_content_us(int content_rate)
{
    if (content_rate <= 0)
        content_rate = 48000;
    return (int) (1536LL * 1000000LL / content_rate);
}

int fs_iec61937_period_transport_us(int carrier_rate)
{
    int bps = fs_iec61937_transport_bytes_per_sec(carrier_rate);
    if (bps <= 0)
        return 0;
    return (int) ((long long) FS_IEC61937_EAC3_BURST_BYTES * 1000000LL / bps);
}

int fs_passthrough_pause_on_queue_underrun(int passthrough_on)
{
    return passthrough_on ? 0 : 1;
}

struct FsIec61937Eac3 {
    uint8_t payload[FS_IEC61937_EAC3_BURST_BYTES - 8];
    int payload_bytes;
    int units;
    int units_needed;
    int active;
};

FsIec61937Eac3 *fs_iec61937_eac3_new(void)
{
    return (FsIec61937Eac3 *) calloc(1, sizeof(FsIec61937Eac3));
}

void fs_iec61937_eac3_free(FsIec61937Eac3 *st)
{
    free(st);
}

/* FFmpeg spdifenc.c eac3_repeat[]: how many frames fill one 1536-sample period. */
static int fs_eac3_repeat_count(const uint8_t *frame, int size)
{
    static const int repeat_for_blocks[4] = {6, 3, 2, 1};
    int bsid;
    int numblkscod;
    if (!frame || size < 6)
        return 1;
    bsid = frame[5] >> 3;
    if (bsid <= 10)
        return 1;
    /* fscod == 3: numblkscod is not in this field; the frame is 6 blocks. */
    if ((frame[4] & 0xc0) == 0xc0)
        return 1;
    numblkscod = (frame[4] >> 4) & 0x3;
    return repeat_for_blocks[numblkscod];
}

int fs_eac3_frame_samples(const uint8_t *data, int size)
{
    int repeat = fs_eac3_repeat_count(data, size);
    if (repeat <= 0)
        return 0;
    return 1536 / repeat;
}

int fs_iec61937_eac3_active(const FsIec61937Eac3 *st)
{
    return st && st->active;
}

int fs_iec61937_eac3_pending_bytes(const FsIec61937Eac3 *st)
{
    return st && st->active ? st->payload_bytes : 0;
}

static int fs_iec_append(FsIec61937Eac3 *st, const uint8_t *frame, int size)
{
    if (!st || !frame || size < 4)
        return 0;
    if (st->payload_bytes + size > (int) sizeof(st->payload))
        return 0;
    memcpy(st->payload + st->payload_bytes, frame, (size_t) size);
    st->payload_bytes += size;
    return 1;
}

static int fs_iec_emit(FsIec61937Eac3 *st, uint8_t *out, int cap)
{
    int payload;
    int i;
    if (!st || !out || cap < FS_IEC61937_EAC3_BURST_BYTES || st->payload_bytes < 4)
        return -1;
    payload = st->payload_bytes & ~1;
    memset(out, 0, FS_IEC61937_EAC3_BURST_BYTES);
    /* Pa 0xF872, Pb 0x4E1F, little-endian, same as avio_wl16. */
    out[0] = 0x72;
    out[1] = 0xF8;
    out[2] = 0x1F;
    out[3] = 0x4E;
    /* Pc: data-type 21 (E-AC-3). Pd: payload length in bytes, not bits. */
    out[4] = 21;
    out[5] = 0;
    out[6] = (uint8_t) (payload & 0xff);
    out[7] = (uint8_t) ((payload >> 8) & 0xff);
    for (i = 0; i + 1 < payload; i += 2) {
        out[8 + i] = st->payload[i + 1];
        out[8 + i + 1] = st->payload[i];
    }
    st->active = 0;
    st->payload_bytes = 0;
    st->units = 0;
    st->units_needed = 0;
    return FS_IEC61937_EAC3_BURST_BYTES;
}

static void fs_iec_begin(FsIec61937Eac3 *st, const uint8_t *frame, int size)
{
    st->active = 1;
    st->payload_bytes = 0;
    st->units = 0;
    st->units_needed = fs_eac3_repeat_count(frame, size);
}

int fs_iec61937_eac3_push(FsIec61937Eac3 *st, const uint8_t *data, int size,
                          uint8_t *out, int cap)
{
    int at = 0;
    int n;
    int produced = 0;
    const uint8_t *frame;
    if (!st)
        return -1;
    n = fs_eac3_next_frame(data, size, 0, &at);
    if (n < 4 || n > 4096)
        return -1;
    frame = data + at;
    if (fs_eac3_frame_is_dependent(frame, n)) {
        if (!st->active)
            return 0;
        fs_iec_append(st, frame, n);
        return 0;
    }
    if (st->active && st->units >= st->units_needed && st->payload_bytes >= 4) {
        produced = fs_iec_emit(st, out, cap);
        if (produced < 0)
            return produced;
    }
    if (!st->active)
        fs_iec_begin(st, frame, n);
    if (!fs_iec_append(st, frame, n)) {
        if (produced == 0 && st->payload_bytes >= 4) {
            produced = fs_iec_emit(st, out, cap);
            if (produced < 0)
                return produced;
            fs_iec_begin(st, frame, n);
            if (!fs_iec_append(st, frame, n))
                return produced > 0 ? produced : -1;
            st->units = 1;
            return produced;
        }
        return produced > 0 ? produced : -1;
    }
    st->units++;
    return produced;
}

int fs_iec61937_eac3_flush(FsIec61937Eac3 *st, uint8_t *out, int cap)
{
    if (!st || !st->active || st->payload_bytes < 4)
        return 0;
    return fs_iec_emit(st, out, cap);
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
    (void) profile;
    (void) joc_hint;
    (void) extradata;
    (void) extradata_size;
    if (codec_id == FS_CODEC_ID_AC3)
        return FS_ENCODING_AC3;
    if (codec_id != FS_CODEC_ID_EAC3)
        return 0; /* TrueHD and everything else stay on the PCM decoder. */
    /* Family is E-AC-3. JOC vs plain is chosen by fs_passthrough_open_order. */
    joc = 0;
    (void) joc;
    return FS_ENCODING_E_AC3;
}

int fs_passthrough_open_order(int encoding, int joc, int *out, int cap)
{
    if (!out || cap <= 0 || encoding == 0)
        return 0;
    (void) joc;
    if (encoding == FS_ENCODING_AC3) {
        out[0] = FS_ENCODING_AC3;
        return 1;
    }
    if (encoding != FS_ENCODING_E_AC3 && encoding != FS_ENCODING_E_AC3_JOC)
        return 0;
    /* Path A (ENCODING_E_AC3 / JOC, raw syncframes) killed the Dune. */
    out[0] = FS_ENCODING_IEC61937;
    if (cap < 2)
        return 1;
    out[1] = FS_ENCODING_IEC61937_PCM;
    return 2;
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
    d.joc = d.encoding == FS_ENCODING_E_AC3 &&
            (joc_hint || profile == FS_PROFILE_EAC3_DDP_ATMOS ||
             fs_eac3_extradata_has_dependent(extradata, extradata_size));
    if (d.encoding != 0 && endpoint_open_ok == 0) {
        d.encoding = 0;
        d.joc = 0;
        d.fallback_pcm = 1;
    }
    return d;
}
