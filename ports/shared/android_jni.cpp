/*
 Copyright (c) 2026 Shivam Pingale. All rights reserved.
*/

#include <jni.h>
#include <string.h>
#include <stdio.h>
#include "vc_mobile.h"

extern "C" JNIEXPORT jlong JNICALL
Java_dev_shivampingale_vcport_NativeBridge_openVolume(
	JNIEnv *env, jobject, jstring path, jstring password, jint pim, jboolean backup)
{
	const char *cPath = env->GetStringUTFChars(path, nullptr);
	const char *cPassword = env->GetStringUTFChars(password, nullptr);
	VcOpenOptions options = {};
	options.path = cPath;
	options.password = cPassword;
	options.password_len = cPassword ? strlen(cPassword) : 0;
	options.pim = pim;
	options.use_backup_header = backup ? 1 : 0;
	int error = 0;
	VcVolume *volume = vc_open(&options, &error);
	env->ReleaseStringUTFChars(path, cPath);
	env->ReleaseStringUTFChars(password, cPassword);
	if (!volume)
		return (jlong) error;
	return (jlong) volume;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_shivampingale_vcport_NativeBridge_closeVolume(JNIEnv *, jobject, jlong handle)
{
	if (handle > 0)
		vc_close(reinterpret_cast<VcVolume *>(handle));
}

extern "C" JNIEXPORT jlong JNICALL
Java_dev_shivampingale_vcport_NativeBridge_volumeSize(JNIEnv *, jobject, jlong handle)
{
	if (handle <= 0)
		return 0;
	return (jlong) vc_size(reinterpret_cast<VcVolume *>(handle));
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_dev_shivampingale_vcport_NativeBridge_listRoot(JNIEnv *env, jobject, jlong handle)
{
	jclass stringClass = env->FindClass("java/lang/String");
	if (handle <= 0)
		return env->NewObjectArray(0, stringClass, nullptr);

	VcDirEntry entries[128];
	int n = vc_list_root(reinterpret_cast<VcVolume *>(handle), entries, 128);
	if (n < 0)
		n = 0;
	jobjectArray result = env->NewObjectArray(n, stringClass, nullptr);
	for (int i = 0; i < n; ++i)
	{
		char line[320];
		snprintf(line, sizeof(line), "%s\t%u\t%llu",
			entries[i].name,
			(unsigned) entries[i].is_dir,
			(unsigned long long) entries[i].size);
		env->SetObjectArrayElement(result, i, env->NewStringUTF(line));
	}
	return result;
}
