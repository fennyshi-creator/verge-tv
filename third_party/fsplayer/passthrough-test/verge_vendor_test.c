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
    failed += expect(verge_vendor_wants_gles("rtd1619dr", "exynos", "Google"), 1, "rtd hardware");
    failed += expect(verge_vendor_wants_gles("amlogic", "rtd1619", "Droid"), 1, "rtd board");
    failed += expect(verge_vendor_wants_gles("Realtek", "meson", "TCL"), 1, "realtek hardware");
    failed += expect(verge_vendor_wants_gles("amlogic", "g12a", "Dune HD"), 1, "dune manufacturer");
    failed += expect(verge_vendor_wants_gles("qcom", "msm8998", "Sony"), 0, "unrelated");
    failed += expect(verge_vendor_wants_gles(NULL, NULL, NULL), 0, "null");
    failed += expect(verge_vendor_wants_gles("dune", "box", "Generic"), 0, "dune is not a chip id");
    return failed ? 1 : 0;
}
