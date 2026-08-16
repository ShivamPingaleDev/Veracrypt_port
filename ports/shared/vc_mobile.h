/*
 Copyright (c) 2026 Shivam Pingale. All rights reserved.

 Governed by the Apache License 2.0 the full text of which is
 contained in the file License.txt included in VeraCrypt binary and source
 code distribution packages.
*/

#ifndef VC_MOBILE_H
#define VC_MOBILE_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct VcVolume VcVolume;

enum VcError
{
	VC_OK = 0,
	VC_ERR_IO = -1,
	VC_ERR_PASSWORD = -2,
	VC_ERR_FORMAT = -3,
	VC_ERR_ARGUMENT = -4,
	VC_ERR_MEMORY = -5,
	VC_ERR_UNSUPPORTED = -6
};

typedef struct VcOpenOptions
{
	const char *path;
	const char *password;
	size_t password_len;
	int pim;
	int use_backup_header;
	const char *const *keyfiles;
	size_t keyfile_count;
} VcOpenOptions;

VcVolume *vc_open (const VcOpenOptions *options, int *error);
void vc_close (VcVolume *volume);
uint64_t vc_size (VcVolume *volume);
uint32_t vc_sector_size (VcVolume *volume);
int vc_read (VcVolume *volume, uint64_t offset, void *buffer, size_t size);
int vc_write (VcVolume *volume, uint64_t offset, const void *buffer, size_t size);

typedef struct VcDirEntry
{
	char name[256];
	uint8_t is_dir;
	uint64_t size;
	uint32_t first_cluster;
} VcDirEntry;

int vc_list_root (VcVolume *volume, VcDirEntry *entries, int max_entries);
int vc_read_file (VcVolume *volume, const char *path, void *buffer, size_t buffer_size, size_t *out_size);
int vc_export_file (VcVolume *volume, const char *path, const char *dest_path);

#ifdef __cplusplus
}
#endif

#endif
