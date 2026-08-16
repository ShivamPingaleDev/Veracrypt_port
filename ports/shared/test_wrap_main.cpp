/*
 Host tests for wrap/unwrap, password generation, and wrap-file contracts.
 These run on macOS/Linux CI without a phone or FUSE-T volume.
 Compile: see ports/shared/run_wrap_test.sh
*/

#include "vc_mobile.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

static int g_fail = 0;
static int g_pass = 0;
static char g_tmp[256];

static void expect (int ok, const char *msg)
{
	if (ok)
	{
		printf ("ok  %s\n", msg);
		g_pass++;
	}
	else
	{
		printf ("FAIL  %s\n", msg);
		g_fail = 1;
	}
}

static void write_file (const char *path, const void *data, size_t n)
{
	FILE *f = fopen (path, "wb");
	if (!f)
		exit (2);
	if (n && fwrite (data, 1, n, f) != n)
		exit (2);
	fclose (f);
}

static int read_file (const char *path, char *buf, size_t cap, size_t *out)
{
	FILE *f = fopen (path, "rb");
	if (!f)
		return -1;
	size_t n = fread (buf, 1, cap, f);
	fclose (f);
	*out = n;
	return 0;
}

static int file_mode (const char *path)
{
	struct stat st;
	if (stat (path, &st) != 0)
		return -1;
	return (int) (st.st_mode & 0777);
}

static int has_any (const char *s, const char *cls)
{
	for (; *s; ++s)
	{
		if (strchr (cls, *s))
			return 1;
	}
	return 0;
}

static int only_alphabet (const char *s)
{
	static const char kAlphabet[] =
		"ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*-_=+";
	for (; *s; ++s)
	{
		if (!strchr (kAlphabet, *s))
			return 0;
	}
	return 1;
}

static void path_in (char *out, size_t cap, const char *name)
{
	snprintf (out, cap, "%s/%s", g_tmp, name);
}

static void test_password_generator (void)
{
	char a[80], b[80];

	expect (vc_generate_password (NULL, 80, 24) == VC_ERR_ARGUMENT, "generate rejects NULL buffer");
	expect (vc_generate_password (a, 16, 16) == VC_ERR_ARGUMENT, "generate rejects buffer without NUL room");
	expect (vc_generate_password (a, sizeof (a), 8) == VC_ERR_ARGUMENT, "generate rejects length 8");
	expect (vc_generate_password (a, sizeof (a), 15) == VC_ERR_ARGUMENT, "generate rejects length 15");
	expect (vc_generate_password (a, sizeof (a), 65) == VC_ERR_ARGUMENT, "generate rejects length 65");
	expect (vc_generate_password (a, sizeof (a), 0) == VC_ERR_ARGUMENT, "generate rejects length 0");
	expect (vc_generate_password (a, sizeof (a), -1) == VC_ERR_ARGUMENT, "generate rejects negative length");

	int n16 = vc_generate_password (a, sizeof (a), 16);
	expect (n16 == 16 && (int) strlen (a) == 16, "generate 16-char password");
	int n64 = vc_generate_password (b, sizeof (b), 64);
	expect (n64 == 64 && (int) strlen (b) == 64, "generate 64-char password");
	expect (only_alphabet (a) && only_alphabet (b), "generated passwords stay in alphabet");
	expect (has_any (a, "ABCDEFGH") && has_any (a, "abcdefgh")
		&& has_any (a, "23456789") && has_any (a, "!@#$%^&*"),
		"16-char password includes all four classes");
	expect (strchr (a, '0') == NULL && strchr (a, 'O') == NULL
		&& strchr (a, 'l') == NULL && strchr (a, '1') == NULL,
		"ambiguous 0/O/l/1 omitted from generator");

	int same = 0;
	char prev[80];
	vc_generate_password (prev, sizeof (prev), 24);
	for (int i = 0; i < 8; ++i)
	{
		vc_generate_password (a, sizeof (a), 24);
		if (strcmp (a, prev) == 0)
			same++;
		memcpy (prev, a, sizeof (prev));
	}
	expect (same == 0, "eight successive 24-char passwords differ");
}

static void test_is_wrap_and_args (void)
{
	char missing[512], notwrap[512];
	path_in (missing, sizeof (missing), "no-such.vcpw");
	path_in (notwrap, sizeof (notwrap), "plain.txt");
	write_file (notwrap, "x", 1);

	expect (vc_is_wrap (NULL) == 0, "is_wrap NULL is false");
	expect (vc_is_wrap (missing) == 0, "is_wrap missing file is false");
	expect (vc_is_wrap (notwrap) == 0, "is_wrap plaintext is false");

	expect (vc_wrap_file (NULL, notwrap, "pw", 2, "n") == VC_ERR_ARGUMENT, "wrap rejects NULL src");
	expect (vc_wrap_file (notwrap, NULL, "pw", 2, "n") == VC_ERR_ARGUMENT, "wrap rejects NULL dest");
	expect (vc_wrap_file (notwrap, notwrap, NULL, 2, "n") == VC_ERR_ARGUMENT, "wrap rejects NULL password");
	expect (vc_wrap_file (notwrap, notwrap, "pw", 0, "n") == VC_ERR_ARGUMENT, "wrap rejects empty password");
	expect (vc_wrap_file (missing, notwrap, "pw", 2, "n") == VC_ERR_IO, "wrap missing src is IO");

	char outpath[256];
	expect (vc_unwrap_file (NULL, g_tmp, "pw", 2, outpath, sizeof (outpath)) == VC_ERR_ARGUMENT,
		"unwrap rejects NULL src");
	expect (vc_unwrap_file (notwrap, NULL, "pw", 2, outpath, sizeof (outpath)) == VC_ERR_ARGUMENT,
		"unwrap rejects NULL dest dir");
	expect (vc_unwrap_file (notwrap, g_tmp, NULL, 2, outpath, sizeof (outpath)) == VC_ERR_ARGUMENT,
		"unwrap rejects NULL password");
}

static int wrap_roundtrip (const char *label, const void *payload, size_t payload_len,
	const char *orig_name, const char *expect_substr)
{
	char plain[512], wrap[512], outdir[512], pw[80], recovered[256 * 1024];
	char outpath[1024];
	static int seq = 0;
	seq++;
	snprintf (plain, sizeof (plain), "%s/p-%d.bin", g_tmp, seq);
	snprintf (wrap, sizeof (wrap), "%s/p-%d.vcpw", g_tmp, seq);
	snprintf (outdir, sizeof (outdir), "%s/out-%d", g_tmp, seq);
	mkdir (outdir, 0700);

	write_file (plain, payload, payload_len);
	int n = vc_generate_password (pw, sizeof (pw), 24);
	if (n != 24)
	{
		expect (0, label);
		return 1;
	}

	int rc = vc_wrap_file (plain, wrap, pw, strlen (pw), orig_name);
	expect (rc == VC_OK, label);
	if (rc != VC_OK)
		return 1;
	expect (vc_is_wrap (wrap) == 1, "wrapped file has VCPW magic");
	expect (file_mode (wrap) == 0600, "wrap dest mode is 0600");

	char magic[4];
	size_t got = 0;
	expect (read_file (wrap, magic, 4, &got) == 0 && got == 4 && memcmp (magic, "VCPW", 4) == 0,
		"wrap header starts with VCPW");

	rc = vc_unwrap_file (wrap, outdir, pw, strlen (pw), outpath, sizeof (outpath));
	expect (rc == VC_OK, "unwrap with correct password");
	if (rc != VC_OK)
		return 1;
	expect (file_mode (outpath) == 0600, "unwrapped dest mode is 0600");
	if (expect_substr)
		expect (strstr (outpath, expect_substr) != NULL, "restored name contains expected stem");

	if (payload_len + 1 > sizeof (recovered))
	{
		expect (0, "payload too large for host check buffer");
		return 1;
	}
	got = 0;
	expect (read_file (outpath, recovered, sizeof (recovered), &got) == 0, "read unwrapped file");
	expect (got == payload_len && memcmp (recovered, payload, payload_len) == 0, "payload matches");

	rc = vc_unwrap_file (wrap, outdir, "wrong-password-XXXX", 20, outpath, sizeof (outpath));
	expect (rc == VC_ERR_PASSWORD, "wrong password is rejected");
	vc_secure_wipe (pw, sizeof (pw));
	return 0;
}

static void test_wrap_payloads (void)
{
	const char *text = "hello wrap roundtrip\nline two\n";
	wrap_roundtrip ("wrap UTF-8 text", text, strlen (text), "secret.txt", "secret.txt");

	wrap_roundtrip ("wrap empty file", "", 0, "empty.dat", "empty.dat");

	unsigned char binary[64];
	for (int i = 0; i < 64; ++i)
		binary[i] = (unsigned char) i;
	wrap_roundtrip ("wrap binary with NUL bytes", binary, sizeof (binary), "blob.bin", "blob.bin");

	char chunked[65537];
	memset (chunked, 0x5A, sizeof (chunked));
	chunked[0] = 0x00;
	chunked[65536] = 0xFF;
	wrap_roundtrip ("wrap 64KiB+1 payload (two CTR chunks)", chunked, sizeof (chunked),
		"chunked.bin", "chunked.bin");

	wrap_roundtrip ("wrap unicode original name", text, strlen (text), "café.txt", "café.txt");
	wrap_roundtrip ("sanitize path separators in name", text, strlen (text),
		"../etc/passwd", "etc_passwd");
	wrap_roundtrip ("dot name becomes file", text, strlen (text), "..", "file");
}

static void test_tamper_and_salt (void)
{
	char plain[512], wrap_a[512], wrap_b[512], outdir[512], pw[80], outpath[1024];
	path_in (plain, sizeof (plain), "salt.txt");
	path_in (wrap_a, sizeof (wrap_a), "a.vcpw");
	path_in (wrap_b, sizeof (wrap_b), "b.vcpw");
	path_in (outdir, sizeof (outdir), "salt-out");
	mkdir (outdir, 0700);
	write_file (plain, "same-plaintext", 14);
	vc_generate_password (pw, sizeof (pw), 24);

	expect (vc_wrap_file (plain, wrap_a, pw, strlen (pw), "salt.txt") == VC_OK, "wrap A");
	expect (vc_wrap_file (plain, wrap_b, pw, strlen (pw), "salt.txt") == VC_OK, "wrap B same password");

	char buf_a[4096], buf_b[4096];
	size_t na = 0, nb = 0;
	expect (read_file (wrap_a, buf_a, sizeof (buf_a), &na) == 0, "read wrap A");
	expect (read_file (wrap_b, buf_b, sizeof (buf_b), &nb) == 0, "read wrap B");
	expect (na == nb && na > 80, "two wraps of same file are the same size");
	expect (memcmp (buf_a, buf_b, na) != 0, "random salt makes ciphertext differ");

	FILE *wf = fopen (wrap_a, "r+b");
	if (wf)
	{
		fseek (wf, 80, SEEK_SET);
		fputc ('X', wf);
		fclose (wf);
	}
	int rc = vc_unwrap_file (wrap_a, outdir, pw, strlen (pw), outpath, sizeof (outpath));
	expect (rc == VC_ERR_PASSWORD || rc == VC_ERR_FORMAT || rc == VC_ERR_IO, "tampered wrap is rejected");

	/* Truncate MAC */
	if (truncate (wrap_b, 90) == 0)
	{
		rc = vc_unwrap_file (wrap_b, outdir, pw, strlen (pw), outpath, sizeof (outpath));
		expect (rc != VC_OK, "truncated wrap is rejected");
	}

	vc_secure_wipe (pw, sizeof (pw));
	expect (pw[0] == 0 && pw[10] == 0 && pw[23] == 0, "secure wipe clears password buffer");
}

int main (void)
{
	snprintf (g_tmp, sizeof (g_tmp), "/tmp/vcport-test-%d", (int) getpid ());
	mkdir (g_tmp, 0700);

	printf ("# wrap/crypto host tests (no device)\n");
	test_password_generator ();
	test_is_wrap_and_args ();
	test_wrap_payloads ();
	test_tamper_and_salt ();

	printf ("\n%d passed, %s\n", g_pass, g_fail ? "TEST RUN FAILED" : "TEST RUN PASSED");
	return g_fail;
}
