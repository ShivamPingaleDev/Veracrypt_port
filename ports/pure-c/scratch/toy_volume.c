/* Toy XOR container — educational scratch C lab */

#include "toy_volume.h"

#include <stdio.h>
#include <string.h>

static const uint8_t magic[8] = { 'V', 'C', 'E', 'D', 'U', '1', '\0', '\0' };

static void stream_key (const char *password, uint8_t key[32])
{
	size_t i;
	memset (key, 0, 32);
	for (i = 0; password[i]; i++)
		key[i % 32] ^= (uint8_t) password[i];
	for (i = 0; i < 32; i++)
		key[i] = (uint8_t) (key[i] + ((uint8_t) i ^ 0x5c));
}

static void xor_stream (const uint8_t *in, size_t len, const uint8_t *key, uint8_t *out)
{
	size_t i;
	for (i = 0; i < len; i++)
		out[i] = in[i] ^ key[i % 32];
}

int toy_write (const char *path, const char *password, const uint8_t *plain, size_t plain_len)
{
	uint8_t key[32];
	uint8_t *body;
	uint32_t le;
	FILE *f;
	size_t i;

	if (!password || !password[0])
		return -1;
	if (plain_len > 1024 * 1024)
		return -1;

	stream_key (password, key);
	body = (uint8_t *) malloc (plain_len);
	if (!body)
		return -1;
	xor_stream (plain, plain_len, key, body);

	f = fopen (path, "wb");
	if (!f)
	{
		free (body);
		return -1;
	}
	fwrite (magic, 1, 8, f);
	le = (uint32_t) plain_len;
	fwrite (&le, 1, 4, f);
	fwrite (body, 1, plain_len, f);
	fclose (f);
	free (body);
	(void) i;
	return 0;
}

int toy_open (const char *path, const char *password, uint8_t **plain, size_t *plain_len)
{
	uint8_t key[32];
	uint8_t hdr[8];
	uint32_t le;
	uint8_t *body;
	FILE *f;
	size_t n;

	if (!password || !password[0])
		return -1;

	f = fopen (path, "rb");
	if (!f)
		return -1;
	n = fread (hdr, 1, 8, f);
	if (n != 8 || memcmp (hdr, magic, 8) != 0)
	{
		fclose (f);
		return -2;
	}
	if (fread (&le, 1, 4, f) != 4)
	{
		fclose (f);
		return -1;
	}
	body = (uint8_t *) malloc (le);
	if (!body)
	{
		fclose (f);
		return -1;
	}
	if (fread (body, 1, le, f) != le)
	{
		free (body);
		fclose (f);
		return -1;
	}
	fclose (f);

	stream_key (password, key);
	xor_stream (body, le, key, body);
	*plain = body;
	*plain_len = le;
	return 0;
}
