/*
 Copyright (c) 2026 Shivam Mangesh Pingale. All rights reserved.
*/

#include "mobile_mocks.h"

#include "Crypto/Aes.h"
#include "Crypto/Sha2.h"
#include "Common/Tcdefs.h"

#include <string.h>

int mock_jni_copy_utf(JNIEnv *env, jstring s, char *out, size_t out_size, size_t *out_len)
{
	if (out_len)
		*out_len = 0;
	if (!out || out_size == 0)
		return VC_ERR_ARGUMENT;
	out[0] = 0;
	if (!s)
		return VC_OK;
	if (env->GetStringLength(s) > VC_JNI_UTF_MAX)
		return VC_ERR_ARGUMENT;
	const char *p = env->GetStringUTFChars(s, nullptr);
	if (!p)
		return VC_ERR_MEMORY;
	size_t n = strlen(p);
	if (n > (size_t) VC_JNI_UTF_MAX || n + 1 > out_size)
	{
		env->ReleaseStringUTFChars(s, p);
		return VC_ERR_ARGUMENT;
	}
	memcpy(out, p, n + 1);
	if (out_len)
		*out_len = n;
	env->ReleaseStringUTFChars(s, p);
	return VC_OK;
}

int mock_jni_live_handle(jlong handle)
{
	return handle < (jlong) VC_ERR_UNSUPPORTED || handle > 0;
}

namespace {

const uint8_t kMockDeviceKey[32] = {
	0x10, 0x32, 0x54, 0x76, 0x98, 0xba, 0xdc, 0xfe,
	0x0f, 0x1e, 0x2d, 0x3c, 0x4b, 0x5a, 0x69, 0x78,
	0x87, 0x96, 0xa5, 0xb4, 0xc3, 0xd2, 0xe1, 0xf0,
	0x01, 0x23, 0x45, 0x67, 0x89, 0xab, 0xcd, 0xef
};

void ctr_inc(uint8_t counter[16])
{
	for (int i = 15; i >= 0; --i)
	{
		if (++counter[i])
			break;
	}
}

void aes_ctr_xor(aes_encrypt_ctx *cx, uint8_t counter[16], uint8_t *data, size_t len)
{
	uint8_t ks[16];
	size_t i = 0;
	while (i < len)
	{
		aes_encrypt(counter, ks, cx);
		ctr_inc(counter);
		size_t n = len - i < 16 ? len - i : 16;
		for (size_t j = 0; j < n; ++j)
			data[i + j] = (uint8_t) (data[i + j] ^ ks[j]);
		i += n;
	}
	burn(ks, sizeof(ks));
}

} // namespace

int mock_enclave_seal(const uint8_t *keyfile, size_t n, uint8_t *blob, size_t blob_cap, size_t *blob_len)
{
	if (!keyfile || !blob || !blob_len || n == 0 || n > 1024)
		return VC_ERR_ARGUMENT;
	if (blob_cap < 16 + n + 32)
		return VC_ERR_ARGUMENT;

	uint8_t iv[16];
	memset(iv, 0x5a, sizeof(iv));
	iv[15] = (uint8_t) n;

	uint8_t device[32];
	memcpy(device, kMockDeviceKey, 32);

	aes_init();
	aes_encrypt_ctx cx;
	memset(&cx, 0, sizeof(cx));
	aes_encrypt_key256(device, &cx);

	uint8_t counter[16];
	memcpy(counter, iv, 16);
	memcpy(blob, iv, 16);
	memcpy(blob + 16, keyfile, n);
	aes_ctr_xor(&cx, counter, blob + 16, n);

	sha256(blob + 16 + n, blob, (uint_32t) (16 + n));
	*blob_len = 16 + n + 32;

	burn(device, sizeof(device));
	burn(&cx, sizeof(cx));
	burn(counter, sizeof(counter));
	burn(iv, sizeof(iv));
	return VC_OK;
}

int mock_enclave_open(const uint8_t *blob, size_t blob_len, uint8_t *keyfile, size_t keyfile_cap, size_t *out_n)
{
	if (!blob || !keyfile || !out_n || blob_len < 16 + 1 + 32)
		return VC_ERR_ARGUMENT;
	size_t n = blob_len - 16 - 32;
	if (n > keyfile_cap)
		return VC_ERR_ARGUMENT;

	uint8_t mac[32];
	sha256(mac, blob, (uint_32t) (16 + n));
	uint8_t d = 0;
	for (size_t i = 0; i < 32; ++i)
		d = (uint8_t) (d | (mac[i] ^ blob[16 + n + i]));
	if (d != 0)
	{
		burn(mac, sizeof(mac));
		return VC_ERR_PASSWORD;
	}

	uint8_t device[32];
	memcpy(device, kMockDeviceKey, 32);
	aes_init();
	aes_encrypt_ctx cx;
	memset(&cx, 0, sizeof(cx));
	aes_encrypt_key256(device, &cx);
	uint8_t counter[16];
	memcpy(counter, blob, 16);
	memcpy(keyfile, blob + 16, n);
	aes_ctr_xor(&cx, counter, keyfile, n);
	*out_n = n;

	burn(device, sizeof(device));
	burn(&cx, sizeof(cx));
	burn(counter, sizeof(counter));
	burn(mac, sizeof(mac));
	return VC_OK;
}
