# VC Port

Android and iOS clients for VeraCrypt-compatible volumes. This derived work is **not** named VeraCrypt.

Package id: `dev.shivampingale.vcport`

**Contact:** Shivam Mangesh Pingale — [shivampingaledev@proton.me](mailto:shivampingaledev@proton.me) · [shivampingaledev@gmail.com](mailto:shivampingaledev@gmail.com)

The full macOS port and VeraCrypt source tree live in [Veracrypt_port](https://github.com/ShivamPingaleDev/Veracrypt_port). This repo is the mobile apps plus the shared native core.

**F-Droid / FOSS:** see [FOSS.md](FOSS.md). **Privacy:** [PRIVACY.md](PRIVACY.md). **Seizure / whistleblower profile:** [THREAT-MODEL.md](THREAT-MODEL.md). **VeraCrypt updates:** [UPSTREAM.md](UPSTREAM.md).

## What it does

- Open a VeraCrypt container (FAT root listing and file extract)
- Biometric unlock (Android Keystore / Face ID / Touch ID) as a password factor, combinable with a text password, keyfiles, and PIM
- System share sheet (WhatsApp, Gmail, Drive, Mail, AirDrop, …)
- Share encrypted `.hc` / `.tc` / `.vera` files as-is
- Wrap or unwrap a single file (`.vcpw`) with a password that is never stored
- Strong password generator (in memory only, no history)
- Offline by default. The F-Droid Android flavor has no network permission. iOS update checks are off unless you set `VCPortEnableUpdateCheck`.

## Layout

```
android/     Kotlin / Compose app (F-Droid `fdroid` flavor)
ios/         SwiftUI sources + XcodeGen + AltStore source JSON
shared/      Native volume + wrap core (C/C++)
fdroiddata/  Recipe to copy into F-Droid's fdroiddata repo
```

## Native core

The JNI/CMake build needs the VeraCrypt `src` tree from Veracrypt_port:

```bash
git clone https://github.com/ShivamPingaleDev/Veracrypt_port.git veracrypt
```

Or set `VC_SRC` to that clone's `src` directory.

Host wrap test (macOS or Linux):

```bash
./shared/run_wrap_test.sh
```

## Android

```bash
cd android
./gradlew :app:assembleFdroidRelease   # F-Droid / FOSS (no INTERNET)
./gradlew :app:assembleGithubDebug     # optional in-app update check
```

`minSdk` 28, `applicationId` `dev.shivampingale.vcport`. No Google Play libraries.

## iOS

There is no F-Droid for iPhone. Build from source or sideload with AltStore / SideStore. See `ios/README.md` and [FOSS.md](FOSS.md).

```bash
cd ios
./build-native.sh
xcodegen generate    # brew install xcodegen
```

## License

Same terms as VeraCrypt (Apache 2.0 / TrueCrypt 3.0). You may not call this app VeraCrypt.

Portions of this product are based in part on TrueCrypt, freely available at http://www.truecrypt.org/
