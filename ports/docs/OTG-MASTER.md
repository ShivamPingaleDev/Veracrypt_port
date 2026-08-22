# Experimental: OTG Master–style USB disk (no auto-mount)

OTG + in-app preview **ship on `master`** (from 0.3.9). The old `experimental-otg-master` branch is **ignored** for freeze. Do not add commits there. This is not 1.0. It is not unbreakable.

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

**Allow Files app to browse** is off until the user ticks it. DocumentsProvider is a seizure leak versus “slots this session only.”

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

## Builds

```bash
ports/scripts/build-phones.sh
# Android: assembleFossRelease and assembleGithubRelease (both no biometrics)
```

OTG Kotlin: `OtgUsb.kt`, `OtgScsi.kt`, `OtgPartitions.kt`, `OtgBlockStore.kt`, `OtgVolumePanel.kt`, `VolumeDocumentsProvider.kt`.
