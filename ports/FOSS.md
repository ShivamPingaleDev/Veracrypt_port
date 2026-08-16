# FOSS, F-Droid, and iPhone

There is **no F-Droid for iPhone**. Android and iOS are different stores, different licenses, and different definitions of “open.” This file is the checklist for both.

## Android — F-Droid main repo

F-Droid builds the APK from public source on their servers. The `fdroid` flavor is the one to submit:

```bash
cd android
./gradlew :app:assembleFdroidRelease
```

That flavor has:

- no `INTERNET` permission (updates come from F-Droid)
- no Google Play / Firebase / Crashlytics / ads
- AndroidX + Kotlin + NDK only
- backups disabled
- Gradle Wrapper 8.7 with a published SHA-256
- High-threat defaults documented in [THREAT-MODEL.md](THREAT-MODEL.md) (no Play Integrity, no obfuscation)
- Fastlane text under `android/fastlane/metadata/android/`

Recipe to copy into [fdroiddata](https://gitlab.com/fdroid/fdroiddata): `fdroiddata/metadata/dev.shivampingale.vcport.yml`.

### Still required before an inclusion merge request

1. **Public git repo.** TrueCrypt License 3.0 and F-Droid both require publicly available source. This tree is [Veracrypt_port](https://github.com/ShivamPingaleDev/Veracrypt_port). The `fdroiddata` recipe clones that repo with `subdir: ports/android`. The mobile-only [VCPort](https://github.com/ShivamPingaleDev/VCPort) mirror is public and is not the F-Droid source.
2. **Git tag** matching `versionName`, e.g. `v0.3.0`, on the commit F-Droid should build.
3. **Screenshots** in `android/fastlane/metadata/android/en-US/images/phoneScreenshots/`. Do not fake device photos. That folder stays empty until a **physical phone** capture exists (`FLAG_SECURE` makes `adb screencap` black). GitHub README shots are real emulator Compose captures in [docs/screenshots/](docs/screenshots/).
4. **VeraCrypt `src` as an F-Droid srclib** (`fdroiddata/srclibs/VeraCryptPort.yml`), because this repo does not vendor the whole VeraCrypt tree.
5. **License review.** VeraCrypt is dual-licensed Apache-2.0 / TrueCrypt 3.0. TrueCrypt 3.0 is **not** OSI/FSF/Debian-free. F-Droid defers to those lists. They may accept Apache-2.0 for VeraCrypt-authored files, or they may refuse the inherited TrueCrypt files. If the main repo refuses, host your own F-Droid repo with `fdroidserver` or ask [IzzyOnDroid](https://apt.izzysoft.de/fdroid/) — still FOSS, not Google Play.

Do not add a second signing key later if you want reproducible builds; decide that on the first published APK. GitHub Actions APKs are **debug-signed previews**. F-Droid (or `VC_PORT_RELEASE_STORE_FILE`) must sign production builds.

GitHub also builds **Looks** APKs (`assembleStyledRelease` offline, `assembleLooksgithubRelease` with tap-to-check) with Desktop plus Cyberpunk / Matrix / MAGI / Signal. Same `applicationId` as the store app (`dev.shivampingale.vcport`) — not a separate package. F-Droid must keep assembling the `fdroid` flavor only.

Contact: Shivam Mangesh Pingale — shivampingaledev@proton.me · shivampingaledev@gmail.com. See [SECURITY.md](../SECURITY.md).

**Footnote:** A programming noob still doing a five-year IT engineering degree (graduate summer 2027). Just trying to make something better that he likes to use, without much knowledge. Open to suggestions and advice.

## iPhone — there is no F-Droid equivalent

Apple does not allow a third-party store like F-Droid on iOS outside the EU DMA marketplaces. “FOSS on iPhone” in practice means:

| Channel | What it is | FOSS fit |
| --- | --- | --- |
| **Build from source** | Xcode / `xcodegen` + `ios/build-native.sh` | Best. You compile the IPA yourself. |
| **AltStore / SideStore** | Sideload an IPA you built. Refresh the 7-day developer cert (or use AltStore PAL in the EU). | Closest to F-Droid. Source JSON: `ios/altstore/source.json`. |
| **EU alternative marketplace** | DMA store, still Apple-notarized rules | Possible later; not F-Droid. |
| **App Store** | Apple review, Apple signing, yearly encryption declaration | Allowed for Apache-2.0 UI code. Not “free” in the FSF sense (non-free OS/SDK, Apple terms). |

The Free Software Foundation’s position is that an iOS app cannot be fully free software because the kernel, SDK, and distribution terms are non-free. That does not stop you from publishing **source-available, no-tracker** iOS builds. It does mean you should not call the iPhone app “F-Droid ready.”

### Apple requirements this tree already matches

These are the iPhone standards that actually apply:

1. **Privacy Manifest** (`ios/VCPort/PrivacyInfo.xcprivacy`) — required since May 2024 for App Store and expected by current AltStore/SideStore. Declares no tracking.
2. **Face ID usage string** (`NSFaceIDUsageDescription`) — required if you use biometrics.
3. **Encryption export** (`ITSAppUsesNonExemptEncryption=true`) — volume AES is **not** the HTTPS-only exemption. For App Store you must complete Apple’s encryption questions and, if you distribute outside the US, typically file an annual self-classification (ERN). Sideloaded / AltStore IPAs still encrypt; they just skip App Store Connect.
4. **No tracking SDKs** — no ATT prompt, no `NSUserTrackingUsageDescription`.
5. **Minimum iOS 16**, no private APIs.
6. **Keychain entitlements** for Face ID–gated volume passwords.
7. **Source must stay public** if you distribute binaries (TrueCrypt License 3.0 §III.1.d).

### Build the iOS app from source

```bash
brew install xcodegen   # optional; creates the Xcode project
cd ios
./build-native.sh       # needs VC_SRC or a Veracrypt_port checkout
xcodegen generate
xcodebuild -scheme VCPort -configuration Release \
  -destination 'generic/platform=iOS' \
  CODE_SIGNING_ALLOWED=NO
```

### Apple users sign it themselves

GitHub’s `VCPort-*-unsigned-preview.ipa` is **not** Apple-signed. Each person signs **their own** copy with **their** Apple ID. A cert from one Apple ID will not install on someone else’s iPhone.

**AltStore / SideStore (usual path)**

1. Install AltStore or SideStore on the iPhone and add your Apple ID.
2. Download the unsigned IPA from the GitHub Release (or build from source below).
3. Open the IPA in AltStore → Install. AltStore stamps your 7-day (free) or 1-year (paid Developer) cert.
4. On the phone: Settings → General → VPN & Device Management → trust your developer cert.

**Xcode (you have a Mac)**

```bash
cd ios
./build-native.sh
xcodegen generate
open VCPort.xcodeproj
```

Signing & Capabilities → Automatically manage signing → your Team → Run on the iPhone. Bundle id stays `dev.shivampingale.vcport` on a paid team; a free Personal Team may add a unique suffix.

Do **not** put the unsigned IPA in AltStore `downloadURL`. That field stays empty until a signed IPA exists. See [PUBLIC.md](PUBLIC.md).

Default Info.plist has `VCPortEnableUpdateCheck=false`, so the iPhone app does not use the network. AltStore is how updates arrive after you sign a build.

### App Store extras (only if you submit there)

- Privacy Nutrition Label: no data collected
- Encryption compliance / ERN as above
- Review Guidelines: no misleading “VeraCrypt” branding
- Provide the public source URL in the review notes (TrueCrypt source-available condition)

You do **not** need TestFlight, Push, IAP, or Sign in with Apple.

## Shared FOSS rules (both platforms)

- Offline by default
- No telemetry
- Apache-2.0 for original VC Port code; keep `License.txt` + `NOTICE`
- Show the TrueCrypt attribution in the About UI
- Never ship prebuilt proprietary binaries
