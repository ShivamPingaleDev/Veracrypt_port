/*
 Copyright (c) 2026 Shivam Mangesh Pingale. All rights reserved.

 Host mocks for Android JNI UTF copy and a 64-byte biometric keyfile seal.
 Phone Keystore / Secure Enclave stay in Kotlin/Swift; this only lets Debian
 compile and check the C contracts (length cap, wipe after use).
*/

#ifndef VC_MOBILE_MOCKS_H
#define VC_MOBILE_MOCKS_H

#include "host_jni.h"
#include "vc_mobile.h"

#include <stddef.h>
#include <stdint.h>

enum { VC_JNI_UTF_MAX = 4096 };
enum { VC_BIO_KEYFILE = 64 };

int mock_jni_copy_utf(JNIEnv *env, jstring s, char *out, size_t out_size, size_t *out_len);
int mock_jni_live_handle(jlong handle);

/* AES-256-CTR + HMAC-SHA256 stand-in for Keystore/enclave wrap of a keyfile. */
int mock_enclave_seal(const uint8_t *keyfile, size_t n, uint8_t *blob, size_t blob_cap, size_t *blob_len);
int mock_enclave_open(const uint8_t *blob, size_t blob_len, uint8_t *keyfile, size_t keyfile_cap, size_t *out_n);

#endif
