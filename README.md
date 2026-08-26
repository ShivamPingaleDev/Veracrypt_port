# VC Port

Open the same **locked files** on your **phone** that you use on a computer with VeraCrypt.

This app is **not** called VeraCrypt. It is **not unbreakable**. It is **100% free**.

[![VC Port](https://github.com/ShivamPingaleDev/Veracrypt_port/actions/workflows/vcport.yml/badge.svg?branch=master)](https://github.com/ShivamPingaleDev/Veracrypt_port/actions/workflows/vcport.yml)

**Support (optional)** — same features for everyone; support does not unlock extras or stronger encryption. Links are on GitHub only, not inside the app.

[![GitHub Sponsors](https://img.shields.io/badge/GitHub-Sponsor-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/ShivamPingaleDev)
[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-support-yellow?logo=buy-me-a-coffee&logoColor=black)](https://buymeacoffee.com/shivampingaledev)
[![Ko-fi](https://img.shields.io/badge/Ko--fi-support-ff5e5b?logo=ko-fi&logoColor=white)](https://ko-fi.com/shivampingaledev)
[![Donate on Liberapay](https://liberapay.com/assets/widgets/donate.svg)](https://liberapay.com/ShivamPingaleDev/donate)

[![Liberapay receives](https://img.shields.io/liberapay/receives/ShivamPingaleDev.svg?logo=liberapay)](https://liberapay.com/ShivamPingaleDev)
[![Liberapay gives](https://img.shields.io/liberapay/gives/ShivamPingaleDev.svg?logo=liberapay)](https://liberapay.com/ShivamPingaleDev)
[![Liberapay patrons](https://img.shields.io/liberapay/patrons/ShivamPingaleDev.svg?logo=liberapay)](https://liberapay.com/ShivamPingaleDev)
[![Liberapay goal](https://img.shields.io/liberapay/goal/ShivamPingaleDev.svg?logo=liberapay)](https://liberapay.com/ShivamPingaleDev)

More: [SUPPORT.md](SUPPORT.md)

## Download

**0.3.12** — **proof of concept** (stable alpha: still testing, not a store app, not production-ready) (On Feature Freez).

| Phone | What to get |
| --- | --- |
| **Android** | [Latest release](https://github.com/ShivamPingaleDev/Veracrypt_port/releases/latest) — one APK (debug-signed preview) |
| **iPhone** | Same page — unsigned IPA; you install with **your Apple ID** via AltStore or SideStore |

On iPhone: download IPA → open in AltStore/SideStore → sign in with your Apple ID → Trust in Settings → open VC Port.  
More steps: [ports/ios/README.md](ports/ios/README.md)

### Proof of concept (what that means here)

GitHub releases are **demos** that show VeraCrypt-compatible volumes can be opened, edited, and saved back on a phone. They are useful for feedback and cross-device tests, but:

- APKs are **debug-signed previews**; IPAs are **unsigned**
- Container save-back, SAF/Files URIs, OTG, and multi-mount are still being hardened
- Host Python tests and emulator walks do not replace testing on your own phone and desktop VeraCrypt

Do not treat a GitHub download as a finished security product. Sign your own production build when you trust the tree.

#### Feature Freez.

Currently Focusing on my academics so I am not going to add new features, Please improve upon this and please suggest new one for next iteration of app.

## What it does

A locked file is a single file (for example `.hc`). On a PC, VeraCrypt opens it as a drive. A phone cannot do that. VC Port unlocks it **inside the app**:

1. Pick the locked file.
2. Type the password (and keyfiles / PIM if you use them).
3. Browse folders on the **Mounted** tab and copy or move files.
4. **Dismount** or **Panic wipe** closes it and clears secrets on the phone. The locked file on disk is not deleted.

You can also **Create** a new locked file. Use the **same password** on a computer later.

- Works **offline** (no internet permission in this build).
- Android can scan a **USB disk** (experimental).
- A forced password still opens the volume — use a long password and keep keyfiles off the phone.

## Screenshots (0.3.12)

Emulator UI, empty tabs. Click a picture for full size. [How we capture these](ports/docs/screenshots/README.md).

### Volume

| Android | iPhone |
| --- | --- |
| [<img src="ports/docs/screenshots/thumbs/01-volume.png" alt="Android Volume 0.3.12" width="240" style="border-radius:10px;border:1px solid #3d444d">](ports/docs/screenshots/01-volume.png) | [<img src="ports/docs/screenshots/thumbs/ios-01-volume.png" alt="iPhone Volume 0.3.12" width="240" style="border-radius:10px;border:1px solid #3d444d">](ports/docs/screenshots/ios-01-volume.png) |

### Create

| Android | iPhone |
| --- | --- |
| [<img src="ports/docs/screenshots/thumbs/03-create.png" alt="Android Create 0.3.12" width="240" style="border-radius:10px;border:1px solid #3d444d">](ports/docs/screenshots/03-create.png) | [<img src="ports/docs/screenshots/thumbs/ios-03-create.png" alt="iPhone Create 0.3.12" width="240" style="border-radius:10px;border:1px solid #3d444d">](ports/docs/screenshots/ios-03-create.png) |

### Mounted

| Android | iPhone |
| --- | --- |
| [<img src="ports/docs/screenshots/thumbs/05-mounted.png" alt="Android Mounted 0.3.12" width="240" style="border-radius:10px;border:1px solid #3d444d">](ports/docs/screenshots/05-mounted.png) | [<img src="ports/docs/screenshots/thumbs/ios-05-mounted.png" alt="iPhone Mounted 0.3.12" width="240" style="border-radius:10px;border:1px solid #3d444d">](ports/docs/screenshots/ios-05-mounted.png) |

### Tools

| Android | iPhone |
| --- | --- |
| [<img src="ports/docs/screenshots/thumbs/04-tools.png" alt="Android Tools 0.3.12" width="240" style="border-radius:10px;border:1px solid #3d444d">](ports/docs/screenshots/04-tools.png) | [<img src="ports/docs/screenshots/thumbs/ios-04-tools.png" alt="iPhone Tools 0.3.12" width="240" style="border-radius:10px;border:1px solid #3d444d">](ports/docs/screenshots/ios-04-tools.png) |

### Dark mode (Android)

| Android |
| --- |
| [<img src="ports/docs/screenshots/thumbs/08-skin-signal.png" alt="Android dark mode 0.3.12" width="240" style="border-radius:10px;border:1px solid #3d444d">](ports/docs/screenshots/08-skin-signal.png) |

## License (short)

The volume engine is **VeraCrypt** source. That **inherits** **TrueCrypt License 3.0** plus Apache-2.0. TrueCrypt 3.0 is **not OSI / FSF / Debian-free**; public source is required when binaries ship. GitHub APKs are **debug-signed** previews — not production-signed store builds.

Details: [License.txt](License.txt), [ports/FOSS.md](ports/FOSS.md)

## More

| | |
| --- | --- |
| Build from source | [PORTING.md](PORTING.md) |
| Security issues | [SECURITY.md](SECURITY.md) |
| Public talking points | [ports/PUBLIC.md](ports/PUBLIC.md) |

**Contact:** [shivampingaledev@proton.me](mailto:shivampingaledev@proton.me)

This repo is named Veracrypt_port because it includes VeraCrypt source. The app on the phone is **VC Port**. Use official VeraCrypt on a PC or Mac — this project does not ship a desktop app.
