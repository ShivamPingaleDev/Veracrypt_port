/* Hand-written hex encode/decode — educational scratch C lab */

#include "hex.h"

#include <ctype.h>
#include <stdio.h>

static int hex_digit (char c)
{
	if (c >= '0' && c <= '9')
		return c - '0';
	if (c >= 'a' && c <= 'f')
		return c - 'a' + 10;
	if (c >= 'A' && c <= 'F')
		return c - 'A' + 10;
	return -1;
}

size_t hex_encode (const uint8_t *in, size_t in_len, char *out, size_t out_cap)
{
	static const char *digits = "0123456789abcdef";
	size_t i;
	if (out_cap < in_len * 2 + 1)
		return 0;
	for (i = 0; i < in_len; i++)
	{
		out[i * 2] = digits[(in[i] >> 4) & 0x0f];
		out[i * 2 + 1] = digits[in[i] & 0x0f];
	}
	out[in_len * 2] = '\0';
	return in_len * 2;
}

int hex_decode (const char *in, uint8_t *out, size_t out_cap, size_t *out_len)
{
	size_t n = 0;
	size_t i = 0;
	while (in[i] && isspace ((unsigned char) in[i]))
		i++;
	while (in[i] && in[i + 1])
	{
		int hi = hex_digit (in[i]);
		int lo = hex_digit (in[i + 1]);
		if (hi < 0 || lo < 0)
			return -1;
		if (n >= out_cap)
			return -1;
		out[n++] = (uint8_t) ((hi << 4) | lo);
		i += 2;
	}
	if (out_len)
		*out_len = n;
	return 0;
}
