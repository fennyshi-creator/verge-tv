#ifndef VERGE_VENDOR_H
#define VERGE_VENDOR_H

/*
 * Dune / Realtek RTD1619 players skip Vulkan. The strings are the same
 * rule as VendorVideoRoute in the app module.
 * hardware and board_platform match rtd1619 or realtek.
 * manufacturer matches Dune. Other devices stay on Vulkan.
 */
int verge_vendor_wants_gles(const char *hardware, const char *board_platform, const char *manufacturer);

#ifndef VERGE_VENDOR_HOST
int verge_vendor_wants_gles_device(void);
#endif

#endif
