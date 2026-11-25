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

/* Encoded seeks still flush AudioTrack. PCM seeks do not. */
static inline int fs_seek_flushes_track(int encoded)
{
    return encoded ? 1 : 0;
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
