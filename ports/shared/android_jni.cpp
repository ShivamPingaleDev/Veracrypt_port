/*
 Copyright (c) 2026 Shivam Mangesh Pingale. All rights reserved.
*/

#ifdef VC_HOST_JNI
#include "host_jni.h"
#else
#include <jni.h>
#endif
#include <stdio.h>
#include <string.h>
#include <string>
#include <vector>
#ifdef __linux__
#include <sys/prctl.h>
#endif
#include "vc_mobile.h"
#include "vc_otg_dev.h"

enum { JNI_UTF_MAX = 4096 };

static int jni_copy_utf(JNIEnv *env, jstring s, std::string &out)
{
	out.clear();
	if (!s)
		return VC_OK;
	if (env->GetStringLength(s) > JNI_UTF_MAX)
		return VC_ERR_ARGUMENT;
	const char *p = env->GetStringUTFChars(s, nullptr);
	if (!p)
		return VC_ERR_MEMORY;
	out.assign(p);
	env->ReleaseStringUTFChars(s, p);
	if (out.size() > (size_t) JNI_UTF_MAX)
		return VC_ERR_ARGUMENT;
	return VC_OK;
}

static int jni_copy_string_array(JNIEnv *env, jobjectArray arr, std::vector<std::string> &owned, std::vector<const char *> &ptrs)
{
	jsize n = arr ? env->GetArrayLength(arr) : 0;
	owned.reserve(owned.size() + (size_t) n);
	for (jsize i = 0; i < n; ++i)
	{
		jstring item = (jstring) env->GetObjectArrayElement(arr, i);
		if (!item)
			continue;
		std::string piece;
		int rc = jni_copy_utf(env, item, piece);
		env->DeleteLocalRef(item);
		if (rc != VC_OK)
			return rc;
		owned.push_back(std::move(piece));
	}
	ptrs.clear();
	ptrs.reserve(owned.size());
	for (const std::string &s : owned)
		ptrs.push_back(s.c_str());
	return VC_OK;
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

static JavaVM *g_otg_vm = nullptr;
static jclass g_otg_class = nullptr;
static jmethodID g_otg_read = nullptr;
static jmethodID g_otg_write = nullptr;
static jmethodID g_otg_size = nullptr;
static jmethodID g_otg_sector = nullptr;
static jmethodID g_otg_ready = nullptr;

static JNIEnv *otg_env()
{
	if (!g_otg_vm)
		return nullptr;
	JNIEnv *env = nullptr;
	if (g_otg_vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_OK)
		return env;
	if (g_otg_vm->AttachCurrentThread(&env, nullptr) == 0)
		return env;
	return nullptr;
}

static int otg_read_at(int slot, uint64_t offset, void *buffer, size_t size)
{
	JNIEnv *env = otg_env();
	if (!env || !g_otg_class || !g_otg_read || !buffer || size == 0 || size > 1024 * 1024)
		return -1;
	jbyteArray arr = env->NewByteArray((jsize) size);
	if (!arr)
		return -1;
	jint n = env->CallStaticIntMethod(g_otg_class, g_otg_read, slot, (jlong) offset, arr);
	if (env->ExceptionCheck())
	{
		env->ExceptionClear();
		env->DeleteLocalRef(arr);
		return -1;
	}
	if (n > 0)
		env->GetByteArrayRegion(arr, 0, n, reinterpret_cast<jbyte *>(buffer));
	env->DeleteLocalRef(arr);
	return n;
}

static int otg_write_at(int slot, uint64_t offset, const void *buffer, size_t size)
{
	JNIEnv *env = otg_env();
	if (!env || !g_otg_class || !g_otg_write || !buffer || size == 0 || size > 1024 * 1024)
		return -1;
	jbyteArray arr = env->NewByteArray((jsize) size);
	if (!arr)
		return -1;
	env->SetByteArrayRegion(arr, 0, (jsize) size, reinterpret_cast<const jbyte *>(buffer));
	jint n = env->CallStaticIntMethod(g_otg_class, g_otg_write, slot, (jlong) offset, arr);
	env->DeleteLocalRef(arr);
	if (env->ExceptionCheck())
	{
		env->ExceptionClear();
		return -1;
	}
	return n;
}

static int64_t otg_size(int slot)
{
	JNIEnv *env = otg_env();
	if (!env || !g_otg_class || !g_otg_size)
		return -1;
	jlong n = env->CallStaticLongMethod(g_otg_class, g_otg_size, slot);
	if (env->ExceptionCheck())
	{
		env->ExceptionClear();
		return -1;
	}
	return (int64_t) n;
}

static int otg_sector_size(int slot)
{
	JNIEnv *env = otg_env();
	if (!env || !g_otg_class || !g_otg_sector)
		return 512;
	jint n = env->CallStaticIntMethod(g_otg_class, g_otg_sector, slot);
	if (env->ExceptionCheck())
	{
		env->ExceptionClear();
		return 512;
	}
	return n;
}

static int otg_ready(int slot)
{
	JNIEnv *env = otg_env();
	if (!env || !g_otg_class || !g_otg_ready)
		return 0;
	jboolean ok = env->CallStaticBooleanMethod(g_otg_class, g_otg_ready, slot);
	if (env->ExceptionCheck())
	{
		env->ExceptionClear();
		return 0;
	}
	return ok ? 1 : 0;
}

extern "C" JNIEXPORT jlong JNICALL
Java_dev_shivampingale_vcport_NativeBridge_openVolume(
	JNIEnv *env, jobject, jstring path, jstring password, jint pim, jboolean backup,
	jobjectArray keyfiles, jboolean readOnly, jboolean protectHidden, jstring hiddenPassword, jint hiddenPim)
{
	if (!path)
		return (jlong) VC_ERR_ARGUMENT;

	std::string cPath, cPassword, cHidden;
	if (jni_copy_utf(env, path, cPath) != VC_OK ||
		jni_copy_utf(env, password, cPassword) != VC_OK ||
		jni_copy_utf(env, hiddenPassword, cHidden) != VC_OK)
		return (jlong) VC_ERR_ARGUMENT;
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

	std::vector<std::string> owned;
	std::vector<const char *> ptrs;
	if (jni_copy_string_array(env, keyfiles, owned, ptrs) != VC_OK)
		return (jlong) VC_ERR_ARGUMENT;
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

	std::string cPath;
	if (jni_copy_utf(env, path, cPath) != VC_OK)
	{
		char line[64];
		snprintf(line, sizeof(line), "!error!\t0\t%d", VC_ERR_ARGUMENT);
		jobjectArray result = env->NewObjectArray(1, stringClass, nullptr);
		env->SetObjectArrayElement(result, 0, env->NewStringUTF(line));
		return result;
	}
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
	std::string cName, cDest;
	if (jni_copy_utf(env, name, cName) != VC_OK || jni_copy_utf(env, dest, cDest) != VC_OK)
		return VC_ERR_ARGUMENT;
	return vc_export_file(reinterpret_cast<VcVolume *>(handle), cName.c_str(), cDest.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_shivampingale_vcport_NativeBridge_importFile(
	JNIEnv *env, jobject, jlong handle, jstring destDir, jstring src, jstring destName)
{
	if (!jni_live_handle(handle) || !src)
		return VC_ERR_ARGUMENT;
	std::string cDir, cSrc, cName;
	if (jni_copy_utf(env, destDir, cDir) != VC_OK ||
		jni_copy_utf(env, src, cSrc) != VC_OK ||
		jni_copy_utf(env, destName, cName) != VC_OK)
		return VC_ERR_ARGUMENT;
	return vc_import_file(reinterpret_cast<VcVolume *>(handle), cDir.c_str(), cSrc.c_str(),
		cName.empty() ? nullptr : cName.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_shivampingale_vcport_NativeBridge_deleteFile(
	JNIEnv *env, jobject, jlong handle, jstring path)
{
	if (!jni_live_handle(handle) || !path)
		return VC_ERR_ARGUMENT;
	std::string cPath;
	if (jni_copy_utf(env, path, cPath) != VC_OK)
		return VC_ERR_ARGUMENT;
	return vc_delete_file(reinterpret_cast<VcVolume *>(handle), cPath.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_shivampingale_vcport_NativeBridge_mkdir(
	JNIEnv *env, jobject, jlong handle, jstring parent, jstring name)
{
	if (!jni_live_handle(handle) || !name)
		return VC_ERR_ARGUMENT;
	std::string cParent, cName;
	if (jni_copy_utf(env, parent, cParent) != VC_OK || jni_copy_utf(env, name, cName) != VC_OK)
		return VC_ERR_ARGUMENT;
	return vc_mkdir(reinterpret_cast<VcVolume *>(handle), cParent.c_str(), cName.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_shivampingale_vcport_NativeBridge_rmdir(
	JNIEnv *env, jobject, jlong handle, jstring path)
{
	if (!jni_live_handle(handle) || !path)
		return VC_ERR_ARGUMENT;
	std::string cPath;
	if (jni_copy_utf(env, path, cPath) != VC_OK)
		return VC_ERR_ARGUMENT;
	return vc_rmdir(reinterpret_cast<VcVolume *>(handle), cPath.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_shivampingale_vcport_NativeBridge_renameFile(
	JNIEnv *env, jobject, jlong handle, jstring path, jstring newName)
{
	if (!jni_live_handle(handle) || !path || !newName)
		return VC_ERR_ARGUMENT;
	std::string cPath, cName;
	if (jni_copy_utf(env, path, cPath) != VC_OK || jni_copy_utf(env, newName, cName) != VC_OK)
		return VC_ERR_ARGUMENT;
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
	std::string cSrc, cDest, cPassword, cName;
	if (jni_copy_utf(env, src, cSrc) != VC_OK ||
		jni_copy_utf(env, dest, cDest) != VC_OK ||
		jni_copy_utf(env, password, cPassword) != VC_OK ||
		jni_copy_utf(env, originalName, cName) != VC_OK)
		return VC_ERR_ARGUMENT;
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
	std::string cSrc, cDir, cPassword;
	if (jni_copy_utf(env, src, cSrc) != VC_OK ||
		jni_copy_utf(env, destDir, cDir) != VC_OK ||
		jni_copy_utf(env, password, cPassword) != VC_OK)
		return nullptr;
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
	std::string cPath;
	if (jni_copy_utf(env, path, cPath) != VC_OK)
		return JNI_FALSE;
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
	jstring hiddenPassword, jint hiddenPim, jlong hiddenSizeBytes, jobjectArray hiddenKeyfiles,
	jstring filesystem)
{
	if (!path || sizeBytes <= 0)
		return VC_ERR_ARGUMENT;
	std::string cPath, cPassword, cCipher, cKdf, cHidden, cFs;
	if (jni_copy_utf(env, path, cPath) != VC_OK ||
		jni_copy_utf(env, password, cPassword) != VC_OK ||
		jni_copy_utf(env, cipher, cCipher) != VC_OK ||
		jni_copy_utf(env, kdf, cKdf) != VC_OK ||
		jni_copy_utf(env, hiddenPassword, cHidden) != VC_OK ||
		jni_copy_utf(env, filesystem, cFs) != VC_OK)
		return VC_ERR_ARGUMENT;
	VcCreateOptions options = {};
	options.path = cPath.c_str();
	options.password = cPassword.c_str();
	options.password_len = cPassword.size();
	options.pim = pim;
	options.size_bytes = (uint64_t) sizeBytes;
	options.cipher = cCipher.c_str();
	options.kdf = cKdf.c_str();
	options.filesystem = cFs.c_str();

	std::vector<std::string> owned;
	std::vector<const char *> ptrs;
	if (jni_copy_string_array(env, keyfiles, owned, ptrs) != VC_OK)
		return VC_ERR_ARGUMENT;
	if (!ptrs.empty())
	{
		options.keyfiles = ptrs.data();
		options.keyfile_count = ptrs.size();
	}

	std::vector<std::string> hiddenOwned;
	std::vector<const char *> hiddenPtrs;
	if (jni_copy_string_array(env, hiddenKeyfiles, hiddenOwned, hiddenPtrs) != VC_OK)
		return VC_ERR_ARGUMENT;
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
	std::string cPath, cPassword, cNewPassword, cKdf;
	if (jni_copy_utf(env, path, cPath) != VC_OK ||
		jni_copy_utf(env, password, cPassword) != VC_OK ||
		jni_copy_utf(env, newPassword, cNewPassword) != VC_OK ||
		jni_copy_utf(env, newKdf, cKdf) != VC_OK)
		return VC_ERR_ARGUMENT;
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
	if (jni_copy_string_array(env, keyfiles, owned, ptrs) != VC_OK)
		return VC_ERR_ARGUMENT;
	if (!ptrs.empty())
	{
		options.keyfiles = ptrs.data();
		options.keyfile_count = ptrs.size();
	}
	std::vector<std::string> newOwned;
	std::vector<const char *> newPtrs;
	if (jni_copy_string_array(env, newKeyfiles, newOwned, newPtrs) != VC_OK)
		return VC_ERR_ARGUMENT;
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
	std::string cVol, cBak, cPassword;
	if (jni_copy_utf(env, volumePath, cVol) != VC_OK ||
		jni_copy_utf(env, backupPath, cBak) != VC_OK ||
		jni_copy_utf(env, password, cPassword) != VC_OK)
		return VC_ERR_ARGUMENT;
	std::vector<std::string> owned;
	std::vector<const char *> ptrs;
	if (jni_copy_string_array(env, keyfiles, owned, ptrs) != VC_OK)
		return VC_ERR_ARGUMENT;
	int rc = vc_backup_headers(cVol.c_str(), cBak.c_str(), cPassword.c_str(), cPassword.size(), pim,
		ptrs.empty() ? nullptr : ptrs.data(), ptrs.size());
	jni_wipe_string(cPassword);
	return rc;
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_shivampingale_vcport_NativeBridge_restoreHeaders(
	JNIEnv *env, jobject, jstring volumePath, jstring backupPath, jstring password, jint pim, jobjectArray keyfiles)
{
	std::string cVol, cBak, cPassword;
	if (jni_copy_utf(env, volumePath, cVol) != VC_OK ||
		jni_copy_utf(env, backupPath, cBak) != VC_OK ||
		jni_copy_utf(env, password, cPassword) != VC_OK)
		return VC_ERR_ARGUMENT;
	std::vector<std::string> owned;
	std::vector<const char *> ptrs;
	if (jni_copy_string_array(env, keyfiles, owned, ptrs) != VC_OK)
		return VC_ERR_ARGUMENT;
	int rc = vc_restore_headers(cVol.c_str(), cBak.c_str(), cPassword.c_str(), cPassword.size(), pim,
		ptrs.empty() ? nullptr : ptrs.data(), ptrs.size());
	jni_wipe_string(cPassword);
	return rc;
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_shivampingale_vcport_NativeBridge_generateKeyfile(JNIEnv *env, jobject, jstring path, jint size)
{
	std::string cPath;
	if (jni_copy_utf(env, path, cPath) != VC_OK)
		return VC_ERR_ARGUMENT;
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
	std::string cPhase;
	if (jni_copy_utf(env, phase, cPhase) != VC_OK)
		cPhase.clear();
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

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *)
{
#ifdef __linux__
	prctl(PR_SET_DUMPABLE, 0);
#endif
	g_otg_vm = vm;
	JNIEnv *env = nullptr;
	if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_OK && env)
	{
		jclass local = env->FindClass("dev/shivampingale/vcport/OtgBlockStore");
		if (local)
		{
			g_otg_class = reinterpret_cast<jclass>(env->NewGlobalRef(local));
			env->DeleteLocalRef(local);
			g_otg_read = env->GetStaticMethodID(g_otg_class, "nativeRead", "(IJ[B)I");
			g_otg_write = env->GetStaticMethodID(g_otg_class, "nativeWrite", "(IJ[B)I");
			g_otg_size = env->GetStaticMethodID(g_otg_class, "nativeSize", "(I)J");
			g_otg_sector = env->GetStaticMethodID(g_otg_class, "nativeSectorSize", "(I)I");
			g_otg_ready = env->GetStaticMethodID(g_otg_class, "nativeReady", "(I)Z");
			VcOtgBackend backend = {};
			backend.read_at = otg_read_at;
			backend.write_at = otg_write_at;
			backend.size = otg_size;
			backend.sector_size = otg_sector_size;
			backend.ready = otg_ready;
			vc_otg_set_backend(&backend);
		}
	}
	/* Worker threads start from NativeBridge.startRuntime after loadLibrary. */
	return JNI_VERSION_1_6;
}
