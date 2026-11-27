/*
 * Decisions shared by the player and the host test.
 * The mutex around a seek slot stays in the caller.
 */
#ifndef FS_AUDIO_PLAN_H
#define FS_AUDIO_PLAN_H

#include <stdint.h>

/* Compatible decode asks for a real 2-channel PCM open. Other modes keep
 * the source channel count, including 6. */
static inline int fs_downmix_open_channels(int source_channels, int downmix_stereo)
{
    if (source_channels < 1)
        return source_channels;
    if (downmix_stereo && source_channels > 2)
        return 2;
    return source_channels;
}

/* Seeks never flush AudioTrack. PCM and encoded bursts are dropped by
 * serial. AudioTrack.flush on an E-AC3/Atmos passthrough track wedges
 * the HDMI HAL, including after the user has switched back to PCM. */
static inline int fs_seek_flushes_track(int encoded)
{
    (void) encoded;
    return 0;
}

/* How long close waits for the audio thread after abort, before a
 * last-resort stop/release. Shorter than the 12s Java gate. */
static inline int fs_close_wait_ms(void)
{
    return 1500;
}

/* Close may stop and release a track that is still inside a write.
 * A seek must not: closing is 0 while the user is only seeking. */
static inline int fs_close_unstick(int closing, int write_in_progress)
{
    return closing && write_in_progress;
}

/* A user pause already in effect survives a seek. */
static inline int fs_seek_keeps_user_pause(int pause_req)
{
    return pause_req ? 1 : 0;
}

typedef struct FsSeekCoalesce {
    int req;
    int id;
    int64_t pos;
} FsSeekCoalesce;

/* Latest target wins, including a seek that arrives before the previous
 * one has been taken. */
static inline void fs_seek_coalesce_put(FsSeekCoalesce *slot, int64_t pos)
{
    slot->pos = pos;
    slot->id += 1;
    if (slot->id <= 0)
        slot->id = 1;
    slot->req = 1;
}

static inline int fs_seek_coalesce_take(FsSeekCoalesce *slot, int64_t *pos, int *id)
{
    if (!slot->req)
        return 0;
    *pos = slot->pos;
    *id = slot->id;
    slot->req = 0;
    return 1;
}

#endif
