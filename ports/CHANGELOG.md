# VC Port changelog

## Unreleased

- Official VeraCrypt git + latest-release URLs in `ports/version.json`. Check for updates is a ≤20s HTTPS window to those hosts plus GitHub status; no redirects; the app never listens and never fetches `src/` or installs itself.
- Progress overlay on long jobs. Copy/move, New folder / Rename / Delete / Properties / Wipe free space, Read-only and TrueCrypt Mode, restore from the embedded backup header.
- Aligned FAT copy/wipe/export skip an extra sector buffer. Overlay polls at 10 Hz only while a job runs.
- Host tests: wrap/volume object cache, `test_quality.py` taxonomy + property/fuzz, fail-closed VCF2 decode. See `ports/tests/TESTING.md`.
- Fastlane changelog for versionCode 5; F-Droid recipe clones public `Veracrypt_port`; SECURITY.md and CI path filters match a public tree.
- FAT list cap 1024; `vc_list_dir_from` + Load more; iOS wrap uses `arc4random_buf`; iOS native defines `TC_IOS`.

## 0.3.0

Pre-public hardening cycle, then public relaunch.

- Host tests open a known-password FAT volume, list folders, and export nested files
- In-app FAT folder navigation on Android and iOS; exFAT stays unsupported
- Desktop Help → Check for updates honors StayOffline; panic wipe reports dismount failures
- `version.json` carries `android_apk_sha256` / `source_sha256` (empty until a hashed artifact exists)
- GitHub Release APK attach stays off; CI APKs are debug-signed previews only
- Contact emails and SECURITY.md

Not unbreakable. F-Droid screenshots are still missing.

## 0.2.2

Fix CI/NDK package builds: drop Windows-only `blake2s-ref.c`, link Argon2 SSE/AVX2 on x86_64.

## 0.2.1

High-threat hardening. Not unbreakable.

- Always-on screenshot block, recents hidden, no cloud/D2D backup
- Panic wipe and background session lock
- No exported DocumentsProvider; FileProvider no longer exposes all of cache
- System TLS CAs only; StrongBox when present
- Wrap: Argon2id 32 MiB, mode 0600, mlock keys
- Compelled-biometrics warning; FOSS profile in THREAT-MODEL.md

## 0.2.0

First tagged GitHub release.

- F-Droid flavor with no `INTERNET` permission; GitHub flavor with tap-to-check updates only
- Unlock mix: text password, PIM, keyfiles, and a biometric/device secret mixed as a VeraCrypt keyfile
- Privacy Manifest, encryption export flag, and AltStore source stub on iOS
- Fastlane metadata and an fdroiddata recipe
- Portable wrap/unwrap host test for CI

APKs from GitHub Actions are **debug-signed previews**. F-Droid (or your own keystore) must sign production builds.

## 0.1.0

Initial VC Port work on `master` (untagged): Apple silicon FUSE-T, Touch ID, native admin auth, Android/iOS clients, wrap helper.
