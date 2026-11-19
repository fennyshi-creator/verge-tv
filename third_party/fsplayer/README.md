# FSPlayer (vendored)

Sources are from [debugly/fsplayer](https://github.com/debugly/fsplayer) 1.1.2, licensed under the GNU Lesser General Public License v2.1 or later. See `COPYING.LGPLv2.1`. The app itself stays CC BY-NC 4.0. This module is not published to Maven.

`libfsplayer.so` is linked dynamically (`libfsplayer.so` plus `libsmb2.so`) for `arm64-v8a` and `armeabi-v7a`. `libsmb2.so` is the FFToolChain prebuilt of libsmb2 and is only pulled in because the upstream Android link line needs it; playback does not require an SMB server.

## Passthrough

Stock FSPlayer writes PCM. The patch in this tree copies AC-3 / E-AC-3 access units to `AudioTrack` instead of decoding them:

- `ijkmedia/ijkplayer/fs_passthrough.c` — codec, JOC, speed, and fallback decision
- `ijkmedia/ijkplayer/ff_ffplay.c` — `fs_passthrough_take_packet`, `audio_open`, `audio_decode_frame`
- `ijkmedia/ijksdl/android/android_audiotrack.c` — `ENCODING_AC3` (5), `ENCODING_E_AC3` (6), `ENCODING_E_AC3_JOC` (18)
- `android/fsplayer/src/main/java/com/debugly/fsplayer/audio/FsEncodedAudioTrack.java` — `AudioTrack.Builder` with usage media and content type movie

TrueHD is not passed through. If the encoded track cannot be opened, playback falls back to 16-bit PCM and `FFP_PROP_INT64_AUDIO_PASSTHROUGH_FALLBACK` is set. Playback speed other than 1.0 opens the PCM path, because encoded `AudioTrack` does not time-stretch. Returning to 1.0 reopens the stream with passthrough.

Options: `audio-passthrough` (default 1), `mediacodec-audio-passthrough` (same flag), `audio-passthrough-joc`.

## Rebuild

Native rebuild needs the NDK 27.3.13750724 and the FFToolChain prebuilts (FFmpeg 8). From this directory:

```sh
git clone --depth 1 https://github.com/debugly/MRFFToolChainBuildShell.git FFToolChain
./FFToolChain/main.sh install -p android -l 'ass ffmpeg8'
# openssl-1.1.1w.zip may 404; ffmpeg8 already contains openssl.
./build-android.sh
```

`build-android.sh` writes `libfsplayer.so` into `android/fsplayer/src/main/jniLibs`. The host decision test does not need the NDK:

```sh
gcc -Wall -Wextra -Werror -Iijkmedia/ijkplayer -o /tmp/fs_passthrough_test passthrough-test/fs_passthrough_test.c
/tmp/fs_passthrough_test
```
