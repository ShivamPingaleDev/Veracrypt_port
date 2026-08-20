# How VC Port is tested

Host tests stand in for a phone or an iOS simulator.

```
ports/tests/run-all.sh
python3 ports/tests/test_quality.py
```

## The map

| Kind | Question it answers | What actually runs |
| --- | --- | --- |
| **Unit** | Does one function do the right thing? | `test_factors.py` (VCF2), `test_wipe.py`, tag/version tables in `test_quality.py`, password generator in `test_wrap_main.cpp`, AES-256 FIPS-197 + wipe in `run_crypto_safety_test.sh` |
| **Module** | Does one C/Kotlin/Swift unit keep its contract? | `run_wrap_test.sh`, `run_crypto_safety_test.sh` (ASan/UBSan), `run_volume_test.sh`, `run_lifecycle_test.sh`, SourcePin / UpdateChecker |
| **White-box** | Do we look at the code paths? | Wrap wrong password / tamper MAC, FOSS `check()` throws |
| **Black-box** | Does it behave from the outside? | Wrap in → unwrap out; create → open → list → export; create → store → close → reopen |
| **Integration** | Do two layers talk? | JNI/C API; Kotlin/Swift VCF2; version.json → Info.plist / Android BuildConfig |
| **Functional** | Can a user finish a job? | Copy, wipe, panic, keyfiles, progress overlay — no open-time hidden checkbox. Lifecycle: password + PIM + biometric keyfile, Remember VCF2, files survive dismount, hidden-volume write protection |
| **System** | Whole tree on a laptop | `run-phases.sh` |
| **Smoke** | Does the pin parse? | `check_veracrypt_release.py --pin-only` |
| **Regression** | Frozen pin / FOSS rule | Honesty freeze; app is still VC Port |
| **Contract** | Clients stay in lockstep | `test_contracts.py` |
| **Security / tamper** | Ciphertext and leftovers | Wrap HMAC; FLAG_SECURE; no INTERNET |
| **Negative / boundary** | Bad input | Generator length 8/65; import FAT 4 GiB-1; keyfile 1 MiB |
| **Compatibility** | Same volume on a computer | AES(Twofish(Serpent)) / HMAC-SHA-512; FAT or exFAT |
| **Recovery** | Header tools | Backup/restore, corrupt primary then restore from `.bak` and embedded backup in the volume fixture |
| **Acceptance** | Ship checklist | Phase 10 public repo + version tag |
| **Static** | Read source without executing crypto | File greps |
| **Exploratory / device** | Human on a phone | Listed below — not faked |

## Deterministic extras (`test_quality.py`)

No Hypothesis, no wall-clock, no network. Seeded RNG and tables only.

| Technique | What it does |
| --- | --- |
| **Table-driven** | Known tag/version rows |
| **Property** | Version compare is reflexive, antisymmetric, transitive |
| **Metamorphic** | Prefix strip is identity on a numeric version |
| **Differential** | Python / Kotlin / Swift share tag prefixes |
| **Bounded fuzz** | Random tags never throw; wrap garbage fails before a pile of Argon2 |
| **libFuzzer / AFL++** | `ports/shared/fuzz_wrap.cc` `LLVMFuzzerTestOneInput`; malformed `.vcpw` bytes. See `Makefile.crypto-safety` |

A corrupted biometric vault must decode to empty factors, not crash.

## What still needs a real device

- Opening a real VeraCrypt volume on Android/iOS
- Biometric prompt (StrongBox / Face ID / Touch ID)
- Share sheet and USB/OTG roundtrip
- FLAG_SECURE screenshot (`adb screencap` is black by design). GitHub README
  shots are Compose `captureToImage` of the real UI with FLAG_SECURE still on.

Do not add Play Integrity, obfuscation, or an open-time hidden-volume checkbox
to “make tests pass.” Those fail the threat model.

Wrap and volume tests are both white-box (return codes) and black-box (files on
disk). Wrong password must not yield plaintext; a flipped byte in `.vcpw` is
rejected.

The lifecycle harness runs independent volumes on a CPU worker pool sized to
half of `hardware_concurrency()`, so VeraCrypt's EncryptionThreadPool still has
cores for XTS import/export. HMAC-SHA-512 PBKDF2 is sequential per password, so
a GPU cannot shorten one unlock and would put key material in VRAM. The KDF
itself is unchanged.

A "phone session" in the same binary walks every NativeBridge call: entropy,
create, wrap/unwrap, open, store, dismount, reopen, header backup/restore,
change password, read-only, backup header, hidden-volume write protection.
Android emulator / device: `ports/android/run_device_sim.sh` (starts AVD
`vcport-api35` when adb is empty; skips if there is no SDK/AVD). iPad Simulator:
`ports/ios/run_ipad_sim.sh` (skips if CoreSimulator has no iPad runtime). Device
sideload under your Apple ID: `ports/ios/sideload-sign.sh` (needs a 10-character
Team ID: `VC_PORT_IOS_TEAM` or `./sideload-sign.sh YOUR10CHARID`). CI builds the
Android APKs and the unsigned iOS IPA in parallel; local equivalent is
`ports/scripts/build-phones.sh`.
That Android test never calls `UpdateChecker.check()`. `DeviceSimulationTest` is a person-session
on NativeBridge: wrap/unwrap (wrong password and a flipped byte fail), create,
open, FAT mkdir/import/list/export/copy-to-folder/rename/delete, wipe free
space, dismount/reopen, read-only, backup header, change password, PIM 0
rejected, hidden-volume write protection, benchmark. A second method
(`phoneSessionFlows`) creates several volumes (FAT + exFAT), packs a file
basket with `BASKET.sha256`, corrupts the primary header and restores from
an external `.bak` and from the embedded backup, uses a 64-byte phone-unlock
keyfile, changes the header KDF, adds then removes all keyfiles, changes
the password, and checks Copy once. `securityMeasureCombos` creates volumes
disguised as `.jpg` / `.mp4` / `.zip` / `.safetensors` / `.lora` / `.hc`,
stores random files with those extensions, and checks hashes after backup
header restore, KDF change, keyfile add/remove, password change, and
biometric+keyfile unlock. Compose UI coverage is
`MainActivityUiTest` (FLAG_SECURE, tabs Volume/Create/Tools/Mounted, Panic wipe
visible, Stay offline, Generate strong password,
Copy once then Home then resume so the Create form continues;
does not tap Panic wipe or Check for updates; writes GitHub shots under
app files for `run_device_sim.sh` to pull into `ports/docs/screenshots/`).
`SlowHumanSessionTest` scribbles the entropy pad, generates, Copy once,
Homes for several seconds, pastes into a notes file, resumes, keeps the
Create password, adds basket files, and asserts the Size field grows.
A second method (`nestedCreateMinimizeKeepsWizard`) enables a nested
volume, generates outer and nested passwords, Homes twice, and checks
that the nested checkbox, both passwords, PIM, KDF, filename, and basket
size are still there. Home keeping the Create wizard is intentional;
Dismount / Panic wipe still clear it. `AppInterfaceSessionTest` finishes
Create through the UI (save wipes secrets), leaves and reopens, decrypts,
mounts two volumes, copies/moves files, then uses read-only / backup
header / hidden-volume protection. After a successful Open the Volume password
and PIM fields are empty; Tools header ops still use the last unlock in RAM.
Tests must not tap Panic wipe.
One local command for the visible UI walk on both phones:
`ports/scripts/run-ui-walk.sh` (results and USB limits: `ports/tests/UI-WALK.md`).
iPad Simulator has the same session as `ports/ios/VCPortTests/AppInterfaceSessionTests.swift`
(`ports/ios/run_ios_session_test.sh`; skips Files/share sheets like Android
skips SAF). Sprint 10 (create a random volume on one phone, open it on the
other): `ports/scripts/cross-phone-open.sh`. Sprint 11 (official desktop VeraCrypt
volumes with password / PIM / keyfile / cascade / hash open on the phones, and
phone FAT volumes open on desktop; 2 MiB containers are FAT12):
`ports/scripts/desktop-phone-open.sh`. Appearance is Original plus Dark mode on the foss APK
(`connectedFossDebugAndroidTest`). Cyberpunk / Matrix / MAGI are archived
under `archive/looks/` and are not built. Tests must not tap Panic wipe.

ARM64 slices compile Aes_hw_armv8 / sha256_armv8 with `-O3 -march=armv8-a+crypto`.
`vc_runtime_start()` calls `DetectArmFeatures()` (getauxval HWCAP_AES on Android, always-on on Apple arm64) before any volume work so XTS uses the AES crypto extension instead of table AES. Debug NDK builds still use `-O2` on that slice so AES/SHA detection and
Twofish/Serpent/SHA-512 are not stuck at `-O0`. armeabi-v7a has no AES crypto-extension; table AES is built `-O3 -mfpu=neon`.
`vc_runtime_start()` also warms VeraCrypt's EncryptionThreadPool at JNI load (and
on iOS before open/create) so XTS uses every core. HMAC-SHA-512 PBKDF2 stays
sequential per password.

