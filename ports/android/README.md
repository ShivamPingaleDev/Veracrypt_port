# VC Port for Android

Kotlin / Jetpack Compose client with:

- VeraCrypt volume core via NDK (`ports/shared`)
- Biometric unlock (Android Keystore + BiometricPrompt)
- In-app FAT root listing
- DocumentsProvider stub for other apps

```bash
cd ports/android
./gradlew :app:assembleDebug
```

Open `ports/android` in Android Studio if you prefer. The native library is `libvcport.so`, built from `ports/shared/CMakeLists.txt`.
