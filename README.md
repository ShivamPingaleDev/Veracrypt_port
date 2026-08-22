# VC Port

Android and iOS clients for VeraCrypt-compatible volumes. This derived work is **not** named VeraCrypt.

Package id: `dev.shivampingale.vcport`

The full macOS port and VeraCrypt source tree live in [Veracrypt_port](https://github.com/ShivamPingaleDev/Veracrypt_port). This repo is the mobile apps plus the shared native core.

Optional support (GitHub only, not in the app): [GitHub Sponsors](https://github.com/sponsors/ShivamPingaleDev) · [SUPPORT.md on Veracrypt_port](https://github.com/ShivamPingaleDev/Veracrypt_port/blob/master/SUPPORT.md)

## What it does

- Open a VeraCrypt container (FAT root listing and file extract)
- Biometric unlock (Android Keystore / Face ID / Touch ID)
- System share sheet (WhatsApp, Gmail, Drive, Mail, AirDrop, …)
- Share encrypted `.hc` / `.tc` / `.vera` files as-is
- Wrap or unwrap a single file (`.vcpw`) with a password that is never stored
- Strong password generator (in memory only, no history)
- Offline by default; update check is one HTTPS request when you tap it

## Layout

```
android/   Kotlin / Compose app
ios/       SwiftUI sources (create an Xcode project — see ios/README.md)
shared/    Native volume + wrap core (C/C++)
```

## Native core

The JNI/CMake build needs the VeraCrypt `src` tree from Veracrypt_port:

```bash
git clone https://github.com/ShivamPingaleDev/Veracrypt_port.git veracrypt
```

Or set `VC_SRC` to that clone's `src` directory.

Host wrap test (macOS Apple silicon):

```bash
./shared/run_wrap_test.sh
```

## Android

Open `android/` in Android Studio, or:

```bash
cd android
./gradlew :app:assembleDebug
```

If `gradlew` is missing, use Android Studio's Gradle wrapper generation. `minSdk` 28, `applicationId` `dev.shivampingale.vcport`.

## iOS

Follow `ios/README.md`: create an Xcode app named `VCPort`, add `ios/VCPort/*`, link `libvc_mobile.a` built from `shared/`.

## License

Same terms as VeraCrypt (Apache 2.0 / TrueCrypt 3.0). You may not call this app VeraCrypt.
