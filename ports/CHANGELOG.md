# VC Port changelog

## Unreleased

- Apple users sign the unsigned IPA themselves (AltStore / Xcode). How to keep the repos public: `PUBLIC.md`. GitHub README has real emulator UI shots (`docs/screenshots/`); Fastlane `phoneScreenshots/` stays empty until a physical phone capture.

## 0.3.1

Phone release: hidden-volume write protection, full emulator NativeBridge + Compose UI tests, ARM crypto flags, and the XTS thread pool on open/create.

- Official VeraCrypt git + latest-release URLs in `ports/version.json`. Check for updates is a ≤20s HTTPS window to those hosts plus GitHub status; no redirects; the app never listens and never fetches `src/` or installs itself.
- Progress overlay on long jobs. Copy/move, New folder / Rename / Delete / Properties / Wipe free space, Read-only and TrueCrypt Mode, restore from the embedded backup header.
- Aligned FAT copy/wipe/export skip an extra sector buffer. Overlay polls at 10 Hz only while a job runs.
- Host tests: wrap/volume object cache, `test_quality.py` taxonomy + property/fuzz, fail-closed VCF2 decode. See `ports/tests/TESTING.md`.
- Fastlane changelog for versionCode 6; F-Droid recipe clones public `Veracrypt_port`; SECURITY.md and CI path filters match a public tree.
- FAT list cap 1024; `vc_list_dir_from` + Load more; iOS wrap uses `arc4random_buf`; iOS native defines `TC_IOS`.
- Nation-state APTs (Unit 8200, TAO, Lazarus, and the rest) are documented as out of scope: no key escrow, no intelligence backdoor, and no foolproof claim.
- Remember / biometric save stays off unless the user types REMEMBER. Password fields skip IME, Autofill, and iOS Keychain history. Desktop never writes History.xml; leftover history is overwritten then deleted.
- Host tests and pin scripts run in both Veracrypt_port (full tree) and the mobile-only VCPort repo.
- The mobile-only VCPort GitHub repo is public.
- About / README footnote: programming noob with a five-year IT engineering degree that did not work out; open to suggestions and advice.
- Generated passwords are 64 characters. Copy once works for wrap and new volumes. Finger-scribble entropy takes longer to fill.
- After Open volume, the FAT folder is shown in the app as Mounted in this app. Not a system drive.
- Work overlay names the live step and percent on a visible meter. Entropy scribble draws the finger path. Nothing runs out of sight.
- Dropped the unused Share tab and the duplicate USB/OTG picker button. Share encrypted stays on Volume and the bar in front of you.
- File-container VeraCrypt menu items are in the apps (create, open, dismount, password/KDF/keyfiles, header backup/restore, keyfile generator, benchmark, test vectors, properties, wipe cache, FAT folders, hidden-volume write protection). System encryption, devices, favorites, Quick Format, and the rest stay on a computer.
- About quotes Eric Hughes: we must defend our own privacy if we expect to have any.
- Android x86/x86_64 NDK slices link: software AES (no AES-NI asm), Argon2 AVX2 object on i686 so emulator ABIs package.
- iOS compiles on Xcode 26: listDir uses an Error wrapper; biometric keyfile byte count is not inlined in SwiftUI Text.
- Host lifecycle simulation: create a FAT volume with password, PIM, and a biometric keyfile, store files, dismount, reopen. Independent cases run on a CPU worker pool; HMAC-SHA-512 is unchanged. Phone-session + optional emulator NativeBridge test stay offline (no UpdateChecker).
- Android 64-bit openVolume handles: a live pointer may look negative as signed Long; JNI/UI use isOpen instead of handle > 0. Debug ARM cpu.c links with GNU89 inline so CPU_QueryAES/SHA2 exist at -O0.
- Emulator NativeBridge covers read-only, backup header, PIM 0, and hidden-volume write protection. Compose UI test walks Volume/Wrap/Create/Tools without tapping Panic wipe or Check for updates. ARM64 Debug NDK uses `-O2 -march=armv8-a+crypto`; armeabi-v7a uses NEON. `vc_runtime_start` warms the XTS thread pool after native load.
- Unsigned iOS IPA on GitHub Releases (`VCPort-0.3.1-unsigned-preview.ipa`). Re-sign with your Apple ID or sideload via AltStore. AltStore `downloadURL` stays empty.

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
