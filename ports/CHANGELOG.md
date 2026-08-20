# VC Port changelog

Release notes by version. The git commit list from Veracrypt_port is in [HISTORY.md](HISTORY.md).

## Unreleased

- `experimental-biometrics` is **stale**. Fingerprint extra on this branch is the Android github flavor / iOS `VCPortEnableBiometrics`. Live Check for updates is not here.

- [HISTORY.md](HISTORY.md) copies the Veracrypt_port git commit list so the VCPort phone tree has the same history as the full repo.

## experimental-otg-master (not a release)

USB whole-disk Open without auto-mount; foss = no biometrics, github = fingerprint extra. Optional DocumentsProvider. In-app **View in app** preview (not VLC). Do not treat as 1.0.

## 0.3.8

Stable alpha: 2 MiB FAT volumes are FAT12, so files larger than one sector open in official desktop VeraCrypt.

- Phone Create (FAT) writes FAT12 when cluster count is under 4085, FAT16 otherwise. Open follows the same Microsoft rule, so a 32 KiB file is not truncated to 512 bytes on a computer.
- Desktop volumes with password, PIM, keyfile, AES-Twofish-Serpent, SHA-256, and a hidden volume open on Android and iOS. Phone-made FAT volumes open on desktop VeraCrypt 1.26.29.
- Sprint 10/11 gates: `ports/scripts/cross-phone-open.sh`, `ports/scripts/desktop-phone-open.sh`.
- GitHub Release ships **one** debug-signed FOSS APK (`VCPort-0.3.8.apk`) and an unsigned iOS preview IPA (`VCPort-0.3.8-unsigned-preview.ipa`). Same `applicationId` as 0.3.7; it replaces the old install. Still alpha (not 1.0, not a store build).

## 0.3.7

Stable alpha: shorter in-app About; several-file copy; Open after Home without `/proc/self/fd`.

- About keeps both Eric Hughes quotes, TrueCrypt attribution, GitHub URL, name, and email. Author footnote stays in the README, not the app.
- Copy from device / Move from device can pick several files. Copy to device / Move to device send every selected file (Android: a folder in Files when more than one is selected).
- Native open no longer uses `/proc/self/fd`. Payload import copies into cache first.
- GitHub Release ships **one** debug-signed FOSS APK (`VCPort-0.3.7.apk`) and an unsigned iOS preview IPA (`VCPort-0.3.7-unsigned-preview.ipa`). Same `applicationId` as 0.3.6; it replaces the old install. Still alpha (not 1.0, not a store build).

## 0.3.6

Stable alpha: Open a saved container after Home or closing the app.

- After Home or closing the app, Open copies the chosen container into app cache when the Files descriptor is gone, instead of failing with “Could not read the container file.”
- GitHub Release ships **one** debug-signed FOSS APK (`VCPort-0.3.6.apk`) and an unsigned iOS preview IPA (`VCPort-0.3.6-unsigned-preview.ipa`). Same `applicationId` as 0.3.5; it replaces the old install. Still alpha (not 1.0, not a store build).

## 0.3.5

Phone release: wrap UI gone, Volume secrets wipe after Open, quieter Mounted tab.

- Drop wrap (`.vcpw`) from the Android and iOS apps. Copy files in and out of a volume instead.
- Tools no longer copies Volume PIM into New PIM. Home, save, and Dismount wipe Tools PIM with the other secrets.
- Mounted tab: one slim action row and a Folder menu instead of a stack of full-width buttons. Slot and file lists are quieter.
- After a successful Open, the Volume tab clears password, PIM, and mount-option checkboxes. Tools still uses the last unlock in RAM until Dismount.
- CI builds Android APKs and the unsigned iOS IPA in parallel (`.github/workflows/vcport.yml`). Local: `ports/scripts/build-phones.sh`. Sign the IPA with your Apple Team ID: `VC_PORT_IOS_TEAM=YOUR10CHARID ports/ios/sideload-sign.sh`.
- GitHub Release ships **one** debug-signed FOSS APK (`VCPort-0.3.5.apk`) and an unsigned iOS preview IPA (`VCPort-0.3.5-unsigned-preview.ipa`). Same `applicationId` as 0.3.4; it replaces the old install.

## 0.3.4

Phone release: session-test fixes on Android and iOS, full app-interface tests.

- Copy/move success is no longer overwritten by “Reading folder…”.
- Dismount clears backup-header / read-only / TrueCrypt / hidden-protect so the next open is not a wrong-password mix.
- Add/Remove keyfiles unlocks with the last successful keyfile mix, then writes the new list.
- App-interface session tests on the Android emulator and iPad Simulator: basket + nested volume, save wipes secrets, reopen, multi-mount copy/move, header backup/restore, KDF, keyfiles. Tests do not tap Panic wipe or Check for updates.
- Removed `archive/desktop/`. This repo is phone apps plus official VeraCrypt `src/`. On a computer, use official VeraCrypt.
- iPad Simulator run (`ports/ios/run_ipad_sim.sh`) and Xcode development sideload under your Apple ID (`ports/ios/sideload-sign.sh`).
- GitHub Release ships **one** debug-signed FOSS APK (`VCPort-0.3.4.apk`) and an unsigned iOS preview IPA (`VCPort-0.3.4-unsigned-preview.ipa`). Same `applicationId` as 0.3.3; it replaces the old install.

## 0.3.3

Phone release: save/open fix, wipe Create secrets after save, Mounted tab.

- After a volume is created and saved, password, PIM, and keyfiles are wiped. Open volume needs the password typed again.
- Saving a new volume no longer recopies the Files URI over the cache copy (that overwrite made Open fail with "Could not read the container file"). Picker copies go to unique names under cache `containers/`.
- Dedicated Mounted tab with a desktop-style slot column (8 slots). Volume/Create/Tools stay available while volumes are open. Select several files and Copy to volume / Move to volume across mounted containers.
- README explains how the phone apps work in plain steps and shows the current Volume, Create, Mounted, and Tools screens.
- GitHub Release ships **one** debug-signed FOSS APK (`VCPort-0.3.3.apk`). Same `applicationId` as 0.3.2; it replaces the old install.

## 0.3.2

Phone release: official VeraCrypt `src/` with a thin overlay, one FOSS APK, Original and Dark mode.

- `src/` matches official VeraCrypt at the pin. Phone hunks use the same relative paths under `ports/overlay/src/` (`File.cpp`, `Token.cpp`, token headers). Official `Keyfile.cpp` is unchanged.
- This repo is phones plus original VeraCrypt `src/`. Mac/Linux GUI extras from this fork are frozen under `archive/desktop/` and are not built.
- GitHub Release ships **one** debug-signed FOSS APK (`VCPort-0.3.2.apk`). The Looks APKs (`styled` / `looksgithub`) are gone. The `github` flavor still builds and is also offline; it is not attached to the release.
- Appearance is Original (VeraCrypt-like) and Dark mode. Cyberpunk, Matrix, and MAGI live under `archive/looks/`.
- Several volumes can stay mounted in one session (up to 8). Copy to volume / Move to volume sends a file into the folder last opened on another mounted container. Toolbar Dismount still closes every volume and wipes secrets.
- Fingerprint / Face ID unlock and in-app Check for updates are not on master; they lived on `experimental-biometrics` (**now stale**). Production Android is the `foss` flavor (public source, no trackers, no INTERNET); there is no F-Droid store target.
- The app stays in Recents as a blank card (`FLAG_SECURE`). Home dismounts an open volume but keeps the Create wizard so Copy once still works.
- ARM64 AES uses the CPU crypto extension after `DetectArmFeatures()`. ARMv7 stays table AES with NEON. HMAC-SHA-512 is unchanged.

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
