# Keeping VC Port public

**Contact:** Shivam Mangesh Pingale — shivampingaledev@proton.me · shivampingaledev@gmail.com

**Footnote:** A programming noob still doing a five-year IT engineering degree (graduate summer 2027). Just trying to make something better that he likes to use, without much knowledge. Open to suggestions and advice.

If this work is useful to you and you have room to **teach**, offer an **internship**, or **hire**: I am looking for that. Same email as Contact. No pressure.

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
| Android previews | GitHub Release `v0.3.1` APKs (debug-signed): `VCPort-0.3.1-fdroid-preview.apk`, `VCPort-0.3.1-github-preview.apk` |
| Looks preview | GitHub Release `v0.3.1`: `VCPort-0.3.1-looks-preview.apk` (no INTERNET) and `VCPort-0.3.1-looks-github-preview.apk` (tap Check for updates). Same `applicationId`, skins on. Installing either replaces the Desktop-only APK. |
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

## Discovery and SEO (no ads, no trackers)

There is **no advertisement SDK** in the apps and **no Google Analytics / pixels** on the source pages. Discovery is search + honest posts.

### What GitHub already is

GitHub is the index. Fill **About** on both repos (description, topics, website). Search engines quote the first paragraph of `README.md`. Releases (`v0.3.1`) are a second URL people share.

Honest phrases people actually type — use them in README/About, never as a fake product name:

- VeraCrypt-compatible Android
- VeraCrypt on iPhone / iOS (then immediately: **not named VeraCrypt**; you sign the IPA)
- open `.hc` / `.tc` / `.vera` on Android
- F-Droid VeraCrypt-compatible (only after a listing exists)
- AltStore sideload encrypted volume

Do **not** buy Google/Apple/Meta ads. Do **not** title posts “VeraCrypt for Android.” Do **not** keyword-stuff “unbreakable.”

### Topics to set on the GitHub About box

`android` `ios` `encryption` `cryptography` `veracrypt` `truecrypt` `kotlin` `swift` `foss` `privacy` `disk-encryption` `offline`

Website field: VCPort → `https://github.com/ShivamPingaleDev/Veracrypt_port` (full source). Veracrypt_port → `https://github.com/ShivamPingaleDev/VCPort` (mobile apps).

### Where to post once (human, not spam)

| Place | Why | How |
| --- | --- | --- |
| GitHub topics + README | Default search | Already in-tree |
| F-Droid / IzzyOnDroid | Real Android SEO for FOSS | Recipe in `fdroiddata/`; submit when ready; do not claim a listing first |
| AltStore source | iPhone users | Only after a **signed** IPA; `downloadURL` stays empty until then |
| VeraCrypt forum / r/VeraCrypt | People who already have volumes | “Derived work, VC Port, not official, not unbreakable,” link source |
| r/fossdroid, GrapheneOS, privacy forums | Offline Android | F-Droid flavor, no INTERNET |
| Mastodon / fediverse | No ad network | Same copy as README first paragraph |
| AlternativeTo / awesome-privacy lists | Long-tail search | Submit as VC Port, VeraCrypt-compatible |

Do not open drive-by issues on `veracrypt/VeraCrypt` to advertise. Do not paste binaries into random Discords without the TrueCrypt attribution.

### More that is not ads

- Keep releases tagged (`v0.3.1`) so “VC Port 0.3.1” is a stable URL.
- Keep screenshots in [docs/screenshots/](docs/screenshots/) — GitHub uses them as social preview.
- Fastlane short/full description is F-Droid/Play copy; same rules (not named VeraCrypt, not unbreakable).
- A personal site is optional. If you add one, static HTML only, no analytics, link both git repos and `License.txt`.

F-Droid Fastlane `phoneScreenshots/` stays empty until a **physical phone** capture exists. Emulator Compose shots on GitHub are the real UI, not store mockups; `adb screencap` is black on purpose (`FLAG_SECURE`).
