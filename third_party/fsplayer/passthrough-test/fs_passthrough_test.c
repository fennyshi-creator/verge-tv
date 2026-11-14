#include "fs_passthrough.c"

#include <stdio.h>
#include <string.h>

static int g_failed;

static void expect_int(const char *name, int got, int want)
{
    if (got != want) {
        fprintf(stderr, "FAIL %s: got %d want %d\n", name, got, want);
        g_failed++;
    }
}

/* data_rate=768, num_ind_sub=0, one substream with num_dep_sub=1. */
static void write_bits(uint8_t *out, int *bitpos, int value, int nbits)
{
    int i;
    for (i = nbits - 1; i >= 0; i--) {
        int pos = *bitpos;
        int byte = pos >> 3;
        int shift = 7 - (pos & 7);
        if ((value >> i) & 1)
            out[byte] |= (uint8_t) (1 << shift);
        *bitpos = pos + 1;
    }
}

static int make_dec3_with_dependent(uint8_t *out)
{
    int bit = 0;
    memset(out, 0, 16);
    write_bits(out, &bit, 768, 13);
    write_bits(out, &bit, 0, 3);    /* num_ind_sub */
    write_bits(out, &bit, 0, 2);    /* fscod */
    write_bits(out, &bit, 16, 5);   /* bsid */
    write_bits(out, &bit, 0, 1);
    write_bits(out, &bit, 0, 1);
    write_bits(out, &bit, 0, 3);    /* bsmod */
    write_bits(out, &bit, 7, 3);    /* acmod 3/2 */
    write_bits(out, &bit, 1, 1);    /* lfeon */
    write_bits(out, &bit, 0, 3);
    write_bits(out, &bit, 1, 4);    /* num_dep_sub */
    write_bits(out, &bit, 0, 9);    /* chan_loc */
    return (bit + 7) >> 3;
}

int main(void)
{
    uint8_t dec3[16];
    int dec3_size = make_dec3_with_dependent(dec3);
    uint8_t independent[3] = {0x0B, 0x77, 0x00};
    uint8_t dependent[3] = {0x0B, 0x77, 0x40};
    FsPassthroughDecision d;

    expect_int("ac3", fs_passthrough_decide(1, 1, FS_CODEC_ID_AC3, 0, 0, -1, NULL, 0).encoding,
               FS_ENCODING_AC3);
    expect_int("eac3", fs_passthrough_decide(1, 1, FS_CODEC_ID_EAC3, 0, 0, -1, NULL, 0).encoding,
               FS_ENCODING_E_AC3);
    expect_int("joc profile opens as eac3",
               fs_passthrough_decide(1, 1, FS_CODEC_ID_EAC3, FS_PROFILE_EAC3_DDP_ATMOS, 0, -1, NULL, 0).encoding,
               FS_ENCODING_E_AC3);
    expect_int("joc hint opens as eac3", fs_passthrough_decide(1, 1, FS_CODEC_ID_EAC3, 0, 1, -1, NULL, 0).encoding,
               FS_ENCODING_E_AC3);
    expect_int("joc dec3 opens as eac3",
               fs_passthrough_decide(1, 1, FS_CODEC_ID_EAC3, 0, 0, -1, dec3, dec3_size).encoding,
               FS_ENCODING_E_AC3);
    expect_int("joc frame opens as eac3",
               fs_passthrough_decide(1, 1, FS_CODEC_ID_EAC3, 0, 0, -1, dependent, 3).encoding,
               FS_ENCODING_E_AC3);
    expect_int("independent stays eac3",
               fs_passthrough_decide(1, 1, FS_CODEC_ID_EAC3, 0, 0, -1, independent, 3).encoding,
               FS_ENCODING_E_AC3);
    expect_int("truehd", fs_passthrough_decide(1, 1, FS_CODEC_ID_TRUEHD, 30, 1, 1, NULL, 0).encoding, 0);
    expect_int("disabled", fs_passthrough_decide(0, 1, FS_CODEC_ID_AC3, 0, 0, 1, NULL, 0).encoding, 0);
    expect_int("speed", fs_passthrough_decide(1, 0, FS_CODEC_ID_EAC3, 30, 1, 1, NULL, 0).encoding, 0);

    d = fs_passthrough_decide(1, 1, FS_CODEC_ID_EAC3, 30, 0, 0, NULL, 0);
    expect_int("fallback encoding", d.encoding, 0);
    expect_int("fallback flag", d.fallback_pcm, 1);
    expect_int("fallback not joc", d.joc, 0);

    d = fs_passthrough_decide(1, 1, FS_CODEC_ID_EAC3, 30, 0, 1, NULL, 0);
    expect_int("open ok", d.encoding, FS_ENCODING_E_AC3);
    expect_int("atmos opens joc first", d.joc, 1);
    expect_int("open ok no fallback", d.fallback_pcm, 0);
    expect_int("plain eac3 is not joc",
               fs_passthrough_decide(1, 1, FS_CODEC_ID_EAC3, 0, 0, 1, NULL, 0).joc, 0);
    {
        int order[2] = {0, 0};
        expect_int("atmos order count", fs_passthrough_open_order(FS_ENCODING_E_AC3, 1, order, 2), 2);
        expect_int("iec is the first try", order[0], FS_ENCODING_IEC61937);
        expect_int("4x pcm is the second try", order[1], FS_ENCODING_IEC61937_PCM);
        expect_int("plain order count", fs_passthrough_open_order(FS_ENCODING_E_AC3, 0, order, 2), 2);
        expect_int("plain also opens iec", order[0], FS_ENCODING_IEC61937);
        expect_int("ac3 stays ac3", fs_passthrough_open_order(FS_ENCODING_AC3, 0, order, 2), 1);
        expect_int("ac3 encoding", order[0], FS_ENCODING_AC3);
    }
    expect_int("48k carrier", fs_iec61937_carrier_rate(48000), 192000);
    expect_int("unknown carrier", fs_iec61937_carrier_rate(0), 192000);
    expect_int("iec opens at carrier", fs_iec61937_open_rate(FS_ENCODING_IEC61937, 48000), 192000);
    expect_int("pcm carrier opens at 4x", fs_iec61937_open_rate(FS_ENCODING_IEC61937_PCM, 48000), 192000);
    expect_int("carrier is not 8x", fs_iec61937_open_rate(FS_ENCODING_IEC61937, 192000), 192000);
    expect_int("content period 32ms", fs_iec61937_period_content_us(48000), 32000);
    expect_int("transport period at 192k", fs_iec61937_period_transport_us(192000), 32000);
    expect_int("periods match", fs_iec61937_period_content_us(48000), fs_iec61937_period_transport_us(192000));
    expect_int("48k carrier is 128ms", fs_iec61937_period_transport_us(48000), 128000);
    expect_int("transport bytes", fs_iec61937_transport_bytes_per_sec(192000), 768000);
    expect_int("5.1 mask", fs_passthrough_open_channels(6), 6);
    expect_int("stereo container still opens 5.1", fs_passthrough_open_channels(2), 6);
    expect_int("7.1 mask", fs_passthrough_open_channels(8), 8);

    d = fs_passthrough_decide(1, 1, FS_CODEC_ID_TRUEHD, 0, 0, 0, NULL, 0);
    expect_int("truehd reject is not a fallback", d.fallback_pcm, 0);

    {
        uint8_t packet[16];
        uint8_t out[16];
        int n;
        memset(packet, 0, sizeof(packet));
        /* 8-byte independent frame: frmsiz = 3 -> (3+1)*2 = 8. */
        packet[0] = 0x0B;
        packet[1] = 0x77;
        packet[2] = 0x00;
        packet[3] = 0x03;
        /* 8-byte dependent frame glued on. strmtyp = 1. */
        packet[8] = 0x0B;
        packet[9] = 0x77;
        packet[10] = 0x40;
        packet[11] = 0x03;
        n = fs_eac3_passthrough_payload(packet, 16, out, 16);
        expect_int("one syncframe per write", n, 8);
        expect_int("payload is the independent frame", out[2], 0x00);
        {
            int at = -1;
            int n2 = fs_eac3_next_frame(packet, 16, n, &at);
            expect_int("dependent follows as its own frame", n2, 8);
            expect_int("dependent offset", at, 8);
        }
        n = fs_eac3_passthrough_payload(packet + 8, 8, out, 16);
        expect_int("dependent frame is kept", n, 8);
        packet[0] = 0x00;
        n = fs_eac3_passthrough_payload(packet, 8, out, 16);
        expect_int("non sync is dropped", n, 0);
    }

    {
        FsIec61937Eac3 *st = fs_iec61937_eac3_new();
        uint8_t independent[8];
        uint8_t dependent[8];
        uint8_t next[8];
        uint8_t burst[FS_IEC61937_EAC3_BURST_BYTES];
        int i;
        memset(independent, 0, sizeof(independent));
        memset(dependent, 0, sizeof(dependent));
        memset(next, 0, sizeof(next));
        /* 8-byte 6-block frames. bsid 16 so numblkscod is honored. */
        independent[0] = dependent[0] = next[0] = 0x0B;
        independent[1] = dependent[1] = next[1] = 0x77;
        independent[2] = 0x00;
        dependent[2] = 0x40;
        next[2] = 0x00;
        independent[3] = dependent[3] = next[3] = 0x03;
        independent[4] = dependent[4] = next[4] = 0x30; /* numblkscod = 3, 6 blocks */
        independent[5] = dependent[5] = next[5] = 0x80; /* bsid 16 */
        expect_int("hold independent", fs_iec61937_eac3_push(st, independent, 8, burst, sizeof(burst)), 0);
        expect_int("hold dependent in the same burst", fs_iec61937_eac3_push(st, dependent, 8, burst, sizeof(burst)), 0);
        expect_int("next independent emits the burst", fs_iec61937_eac3_push(st, next, 8, burst, sizeof(burst)), FS_IEC61937_EAC3_BURST_BYTES);
        expect_int("Pa0", burst[0], 0x72);
        expect_int("Pa1", burst[1], 0xF8);
        expect_int("Pb0", burst[2], 0x1F);
        expect_int("Pb1", burst[3], 0x4E);
        expect_int("Pc data-type", burst[4], 21);
        expect_int("Pc high", burst[5], 0);
        expect_int("Pd bytes", burst[6], 16);
        expect_int("Pd high", burst[7], 0);
        expect_int("swapped sync0", burst[8], 0x77);
        expect_int("swapped sync1", burst[9], 0x0B);
        expect_int("pad after payload", burst[8 + 16], 0);
        for (i = 8 + 16; i < FS_IEC61937_EAC3_BURST_BYTES; i++) {
            if (burst[i] != 0) {
                expect_int("padding is zero", burst[i], 0);
                break;
            }
        }
        {
            uint8_t gather[64];
            int glen = 0;
            uint8_t got[16];
            /* The same 8-byte syncframe, split 3 + 5 the way an HLS packet can. */
            expect_int("partial append", fs_eac3_gather_append(gather, &glen, 64, independent, 3), 0);
            expect_int("partial is not a frame yet", fs_eac3_gather_next(gather, &glen, got, 16), 0);
            expect_int("rest append", fs_eac3_gather_append(gather, &glen, 64, independent + 3, 5), 0);
            expect_int("split frame reassembled", fs_eac3_gather_next(gather, &glen, got, 16), 8);
            expect_int("reassembled sync", got[0], 0x0B);
            expect_int("reassembled frmsiz", got[3], 0x03);
            expect_int("gather drained", glen, 0);
        }
        expect_int("flush held frame", fs_iec61937_eac3_flush(st, burst, sizeof(burst)), FS_IEC61937_EAC3_BURST_BYTES);
        expect_int("flushed pd", burst[6], 8);
        fs_iec61937_eac3_free(st);
    }

    if (g_failed) {
        fprintf(stderr, "%d assertion(s) failed\n", g_failed);
        return 1;
    }
    printf("fs_passthrough_test ok\n");
    return 0;
}
