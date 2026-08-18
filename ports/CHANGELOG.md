# VC Port changelog

## Unreleased

- Looks skins except Signal are quieter. Desktop chrome is calmer. The app stays in Recents as a blank card (`FLAG_SECURE`); it is not hidden from the task switcher. Home dismounts an open volume but keeps the Create wizard (generated outer and nested passwords, nested checkbox, cipher/KDF/PIM, basket, size) so you can paste Copy once into Notes and continue. That keep-on-Home is intentional: wiping the wizard on minimize made Copy once useless. Dismount and Panic wipe still clear those secrets. Copy once stays on the clipboard for 30 seconds; Panic wipe still clears it. The basket “at least” size includes the nested volume, and tapping the nested label toggles it. Create can generate several keyfiles with any extension and add them. Volume shows password, PIM, keyfiles, and mount options (backup header, read-only, TrueCrypt Mode, hidden-volume protection) without a More-factors fold. Fingerprint / Face ID unlock and in-app Check for updates are not on master; they live on `experimental-biometrics`. Production Android is the `foss` flavor (public source, no trackers, no INTERNET); there is no F-Droid store target.
- Create size follows the file basket. KiB / MiB / GiB is a compact menu. Basket file size uses the Documents SIZE column, then the open file descriptor length, then `file://` length — not a 1 MiB guess when SIZE is missing. exFAT import/delete work inside a folder, not only the volume root. Switching Volume/Tools and back to Create does not wipe the randomness bar; it resets after a volume is created.
- Set header KDF / add or remove keyfiles keep the current PIM when New PIM is left at 0. Change password still treats 0 as VeraCrypt default.
- ARM64 AES uses the CPU crypto extension (NEON `vaeseq`) after `DetectArmFeatures()`. ARMv7 stays table AES with NEON. HMAC-SHA-512 is unchanged.
- Wrap tab removed; leftover `.vcpw` decrypt stays on Tools. Create leads with the file basket. Size is KiB / MiB / GiB (2 MiB–64 GiB). Nested volumes get password, PIM, keyfiles, and generate. Session SHA-256 of basket files; `BASKET.sha256` is written inside the volume.
- Create volumes as FAT or exFAT. exFAT if a file is over 4 GiB. USB/OTG opens a container file on the stick, not the whole disk.
- Unlock: text password is primary, plus optional keyfiles. Fingerprint / Face ID is on `experimental-biometrics`.
- File name is only a disguise; the extension is ignored.
- Apple users sign the unsigned IPA themselves (AltStore / Xcode). How to keep the repos public: `PUBLIC.md`. GitHub README has real emulator UI shots (`docs/screenshots/`); Fastlane `phoneScreenshots/` stays empty until a physical phone capture.
- Honest discovery only: GitHub topics + README snippet, no ad SDK, no analytics. See `PUBLIC.md`.
- Author footnote: still in a five-year IT engineering degree (graduate summer 2027). Quiet README ask for teaching, internship, or work.
- Looks APKs share `applicationId` with production: `assembleStyledRelease` (`VCPort-0.3.1-looks-preview.apk`) and `assembleLooksgithubRelease` (`VCPort-0.3.1-looks-github-preview.apk`). Both are offline on master (no INTERNET). Live Check for updates is on `experimental-biometrics`. Installing either Looks APK replaces the other Desktop/Looks APK. Production Android is the `foss` flavor, not an F-Droid listing.
- Wrap, PIM, keyfile, and container-name fixes: wrap copies then Save-as; Lock clears PIM; custom disguise names stay; any file can be a keyfile (first 1 MiB); container label is the Files name, not `/proc/self/fd`.
- Wrap keeps the password while Files is open (the picker used to look like leaving the app, which wiped the secret).
- Create / Choose container / keyfiles also keep the session while Files is open. The selected file is the one you picked or saved (name shown, not `/proc/self/fd` or a cache copy). Home dismounts an open volume; the Create form stays so you can paste Copy once into Notes and continue.
- Create volume Basket: pick several files, then Create volume copies them into the new container. Volume size grows to fit (max 64 GiB). Originals stay on the phone.
- Create/Open with phone unlock selected shows the system PIN / fingerprint / face prompt. Dismount wipes passwords, RAM keyfiles, and decrypted copies; remembered Keystore/Keychain factors stay until Panic wipe.
- Phone UI: same skins and honest copy, less essay. Volume / Create / Tools first; About and desktop leftovers live under Tools. Mounted folders keep Copy/Move on two rows.
- GitHub README: Hughes quote first, phones first, Mac extra, Looks last, footnote, then “Cypherpunks write code.”
- Host crypto-safety suite: AES-256 FIPS-197 known-answer, CTR partial blocks, `vc_secure_wipe`, wrap reject-before-Argon2, JNI/enclave mocks, ASan/UBSan. Optional libFuzzer harness `fuzz_wrap.cc`.
- Host Linux CI links wrap/volume tests without SHA-NI objects or PCSC; overlay inventories refreshed.

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
