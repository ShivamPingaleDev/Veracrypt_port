/*
 Copyright (c) 2026 Shivam Mangesh Pingale. All rights reserved.

 Governed by the Apache License 2.0 the full text of which is
 contained in the file License.txt included in VeraCrypt binary and source
 code distribution packages.
*/

#ifndef VC_EXFAT_H
#define VC_EXFAT_H

#include "vc_mobile.h"

#ifdef __cplusplus
extern "C" {
#endif

/* 1 = exFAT boot OEM, 0 = not exFAT, <0 = I/O error. */
int vc_exfat_probe (VcVolume *volume);
int vc_exfat_format (VcVolume *volume, uint64_t data_bytes);

int vc_exfat_list_dir_from (VcVolume *volume, const char *path, VcDirEntry *entries, int max_entries, int skip);
int vc_exfat_export (VcVolume *volume, const char *path, const char *dest_path);
int vc_exfat_read_file (VcVolume *volume, const char *path, void *buffer, size_t buffer_size, size_t *out_size);
int vc_exfat_import (VcVolume *volume, const char *dest_dir, const char *src_path, const char *dest_name);
int vc_exfat_delete (VcVolume *volume, const char *path);
int vc_exfat_mkdir (VcVolume *volume, const char *parent_dir, const char *name);
int vc_exfat_rmdir (VcVolume *volume, const char *path);
int vc_exfat_rename (VcVolume *volume, const char *path, const char *new_name);
int vc_exfat_wipe_free (VcVolume *volume);

#ifdef __cplusplus
}
#endif

#endif
