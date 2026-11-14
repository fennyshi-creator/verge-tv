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
    expect_int("open ok not joc encoding", d.joc, 0);
    expect_int("open ok no fallback", d.fallback_pcm, 0);
    {
        int order[2] = {0, 0};
        expect_int("eac3 order count", fs_passthrough_open_order(FS_ENCODING_E_AC3_JOC, order, 2), 2);
        expect_int("eac3 tried before joc", order[0], FS_ENCODING_E_AC3);
        expect_int("joc is the second try", order[1], FS_ENCODING_E_AC3_JOC);
    }

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
        expect_int("independent plus dependent", n, 16);
        n = fs_eac3_passthrough_payload(packet + 8, 8, out, 16);
        expect_int("lone dependent is dropped", n, 0);
        packet[0] = 0x00;
        n = fs_eac3_passthrough_payload(packet, 8, out, 16);
        expect_int("non sync is dropped", n, 0);
    }

    if (g_failed) {
        fprintf(stderr, "%d assertion(s) failed\n", g_failed);
        return 1;
    }
    printf("fs_passthrough_test ok\n");
    return 0;
}
