# FOSS

VC Port is free software you can study, change, and share. Original VC Port
code is Apache License 2.0. The volume core is VeraCrypt, dual-licensed
Apache-2.0 **and** TrueCrypt License 3.0. TrueCrypt 3.0 is **not** OSI / FSF /
Debian-free; this tree still follows it: public source whenever a binary ships,
and the TrueCrypt attribution in About.

This is not a store listing checklist. The method is: keep the source public,
ship nothing proprietary, let people rebuild and sign their own copies.

## What this tree keeps doing

- Public git: home is [Veracrypt_port](https://github.com/ShivamPingaleDev/Veracrypt_port) (source + APK/IPA). [VCPort](https://github.com/ShivamPingaleDev/VCPort) is only the phone folders, not a second app.
- No Google Play / Firebase / Crashlytics / ads / analytics
- AndroidX + Kotlin + NDK only on Android; no prebuilt `.so` / `.a` blobs
- `minifyEnabled false` (reviewable bytecode)
- Gradle Wrapper 8.7 with a published SHA-256
- No Play Integrity, SafetyNet, or obfuscation
- Offline by default: master phone builds have no `INTERNET` permission
- Dual-license honesty in the About UI (Apache-2.0 + TrueCrypt 3.0)
- You build from source; you sign production binaries (`VC_PORT_RELEASE_STORE_FILE` or your Apple ID)

High-threat defaults: [THREAT-MODEL.md](THREAT-MODEL.md). How the repos stay public: [PUBLIC.md](PUBLIC.md).

## Android — production FOSS flavor

```bash
cd android
./gradlew :app:assembleFossRelease
```

That flavor has no `INTERNET`, no Play libraries, and backups disabled. Updates are a new APK you rebuild from this git tree, not a download inside the app.

Android APK and unsigned iOS IPA together (local, same as CI):

```bash
ports/scripts/build-phones.sh
```

Sign the IPA on this Mac with your 10-character Apple Team ID (`VC_PORT_IOS_TEAM` or gitignored `ios/Signing.local.xcconfig`). GitHub never Apple-signs.

Appearance is Original plus Dark mode. Cyberpunk, Matrix, and MAGI are archived under `archive/looks/` and are not built. The `github` flavor (`assembleGithubRelease`) shares `applicationId` (`dev.shivampingale.vcport`) and is also offline on master.

GitHub Actions APKs are **debug-signed previews**. The GitHub Release for this version attaches **one** FOSS APK and an unsigned iOS IPA. CI builds both in parallel. Sign anything called production yourself (`VC_PORT_IOS_TEAM` / `VC_PORT_RELEASE_STORE_FILE`).

Contact: Shivam Mangesh Pingale — shivampingaledev@proton.me · shivampingaledev@gmail.com. See [SECURITY.md](../SECURITY.md).

**Footnote:** A programming noob still doing a five-year IT engineering degree (graduate summer 2027). Just trying to make something better that he likes to use, without much knowledge. Open to suggestions and advice.

## iPhone — build from source

The Free Software Foundation’s position is that an iOS app cannot be fully free software because the kernel, SDK, and distribution terms are non-free. That does not stop you from publishing **source-available, no-tracker** iOS builds.

| Channel | What it is | FOSS fit |
| --- | --- | --- |
| **Build from source** | Xcode / `xcodegen` + `ios/build-native.sh` | Best. You compile the IPA yourself. |
| **AltStore / SideStore** | Sideload an IPA you built. Refresh the 7-day developer cert (or use AltStore PAL in the EU). | Closest to a user-signed store. Source JSON: `ios/altstore/source.json`. |
| **EU alternative marketplace** | DMA store, still Apple-notarized rules | Possible later. |
| **App Store** | Apple review, Apple signing, yearly encryption declaration | Allowed for Apache-2.0 UI code. Not “free” in the FSF sense (non-free OS/SDK, Apple terms). |

### Apple requirements this tree already matches

1. **Privacy Manifest** (`ios/VCPort/PrivacyInfo.xcprivacy`) — required since May 2024 for App Store and expected by current AltStore/SideStore. Declares no tracking.
2. **Face ID usage string** (`NSFaceIDUsageDescription`) — unused on this branch (`VCPortEnableBiometrics` false). Android foss and github also have no fingerprint extra.
3. **Encryption export** (`ITSAppUsesNonExemptEncryption=true`) — volume AES is **not** the HTTPS-only exemption. For App Store you must complete Apple’s encryption questions and, if you distribute outside the US, typically file an annual self-classification (ERN). Sideloaded / AltStore IPAs still encrypt; they just skip App Store Connect.
4. **No tracking SDKs** — no ATT prompt, no `NSUserTrackingUsageDescription`.
5. **Minimum iOS 16**, no private APIs.
6. **Keychain entitlements** remain for sideload signing; master does not store volume passwords behind Face ID.
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

iPad Simulator (no Apple ID, same idea as the Android emulator):

```bash
cd ios
./run_ipad_sim.sh
```

Your iPad (signed with **your** Apple ID name on the cert):

```bash
cd ios
./build-native.sh
xcodegen generate
open VCPort.xcodeproj
```

Signing & Capabilities → Automatically manage signing → Team → your name → Run on the iPad. Bundle id stays `dev.shivampingale.vcport` on a paid team; a free Personal Team may add a unique suffix.

Or, after you put your 10-character Team ID in gitignored `ios/Signing.local.xcconfig` (or `VC_PORT_IOS_TEAM`):

```bash
./sideload-sign.sh
```

That writes a development IPA under `ios/build/sideload/` for Finder / Apple Configurator.

Do **not** put the unsigned IPA in AltStore `downloadURL`. That field stays empty until a signed IPA exists. See [PUBLIC.md](PUBLIC.md).

Default Info.plist has `VCPortEnableUpdateCheck=false`, so the iPhone app does not use the network. A rebuild you sign is how a new IPA arrives.

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
