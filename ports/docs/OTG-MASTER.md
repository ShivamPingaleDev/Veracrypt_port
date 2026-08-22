# Experimental: OTG Master–style USB disk (no auto-mount)

This branch is **`experimental-otg-master`**. It is not master. It is not 1.0. It is not unbreakable.

## Citation

Whole-disk USB + in-app unlock + optional Files-app browse follows the **idea** of:

- **App:** OTG Master
- **Author:** **moylali** (copyright line in their LICENSE: `Copyright (C) 2025 moylali`)
- **Repo:** https://github.com/moylali/OTGMaster
- **F-Droid:** https://f-droid.org/packages/app.fayaz.otgmaster/ (`app.fayaz.otgmaster`)
- **License of OTG Master:** GPL-2.0-or-later (required there by vendored `libexfat`)

VC Port did **not** copy that tree. OTG Master is GPL-2.0-or-later; this port stays Apache-2.0 + TrueCrypt License 3.0. USB BOT/SCSI, MBR/GPT probe, overlay `File` callbacks, and `VolumeDocumentsProvider` are new VC Port code. Unlock still uses the pinned VeraCrypt core in `src/` plus `ports/shared` FAT/exFAT.

## What this branch adds

| Flavor | Biometrics | USB whole disk | Auto-mount | Network |
| --- | --- | --- | --- | --- |
| Android **foss** | No | Yes, tap Scan → pick partition → Open | **No** | No |
| Android **github** | No | Same | **No** | No |
| iOS | No (`VCPortEnableBiometrics` stays false) | **No** (file on a stick via Files only) | **No** | No |

There is **no** `USB_DEVICE_ATTACHED` auto-open and **no** auto-unlock of plain FAT/exFAT sticks (OTG Master 0.3.9 added that; this branch does not). iOS never compiles whole-disk USB slots (`-DVC_PORT_OTG=OFF`, `vc_otg_stub.cpp`). Preview, copy, and in-app browse still run on iPhone.

Native Open uses `/vcport-otg-dev/N`, never `/proc/self/fd/`.

**Allow Files app to browse** is off until the user ticks it. DocumentsProvider is a seizure leak versus master.

## In-app file preview (this branch)

Mounted-tab **View in app** decrypts one file into app cache and shows it **inside VC Port**. It does not open VLC, Files, Gallery, or `ACTION_VIEW`.

| Kind | What actually plays |
| --- | --- |
| Images | JPEG / PNG / GIF / WebP / BMP / HEIC — platform decoder in-process |
| Text | UTF-8, first 256 KiB |
| PDF | Android `PdfRenderer` / iOS PDFKit, in this app |
| Audio / video | Android `MediaPlayer`/`VideoView` / iOS `AVPlayer`, in this app |
| Office / other | Not decoded. Hex of the first 256 bytes. Copy to device if you need another tool |

No LibreOffice, no VLCKit. Whole-disk USB Open is Android-only. iPhone keeps file-on-stick Open plus **View in app**.

Flags: Android `ENABLE_IN_APP_PREVIEW`, iOS `VCPortEnableInAppPreview`. Preview cache is `cache/preview` (Android) / tmp `preview/` (iOS), wiped on Close, Dismount, and Panic.

## Merge into master later

Keep this branch modular. A future master merge should be flag flips plus a review, not a rewrite:

1. Android `ENABLE_OTG_DISK=false` (foss + github). USB UI is `OtgVolumePanel.kt` behind that flag.
2. CMake `-DVC_PORT_OTG=OFF` (iOS already forces this) uses `vc_otg_stub.cpp` and skips `vc_otg_usb_test`.
3. Drop flavor `VolumeDocumentsProvider` from foss/github manifests if master must not export to Files.
4. In-app preview can merge on its own: `InAppPreview.kt` / `InAppPreview.swift`, `ENABLE_IN_APP_PREVIEW` / `VCPortEnableInAppPreview`.

OTG-only Kotlin: `OtgUsb.kt`, `OtgScsi.kt`, `OtgPartitions.kt`, `OtgBlockStore.kt`, `OtgVolumePanel.kt`, `VolumeDocumentsProvider.kt`.

## Builds

```bash
ports/scripts/build-phones.sh
# Android: assembleFossRelease and assembleGithubRelease (both no biometrics)
```

Do not merge this to `master` without a separate review. Master stays in-app browse only.
