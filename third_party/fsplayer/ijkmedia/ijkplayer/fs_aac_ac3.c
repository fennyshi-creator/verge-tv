#include "fs_aac_ac3.h"

#include <math.h>
#include <string.h>

#include "libavcodec/avcodec.h"
#include "libavutil/channel_layout.h"
#include "libavutil/error.h"
#include "libavutil/samplefmt.h"

/*
 * FFmpeg's AC-3 encoder (LGPL-2.1-or-later, Justin Ruggles / Fabrice Bellard).
 * The prebuilt libavcodec in this tree was configured with --disable-encoders,
 * so these symbols come from ac3enc.c and ac3enc_float.c compiled into
 * libfsplayer. ff_ac3_encoder is an FFCodec; its first field is the public
 * AVCodec, which is what avcodec_open2 casts back.
 *
 * This is a lossy compatibility path for multichannel AAC, not Dolby Atmos
 * and not a patent or compliance certificate.
 */
typedef struct FFCodec FFCodec;
extern const FFCodec ff_ac3_encoder;

enum {
    FS_AAC_AC3_FRAME = 1536,
    FS_AAC_AC3_CH = 6,
    FS_AAC_AC3_RATE = 48000,
    FS_AAC_AC3_QUEUE = 8,
    FS_AAC_AC3_BYTES = 2048
};

struct FsAacAc3 {
    AVCodecContext *ctx;
    AVFrame *frame;
    AVPacket *packet;
    int filled;
    int failed;
    int qn;
    int qr;
    int qsize[FS_AAC_AC3_QUEUE];
    double qpts[FS_AAC_AC3_QUEUE];
    unsigned char q[FS_AAC_AC3_QUEUE][FS_AAC_AC3_BYTES];
    FsAacLedger ledger;
};

static int open_encoder(FsAacAc3 *st)
{
    const AVCodec *codec = (const AVCodec *)&ff_ac3_encoder;
    int ret;

    st->ctx = avcodec_alloc_context3(codec);
    st->frame = av_frame_alloc();
    st->packet = av_packet_alloc();
    if (!st->ctx || !st->frame || !st->packet)
        return -1;
    st->ctx->sample_rate = FS_AAC_AC3_RATE;
    st->ctx->bit_rate = 384000;
    st->ctx->sample_fmt = AV_SAMPLE_FMT_FLTP;
    /* FL FR FC LFE BL BR. swr writes this order into interleaved S16. */
    av_channel_layout_from_mask(&st->ctx->ch_layout, AV_CH_LAYOUT_5POINT1);
    ret = avcodec_open2(st->ctx, codec, NULL);
    if (ret < 0)
        return -1;
    st->frame->nb_samples = FS_AAC_AC3_FRAME;
    st->frame->format = AV_SAMPLE_FMT_FLTP;
    st->frame->sample_rate = FS_AAC_AC3_RATE;
    av_channel_layout_from_mask(&st->frame->ch_layout, AV_CH_LAYOUT_5POINT1);
    if (av_frame_get_buffer(st->frame, 0) < 0)
        return -1;
    if (st->ctx->initial_padding > 0)
        fs_aac_ledger_prime(&st->ledger, st->ctx->initial_padding);
    return 0;
}

FsAacAc3 *fs_aac_ac3_open(void)
{
    FsAacAc3 *st = av_mallocz(sizeof(*st));
    if (!st)
        return NULL;
    if (open_encoder(st) < 0) {
        fs_aac_ac3_close(st);
        return NULL;
    }
    return st;
}

void fs_aac_ac3_close(FsAacAc3 *st)
{
    if (!st)
        return;
    avcodec_free_context(&st->ctx);
    av_frame_free(&st->frame);
    av_packet_free(&st->packet);
    av_free(st);
}

void fs_aac_ac3_reset(FsAacAc3 *st)
{
    if (!st || !st->ctx)
        return;
    st->filled = 0;
    st->qn = 0;
    st->qr = 0;
    st->failed = 0;
    fs_aac_ledger_reset(&st->ledger);
    if (st->ctx->initial_padding > 0)
        fs_aac_ledger_prime(&st->ledger, st->ctx->initial_padding);
    avcodec_flush_buffers(st->ctx);
}

static void store_planar(FsAacAc3 *st, int at, const int16_t *interleaved, int samples)
{
    int ch, i;
    for (ch = 0; ch < FS_AAC_AC3_CH; ch++) {
        float *plane = (float *)st->frame->data[ch];
        for (i = 0; i < samples; i++)
            plane[at + i] = interleaved[i * FS_AAC_AC3_CH + ch] / 32768.0f;
    }
}

static int enqueue_packet(FsAacAc3 *st)
{
    int slot;
    double start;
    if (st->packet->size <= 0 || st->packet->size > FS_AAC_AC3_BYTES)
        return -1;
    if (st->qn >= FS_AAC_AC3_QUEUE)
        return -1;
    slot = (st->qr + st->qn) % FS_AAC_AC3_QUEUE;
    memcpy(st->q[slot], st->packet->data, st->packet->size);
    st->qsize[slot] = st->packet->size;
    start = fs_aac_ledger_next_packet_pts(&st->ledger);
    fs_aac_ledger_emit(&st->ledger);
    st->qpts[slot] = isnan(start) ? start : start + (double)FS_AAC_AC3_FRAME / FS_AAC_AC3_RATE;
    st->qn++;
    return 0;
}

int fs_aac_ac3_push(FsAacAc3 *st, const int16_t *interleaved, int samples, double first_pts)
{
    if (!st || st->failed || !interleaved || samples < 0)
        return -1;
    fs_aac_ledger_push(&st->ledger, samples, first_pts);
    while (samples > 0) {
        int room = FS_AAC_AC3_FRAME - st->filled;
        int take = samples < room ? samples : room;
        int ret;
        if (st->filled == 0 && av_frame_make_writable(st->frame) < 0) {
            st->failed = 1;
            return -1;
        }
        store_planar(st, st->filled, interleaved, take);
        st->filled += take;
        interleaved += take * FS_AAC_AC3_CH;
        samples -= take;
        if (st->filled < FS_AAC_AC3_FRAME)
            break;
        if (st->qn >= FS_AAC_AC3_QUEUE) {
            st->failed = 1;
            return -1;
        }
        ret = avcodec_send_frame(st->ctx, st->frame);
        st->filled = 0;
        if (ret < 0) {
            st->failed = 1;
            return -1;
        }
        while ((ret = avcodec_receive_packet(st->ctx, st->packet)) == 0) {
            if (enqueue_packet(st) < 0) {
                av_packet_unref(st->packet);
                st->failed = 1;
                return -1;
            }
            av_packet_unref(st->packet);
        }
        if (ret != AVERROR(EAGAIN) && ret != AVERROR_EOF) {
            st->failed = 1;
            return -1;
        }
    }
    return 0;
}

int fs_aac_ac3_take(FsAacAc3 *st, unsigned char *out, int cap, double *content_end)
{
    int size;
    if (content_end)
        *content_end = 0.0 / 0.0;
    if (!st || !out || cap <= 0)
        return -1;
    if (st->qn <= 0)
        return 0;
    size = st->qsize[st->qr];
    if (size <= 0 || size > cap)
        return -1;
    memcpy(out, st->q[st->qr], size);
    if (content_end)
        *content_end = st->qpts[st->qr];
    st->qr = (st->qr + 1) % FS_AAC_AC3_QUEUE;
    st->qn--;
    return size;
}
