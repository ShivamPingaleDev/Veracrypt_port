/*
 Host test for wrap/unwrap and the password generator.
 Compile: see ports/shared/run_wrap_test.sh
*/

#include "vc_mobile.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

static int g_fail = 0;

static void expect (int ok, const char *msg)
{
	if (ok)
		printf ("ok  %s\n", msg);
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
	fwrite (data, 1, n, f);
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

int main (void)
{
	char tmp[256];
	snprintf (tmp, sizeof (tmp), "/tmp/vcport-test-%d", (int) getpid ());
	mkdir (tmp, 0700);

	char plain[512], wrap[512], outdir[512], pw1[80], pw2[80];
	snprintf (plain, sizeof (plain), "%s/secret.txt", tmp);
	snprintf (wrap, sizeof (wrap), "%s/secret.txt.vcpw", tmp);
	snprintf (outdir, sizeof (outdir), "%s/out", tmp);
	mkdir (outdir, 0700);

	const char *payload = "hello wrap roundtrip\nline two\n";
	write_file (plain, payload, strlen (payload));

	int n1 = vc_generate_password (pw1, sizeof (pw1), 24);
	int n2 = vc_generate_password (pw2, sizeof (pw2), 24);
	expect (n1 == 24 && strlen (pw1) == 24, "generate 24-char password");
	expect (n2 == 24 && strlen (pw2) == 24, "generate second password");
	expect (strcmp (pw1, pw2) != 0, "two passwords differ");
	expect (vc_generate_password (pw1, sizeof (pw1), 8) == VC_ERR_ARGUMENT, "reject short length");

	n1 = vc_generate_password (pw1, sizeof (pw1), 24);
	int rc = vc_wrap_file (plain, wrap, pw1, strlen (pw1), "secret.txt");
	expect (rc == VC_OK, "wrap file");
	expect (vc_is_wrap (wrap) == 1, "wrapped file has VCPW magic");
	expect (vc_is_wrap (plain) == 0, "plaintext is not a wrap");

	char outpath[1024];
	rc = vc_unwrap_file (wrap, outdir, pw1, strlen (pw1), outpath, sizeof (outpath));
	expect (rc == VC_OK, "unwrap with correct password");

	char recovered[256];
	size_t got = 0;
	expect (read_file (outpath, recovered, sizeof (recovered) - 1, &got) == 0, "read unwrapped file");
	recovered[got] = 0;
	expect (got == strlen (payload) && memcmp (recovered, payload, got) == 0, "payload matches");
	expect (strstr (outpath, "secret.txt") != NULL, "original name restored");

	rc = vc_unwrap_file (wrap, outdir, "wrong-password-XXXX", 20, outpath, sizeof (outpath));
	expect (rc == VC_ERR_PASSWORD, "wrong password is rejected");

	FILE *wf = fopen (wrap, "r+b");
	if (wf)
	{
		fseek (wf, 80, SEEK_SET);
		fputc ('X', wf);
		fclose (wf);
	}
	rc = vc_unwrap_file (wrap, outdir, pw1, strlen (pw1), outpath, sizeof (outpath));
	expect (rc == VC_ERR_PASSWORD || rc == VC_ERR_FORMAT || rc == VC_ERR_IO, "tampered wrap is rejected");

	vc_secure_wipe (pw1, sizeof (pw1));
	vc_secure_wipe (pw2, sizeof (pw2));
	expect (pw1[0] == 0 && pw1[10] == 0, "secure wipe clears password buffer");

	printf ("\n%s\n", g_fail ? "TEST RUN FAILED" : "TEST RUN PASSED");
	return g_fail;
}
