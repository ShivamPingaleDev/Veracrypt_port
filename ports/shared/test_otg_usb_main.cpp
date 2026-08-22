/*
 Copyright (c) 2026 Shivam Mangesh Pingale. All rights reserved.

 12-phase host simulation of a whole encrypted USB disk.
 Open goes through /vcport-otg-dev/N (overlay File callbacks), never
 /proc/self/fd. Nothing is auto-mounted: probe does not call vc_open.
 Idea from OTG Master by moylali, https://github.com/moylali/OTGMaster.
*/

#include "vc_mobile.h"
#include "vc_otg_dev.h"

#include <cerrno>
#include <cstdio>
#include <cstring>
#include <fcntl.h>
#include <string>
#include <unistd.h>
#include <vector>
#include <sys/stat.h>

static const char *kPw = "vcport-otg-usb-sim-ok";
static const int kPim = 1;
static int gFail = 0;

struct SlotFile
{
	int fd;
	uint64_t base;
	uint64_t len;
};

static SlotFile g_slots[VC_OTG_MAX_SLOTS];

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

static void entropy (void)
{
	vc_entropy_reset ();
	int ur = open ("/dev/urandom", O_RDONLY);
	if (ur >= 0)
	{
		unsigned char buf[256];
		ssize_t n = read (ur, buf, sizeof (buf));
		close (ur);
		if (n > 0)
			vc_entropy_add (buf, (size_t) n);
	}
}

static int otg_read (int slot, uint64_t offset, void *buffer, size_t size)
{
	if (slot < 0 || slot >= VC_OTG_MAX_SLOTS || g_slots[slot].fd < 0)
		return -1;
	if (offset >= g_slots[slot].len)
		return 0;
	size_t n = size;
	if (offset + n > g_slots[slot].len)
		n = (size_t) (g_slots[slot].len - offset);
	if (pread (g_slots[slot].fd, buffer, n, (off_t) (g_slots[slot].base + offset)) != (ssize_t) n)
		return -1;
	return (int) n;
}

static int otg_write (int slot, uint64_t offset, const void *buffer, size_t size)
{
	if (slot < 0 || slot >= VC_OTG_MAX_SLOTS || g_slots[slot].fd < 0)
		return -1;
	if (offset >= g_slots[slot].len)
		return -1;
	size_t n = size;
	if (offset + n > g_slots[slot].len)
		n = (size_t) (g_slots[slot].len - offset);
	if (pwrite (g_slots[slot].fd, buffer, n, (off_t) (g_slots[slot].base + offset)) != (ssize_t) n)
		return -1;
	return (int) n;
}

static int64_t otg_size (int slot)
{
	if (slot < 0 || slot >= VC_OTG_MAX_SLOTS || g_slots[slot].fd < 0)
		return -1;
	return (int64_t) g_slots[slot].len;
}

static int otg_sector (int)
{
	return 512;
}

static int otg_ready (int slot)
{
	return slot >= 0 && slot < VC_OTG_MAX_SLOTS && g_slots[slot].fd >= 0;
}

static void bind_slot (int slot, int fd, uint64_t base, uint64_t len)
{
	g_slots[slot].fd = fd;
	g_slots[slot].base = base;
	g_slots[slot].len = len;
}

static void write_mbr (int fd, uint32_t startLba, uint32_t sectors)
{
	unsigned char mbr[512];
	memset (mbr, 0, sizeof (mbr));
	mbr[510] = 0x55;
	mbr[511] = 0xAA;
	unsigned char *p = mbr + 446;
	p[4] = 0x83;
	p[8] = (unsigned char) startLba;
	p[9] = (unsigned char) (startLba >> 8);
	p[10] = (unsigned char) (startLba >> 16);
	p[11] = (unsigned char) (startLba >> 24);
	p[12] = (unsigned char) sectors;
	p[13] = (unsigned char) (sectors >> 8);
	p[14] = (unsigned char) (sectors >> 16);
	p[15] = (unsigned char) (sectors >> 24);
	pwrite (fd, mbr, 512, 0);
}

static int probe_mbr_partition (int fd, uint64_t *outOff, uint64_t *outLen)
{
	unsigned char mbr[512];
	if (pread (fd, mbr, 512, 0) != 512)
		return 0;
	if (mbr[510] != 0x55 || mbr[511] != 0xAA)
		return 0;
	unsigned char *p = mbr + 446;
	if (p[4] == 0)
		return 0;
	uint32_t lba = (uint32_t) p[8] | ((uint32_t) p[9] << 8) | ((uint32_t) p[10] << 16) | ((uint32_t) p[11] << 24);
	uint32_t sec = (uint32_t) p[12] | ((uint32_t) p[13] << 8) | ((uint32_t) p[14] << 16) | ((uint32_t) p[15] << 24);
	if (lba == 0 || sec == 0)
		return 0;
	*outOff = (uint64_t) lba * 512ull;
	*outLen = (uint64_t) sec * 512ull;
	return 1;
}

static int create_vol (const char *path, uint64_t bytes, const char *cipher, const char *kdf,
	const char *const *keyfiles, size_t kfCount, uint64_t hidden, const char *hiddenPw)
{
	entropy ();
	VcCreateOptions c = {};
	c.path = path;
	c.password = kPw;
	c.password_len = strlen (kPw);
	c.pim = kPim;
	c.size_bytes = bytes;
	c.cipher = cipher;
	c.kdf = kdf;
	c.keyfiles = keyfiles;
	c.keyfile_count = kfCount;
	c.filesystem = "FAT";
	c.hidden_size_bytes = hidden;
	c.hidden_password = hiddenPw;
	c.hidden_password_len = hiddenPw ? strlen (hiddenPw) : 0;
	c.hidden_pim = kPim;
	return vc_create_volume (&c);
}

static int copy_into (int diskFd, uint64_t at, const char *src)
{
	int s = open (src, O_RDONLY);
	if (s < 0)
		return -1;
	char buf[4096];
	uint64_t off = at;
	for (;;)
	{
		ssize_t n = read (s, buf, sizeof (buf));
		if (n < 0)
		{
			close (s);
			return -1;
		}
		if (n == 0)
			break;
		if (pwrite (diskFd, buf, (size_t) n, (off_t) off) != n)
		{
			close (s);
			return -1;
		}
		off += (uint64_t) n;
	}
	close (s);
	return 0;
}

static std::string tmp_path (const char *tmpl)
{
	char path[128];
	snprintf (path, sizeof (path), "%s", tmpl);
	int fd = mkstemp (path);
	if (fd >= 0)
		close (fd);
	return std::string (path);
}

static int sha_eq (VcVolume *vol, const char *inner, const char *expectPath)
{
	char out[] = "/tmp/vcport-otg-out-XXXXXX";
	int fd = mkstemp (out);
	if (fd < 0)
		return 0;
	close (fd);
	if (vc_export_file (vol, inner, out) != VC_OK)
	{
		unlink (out);
		return 0;
	}
	int a = open (expectPath, O_RDONLY);
	int b = open (out, O_RDONLY);
	if (a < 0 || b < 0)
	{
		if (a >= 0)
			close (a);
		if (b >= 0)
			close (b);
		unlink (out);
		return 0;
	}
	char ba[4096], bb[4096];
	int ok = 1;
	for (;;)
	{
		ssize_t na = read (a, ba, sizeof (ba));
		ssize_t nb = read (b, bb, sizeof (bb));
		if (na != nb || memcmp (ba, bb, (size_t) na) != 0)
		{
			ok = 0;
			break;
		}
		if (na == 0)
			break;
	}
	close (a);
	close (b);
	unlink (out);
	return ok;
}

int main (void)
{
	printf ("VC Port 12-phase whole encrypted USB simulation\n");
	printf ("OTG Master idea (moylali / https://github.com/moylali/OTGMaster) — no auto-mount\n");
	for (int i = 0; i < VC_OTG_MAX_SLOTS; ++i)
		g_slots[i].fd = -1;
	VcOtgBackend backend = {};
	backend.read_at = otg_read;
	backend.write_at = otg_write;
	backend.size = otg_size;
	backend.sector_size = otg_sector;
	backend.ready = otg_ready;
	vc_otg_set_backend (&backend);
	vc_runtime_start ();

	const uint32_t startLba = 2048;
	const uint32_t partSecs = 4096; /* 2 MiB */
	const uint64_t partOff = (uint64_t) startLba * 512ull;
	const uint64_t partLen = (uint64_t) partSecs * 512ull;
	const uint64_t diskLen = partOff + partLen + 512ull;

	std::string partA = tmp_path ("/tmp/vcport-usb-part-a-XXXXXX");
	std::string diskA = tmp_path ("/tmp/vcport-usb-disk-a-XXXXXX");
	std::string partB = tmp_path ("/tmp/vcport-usb-part-b-XXXXXX");
	std::string diskB = tmp_path ("/tmp/vcport-usb-disk-b-XXXXXX");
	std::string kf = tmp_path ("/tmp/vcport-usb-kf-XXXXXX");
	std::string note = tmp_path ("/tmp/vcport-usb-note-XXXXXX");
	std::string jpg = tmp_path ("/tmp/vcport-usb-jpg-XXXXXX");
	std::string bak = tmp_path ("/tmp/vcport-usb-bak-XXXXXX");
	std::string hiddenPw = "vcport-otg-hidden-sim-ok";

	printf ("\n======== phase 1 create whole encrypted USB A (MBR + partition) ========\n");
	expect (create_vol (partA.c_str (), 2ull * 1024ull * 1024ull, "AES", "HMAC-SHA-512", nullptr, 0, 0, nullptr) == VC_OK,
		"create 2 MiB FAT volume that will live in a USB partition");
	int da = open (diskA.c_str (), O_RDWR | O_TRUNC);
	expect (da >= 0, "open USB A disk image");
	if (da >= 0)
	{
		ftruncate (da, (off_t) diskLen);
		write_mbr (da, startLba, partSecs);
		expect (copy_into (da, partOff, partA.c_str ()) == 0, "copy encrypted volume into USB partition");
	}

	printf ("\n======== phase 2 probe MBR — do not auto-mount ========\n");
	uint64_t probedOff = 0, probedLen = 0;
	expect (probe_mbr_partition (da, &probedOff, &probedLen) == 1, "MBR partition 1 found");
	expect (probedOff == partOff && probedLen == partLen, "partition offset/length match");
	expect (vc_otg_is_path ("/vcport-otg-dev/0") == 1, "native path is /vcport-otg-dev/N");
	expect (vc_otg_is_path ("/proc/self/fd/7") == 0, "proc fd is not an OTG path");

	printf ("\n======== phase 3 bind USB A and Open (password + PIM) ========\n");
	bind_slot (0, da, partOff, partLen);
	VcOpenOptions oa = {};
	oa.path = "/vcport-otg-dev/0";
	oa.password = kPw;
	oa.password_len = strlen (kPw);
	oa.pim = kPim;
	int err = 0;
	VcVolume *a = vc_open (&oa, &err);
	expect (a != nullptr && err == VC_OK, "open whole-USB partition via /vcport-otg-dev/0");

	printf ("\n======== phase 4 wrong password ========\n");
	VcOpenOptions bad = oa;
	bad.password = "wrong-usb-password";
	bad.password_len = strlen (bad.password);
	err = 0;
	expect (vc_open (&bad, &err) == nullptr && err == VC_ERR_PASSWORD, "wrong password rejected on USB volume");

	printf ("\n======== phase 5 nested folder + mixed files ========\n");
	{
		int nfd = open (note.c_str (), O_RDWR | O_TRUNC);
		int jfd = open (jpg.c_str (), O_RDWR | O_TRUNC);
		const char *body = "usb-nested-ok\n";
		write (nfd, body, 14);
		close (nfd);
		unsigned char blob[1024];
		for (int i = 0; i < 1024; ++i)
			blob[i] = (unsigned char) i;
		write (jfd, blob, sizeof (blob));
		close (jfd);
	}
	expect (vc_mkdir (a, "/", "PHOTOS") == VC_OK, "mkdir PHOTOS on USB FAT");
	expect (vc_import_file (a, "PHOTOS", note.c_str (), "NOTE.TXT") == VC_OK, "import NOTE.TXT into nested folder");
	expect (vc_import_file (a, "/", jpg.c_str (), "clip.mp4") == VC_OK, "import clip.mp4 at USB root");

	printf ("\n======== phase 6 export hashes match ========\n");
	expect (sha_eq (a, "PHOTOS/NOTE.TXT", note.c_str ()) != 0, "NOTE.TXT roundtrip from USB");
	expect (sha_eq (a, "clip.mp4", jpg.c_str ()) != 0, "clip.mp4 roundtrip from USB");

	printf ("\n======== phase 7 second USB with keyfile (whole disk, no MBR) ========\n");
	expect (vc_generate_keyfile (kf.c_str (), 64) == VC_OK, "generate USB keyfile");
	const char *kfs[] = { kf.c_str () };
	expect (create_vol (partB.c_str (), 2ull * 1024ull * 1024ull, "AES(Twofish(Serpent))", "HMAC-SHA-512", kfs, 1, 0, nullptr) == VC_OK,
		"create cascade+keyfile volume for USB B");
	int db = open (diskB.c_str (), O_RDWR | O_TRUNC);
	expect (db >= 0, "open USB B disk image");
	if (db >= 0)
	{
		struct stat st;
		stat (partB.c_str (), &st);
		ftruncate (db, st.st_size);
		expect (copy_into (db, 0, partB.c_str ()) == 0, "USB B is whole-disk encrypted (LBA 0)");
		bind_slot (1, db, 0, (uint64_t) st.st_size);
	}
	VcOpenOptions ob = {};
	ob.path = "/vcport-otg-dev/1";
	ob.password = kPw;
	ob.password_len = strlen (kPw);
	ob.pim = kPim;
	ob.keyfiles = kfs;
	ob.keyfile_count = 1;
	err = 0;
	VcVolume *b = vc_open (&ob, &err);
	expect (b != nullptr && err == VC_OK, "open whole-disk USB B with keyfile via /vcport-otg-dev/1");
	err = 0;
	VcOpenOptions obNoKf = ob;
	obNoKf.keyfiles = nullptr;
	obNoKf.keyfile_count = 0;
	expect (vc_open (&obNoKf, &err) == nullptr, "USB B rejects password-only (keyfile required)");

	printf ("\n======== phase 8 copy between two mounted USB volumes ========\n");
	char stage[] = "/tmp/vcport-usb-xfer-XXXXXX";
	int sfd = mkstemp (stage);
	close (sfd);
	expect (vc_export_file (a, "PHOTOS/NOTE.TXT", stage) == VC_OK, "export from USB A");
	expect (vc_import_file (b, "/", stage, "FROM-A.TXT") == VC_OK, "import onto USB B");
	expect (sha_eq (b, "FROM-A.TXT", note.c_str ()) != 0, "file survived USB A → USB B");
	unlink (stage);

	printf ("\n======== phase 9 hidden nested volume on USB A-style disk ========\n");
	std::string partH = tmp_path ("/tmp/vcport-usb-part-h-XXXXXX");
	std::string diskH = tmp_path ("/tmp/vcport-usb-disk-h-XXXXXX");
	expect (create_vol (partH.c_str (), 8ull * 1024ull * 1024ull, "AES", "HMAC-SHA-512", nullptr, 0,
			2ull * 1024ull * 1024ull, hiddenPw.c_str ()) == VC_OK,
		"create outer+hidden volume to place on USB");
	int dh = open (diskH.c_str (), O_RDWR | O_TRUNC);
	expect (dh >= 0, "open hidden USB disk");
	if (dh >= 0)
	{
		struct stat st;
		stat (partH.c_str (), &st);
		ftruncate (dh, st.st_size);
		copy_into (dh, 0, partH.c_str ());
		bind_slot (2, dh, 0, (uint64_t) st.st_size);
	}
	VcOpenOptions oh = oa;
	oh.path = "/vcport-otg-dev/2";
	err = 0;
	VcVolume *outer = vc_open (&oh, &err);
	expect (outer != nullptr, "open outer USB with outer password");
	if (outer)
		vc_close (outer);
	oh.password = hiddenPw.c_str ();
	oh.password_len = hiddenPw.size ();
	err = 0;
	VcVolume *inner = vc_open (&oh, &err);
	expect (inner != nullptr, "open hidden USB with nested password (no hidden checkbox)");
	if (inner)
		vc_close (inner);

	printf ("\n======== phase 10 backup header, corrupt, restore on USB A ========\n");
	if (a)
	{
		vc_close (a);
		a = nullptr;
	}
	expect (vc_backup_headers ("/vcport-otg-dev/0", bak.c_str (), kPw, strlen (kPw), kPim, nullptr, 0) == VC_OK,
		"backup USB A headers through OTG path");
	{
		unsigned char junk[64 * 1024];
		memset (junk, 0xFF, sizeof (junk));
		expect (pwrite (da, junk, sizeof (junk), (off_t) partOff) == (ssize_t) sizeof (junk),
			"overwrite primary header on the USB image");
	}
	err = 0;
	expect (vc_open (&oa, &err) == nullptr, "USB A primary corruption rejects open");
	oa.use_backup_header = 1;
	err = 0;
	VcVolume *fromBak = vc_open (&oa, &err);
	expect (fromBak != nullptr, "open USB A with backup header after corruption");
	if (fromBak)
		vc_close (fromBak);
	expect (vc_restore_headers ("/vcport-otg-dev/0", bak.c_str (), kPw, strlen (kPw), kPim, nullptr, 0) == VC_OK,
		"restore USB A from .bak via OTG path");
	oa.use_backup_header = 0;
	err = 0;
	a = vc_open (&oa, &err);
	expect (a != nullptr, "open USB A after restore");

	printf ("\n======== phase 11 dismount and reopen (app exit) ========\n");
	if (a)
		vc_close (a);
	if (b)
		vc_close (b);
	a = nullptr;
	b = nullptr;
	err = 0;
	a = vc_open (&oa, &err);
	expect (a != nullptr, "reopen USB A after dismount");
	expect (sha_eq (a, "PHOTOS/NOTE.TXT", note.c_str ()) != 0, "nested file still intact after USB reopen");
	if (a)
		vc_close (a);

	printf ("\n======== phase 12 no auto-mount leftovers; release slots ========\n");
	bind_slot (0, -1, 0, 0);
	bind_slot (1, -1, 0, 0);
	bind_slot (2, -1, 0, 0);
	err = 0;
	expect (vc_open (&oa, &err) == nullptr, "USB A will not open after slot release (not auto-mounted)");
	if (da >= 0)
		close (da);
	if (db >= 0)
		close (db);
	if (dh >= 0)
		close (dh);
	unlink (partA.c_str ());
	unlink (diskA.c_str ());
	unlink (partB.c_str ());
	unlink (diskB.c_str ());
	unlink (partH.c_str ());
	unlink (diskH.c_str ());
	unlink (kf.c_str ());
	unlink (note.c_str ());
	unlink (jpg.c_str ());
	unlink (bak.c_str ());
	vc_otg_set_backend (nullptr);

	printf ("\n%s\n", gFail ? "USB 12-phase FAIL" : "USB 12-phase PASS");
	return gFail ? 1 : 0;
}
