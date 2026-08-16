/*
 Copyright (c) 2026 Shivam Mangesh Pingale. All rights reserved.

 Host test: create a tiny AES/SHA-512 FAT volume, then open/list/export
 through vc_mobile. Password is only in this file.
*/

#include "vc_mobile.h"

#include "Volume/EncryptionAlgorithm.h"
#include "Volume/EncryptionModeXTS.h"
#include "Volume/Pkcs5Kdf.h"
#include "Volume/Volume.h"
#include "Volume/VolumeHeader.h"
#include "Volume/VolumeLayout.h"
#include "Volume/VolumePassword.h"
#include "Platform/File.h"

#include <cstdio>
#include <cstring>
#include <string>
#include <unistd.h>
#include <vector>

using namespace VeraCrypt;

static const char *kPassword = "vcport-test-volume";
static const int kPim = 1;
static int gFail = 0;

static void expect (bool ok, const char *msg)
{
	if (ok)
	{
		printf ("  ok  %s\n", msg);
		return;
	}
	printf (" FAIL %s\n", msg);
	gFail = 1;
}

static void put16 (std::vector<uint8_t> &b, size_t off, uint16_t v)
{
	b[off] = (uint8_t) v;
	b[off + 1] = (uint8_t) (v >> 8);
}

static void put32 (std::vector<uint8_t> &b, size_t off, uint32_t v)
{
	b[off] = (uint8_t) v;
	b[off + 1] = (uint8_t) (v >> 8);
	b[off + 2] = (uint8_t) (v >> 16);
	b[off + 3] = (uint8_t) (v >> 24);
}

static void dirent (std::vector<uint8_t> &dir, size_t index, const char *name83, uint8_t attr, uint32_t cluster, uint32_t size)
{
	size_t o = index * 32;
	memset (&dir[o], ' ', 11);
	memcpy (&dir[o], name83, 11);
	dir[o + 11] = attr;
	put16 (dir, o + 20, (uint16_t) (cluster >> 16));
	put16 (dir, o + 26, (uint16_t) cluster);
	put32 (dir, o + 28, size);
}

static std::vector<uint8_t> make_fat16 ()
{
	const uint32_t bps = 512;
	const uint32_t spc = 1;
	const uint32_t reserved = 1;
	const uint32_t fats = 2;
	const uint32_t rootEnt = 16;
	const uint32_t fatSecs = 16;
	const uint32_t totalSecs = 2048;
	const uint32_t rootSecs = (rootEnt * 32) / bps;
	const uint32_t dataStart = reserved + fats * fatSecs + rootSecs;

	std::vector<uint8_t> img (totalSecs * bps, 0);
	img[0] = 0xEB;
	img[1] = 0x3C;
	img[2] = 0x90;
	memcpy (&img[3], "MSDOS5.0", 8);
	put16 (img, 11, (uint16_t) bps);
	img[13] = (uint8_t) spc;
	put16 (img, 14, (uint16_t) reserved);
	img[16] = (uint8_t) fats;
	put16 (img, 17, (uint16_t) rootEnt);
	put16 (img, 19, (uint16_t) totalSecs);
	img[21] = 0xF8;
	put16 (img, 22, (uint16_t) fatSecs);
	put16 (img, 24, 1);
	put16 (img, 26, 1);
	memcpy (&img[54], "FAT16   ", 8);

	auto fatAt = [&](uint32_t fatIndex, uint32_t cluster, uint16_t val)
	{
		size_t off = (reserved + fatIndex * fatSecs) * bps + cluster * 2;
		put16 (img, off, val);
	};
	for (uint32_t f = 0; f < fats; ++f)
	{
		fatAt (f, 0, 0xFFF8);
		fatAt (f, 1, 0xFFFF);
		fatAt (f, 2, 0xFFFF);
		fatAt (f, 3, 0xFFFF);
		fatAt (f, 4, 0xFFFF);
	}

	const char *hello = "hello-vc-port\n";
	const char *nested = "nested-ok\n";
	uint32_t helloLen = (uint32_t) strlen (hello);
	uint32_t nestedLen = (uint32_t) strlen (nested);

	std::vector<uint8_t> root (rootEnt * 32, 0);
	dirent (root, 0, "HELLO   TXT", 0x20, 2, helloLen);
	dirent (root, 1, "DOCS       ", 0x10, 3, 0);
	memcpy (&img[reserved * bps + fats * fatSecs * bps], &root[0], root.size ());

	std::vector<uint8_t> sub (bps, 0);
	dirent (sub, 0, "NESTED  TXT", 0x20, 4, nestedLen);
	memcpy (&img[(dataStart + 1) * bps], &sub[0], bps);

	memcpy (&img[dataStart * bps], hello, helloLen);
	memcpy (&img[(dataStart + 2) * bps], nested, nestedLen);
	return img;
}

static void create_volume (const char *path)
{
	const uint64 hostSize = 2 * 1024 * 1024;
	VolumeLayoutV2Normal layout;
	shared_ptr <VolumeHeader> header = layout.GetHeader ();
	SecureBuffer headerBuffer (layout.GetHeaderSize ());

	shared_ptr <VeraCrypt::EncryptionAlgorithm> ea (new VeraCrypt::AES ());
	shared_ptr <Pkcs5Kdf> kdf (new Pkcs5HmacSha512 ());
	shared_ptr <VolumePassword> password (new VolumePassword (
		reinterpret_cast <const uint8 *> (kPassword), strlen (kPassword)));

	VolumeHeaderCreationOptions opt;
	opt.EA = ea;
	opt.Kdf = kdf;
	opt.Type = VolumeType::Normal;
	opt.SectorSize = TC_SECTOR_SIZE_FILE_HOSTED_VOLUME;
	opt.VolumeDataStart = (uint64) layout.GetHeaderSize () * 2;
	opt.VolumeDataSize = layout.GetMaxDataSize (hostSize);

	SecureBuffer master (ea->GetKeySize () * 2);
	for (size_t i = 0; i < master.Size (); ++i)
		master[i] = (uint8) (i + 1);
	if (memcmp (master.Ptr (), master.Ptr () + master.Size () / 2, master.Size () / 2) == 0)
		throw ParameterIncorrect (SRC_POS);
	opt.DataKey = master;

	SecureBuffer salt (VolumeHeader::GetSaltSize ());
	for (size_t i = 0; i < salt.Size (); ++i)
		salt[i] = (uint8) (0xA5 ^ i);
	opt.Salt = salt;

	SecureBuffer headerKey (VolumeHeader::GetHeaderKeyDerivationSize (kdf));
	if (kdf->DeriveKey (headerKey, *password, kPim, salt) != 0)
		throw ParameterIncorrect (SRC_POS);
	opt.HeaderKey = headerKey;

	header->Create (headerBuffer, opt);

	File file;
	file.Open (path, File::CreateReadWrite, File::ShareNone);
	file.Write (headerBuffer);
	file.SetLength (hostSize);
	file.SeekEnd (layout.GetBackupHeaderOffset ());
	file.Write (headerBuffer);
	file.Close ();
}

static bool has_name (VcDirEntry *entries, int n, const char *name, int wantDir)
{
	for (int i = 0; i < n; ++i)
	{
		if (strcasecmp (entries[i].name, name) == 0 && entries[i].is_dir == wantDir)
			return true;
	}
	return false;
}

int main ()
{
	char path[] = "/tmp/vcport-fat-fixture-XXXXXX";
	int fd = mkstemp (path);
	if (fd < 0)
	{
		perror ("mkstemp");
		return 1;
	}
	close (fd);

	try
	{
		create_volume (path);
	}
	catch (...)
	{
		fprintf (stderr, "create_volume failed\n");
		unlink (path);
		return 1;
	}

	int err = 0;
	VcOpenOptions bad = {};
	bad.path = path;
	bad.password = "wrong-password";
	bad.password_len = 14;
	bad.pim = kPim;
	expect (vc_open (&bad, &err) == nullptr && err == VC_ERR_PASSWORD, "wrong password");

	VcOpenOptions opt = {};
	opt.path = path;
	opt.password = kPassword;
	opt.password_len = strlen (kPassword);
	opt.pim = kPim;
	err = 0;
	VcVolume *vol = vc_open (&opt, &err);
	expect (vol != nullptr && err == VC_OK, "open fixture");
	if (!vol)
	{
		unlink (path);
		return 1;
	}

	std::vector<uint8_t> fat = make_fat16 ();
	expect (vc_write (vol, 0, &fat[0], fat.size ()) == VC_OK, "write FAT");

	VcDirEntry entries[32];
	int n = vc_list_root (vol, entries, 32);
	expect (n >= 2, "list root count");
	expect (has_name (entries, n, "HELLO.TXT", 0), "HELLO.TXT at root");
	expect (has_name (entries, n, "DOCS", 1), "DOCS folder at root");

	char tmpout[] = "/tmp/vcport-export-XXXXXX";
	int ofd = mkstemp (tmpout);
	if (ofd >= 0)
		close (ofd);
	expect (vc_export_file (vol, "HELLO.TXT", tmpout) == VC_OK, "export HELLO.TXT");
	FILE *f = fopen (tmpout, "rb");
	char buf[64];
	size_t got = f ? fread (buf, 1, sizeof (buf), f) : 0;
	if (f)
		fclose (f);
	expect (got == 14 && memcmp (buf, "hello-vc-port\n", 14) == 0, "HELLO.TXT contents");

	n = vc_list_dir (vol, "DOCS", entries, 32);
	expect (n >= 1, "list DOCS");
	expect (has_name (entries, n, "NESTED.TXT", 0), "NESTED.TXT in DOCS");
	expect (vc_export_file (vol, "DOCS/NESTED.TXT", tmpout) == VC_OK, "export nested path");
	f = fopen (tmpout, "rb");
	got = f ? fread (buf, 1, sizeof (buf), f) : 0;
	if (f)
		fclose (f);
	expect (got == 10 && memcmp (buf, "nested-ok\n", 10) == 0, "NESTED.TXT contents");
	expect (vc_list_dir (vol, "..", entries, 32) < 0, "reject ..");

	uint8_t marker[8];
	memcpy (marker, "EXFAT   ", 8);
	expect (vc_write (vol, 3, marker, 8) == VC_OK, "write exFAT marker");
	expect (vc_list_root (vol, entries, 32) == VC_ERR_UNSUPPORTED, "exFAT unsupported");

	vc_close (vol);
	unlink (path);
	unlink (tmpout);
	if (gFail)
	{
		printf ("VOLUME TESTS FAILED\n");
		return 1;
	}
	printf ("VOLUME TESTS PASSED\n");
	return 0;
}
