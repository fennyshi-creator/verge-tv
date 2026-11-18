#include <dlfcn.h>
#include <jni.h>
#include <stdbool.h>
#include <stdint.h>
#include <string.h>

/*
 * libfsplayer configures MediaCodec through the NDK AMediaCodec_configure
 * import. This library is DT_NEEDED ahead of libmediandk.so, so that call
 * lands here. When SDR mapping is armed, the same AMediaFormat receives
 * color-transfer-request = COLOR_TRANSFER_SDR_VIDEO (3) before the real
 * configure. Audio configures are left alone.
 */
struct AMediaCodec;
struct AMediaFormat;
struct ANativeWindow;
struct AMediaCrypto;

typedef int media_status_t;
typedef media_status_t (*configure_fn)(
    struct AMediaCodec *,
    const struct AMediaFormat *,
    struct ANativeWindow *,
    struct AMediaCrypto *,
    uint32_t);
typedef bool (*get_string_fn)(struct AMediaFormat *, const char *, const char **);
typedef void (*set_int32_fn)(struct AMediaFormat *, const char *, int32_t);

static volatile int request_sdr = 0;
static configure_fn real_configure_fn = NULL;
static get_string_fn format_get_string = NULL;
static set_int32_fn format_set_int32 = NULL;

static void resolve(void) {
    if (real_configure_fn) return;
    void *mediandk = dlopen("libmediandk.so", RTLD_NOW);
    if (!mediandk) return;
    real_configure_fn = (configure_fn) dlsym(mediandk, "AMediaCodec_configure");
    format_get_string = (get_string_fn) dlsym(mediandk, "AMediaFormat_getString");
    format_set_int32 = (set_int32_fn) dlsym(mediandk, "AMediaFormat_setInt32");
}

static bool is_video(struct AMediaFormat *format) {
    const char *mime = NULL;
    if (!format_get_string || !format_get_string(format, "mime", &mime) || !mime) return false;
    return strncmp(mime, "video/", 6) == 0;
}

__attribute__((visibility("default")))
media_status_t AMediaCodec_configure(
    struct AMediaCodec *codec,
    const struct AMediaFormat *format,
    struct ANativeWindow *surface,
    struct AMediaCrypto *crypto,
    uint32_t flags) {
    resolve();
    if (!real_configure_fn) return (media_status_t) -1;
    if (request_sdr && format && is_video((struct AMediaFormat *) format)) {
        format_set_int32((struct AMediaFormat *) format, "color-transfer-request", 3);
    }
    return real_configure_fn(codec, format, surface, crypto, flags);
}

JNIEXPORT jboolean JNICALL
Java_com_xxxx_emby_1tv_ui_player_HdrToneMap_nativeLoaded(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_xxxx_emby_1tv_ui_player_HdrToneMap_nativeSetRequestSdr(JNIEnv *env, jclass clazz, jboolean on) {
    (void) env;
    (void) clazz;
    request_sdr = on ? 1 : 0;
}
