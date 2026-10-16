# Code map

Where a change goes in VC Port 0.3.15. The volume bytes are VeraCrypt. The phone code around them is in `ports/`.

Official `src/` is upstream. Do not edit it for a phone fix. A replacement goes in `ports/overlay/src/`, then `scripts/refresh-overlay.sh`. See [UPSTREAM.md](../UPSTREAM.md).

## Layers

| Layer | Path | What it owns |
| --- | --- | --- |
| Android screens | `ports/android/.../vcport/` | One activity, four panes, SAF copy, USB |
| iOS screens | `ports/ios/VCPort/` | One `ContentView`, the same four panes, Files bookmarks |
| JNI | `ports/shared/android_jni.cpp` | `NativeBridge` methods. Copies Java strings, then calls C |
| C API | `ports/shared/vc_mobile.h` | The only native surface both phones use |
| Engine glue | `ports/shared/vc_mobile.cpp` | Open, create, FAT, worker pool. Calls VeraCrypt |
| exFAT | `ports/shared/vc_exfat.cpp` | Volumes at 4 GiB and above, and any volume probed as exFAT |
| Android USB | `ports/shared/vc_otg_dev.cpp` plus `OtgScsi.kt` | Bulk-only SCSI. Nothing mounts on plug-in |
| Upstream crypto | `src/` | AES-XTS, headers, Argon2, PIM. Leave the parameters alone |

`ports/pure-c/` and `ports/pure-rust/` are labs on their own branches. They are not in the phone APK or IPA.

## One session

1. The person picks a container. Android copies the SAF URI into app cache (`ensureContainerPath`). iOS copies a security-scoped file the same way. Native open receives that cache path.
2. `vc_runtime_prepare` probes AES and SHA once. It does not start workers. `vc_open`, create, header tools, and benchmark start the pool and hold it with `WorkerUse`.
3. Phone builds cap the pool at two workers. The pool stops when no volume is open and no call is in progress. The next open starts it again.
4. A change inside the volume (import, delete, mkdir, rename, wipe, transfer) updates the cache file. `vc_flush_volume` writes filesystem metadata while the handle is still open. The UI then copies the cache file back to the URI the person picked.
5. Dismount, Home, idle, and screen lock save, then close. Panic calls close and does not copy back.
6. Passwords, PIM, and keyfile bytes live in the session. FOSS builds do not store them behind a fingerprint. `BiometricVault` and `BiometricStore` are the old opt-in path. `ENABLE_BIOMETRIC` is false.

FAT cannot hold one file over 4 GiB. That import returns `VC_ERR_MEMORY`. exFAT is the large-file filesystem. The volume file itself stays a VeraCrypt container a desktop can open.

Hidden volume is whichever password unlocks. There is no open-time checkbox that would admit a hidden volume exists.

## Error codes

`VcError` in `vc_mobile.h`. `0` finished. `-1` IO, stay. `-2` password, stay and keep the field. `-3` format, stay unmounted. `-4` argument, nothing written. `-5` memory or a FAT file past 4 GiB, stop. `-6` cipher or filesystem refused. `-7` self-test failed, open and create refuse.

## Android files

| File | Job |
| --- | --- |
| `MainActivity.kt` | Session state, pickers, save-back, panic, transfer worker |
| `OpenVolumeForm.kt` | The one Open form. Volume tab and Mounted Empty both use it |
| `VaultPane.kt` | Mounted file list, selection, copy and move actions |
| `CreateVolumeForm.kt` | New container, nested size, basket |
| `ToolsPane.kt` | Header change, backup, restore, keyfile, benchmark, PIM helper |
| `NativeBridge.kt` | `System.loadLibrary("vcport")` and the JNI method list |
| `UnlockFactors.kt` | Password, PIM, and keyfile bytes for one attempt. Wipe after use |
| `FactorFields.kt` | Shared password and PIM fields |
| `TransferQueue.kt` | One in-memory batch. One worker. Cancel at the next file |
| `MountLeave.kt` / `MountReport.kt` | Sentences for leave and for a failed save |
| `SessionIdle.kt` | Typed idle. 0 is off. Home and lock still close |
| `InAppPreview.kt` | View a decrypted file inside the app. Wipe the temp |
| `ShareHelper.kt` | Share a decrypted copy, or the locked file as-is |
| `SensitiveClipboard.kt` | Clipboard clear after 30 seconds |
| `OtgUsb.kt` / `OtgScsi.kt` / `OtgBlockStore.kt` / `OtgVolumePanel.kt` | USB disk the person chose. No auto-mount |
| `VolumeDocumentsProvider.kt` | Optional Files browse. Off until the person ticks it. A seizure leak |
| `PanicTileService.kt` / `PanicIntents.kt` | Quick Settings tile. Same wipe as Tools |
| `BiometricVault.kt` | Stored factors for one path. Not built into FOSS |
| `Hardening.kt` / `SourcePin.kt` / `UpdateChecker.kt` | Offline FOSS build. No Play Integrity |
| `VcPortTheme.kt` | Two skins. `WorkOverlay` is the only progress UI |
| `PimEstimator.kt` / `PasswordEntropy.kt` | Helpers. Not a crack-time claim and not Benchmark |

## iOS files

| File | Job |
| --- | --- |
| `ContentView.swift` | Session state. Same save, panic, and slot rules as Android |
| `ContentView+Open.swift` | Open form |
| `ContentView+Mounted.swift` | Mounted list and transfers |
| `ContentView+Create.swift` | Create and basket |
| `ContentView+Tools.swift` | Header tools |
| `VcMobileBridge.swift` | Swift to `vc_mobile.h`. `startCpu` is `vc_runtime_prepare` |
| `UnlockFactors.swift` | One attempt’s factors |
| `TransferQueue.swift` | Same queue rules as Android |
| `AppStorageSpace.swift` | Names a cache shortage |
| `InAppPreview.swift` | In-app preview. Wipe the temp |
| `ShareSheet.swift` | System share. Locked file goes as-is |
| `SensitivePaste.swift` | One-shot pasteboard |
| `SessionIdle.swift` | Same idle rule as Android |
| `BiometricStore.swift` | Keychain factors. Face ID extra is off |
| `FossConfig.swift` | Biometrics and whole-disk USB stay off |
| `VcPortTesting.swift` | In-process tests skip the system pickers |

iPhone has no whole-disk USB. The person picks a file on the stick in Files.

## Shared C

`vc_mobile.h` is the contract. A new phone feature that needs native behavior adds a function there, a body in `vc_mobile.cpp` or `vc_exfat.cpp`, a JNI method, and a Swift wrapper. Both phones call it. Do not grow a fifth close path: idle, lock, panic, and Tools wipe already meet in `closeOpenVolumes`. Home uses `dismountOnLeave`, which saves first.

`vc_flush_volume` does not copy the container back to the picked URI, and it does not close. `vc_close` flushes metadata, drops the handle, then lets idle workers stop. The UI copies the cache file back before close, except Panic.

Import reads a normal filesystem path in cache. After `importFile`, Android wipes that temp with `KeyfileIo.wipe` and iOS with `wipeFile`.

## Comments

A comment states a rule the next edit can break: when the pool runs, when the cache is copied back, which close skips write-back, which build has biometrics. It does not restate the next line. Host tests search some of those sentences. Reword one only when the test that names it is updated in the same change.

## Out of this tree

LUKS, cloud disks, Expand Volume, and a fingerprint that replaces the password are not in the shipping code. Reasons are in [FEATURE-BACKLOG.md](FEATURE-BACKLOG.md), [INSPIRATION.md](INSPIRATION.md), and [THREAT-MODEL.md](../THREAT-MODEL.md).
