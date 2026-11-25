#include "verge_vendor.h"

#include <ctype.h>
#include <stdio.h>
#include <string.h>

#ifndef VERGE_VENDOR_HOST
#include <sys/system_properties.h>
#endif

static int contains_ci(const char *haystack, const char *needle)
{
    size_t needle_len;
    const char *cursor;

    if (!haystack || !needle || !needle[0])
        return 0;
    needle_len = strlen(needle);
    for (cursor = haystack; *cursor; cursor++) {
        size_t index;
        for (index = 0; index < needle_len; index++) {
            unsigned char left = (unsigned char)cursor[index];
            unsigned char right = (unsigned char)needle[index];
            if (!left || tolower(left) != tolower(right))
                break;
        }
        if (index == needle_len)
            return 1;
    }
    return 0;
}

static int equals_ci(const char *value, const char *token)
{
    if (!value || !token)
        return 0;
    while (*value && *token) {
        if (tolower((unsigned char)*value) != tolower((unsigned char)*token))
            return 0;
        value++;
        token++;
    }
    return *value == '\0' && *token == '\0';
}

static int vendor_name(const char *value)
{
    return contains_ci(value, "dune") || contains_ci(value, "realtek");
}

static int board_token(const char *value)
{
    return contains_ci(value, "rtd1619")
        || contains_ci(value, "realtek")
        || contains_ci(value, "tv794a")
        || contains_ci(value, "mjolnir")
        || equals_ci(value, "thor");
}

int verge_vendor_wants_gles(const char *hardware, const char *board_platform,
                            const char *manufacturer, const char *model)
{
    if (vendor_name(manufacturer))
        return 1;
    return board_token(hardware) || board_token(board_platform) || board_token(model);
}

int verge_vendor_verified_iec(const char *manufacturer, const char *hardware,
                              const char *board, const char *model)
{
    if (!contains_ci(manufacturer, "realtek"))
        return 0;
    if (contains_ci(model, "tv794a"))
        return 1;
    if (equals_ci(hardware, "thor"))
        return 1;
    if (contains_ci(board, "mjolnir"))
        return 1;
    return 0;
}

#ifndef VERGE_VENDOR_HOST
static void read_prop(const char *key, char *out, size_t cap)
{
    if (!out || cap == 0)
        return;
    out[0] = '\0';
    __system_property_get(key, out);
    out[cap - 1] = '\0';
}

int verge_vendor_wants_gles_device(char *detail, size_t detail_cap)
{
    char hardware[PROP_VALUE_MAX];
    char platform[PROP_VALUE_MAX];
    char board[PROP_VALUE_MAX];
    char manufacturer[PROP_VALUE_MAX];
    char model[PROP_VALUE_MAX];
    char brand[PROP_VALUE_MAX];
    char device[PROP_VALUE_MAX];
    int match;

    read_prop("ro.hardware", hardware, sizeof(hardware));
    read_prop("ro.board.platform", platform, sizeof(platform));
    read_prop("ro.product.board", board, sizeof(board));
    read_prop("ro.product.manufacturer", manufacturer, sizeof(manufacturer));
    read_prop("ro.product.model", model, sizeof(model));
    read_prop("ro.product.brand", brand, sizeof(brand));
    read_prop("ro.product.device", device, sizeof(device));
    match = verge_vendor_wants_gles(hardware, platform, manufacturer, model)
        || verge_vendor_wants_gles(hardware, board, brand, device);
    if (detail && detail_cap > 0) {
        snprintf(detail, detail_cap,
                 "hw=%s platform=%s board=%s mfg=%s model=%s brand=%s device=%s",
                 hardware, platform, board, manufacturer, model, brand, device);
        detail[detail_cap - 1] = '\0';
    }
    return match;
}

int verge_vendor_verified_iec_device(void)
{
    char hardware[PROP_VALUE_MAX];
    char board[PROP_VALUE_MAX];
    char manufacturer[PROP_VALUE_MAX];
    char model[PROP_VALUE_MAX];

    read_prop("ro.hardware", hardware, sizeof(hardware));
    read_prop("ro.product.board", board, sizeof(board));
    read_prop("ro.product.manufacturer", manufacturer, sizeof(manufacturer));
    read_prop("ro.product.model", model, sizeof(model));
    return verge_vendor_verified_iec(manufacturer, hardware, board, model);
}
#endif
