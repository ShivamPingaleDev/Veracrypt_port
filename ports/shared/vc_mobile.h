/*
 Copyright (c) 2026 Shivam Mangesh Pingale. All rights reserved.

 Governed by the Apache License 2.0 the full text of which is
 contained in the file License.txt included in VeraCrypt binary and source
 code distribution packages.
*/

#ifndef VC_MOBILE_H
#define VC_MOBILE_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct VcVolume VcVolume;

enum VcError
{
	VC_OK = 0,
	VC_ERR_IO = -1,
	VC_ERR_PASSWORD = -2,
	VC_ERR_FORMAT = -3,
	VC_ERR_ARGUMENT = -4,
	VC_ERR_MEMORY = -5,
	VC_ERR_UNSUPPORTED = -6
};

/* In-app FAT listing cap. Path lookup uses the full directory (up to 32768). */
enum { VC_LIST_UI_MAX = 1024 };

typedef struct VcOpenOptions
{
	const char *path;
	const char *password; /* empty allowed when keyfiles are used */
	size_t password_len;
	int pim;
	int use_backup_header;
	const char *const *keyfiles;
	size_t keyfile_count;
	int read_only;
	/* Protect a nested (hidden) volume while the outer is open. Same as
	 * desktop Mount Options → Protect hidden volume against damage. */
	int protect_hidden;
	const char *hidden_password;
	size_t hidden_password_len;
	int hidden_pim;
	const char *const *hidden_keyfiles;
	size_t hidden_keyfile_count;
} VcOpenOptions;

typedef struct VcCreateOptions
{
	const char *path;
	const char *password;
	size_t password_len;
	int pim;
	uint64_t size_bytes;
	const char *cipher; /* GUI name, e.g. AES(Twofish(Serpent)) */
	const char *kdf;    /* e.g. HMAC-SHA-512 */
	const char *const *keyfiles;
	size_t keyfile_count;
	/* 0 = normal volume only. >0 writes a VeraCrypt hidden volume inside. */
	uint64_t hidden_size_bytes;
	const char *hidden_password;
	size_t hidden_password_len;
	int hidden_pim;
	const char *const *hidden_keyfiles;
	size_t hidden_keyfile_count;
	/* "FAT", "exFAT", or empty: FAT below 4 GiB, exFAT at 4 GiB and above. */
	const char *filesystem;
} VcCreateOptions;

/* Start VeraCrypt EncryptionThreadPool (XTS + auto-detect KDF). Safe to call
 * more than once. HMAC-SHA-512 itself stays sequential per password. */
void vc_runtime_start (void);

VcVolume *vc_open (const VcOpenOptions *options, int *error);
int vc_create_volume (const VcCreateOptions *options);
void vc_entropy_reset (void);
void vc_entropy_add (const void *data, size_t size);
int vc_entropy_percent (void);
void vc_close (VcVolume *volume);
uint64_t vc_size (VcVolume *volume);
uint32_t vc_sector_size (VcVolume *volume);
int vc_read (VcVolume *volume, uint64_t offset, void *buffer, size_t size);
int vc_write (VcVolume *volume, uint64_t offset, const void *buffer, size_t size);

typedef struct VcDirEntry
{
	char name[256];
	uint8_t is_dir;
	uint64_t size;
	uint32_t first_cluster;
	uint16_t dos_date;
	uint16_t dos_time;
} VcDirEntry;

int vc_list_root (VcVolume *volume, VcDirEntry *entries, int max_entries);
int vc_list_dir (VcVolume *volume, const char *path, VcDirEntry *entries, int max_entries);
int vc_list_dir_from (VcVolume *volume, const char *path, VcDirEntry *entries, int max_entries, int skip);
int vc_read_file (VcVolume *volume, const char *path, void *buffer, size_t buffer_size, size_t *out_size);
int vc_export_file (VcVolume *volume, const char *path, const char *dest_path);
int vc_import_file (VcVolume *volume, const char *dest_dir, const char *src_path, const char *dest_name);
int vc_delete_file (VcVolume *volume, const char *path);
int vc_mkdir (VcVolume *volume, const char *parent_dir, const char *name);
int vc_rmdir (VcVolume *volume, const char *path);
int vc_rename (VcVolume *volume, const char *path, const char *new_name);
int vc_wipe_free_space (VcVolume *volume);

int vc_wrap_file (const char *src_path, const char *dest_path,
	const char *password, size_t password_len, const char *original_name);
int vc_unwrap_file (const char *src_path, const char *dest_dir,
	const char *password, size_t password_len, char *out_path, size_t out_path_size);
int vc_is_wrap (const char *path);

int vc_generate_password (char *out, size_t out_size, int length);
void vc_secure_wipe (void *p, size_t n);

/* percent -1 = indeterminate (KDF / unlock). 0–100 = determinate. */
void vc_progress_reset (void);
void vc_progress_set (int percent, const char *phase);
void vc_progress_tick (int percent, const char *phase); /* no-op if percent unchanged */
int vc_progress_percent (void);
void vc_progress_phase (char *out, size_t out_size);

typedef struct VcChangeHeaderOptions
{
	const char *path;
	const char *password;
	size_t password_len;
	int pim;
	const char *const *keyfiles;
	size_t keyfile_count;
	int use_backup_header;
	const char *new_password;
	size_t new_password_len;
	int new_pim;
	const char *new_kdf; /* empty = keep current */
	const char *const *new_keyfiles;
	size_t new_keyfile_count;
} VcChangeHeaderOptions;

int vc_change_header (const VcChangeHeaderOptions *options);
int vc_backup_headers (const char *volume_path, const char *backup_path,
	const char *password, size_t password_len, int pim,
	const char *const *keyfiles, size_t keyfile_count);
int vc_restore_headers (const char *volume_path, const char *backup_path,
	const char *password, size_t password_len, int pim,
	const char *const *keyfiles, size_t keyfile_count);
int vc_generate_keyfile (const char *path, size_t size);
int vc_volume_info (VcVolume *volume, char *out, size_t out_size);
int vc_protection_triggered (VcVolume *volume);
int vc_benchmark (char *out, size_t out_size);
int vc_test_vectors (void);

#ifdef __cplusplus
}
#endif

#endif
