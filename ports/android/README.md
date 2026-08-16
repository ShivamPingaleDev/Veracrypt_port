# VC Port for Android

Kotlin / Jetpack Compose client with:

- VeraCrypt volume core via NDK (`ports/shared`)
- Biometric unlock (Android Keystore + BiometricPrompt)
- In-app FAT root listing and file extract
- System share sheet for decrypted files inside a volume, and **Share encrypted file** to send `.hc` / `.tc` / `.vera` as-is (no unlock)
- Wrap/unwrap individual files (`.vcpw`) and an in-memory password generator that never saves history
- Incoming share / open: other apps can send a file into VC Port
- DocumentsProvider stub for other apps

```bash
cd ports/android
./gradlew :app:assembleDebug
```

Open `ports/android` in Android Studio if you prefer. The native library is `libvcport.so`, built from `ports/shared/CMakeLists.txt`.
