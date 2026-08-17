/*
 Copyright (c) 2026 Shivam Mangesh Pingale. All rights reserved.

 Debian/macOS stand-in for <jni.h>. Compile android_jni.cpp with -DVC_HOST_JNI
 so JNI boundary code type-checks without an NDK. Not a JVM.
*/

#ifndef VC_HOST_JNI_H
#define VC_HOST_JNI_H

#include <stddef.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <string>
#include <vector>

#define JNIEXPORT
#define JNICALL
#define JNI_FALSE 0
#define JNI_TRUE 1
#define JNI_ABORT 2
#define JNI_VERSION_1_6 0x00010006

typedef uint8_t jboolean;
typedef int8_t jbyte;
typedef int32_t jint;
typedef int64_t jlong;
typedef jint jsize;

struct MockJniRef
{
	enum Kind
	{
		STR = 1,
		OBJARR = 2,
		BYTEARR = 3,
		CLAZZ = 4
	} kind;
	std::string utf;
	std::vector<MockJniRef *> items;
	std::vector<jbyte> bytes;
};

typedef MockJniRef *jobject;
typedef MockJniRef *jstring;
typedef MockJniRef *jclass;
typedef MockJniRef *jobjectArray;
typedef MockJniRef *jbyteArray;
typedef MockJniRef *jarray;
typedef void JavaVM;

struct JNIEnv
{
	jsize GetStringLength(jstring s)
	{
		return s ? (jsize) s->utf.size() : 0;
	}

	const char *GetStringUTFChars(jstring s, jboolean *isCopy)
	{
		if (isCopy)
			*isCopy = JNI_TRUE;
		if (!s)
			return nullptr;
		char *p = (char *) malloc(s->utf.size() + 1);
		if (!p)
			return nullptr;
		memcpy(p, s->utf.c_str(), s->utf.size() + 1);
		return p;
	}

	void ReleaseStringUTFChars(jstring, const char *p)
	{
		free((void *) p);
	}

	jsize GetArrayLength(jarray arr)
	{
		if (!arr)
			return 0;
		if (arr->kind == MockJniRef::BYTEARR)
			return (jsize) arr->bytes.size();
		return (jsize) arr->items.size();
	}

	jobject GetObjectArrayElement(jobjectArray arr, jsize i)
	{
		if (!arr || i < 0 || (size_t) i >= arr->items.size())
			return nullptr;
		return arr->items[(size_t) i];
	}

	void DeleteLocalRef(jobject) {}

	jclass FindClass(const char *name)
	{
		MockJniRef *c = new MockJniRef();
		c->kind = MockJniRef::CLAZZ;
		c->utf = name ? name : "";
		return c;
	}

	jobjectArray NewObjectArray(jsize n, jclass, jobject)
	{
		MockJniRef *a = new MockJniRef();
		a->kind = MockJniRef::OBJARR;
		a->items.assign((size_t) n, nullptr);
		return a;
	}

	void SetObjectArrayElement(jobjectArray arr, jsize i, jobject value)
	{
		if (!arr || i < 0 || (size_t) i >= arr->items.size())
			return;
		arr->items[(size_t) i] = value;
	}

	jstring NewStringUTF(const char *s)
	{
		MockJniRef *o = new MockJniRef();
		o->kind = MockJniRef::STR;
		o->utf = s ? s : "";
		return o;
	}

	jbyte *GetByteArrayElements(jbyteArray arr, jboolean *isCopy)
	{
		if (isCopy)
			*isCopy = JNI_FALSE;
		if (!arr || arr->bytes.empty())
			return nullptr;
		return arr->bytes.data();
	}

	void ReleaseByteArrayElements(jbyteArray, jbyte *, jint) {}
};

inline jstring host_jni_string(const char *s)
{
	MockJniRef *o = new MockJniRef();
	o->kind = MockJniRef::STR;
	o->utf = s ? s : "";
	return o;
}

inline jstring host_jni_string_n(size_t n, char fill)
{
	MockJniRef *o = new MockJniRef();
	o->kind = MockJniRef::STR;
	o->utf.assign(n, fill);
	return o;
}

#endif
