/*
 Copyright (c) 2026 Shivam Mangesh Pingale. All rights reserved.

 Governed by the Apache License 2.0 the full text of which is
 contained in the file License.txt included in VeraCrypt binary and source
 code distribution packages.
*/

#include "vc_mobile.h"

#include "Volume/Volume.h"
#include "Volume/Keyfile.h"
#include "Volume/EncryptionAlgorithm.h"
#include "Volume/EncryptionModeXTS.h"
#include "Volume/Pkcs5Kdf.h"
#include "Volume/VolumeHeader.h"
#include "Volume/VolumeLayout.h"
#include "Volume/VolumePassword.h"
#include "Volume/EncryptionThreadPool.h"
#include "Volume/EncryptionTest.h"
#include "Platform/File.h"
#include "Platform/Buffer.h"
#include "Platform/StringConverter.h"
#include "Platform/Mutex.h"
#include "Crypto/Sha2.h"
#include "Common/Volumes.h"

#include <chrono>
#include <cstdio>
#include <cstring>
#include <ctime>
#include <fcntl.h>
#include <memory>
#include <strings.h>
#include <string>
#include <unistd.h>
#include <vector>
#ifdef __APPLE__
#include <stdlib.h>
#endif

using namespace VeraCrypt;

struct VcVolume
{
	shared_ptr <Volume> volume;
	string path;
	int read_only;
};

static shared_ptr <KeyfileList> MakeKeyfilesFrom (const char *const *keyfiles, size_t count)
{
	if (!keyfiles || count == 0)
		return shared_ptr <KeyfileList> ();

	shared_ptr <KeyfileList> list (new KeyfileList);
	for (size_t i = 0; i < count; ++i)
	{
		if (keyfiles[i] && keyfiles[i][0])
			list->push_back (make_shared <Keyfile> (wstring (keyfiles[i], keyfiles[i] + strlen (keyfiles[i]))));
	}
	return list;
}

static shared_ptr <KeyfileList> MakeKeyfiles (const VcOpenOptions *options)
{
	if (!options)
		return shared_ptr <KeyfileList> ();
	return MakeKeyfilesFrom (options->keyfiles, options->keyfile_count);
}

VcVolume *vc_open (const VcOpenOptions *options, int *error)
{
	if (error)
		*error = VC_OK;
	if (!options || !options->path)
	{
		if (error)
			*error = VC_ERR_ARGUMENT;
		return nullptr;
	}

	try
	{
		vc_progress_set (-1, "Unlocking");
		const char *pw = options->password ? options->password : "";
		size_t pwLen = options->password_len;
		if (!pwLen && options->password)
			pwLen = strlen (options->password);
		shared_ptr <VolumePassword> password (new VolumePassword (
			reinterpret_cast <const uint8 *> (pw), pwLen));

		shared_ptr <Volume> volume (new Volume);
		if (!EncryptionThreadPool::IsRunning ())
			EncryptionThreadPool::Start ();

		volume->Open (
			VolumePath (wstring (options->path, options->path + strlen (options->path))),
			true,
			password,
			options->pim,
			shared_ptr <Pkcs5Kdf> (),
			MakeKeyfiles (options),
			false,
			options->read_only ? VolumeProtection::ReadOnly : VolumeProtection::None,
			shared_ptr <VolumePassword> (),
			0,
			shared_ptr <Pkcs5Kdf> (),
			shared_ptr <KeyfileList> (),
			false,
			VolumeType::Unknown,
			options->use_backup_header != 0,
			false);

		VcVolume *handle = new VcVolume;
		handle->volume = volume;
		handle->path = options->path;
		handle->read_only = options->read_only != 0;
		return handle;
	}
	catch (PasswordException &)
	{
		if (error)
			*error = VC_ERR_PASSWORD;
	}
	catch (SystemException &)
	{
		if (error)
			*error = VC_ERR_IO;
	}
	catch (...)
	{
		if (error)
			*error = VC_ERR_FORMAT;
	}
	return nullptr;
}

void vc_close (VcVolume *volume)
{
	delete volume;
}

uint64_t vc_size (VcVolume *volume)
{
	return volume && volume->volume ? volume->volume->GetSize () : 0;
}

uint32_t vc_sector_size (VcVolume *volume)
{
	return volume && volume->volume ? (uint32_t) volume->volume->GetSectorSize () : 512;
}

int vc_read (VcVolume *volume, uint64_t offset, void *buffer, size_t size)
{
	if (!volume || !volume->volume || !buffer)
		return VC_ERR_ARGUMENT;
	if (size == 0)
		return VC_OK;
	try
	{
		size_t sector = volume->volume->GetSectorSize ();
		if (sector == 0)
			return VC_ERR_FORMAT;
		uint64_t alignedOffset = offset - (offset % sector);
		size_t prefix = (size_t) (offset - alignedOffset);
		size_t total = prefix + size;
		total = ((total + sector - 1) / sector) * sector;
		if (prefix == 0 && total == size)
		{
			BufferPtr view (static_cast<uint8 *> (buffer), size);
			volume->volume->ReadSectors (view, alignedOffset);
			return VC_OK;
		}
		SecureBuffer buf (total);
		volume->volume->ReadSectors (buf, alignedOffset);
		memcpy (buffer, buf.Ptr () + prefix, size);
		return VC_OK;
	}
	catch (...)
	{
		return VC_ERR_IO;
	}
}

int vc_write (VcVolume *volume, uint64_t offset, const void *buffer, size_t size)
{
	if (!volume || !volume->volume || !buffer)
		return VC_ERR_ARGUMENT;
	if (size == 0)
		return VC_OK;
	try
	{
		size_t sector = volume->volume->GetSectorSize ();
		if (sector == 0)
			return VC_ERR_FORMAT;
		uint64_t alignedOffset = offset - (offset % sector);
		size_t prefix = (size_t) (offset - alignedOffset);
		size_t total = prefix + size;
		total = ((total + sector - 1) / sector) * sector;
		if (prefix == 0 && total == size)
		{
			volume->volume->WriteSectors (
				ConstBufferPtr (static_cast<const uint8 *> (buffer), size), alignedOffset);
			return VC_OK;
		}
		SecureBuffer buf (total);
		volume->volume->ReadSectors (buf, alignedOffset);
		memcpy (buf.Ptr () + prefix, buffer, size);
		volume->volume->WriteSectors (buf, alignedOffset);
		return VC_OK;
	}
	catch (...)
	{
		return VC_ERR_IO;
	}
}

static uint16_t u16le (const uint8_t *p)
{
	return (uint16_t) (p[0] | (p[1] << 8));
}

static uint32_t u32le (const uint8_t *p)
{
	return (uint32_t) p[0] | ((uint32_t) p[1] << 8) | ((uint32_t) p[2] << 16) | ((uint32_t) p[3] << 24);
}

static void put16 (uint8_t *p, uint16_t v)
{
	p[0] = (uint8_t) v;
	p[1] = (uint8_t) (v >> 8);
}

static void put32 (uint8_t *p, uint32_t v)
{
	p[0] = (uint8_t) v;
	p[1] = (uint8_t) (v >> 8);
	p[2] = (uint8_t) (v >> 16);
	p[3] = (uint8_t) (v >> 24);
}

struct FatGeom
{
	uint32_t bytes_per_sector;
	uint32_t sectors_per_cluster;
	uint32_t cluster_size;
	uint32_t reserved;
	uint32_t fats;
	uint32_t root_entries;
	uint32_t fat_size_sectors;
	uint32_t root_cluster;
	uint64_t fat_offset;
	uint64_t root_offset;
	uint64_t data_offset;
	int fat32;
};

static int fat_load_geom (VcVolume *volume, FatGeom *g)
{
	uint32_t sector = vc_sector_size (volume);
	if (sector < 512)
		sector = 512;
	std::vector <uint8_t> boot (sector);
	if (vc_read (volume, 0, &boot[0], sector) != VC_OK)
		return VC_ERR_IO;

	if (memcmp (&boot[3], "EXFAT   ", 8) == 0)
		return VC_ERR_UNSUPPORTED;

	g->bytes_per_sector = u16le (&boot[11]);
	g->sectors_per_cluster = boot[13];
	g->reserved = u16le (&boot[14]);
	g->fats = boot[16];
	g->root_entries = u16le (&boot[17]);
	uint16_t fat16Sectors = u16le (&boot[22]);
	uint32_t fat32Sectors = u32le (&boot[36]);
	g->root_cluster = u32le (&boot[44]);
	g->fat_size_sectors = fat16Sectors ? fat16Sectors : fat32Sectors;
	g->fat32 = g->root_entries == 0;

	if (g->bytes_per_sector == 0 || g->sectors_per_cluster == 0 || g->fats == 0)
		return VC_ERR_FORMAT;

	g->cluster_size = g->bytes_per_sector * g->sectors_per_cluster;
	g->fat_offset = (uint64_t) g->reserved * g->bytes_per_sector;
	uint64_t fatBytes = (uint64_t) g->fats * g->fat_size_sectors * g->bytes_per_sector;
	if (g->root_entries)
	{
		g->root_offset = g->fat_offset + fatBytes;
		g->data_offset = g->root_offset + (uint64_t) g->root_entries * 32;
	}
	else
	{
		g->root_offset = 0;
		g->data_offset = g->fat_offset + fatBytes;
	}
	return VC_OK;
}

static uint32_t fat_next (VcVolume *volume, const FatGeom *g, uint32_t cluster)
{
	if (g->fat32)
	{
		uint8_t b[4];
		if (vc_read (volume, g->fat_offset + (uint64_t) cluster * 4, b, 4) != VC_OK)
			return 0x0FFFFFFFu;
		return u32le (b) & 0x0FFFFFFFu;
	}
	uint8_t b[2];
	if (vc_read (volume, g->fat_offset + (uint64_t) cluster * 2, b, 2) != VC_OK)
		return 0xFFFFu;
	return u16le (b);
}

static int fat_is_eof (const FatGeom *g, uint32_t cluster)
{
	return g->fat32 ? cluster >= 0x0FFFFFF8u : cluster >= 0xFFF8u;
}

static int fat_read_chain (VcVolume *volume, const FatGeom *g, uint32_t start, std::vector <uint8_t> &out, size_t max_bytes)
{
	out.clear ();
	uint32_t cluster = start;
	int hops = 0;
	while (cluster >= 2 && !fat_is_eof (g, cluster) && hops++ < 1 << 20 && out.size () < max_bytes)
	{
		uint64_t offset = g->data_offset + (uint64_t) (cluster - 2) * g->cluster_size;
		size_t n = g->cluster_size;
		if (out.size () + n > max_bytes)
			n = max_bytes - out.size ();
		size_t at = out.size ();
		out.resize (at + n);
		if (vc_read (volume, offset, &out[at], n) != VC_OK)
			return VC_ERR_IO;
		cluster = fat_next (volume, g, cluster);
	}
	return VC_OK;
}

static int fat_load_root (VcVolume *volume, const FatGeom *g, std::vector <uint8_t> &dir)
{
	if (!g->fat32)
	{
		dir.resize ((size_t) g->root_entries * 32);
		return vc_read (volume, g->root_offset, &dir[0], dir.size ());
	}
	return fat_read_chain (volume, g, g->root_cluster, dir, 8 * 1024 * 1024);
}

static size_t utf16le_to_utf8 (const uint16_t *src, int count, char *out, size_t out_size)
{
	size_t o = 0;
	if (!out_size)
		return 0;
	for (int i = 0; i < count && src[i] && src[i] != 0xFFFFu; ++i)
	{
		uint32_t c = src[i];
		if (c < 0x80)
		{
			if (o + 1 >= out_size)
				break;
			out[o++] = (char) c;
		}
		else if (c < 0x800)
		{
			if (o + 2 >= out_size)
				break;
			out[o++] = (char) (0xC0 | (c >> 6));
			out[o++] = (char) (0x80 | (c & 0x3F));
		}
		else
		{
			if (o + 3 >= out_size)
				break;
			out[o++] = (char) (0xE0 | (c >> 12));
			out[o++] = (char) (0x80 | ((c >> 6) & 0x3F));
			out[o++] = (char) (0x80 | (c & 0x3F));
		}
	}
	out[o] = 0;
	return o;
}

static void lfn_piece (const uint8_t *e, uint16_t *out13)
{
	int n = 0;
	for (int k = 0; k < 5; ++k)
		out13[n++] = u16le (e + 1 + k * 2);
	for (int k = 0; k < 6; ++k)
		out13[n++] = u16le (e + 14 + k * 2);
	for (int k = 0; k < 2; ++k)
		out13[n++] = u16le (e + 28 + k * 2);
}

static int fat_parse_dir (const uint8_t *dir, size_t bytes, VcDirEntry *entries, int max_entries, int skip)
{
	uint16_t lfn[260];
	memset (lfn, 0, sizeof (lfn));
	int count = 0;
	int seen = 0;
	if (skip < 0)
		skip = 0;
	for (size_t i = 0; i + 32 <= bytes && count < max_entries; i += 32)
	{
		uint8_t first = dir[i];
		if (first == 0)
			break;
		if (first == 0xE5)
		{
			memset (lfn, 0, sizeof (lfn));
			continue;
		}

		uint8_t attr = dir[i + 11];
		if (attr == 0x0F)
		{
			int seq = first & 0x3F;
			if (seq == 0 || seq > 20)
			{
				memset (lfn, 0, sizeof (lfn));
				continue;
			}
			if (first & 0x40)
				memset (lfn, 0, sizeof (lfn));
			uint16_t piece[13];
			lfn_piece (&dir[i], piece);
			memcpy (lfn + (seq - 1) * 13, piece, sizeof (piece));
			continue;
		}
		if (attr & 0x08)
		{
			memset (lfn, 0, sizeof (lfn));
			continue;
		}
		if (dir[i] == '.')
		{
			memset (lfn, 0, sizeof (lfn));
			continue;
		}

		char name[256];
		memset (name, 0, sizeof (name));
		if (lfn[0])
			utf16le_to_utf8 (lfn, 260, name, sizeof (name));
		if (!name[0])
		{
			char base[9];
			char ext[4];
			memset (base, 0, sizeof (base));
			memset (ext, 0, sizeof (ext));
			memcpy (base, &dir[i], 8);
			memcpy (ext, &dir[i + 8], 3);
			for (int n = 7; n >= 0 && base[n] == ' '; --n)
				base[n] = 0;
			for (int n = 2; n >= 0 && ext[n] == ' '; --n)
				ext[n] = 0;
			strncpy (name, base, sizeof (name) - 1);
			if (ext[0])
			{
				strncat (name, ".", sizeof (name) - strlen (name) - 1);
				strncat (name, ext, sizeof (name) - strlen (name) - 1);
			}
		}

		if (seen++ < skip)
		{
			memset (lfn, 0, sizeof (lfn));
			continue;
		}

		memset (entries[count].name, 0, sizeof (entries[count].name));
		strncpy (entries[count].name, name, sizeof (entries[count].name) - 1);
		entries[count].is_dir = (attr & 0x10) ? 1 : 0;
		entries[count].size = u32le (&dir[i + 28]);
		entries[count].first_cluster = u16le (&dir[i + 26]) | ((uint32_t) u16le (&dir[i + 20]) << 16);
		entries[count].dos_time = u16le (&dir[i + 22]);
		entries[count].dos_date = u16le (&dir[i + 24]);
		++count;
		memset (lfn, 0, sizeof (lfn));
	}
	return count;
}

static int fat_load_dir (VcVolume *volume, const FatGeom *g, uint32_t cluster, std::vector <uint8_t> &dir)
{
	if (!g->fat32 && cluster < 2)
		return fat_load_root (volume, g, dir);
	if (g->fat32 && cluster < 2)
		cluster = g->root_cluster;
	return fat_read_chain (volume, g, cluster, dir, 8 * 1024 * 1024);
}

static int fat_is_root_path (const char *path)
{
	if (!path || !path[0])
		return 1;
	while (*path == '/' || *path == '\\')
		++path;
	return *path == 0;
}

static const char *fat_next_component (const char *path, char *out, size_t out_size)
{
	while (*path == '/' || *path == '\\')
		++path;
	if (!*path)
		return nullptr;
	size_t n = 0;
	while (path[n] && path[n] != '/' && path[n] != '\\')
		++n;
	if (n == 0 || n >= out_size)
		return nullptr;
	memcpy (out, path, n);
	out[n] = 0;
	if (strcmp (out, ".") == 0 || strcmp (out, "..") == 0)
		return nullptr;
	return path + n;
}

static int fat_find_path (VcVolume *volume, const char *path, VcDirEntry *found)
{
	if (!path || !found)
		return VC_ERR_ARGUMENT;
	if (fat_is_root_path (path))
		return VC_ERR_UNSUPPORTED;

	FatGeom geom;
	int rc = fat_load_geom (volume, &geom);
	if (rc != VC_OK)
		return rc;

	uint32_t cluster = 0;
	const char *cursor = path;
	char name[256];
	for (;;)
	{
		const char *next = fat_next_component (cursor, name, sizeof (name));
		if (!next)
			return VC_ERR_ARGUMENT;
		std::vector <uint8_t> dir;
		rc = fat_load_dir (volume, &geom, cluster, dir);
		if (rc != VC_OK)
			return rc;
		if (dir.empty ())
			return VC_ERR_IO;
		int cap = (int) (dir.size () / 32);
		if (cap < 1)
			cap = 1;
		if (cap > 32768)
			cap = 32768;
		std::vector <VcDirEntry> entries ((size_t) cap);
		int n = fat_parse_dir (&dir[0], dir.size (), &entries[0], cap, 0);
		int hit = -1;
		for (int i = 0; i < n; ++i)
		{
			if (strcasecmp (entries[i].name, name) == 0)
			{
				hit = i;
				break;
			}
		}
		if (hit < 0)
			return VC_ERR_IO;
		while (*next == '/' || *next == '\\')
			++next;
		if (!*next)
		{
			*found = entries[hit];
			return VC_OK;
		}
		if (!entries[hit].is_dir)
			return VC_ERR_UNSUPPORTED;
		cluster = entries[hit].first_cluster;
		cursor = next;
	}
}

int vc_list_root (VcVolume *volume, VcDirEntry *entries, int max_entries)
{
	return vc_list_dir (volume, "/", entries, max_entries);
}

int vc_list_dir (VcVolume *volume, const char *path, VcDirEntry *entries, int max_entries)
{
	return vc_list_dir_from (volume, path, entries, max_entries, 0);
}

int vc_list_dir_from (VcVolume *volume, const char *path, VcDirEntry *entries, int max_entries, int skip)
{
	if (!volume || !entries || max_entries <= 0 || skip < 0)
		return VC_ERR_ARGUMENT;

	FatGeom geom;
	int rc = fat_load_geom (volume, &geom);
	if (rc != VC_OK)
		return rc;

	uint32_t cluster = 0;
	if (!fat_is_root_path (path))
	{
		VcDirEntry dirent;
		rc = fat_find_path (volume, path, &dirent);
		if (rc != VC_OK)
			return rc;
		if (!dirent.is_dir)
			return VC_ERR_UNSUPPORTED;
		cluster = dirent.first_cluster;
	}

	std::vector <uint8_t> dir;
	rc = fat_load_dir (volume, &geom, cluster, dir);
	if (rc != VC_OK)
		return rc;
	if (dir.empty ())
		return 0;
	return fat_parse_dir (&dir[0], dir.size (), entries, max_entries, skip);
}

static int fat_copy_file (VcVolume *volume, const VcDirEntry *entry, void *buffer, size_t buffer_size, size_t *out_size, FILE *dest)
{
	if (out_size)
		*out_size = 0;
	if (entry->is_dir)
		return VC_ERR_UNSUPPORTED;

	FatGeom geom;
	int rc = fat_load_geom (volume, &geom);
	if (rc != VC_OK)
		return rc;

	uint64_t remaining = entry->size;
	if (remaining == 0)
		return VC_OK;
	if (entry->first_cluster < 2)
		return VC_ERR_FORMAT;

	uint32_t cluster = entry->first_cluster;
	size_t written = 0;
	int hops = 0;
	std::vector <uint8_t> chunk (geom.cluster_size);
	while (remaining && cluster >= 2 && !fat_is_eof (&geom, cluster) && hops++ < 1 << 20)
	{
		uint64_t offset = geom.data_offset + (uint64_t) (cluster - 2) * geom.cluster_size;
		size_t n = remaining < geom.cluster_size ? (size_t) remaining : geom.cluster_size;
		if (vc_read (volume, offset, &chunk[0], n) != VC_OK)
			return VC_ERR_IO;
		if (dest)
		{
			if (fwrite (&chunk[0], 1, n, dest) != n)
				return VC_ERR_IO;
		}
		else if (buffer)
		{
			size_t room = buffer_size > written ? buffer_size - written : 0;
			size_t copy = n < room ? n : room;
			if (copy)
				memcpy ((uint8_t *) buffer + written, &chunk[0], copy);
			written += copy;
			if (out_size)
				*out_size = written;
			if (written >= buffer_size)
				return VC_OK;
		}
		remaining -= n;
		if (entry->size)
			vc_progress_tick ((int) (((entry->size - remaining) * 100ull) / entry->size), "Reading file");
		cluster = fat_next (volume, &geom, cluster);
	}
	if (out_size && !dest)
		*out_size = written;
	return remaining ? VC_ERR_IO : VC_OK;
}

int vc_read_file (VcVolume *volume, const char *path, void *buffer, size_t buffer_size, size_t *out_size)
{
	if (out_size)
		*out_size = 0;
	if (!volume || !path || !buffer)
		return VC_ERR_ARGUMENT;

	VcDirEntry entry;
	int rc = fat_find_path (volume, path, &entry);
	if (rc != VC_OK)
		return rc;
	return fat_copy_file (volume, &entry, buffer, buffer_size, out_size, nullptr);
}

int vc_export_file (VcVolume *volume, const char *path, const char *dest_path)
{
	if (!volume || !path || !dest_path)
		return VC_ERR_ARGUMENT;

	VcDirEntry entry;
	int rc = fat_find_path (volume, path, &entry);
	if (rc != VC_OK)
		return rc;
	if (entry.is_dir)
		return VC_ERR_UNSUPPORTED;

	FILE *out = fopen (dest_path, "wb");
	if (!out)
		return VC_ERR_IO;
	rc = fat_copy_file (volume, &entry, nullptr, 0, nullptr, out);
	if (fclose (out) != 0 && rc == VC_OK)
		rc = VC_ERR_IO;
	return rc;
}

enum { VC_IMPORT_MAX = 256 * 1024 * 1024 };

static uint32_t fat_eof_mark (const FatGeom *g)
{
	return g->fat32 ? 0x0FFFFFFFu : 0xFFFFu;
}

static uint32_t fat_max_cluster (VcVolume *volume, const FatGeom *g)
{
	uint64_t sz = vc_size (volume);
	if (sz <= g->data_offset + g->cluster_size)
		return 2;
	uint32_t clusters = (uint32_t) ((sz - g->data_offset) / g->cluster_size);
	uint32_t maxc = clusters + 1;
	if (!g->fat32 && maxc > 0xFFF4u)
		maxc = 0xFFF4u;
	if (g->fat32 && maxc > 0x0FFFFFF6u)
		maxc = 0x0FFFFFF6u;
	return maxc;
}

static int fat_poke (VcVolume *volume, const FatGeom *g, uint32_t cluster, uint32_t value)
{
	uint64_t fatBytes = (uint64_t) g->fat_size_sectors * g->bytes_per_sector;
	for (uint32_t f = 0; f < g->fats; ++f)
	{
		uint64_t base = g->fat_offset + (uint64_t) f * fatBytes;
		if (g->fat32)
		{
			uint8_t b[4];
			put32 (b, value & 0x0FFFFFFFu);
			if (vc_write (volume, base + (uint64_t) cluster * 4, b, 4) != VC_OK)
				return VC_ERR_IO;
		}
		else
		{
			uint8_t b[2];
			put16 (b, (uint16_t) value);
			if (vc_write (volume, base + (uint64_t) cluster * 2, b, 2) != VC_OK)
				return VC_ERR_IO;
		}
	}
	return VC_OK;
}

static uint32_t fat_find_free (VcVolume *volume, const FatGeom *g)
{
	uint32_t maxc = fat_max_cluster (volume, g);
	for (uint32_t c = 2; c <= maxc; ++c)
	{
		uint32_t v = fat_next (volume, g, c);
		if (v == 0)
			return c;
	}
	return 0;
}

static int fat_free_chain (VcVolume *volume, const FatGeom *g, uint32_t start)
{
	uint32_t cluster = start;
	int hops = 0;
	while (cluster >= 2 && !fat_is_eof (g, cluster) && hops++ < 1 << 20)
	{
		uint32_t next = fat_next (volume, g, cluster);
		if (fat_poke (volume, g, cluster, 0) != VC_OK)
			return VC_ERR_IO;
		cluster = next;
	}
	return VC_OK;
}

static int fat_collect_chain (VcVolume *volume, const FatGeom *g, uint32_t start, std::vector<uint32_t> &out)
{
	out.clear ();
	if (!g->fat32 && start < 2)
		return VC_OK;
	uint32_t cluster = start;
	if (g->fat32 && cluster < 2)
		cluster = g->root_cluster;
	int hops = 0;
	while (cluster >= 2 && !fat_is_eof (g, cluster) && hops++ < 1 << 20)
	{
		out.push_back (cluster);
		cluster = fat_next (volume, g, cluster);
	}
	return VC_OK;
}

static int fat_store_dir (VcVolume *volume, const FatGeom *g, uint32_t start, const std::vector<uint8_t> &dir)
{
	if (dir.empty ())
		return VC_ERR_IO;
	if (!g->fat32 && start < 2)
	{
		size_t cap = (size_t) g->root_entries * 32;
		if (dir.size () > cap)
			return VC_ERR_MEMORY;
		return vc_write (volume, g->root_offset, dir.data (), dir.size ());
	}
	std::vector<uint32_t> chain;
	int rc = fat_collect_chain (volume, g, start, chain);
	if (rc != VC_OK)
		return rc;
	if (chain.empty ())
		return VC_ERR_FORMAT;
	size_t off = 0;
	for (size_t i = 0; i < chain.size () && off < dir.size (); ++i)
	{
		size_t n = g->cluster_size;
		if (off + n > dir.size ())
			n = dir.size () - off;
		uint64_t pos = g->data_offset + (uint64_t) (chain[i] - 2) * g->cluster_size;
		if (n < g->cluster_size)
		{
			std::vector<uint8_t> pad (g->cluster_size, 0);
			memcpy (pad.data (), dir.data () + off, n);
			if (vc_write (volume, pos, pad.data (), pad.size ()) != VC_OK)
				return VC_ERR_IO;
		}
		else if (vc_write (volume, pos, dir.data () + off, n) != VC_OK)
			return VC_ERR_IO;
		off += n;
	}
	return off < dir.size () ? VC_ERR_MEMORY : VC_OK;
}

static int fat_grow_dir (VcVolume *volume, const FatGeom *g, uint32_t start, std::vector<uint8_t> &dir)
{
	if (!g->fat32 && start < 2)
		return VC_ERR_MEMORY;
	std::vector<uint32_t> chain;
	int rc = fat_collect_chain (volume, g, start, chain);
	if (rc != VC_OK)
		return rc;
	uint32_t extra = fat_find_free (volume, g);
	if (!extra)
		return VC_ERR_MEMORY;
	if (fat_poke (volume, g, extra, fat_eof_mark (g)) != VC_OK)
		return VC_ERR_IO;
	if (!chain.empty ())
	{
		if (fat_poke (volume, g, chain.back (), extra) != VC_OK)
			return VC_ERR_IO;
	}
	std::vector<uint8_t> z (g->cluster_size, 0);
	uint64_t pos = g->data_offset + (uint64_t) (extra - 2) * g->cluster_size;
	if (vc_write (volume, pos, z.data (), z.size ()) != VC_OK)
		return VC_ERR_IO;
	dir.insert (dir.end (), z.begin (), z.end ());
	return VC_OK;
}

static int fat_utf8_to_utf16 (const char *s, uint16_t *out, int max)
{
	int n = 0;
	const unsigned char *p = (const unsigned char *) s;
	while (*p && n < max)
	{
		if (*p < 0x80)
			out[n++] = *p++;
		else if ((*p & 0xE0) == 0xC0 && p[1])
		{
			out[n++] = (uint16_t) (((p[0] & 0x1F) << 6) | (p[1] & 0x3F));
			p += 2;
		}
		else if ((*p & 0xF0) == 0xE0 && p[1] && p[2])
		{
			out[n++] = (uint16_t) (((p[0] & 0x0F) << 12) | ((p[1] & 0x3F) << 6) | (p[2] & 0x3F));
			p += 3;
		}
		else
		{
			out[n++] = '?';
			++p;
		}
	}
	return n;
}

static uint8_t fat_lfn_checksum (const uint8_t *short11)
{
	uint8_t sum = 0;
	for (int i = 0; i < 11; ++i)
		sum = (uint8_t) (((sum & 1) ? 0x80 : 0) + (sum >> 1) + short11[i]);
	return sum;
}

static void fat_make_short11 (const char *name, uint8_t short11[11])
{
	memset (short11, ' ', 11);
	const char *dot = strrchr (name, '.');
	const char *baseEnd = dot ? dot : name + strlen (name);
	int b = 0;
	for (const char *p = name; p < baseEnd && b < 8; ++p)
	{
		unsigned char c = (unsigned char) *p;
		if (c <= 32 || strchr ("+,:;=[]/*|\\<>?", c))
			continue;
		if (c >= 'a' && c <= 'z')
			c = (unsigned char) (c - 32);
		short11[b++] = c;
	}
	if (b == 0)
	{
		short11[0] = 'F';
		short11[1] = 'I';
		short11[2] = 'L';
		short11[3] = 'E';
	}
	if (dot)
	{
		int e = 0;
		for (const char *p = dot + 1; *p && e < 3; ++p)
		{
			unsigned char c = (unsigned char) *p;
			if (c <= 32 || strchr ("+,:;=[]/*|\\<>?", c))
				continue;
			if (c >= 'a' && c <= 'z')
				c = (unsigned char) (c - 32);
			short11[8 + e++] = c;
		}
	}
}

static int fat_short_taken (const uint8_t *dir, size_t bytes, const uint8_t short11[11])
{
	for (size_t i = 0; i + 32 <= bytes; i += 32)
	{
		if (dir[i] == 0)
			break;
		if (dir[i] == 0xE5 || dir[i + 11] == 0x0F)
			continue;
		if (memcmp (&dir[i], short11, 11) == 0)
			return 1;
	}
	return 0;
}

static void fat_unique_short11 (const uint8_t *dir, size_t bytes, uint8_t short11[11])
{
	uint8_t base[11];
	memcpy (base, short11, 11);
	for (int n = 1; n < 100; ++n)
	{
		if (!fat_short_taken (dir, bytes, short11))
			return;
		memcpy (short11, base, 11);
		char tag[8];
		snprintf (tag, sizeof (tag), "~%d", n);
		size_t tlen = strlen (tag);
		int keep = 8 - (int) tlen;
		if (keep < 1)
			keep = 1;
		memcpy (short11 + keep, tag, tlen);
	}
}

static void fat_write_lfn_slot (uint8_t *e, int seq, int last, uint8_t sum, const uint16_t *utf16, int utf16n)
{
	memset (e, 0, 32);
	e[0] = (uint8_t) (seq | (last ? 0x40 : 0));
	e[11] = 0x0F;
	e[13] = sum;
	uint16_t piece[13];
	for (int i = 0; i < 13; ++i)
	{
		int src = (seq - 1) * 13 + i;
		piece[i] = (src < utf16n) ? utf16[src] : (src == utf16n ? 0 : 0xFFFF);
	}
	for (int k = 0; k < 5; ++k)
		put16 (e + 1 + k * 2, piece[k]);
	for (int k = 0; k < 6; ++k)
		put16 (e + 14 + k * 2, piece[5 + k]);
	for (int k = 0; k < 2; ++k)
		put16 (e + 28 + k * 2, piece[11 + k]);
}

static int fat_find_slots (std::vector<uint8_t> &dir, int needed)
{
	int run = 0;
	int runAt = -1;
	for (size_t i = 0; i + 32 <= dir.size (); i += 32)
	{
		uint8_t first = dir[i];
		if (first == 0 || first == 0xE5)
		{
			if (run == 0)
				runAt = (int) (i / 32);
			++run;
			if (run >= needed)
				return runAt;
			if (first == 0)
			{
				size_t needBytes = (size_t) needed * 32;
				size_t from = (size_t) runAt * 32;
				if (from + needBytes <= dir.size ())
					return runAt;
				return -1;
			}
		}
		else
		{
			run = 0;
			runAt = -1;
		}
	}
	return -1;
}

static const char *fat_basename (const char *path)
{
	if (!path || !path[0])
		return "FILE";
	const char *s = path;
	for (const char *p = path; *p; ++p)
	{
		if (*p == '/' || *p == '\\')
			s = p + 1;
	}
	return *s ? s : "FILE";
}

static int fat_writable (VcVolume *volume)
{
	if (!volume || !volume->volume)
		return VC_ERR_ARGUMENT;
	if (volume->read_only)
		return VC_ERR_UNSUPPORTED;
	return VC_OK;
}

static int fat_flush (VcVolume *volume)
{
	try
	{
		if (volume->volume && volume->volume->GetFile ())
			volume->volume->GetFile ()->Flush ();
	}
	catch (...)
	{
	}
	return VC_OK;
}

static int fat_name_bad (const char *name)
{
	if (!name || !name[0] || strcmp (name, ".") == 0 || strcmp (name, "..") == 0)
		return 1;
	return strpbrk (name, "\\/:*?\"<>|") != nullptr;
}

static void fat_dos_now (uint16_t *t, uint16_t *d)
{
	time_t now = time (nullptr);
	struct tm tm;
	memset (&tm, 0, sizeof (tm));
#ifdef _WIN32
	gmtime_s (&tm, &now);
#else
	gmtime_r (&now, &tm);
#endif
	int year = tm.tm_year + 1900;
	if (year < 1980)
		year = 1980;
	if (year > 2107)
		year = 2107;
	*d = (uint16_t) (((year - 1980) << 9) | ((tm.tm_mon + 1) << 5) | tm.tm_mday);
	*t = (uint16_t) ((tm.tm_hour << 11) | (tm.tm_min << 5) | (tm.tm_sec / 2));
}

static int fat_name_exists (const std::vector<uint8_t> &dir, const char *name)
{
	int cap = (int) (dir.size () / 32);
	if (cap > 32768)
		cap = 32768;
	if (cap < 1)
		return 0;
	std::vector<VcDirEntry> listed ((size_t) cap);
	int n = fat_parse_dir (dir.data (), dir.size (), listed.data (), cap, 0);
	for (int i = 0; i < n; ++i)
	{
		if (strcasecmp (listed[i].name, name) == 0)
			return 1;
	}
	return 0;
}

static int fat_split_parent (const char *path, char *parent, size_t parent_sz, char *leaf, size_t leaf_sz)
{
	if (!path || !parent || !leaf || parent_sz < 2 || leaf_sz < 2)
		return VC_ERR_ARGUMENT;
	parent[0] = 0;
	leaf[0] = 0;
	const char *slash = nullptr;
	for (const char *p = path; *p; ++p)
	{
		if (*p == '/' || *p == '\\')
			slash = p;
	}
	if (!slash)
		snprintf (leaf, leaf_sz, "%s", path);
	else
	{
		size_t plen = (size_t) (slash - path);
		if (plen >= parent_sz)
			return VC_ERR_ARGUMENT;
		memcpy (parent, path, plen);
		parent[plen] = 0;
		snprintf (leaf, leaf_sz, "%s", slash + 1);
	}
	return leaf[0] ? VC_OK : VC_ERR_ARGUMENT;
}

static int fat_parent_cluster (VcVolume *volume, const char *parent, uint32_t *out)
{
	*out = 0;
	if (fat_is_root_path (parent))
		return VC_OK;
	VcDirEntry dirent;
	int rc = fat_find_path (volume, parent, &dirent);
	if (rc != VC_OK)
		return rc;
	if (!dirent.is_dir)
		return VC_ERR_UNSUPPORTED;
	*out = dirent.first_cluster;
	return VC_OK;
}

static int fat_insert_entry (VcVolume *volume, FatGeom *g, uint32_t dirCluster,
	std::vector<uint8_t> &dir, const char *name, uint8_t attr, uint32_t first, uint32_t size)
{
	if (fat_name_exists (dir, name))
		return VC_ERR_FORMAT;
	uint16_t utf16[260];
	int utf16n = fat_utf8_to_utf16 (name, utf16, 255);
	int lfnSlots = utf16n <= 0 ? 0 : (utf16n + 12) / 13;
	int needed = lfnSlots + 1;
	int slot = fat_find_slots (dir, needed);
	while (slot < 0)
	{
		int rc = fat_grow_dir (volume, g, dirCluster, dir);
		if (rc != VC_OK)
			return rc;
		slot = fat_find_slots (dir, needed);
	}
	uint8_t short11[11];
	fat_make_short11 (name, short11);
	fat_unique_short11 (dir.data (), dir.size (), short11);
	uint8_t sum = fat_lfn_checksum (short11);
	size_t at = (size_t) slot * 32;
	for (int seq = lfnSlots; seq >= 1; --seq)
	{
		fat_write_lfn_slot (&dir[at], seq, seq == lfnSlots, sum, utf16, utf16n);
		at += 32;
	}
	memset (&dir[at], 0, 32);
	memcpy (&dir[at], short11, 11);
	dir[at + 11] = attr;
	uint16_t t = 0, d = 0;
	fat_dos_now (&t, &d);
	put16 (&dir[at + 22], t);
	put16 (&dir[at + 24], d);
	put16 (&dir[at + 26], (uint16_t) first);
	put16 (&dir[at + 20], (uint16_t) (first >> 16));
	put32 (&dir[at + 28], size);
	return fat_store_dir (volume, g, dirCluster, dir);
}

static int fat_remove_dirent (VcVolume *volume, FatGeom *g, uint32_t dirCluster,
	std::vector<uint8_t> &dir, const char *leaf, int free_clusters)
{
	size_t lfnStart = (size_t) -1;
	for (size_t i = 0; i + 32 <= dir.size (); i += 32)
	{
		uint8_t first = dir[i];
		if (first == 0)
			break;
		if (first == 0xE5)
		{
			lfnStart = (size_t) -1;
			continue;
		}
		uint8_t attr = dir[i + 11];
		if (attr == 0x0F)
		{
			if (lfnStart == (size_t) -1)
				lfnStart = i;
			continue;
		}
		VcDirEntry one[1];
		size_t from = (lfnStart == (size_t) -1) ? i : lfnStart;
		std::vector<uint8_t> slice (dir.begin () + from, dir.begin () + i + 32);
		int got = fat_parse_dir (slice.data (), slice.size (), one, 1, 0);
		if (got == 1 && strcasecmp (one[0].name, leaf) == 0)
		{
			if (free_clusters && one[0].first_cluster >= 2)
			{
				int rc = fat_free_chain (volume, g, one[0].first_cluster);
				if (rc != VC_OK)
					return rc;
			}
			for (size_t k = from; k <= i; k += 32)
				dir[k] = 0xE5;
			int rc = fat_store_dir (volume, g, dirCluster, dir);
			if (rc != VC_OK)
				return rc;
			return fat_flush (volume);
		}
		lfnStart = (size_t) -1;
	}
	return VC_ERR_IO;
}

int vc_import_file (VcVolume *volume, const char *dest_dir, const char *src_path, const char *dest_name)
{
	int wr = fat_writable (volume);
	if (wr != VC_OK)
		return wr;
	if (!src_path || !src_path[0])
		return VC_ERR_ARGUMENT;
	const char *name = dest_name && dest_name[0] ? dest_name : fat_basename (src_path);
	name = fat_basename (name);
	if (fat_name_bad (name))
		return VC_ERR_ARGUMENT;

	FatGeom geom;
	int rc = fat_load_geom (volume, &geom);
	if (rc != VC_OK)
		return rc;

	uint32_t dirCluster = 0;
	if (!fat_is_root_path (dest_dir))
	{
		VcDirEntry dirent;
		rc = fat_find_path (volume, dest_dir, &dirent);
		if (rc != VC_OK)
			return rc;
		if (!dirent.is_dir)
			return VC_ERR_UNSUPPORTED;
		dirCluster = dirent.first_cluster;
	}

	std::vector<uint8_t> dir;
	rc = fat_load_dir (volume, &geom, dirCluster, dir);
	if (rc != VC_OK)
		return rc;
	if (dir.empty ())
		return VC_ERR_IO;
	if (fat_name_exists (dir, name))
		return VC_ERR_FORMAT;

	FILE *in = fopen (src_path, "rb");
	if (!in)
		return VC_ERR_IO;
	if (fseek (in, 0, SEEK_END) != 0)
	{
		fclose (in);
		return VC_ERR_IO;
	}
	long szl = ftell (in);
	if (szl < 0)
	{
		fclose (in);
		return VC_ERR_IO;
	}
	uint64_t size = (uint64_t) szl;
	if (size > VC_IMPORT_MAX)
	{
		fclose (in);
		return VC_ERR_MEMORY;
	}
	if (size > 0xFFFFFFFFull)
	{
		fclose (in);
		return VC_ERR_ARGUMENT;
	}
	rewind (in);

	uint32_t nClusters = size == 0 ? 0 : (uint32_t) ((size + geom.cluster_size - 1) / geom.cluster_size);
	std::vector<uint32_t> chain;
	chain.reserve (nClusters);
	for (uint32_t i = 0; i < nClusters; ++i)
	{
		uint32_t c = fat_find_free (volume, &geom);
		if (!c)
		{
			fclose (in);
			for (uint32_t x : chain)
				fat_poke (volume, &geom, x, 0);
			return VC_ERR_MEMORY;
		}
		if (fat_poke (volume, &geom, c, fat_eof_mark (&geom)) != VC_OK)
		{
			fclose (in);
			return VC_ERR_IO;
		}
		chain.push_back (c);
	}
	for (size_t i = 0; i + 1 < chain.size (); ++i)
	{
		if (fat_poke (volume, &geom, chain[i], chain[i + 1]) != VC_OK)
		{
			fclose (in);
			return VC_ERR_IO;
		}
	}

	std::vector<uint8_t> chunk (geom.cluster_size, 0);
	uint64_t remaining = size;
	vc_progress_set (0, "Copying into volume");
	for (uint32_t c : chain)
	{
		size_t nread = remaining < geom.cluster_size ? (size_t) remaining : geom.cluster_size;
		if (nread < geom.cluster_size)
			memset (chunk.data (), 0, chunk.size ());
		if (nread && fread (chunk.data (), 1, nread, in) != nread)
		{
			fclose (in);
			fat_free_chain (volume, &geom, chain.empty () ? 0 : chain[0]);
			return VC_ERR_IO;
		}
		uint64_t pos = geom.data_offset + (uint64_t) (c - 2) * geom.cluster_size;
		if (vc_write (volume, pos, chunk.data (), chunk.size ()) != VC_OK)
		{
			fclose (in);
			return VC_ERR_IO;
		}
		remaining -= nread;
		if (size)
			vc_progress_tick ((int) (((size - remaining) * 100ull) / size), "Copying into volume");
	}
	fclose (in);

	rc = fat_insert_entry (volume, &geom, dirCluster, dir, name, 0x20,
		chain.empty () ? 0 : chain[0], (uint32_t) size);
	if (rc != VC_OK)
	{
		if (!chain.empty ())
			fat_free_chain (volume, &geom, chain[0]);
		return rc;
	}
	return fat_flush (volume);
}

int vc_delete_file (VcVolume *volume, const char *path)
{
	int wr = fat_writable (volume);
	if (wr != VC_OK)
		return wr;
	if (!path)
		return VC_ERR_ARGUMENT;
	VcDirEntry entry;
	int rc = fat_find_path (volume, path, &entry);
	if (rc != VC_OK)
		return rc;
	if (entry.is_dir)
		return VC_ERR_UNSUPPORTED;
	FatGeom geom;
	rc = fat_load_geom (volume, &geom);
	if (rc != VC_OK)
		return rc;
	char parent[512];
	char leaf[256];
	rc = fat_split_parent (path, parent, sizeof (parent), leaf, sizeof (leaf));
	if (rc != VC_OK)
		return rc;
	uint32_t dirCluster = 0;
	rc = fat_parent_cluster (volume, parent, &dirCluster);
	if (rc != VC_OK)
		return rc;
	std::vector<uint8_t> dir;
	rc = fat_load_dir (volume, &geom, dirCluster, dir);
	if (rc != VC_OK)
		return rc;
	return fat_remove_dirent (volume, &geom, dirCluster, dir, leaf, 1);
}

int vc_mkdir (VcVolume *volume, const char *parent_dir, const char *name)
{
	int wr = fat_writable (volume);
	if (wr != VC_OK)
		return wr;
	name = fat_basename (name);
	if (fat_name_bad (name))
		return VC_ERR_ARGUMENT;
	FatGeom geom;
	int rc = fat_load_geom (volume, &geom);
	if (rc != VC_OK)
		return rc;
	uint32_t dirCluster = 0;
	rc = fat_parent_cluster (volume, parent_dir, &dirCluster);
	if (rc != VC_OK)
		return rc;
	std::vector<uint8_t> dir;
	rc = fat_load_dir (volume, &geom, dirCluster, dir);
	if (rc != VC_OK)
		return rc;
	if (dir.empty ())
		return VC_ERR_IO;
	uint32_t neu = fat_find_free (volume, &geom);
	if (!neu)
		return VC_ERR_MEMORY;
	if (fat_poke (volume, &geom, neu, fat_eof_mark (&geom)) != VC_OK)
		return VC_ERR_IO;
	std::vector<uint8_t> body (geom.cluster_size, 0);
	memset (&body[0], ' ', 11);
	body[0] = '.';
	body[11] = 0x10;
	put16 (&body[26], (uint16_t) neu);
	put16 (&body[20], (uint16_t) (neu >> 16));
	memset (&body[32], ' ', 11);
	body[32] = '.';
	body[33] = '.';
	body[32 + 11] = 0x10;
	uint32_t parentCl = (!geom.fat32 && dirCluster < 2) ? 0 : dirCluster;
	if (geom.fat32 && parentCl < 2)
		parentCl = geom.root_cluster;
	put16 (&body[32 + 26], (uint16_t) parentCl);
	put16 (&body[32 + 20], (uint16_t) (parentCl >> 16));
	uint64_t pos = geom.data_offset + (uint64_t) (neu - 2) * geom.cluster_size;
	if (vc_write (volume, pos, body.data (), body.size ()) != VC_OK)
		return VC_ERR_IO;
	rc = fat_insert_entry (volume, &geom, dirCluster, dir, name, 0x10, neu, 0);
	if (rc != VC_OK)
	{
		fat_poke (volume, &geom, neu, 0);
		return rc;
	}
	return fat_flush (volume);
}

int vc_rmdir (VcVolume *volume, const char *path)
{
	int wr = fat_writable (volume);
	if (wr != VC_OK)
		return wr;
	if (!path || fat_is_root_path (path))
		return VC_ERR_ARGUMENT;
	VcDirEntry entry;
	int rc = fat_find_path (volume, path, &entry);
	if (rc != VC_OK)
		return rc;
	if (!entry.is_dir)
		return VC_ERR_UNSUPPORTED;
	FatGeom geom;
	rc = fat_load_geom (volume, &geom);
	if (rc != VC_OK)
		return rc;
	std::vector<uint8_t> child;
	rc = fat_load_dir (volume, &geom, entry.first_cluster, child);
	if (rc != VC_OK)
		return rc;
	int cap = (int) (child.size () / 32);
	if (cap > 32768)
		cap = 32768;
	std::vector<VcDirEntry> listed ((size_t) (cap < 1 ? 1 : cap));
	int n = fat_parse_dir (child.data (), child.size (), listed.data (), cap < 1 ? 1 : cap, 0);
	if (n > 0)
		return VC_ERR_FORMAT;
	char parent[512];
	char leaf[256];
	rc = fat_split_parent (path, parent, sizeof (parent), leaf, sizeof (leaf));
	if (rc != VC_OK)
		return rc;
	uint32_t dirCluster = 0;
	rc = fat_parent_cluster (volume, parent, &dirCluster);
	if (rc != VC_OK)
		return rc;
	std::vector<uint8_t> dir;
	rc = fat_load_dir (volume, &geom, dirCluster, dir);
	if (rc != VC_OK)
		return rc;
	return fat_remove_dirent (volume, &geom, dirCluster, dir, leaf, 1);
}

int vc_rename (VcVolume *volume, const char *path, const char *new_name)
{
	int wr = fat_writable (volume);
	if (wr != VC_OK)
		return wr;
	new_name = fat_basename (new_name);
	if (!path || fat_name_bad (new_name))
		return VC_ERR_ARGUMENT;
	VcDirEntry entry;
	int rc = fat_find_path (volume, path, &entry);
	if (rc != VC_OK)
		return rc;
	char parent[512];
	char leaf[256];
	rc = fat_split_parent (path, parent, sizeof (parent), leaf, sizeof (leaf));
	if (rc != VC_OK)
		return rc;
	if (strcasecmp (leaf, new_name) == 0)
		return VC_OK;
	FatGeom geom;
	rc = fat_load_geom (volume, &geom);
	if (rc != VC_OK)
		return rc;
	uint32_t dirCluster = 0;
	rc = fat_parent_cluster (volume, parent, &dirCluster);
	if (rc != VC_OK)
		return rc;
	std::vector<uint8_t> dir;
	rc = fat_load_dir (volume, &geom, dirCluster, dir);
	if (rc != VC_OK)
		return rc;
	if (fat_name_exists (dir, new_name))
		return VC_ERR_FORMAT;
	rc = fat_remove_dirent (volume, &geom, dirCluster, dir, leaf, 0);
	if (rc != VC_OK)
		return rc;
	rc = fat_load_dir (volume, &geom, dirCluster, dir);
	if (rc != VC_OK)
		return rc;
	uint8_t attr = entry.is_dir ? 0x10 : 0x20;
	rc = fat_insert_entry (volume, &geom, dirCluster, dir, new_name, attr,
		entry.first_cluster, (uint32_t) entry.size);
	if (rc != VC_OK)
		return rc;
	return fat_flush (volume);
}

int vc_wipe_free_space (VcVolume *volume)
{
	int wr = fat_writable (volume);
	if (wr != VC_OK)
		return wr;
	FatGeom geom;
	int rc = fat_load_geom (volume, &geom);
	if (rc != VC_OK)
		return rc;
	std::vector<uint8_t> z (geom.cluster_size, 0);
	uint32_t maxc = fat_max_cluster (volume, &geom);
	vc_progress_set (0, "Wiping free space");
	uint32_t free_n = 0;
	for (uint32_t c = 2; c <= maxc; ++c)
	{
		if (fat_next (volume, &geom, c) == 0)
			++free_n;
	}
	if (free_n == 0)
	{
		vc_progress_set (100, "Wiping free space");
		return fat_flush (volume);
	}
	uint32_t done = 0;
	for (uint32_t c = 2; c <= maxc; ++c)
	{
		if (fat_next (volume, &geom, c) != 0)
			continue;
		uint64_t pos = geom.data_offset + (uint64_t) (c - 2) * geom.cluster_size;
		if (vc_write (volume, pos, z.data (), z.size ()) != VC_OK)
			return VC_ERR_IO;
		++done;
		vc_progress_tick ((int) ((done * 100ull) / free_n), "Wiping free space");
	}
	vc_progress_set (100, "Wiping free space");
	return fat_flush (volume);
}

static Mutex gEntropyMutex;
enum { VC_ENTROPY_POOL = 320, VC_ENTROPY_NEED = 2048 };
static uint8_t gPool[VC_ENTROPY_POOL];
static size_t gPoolWrite = 0;
static size_t gPoolSinceMix = 0;
static size_t gEntropyCollected = 0;
static int gPoolReady = 0;

static int fill_os_rand (void *buf, size_t n)
{
	if (!buf || n == 0)
		return -1;
#ifdef __APPLE__
	arc4random_buf (buf, n);
	return 0;
#else
	uint8_t *p = (uint8_t *) buf;
	size_t got = 0;
	int fd = open ("/dev/urandom", O_RDONLY);
	if (fd < 0)
		return -1;
	while (got < n)
	{
		ssize_t r = read (fd, p + got, n - got);
		if (r <= 0)
		{
			close (fd);
			return -1;
		}
		got += (size_t) r;
	}
	close (fd);
	return 0;
#endif
}

static void entropy_mix_unlocked (void)
{
	uint8_t digest[SHA512_DIGEST_SIZE];
	sha512 (digest, gPool, VC_ENTROPY_POOL);
	for (size_t i = 0; i < VC_ENTROPY_POOL; ++i)
		gPool[i] ^= digest[i % SHA512_DIGEST_SIZE];
	vc_secure_wipe (digest, sizeof (digest));
}

static void entropy_ensure_unlocked (void)
{
	if (gPoolReady)
		return;
	fill_os_rand (gPool, sizeof (gPool));
	gPoolWrite = 0;
	gPoolSinceMix = 0;
	gEntropyCollected = 0;
	gPoolReady = 1;
}

void vc_entropy_reset (void)
{
	ScopeLock lock (gEntropyMutex);
	gPoolReady = 0;
	entropy_ensure_unlocked ();
	gEntropyCollected = 0;
}

void vc_entropy_add (const void *data, size_t size)
{
	if (!data || size == 0)
		return;
	ScopeLock lock (gEntropyMutex);
	entropy_ensure_unlocked ();
	const uint8_t *p = (const uint8_t *) data;
	for (size_t i = 0; i < size; ++i)
	{
		gPool[gPoolWrite] = (uint8_t) (gPool[gPoolWrite] + p[i]);
		gPoolWrite = (gPoolWrite + 1) % VC_ENTROPY_POOL;
		if (++gPoolSinceMix >= 16)
		{
			entropy_mix_unlocked ();
			gPoolSinceMix = 0;
		}
	}
	gEntropyCollected += size;
}

int vc_entropy_percent (void)
{
	ScopeLock lock (gEntropyMutex);
	if (gEntropyCollected >= VC_ENTROPY_NEED)
		return 100;
	return (int) (gEntropyCollected * 100 / VC_ENTROPY_NEED);
}

static int fill_rand (void *buf, size_t n)
{
	if (!buf || n == 0)
		return -1;
	if (fill_os_rand (buf, n) != 0)
		return -1;
	ScopeLock lock (gEntropyMutex);
	entropy_ensure_unlocked ();
	entropy_mix_unlocked ();
	uint8_t *out = (uint8_t *) buf;
	for (size_t i = 0; i < n; ++i)
		out[i] ^= gPool[i % VC_ENTROPY_POOL];
	entropy_mix_unlocked ();
	return 0;
}

static void put16_le (uint8_t *b, size_t off, uint16_t v)
{
	b[off] = (uint8_t) v;
	b[off + 1] = (uint8_t) (v >> 8);
}

static int format_empty_fat16 (VcVolume *volume, uint64_t dataBytes)
{
	const uint32_t bps = 512;
	if (!volume || dataBytes < 2048ull * bps)
		return VC_ERR_ARGUMENT;
	uint32_t totalSecs = (uint32_t) (dataBytes / bps);
	uint32_t spc = 1;
	while (spc < 128 && (totalSecs / spc) > 65524u)
		spc *= 2;
	if ((totalSecs / spc) < 16)
		return VC_ERR_ARGUMENT;
	uint32_t reserved = 1;
	uint32_t fats = 2;
	uint32_t rootEnt = 512;
	uint32_t rootSecs = (rootEnt * 32 + bps - 1) / bps;
	uint32_t fatSecs = 16;
	for (;;)
	{
		uint32_t dataSecs = totalSecs - reserved - fats * fatSecs - rootSecs;
		uint32_t clusters = dataSecs / spc;
		uint32_t need = (clusters * 2 + bps - 1) / bps;
		if (need <= fatSecs)
			break;
		fatSecs = need;
		if (reserved + fats * fatSecs + rootSecs >= totalSecs)
			return VC_ERR_ARGUMENT;
	}

	std::vector<uint8_t> boot (bps, 0);
	boot[0] = 0xEB;
	boot[1] = 0x3C;
	boot[2] = 0x90;
	memcpy (&boot[3], "MSDOS5.0", 8);
	put16_le (boot.data (), 11, (uint16_t) bps);
	boot[13] = (uint8_t) spc;
	put16_le (boot.data (), 14, (uint16_t) reserved);
	boot[16] = (uint8_t) fats;
	put16_le (boot.data (), 17, (uint16_t) rootEnt);
	if (totalSecs < 65536)
		put16_le (boot.data (), 19, (uint16_t) totalSecs);
	else
	{
		put16_le (boot.data (), 19, 0);
		boot[32] = (uint8_t) totalSecs;
		boot[33] = (uint8_t) (totalSecs >> 8);
		boot[34] = (uint8_t) (totalSecs >> 16);
		boot[35] = (uint8_t) (totalSecs >> 24);
	}
	boot[21] = 0xF8;
	put16_le (boot.data (), 22, (uint16_t) fatSecs);
	put16_le (boot.data (), 24, 1);
	put16_le (boot.data (), 26, 1);
	memcpy (&boot[54], "FAT16   ", 8);
	boot[510] = 0x55;
	boot[511] = 0xAA;
	if (vc_write (volume, 0, boot.data (), boot.size ()) != VC_OK)
		return VC_ERR_IO;

	std::vector<uint8_t> fat (fatSecs * bps, 0);
	fat[0] = 0xF8;
	fat[1] = 0xFF;
	fat[2] = 0xFF;
	fat[3] = 0xFF;
	for (uint32_t f = 0; f < fats; ++f)
	{
		uint64_t off = (uint64_t) (reserved + f * fatSecs) * bps;
		if (vc_write (volume, off, fat.data (), fat.size ()) != VC_OK)
			return VC_ERR_IO;
	}
	std::vector<uint8_t> root (rootSecs * bps, 0);
	uint64_t rootOff = (uint64_t) (reserved + fats * fatSecs) * bps;
	if (vc_write (volume, rootOff, root.data (), root.size ()) != VC_OK)
		return VC_ERR_IO;
	return VC_OK;
}

static shared_ptr <VeraCrypt::EncryptionAlgorithm> FindCipher (const char *name)
{
	if (!name || !name[0])
		return shared_ptr <VeraCrypt::EncryptionAlgorithm> ();
	wstring want = StringConverter::ToWide (string (name));
	for (shared_ptr <VeraCrypt::EncryptionAlgorithm> ea : VeraCrypt::EncryptionAlgorithm::GetAvailableAlgorithms ())
	{
		if (ea->GetName (true) == want || ea->GetName (false) == want)
			return ea->GetNew ();
	}
	return shared_ptr <VeraCrypt::EncryptionAlgorithm> ();
}

static shared_ptr <Pkcs5Kdf> FindKdf (const char *name)
{
	if (!name || !name[0])
		return shared_ptr <Pkcs5Kdf> ();
	wstring want = StringConverter::ToWide (string (name));
	for (shared_ptr <Pkcs5Kdf> kdf : Pkcs5Kdf::GetAvailableAlgorithms ())
	{
		if (kdf->GetName () == want)
			return shared_ptr <Pkcs5Kdf> (kdf->Clone ());
	}
	return shared_ptr <Pkcs5Kdf> ();
}

static int BuildHeader (
	shared_ptr <VeraCrypt::EncryptionAlgorithm> ea,
	shared_ptr <Pkcs5Kdf> kdf,
	VolumeType::Enum type,
	uint64 dataStart,
	uint64 dataSize,
	const VolumePassword &password,
	int pim,
	SecureBuffer &headerBuffer)
{
	if (!ea || !kdf || dataSize < 1024ull * 1024ull)
		return VC_ERR_ARGUMENT;
	VolumeHeader header ((uint32) headerBuffer.Size ());
	VolumeHeaderCreationOptions opt;
	opt.EA = ea;
	opt.Kdf = kdf;
	opt.Type = type;
	opt.SectorSize = TC_SECTOR_SIZE_FILE_HOSTED_VOLUME;
	opt.VolumeDataStart = dataStart;
	opt.VolumeDataSize = dataSize;

	SecureBuffer master (ea->GetKeySize () * 2);
	do
	{
		if (fill_rand (master.Ptr (), master.Size ()) != 0)
			return VC_ERR_IO;
	} while (memcmp (master.Ptr (), master.Ptr () + master.Size () / 2, master.Size () / 2) == 0);
	opt.DataKey = master;

	SecureBuffer salt (VolumeHeader::GetSaltSize ());
	if (fill_rand (salt.Ptr (), salt.Size ()) != 0)
		return VC_ERR_IO;
	opt.Salt = salt;

	SecureBuffer headerKey (VolumeHeader::GetHeaderKeyDerivationSize (kdf));
	if (kdf->DeriveKey (headerKey, password, pim, salt) != 0)
		return VC_ERR_PASSWORD;
	opt.HeaderKey = headerKey;
	header.Create (headerBuffer, opt);
	return VC_OK;
}

static int FormatOpened (const char *path, const char *password, size_t passwordLen, int pim,
	const char *const *keyfiles, size_t keyfileCount, uint64 dataBytes)
{
	VcOpenOptions openOpt = {};
	openOpt.path = path;
	openOpt.password = password;
	openOpt.password_len = passwordLen;
	openOpt.pim = pim;
	openOpt.keyfiles = keyfiles;
	openOpt.keyfile_count = keyfileCount;
	int err = 0;
	VcVolume *vol = vc_open (&openOpt, &err);
	if (!vol)
		return err != 0 ? err : VC_ERR_FORMAT;
	int fatRc = format_empty_fat16 (vol, dataBytes);
	vc_close (vol);
	return fatRc;
}

int vc_create_volume (const VcCreateOptions *options)
{
	if (!options || !options->path || options->size_bytes < 2ull * 1024ull * 1024ull)
		return VC_ERR_ARGUMENT;
	const char *pw = options->password ? options->password : "";
	size_t pwLen = options->password_len;
	if (!pwLen && options->password)
		pwLen = strlen (options->password);
	if (pwLen == 0 && options->keyfile_count == 0)
		return VC_ERR_ARGUMENT;

	const uint64 hiddenSize = options->hidden_size_bytes;
	const char *hiddenPw = options->hidden_password ? options->hidden_password : "";
	size_t hiddenPwLen = options->hidden_password_len;
	if (!hiddenPwLen && options->hidden_password)
		hiddenPwLen = strlen (options->hidden_password);
	if (hiddenSize > 0)
	{
		if (hiddenSize < 2ull * 1024ull * 1024ull)
			return VC_ERR_ARGUMENT;
		if (hiddenSize + TC_TOTAL_VOLUME_HEADERS_SIZE + 2ull * 1024ull * 1024ull > options->size_bytes)
			return VC_ERR_ARGUMENT;
		if (hiddenPwLen == 0 && options->hidden_keyfile_count == 0)
			return VC_ERR_ARGUMENT;
	}

	try
	{
		vc_progress_set (-1, "Deriving keys");
		shared_ptr <VeraCrypt::EncryptionAlgorithm> ea = FindCipher (
			options->cipher && options->cipher[0] ? options->cipher : "AES(Twofish(Serpent))");
		shared_ptr <Pkcs5Kdf> kdf = FindKdf (
			options->kdf && options->kdf[0] ? options->kdf : "HMAC-SHA-512");
		if (!ea || !kdf)
			return VC_ERR_UNSUPPORTED;

		VolumeLayoutV2Normal outerLayout;
		SecureBuffer outerHeader (outerLayout.GetHeaderSize ());
		shared_ptr <VolumePassword> password (new VolumePassword (
			reinterpret_cast <const uint8 *> (pw), pwLen));
		shared_ptr <KeyfileList> keyfiles = MakeKeyfilesFrom (options->keyfiles, options->keyfile_count);
		if (keyfiles)
			password = Keyfile::ApplyListToPassword (keyfiles, password);

		uint64 outerDataStart = (uint64) outerLayout.GetHeaderSize () * 2;
		uint64 outerDataSize = outerLayout.GetMaxDataSize (options->size_bytes);
		int rc = BuildHeader (ea->GetNew (), kdf, VolumeType::Normal, outerDataStart, outerDataSize,
			*password, options->pim, outerHeader);
		if (rc != VC_OK)
			return rc;

		SecureBuffer innerHeader (outerLayout.GetHeaderSize ());
		uint64 hiddenDataSize = 0;
		if (hiddenSize > 0)
		{
			VolumeLayoutV2Hidden hiddenLayout;
			hiddenDataSize = hiddenLayout.GetMaxDataSize (hiddenSize);
			uint64 hiddenDataStart = options->size_bytes - (uint64) hiddenLayout.GetHeaderSize () * 2 - hiddenSize;
			shared_ptr <VolumePassword> hiddenPassword (new VolumePassword (
				reinterpret_cast <const uint8 *> (hiddenPw), hiddenPwLen));
			shared_ptr <KeyfileList> hiddenKeys = MakeKeyfilesFrom (options->hidden_keyfiles, options->hidden_keyfile_count);
			if (hiddenKeys)
				hiddenPassword = Keyfile::ApplyListToPassword (hiddenKeys, hiddenPassword);
			shared_ptr <Pkcs5Kdf> hiddenKdf = FindKdf (
				options->kdf && options->kdf[0] ? options->kdf : "HMAC-SHA-512");
			rc = BuildHeader (FindCipher (options->cipher && options->cipher[0] ? options->cipher : "AES(Twofish(Serpent))"),
				hiddenKdf, VolumeType::Hidden, hiddenDataStart, hiddenDataSize,
				*hiddenPassword, options->hidden_pim, innerHeader);
			if (rc != VC_OK)
				return rc;
		}
		else if (fill_rand (innerHeader.Ptr (), innerHeader.Size ()) != 0)
			return VC_ERR_IO;

		vc_progress_set (40, "Writing headers");
		File file;
		file.Open (options->path, File::CreateReadWrite, File::ShareNone);
		file.Write (outerHeader);
		file.Write (innerHeader);
		file.SetLength (options->size_bytes);
		file.SeekEnd (outerLayout.GetBackupHeaderOffset ());
		file.Write (outerHeader);
		file.SeekEnd (-TC_HIDDEN_VOLUME_HEADER_OFFSET);
		file.Write (innerHeader);
		file.Close ();

		vc_progress_set (70, "Formatting");
		rc = FormatOpened (options->path, pw, pwLen, options->pim,
			options->keyfiles, options->keyfile_count, outerDataSize);
		if (rc != VC_OK)
			return rc;
		if (hiddenSize > 0)
		{
			vc_progress_set (85, "Formatting nested volume");
			rc = FormatOpened (options->path, hiddenPw, hiddenPwLen, options->hidden_pim,
				options->hidden_keyfiles, options->hidden_keyfile_count, hiddenDataSize);
		}
		if (rc == VC_OK)
			vc_progress_set (100, "Done");
		return rc;
	}
	catch (PasswordException &)
	{
		return VC_ERR_PASSWORD;
	}
	catch (SystemException &)
	{
		return VC_ERR_IO;
	}
	catch (...)
	{
		return VC_ERR_FORMAT;
	}
}

static shared_ptr <Volume> OpenWritableVolume (const char *path, const char *password, size_t passwordLen,
	int pim, const char *const *keyfiles, size_t keyfileCount, int useBackup)
{
	shared_ptr <VolumePassword> pw (new VolumePassword (
		reinterpret_cast <const uint8 *> (password ? password : ""), passwordLen));
	shared_ptr <Volume> volume (new Volume);
	if (!EncryptionThreadPool::IsRunning ())
		EncryptionThreadPool::Start ();
	volume->Open (
		VolumePath (wstring (path, path + strlen (path))),
		true,
		pw,
		pim,
		shared_ptr <Pkcs5Kdf> (),
		MakeKeyfilesFrom (keyfiles, keyfileCount),
		false,
		VolumeProtection::None,
		shared_ptr <VolumePassword> (),
		0,
		shared_ptr <Pkcs5Kdf> (),
		shared_ptr <KeyfileList> (),
		false,
		VolumeType::Unknown,
		useBackup != 0,
		false);
	return volume;
}

static int ReEncryptOpened (shared_ptr <Volume> volume, const VolumePassword &password, int pim,
	shared_ptr <Pkcs5Kdf> kdf)
{
	bool backup = false;
	while (true)
	{
		SecureBuffer salt (volume->GetSaltSize ());
		if (fill_rand (salt.Ptr (), salt.Size ()) != 0)
			return VC_ERR_IO;
		SecureBuffer headerKey (VolumeHeader::GetHeaderKeyDerivationSize (kdf));
		if (kdf->DeriveKey (headerKey, password, pim, salt) != 0)
			return VC_ERR_PASSWORD;
		volume->ReEncryptHeader (backup, salt, headerKey, kdf);
		volume->GetFile ()->Flush ();
		if (!volume->GetLayout ()->HasBackupHeader () || backup)
			break;
		backup = true;
	}
	return VC_OK;
}

int vc_change_header (const VcChangeHeaderOptions *options)
{
	if (!options || !options->path)
		return VC_ERR_ARGUMENT;
	const char *pw = options->password ? options->password : "";
	size_t pwLen = options->password_len;
	if (!pwLen && options->password)
		pwLen = strlen (options->password);
	const char *newPw = options->new_password ? options->new_password : "";
	size_t newLen = options->new_password_len;
	if (!newLen && options->new_password)
		newLen = strlen (options->new_password);
	if (!newLen)
	{
		newPw = pw;
		newLen = pwLen;
	}
	if (newLen == 0 && options->new_keyfile_count == 0)
		return VC_ERR_ARGUMENT;
	try
	{
		shared_ptr <Volume> volume = OpenWritableVolume (options->path, pw, pwLen, options->pim,
			options->keyfiles, options->keyfile_count, options->use_backup_header);
		shared_ptr <Pkcs5Kdf> kdf = volume->GetPkcs5Kdf ();
		if (options->new_kdf && options->new_kdf[0])
		{
			kdf = FindKdf (options->new_kdf);
			if (!kdf)
				return VC_ERR_UNSUPPORTED;
		}
		shared_ptr <VolumePassword> newPassword (new VolumePassword (
			reinterpret_cast <const uint8 *> (newPw), newLen));
		shared_ptr <KeyfileList> newKeyfiles = MakeKeyfilesFrom (options->new_keyfiles, options->new_keyfile_count);
		if (newKeyfiles)
			newPassword = Keyfile::ApplyListToPassword (newKeyfiles, newPassword);
		int rc = ReEncryptOpened (volume, *newPassword, options->new_pim, kdf);
		volume->Close ();
		return rc;
	}
	catch (PasswordException &)
	{
		return VC_ERR_PASSWORD;
	}
	catch (SystemException &)
	{
		return VC_ERR_IO;
	}
	catch (...)
	{
		return VC_ERR_FORMAT;
	}
}

int vc_backup_headers (const char *volume_path, const char *backup_path,
	const char *password, size_t password_len, int pim,
	const char *const *keyfiles, size_t keyfile_count)
{
	if (!volume_path || !backup_path)
		return VC_ERR_ARGUMENT;
	if (!password_len && password)
		password_len = strlen (password);
	try
	{
		shared_ptr <Volume> volume = OpenWritableVolume (volume_path, password ? password : "",
			password_len, pim, keyfiles, keyfile_count, 0);
		shared_ptr <Pkcs5Kdf> kdf = volume->GetPkcs5Kdf ();
		shared_ptr <VolumePassword> pw (new VolumePassword (
			reinterpret_cast <const uint8 *> (password ? password : ""), password_len));
		shared_ptr <KeyfileList> keys = MakeKeyfilesFrom (keyfiles, keyfile_count);
		if (keys)
			pw = Keyfile::ApplyListToPassword (keys, pw);
		SecureBuffer salt (volume->GetSaltSize ());
		if (fill_rand (salt.Ptr (), salt.Size ()) != 0)
			return VC_ERR_IO;
		SecureBuffer headerKey (VolumeHeader::GetHeaderKeyDerivationSize (kdf));
		if (kdf->DeriveKey (headerKey, *pw, pim, salt) != 0)
			return VC_ERR_PASSWORD;
		SecureBuffer headerBuf (volume->GetLayout ()->GetHeaderSize ());
		volume->GetHeader ()->EncryptNew (headerBuf, salt, headerKey, kdf);
		File backup;
		backup.Open (backup_path, File::CreateWrite, File::ShareNone);
		backup.Write (headerBuf);
		SecureBuffer dummy (headerBuf.Size ());
		if (fill_rand (dummy.Ptr (), dummy.Size ()) != 0)
			return VC_ERR_IO;
		backup.Write (dummy);
		backup.Close ();
		volume->Close ();
		return VC_OK;
	}
	catch (PasswordException &)
	{
		return VC_ERR_PASSWORD;
	}
	catch (SystemException &)
	{
		return VC_ERR_IO;
	}
	catch (...)
	{
		return VC_ERR_FORMAT;
	}
}

int vc_restore_headers (const char *volume_path, const char *backup_path,
	const char *password, size_t password_len, int pim,
	const char *const *keyfiles, size_t keyfile_count)
{
	if (!volume_path)
		return VC_ERR_ARGUMENT;
	if (!password_len && password)
		password_len = strlen (password);
	try
	{
		SecureBuffer headerBuf (TC_VOLUME_HEADER_SIZE);
		if (!backup_path || !backup_path[0])
		{
			File volume;
			volume.Open (volume_path, File::OpenRead, File::ShareRead);
			shared_ptr <VolumeLayout> tryLayouts[2] = {
				shared_ptr <VolumeLayout> (new VolumeLayoutV2Normal ()),
				shared_ptr <VolumeLayout> (new VolumeLayoutV2Hidden ())
			};
			int got = 0;
			for (int i = 0; i < 2 && !got; ++i)
			{
				int off = tryLayouts[i]->GetBackupHeaderOffset ();
				if (off >= 0)
					volume.SeekAt ((uint64) off);
				else
					volume.SeekEnd (off);
				if (volume.Read (headerBuf) == TC_VOLUME_HEADER_SIZE)
					got = 1;
			}
			volume.Close ();
			if (!got)
				return VC_ERR_IO;
		}
		else
		{
			File backup;
			backup.Open (backup_path, File::OpenRead, File::ShareRead);
			if (backup.Length () < TC_VOLUME_HEADER_SIZE)
				return VC_ERR_FORMAT;
			backup.Read (headerBuf);
			backup.Close ();
		}

		shared_ptr <VolumePassword> pw (new VolumePassword (
			reinterpret_cast <const uint8 *> (password ? password : ""), password_len));
		shared_ptr <KeyfileList> keys = MakeKeyfilesFrom (keyfiles, keyfile_count);
		if (keys)
			pw = Keyfile::ApplyListToPassword (keys, pw);

		shared_ptr <VolumeLayout> layouts[2] = {
			shared_ptr <VolumeLayout> (new VolumeLayoutV2Normal ()),
			shared_ptr <VolumeLayout> (new VolumeLayoutV2Hidden ())
		};
		shared_ptr <VolumeLayout> found;
		shared_ptr <VolumeHeader> header;
		for (int i = 0; i < 2; ++i)
		{
			shared_ptr <VolumeHeader> candidate = layouts[i]->GetHeader ();
			if (candidate->Decrypt (headerBuf, *pw, pim, shared_ptr <Pkcs5Kdf> (),
				layouts[i]->GetSupportedKeyDerivationFunctions (),
				layouts[i]->GetSupportedEncryptionAlgorithms (),
				layouts[i]->GetSupportedEncryptionModes ()))
			{
				header = candidate;
				found = layouts[i];
				break;
			}
		}
		if (!header || !found)
			return VC_ERR_PASSWORD;

		shared_ptr <Pkcs5Kdf> kdf = header->GetPkcs5Kdf ();
		SecureBuffer salt (VolumeHeader::GetSaltSize ());
		if (fill_rand (salt.Ptr (), salt.Size ()) != 0)
			return VC_ERR_IO;
		SecureBuffer headerKey (VolumeHeader::GetHeaderKeyDerivationSize (kdf));
		if (kdf->DeriveKey (headerKey, *pw, pim, salt) != 0)
			return VC_ERR_PASSWORD;
		SecureBuffer newHeader (found->GetHeaderSize ());
		header->EncryptNew (newHeader, salt, headerKey, kdf);

		File volume;
		volume.Open (volume_path, File::OpenReadWrite, File::ShareNone);
		int primary = found->GetHeaderOffset ();
		if (primary >= 0)
			volume.SeekAt ((uint64) primary);
		else
			volume.SeekEnd (primary);
		volume.Write (newHeader);
		if (found->HasBackupHeader ())
		{
			if (fill_rand (salt.Ptr (), salt.Size ()) != 0)
				return VC_ERR_IO;
			if (kdf->DeriveKey (headerKey, *pw, pim, salt) != 0)
				return VC_ERR_PASSWORD;
			header->EncryptNew (newHeader, salt, headerKey, kdf);
			int backupOff = found->GetBackupHeaderOffset ();
			if (backupOff >= 0)
				volume.SeekAt ((uint64) backupOff);
			else
				volume.SeekEnd (backupOff);
			volume.Write (newHeader);
		}
		volume.Close ();
		return VC_OK;
	}
	catch (PasswordException &)
	{
		return VC_ERR_PASSWORD;
	}
	catch (SystemException &)
	{
		return VC_ERR_IO;
	}
	catch (...)
	{
		return VC_ERR_FORMAT;
	}
}

int vc_generate_keyfile (const char *path, size_t size)
{
	if (!path || size < 64 || size > 1024 * 1024)
		return VC_ERR_ARGUMENT;
	try
	{
		SecureBuffer buf (size);
		if (fill_rand (buf.Ptr (), buf.Size ()) != 0)
			return VC_ERR_IO;
		File file;
		file.Open (path, File::CreateWrite, File::ShareNone);
		file.Write (buf);
		file.Close ();
		return VC_OK;
	}
	catch (...)
	{
		return VC_ERR_IO;
	}
}

int vc_volume_info (VcVolume *volume, char *out, size_t out_size)
{
	if (!volume || !volume->volume || !out || out_size < 8)
		return VC_ERR_ARGUMENT;
	try
	{
		string cipher = StringConverter::ToSingle (volume->volume->GetEncryptionAlgorithm ()->GetName (true));
		string kdf = StringConverter::ToSingle (volume->volume->GetPkcs5Kdf ()->GetName ());
		const char *type = volume->volume->GetType () == VolumeType::Hidden ? "Hidden" : "Normal";
		char line[512];
		snprintf (line, sizeof (line),
			"Encryption algorithm: %s\nKDF: %s\nVolume type: %s\nSize: %llu bytes\nPIM: %d\nXTS",
			cipher.c_str (), kdf.c_str (), type,
			(unsigned long long) volume->volume->GetSize (),
			volume->volume->GetPim ());
		snprintf (out, out_size, "%s", line);
		return VC_OK;
	}
	catch (...)
	{
		return VC_ERR_FORMAT;
	}
}

int vc_benchmark (char *out, size_t out_size)
{
	if (!out || out_size < 8)
		return VC_ERR_ARGUMENT;
	try
	{
		if (!EncryptionThreadPool::IsRunning ())
			EncryptionThreadPool::Start ();
		string result;
		const size_t bytes = 1024 * 1024;
		SecureBuffer buf (bytes);
		memset (buf.Ptr (), 0x5a, bytes);
		for (shared_ptr <VeraCrypt::EncryptionAlgorithm> proto : VeraCrypt::EncryptionAlgorithm::GetAvailableAlgorithms ())
		{
			if (proto->GetCiphers ().size () > 1)
				continue;
			shared_ptr <VeraCrypt::EncryptionAlgorithm> ea = proto->GetNew ();
			shared_ptr <EncryptionMode> mode (new EncryptionModeXTS ());
			if (!ea->IsModeSupported (*mode))
				continue;
			SecureBuffer key (ea->GetKeySize ());
			SecureBuffer modeKey (ea->GetKeySize ());
			if (fill_rand (key.Ptr (), key.Size ()) != 0 || fill_rand (modeKey.Ptr (), modeKey.Size ()) != 0)
				return VC_ERR_IO;
			ea->SetKey (key);
			mode->SetKey (modeKey);
			ea->SetMode (mode);
			auto start = std::chrono::steady_clock::now ();
			ea->EncryptSectors (buf.Ptr (), 0, bytes / 512, 512);
			auto ms = std::chrono::duration_cast<std::chrono::milliseconds> (
				std::chrono::steady_clock::now () - start).count ();
			if (ms < 1)
				ms = 1;
			double mbs = (1000.0 / (double) ms);
			char line[128];
			snprintf (line, sizeof (line), "%s: %.1f MiB/s\n",
				StringConverter::ToSingle (ea->GetName (true)).c_str (), mbs);
			result += line;
		}
		if (result.empty ())
			return VC_ERR_UNSUPPORTED;
		snprintf (out, out_size, "%s", result.c_str ());
		return VC_OK;
	}
	catch (...)
	{
		return VC_ERR_FORMAT;
	}
}

int vc_test_vectors (void)
{
	try
	{
		EncryptionTest::TestAll ();
		return VC_OK;
	}
	catch (...)
	{
		return VC_ERR_FORMAT;
	}
}
