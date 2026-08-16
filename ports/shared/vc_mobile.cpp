/*
 Copyright (c) 2026 Shivam Pingale. All rights reserved.

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
	if (!options || !options->path || !options->password)
	{
		if (error)
			*error = VC_ERR_ARGUMENT;
		return nullptr;
	}

	try
	{
		shared_ptr <VolumePassword> password (new VolumePassword (
			reinterpret_cast <const uint8 *> (options->password),
			options->password_len ? options->password_len : strlen (options->password)));

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
		SecureBuffer buf (size);
		memcpy (buf.Ptr (), buffer, size);
		volume->volume->WriteSectors (buf, offset);
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

int vc_list_root (VcVolume *volume, VcDirEntry *entries, int max_entries)
{
	if (!volume || !entries || max_entries <= 0)
		return VC_ERR_ARGUMENT;

	uint32_t sector = vc_sector_size (volume);
	std::vector <uint8_t> boot (sector);
	if (vc_read (volume, 0, &boot[0], sector) != VC_OK)
		return VC_ERR_IO;

	uint16_t bytesPerSector = u16le (&boot[11]);
	uint8_t sectorsPerCluster = boot[13];
	uint16_t reserved = u16le (&boot[14]);
	uint8_t fats = boot[16];
	uint16_t rootEntries = u16le (&boot[17]);
	uint16_t fat16Sectors = u16le (&boot[22]);
	uint32_t fat32Sectors = u32le (&boot[36]);
	uint32_t rootCluster = u32le (&boot[44]);

	if (bytesPerSector == 0 || sectorsPerCluster == 0)
		return VC_ERR_FORMAT;

	uint32_t fatSize = fat16Sectors ? fat16Sectors : fat32Sectors;
	uint64_t rootOffset;
	size_t rootBytes;

	if (rootEntries)
	{
		rootOffset = (uint64_t) (reserved + fats * fatSize) * bytesPerSector;
		rootBytes = (size_t) rootEntries * 32;
	}
	else
	{
		uint64_t dataStart = (uint64_t) (reserved + fats * fatSize) * bytesPerSector;
		rootOffset = dataStart + (uint64_t) (rootCluster - 2) * sectorsPerCluster * bytesPerSector;
		rootBytes = (size_t) sectorsPerCluster * bytesPerSector;
	}

	std::vector <uint8_t> dir (rootBytes);
	if (vc_read (volume, rootOffset, &dir[0], rootBytes) != VC_OK)
		return VC_ERR_IO;

	int count = 0;
	for (size_t i = 0; i + 32 <= dir.size() && count < max_entries; i += 32)
	{
		uint8_t first = dir[i];
		if (first == 0)
			break;
		if (first == 0xE5 || (dir[i + 11] & 0x08) || (dir[i + 11] & 0x02))
			continue;

		char name[13];
		memset (name, 0, sizeof (name));
		memcpy (name, &dir[i], 8);
		for (int n = 7; n >= 0 && name[n] == ' '; --n)
			name[n] = 0;
		char ext[4];
		memset (ext, 0, sizeof (ext));
		memcpy (ext, &dir[i + 8], 3);
		for (int n = 2; n >= 0 && ext[n] == ' '; --n)
			ext[n] = 0;
		if (ext[0])
		{
			strncat (name, ".", sizeof (name) - strlen (name) - 1);
			strncat (name, ext, sizeof (name) - strlen (name) - 1);
		}

		memset (entries[count].name, 0, sizeof (entries[count].name));
		strncpy (entries[count].name, name, sizeof (entries[count].name) - 1);
		entries[count].is_dir = (dir[i + 11] & 0x10) ? 1 : 0;
		entries[count].size = u32le (&dir[i + 28]);
		++count;
	}
	return count;
}

int vc_read_file (VcVolume *volume, const char *path, void *buffer, size_t buffer_size, size_t *out_size)
{
	if (out_size)
		*out_size = 0;
	if (!volume || !path || !buffer)
		return VC_ERR_ARGUMENT;

	VcDirEntry entries[64];
	int n = vc_list_root (volume, entries, 64);
	if (n < 0)
		return n;

	for (int i = 0; i < n; ++i)
	{
		if (strcasecmp (entries[i].name, path) == 0)
		{
			if (entries[i].is_dir)
				return VC_ERR_UNSUPPORTED;
			size_t copy = entries[i].size < buffer_size ? (size_t) entries[i].size : buffer_size;
			/* Cluster-chain reads are added with FatFs; this first cut exposes
			   directory metadata so the DocumentsProvider can list files. */
			if (out_size)
				*out_size = copy;
			return VC_ERR_UNSUPPORTED;
		}
	}
	return VC_ERR_IO;
}
