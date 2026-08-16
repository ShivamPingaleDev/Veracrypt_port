# VC Port 0.3.x improvement plan

Two days. Unused Cursor usage does not roll over. Day 1 is this tree; Day 2 waits for hardware or a layout choice.

Do **not**: fake F-Droid screenshots, claim an F-Droid listing, commit `experimental/`, attach debug CI APKs to GitHub Releases, fill `android_apk_sha256` from those APKs, rename the app VeraCrypt, add Play Integrity / obfuscation / an open-time hidden-volume checkbox, or force FUSE-T `backend=smb`.

## Day 1 (this change)

1. Fastlane changelog `5.txt` for versionCode 5
2. Store/README copy: FAT **folders**, not “root only”
3. `SECURITY.md` for a **public** tree (TrueCrypt still forbids silent private binaries)
4. CI path filters include `src/Main/**`, `src/Driver/**`, `SECURITY.md`
5. Contract tests for changelog + public SECURITY wording
6. Android/iOS error strings for `-4` and `-5`
7. Raise in-app FAT list cap; stop silent truncate; `fat_find_path` no longer stops at 128
8. GitHub/iOS update status shows HTTPS URL and SHA-256 when the manifest has them
9. Drop the iOS File Provider claim (no such extension exists)
10. Phase-10 tag check follows `ports/version.json` instead of a hardcoded `v0.3.0`

## Day 2 (started locally; push only when asked)

1. Done: F-Droid recipe clones `Veracrypt_port` with `subdir: ports/android` (same-tree `src/`, no srclib)
2. Still needs a real phone: Fastlane `phoneScreenshots/`
3. Done: `ports/scripts/hash_release.py` (refuses debug CI APKs; `--write` updates `version.json`)
4. Done: `vc_list_dir_from` + Load more on Android/iOS
5. Device smoke: open/list/export + biometric on a phone; FUSE-T mount on a Mac with wxWidgets
6. Done locally: CI `ios-native` job builds the iPhone simulator `libvc_mobile.a` (no IPA, no signing)

Do **not** burn leftover Cursor/Other-model quota on random [official VeraCrypt](https://github.com/veracrypt/VeraCrypt) issues. Drive-by AI patches to a volume-encryption project will be rejected. Only send upstream a patch you already needed in this tree (for example the macOS `File.cpp` `sys/disk.h` host-build fix).

## Honest leftovers that stay leftovers until hardware exists

- F-Droid inclusion MR
- AltStore `downloadURL` / IPA size
- Production signing key (pick one before a real APK)
- macOS GUI build in this clone (no wxWidgets here)
