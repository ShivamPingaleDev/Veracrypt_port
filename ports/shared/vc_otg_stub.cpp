/*
 Copyright (c) 2026 Shivam Mangesh Pingale. All rights reserved.

 iOS / master-merge stub: no whole-disk USB slots. File::Open never
 treats /vcport-otg-dev/N as a device.
*/

#include "vc_otg_dev.h"

void vc_otg_set_backend (const VcOtgBackend *backend)
{
	(void) backend;
}

int vc_otg_is_path (const char *path)
{
	(void) path;
	return 0;
}

int vc_otg_slot (const char *path)
{
	(void) path;
	return -1;
}

int vc_otg_is_fd (int fd)
{
	(void) fd;
	return 0;
}

int vc_otg_slot_from_fd (int fd)
{
	(void) fd;
	return -1;
}

int vc_otg_fake_fd (int slot)
{
	(void) slot;
	return -1;
}

int vc_otg_ready (int slot)
{
	(void) slot;
	return 0;
}

int vc_otg_read_at (int slot, uint64_t offset, void *buffer, size_t size)
{
	(void) slot;
	(void) offset;
	(void) buffer;
	(void) size;
	return -1;
}

int vc_otg_write_at (int slot, uint64_t offset, const void *buffer, size_t size)
{
	(void) slot;
	(void) offset;
	(void) buffer;
	(void) size;
	return -1;
}

int64_t vc_otg_size (int slot)
{
	(void) slot;
	return -1;
}

int vc_otg_sector_size (int slot)
{
	(void) slot;
	return 512;
}

void vc_otg_seek (int slot, uint64_t position)
{
	(void) slot;
	(void) position;
}

uint64_t vc_otg_tell (int slot)
{
	(void) slot;
	return 0;
}
