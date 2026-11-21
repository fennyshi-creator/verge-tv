#include "verge_vendor.h"

#include <ctype.h>
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

static int chip_match(const char *value)
{
    return contains_ci(value, "rtd1619") || contains_ci(value, "realtek");
}

int verge_vendor_wants_gles(const char *hardware, const char *board_platform, const char *manufacturer)
{
    if (chip_match(hardware) || chip_match(board_platform))
        return 1;
    return contains_ci(manufacturer, "dune");
}

#ifndef VERGE_VENDOR_HOST
int verge_vendor_wants_gles_device(void)
{
    char hardware[PROP_VALUE_MAX];
    char board[PROP_VALUE_MAX];
    char manufacturer[PROP_VALUE_MAX];

    hardware[0] = '\0';
    board[0] = '\0';
    manufacturer[0] = '\0';
    __system_property_get("ro.hardware", hardware);
    __system_property_get("ro.board.platform", board);
    __system_property_get("ro.product.manufacturer", manufacturer);
    return verge_vendor_wants_gles(hardware, board, manufacturer);
}
#endif
