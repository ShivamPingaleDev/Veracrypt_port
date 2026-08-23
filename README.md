# VC Port

Open the same **locked files** on your **phone** that you use on a computer with VeraCrypt.

This app is **not** called VeraCrypt. It is **not unbreakable**. It is **100% free**.

[![VC Port](https://github.com/ShivamPingaleDev/Veracrypt_port/actions/workflows/vcport.yml/badge.svg?branch=master)](https://github.com/ShivamPingaleDev/Veracrypt_port/actions/workflows/vcport.yml)

## Download

**0.3.12** — stable alpha (still testing, not a store app).

| Phone | What to get |
| --- | --- |
| **Android** | [Latest release](https://github.com/ShivamPingaleDev/Veracrypt_port/releases/latest) — one APK (debug-signed preview) |
| **iPhone** | Same page — unsigned IPA; you install with **your Apple ID** via AltStore or SideStore |

On iPhone: download IPA → open in AltStore/SideStore → sign in with your Apple ID → Trust in Settings → open VC Port.  
More steps: [ports/ios/README.md](ports/ios/README.md)

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

## Screenshots

<details>
<summary><strong>App UI</strong> (tap to expand — emulator shots, empty tabs)</summary>

Click a picture for full size. [All shots + how we capture them](ports/docs/screenshots/README.md).

| Tab | Android | iPhone |
| --- | --- | --- |
| **Volume** | [<img src="ports/docs/screenshots/thumbs/01-volume.png" alt="Android Volume" width="200">](ports/docs/screenshots/01-volume.png) | [<img src="ports/docs/screenshots/thumbs/ios-01-volume.png" alt="iPhone Volume" width="200">](ports/docs/screenshots/ios-01-volume.png) |
| **Create** | [<img src="ports/docs/screenshots/thumbs/03-create.png" alt="Android Create" width="200">](ports/docs/screenshots/03-create.png) | [<img src="ports/docs/screenshots/thumbs/ios-03-create.png" alt="iPhone Create" width="200">](ports/docs/screenshots/ios-03-create.png) |
| **Mounted** | [<img src="ports/docs/screenshots/thumbs/05-mounted.png" alt="Android Mounted" width="200">](ports/docs/screenshots/05-mounted.png) | [<img src="ports/docs/screenshots/thumbs/ios-05-mounted.png" alt="iPhone Mounted" width="200">](ports/docs/screenshots/ios-05-mounted.png) |
| **Tools** | [<img src="ports/docs/screenshots/thumbs/04-tools.png" alt="Android Tools" width="200">](ports/docs/screenshots/04-tools.png) | [<img src="ports/docs/screenshots/thumbs/ios-04-tools.png" alt="iPhone Tools" width="200">](ports/docs/screenshots/ios-04-tools.png) |
| **Dark mode** | [<img src="ports/docs/screenshots/thumbs/08-skin-signal.png" alt="Android dark mode" width="200">](ports/docs/screenshots/08-skin-signal.png) | — |

</details>

## License (short)

The volume engine is **VeraCrypt** source. That **inherits** **TrueCrypt License 3.0** plus Apache-2.0. TrueCrypt 3.0 is **not OSI / FSF / Debian-free**; public source is required when binaries ship. GitHub APKs are **debug-signed** previews — not production-signed store builds.

Details: [License.txt](License.txt), [ports/FOSS.md](ports/FOSS.md)

## More

| | |
| --- | --- |
| Build from source | [PORTING.md](PORTING.md) |
| Security issues | [SECURITY.md](SECURITY.md) |
| Optional support | [SUPPORT.md](SUPPORT.md) · [GitHub Sponsors](https://github.com/sponsors/ShivamPingaleDev) |
| Public talking points | [ports/PUBLIC.md](ports/PUBLIC.md) |

**Contact:** [shivampingaledev@proton.me](mailto:shivampingaledev@proton.me)

This repo is named Veracrypt_port because it includes VeraCrypt source. The app on the phone is **VC Port**. Use official VeraCrypt on a PC or Mac — this project does not ship a desktop app.
