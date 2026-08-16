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

extern "C" JNIEXPORT jint JNICALL
Java_dev_shivampingale_vcport_NativeBridge_exportFile(
	JNIEnv *env, jobject, jlong handle, jstring name, jstring dest)
{
	if (handle <= 0)
		return VC_ERR_ARGUMENT;
	const char *cName = env->GetStringUTFChars(name, nullptr);
	const char *cDest = env->GetStringUTFChars(dest, nullptr);
	int rc = vc_export_file(reinterpret_cast<VcVolume *>(handle), cName, cDest);
	env->ReleaseStringUTFChars(name, cName);
		env->ReleaseStringUTFChars(dest, cDest);
	return rc;
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_shivampingale_vcport_NativeBridge_wrapFile(
	JNIEnv *env, jobject, jstring src, jstring dest, jstring password, jstring originalName)
{
	const char *cSrc = env->GetStringUTFChars(src, nullptr);
	const char *cDest = env->GetStringUTFChars(dest, nullptr);
	const char *cPassword = env->GetStringUTFChars(password, nullptr);
	const char *cName = originalName ? env->GetStringUTFChars(originalName, nullptr) : nullptr;
	size_t plen = cPassword ? strlen(cPassword) : 0;
	int rc = vc_wrap_file(cSrc, cDest, cPassword, plen, cName);
	env->ReleaseStringUTFChars(src, cSrc);
	env->ReleaseStringUTFChars(dest, cDest);
	env->ReleaseStringUTFChars(password, cPassword);
	if (cName)
		env->ReleaseStringUTFChars(originalName, cName);
	return rc;
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_shivampingale_vcport_NativeBridge_unwrapFile(
	JNIEnv *env, jobject, jstring src, jstring destDir, jstring password)
{
	const char *cSrc = env->GetStringUTFChars(src, nullptr);
	const char *cDir = env->GetStringUTFChars(destDir, nullptr);
	const char *cPassword = env->GetStringUTFChars(password, nullptr);
	char outPath[1024];
	outPath[0] = 0;
	int rc = vc_unwrap_file(cSrc, cDir, cPassword, cPassword ? strlen(cPassword) : 0, outPath, sizeof(outPath));
	env->ReleaseStringUTFChars(src, cSrc);
	env->ReleaseStringUTFChars(destDir, cDir);
	env->ReleaseStringUTFChars(password, cPassword);
	if (rc != VC_OK)
		return nullptr;
	return env->NewStringUTF(outPath);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_shivampingale_vcport_NativeBridge_isWrap(JNIEnv *env, jobject, jstring path)
{
	const char *cPath = env->GetStringUTFChars(path, nullptr);
	int yes = vc_is_wrap(cPath);
	env->ReleaseStringUTFChars(path, cPath);
	return yes ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_shivampingale_vcport_NativeBridge_generatePassword(JNIEnv *env, jobject, jint length)
{
	char buf[80];
	int n = vc_generate_password(buf, sizeof(buf), length);
	if (n < 16)
	{
		vc_secure_wipe(buf, sizeof(buf));
		return nullptr;
	}
	jstring result = env->NewStringUTF(buf);
	vc_secure_wipe(buf, sizeof(buf));
	return result;
}
