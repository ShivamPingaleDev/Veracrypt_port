/* CLI — educational scratch C lab */

#include "hex.h"
#include "sha256.h"
#include "toy_volume.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static int cmd_hash_path (const char *path)
{
	FILE *f;
	uint8_t buf[4096];
	uint8_t digest[32];
	char hex[65];
	size_t n;

	f = fopen (path, "rb");
	if (!f)
	{
		perror (path);
		return 1;
	}
	/* One-shot read for lab files; exercise: stream large files */
	uint8_t *all = NULL;
	size_t len = 0;
	while ((n = fread (buf, 1, sizeof (buf), f)) > 0)
	{
		uint8_t *tmp = realloc (all, len + n);
		if (!tmp)
		{
			free (all);
			fclose (f);
			return 1;
		}
		all = tmp;
		memcpy (all + len, buf, n);
		len += n;
	}
	fclose (f);
	if (len == 0)
		sha256 ((const uint8_t *) "", 0, digest);
	else
		sha256 (all, len, digest);
	free (all);
	hex_encode (digest, 32, hex, sizeof (hex));
	printf ("%s\n", hex);
	return 0;
}

static int cmd_hash_stdin (void)
{
	uint8_t buf[4096];
	uint8_t digest[32];
	char hex[65];
	size_t n;
	uint8_t *all = NULL;
	size_t len = 0;

	while ((n = fread (buf, 1, sizeof (buf), stdin)) > 0)
	{
		uint8_t *tmp = realloc (all, len + n);
		if (!tmp)
		{
			free (all);
			return 1;
		}
		all = tmp;
		memcpy (all + len, buf, n);
		len += n;
	}
	if (len == 0)
		sha256 ((const uint8_t *) "", 0, digest);
	else
		sha256 (all, len, digest);
	free (all);
	hex_encode (digest, 32, hex, sizeof (hex));
	printf ("%s\n", hex);
	return 0;
}

static int read_file (const char *path, uint8_t **data, size_t *len)
{
	FILE *f;
	uint8_t buf[4096];
	size_t n;
	uint8_t *all = NULL;
	size_t total = 0;

	f = fopen (path, "rb");
	if (!f)
		return -1;
	while ((n = fread (buf, 1, sizeof (buf), f)) > 0)
	{
		uint8_t *tmp = realloc (all, total + n);
		if (!tmp)
		{
			free (all);
			fclose (f);
			return -1;
		}
		all = tmp;
		memcpy (all + total, buf, n);
		total += n;
	}
	fclose (f);
	*data = all;
	*len = total;
	return 0;
}

static int cmd_create (const char *container, const char *password, const char *from)
{
	uint8_t *plain;
	size_t len;
	if (read_file (from, &plain, &len) != 0)
	{
		perror (from);
		return 1;
	}
	if (toy_write (container, password, plain, len) != 0)
	{
		free (plain);
		fprintf (stderr, "create failed\n");
		return 1;
	}
	free (plain);
	printf ("wrote %s\n", container);
	return 0;
}

static int cmd_list (const char *container, const char *password)
{
	uint8_t *plain;
	size_t len;
	int rc = toy_open (container, password, &plain, &len);
	if (rc == -2)
	{
		fprintf (stderr, "bad magic\n");
		return 1;
	}
	if (rc != 0)
	{
		perror (container);
		return 1;
	}
	printf ("payload.bin (%zu bytes)\n", len);
	free (plain);
	return 0;
}

int main (int argc, char **argv)
{
	if (argc >= 2 && strcmp (argv[1], "hash") == 0)
	{
		if (argc == 2)
			return cmd_hash_stdin ();
		return cmd_hash_path (argv[2]);
	}
	if (argc >= 6 && strcmp (argv[1], "create") == 0 && strcmp (argv[4], "--from") == 0)
		return cmd_create (argv[2], argv[3], argv[5]);
	if (argc >= 4 && strcmp (argv[1], "list") == 0)
		return cmd_list (argv[2], argv[3]);

	fprintf (stderr,
		"vcedu-c — educational scratch C lab (not VeraCrypt)\n"
		"  hash [FILE]          SHA-256 (stdin if no file)\n"
		"  create OUT PASS --from IN\n"
		"  list CONTAINER PASS\n");
	return 2;
}
