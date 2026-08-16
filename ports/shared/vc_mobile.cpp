/*
 Copyright (c) 2026 Shivam Mangesh Pingale. All rights reserved.

 Governed by the Apache License 2.0 the full text of which is
 contained in the file License.txt included in VeraCrypt binary and source
 code distribution packages.
*/

#include "vc_mobile.h"

#include "Volume/Volume.h"
#include "Volume/Keyfile.h"
#include "Volume/EncryptionThreadPool.h"
#include "Platform/File.h"
#include "Platform/Buffer.h"

#include <cstdio>
#include <cstring>
#include <memory>
#include <strings.h>
#include <string>
#include <vector>

using namespace VeraCrypt;

struct VcVolume
{
	shared_ptr <Volume> volume;
	string path;
};

static shared_ptr <KeyfileList> MakeKeyfiles (const VcOpenOptions *options)
{
	if (!options->keyfiles || options->keyfile_count == 0)
		return shared_ptr <KeyfileList> ();

	shared_ptr <KeyfileList> list (new KeyfileList);
	for (size_t i = 0; i < options->keyfile_count; ++i)
	{
		if (options->keyfiles[i] && options->keyfiles[i][0])
			list->push_back (make_shared <Keyfile> (wstring (options->keyfiles[i], options->keyfiles[i] + strlen (options->keyfiles[i]))));
	}
	return list;
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
			VolumeProtection::None,
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
	try
	{
		size_t sector = volume->volume->GetSectorSize ();
		uint64_t alignedOffset = offset - (offset % sector);
		size_t prefix = (size_t) (offset - alignedOffset);
		size_t total = prefix + size;
		total = ((total + sector - 1) / sector) * sector;
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
	try
	{
		size_t sector = volume->volume->GetSectorSize ();
		uint64_t alignedOffset = offset - (offset % sector);
		size_t prefix = (size_t) (offset - alignedOffset);
		size_t total = prefix + size;
		total = ((total + sector - 1) / sector) * sector;
		SecureBuffer buf (total);
		if (prefix || total != size)
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

static int fat_parse_dir (const uint8_t *dir, size_t bytes, VcDirEntry *entries, int max_entries)
{
	uint16_t lfn[260];
	memset (lfn, 0, sizeof (lfn));
	int count = 0;
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

		memset (entries[count].name, 0, sizeof (entries[count].name));
		strncpy (entries[count].name, name, sizeof (entries[count].name) - 1);
		entries[count].is_dir = (attr & 0x10) ? 1 : 0;
		entries[count].size = u32le (&dir[i + 28]);
		entries[count].first_cluster = u16le (&dir[i + 26]) | ((uint32_t) u16le (&dir[i + 20]) << 16);
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
		VcDirEntry entries[128];
		int n = fat_parse_dir (&dir[0], dir.size (), entries, 128);
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
	if (!volume || !entries || max_entries <= 0)
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
	return fat_parse_dir (&dir[0], dir.size (), entries, max_entries);
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
