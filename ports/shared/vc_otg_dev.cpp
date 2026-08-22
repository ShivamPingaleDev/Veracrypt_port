/*
 Copyright (c) 2026 Shivam Mangesh Pingale. All rights reserved.

 Governed by the Apache License 2.0 the full text of which is
 contained in the file License.txt included in VeraCrypt binary and source
 code distribution packages.
*/

#include "vc_otg_dev.h"

#include <cstring>
#include <mutex>
#include <string>

static VcOtgBackend g_backend = {};
static uint64_t g_pos[VC_OTG_MAX_SLOTS];
static std::mutex g_pos_lock;

void vc_otg_set_backend (const VcOtgBackend *backend)
{
	if (backend)
		g_backend = *backend;
	else
		g_backend = {};
}

int vc_otg_is_path (const char *path)
{
	if (!path)
		return 0;
	return std::strncmp (path, VC_OTG_PATH_PREFIX, sizeof (VC_OTG_PATH_PREFIX) - 1) == 0 ? 1 : 0;
}

int vc_otg_slot (const char *path)
{
	if (!vc_otg_is_path (path))
		return -1;
	const char *n = path + (sizeof (VC_OTG_PATH_PREFIX) - 1);
	if (n[0] < '0' || n[0] > '7' || n[1] != 0)
		return -1;
	return n[0] - '0';
}

int vc_otg_is_fd (int fd)
{
	return fd <= VC_OTG_FD_BASE && fd > VC_OTG_FD_BASE - VC_OTG_MAX_SLOTS;
}

int vc_otg_slot_from_fd (int fd)
{
	if (!vc_otg_is_fd (fd))
		return -1;
	return VC_OTG_FD_BASE - fd;
}

int vc_otg_fake_fd (int slot)
{
	if (slot < 0 || slot >= VC_OTG_MAX_SLOTS)
		return -1;
	return VC_OTG_FD_BASE - slot;
}

int vc_otg_ready (int slot)
{
	if (slot < 0 || slot >= VC_OTG_MAX_SLOTS || !g_backend.ready)
		return 0;
	return g_backend.ready (slot);
}

int vc_otg_read_at (int slot, uint64_t offset, void *buffer, size_t size)
{
	if (!g_backend.read_at || slot < 0 || slot >= VC_OTG_MAX_SLOTS)
		return -1;
	return g_backend.read_at (slot, offset, buffer, size);
}

int vc_otg_write_at (int slot, uint64_t offset, const void *buffer, size_t size)
{
	if (!g_backend.write_at || slot < 0 || slot >= VC_OTG_MAX_SLOTS)
		return -1;
	return g_backend.write_at (slot, offset, buffer, size);
}

int64_t vc_otg_size (int slot)
{
	if (!g_backend.size || slot < 0 || slot >= VC_OTG_MAX_SLOTS)
		return -1;
	return g_backend.size (slot);
}

int vc_otg_sector_size (int slot)
{
	if (!g_backend.sector_size || slot < 0 || slot >= VC_OTG_MAX_SLOTS)
		return 512;
	int n = g_backend.sector_size (slot);
	return n > 0 ? n : 512;
}

void vc_otg_seek (int slot, uint64_t position)
{
	if (slot < 0 || slot >= VC_OTG_MAX_SLOTS)
		return;
	std::lock_guard<std::mutex> lock (g_pos_lock);
	g_pos[slot] = position;
}

uint64_t vc_otg_tell (int slot)
{
	if (slot < 0 || slot >= VC_OTG_MAX_SLOTS)
		return 0;
	std::lock_guard<std::mutex> lock (g_pos_lock);
	return g_pos[slot];
}
