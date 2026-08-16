/*
 Copyright (c) 2026 Shivam Mangesh Pingale. All rights reserved.

 Host simulation: create a FAT container with password, PIM, and a biometric
 keyfile (phone-unlock stand-in, no Keystore/Keychain), store files, close
 (dismount), reopen, and check the files are still there. Remember uses the
 VCF2 factor bundle. High PIMs (98, 485) are codec-only — HMAC-SHA-512 at
 those iteration counts is too slow for CI volume create.

 Independent cases run in parallel on CPU threads. PBKDF2-HMAC-SHA-512 is
 sequential per volume, so a GPU cannot shorten one unlock and would move
 key material into VRAM. Each case still uses the same VeraCrypt KDF.
*/

#include "vc_mobile.h"

#include <atomic>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <string>
#include <strings.h>
#include <sys/stat.h>
#include <thread>
#include <unistd.h>
#include <vector>

static std::atomic<int> gFail {0};
static std::atomic<int> gPass {0};
static std::mutex gLog;
static thread_local const char *gScope = "setup";
static thread_local std::string gBuf;

static void flush_log (void)
{
	if (gBuf.empty ())
		return;
	std::lock_guard<std::mutex> lock (gLog);
	fputs (gBuf.c_str (), stdout);
	fflush (stdout);
	gBuf.clear ();
}

struct FlushOnExit
{
	~FlushOnExit () { flush_log (); }
};

static void banner (const char *title)
{
	gBuf += "\n== ";
	gBuf += title;
	gBuf += " ==\n";
}

static void expect (bool ok, const char *msg)
{
	char line[384];
	const char *scope = gScope ? gScope : "setup";
	if (ok)
	{
		snprintf (line, sizeof (line), "  ok  [%s] %s\n", scope, msg);
		gPass.fetch_add (1);
	}
	else
	{
		snprintf (line, sizeof (line), " FAIL [%s] %s\n", scope, msg);
		gFail.store (1);
	}
	gBuf += line;
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

static void fill_entropy (void)
{
	FlushOnExit flush;
	vc_entropy_reset ();
	for (int i = 0; i < 320; ++i)
		vc_entropy_add ("0123456789abcdef0123456789abcdef", 32);
	expect (vc_entropy_percent () == 100, "entropy bar fills");
}

static int make_temp (char *buf, size_t cap, const char *tmpl)
{
	if (cap < 8)
		return -1;
	snprintf (buf, cap, "%s", tmpl);
	int fd = mkstemp (buf);
	if (fd < 0)
		return -1;
	close (fd);
	return 0;
}

static int write_all (const char *path, const void *data, size_t n)
{
	FILE *f = fopen (path, "wb");
	if (!f)
		return -1;
	size_t w = n ? fwrite (data, 1, n, f) : 0;
	fclose (f);
	return w == n ? 0 : -1;
}

static int read_all (const char *path, void *data, size_t cap, size_t *out)
{
	FILE *f = fopen (path, "rb");
	if (!f)
		return -1;
	size_t n = fread (data, 1, cap, f);
	fclose (f);
	*out = n;
	return 0;
}

static const char kB64[] =
	"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

static std::string b64_encode (const unsigned char *in, size_t n)
{
	std::string out;
	out.reserve (((n + 2) / 3) * 4);
	size_t i = 0;
	while (i + 2 < n)
	{
		unsigned v = ((unsigned) in[i] << 16) | ((unsigned) in[i + 1] << 8) | in[i + 2];
		out.push_back (kB64[(v >> 18) & 63]);
		out.push_back (kB64[(v >> 12) & 63]);
		out.push_back (kB64[(v >> 6) & 63]);
		out.push_back (kB64[v & 63]);
		i += 3;
	}
	if (i < n)
	{
		unsigned v = (unsigned) in[i] << 16;
		if (i + 1 < n)
			v |= (unsigned) in[i + 1] << 8;
		out.push_back (kB64[(v >> 18) & 63]);
		out.push_back (kB64[(v >> 12) & 63]);
		if (i + 1 < n)
		{
			out.push_back (kB64[(v >> 6) & 63]);
			out.push_back ('=');
		}
		else
		{
			out.push_back ('=');
			out.push_back ('=');
		}
	}
	return out;
}

static int b64_val (char c)
{
	if (c >= 'A' && c <= 'Z')
		return c - 'A';
	if (c >= 'a' && c <= 'z')
		return 26 + c - 'a';
	if (c >= '0' && c <= '9')
		return 52 + c - '0';
	if (c == '+')
		return 62;
	if (c == '/')
		return 63;
	return -1;
}

static std::vector<unsigned char> b64_decode (const std::string &s)
{
	std::vector<unsigned char> out;
	int val = 0;
	int bits = 0;
	for (size_t i = 0; i < s.size (); ++i)
	{
		char c = s[i];
		if (c == '=' || c == '\n' || c == '\r')
			break;
		int d = b64_val (c);
		if (d < 0)
			continue;
		val = (val << 6) | d;
		bits += 6;
		if (bits >= 8)
		{
			bits -= 8;
			out.push_back ((unsigned char) ((val >> bits) & 0xff));
		}
	}
	return out;
}

struct FactorBundle
{
	int pim;
	std::string password;
	std::vector<unsigned char> biometric;
	std::string extra_keyfile;
};

static std::string vcf2_encode (const FactorBundle &b)
{
	std::string out = "VCF2\n";
	out += std::to_string (b.pim);
	out += "\n";
	out += b64_encode ((const unsigned char *) b.password.data (), b.password.size ());
	out += "\n";
	if (!b.biometric.empty ())
		out += b64_encode (b.biometric.data (), b.biometric.size ());
	out += "\n";
	if (!b.extra_keyfile.empty ())
	{
		out += b.extra_keyfile;
		out += "\n";
	}
	return out;
}

static FactorBundle vcf2_decode (const std::string &raw)
{
	FactorBundle b;
	b.pim = 0;
	if (raw.size () < 5 || raw.compare (0, 5, "VCF2\n") != 0)
		return b;
	std::vector<std::string> lines;
	size_t start = 0;
	while (start < raw.size ())
	{
		size_t nl = raw.find ('\n', start);
		if (nl == std::string::npos)
		{
			lines.push_back (raw.substr (start));
			break;
		}
		lines.push_back (raw.substr (start, nl - start));
		start = nl + 1;
	}
	if (lines.size () > 1 && !lines[1].empty ())
		b.pim = atoi (lines[1].c_str ());
	if (lines.size () > 2 && !lines[2].empty ())
	{
		std::vector<unsigned char> pw = b64_decode (lines[2]);
		b.password.assign ((const char *) pw.data (), pw.size ());
	}
	if (lines.size () > 3 && !lines[3].empty ())
		b.biometric = b64_decode (lines[3]);
	if (lines.size () > 4 && !lines[4].empty ())
		b.extra_keyfile = lines[4];
	return b;
}

static void test_vcf2_all_pim_passwords (void)
{
	FlushOnExit flush;
	gScope = "VCF2";
	banner ("VCF2 remember store (all PIM test passwords)");
	static const int kPims[] = { 0, 1, 5, 12, 98, 485 };
	unsigned char bio[64];
	for (int i = 0; i < 64; ++i)
		bio[i] = (unsigned char) (0xA5 ^ i);
	for (size_t i = 0; i < sizeof (kPims) / sizeof (kPims[0]); ++i)
	{
		char label[80];
		snprintf (label, sizeof (label), "VCF2 PIM %d roundtrip", kPims[i]);
		FactorBundle src;
		src.pim = kPims[i];
		src.password = "pim-test-password";
		src.biometric.assign (bio, bio + sizeof (bio));
		src.extra_keyfile = "/tmp/extra.key";
		FactorBundle got = vcf2_decode (vcf2_encode (src));
		expect (got.pim == src.pim
			&& got.password == src.password
			&& got.biometric == src.biometric
			&& got.extra_keyfile == src.extra_keyfile,
			label);
	}
	FactorBundle emptyPw;
	emptyPw.pim = 12;
	emptyPw.password = "";
	emptyPw.biometric.assign (bio, bio + 8);
	FactorBundle gotEmpty = vcf2_decode (vcf2_encode (emptyPw));
	expect (gotEmpty.pim == 12 && gotEmpty.password.empty () && gotEmpty.biometric.size () == 8,
		"VCF2 empty password + biometric");
}

struct SimCase
{
	const char *label;
	const char *password; /* NULL = generate 64 chars */
	int pim;
	int use_bio;
	int use_extra_kf;
	int remember_vcf2;
	int full_negatives;
};

static void run_sim (const SimCase &c);

static void fill_keyfiles (const char **slots, size_t *count,
	int use_bio, const char *bio_path, int use_extra, const char *extra_path)
{
	*count = 0;
	if (use_bio && bio_path && bio_path[0])
		slots[(*count)++] = bio_path;
	if (use_extra && extra_path && extra_path[0])
		slots[(*count)++] = extra_path;
}

static VcVolume *open_factors (const char *vol_path, const char *password, int pim,
	const char **keyfiles, size_t keyfile_count, int *err)
{
	VcOpenOptions opt = {};
	opt.path = vol_path;
	opt.password = password ? password : "";
	opt.password_len = password ? strlen (password) : 0;
	opt.pim = pim;
	opt.keyfiles = keyfile_count ? keyfiles : nullptr;
	opt.keyfile_count = keyfile_count;
	*err = 0;
	return vc_open (&opt, err);
}

static int create_factors (const char *vol_path, const char *password, int pim,
	const char **keyfiles, size_t keyfile_count)
{
	VcCreateOptions opt = {};
	opt.path = vol_path;
	opt.password = password ? password : "";
	opt.password_len = password ? strlen (password) : 0;
	opt.pim = pim;
	opt.size_bytes = 2ull * 1024ull * 1024ull;
	opt.cipher = "AES(Twofish(Serpent))";
	opt.kdf = "HMAC-SHA-512";
	opt.keyfiles = keyfile_count ? keyfiles : nullptr;
	opt.keyfile_count = keyfile_count;
	return vc_create_volume (&opt);
}

/* One user session on the NativeBridge / JNI / Swift surface: every security
 * control the phone uses except Keystore/Keychain hardware and FLAG_SECURE. */
static void run_phone_session (void)
{
	FlushOnExit flush;
	gScope = "phone session";
	banner ("device simulation (NativeBridge create/store/encrypt/decrypt)");

	vc_entropy_reset ();
	for (int i = 0; i < 320; ++i)
		vc_entropy_add ("0123456789abcdef0123456789abcdef", 32);
	expect (vc_entropy_percent () == 100, "resetEntropy/addEntropy/entropyPercent");

	vc_progress_reset ();
	vc_progress_set (5, "Create volume");
	char phase[96];
	vc_progress_phase (phase, sizeof (phase));
	expect (vc_progress_percent () == 5 && strstr (phase, "Create") != nullptr,
		"resetProgress/setProgress/progressPercent/progressPhase");

	char pw[80];
	memset (pw, 0, sizeof (pw));
	expect (vc_generate_password (pw, sizeof (pw), 64) == 64, "generatePassword");

	char bio[128] = "", extra[128] = "", vol[128] = "", plain[128] = "";
	char wrap[128] = "", exported[128] = "", bak[128] = "";
	char unwrap_dir[] = "/tmp/vcport-life-unw-XXXXXX";
	expect (make_temp (bio, sizeof (bio), "/tmp/vcport-life-ps-bio-XXXXXX") == 0, "temp biometric");
	expect (make_temp (extra, sizeof (extra), "/tmp/vcport-life-ps-kf-XXXXXX") == 0, "temp keyfile");
	expect (make_temp (vol, sizeof (vol), "/tmp/vcport-life-ps-vol-XXXXXX") == 0, "temp volume");
	expect (make_temp (plain, sizeof (plain), "/tmp/vcport-life-ps-txt-XXXXXX") == 0, "temp plaintext");
	expect (make_temp (wrap, sizeof (wrap), "/tmp/vcport-life-ps-vcpw-XXXXXX") == 0, "temp wrap");
	expect (make_temp (exported, sizeof (exported), "/tmp/vcport-life-ps-out-XXXXXX") == 0, "temp export");
	expect (make_temp (bak, sizeof (bak), "/tmp/vcport-life-ps-bak-XXXXXX") == 0, "temp header backup");
	expect (mkdtemp (unwrap_dir) != nullptr, "temp unwrap dir");

	expect (vc_generate_keyfile (bio, 64) == VC_OK, "generateKeyfile biometric");
	expect (vc_generate_keyfile (extra, 128) == VC_OK, "generateKeyfile");

	const char *kfs[2] = { bio, extra };
	static const char kPayload[] = "from-device-encrypted\n";
	expect (write_all (plain, kPayload, sizeof (kPayload) - 1) == 0, "write plaintext");

	expect (vc_wrap_file (plain, wrap, pw, strlen (pw), "NOTE.TXT") == VC_OK, "wrapFile encrypt");
	expect (vc_is_wrap (wrap) == 1, "isWrap");
	char unwrapped_path[512];
	memset (unwrapped_path, 0, sizeof (unwrapped_path));
	expect (vc_unwrap_file (wrap, unwrap_dir, pw, strlen (pw), unwrapped_path, sizeof (unwrapped_path)) == VC_OK,
		"unwrapFile decrypt");
	char got[64];
	size_t got_n = 0;
	expect (read_all (unwrapped_path, got, sizeof (got), &got_n) == 0
		&& got_n == sizeof (kPayload) - 1 && memcmp (got, kPayload, got_n) == 0,
		"decrypted wrap matches plaintext");

	expect (create_factors (vol, pw, 1, kfs, 2) == VC_OK,
		"createVolume AES(Twofish(Serpent))/HMAC-SHA-512");

	int err = 0;
	VcVolume *bad = open_factors (vol, "wrong-password", 1, kfs, 2, &err);
	expect (bad == nullptr && err == VC_ERR_PASSWORD, "openVolume wrong password");
	if (bad)
		vc_close (bad);

	err = 0;
	VcVolume *volh = open_factors (vol, pw, 1, kfs, 2, &err);
	expect (volh != nullptr && err == VC_OK, "openVolume");
	if (!volh)
	{
		unlink (bio); unlink (extra); unlink (vol); unlink (plain); unlink (wrap);
		unlink (exported); unlink (bak); unlink (unwrapped_path);
		rmdir (unwrap_dir);
		return;
	}

	expect (vc_size (volh) >= 2ull * 1024ull * 1024ull - 512ull * 1024ull, "volumeSize");
	char info[256];
	expect (vc_volume_info (volh, info, sizeof (info)) == VC_OK
		&& strstr (info, "AES(Twofish(Serpent))") != nullptr
		&& strstr (info, "HMAC-SHA-512") != nullptr, "volumeInfo");

	expect (vc_mkdir (volh, "/", "VAULT") == VC_OK, "mkdir");
	expect (vc_import_file (volh, "/", plain, "HELLO.TXT") == VC_OK, "importFile root");
	expect (vc_import_file (volh, "VAULT", plain, "NOTE.TXT") == VC_OK, "importFile");
	VcDirEntry entries[16];
	int n = vc_list_root (volh, entries, 16);
	expect (n >= 2 && has_name (entries, n, "VAULT", 1) && has_name (entries, n, "HELLO.TXT", 0), "listRoot");
	n = vc_list_dir (volh, "VAULT", entries, 16);
	expect (has_name (entries, n, "NOTE.TXT", 0), "listDir");
	n = vc_list_dir_from (volh, "/", entries, 1, 0);
	expect (n == 1, "listDir offset");
	expect (vc_export_file (volh, "VAULT/NOTE.TXT", exported) == VC_OK, "exportFile");
	got_n = 0;
	expect (read_all (exported, got, sizeof (got), &got_n) == 0
		&& got_n == sizeof (kPayload) - 1 && memcmp (got, kPayload, got_n) == 0,
		"stored file decrypts after export");
	expect (vc_rename (volh, "VAULT/NOTE.TXT", "MEMO.TXT") == VC_OK, "renameFile");
	n = vc_list_dir (volh, "VAULT", entries, 16);
	expect (has_name (entries, n, "MEMO.TXT", 0), "renamed file listed");
	expect (vc_delete_file (volh, "VAULT/MEMO.TXT") == VC_OK, "deleteFile");
	expect (vc_import_file (volh, "VAULT", plain, "NOTE.TXT") == VC_OK, "importFile again");
	expect (vc_wipe_free_space (volh) == VC_OK, "wipeFreeSpace");
	vc_close (volh);
	expect (true, "closeVolume");

	err = 0;
	volh = open_factors (vol, pw, 1, kfs, 2, &err);
	expect (volh != nullptr && err == VC_OK, "openVolume after dismount");
	if (volh)
	{
		expect (vc_export_file (volh, "VAULT/NOTE.TXT", exported) == VC_OK, "exportFile after reopen");
		got_n = 0;
		expect (read_all (exported, got, sizeof (got), &got_n) == 0
			&& got_n == sizeof (kPayload) - 1 && memcmp (got, kPayload, got_n) == 0,
			"payload still matches after reopen");
		expect (vc_delete_file (volh, "VAULT/NOTE.TXT") == VC_OK, "deleteFile before rmdir");
		expect (vc_rmdir (volh, "VAULT") == VC_OK, "rmdir");
		vc_close (volh);
	}

	expect (vc_backup_headers (vol, bak, pw, strlen (pw), 1, kfs, 2) == VC_OK, "backupHeaders");
	expect (vc_restore_headers (vol, bak, pw, strlen (pw), 1, kfs, 2) == VC_OK, "restoreHeaders");

	static const char kNew[] = "vcport-changed-password-ok";
	VcChangeHeaderOptions ch = {};
	ch.path = vol;
	ch.password = pw;
	ch.password_len = strlen (pw);
	ch.pim = 1;
	ch.keyfiles = kfs;
	ch.keyfile_count = 2;
	ch.new_password = kNew;
	ch.new_password_len = strlen (kNew);
	ch.new_pim = 1;
	ch.new_keyfiles = kfs;
	ch.new_keyfile_count = 2;
	expect (vc_change_header (&ch) == VC_OK, "changeHeader");
	err = 0;
	bad = open_factors (vol, pw, 1, kfs, 2, &err);
	expect (bad == nullptr && err == VC_ERR_PASSWORD, "old password rejected after changeHeader");
	if (bad)
		vc_close (bad);
	err = 0;
	volh = open_factors (vol, kNew, 1, kfs, 2, &err);
	expect (volh != nullptr && err == VC_OK, "openVolume with new password");
	if (volh)
		vc_close (volh);

	char bench[2048];
	expect (vc_benchmark (bench, sizeof (bench)) == VC_OK && strstr (bench, "MiB/s") != nullptr, "benchmark");

	unlink (bio); unlink (extra); unlink (vol); unlink (plain); unlink (wrap);
	unlink (exported); unlink (bak); unlink (unwrapped_path);
	rmdir (unwrap_dir);
}

static unsigned worker_count (size_t jobs)
{
	unsigned hw = std::thread::hardware_concurrency ();
	if (hw < 2)
		hw = 2;
	/* Leave cores for VeraCrypt EncryptionThreadPool (XTS import/export). */
	unsigned cap = hw / 2;
	if (cap < 1)
		cap = 1;
	if (cap > jobs)
		cap = (unsigned) jobs;
	return cap;
}

static void run_parallel (const SimCase *cases, size_t n)
{
	std::atomic<size_t> next {0};
	unsigned w = worker_count (n);
	{
		std::lock_guard<std::mutex> lock (gLog);
		printf ("parallel CPU: %zu volumes on %u workers (hw=%u, same HMAC-SHA-512)\n",
			n, w, std::thread::hardware_concurrency ());
		fflush (stdout);
	}
	std::vector<std::thread> workers;
	workers.reserve (w);
	for (unsigned i = 0; i < w; ++i)
	{
		workers.emplace_back ([&next, cases, n] () {
			for (;;)
			{
				size_t idx = next.fetch_add (1);
				if (idx >= n)
					return;
				run_sim (cases[idx]);
			}
		});
	}
	for (size_t i = 0; i < workers.size (); ++i)
		workers[i].join ();
}

static void run_sim (const SimCase &c)
{
	FlushOnExit flush;
	gScope = c.label;
	banner (c.label);

	char genpw[80];
	memset (genpw, 0, sizeof (genpw));
	const char *password = c.password;
	if (!password)
	{
		int n = vc_generate_password (genpw, sizeof (genpw), 64);
		expect (n == 64 && strlen (genpw) == 64, "generated 64 character password");
		password = genpw;
	}

	char bio_path[128] = "";
	char extra_path[128] = "";
	char vol_path[128] = "";
	char src_path[128] = "";
	char out_path[128] = "";
	char remember_path[128] = "";
	char bio_reload[128] = "";

	expect (make_temp (vol_path, sizeof (vol_path), "/tmp/vcport-life-vol-XXXXXX") == 0,
		"temp volume path");

	if (c.use_bio)
	{
		expect (make_temp (bio_path, sizeof (bio_path), "/tmp/vcport-life-bio-XXXXXX") == 0,
			"temp biometric path");
		expect (vc_generate_keyfile (bio_path, 64) == VC_OK, "create biometric password");
	}
	if (c.use_extra_kf)
	{
		expect (make_temp (extra_path, sizeof (extra_path), "/tmp/vcport-life-kf-XXXXXX") == 0,
			"temp extra keyfile path");
		expect (vc_generate_keyfile (extra_path, 128) == VC_OK, "keyfile generator 128 bytes");
	}

	const char *kfs[2];
	size_t nkf = 0;
	fill_keyfiles (kfs, &nkf, c.use_bio, bio_path, c.use_extra_kf, extra_path);

	expect (create_factors (vol_path, password, c.pim, kfs, nkf) == VC_OK,
		"create AES(Twofish(Serpent))/HMAC-SHA-512");

	if (c.full_negatives)
	{
		int err = 0;
		const char *wrong = "wrong-password";
		VcVolume *bad = open_factors (vol_path, wrong, c.pim, kfs, nkf, &err);
		expect (bad == nullptr && err == VC_ERR_PASSWORD, "wrong password is rejected");
		if (bad)
			vc_close (bad);

		err = 0;
		int wrongPim = c.pim == 0 ? 1 : c.pim + 1;
		bad = open_factors (vol_path, password, wrongPim, kfs, nkf, &err);
		expect (bad == nullptr && err == VC_ERR_PASSWORD, "wrong PIM is rejected");
		if (bad)
			vc_close (bad);

		if (c.use_bio)
		{
			err = 0;
			bad = open_factors (vol_path, password, c.pim, nullptr, 0, &err);
			expect (bad == nullptr && err == VC_ERR_PASSWORD, "missing biometric is rejected");
			if (bad)
				vc_close (bad);
		}
	}

	int err = 0;
	VcVolume *vol = open_factors (vol_path, password, c.pim, kfs, nkf, &err);
	expect (vol != nullptr && err == VC_OK, "open with password PIM and biometric");
	if (!vol)
	{
		unlink (vol_path);
		if (bio_path[0])
			unlink (bio_path);
		if (extra_path[0])
			unlink (extra_path);
		return;
	}

	expect (vc_mkdir (vol, "/", "VAULT") == VC_OK, "mkdir VAULT");

	char payload[160];
	snprintf (payload, sizeof (payload), "lifecycle-payload %s pim=%d\n", c.label, c.pim);
	size_t payload_len = strlen (payload);
	expect (make_temp (src_path, sizeof (src_path), "/tmp/vcport-life-src-XXXXXX") == 0,
		"temp import source");
	expect (write_all (src_path, payload, payload_len) == 0, "write import payload");
	expect (vc_import_file (vol, "/", src_path, "HELLO.TXT") == VC_OK, "import HELLO.TXT");
	expect (vc_import_file (vol, "VAULT", src_path, "NOTE.TXT") == VC_OK, "import NOTE.TXT");

	VcDirEntry entries[16];
	int n = vc_list_dir (vol, "/", entries, 16);
	expect (n >= 2 && has_name (entries, n, "VAULT", 1) && has_name (entries, n, "HELLO.TXT", 0),
		"list root after store");
	n = vc_list_dir (vol, "VAULT", entries, 16);
	expect (n >= 1 && has_name (entries, n, "NOTE.TXT", 0), "NOTE.TXT inside VAULT");

	vc_close (vol);
	expect (true, "close volume");

	if (c.remember_vcf2)
	{
		unsigned char bio_bytes[128];
		size_t bio_n = 0;
		FactorBundle stored;
		stored.pim = c.pim;
		stored.password = password ? password : "";
		if (c.use_bio && read_all (bio_path, bio_bytes, sizeof (bio_bytes), &bio_n) == 0)
			stored.biometric.assign (bio_bytes, bio_bytes + bio_n);
		if (c.use_extra_kf)
			stored.extra_keyfile = extra_path;
		expect (make_temp (remember_path, sizeof (remember_path), "/tmp/vcport-life-vcf-XXXXXX") == 0,
			"temp remember path");
		std::string blob = vcf2_encode (stored);
		expect (write_all (remember_path, blob.data (), blob.size ()) == 0, "store VCF2 remember bundle");

		char loaded_raw[1024];
		size_t loaded_n = 0;
		expect (read_all (remember_path, loaded_raw, sizeof (loaded_raw) - 1, &loaded_n) == 0,
			"read VCF2 remember bundle");
		loaded_raw[loaded_n] = 0;
		FactorBundle loaded = vcf2_decode (std::string (loaded_raw, loaded_n));
		expect (loaded.pim == c.pim && loaded.password == stored.password
			&& loaded.biometric == stored.biometric, "load VCF2 remember bundle");

		if (!loaded.biometric.empty ())
		{
			expect (make_temp (bio_reload, sizeof (bio_reload), "/tmp/vcport-life-bio2-XXXXXX") == 0,
				"temp reloaded biometric path");
			expect (write_all (bio_reload, loaded.biometric.data (), loaded.biometric.size ()) == 0,
				"rewrite biometric keyfile from remember store");
		}
		snprintf (genpw, sizeof (genpw), "%s", loaded.password.c_str ());
		password = genpw;
	}

	nkf = 0;
	if (bio_reload[0])
		kfs[nkf++] = bio_reload;
	else if (c.use_bio)
		kfs[nkf++] = bio_path;
	if (c.use_extra_kf)
		kfs[nkf++] = extra_path;

	err = 0;
	vol = open_factors (vol_path, password, c.pim, kfs, nkf, &err);
	expect (vol != nullptr && err == VC_OK, "reopen with stored factors");
	if (vol)
	{
		expect (make_temp (out_path, sizeof (out_path), "/tmp/vcport-life-out-XXXXXX") == 0,
			"temp export path");
		expect (vc_export_file (vol, "VAULT/NOTE.TXT", out_path) == VC_OK, "export NOTE.TXT after reopen");
		char got[160];
		size_t got_n = 0;
		expect (read_all (out_path, got, sizeof (got), &got_n) == 0
			&& got_n == payload_len && memcmp (got, payload, payload_len) == 0,
			"payload still matches");
		n = vc_list_dir (vol, "/", entries, 16);
		expect (has_name (entries, n, "HELLO.TXT", 0) && has_name (entries, n, "VAULT", 1),
			"root files still present after reopen");
		vc_close (vol);
		expect (true, "close volume again");
	}

	err = 0;
	vol = open_factors (vol_path, password, c.pim, kfs, nkf, &err);
	expect (vol != nullptr && err == VC_OK, "open again after second close");
	if (vol)
	{
		n = vc_list_dir (vol, "VAULT", entries, 16);
		expect (has_name (entries, n, "NOTE.TXT", 0), "NOTE.TXT still inside after second reopen");
		vc_close (vol);
	}

	unlink (vol_path);
	unlink (src_path);
	unlink (out_path);
	if (bio_path[0])
		unlink (bio_path);
	if (extra_path[0])
		unlink (extra_path);
	if (remember_path[0])
		unlink (remember_path);
	if (bio_reload[0])
		unlink (bio_reload);
}

int main ()
{
	printf ("VC Port volume lifecycle simulation\n");
	fill_entropy ();
	{
		FlushOnExit flush;
		gScope = "setup";
		expect (vc_test_vectors () == VC_OK, "VeraCrypt AES/Twofish/Serpent/HMAC test vectors");
	}
	test_vcf2_all_pim_passwords ();
	run_phone_session ();

	static const SimCase kCases[] = {
		{ "password-only PIM 0", "vcport-alpha", 0, 0, 0, 0, 1 },
		{ "password-only PIM 1", "vcport-alpha", 1, 0, 0, 0, 1 },
		{ "password + biometric PIM 5", "vcport-beta", 5, 1, 0, 0, 1 },
		{ "password + biometric PIM 12", "vcport-gamma", 12, 1, 0, 0, 1 },
		{ "bio-only PIM 1", "", 1, 1, 0, 0, 1 },
		{ "generated 64 + biometric + keyfile PIM 1", nullptr, 1, 1, 1, 1, 1 },
	};

	const size_t n = sizeof (kCases) / sizeof (kCases[0]);
	auto t0 = std::chrono::steady_clock::now ();
	run_parallel (kCases, n);
	auto ms = std::chrono::duration_cast<std::chrono::milliseconds> (
		std::chrono::steady_clock::now () - t0).count ();

	printf ("\n%d passed, %lld ms wall, %s\n",
		gPass.load (), (long long) ms, gFail.load () ? "FAILED" : "all ok");
	return gFail.load () ? 1 : 0;
}
