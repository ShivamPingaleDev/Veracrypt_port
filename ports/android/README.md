# VC Port for Android

Kotlin / Jetpack Compose client with:

- VeraCrypt volume core via NDK (`shared/`)
- Biometric unlock (Android Keystore + BiometricPrompt) as a password factor. Combine it with a text password, keyfiles, and PIM the same way VeraCrypt does on a computer. Create or import the biometric secret, then export it as a keyfile when you create the volume.
- In-app FAT root listing and file extract
- System share sheet for decrypted files inside a volume, and **Share encrypted file** to send `.hc` / `.tc` / `.vera` as-is (no unlock)
- Wrap/unwrap individual files (`.vcpw`) and an in-memory password generator that never saves history
- Incoming share / open: other apps can send a file into VC Port
- DocumentsProvider stub for other apps

F-Droid / FOSS flavor (no `INTERNET` permission, no Play libraries):

```bash
cd android
./gradlew :app:assembleFdroidRelease
```

Optional GitHub flavor (user-tapped update check only):

```bash
./gradlew :app:assembleGithubRelease
```

Open `android/` in Android Studio if you prefer. The native library is `libvcport.so`, built from `shared/CMakeLists.txt`.

Store metadata lives in `android/fastlane/metadata/android/`. Inclusion notes: [FOSS.md](../FOSS.md).
