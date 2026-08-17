/*
 Copyright (c) 2026 Shivam Mangesh Pingale. All rights reserved.

 Tiny Unity-compatible assertions. No third-party download: Debian CI has no
 libcmocka-dev / Unity package requirement. Macros match Unity names so a
 later swap to ThrowTheSwitch/Unity is mechanical.
*/

#ifndef VC_UNITY_LITE_H
#define VC_UNITY_LITE_H

#include <stdio.h>
#include <string.h>

static int g_unity_failures = 0;
static int g_unity_tests = 0;

#define UNITY_BEGIN() (g_unity_failures = 0, g_unity_tests = 0, 0)
#define UNITY_END() (fprintf(stderr, "%d tests, %d failures\n", g_unity_tests, g_unity_failures), g_unity_failures)

#define RUN_TEST(fn)                                                       \
	do                                                                     \
	{                                                                      \
		g_unity_tests++;                                                   \
		fprintf(stderr, "RUN  %s\n", #fn);                                 \
		fn();                                                              \
	} while (0)

#define TEST_FAIL_MESSAGE(msg)                                             \
	do                                                                     \
	{                                                                      \
		g_unity_failures++;                                                \
		fprintf(stderr, "FAIL %s:%d: %s\n", __FILE__, __LINE__, (msg));    \
	} while (0)

#define TEST_ASSERT_TRUE(cond)                                             \
	do                                                                     \
	{                                                                      \
		if (!(cond))                                                       \
			TEST_FAIL_MESSAGE(#cond);                                      \
	} while (0)

#define TEST_ASSERT_FALSE(cond) TEST_ASSERT_TRUE(!(cond))

#define TEST_ASSERT_NULL(p) TEST_ASSERT_TRUE((p) == NULL)
#define TEST_ASSERT_NOT_NULL(p) TEST_ASSERT_TRUE((p) != NULL)

#define TEST_ASSERT_EQUAL_INT(expected, actual)                            \
	do                                                                     \
	{                                                                      \
		int _e = (int)(expected);                                          \
		int _a = (int)(actual);                                            \
		if (_e != _a)                                                      \
		{                                                                  \
			char _m[160];                                                  \
			snprintf(_m, sizeof(_m), "expected %d got %d", _e, _a);        \
			TEST_FAIL_MESSAGE(_m);                                         \
		}                                                                  \
	} while (0)

#define TEST_ASSERT_EQUAL_UINT64(expected, actual)                         \
	do                                                                     \
	{                                                                      \
		unsigned long long _e = (unsigned long long)(expected);            \
		unsigned long long _a = (unsigned long long)(actual);              \
		if (_e != _a)                                                      \
		{                                                                  \
			char _m[160];                                                  \
			snprintf(_m, sizeof(_m), "expected %llu got %llu", _e, _a);    \
			TEST_FAIL_MESSAGE(_m);                                         \
		}                                                                  \
	} while (0)

#define TEST_ASSERT_EQUAL_MEMORY(exp, act, n)                              \
	do                                                                     \
	{                                                                      \
		if ((n) > 0 && memcmp((exp), (act), (n)) != 0)                     \
			TEST_FAIL_MESSAGE("memory mismatch");                          \
	} while (0)

#define TEST_ASSERT_EQUAL_STRING(exp, act)                                 \
	do                                                                     \
	{                                                                      \
		if (!(exp) || !(act) || strcmp((exp), (act)) != 0)                 \
			TEST_FAIL_MESSAGE("string mismatch");                          \
	} while (0)

#endif
