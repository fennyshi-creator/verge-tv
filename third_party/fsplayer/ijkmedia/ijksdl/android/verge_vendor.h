#ifndef VERGE_VENDOR_H
#define VERGE_VENDOR_H

#include <stddef.h>

/*
 * Same rule as VendorVideoRoute in the app module.
 * Manufacturer Dune or Realtek skips Vulkan.
 * Hardware, board, or model skips Vulkan for rtd1619, realtek, tv794a,
 * mjolnir, or an exact "thor" board name. Other devices stay on Vulkan.
 */
int verge_vendor_wants_gles(const char *hardware, const char *board_platform,
                            const char *manufacturer, const char *model);

/*
 * Narrower than the GLES match. Manufacturer contains realtek, and the
 * model contains tv794a, the hardware is exactly thor, or the board
 * contains mjolnir. Other Realtek devices are not included.
 */
int verge_vendor_verified_iec(const char *manufacturer, const char *hardware,
                              const char *board, const char *model);

#ifndef VERGE_VENDOR_HOST
/* detail receives "hw= platform= board= mfg= model=" when non-NULL. */
int verge_vendor_wants_gles_device(char *detail, size_t detail_cap);
int verge_vendor_verified_iec_device(void);
#endif

#endif
