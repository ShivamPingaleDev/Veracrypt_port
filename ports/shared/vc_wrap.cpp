/*
 Copyright (c) 2026 Shivam Mangesh Pingale. All rights reserved.

 Individual-file wrap: Argon2id + AES-256-CTR + HMAC-SHA256.
 Passwords and generated secrets are wiped in memory and never written to logs.
*/

#include "vc_mobile.h"

#include "Crypto/Aes.h"
#include "Crypto/Sha2.h"
#include "Crypto/Argon2/include/argon2.h"
#include "Common/Tcdefs.h"

#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>
#include <vector>

#if defined(_POSIX_MEMLOCK)
#include <sys/mman.h>
#endif

#ifdef __APPLE__
#include <sys/random.h>
#endif

namespace {

const char kMagic[4] = { 'V', 'C', 'P', 'W' };
const uint8_t kVersion = 1;
const uint32_t kMemKib = 32768;
const uint32_t kTimeCost = 3;
const uint32_t kLanes = 1;
const size_t kHeaderSize = 76;
const size_t kMacSize = 32;
const size_t kSaltSize = 32;
const size_t kIvSize = 16;
const size_t kChunk = 64 * 1024;

struct HmacSha256
{
	sha256_ctx inner;
	sha256_ctx outer;
};

void hmac_init (HmacSha256 *h, const uint8_t *key, size_t key_len)
{
	uint8_t ipad[64];
	uint8_t opad[64];
	uint8_t khash[32];
	const uint8_t *k = key;
	size_t n = key_len;
	if (n > 64)
	{
		sha256 (khash, k, (uint_32t) n);
		k = khash;
		n = 32;
	}
	memset (ipad, 0x36, 64);
	memset (opad, 0x5c, 64);
	for (size_t i = 0; i < n; ++i)
	{
		ipad[i] = (uint8_t) (ipad[i] ^ k[i]);
		opad[i] = (uint8_t) (opad[i] ^ k[i]);
	}
	sha256_begin (&h->inner);
	sha256_hash (ipad, 64, &h->inner);
	sha256_begin (&h->outer);
	sha256_hash (opad, 64, &h->outer);
	burn (ipad, sizeof (ipad));
	burn (opad, sizeof (opad));
	burn (khash, sizeof (khash));
}

void hmac_update (HmacSha256 *h, const void *data, size_t len)
{
	if (len)
		sha256_hash ((const unsigned char *) data, (uint_32t) len, &h->inner);
}

void hmac_final (HmacSha256 *h, uint8_t out[32])
{
	uint8_t inner[32];
	sha256_end (inner, &h->inner);
	sha256_hash (inner, 32, &h->outer);
	sha256_end (out, &h->outer);
	burn (inner, sizeof (inner));
}

int ct_equal (const uint8_t *a, const uint8_t *b, size_t n)
{
	uint8_t d = 0;
	for (size_t i = 0; i < n; ++i)
		d = (uint8_t) (d | (a[i] ^ b[i]));
	return d == 0;
}

void store_u32 (uint8_t *p, uint32_t v)
{
	p[0] = (uint8_t) v;
	p[1] = (uint8_t) (v >> 8);
	p[2] = (uint8_t) (v >> 16);
	p[3] = (uint8_t) (v >> 24);
}

uint32_t load_u32 (const uint8_t *p)
{
	return (uint32_t) p[0] | ((uint32_t) p[1] << 8) | ((uint32_t) p[2] << 16) | ((uint32_t) p[3] << 24);
}

void store_u64 (uint8_t *p, uint64_t v)
{
	for (int i = 0; i < 8; ++i)
		p[i] = (uint8_t) (v >> (8 * i));
}

uint64_t load_u64 (const uint8_t *p)
{
	uint64_t v = 0;
	for (int i = 0; i < 8; ++i)
		v |= (uint64_t) p[i] << (8 * i);
	return v;
}

int fill_random (void *buf, size_t n)
{
	uint8_t *p = (uint8_t *) buf;
	size_t got = 0;
#if defined(__APPLE__)
	while (got < n)
	{
		size_t chunk = n - got > 256 ? 256 : n - got;
		if (getentropy (p + got, chunk) != 0)
			break;
		got += chunk;
	}
	if (got == n)
		return 0;
#endif
	int fd = open ("/dev/urandom", O_RDONLY);
	if (fd < 0)
		return -1;
	while (got < n)
	{
		ssize_t r = read (fd, p + got, n - got);
		if (r <= 0)
		{
			close (fd);
			return -1;
		}
		got += (size_t) r;
	}
	close (fd);
	return 0;
}

void ctr_inc (uint8_t counter[16])
{
	for (int i = 15; i >= 0; --i)
	{
		if (++counter[i])
			break;
	}
}

void ctr_xor (aes_encrypt_ctx *cx, uint8_t counter[16], uint8_t *data, size_t len)
{
	uint8_t ks[16];
	size_t i = 0;
	while (i < len)
	{
		aes_encrypt (counter, ks, cx);
		ctr_inc (counter);
		size_t n = len - i < 16 ? len - i : 16;
		for (size_t j = 0; j < n; ++j)
			data[i + j] = (uint8_t) (data[i + j] ^ ks[j]);
		i += n;
	}
	burn (ks, sizeof (ks));
}

int derive_keys (const char *password, size_t password_len, const uint8_t salt[32],
	uint32_t m_kib, uint32_t t_cost, uint32_t lanes, uint8_t aes_key[32], uint8_t mac_key[32])
{
	uint8_t okm[64];
	int rc = argon2id_hash_raw (t_cost, m_kib, lanes,
		password, password_len, salt, kSaltSize, okm, sizeof (okm), nullptr);
	if (rc != ARGON2_OK)
	{
		burn (okm, sizeof (okm));
		return VC_ERR_MEMORY;
	}
	memcpy (aes_key, okm, 32);
	memcpy (mac_key, okm + 32, 32);
	burn (okm, sizeof (okm));
	return VC_OK;
}

const char *basename_c (const char *path)
{
	const char *slash = strrchr (path, '/');
	return slash ? slash + 1 : path;
}

void sanitize_name (char *name)
{
	for (char *p = name; *p; ++p)
	{
		if (*p == '/' || *p == '\\' || *p == '\0')
			*p = '_';
	}
	if (strcmp (name, ".") == 0 || strcmp (name, "..") == 0)
		strcpy (name, "file");
}

int random_u32 (uint32_t *out)
{
	return fill_random (out, sizeof (*out));
}

void lock_secret (void *p, size_t n)
{
#if defined(_POSIX_MEMLOCK)
	if (p && n)
		mlock (p, n);
#else
	(void) p;
	(void) n;
#endif
}

void unlock_secret (void *p, size_t n)
{
#if defined(_POSIX_MEMLOCK)
	if (p && n)
		munlock (p, n);
#else
	(void) p;
	(void) n;
#endif
}

FILE *fopen_private_write (const char *path)
{
	int fd = open (path, O_WRONLY | O_CREAT | O_TRUNC, 0600);
	if (fd < 0)
		return nullptr;
	FILE *f = fdopen (fd, "wb");
	if (!f)
		close (fd);
	return f;
}

} // namespace

void vc_secure_wipe (void *p, size_t n)
{
	if (p && n)
		burn (p, n);
}

int vc_generate_password (char *out, size_t out_size, int length)
{
	static const char kAlphabet[] =
		"ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*-_=+";
	const int alpha_len = (int) (sizeof (kAlphabet) - 1);
	if (!out || out_size < 17 || length < 16 || length > 64 || (size_t) length + 1 > out_size)
		return VC_ERR_ARGUMENT;

	memset (out, 0, out_size);
	for (int i = 0; i < length; ++i)
	{
		uint32_t r = 0;
		uint32_t limit = 0xFFFFFFFFu - (0xFFFFFFFFu % (uint32_t) alpha_len);
		do
		{
			if (random_u32 (&r) != 0)
			{
				vc_secure_wipe (out, out_size);
				return VC_ERR_IO;
			}
		} while (r >= limit);
		out[i] = kAlphabet[r % (uint32_t) alpha_len];
	}

	static const char kClasses[4][9] = { "ABCDEFGH", "abcdefgh", "23456789", "!@#$%^&*" };
	for (int c = 0; c < 4 && c < length; ++c)
	{
		uint32_t pick = 0;
		uint32_t pos = 0;
		if (random_u32 (&pick) != 0 || random_u32 (&pos) != 0)
		{
			vc_secure_wipe (out, out_size);
			return VC_ERR_IO;
		}
		out[pos % (uint32_t) length] = kClasses[c][pick % 8];
	}

	for (int i = length - 1; i > 0; --i)
	{
		uint32_t r = 0;
		if (random_u32 (&r) != 0)
		{
			vc_secure_wipe (out, out_size);
			return VC_ERR_IO;
		}
		int j = (int) (r % (uint32_t) (i + 1));
		char tmp = out[i];
		out[i] = out[j];
		out[j] = tmp;
	}
	out[length] = 0;
	return length;
}

int vc_is_wrap (const char *path)
{
	if (!path)
		return 0;
	FILE *f = fopen (path, "rb");
	if (!f)
		return 0;
	char magic[4];
	size_t n = fread (magic, 1, 4, f);
	fclose (f);
	return n == 4 && memcmp (magic, kMagic, 4) == 0;
}

int vc_wrap_file (const char *src_path, const char *dest_path,
	const char *password, size_t password_len, const char *original_name)
{
	if (!src_path || !dest_path || !password || password_len == 0)
		return VC_ERR_ARGUMENT;

	const char *name = original_name && original_name[0] ? original_name : basename_c (src_path);
	size_t name_len = strlen (name);
	if (name_len == 0)
	{
		name = "file";
		name_len = 4;
	}

	struct stat st;
	if (stat (src_path, &st) != 0 || !S_ISREG (st.st_mode))
		return VC_ERR_IO;
	uint64_t file_size = (uint64_t) st.st_size;
	uint64_t payload_size = 2 + name_len + file_size;

	uint8_t salt[kSaltSize];
	uint8_t iv[kIvSize];
	if (fill_random (salt, sizeof (salt)) != 0 || fill_random (iv, sizeof (iv)) != 0)
		return VC_ERR_IO;

	uint8_t aes_key[32];
	uint8_t mac_key[32];
	int rc = derive_keys (password, password_len, salt, kMemKib, kTimeCost, kLanes, aes_key, mac_key);
	if (rc != VC_OK)
		return rc;
	lock_secret (aes_key, sizeof (aes_key));
	lock_secret (mac_key, sizeof (mac_key));

	aes_init ();
	aes_encrypt_ctx cx;
	memset (&cx, 0, sizeof (cx));
	aes_encrypt_key256 (aes_key, &cx);
	uint8_t counter[kIvSize];
	memcpy (counter, iv, kIvSize);

	uint8_t header[kHeaderSize];
	memset (header, 0, sizeof (header));
	memcpy (header, kMagic, 4);
	header[4] = kVersion;
	header[5] = 1;
	header[6] = 1;
	store_u32 (header + 8, kMemKib);
	store_u32 (header + 12, kTimeCost);
	store_u32 (header + 16, kLanes);
	memcpy (header + 20, salt, kSaltSize);
	memcpy (header + 52, iv, kIvSize);
	store_u64 (header + 68, payload_size);

	FILE *in = fopen (src_path, "rb");
	FILE *out = fopen_private_write (dest_path);
	if (!in || !out)
	{
		if (in) fclose (in);
		if (out) fclose (out);
		burn (aes_key, sizeof (aes_key));
		burn (mac_key, sizeof (mac_key));
		burn (&cx, sizeof (cx));
		return VC_ERR_IO;
	}

	if (fwrite (header, 1, kHeaderSize, out) != kHeaderSize)
	{
		fclose (in);
		fclose (out);
		burn (aes_key, sizeof (aes_key));
		burn (mac_key, sizeof (mac_key));
		return VC_ERR_IO;
	}

	HmacSha256 hmac;
	hmac_init (&hmac, mac_key, 32);
	hmac_update (&hmac, header, kHeaderSize);

	uint8_t prefix[2];
	prefix[0] = (uint8_t) name_len;
	prefix[1] = (uint8_t) (name_len >> 8);
	uint8_t namebuf[256];
	memset (namebuf, 0, sizeof (namebuf));
	memcpy (namebuf, name, name_len);
	ctr_xor (&cx, counter, prefix, 2);
	ctr_xor (&cx, counter, namebuf, name_len);
	if (fwrite (prefix, 1, 2, out) != 2 || fwrite (namebuf, 1, name_len, out) != name_len)
	{
		fclose (in);
		fclose (out);
		rc = VC_ERR_IO;
	}
	else
	{
		hmac_update (&hmac, prefix, 2);
		hmac_update (&hmac, namebuf, name_len);
		rc = VC_OK;
	}

	std::vector<uint8_t> chunk (kChunk);
	while (rc == VC_OK)
	{
		size_t n = fread (&chunk[0], 1, kChunk, in);
		if (n == 0)
			break;
		ctr_xor (&cx, counter, &chunk[0], n);
		if (fwrite (&chunk[0], 1, n, out) != n)
		{
			rc = VC_ERR_IO;
			break;
		}
		hmac_update (&hmac, &chunk[0], n);
	}
	if (rc == VC_OK && ferror (in))
		rc = VC_ERR_IO;

	uint8_t mac[kMacSize];
	hmac_final (&hmac, mac);
	if (rc == VC_OK && fwrite (mac, 1, kMacSize, out) != kMacSize)
		rc = VC_ERR_IO;

	fclose (in);
	if (fclose (out) != 0 && rc == VC_OK)
		rc = VC_ERR_IO;

	burn (aes_key, sizeof (aes_key));
	burn (mac_key, sizeof (mac_key));
	unlock_secret (aes_key, sizeof (aes_key));
	unlock_secret (mac_key, sizeof (mac_key));
	burn (salt, sizeof (salt));
	burn (iv, sizeof (iv));
	burn (counter, sizeof (counter));
	burn (prefix, sizeof (prefix));
	burn (namebuf, sizeof (namebuf));
	burn (mac, sizeof (mac));
	burn (&cx, sizeof (cx));
	burn (&hmac, sizeof (hmac));
	if (!chunk.empty ())
		burn (&chunk[0], chunk.size ());
	return rc;
}

int vc_unwrap_file (const char *src_path, const char *dest_dir,
	const char *password, size_t password_len, char *out_path, size_t out_path_size)
{
	if (!src_path || !dest_dir || !password || password_len == 0)
		return VC_ERR_ARGUMENT;
	if (out_path && out_path_size)
		out_path[0] = 0;

	FILE *in = fopen (src_path, "rb");
	if (!in)
		return VC_ERR_IO;

	uint8_t header[kHeaderSize];
	if (fread (header, 1, kHeaderSize, in) != kHeaderSize)
	{
		fclose (in);
		return VC_ERR_FORMAT;
	}
	if (memcmp (header, kMagic, 4) != 0 || header[4] != kVersion || header[5] != 1 || header[6] != 1)
	{
		fclose (in);
		return VC_ERR_FORMAT;
	}

	uint32_t m_kib = load_u32 (header + 8);
	uint32_t t_cost = load_u32 (header + 12);
	uint32_t lanes = load_u32 (header + 16);
	uint8_t salt[kSaltSize];
	uint8_t iv[kIvSize];
	memcpy (salt, header + 20, kSaltSize);
	memcpy (iv, header + 52, kIvSize);
	uint64_t payload_size = load_u64 (header + 68);
	if (m_kib < 8 || m_kib > 512 * 1024 || t_cost < 1 || t_cost > 16 || lanes < 1 || lanes > 4)
	{
		fclose (in);
		return VC_ERR_FORMAT;
	}

	if (fseeko (in, 0, SEEK_END) != 0)
	{
		fclose (in);
		return VC_ERR_IO;
	}
	off_t total = ftello (in);
	if (total < (off_t) (kHeaderSize + kMacSize) ||
		(uint64_t) total != kHeaderSize + payload_size + kMacSize)
	{
		fclose (in);
		return VC_ERR_FORMAT;
	}

	uint8_t aes_key[32];
	uint8_t mac_key[32];
	int rc = derive_keys (password, password_len, salt, m_kib, t_cost, lanes, aes_key, mac_key);
	if (rc != VC_OK)
	{
		fclose (in);
		return rc;
	}
	lock_secret (aes_key, sizeof (aes_key));
	lock_secret (mac_key, sizeof (mac_key));

	HmacSha256 hmac;
	hmac_init (&hmac, mac_key, 32);
	if (fseeko (in, 0, SEEK_SET) != 0)
	{
		fclose (in);
		burn (aes_key, sizeof (aes_key));
		burn (mac_key, sizeof (mac_key));
		return VC_ERR_IO;
	}

	std::vector<uint8_t> chunk (kChunk);
	uint64_t remain = kHeaderSize + payload_size;
	while (remain)
	{
		size_t n = remain < kChunk ? (size_t) remain : kChunk;
		if (fread (&chunk[0], 1, n, in) != n)
		{
			fclose (in);
			burn (aes_key, sizeof (aes_key));
			burn (mac_key, sizeof (mac_key));
			return VC_ERR_IO;
		}
		hmac_update (&hmac, &chunk[0], n);
		remain -= n;
	}
	uint8_t mac_file[kMacSize];
	uint8_t mac_calc[kMacSize];
	if (fread (mac_file, 1, kMacSize, in) != kMacSize)
	{
		fclose (in);
		burn (aes_key, sizeof (aes_key));
		burn (mac_key, sizeof (mac_key));
		return VC_ERR_IO;
	}
	hmac_final (&hmac, mac_calc);
	if (!ct_equal (mac_file, mac_calc, kMacSize))
	{
		fclose (in);
		burn (aes_key, sizeof (aes_key));
		burn (mac_key, sizeof (mac_key));
		burn (mac_file, sizeof (mac_file));
		burn (mac_calc, sizeof (mac_calc));
		return VC_ERR_PASSWORD;
	}

	aes_init ();
	aes_encrypt_ctx cx;
	memset (&cx, 0, sizeof (cx));
	aes_encrypt_key256 (aes_key, &cx);
	uint8_t counter[kIvSize];
	memcpy (counter, iv, kIvSize);

	if (fseeko (in, (off_t) kHeaderSize, SEEK_SET) != 0)
	{
		fclose (in);
		return VC_ERR_IO;
	}

	uint8_t prefix[2];
	if (payload_size < 2 || fread (prefix, 1, 2, in) != 2)
	{
		fclose (in);
		return VC_ERR_FORMAT;
	}
	ctr_xor (&cx, counter, prefix, 2);
	uint16_t name_len = (uint16_t) (prefix[0] | (prefix[1] << 8));
	if (name_len == 0 || name_len > 255 || payload_size < (uint64_t) 2 + name_len)
	{
		fclose (in);
		return VC_ERR_FORMAT;
	}
	char name[256];
	memset (name, 0, sizeof (name));
	if (fread (name, 1, name_len, in) != name_len)
	{
		fclose (in);
		return VC_ERR_IO;
	}
	ctr_xor (&cx, counter, (uint8_t *) name, name_len);
	name[name_len] = 0;
	sanitize_name (name);

	char dest[1024];
	snprintf (dest, sizeof (dest), "%s/%s", dest_dir, name);
	if (out_path && out_path_size)
	{
		strncpy (out_path, dest, out_path_size - 1);
		out_path[out_path_size - 1] = 0;
	}

	mkdir (dest_dir, 0700);
	FILE *out = fopen_private_write (dest);
	if (!out)
	{
		fclose (in);
		return VC_ERR_IO;
	}

	uint64_t left = payload_size - 2 - name_len;
	rc = VC_OK;
	while (left)
	{
		size_t n = left < kChunk ? (size_t) left : kChunk;
		if (fread (&chunk[0], 1, n, in) != n)
		{
			rc = VC_ERR_IO;
			break;
		}
		ctr_xor (&cx, counter, &chunk[0], n);
		if (fwrite (&chunk[0], 1, n, out) != n)
		{
			rc = VC_ERR_IO;
			break;
		}
		left -= n;
	}

	fclose (in);
	if (fclose (out) != 0 && rc == VC_OK)
		rc = VC_ERR_IO;
	if (rc != VC_OK)
		unlink (dest);

	burn (aes_key, sizeof (aes_key));
	burn (mac_key, sizeof (mac_key));
	unlock_secret (aes_key, sizeof (aes_key));
	unlock_secret (mac_key, sizeof (mac_key));
	burn (salt, sizeof (salt));
	burn (iv, sizeof (iv));
	burn (counter, sizeof (counter));
	burn (prefix, sizeof (prefix));
	burn (mac_file, sizeof (mac_file));
	burn (mac_calc, sizeof (mac_calc));
	burn (&cx, sizeof (cx));
	burn (&hmac, sizeof (hmac));
	if (!chunk.empty ())
		burn (&chunk[0], chunk.size ());
	return rc;
}
