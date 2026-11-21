#include "verge_vendor.h"

#include <stdio.h>

static int expect(int actual, int wanted, const char *label)
{
    if (actual == wanted)
        return 0;
    fprintf(stderr, "FAIL %s actual=%d wanted=%d\n", label, actual, wanted);
    return 1;
}

int main(void)
{
    int failed = 0;
    failed += expect(verge_vendor_wants_gles("rtd1619dr", "exynos", "Google", NULL), 1, "rtd hardware");
    failed += expect(verge_vendor_wants_gles("amlogic", "rtd1619", "Droid", NULL), 1, "rtd board");
    failed += expect(verge_vendor_wants_gles("Realtek", "meson", "TCL", NULL), 1, "realtek hardware");
    failed += expect(verge_vendor_wants_gles("amlogic", "g12a", "Dune HD", NULL), 1, "dune manufacturer");
    failed += expect(verge_vendor_wants_gles("thor", "mjolnir", "Realtek", "tv794a"), 1, "tv794a");
    failed += expect(verge_vendor_wants_gles("thor", "meson", NULL, NULL), 1, "thor board name");
    failed += expect(verge_vendor_wants_gles(NULL, "mjolnir", NULL, NULL), 1, "mjolnir");
    failed += expect(verge_vendor_wants_gles(NULL, NULL, "Realtek", NULL), 1, "realtek manufacturer");
    failed += expect(verge_vendor_wants_gles(NULL, NULL, NULL, "tv794a"), 1, "model only");
    failed += expect(verge_vendor_wants_gles("qcom", "msm8998", "Sony", "Pixel"), 0, "unrelated");
    failed += expect(verge_vendor_wants_gles(NULL, NULL, NULL, NULL), 0, "null");
    failed += expect(verge_vendor_wants_gles("dune", "box", "Generic", NULL), 0, "dune is not a chip id");
    failed += expect(verge_vendor_wants_gles("authority", "box", "Generic", NULL), 0, "thor is not a substring");
    return failed ? 1 : 0;
}
