# Keeping VC Port public

**Contact:** Shivam Mangesh Pingale — shivampingaledev@proton.me · shivampingaledev@gmail.com

**Footnote:** A programming noob with a five-year IT engineering degree that did not work out. Just trying to make something better that he likes to use, without much knowledge. Open to suggestions and advice.

TrueCrypt License 3.0 requires the source to stay public if a binary is shipped. Both GitHub repos stay **public**:

- Full tree: https://github.com/ShivamPingaleDev/Veracrypt_port
- Mobile-only (`ports/` as root): https://github.com/ShivamPingaleDev/VCPort

Do not make either private after an APK or IPA has been posted.

## What to call it

The app is **VC Port**. It is a derived work. It is **not named VeraCrypt**. It is **not unbreakable**.

Say:

- “VC Port opens VeraCrypt-compatible file containers on Android and iPhone.”
- “Offline by default. F-Droid flavor has no INTERNET. iPhone users sign the unsigned IPA themselves.”

Do not say:

- that the app is VeraCrypt, or “VeraCrypt for Android/iPhone”
- “unbreakable”, “military grade”, “no one can open this”, “blocks Unit 8200”
- that GitHub APKs are production-signed (they are **debug-signed previews**)
- that the unsigned IPA installs without the user’s Apple ID
- that F-Droid already ships it (the recipe is in-tree; the main repo has not merged it)

## Where the files live

| What | Where |
| --- | --- |
| Source | Both public repos above |
| Android previews | GitHub Release `v0.3.1` APKs (debug-signed) |
| iPhone preview | `VCPort-0.3.1-unsigned-preview.ipa` — **you sign it** (see [FOSS.md](FOSS.md) / [ios/README.md](ios/README.md)) |
| F-Droid | Recipe `fdroiddata/metadata/dev.shivampingale.vcport.yml` — submit later; do not claim a listing |
| AltStore JSON | `ios/altstore/source.json` — `downloadURL` stays **empty** until a signed IPA exists |
| UI shots | [docs/screenshots/](docs/screenshots/) (real emulator UI; FLAG_SECURE still blocks `adb screencap`) |

## Apple users sign their own

There is no Apple-signed VC Port on the App Store in this project. Each person:

1. Downloads the unsigned IPA **or** builds `ios/` from source.
2. Signs it with **their** Apple ID in AltStore / SideStore, or with their Team in Xcode.
3. Trusts the developer cert on the iPhone.

A signature you create will not install on someone else’s phone. Do not fill AltStore `downloadURL` with the unsigned zip.

## How to keep it in public (without ads)

No tracker, no store ads, no “growth” SDK. Public presence is the git repos and honest posts:

1. Keep `README.md` and [docs/screenshots/](docs/screenshots/) current.
2. Point people at the GitHub Release, this file, and [FOSS.md](FOSS.md).
3. If you post elsewhere (forum, fediverse), use the name **VC Port**, link source, and include “not unbreakable” plus the TrueCrypt attribution.
4. Do not paste volume passwords, keyfiles, or Remember secrets into issues or screenshots.
5. Security reports go to [SECURITY.md](../SECURITY.md) — not a public issue with exploit details.

F-Droid Fastlane `phoneScreenshots/` stays empty until a **physical phone** capture exists. Emulator Compose shots on GitHub are the real UI, not store mockups; `adb screencap` is black on purpose (`FLAG_SECURE`).
