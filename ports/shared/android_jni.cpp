/*
 Copyright (c) 2026 Shivam Pingale. All rights reserved.
*/

#include <jni.h>
#include <stdio.h>
#include <string.h>
#include <string>
#include <vector>
#include "vc_mobile.h"

static std::string jni_copy_utf(JNIEnv *env, jstring s)
{
	if (!s)
		return std::string();
	const char *p = env->GetStringUTFChars(s, nullptr);
	std::string out(p ? p : "");
	env->ReleaseStringUTFChars(s, p);
	return out;
}

static void jni_wipe_string(std::string &s)
{
	if (!s.empty())
		vc_secure_wipe(&s[0], s.size());
	s.clear();
	s.shrink_to_fit();
}

extern "C" JNIEXPORT jlong JNICALL
Java_dev_shivampingale_vcport_NativeBridge_openVolume(
	JNIEnv *env, jobject, jstring path, jstring password, jint pim, jboolean backup,
	jobjectArray keyfiles)
{
	if (!path)
		return (jlong) VC_ERR_ARGUMENT;

	std::string cPath = jni_copy_utf(env, path);
	std::string cPassword = jni_copy_utf(env, password);
	VcOpenOptions options = {};
	options.path = cPath.c_str();
	options.password = cPassword.c_str();
	options.password_len = cPassword.size();
	options.pim = pim;
	options.use_backup_header = backup ? 1 : 0;

	jsize n = keyfiles ? env->GetArrayLength(keyfiles) : 0;
	std::vector<std::string> owned;
	std::vector<const char *> ptrs;
	owned.reserve((size_t) n);
	for (jsize i = 0; i < n; ++i)
	{
		jstring item = (jstring) env->GetObjectArrayElement(keyfiles, i);
		if (!item)
			continue;
		owned.emplace_back(jni_copy_utf(env, item));
		env->DeleteLocalRef(item);
	}
	ptrs.reserve(owned.size());
	for (const std::string &s : owned)
		ptrs.push_back(s.c_str());
	if (!ptrs.empty())
	{
		options.keyfiles = ptrs.data();
		options.keyfile_count = ptrs.size();
	}

	int error = 0;
	VcVolume *volume = vc_open(&options, &error);
	jni_wipe_string(cPassword);
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
	if (handle <= 0 || !name || !dest)
		return VC_ERR_ARGUMENT;
	std::string cName = jni_copy_utf(env, name);
	std::string cDest = jni_copy_utf(env, dest);
	return vc_export_file(reinterpret_cast<VcVolume *>(handle), cName.c_str(), cDest.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_shivampingale_vcport_NativeBridge_wrapFile(
	JNIEnv *env, jobject, jstring src, jstring dest, jstring password, jstring originalName)
{
	if (!src || !dest || !password)
		return VC_ERR_ARGUMENT;
	std::string cSrc = jni_copy_utf(env, src);
	std::string cDest = jni_copy_utf(env, dest);
	std::string cPassword = jni_copy_utf(env, password);
	std::string cName = jni_copy_utf(env, originalName);
	int rc = vc_wrap_file(cSrc.c_str(), cDest.c_str(), cPassword.c_str(), cPassword.size(),
		cName.empty() ? nullptr : cName.c_str());
	jni_wipe_string(cPassword);
	return rc;
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_shivampingale_vcport_NativeBridge_unwrapFile(
	JNIEnv *env, jobject, jstring src, jstring destDir, jstring password)
{
	if (!src || !destDir || !password)
		return nullptr;
	std::string cSrc = jni_copy_utf(env, src);
	std::string cDir = jni_copy_utf(env, destDir);
	std::string cPassword = jni_copy_utf(env, password);
	char outPath[1024];
	outPath[0] = 0;
	int rc = vc_unwrap_file(cSrc.c_str(), cDir.c_str(), cPassword.c_str(), cPassword.size(),
		outPath, sizeof(outPath));
	jni_wipe_string(cPassword);
	if (rc != VC_OK)
		return nullptr;
	return env->NewStringUTF(outPath);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_shivampingale_vcport_NativeBridge_isWrap(JNIEnv *env, jobject, jstring path)
{
	if (!path)
		return JNI_FALSE;
	std::string cPath = jni_copy_utf(env, path);
	return vc_is_wrap(cPath.c_str()) ? JNI_TRUE : JNI_FALSE;
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
