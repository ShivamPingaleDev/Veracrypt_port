/*
 Copyright (c) 2026 Shivam Mangesh Pingale. All rights reserved.

 Governed by the Apache License 2.0 the full text of which is
 contained in the file License.txt included in VeraCrypt binary and source
 code distribution packages.

 Experimental USB whole-disk path for File::Open. Not /proc/self/fd.
 Inspired by OTG Master (moylali, https://github.com/moylali/OTGMaster)
 without copying that GPL tree.
*/

#ifndef VC_OTG_DEV_H
#define VC_OTG_DEV_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define VC_OTG_PATH_PREFIX "/vcport-otg-dev/"
#define VC_OTG_FD_BASE (-2000)
#define VC_OTG_MAX_SLOTS 8

typedef struct VcOtgBackend
{
	int (*read_at) (int slot, uint64_t offset, void *buffer, size_t size);
	int (*write_at) (int slot, uint64_t offset, const void *buffer, size_t size);
	int64_t (*size) (int slot);
	int (*sector_size) (int slot);
	int (*ready) (int slot);
} VcOtgBackend;

void vc_otg_set_backend (const VcOtgBackend *backend);
int vc_otg_is_path (const char *path);
int vc_otg_slot (const char *path);
int vc_otg_is_fd (int fd);
int vc_otg_slot_from_fd (int fd);
int vc_otg_fake_fd (int slot);
int vc_otg_ready (int slot);
int vc_otg_read_at (int slot, uint64_t offset, void *buffer, size_t size);
int vc_otg_write_at (int slot, uint64_t offset, const void *buffer, size_t size);
int64_t vc_otg_size (int slot);
int vc_otg_sector_size (int slot);
void vc_otg_seek (int slot, uint64_t position);
uint64_t vc_otg_tell (int slot);

#ifdef __cplusplus
}
#endif

#endif
