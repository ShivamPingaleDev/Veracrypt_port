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
| Android **github** | Yes (fingerprint / face extra) | Same | **No** | No |
| iOS | Off unless `VCPortEnableBiometrics` is true | File on a stick via Files only | **No** | No |

There is **no** `USB_DEVICE_ATTACHED` auto-open and **no** auto-unlock of plain FAT/exFAT sticks (OTG Master 0.3.9 added that; this branch does not).

Native Open uses `/vcport-otg-dev/N`, never `/proc/self/fd/`.

**Allow Files app to browse** is off until the user ticks it. DocumentsProvider is a seizure leak versus master.

## Builds

```bash
ports/scripts/build-phones.sh
# Android: assembleFossRelease (no bio) and assembleGithubRelease (bio)
```

Do not merge this to `master` without a separate review. Master stays in-app browse only.
