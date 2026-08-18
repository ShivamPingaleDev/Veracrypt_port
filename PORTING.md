# VC Port — phones

VC Port is **Android and iPhone** apps that use official [VeraCrypt](https://github.com/veracrypt/VeraCrypt) source for crypto and volumes. The apps are named **VC Port**. The VeraCrypt license does not allow a derived work to be called VeraCrypt.

This repo keeps original VeraCrypt `src/` byte-identical to the pin so a source update is a git merge. Phone hunks live under [ports/overlay/src/](ports/overlay/README.md) using the same relative paths as VeraCrypt (`Platform/Unix/File.cpp`, `Common/Token.cpp`, token headers) and are compiled instead of those `src/` files. There is no computer GUI from this project. On a PC or Mac, use official VeraCrypt.

Mobile-only GitHub repo: https://github.com/ShivamPingaleDev/VCPort  
Full tree: https://github.com/ShivamPingaleDev/Veracrypt_port

Contact: Shivam Mangesh Pingale — shivampingaledev@proton.me · shivampingaledev@gmail.com

**Footnote:** A programming noob still doing a five-year IT engineering degree (graduate summer 2027). Just trying to make something better that he likes to use, without much knowledge. Open to suggestions and advice.

If this work is useful to you and you have room to **teach**, offer an **internship**, or **hire**: I am looking for that. Same email as Contact. No pressure.

## Android

Project: `ports/android`  
Native core: `ports/shared` (VeraCrypt `Volume` + Crypto via NDK)

One APK ships four ABIs: `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`. Crypto extras follow the slice (ARMv8 AES, x64 AVX2, x86 SSE2). There is no 32-bit iOS.

FOSS production flavor (no `INTERNET` permission, no Play libraries):

```bash
cd ports/android
./gradlew :app:assembleFossRelease
```

High-threat defaults: [ports/THREAT-MODEL.md](ports/THREAT-MODEL.md). There is no DocumentsProvider export. Browse FAT or exFAT from the in-app list only.

Store metadata: `ports/android/fastlane/`. Inclusion notes: [ports/FOSS.md](ports/FOSS.md). How to keep the repos public: [ports/PUBLIC.md](ports/PUBLIC.md). Emulator UI shots: [ports/docs/screenshots/](ports/docs/screenshots/).

## iOS

`ports/ios/build-native.sh` builds `libvc_mobile` for the current SDK: device `arm64`, simulator `arm64` (Apple silicon) or `x86_64` (Intel Mac). Each Apple user **signs their own** IPA with their Apple ID (AltStore / SideStore or Xcode Team). The GitHub IPA is unsigned on purpose. iPad Simulator: `ports/ios/run_ipad_sim.sh`. Device sideload under your name: Xcode Team, or `VC_PORT_IOS_TEAM=YOUR10CHARID ports/ios/sideload-sign.sh`. Parallel Android + iOS: `ports/scripts/build-phones.sh`. See [ports/FOSS.md](ports/FOSS.md), [ports/PUBLIC.md](ports/PUBLIC.md), and `ports/ios/README.md`.

The SwiftUI app uses the same `vc_mobile` C API. Fingerprint / Face ID unlock is on `experimental-biometrics`, not master.

## Offline-first

The apps **do not** contact the network on launch or in the background. Live Check for updates is not on master.

When VeraCrypt itself ships a new source tree, developers run:

```bash
scripts/sync-upstream.sh --check   # temporary fetch, then offline
scripts/sync-upstream.sh           # 3-way merge; restore owned files only
scripts/refresh-overlay.sh
scripts/check-upstream-layout.sh
```

How the overlay is layered: [ports/UPSTREAM.md](ports/UPSTREAM.md).

`ports/overlay/owned.txt` is restored after a merge. `src/` is official VeraCrypt: do not edit it. Phone replacements live in `ports/overlay/src/` at the same relative paths. `ports/UPSTREAM_COMMIT` is the last synced revision.

## License

Original TrueCrypt 7.1a code: TrueCrypt License 3.0  
VeraCrypt modifications: Apache License 2.0 (`License.txt`)  
Port additions in this repository: Apache License 2.0
