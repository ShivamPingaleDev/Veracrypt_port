/*
 Copyright (c) 2026 Shivam Mangesh Pingale. All rights reserved.

 Crypto safety unit tests: AES known-answer, CTR partial blocks, secure wipe,
 wrap header overflow reject-before-KDF, JNI UTF cap, mocked enclave keyfile.
 Compile: ports/shared/run_crypto_safety_test.sh
*/

#include "unity_lite.h"
#include "mobile_mocks.h"
#include "vc_mobile.h"

#include "Crypto/Aes.h"
#include "Common/Tcdefs.h"

#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>
#include <vector>

static char g_tmp[512];

static void write_file(const char *path, const void *data, size_t n)
{
	FILE *f = fopen(path, "wb");
	TEST_ASSERT_NOT_NULL(f);
	if (!f)
		return;
	if (n)
		TEST_ASSERT_TRUE(fwrite(data, 1, n, f) == n);
	fclose(f);
}

/* FIPS-197 Appendix C.3 AES-256. Exact 16-byte block transform. */
static void test_aes256_fips197_c3(void)
{
	static const unsigned char key[32] = {
		0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
		0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f,
		0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
		0x18, 0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e, 0x1f
	};
	static const unsigned char plain[16] = {
		0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77,
		0x88, 0x99, 0xaa, 0xbb, 0xcc, 0xdd, 0xee, 0xff
	};
	static const unsigned char want[16] = {
		0x8e, 0xa2, 0xb7, 0xca, 0x51, 0x67, 0x45, 0xbf,
		0xea, 0xfc, 0x49, 0x90, 0x4b, 0x49, 0x60, 0x89
	};

	aes_init();
	aes_encrypt_ctx enc;
	aes_decrypt_ctx dec;
	memset(&enc, 0, sizeof(enc));
	memset(&dec, 0, sizeof(dec));
	TEST_ASSERT_EQUAL_INT(0, aes_encrypt_key256(key, &enc));
	TEST_ASSERT_EQUAL_INT(0, aes_decrypt_key256(key, &dec));

	unsigned char out[16];
	unsigned char back[16];
	memset(out, 0, sizeof(out));
	memset(back, 0, sizeof(back));
	TEST_ASSERT_EQUAL_INT(0, aes_encrypt(plain, out, &enc));
	TEST_ASSERT_EQUAL_MEMORY(want, out, 16);
	TEST_ASSERT_EQUAL_INT(0, aes_decrypt(out, back, &dec));
	TEST_ASSERT_EQUAL_MEMORY(plain, back, 16);

	burn(&enc, sizeof(enc));
	burn(&dec, sizeof(dec));
	burn(out, sizeof(out));
	burn(back, sizeof(back));
}

/* Wrap is AES-256-CTR: no PKCS#7. Last partial block uses a truncated keystream. */
static void test_aes256_ctr_partial_blocks(void)
{
	static const unsigned char key[32] = {
		0x60, 0x3d, 0xeb, 0x10, 0x15, 0xca, 0x71, 0xbe,
		0x2b, 0x73, 0xae, 0xf0, 0x85, 0x7d, 0x77, 0x81,
		0x1f, 0x35, 0x2c, 0x07, 0x3b, 0x61, 0x08, 0xd7,
		0x2d, 0x98, 0x10, 0xa3, 0x09, 0x14, 0xdf, 0xf4
	};
	unsigned char iv[16];
	memset(iv, 0xf0, sizeof(iv));

	aes_init();
	aes_encrypt_ctx cx;
	memset(&cx, 0, sizeof(cx));
	aes_encrypt_key256(key, &cx);

	unsigned char ks0[16], ks1[16];
	unsigned char ctr[16];
	memcpy(ctr, iv, 16);
	aes_encrypt(ctr, ks0, &cx);
	for (int i = 15; i >= 0; --i)
	{
		if (++ctr[i])
			break;
	}
	aes_encrypt(ctr, ks1, &cx);

	unsigned char p15[15], c15[15], r15[15];
	unsigned char p16[16], c16[16], r16[16];
	unsigned char p17[17], c17[17], r17[17];
	memset(p15, 0x11, sizeof(p15));
	memset(p16, 0x22, sizeof(p16));
	memset(p17, 0x33, sizeof(p17));
	memcpy(c15, p15, 15);
	memcpy(c16, p16, 16);
	memcpy(c17, p17, 17);
	for (int i = 0; i < 15; ++i)
		c15[i] ^= ks0[i];
	for (int i = 0; i < 16; ++i)
		c16[i] ^= ks0[i];
	for (int i = 0; i < 16; ++i)
		c17[i] ^= ks0[i];
	c17[16] ^= ks1[0];

	memcpy(r15, c15, 15);
	memcpy(r16, c16, 16);
	memcpy(r17, c17, 17);
	for (int i = 0; i < 15; ++i)
		r15[i] ^= ks0[i];
	for (int i = 0; i < 16; ++i)
		r16[i] ^= ks0[i];
	for (int i = 0; i < 16; ++i)
		r17[i] ^= ks0[i];
	r17[16] ^= ks1[0];

	TEST_ASSERT_EQUAL_MEMORY(p15, r15, 15);
	TEST_ASSERT_EQUAL_MEMORY(p16, r16, 16);
	TEST_ASSERT_EQUAL_MEMORY(p17, r17, 17);
	TEST_ASSERT_TRUE(memcmp(c15, p15, 15) != 0);
	TEST_ASSERT_TRUE(c15[14] != p15[14]);

	burn(&cx, sizeof(cx));
	burn(ks0, sizeof(ks0));
	burn(ks1, sizeof(ks1));
	burn(ctr, sizeof(ctr));
}

static void test_secure_wipe_zeros_and_null_is_safe(void)
{
	unsigned char buf[48];
	memset(buf, 0xa5, sizeof(buf));
	vc_secure_wipe(NULL, sizeof(buf));
	vc_secure_wipe(buf, 0);
	TEST_ASSERT_TRUE(buf[0] == 0xa5);

	vc_secure_wipe(buf, sizeof(buf));
	for (size_t i = 0; i < sizeof(buf); ++i)
		TEST_ASSERT_EQUAL_INT(0, buf[i]);
}

static void store_u64(uint8_t *p, uint64_t v)
{
	for (int i = 0; i < 8; ++i)
		p[i] = (uint8_t) (v >> (8 * i));
}

static void store_u32(uint8_t *p, uint32_t v)
{
	p[0] = (uint8_t) v;
	p[1] = (uint8_t) (v >> 8);
	p[2] = (uint8_t) (v >> 16);
	p[3] = (uint8_t) (v >> 24);
}

/* Malformed wrap must fail before Argon2 (32 MiB). Integer overflow / short files. */
static void test_unwrap_rejects_overflow_before_kdf(void)
{
	char path[512];
	char outdir[512];
	char outpath[256];
	snprintf(path, sizeof(path), "%s/bad.vcpw", g_tmp);
	snprintf(outdir, sizeof(outdir), "%s/out", g_tmp);
	mkdir(outdir, 0700);

	TEST_ASSERT_EQUAL_INT(VC_ERR_ARGUMENT, vc_unwrap_file(NULL, outdir, "pw", 2, outpath, sizeof(outpath)));
	TEST_ASSERT_EQUAL_INT(0, vc_is_wrap(path));

	uint8_t tiny[8];
	memset(tiny, 0, sizeof(tiny));
	memcpy(tiny, "VCPW", 4);
	write_file(path, tiny, sizeof(tiny));
	TEST_ASSERT_EQUAL_INT(1, vc_is_wrap(path));
	int rc = vc_unwrap_file(path, outdir, "password-for-fuzz", 17, outpath, sizeof(outpath));
	TEST_ASSERT_EQUAL_INT(VC_ERR_FORMAT, rc);

	uint8_t header[76 + 32];
	memset(header, 0, sizeof(header));
	memcpy(header, "VCPW", 4);
	header[4] = 1;
	header[5] = 1;
	header[6] = 1;
	store_u32(header + 8, 32768);
	store_u32(header + 12, 3);
	store_u32(header + 16, 1);
	store_u64(header + 68, UINT64_MAX - 8);
	write_file(path, header, sizeof(header));
	TEST_ASSERT_EQUAL_INT(1, vc_is_wrap(path));
	rc = vc_unwrap_file(path, outdir, "password-for-fuzz", 17, outpath, sizeof(outpath));
	TEST_ASSERT_TRUE(rc == VC_ERR_FORMAT || rc == VC_ERR_IO);

	store_u32(header + 8, 1);
	write_file(path, header, sizeof(header));
	rc = vc_unwrap_file(path, outdir, "password-for-fuzz", 17, outpath, sizeof(outpath));
	TEST_ASSERT_EQUAL_INT(VC_ERR_FORMAT, rc);
}

static void test_jni_utf_cap_and_live_handle(void)
{
	JNIEnv env;
	MockJniRef empty;
	empty.kind = MockJniRef::STR;
	char buf[16];
	size_t n = 99;
	TEST_ASSERT_EQUAL_INT(VC_OK, mock_jni_copy_utf(&env, nullptr, buf, sizeof(buf), &n));
	TEST_ASSERT_EQUAL_UINT64(0, n);
	TEST_ASSERT_EQUAL_INT(0, buf[0]);

	TEST_ASSERT_EQUAL_INT(VC_OK, mock_jni_copy_utf(&env, &empty, buf, sizeof(buf), &n));
	TEST_ASSERT_EQUAL_UINT64(0, n);

	MockJniRef ok;
	ok.kind = MockJniRef::STR;
	ok.utf = "cache/volume.hc";
	char path[64];
	TEST_ASSERT_EQUAL_INT(VC_OK, mock_jni_copy_utf(&env, &ok, path, sizeof(path), &n));
	TEST_ASSERT_EQUAL_STRING("cache/volume.hc", path);

	MockJniRef huge;
	huge.kind = MockJniRef::STR;
	huge.utf.assign((size_t) VC_JNI_UTF_MAX + 1, 'A');
	TEST_ASSERT_EQUAL_INT(VC_ERR_ARGUMENT, mock_jni_copy_utf(&env, &huge, path, sizeof(path), &n));

	TEST_ASSERT_FALSE(mock_jni_live_handle(0));
	TEST_ASSERT_FALSE(mock_jni_live_handle((jlong) VC_ERR_PASSWORD));
	TEST_ASSERT_FALSE(mock_jni_live_handle((jlong) VC_ERR_UNSUPPORTED));
	TEST_ASSERT_TRUE(mock_jni_live_handle(1));
	TEST_ASSERT_TRUE(mock_jni_live_handle((jlong) (intptr_t) path));
}

static void test_enclave_keyfile_roundtrip_then_wipe(void)
{
	uint8_t keyfile[VC_BIO_KEYFILE];
	for (int i = 0; i < VC_BIO_KEYFILE; ++i)
		keyfile[i] = (uint8_t) (0xC0 + i);

	uint8_t blob[16 + VC_BIO_KEYFILE + 32];
	size_t blob_len = 0;
	TEST_ASSERT_EQUAL_INT(VC_OK, mock_enclave_seal(keyfile, sizeof(keyfile), blob, sizeof(blob), &blob_len));
	TEST_ASSERT_TRUE(blob_len == sizeof(blob));
	TEST_ASSERT_TRUE(memcmp(blob + 16, keyfile, sizeof(keyfile)) != 0);

	uint8_t opened[VC_BIO_KEYFILE];
	memset(opened, 0, sizeof(opened));
	size_t out_n = 0;
	TEST_ASSERT_EQUAL_INT(VC_OK, mock_enclave_open(blob, blob_len, opened, sizeof(opened), &out_n));
	TEST_ASSERT_EQUAL_UINT64(VC_BIO_KEYFILE, out_n);
	TEST_ASSERT_EQUAL_MEMORY(keyfile, opened, VC_BIO_KEYFILE);

	blob[20] ^= 0x01;
	uint8_t bad[VC_BIO_KEYFILE];
	TEST_ASSERT_EQUAL_INT(VC_ERR_PASSWORD, mock_enclave_open(blob, blob_len, bad, sizeof(bad), &out_n));

	vc_secure_wipe(keyfile, sizeof(keyfile));
	vc_secure_wipe(opened, sizeof(opened));
	vc_secure_wipe(blob, sizeof(blob));
	for (size_t i = 0; i < sizeof(keyfile); ++i)
		TEST_ASSERT_EQUAL_INT(0, keyfile[i]);
}

int main(void)
{
	snprintf(g_tmp, sizeof(g_tmp), "%s/vcport-crypto-safety-XXXXXX",
		getenv("TMPDIR") ? getenv("TMPDIR") : "/tmp");
	if (!mkdtemp(g_tmp))
	{
		perror("mkdtemp");
		return 2;
	}

	UNITY_BEGIN();
	RUN_TEST(test_aes256_fips197_c3);
	RUN_TEST(test_aes256_ctr_partial_blocks);
	RUN_TEST(test_secure_wipe_zeros_and_null_is_safe);
	RUN_TEST(test_unwrap_rejects_overflow_before_kdf);
	RUN_TEST(test_jni_utf_cap_and_live_handle);
	RUN_TEST(test_enclave_keyfile_roundtrip_then_wipe);
	int rc = UNITY_END();

	char cmd[640];
	snprintf(cmd, sizeof(cmd), "rm -rf \"%s\"", g_tmp);
	if (system(cmd) != 0)
		(void) 0;
	return rc == 0 ? 0 : 1;
}
