/*
 Copyright (c) 2026 Shivam Mangesh Pingale. All rights reserved.
*/

#include <jni.h>
#include <stdio.h>
#include <string.h>
#include <string>
#include <vector>
#ifdef __linux__
#include <sys/prctl.h>
#endif
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

static void jni_copy_string_array(JNIEnv *env, jobjectArray arr, std::vector<std::string> &owned, std::vector<const char *> &ptrs)
{
	jsize n = arr ? env->GetArrayLength(arr) : 0;
	owned.reserve(owned.size() + (size_t) n);
	for (jsize i = 0; i < n; ++i)
	{
		jstring item = (jstring) env->GetObjectArrayElement(arr, i);
		if (!item)
			continue;
		owned.emplace_back(jni_copy_utf(env, item));
		env->DeleteLocalRef(item);
	}
	ptrs.clear();
	ptrs.reserve(owned.size());
	for (const std::string &s : owned)
		ptrs.push_back(s.c_str());
}

static void jni_wipe_string(std::string &s)
{
	if (!s.empty())
		vc_secure_wipe(&s[0], s.size());
	s.clear();
	s.shrink_to_fit();
}

/* Error codes are 0 and VC_ERR_* (-1..-6). A live VcVolume* may look negative
 * as signed jlong on 64-bit Android. */
static int jni_live_handle(jlong handle)
{
	return handle < (jlong) VC_ERR_UNSUPPORTED || handle > 0;
}

extern "C" JNIEXPORT jlong JNICALL
Java_dev_shivampingale_vcport_NativeBridge_openVolume(
	JNIEnv *env, jobject, jstring path, jstring password, jint pim, jboolean backup,
	jobjectArray keyfiles, jboolean readOnly, jboolean protectHidden, jstring hiddenPassword, jint hiddenPim)
{
	if (!path)
		return (jlong) VC_ERR_ARGUMENT;

	std::string cPath = jni_copy_utf(env, path);
	std::string cPassword = jni_copy_utf(env, password);
	std::string cHidden = jni_copy_utf(env, hiddenPassword);
	VcOpenOptions options = {};
	options.path = cPath.c_str();
	options.password = cPassword.c_str();
	options.password_len = cPassword.size();
	options.pim = pim;
	options.use_backup_header = backup ? 1 : 0;
	options.read_only = readOnly ? 1 : 0;
	options.protect_hidden = protectHidden ? 1 : 0;
	options.hidden_password = cHidden.c_str();
	options.hidden_password_len = cHidden.size();
	options.hidden_pim = hiddenPim;

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
	jni_wipe_string(cHidden);
	if (!volume)
		return (jlong) error;
	return (jlong) volume;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_shivampingale_vcport_NativeBridge_closeVolume(JNIEnv *, jobject, jlong handle)
{
	if (jni_live_handle(handle))
		vc_close(reinterpret_cast<VcVolume *>(handle));
}

extern "C" JNIEXPORT jlong JNICALL
Java_dev_shivampingale_vcport_NativeBridge_volumeSize(JNIEnv *, jobject, jlong handle)
{
	if (!jni_live_handle(handle))
		return 0;
	return (jlong) vc_size(reinterpret_cast<VcVolume *>(handle));
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_dev_shivampingale_vcport_NativeBridge_listDir(JNIEnv *env, jobject, jlong handle, jstring path, jint skip)
{
	jclass stringClass = env->FindClass("java/lang/String");
	if (!jni_live_handle(handle))
		return env->NewObjectArray(0, stringClass, nullptr);

	std::string cPath = jni_copy_utf(env, path);
	if (cPath.empty())
		cPath = "/";
	const int cap = VC_LIST_UI_MAX;
	std::vector<VcDirEntry> entries((size_t) cap + 1);
	int n = vc_list_dir_from(reinterpret_cast<VcVolume *>(handle), cPath.c_str(), entries.data(), cap + 1, skip);
	if (n < 0)
	{
		char line[64];
		snprintf(line, sizeof(line), "!error!\t0\t%d", n);
		jobjectArray result = env->NewObjectArray(1, stringClass, nullptr);
		env->SetObjectArrayElement(result, 0, env->NewStringUTF(line));
		return result;
	}
	int truncated = 0;
	if (n > cap)
	{
		n = cap;
		truncated = 1;
	}
	jobjectArray result = env->NewObjectArray(n + truncated, stringClass, nullptr);
	for (int i = 0; i < n; ++i)
	{
		char line[320];
		snprintf(line, sizeof(line), "%s\t%u\t%llu\t%u\t%u",
			entries[i].name,
			(unsigned) entries[i].is_dir,
			(unsigned long long) entries[i].size,
			(unsigned) entries[i].dos_date,
			(unsigned) entries[i].dos_time);
		env->SetObjectArrayElement(result, i, env->NewStringUTF(line));
	}
	if (truncated)
	{
		char line[64];
		snprintf(line, sizeof(line), "!truncated!\t0\t%d", cap);
		env->SetObjectArrayElement(result, n, env->NewStringUTF(line));
	}
	return result;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_dev_shivampingale_vcport_NativeBridge_listRoot(JNIEnv *env, jobject obj, jlong handle)
{
	return Java_dev_shivampingale_vcport_NativeBridge_listDir(env, obj, handle, env->NewStringUTF("/"), 0);
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_shivampingale_vcport_NativeBridge_exportFile(
	JNIEnv *env, jobject, jlong handle, jstring name, jstring dest)
{
	if (!jni_live_handle(handle) || !name || !dest)
		return VC_ERR_ARGUMENT;
	std::string cName = jni_copy_utf(env, name);
	std::string cDest = jni_copy_utf(env, dest);
	return vc_export_file(reinterpret_cast<VcVolume *>(handle), cName.c_str(), cDest.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_shivampingale_vcport_NativeBridge_importFile(
	JNIEnv *env, jobject, jlong handle, jstring destDir, jstring src, jstring destName)
{
	if (!jni_live_handle(handle) || !src)
		return VC_ERR_ARGUMENT;
	std::string cDir = jni_copy_utf(env, destDir);
	std::string cSrc = jni_copy_utf(env, src);
	std::string cName = jni_copy_utf(env, destName);
	return vc_import_file(reinterpret_cast<VcVolume *>(handle), cDir.c_str(), cSrc.c_str(),
		cName.empty() ? nullptr : cName.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_shivampingale_vcport_NativeBridge_deleteFile(
	JNIEnv *env, jobject, jlong handle, jstring path)
{
	if (!jni_live_handle(handle) || !path)
		return VC_ERR_ARGUMENT;
	std::string cPath = jni_copy_utf(env, path);
	return vc_delete_file(reinterpret_cast<VcVolume *>(handle), cPath.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_shivampingale_vcport_NativeBridge_mkdir(
	JNIEnv *env, jobject, jlong handle, jstring parent, jstring name)
{
	if (!jni_live_handle(handle) || !name)
		return VC_ERR_ARGUMENT;
	std::string cParent = jni_copy_utf(env, parent);
	std::string cName = jni_copy_utf(env, name);
	return vc_mkdir(reinterpret_cast<VcVolume *>(handle), cParent.c_str(), cName.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_shivampingale_vcport_NativeBridge_rmdir(
	JNIEnv *env, jobject, jlong handle, jstring path)
{
	if (!jni_live_handle(handle) || !path)
		return VC_ERR_ARGUMENT;
	std::string cPath = jni_copy_utf(env, path);
	return vc_rmdir(reinterpret_cast<VcVolume *>(handle), cPath.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_shivampingale_vcport_NativeBridge_renameFile(
	JNIEnv *env, jobject, jlong handle, jstring path, jstring newName)
{
	if (!jni_live_handle(handle) || !path || !newName)
		return VC_ERR_ARGUMENT;
	std::string cPath = jni_copy_utf(env, path);
	std::string cName = jni_copy_utf(env, newName);
	return vc_rename(reinterpret_cast<VcVolume *>(handle), cPath.c_str(), cName.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_shivampingale_vcport_NativeBridge_wipeFreeSpace(JNIEnv *, jobject, jlong handle)
{
	if (!jni_live_handle(handle))
		return VC_ERR_ARGUMENT;
	return vc_wipe_free_space(reinterpret_cast<VcVolume *>(handle));
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

extern "C" JNIEXPORT jint JNICALL
Java_dev_shivampingale_vcport_NativeBridge_createVolume(
	JNIEnv *env, jobject, jstring path, jstring password, jint pim, jlong sizeBytes,
	jstring cipher, jstring kdf, jobjectArray keyfiles,
	jstring hiddenPassword, jint hiddenPim, jlong hiddenSizeBytes, jobjectArray hiddenKeyfiles)
{
	if (!path || sizeBytes <= 0)
		return VC_ERR_ARGUMENT;
	std::string cPath = jni_copy_utf(env, path);
	std::string cPassword = jni_copy_utf(env, password);
	std::string cCipher = jni_copy_utf(env, cipher);
	std::string cKdf = jni_copy_utf(env, kdf);
	std::string cHidden = jni_copy_utf(env, hiddenPassword);
	VcCreateOptions options = {};
	options.path = cPath.c_str();
	options.password = cPassword.c_str();
	options.password_len = cPassword.size();
	options.pim = pim;
	options.size_bytes = (uint64_t) sizeBytes;
	options.cipher = cCipher.c_str();
	options.kdf = cKdf.c_str();

	std::vector<std::string> owned;
	std::vector<const char *> ptrs;
	jni_copy_string_array(env, keyfiles, owned, ptrs);
	if (!ptrs.empty())
	{
		options.keyfiles = ptrs.data();
		options.keyfile_count = ptrs.size();
	}

	std::vector<std::string> hiddenOwned;
	std::vector<const char *> hiddenPtrs;
	jni_copy_string_array(env, hiddenKeyfiles, hiddenOwned, hiddenPtrs);
	options.hidden_size_bytes = hiddenSizeBytes > 0 ? (uint64_t) hiddenSizeBytes : 0;
	options.hidden_password = cHidden.c_str();
	options.hidden_password_len = cHidden.size();
	options.hidden_pim = hiddenPim;
	if (!hiddenPtrs.empty())
	{
		options.hidden_keyfiles = hiddenPtrs.data();
		options.hidden_keyfile_count = hiddenPtrs.size();
	}

	int rc = vc_create_volume(&options);
	jni_wipe_string(cPassword);
	jni_wipe_string(cHidden);
	return rc;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_shivampingale_vcport_NativeBridge_addEntropy(JNIEnv *env, jobject, jbyteArray samples)
{
	if (!samples)
		return;
	jsize n = env->GetArrayLength(samples);
	if (n <= 0)
		return;
	jbyte *p = env->GetByteArrayElements(samples, nullptr);
	if (!p)
		return;
	vc_entropy_add(p, (size_t) n);
	env->ReleaseByteArrayElements(samples, p, JNI_ABORT);
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_shivampingale_vcport_NativeBridge_entropyPercent(JNIEnv *, jobject)
{
	return vc_entropy_percent();
}

extern "C" JNIEXPORT void JNICALL
Java_dev_shivampingale_vcport_NativeBridge_resetEntropy(JNIEnv *, jobject)
{
	vc_entropy_reset();
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_shivampingale_vcport_NativeBridge_changeHeader(
	JNIEnv *env, jobject, jstring path, jstring password, jint pim, jobjectArray keyfiles,
	jboolean backup, jstring newPassword, jint newPim, jstring newKdf, jobjectArray newKeyfiles)
{
	if (!path)
		return VC_ERR_ARGUMENT;
	std::string cPath = jni_copy_utf(env, path);
	std::string cPassword = jni_copy_utf(env, password);
	std::string cNewPassword = jni_copy_utf(env, newPassword);
	std::string cKdf = jni_copy_utf(env, newKdf);
	VcChangeHeaderOptions options = {};
	options.path = cPath.c_str();
	options.password = cPassword.c_str();
	options.password_len = cPassword.size();
	options.pim = pim;
	options.use_backup_header = backup ? 1 : 0;
	options.new_password = cNewPassword.c_str();
	options.new_password_len = cNewPassword.size();
	options.new_pim = newPim;
	options.new_kdf = cKdf.c_str();
	std::vector<std::string> owned;
	std::vector<const char *> ptrs;
	jni_copy_string_array(env, keyfiles, owned, ptrs);
	if (!ptrs.empty())
	{
		options.keyfiles = ptrs.data();
		options.keyfile_count = ptrs.size();
	}
	std::vector<std::string> newOwned;
	std::vector<const char *> newPtrs;
	jni_copy_string_array(env, newKeyfiles, newOwned, newPtrs);
	if (!newPtrs.empty())
	{
		options.new_keyfiles = newPtrs.data();
		options.new_keyfile_count = newPtrs.size();
	}
	int rc = vc_change_header(&options);
	jni_wipe_string(cPassword);
	jni_wipe_string(cNewPassword);
	return rc;
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_shivampingale_vcport_NativeBridge_backupHeaders(
	JNIEnv *env, jobject, jstring volumePath, jstring backupPath, jstring password, jint pim, jobjectArray keyfiles)
{
	std::string cVol = jni_copy_utf(env, volumePath);
	std::string cBak = jni_copy_utf(env, backupPath);
	std::string cPassword = jni_copy_utf(env, password);
	std::vector<std::string> owned;
	std::vector<const char *> ptrs;
	jni_copy_string_array(env, keyfiles, owned, ptrs);
	int rc = vc_backup_headers(cVol.c_str(), cBak.c_str(), cPassword.c_str(), cPassword.size(), pim,
		ptrs.empty() ? nullptr : ptrs.data(), ptrs.size());
	jni_wipe_string(cPassword);
	return rc;
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_shivampingale_vcport_NativeBridge_restoreHeaders(
	JNIEnv *env, jobject, jstring volumePath, jstring backupPath, jstring password, jint pim, jobjectArray keyfiles)
{
	std::string cVol = jni_copy_utf(env, volumePath);
	std::string cBak = jni_copy_utf(env, backupPath);
	std::string cPassword = jni_copy_utf(env, password);
	std::vector<std::string> owned;
	std::vector<const char *> ptrs;
	jni_copy_string_array(env, keyfiles, owned, ptrs);
	int rc = vc_restore_headers(cVol.c_str(), cBak.c_str(), cPassword.c_str(), cPassword.size(), pim,
		ptrs.empty() ? nullptr : ptrs.data(), ptrs.size());
	jni_wipe_string(cPassword);
	return rc;
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_shivampingale_vcport_NativeBridge_generateKeyfile(JNIEnv *env, jobject, jstring path, jint size)
{
	std::string cPath = jni_copy_utf(env, path);
	return vc_generate_keyfile(cPath.c_str(), size > 0 ? (size_t) size : 128);
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_shivampingale_vcport_NativeBridge_volumeInfo(JNIEnv *env, jobject, jlong handle)
{
	char buf[512];
	if (!jni_live_handle(handle) || vc_volume_info((VcVolume *) handle, buf, sizeof(buf)) != VC_OK)
		return nullptr;
	return env->NewStringUTF(buf);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_shivampingale_vcport_NativeBridge_protectionTriggered(JNIEnv *, jobject, jlong handle)
{
	if (!jni_live_handle(handle))
		return JNI_FALSE;
	return vc_protection_triggered((VcVolume *) handle) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_shivampingale_vcport_NativeBridge_benchmark(JNIEnv *env, jobject)
{
	char buf[2048];
	if (vc_benchmark(buf, sizeof(buf)) != VC_OK)
		return env->NewStringUTF("Benchmark failed.");
	return env->NewStringUTF(buf);
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_shivampingale_vcport_NativeBridge_testVectors(JNIEnv *, jobject)
{
	return vc_test_vectors();
}

extern "C" JNIEXPORT void JNICALL
Java_dev_shivampingale_vcport_NativeBridge_resetProgress(JNIEnv *, jobject)
{
	vc_progress_reset();
}

extern "C" JNIEXPORT void JNICALL
Java_dev_shivampingale_vcport_NativeBridge_setProgress(JNIEnv *env, jobject, jint percent, jstring phase)
{
	std::string cPhase = jni_copy_utf(env, phase);
	vc_progress_set((int) percent, cPhase.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_shivampingale_vcport_NativeBridge_progressPercent(JNIEnv *, jobject)
{
	return vc_progress_percent();
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_shivampingale_vcport_NativeBridge_progressPhase(JNIEnv *env, jobject)
{
	char phase[96];
	vc_progress_phase(phase, sizeof(phase));
	return env->NewStringUTF(phase);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_shivampingale_vcport_NativeBridge_startRuntime(JNIEnv *, jobject)
{
	vc_runtime_start();
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *, void *)
{
#ifdef __linux__
	prctl(PR_SET_DUMPABLE, 0);
#endif
	/* Worker threads start from NativeBridge.startRuntime after loadLibrary. */
	return JNI_VERSION_1_6;
}
