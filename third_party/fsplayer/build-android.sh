#!/bin/sh
# Rebuild libfsplayer.so for arm64-v8a and armeabi-v7a.
# Requires ANDROID_NDK_HOME (NDK 27.3.13750724) and ./FFToolChain prebuilts.
set -eu
cd "$(dirname "$0")"
NDK="${ANDROID_NDK_HOME:-${ANDROID_HOME:-$HOME/android-sdk}/ndk/27.3.13750724}"
if [ ! -x "$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/clang" ]; then
    echo "NDK not found at $NDK" >&2
    exit 1
fi
STRIP="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
build_one() {
    abi="$1"
    out="$(pwd)/.build-$abi"
    rm -rf "$out"
    cmake -S android/fsplayer/src/main/cpp -B "$out" \
        -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="$abi" \
        -DANDROID_PLATFORM=android-24 \
        -DANDROID_NDK="$NDK" \
        -DCMAKE_BUILD_TYPE=Release
    cmake --build "$out" -j"$(nproc)"
    dest="android/fsplayer/src/main/jniLibs/$abi"
    mkdir -p "$dest"
    cp "$out/libfsplayer.so" "$dest/libfsplayer.so"
    "$STRIP" --strip-unneeded "$dest/libfsplayer.so"
    rm -rf "$out"
}
build_one arm64-v8a
build_one armeabi-v7a
echo "libfsplayer.so updated"
