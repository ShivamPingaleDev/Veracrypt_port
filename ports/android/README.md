# VC Port for Android

Kotlin / Jetpack Compose client with:

- VeraCrypt volume core via NDK (`shared/`)
- Biometric unlock (Android Keystore + BiometricPrompt) as a password factor. Combine it with a text password, keyfiles, and PIM the same way VeraCrypt does on a computer. Create or import the biometric secret, then export it as a keyfile when you create the volume.
- In-app FAT folder browse, extract, copy/move, New folder / Rename / Delete / Properties, and wipe free space (exFAT unsupported)
- System share sheet for decrypted files inside a volume, and **Share encrypted file** to send `.hc` / `.tc` / `.vera` as-is (no unlock)
- Wrap/unwrap individual files (`.vcpw`) and an in-memory password generator that never saves history
- Incoming share / open: other apps can send a file into VC Port
- In-app FAT browser only (no DocumentsProvider; that was a seizure leak). Copy/move uses the system file picker.

F-Droid / FOSS flavor (no `INTERNET` permission, no Play libraries):

```bash
cd android
./gradlew :app:assembleFdroidRelease
```

Optional GitHub flavor (user-tapped update check only):

```bash
./gradlew :app:assembleGithubRelease
```

Looks APK — same `applicationId` as production (`dev.shivampingale.vcport`), Desktop plus Cyberpunk / Matrix / MAGI / Signal. Installing it replaces the Desktop-only APK. GitHub Release asset, not F-Droid:

```bash
./gradlew :app:assembleStyledRelease
```

Open `android/` in Android Studio if you prefer. The native library is `libvcport.so`, built from `shared/CMakeLists.txt`.

Release signing: do **not** commit a keystore. CI and GitHub APKs stay **debug-signed previews**. For a local production APK, set `VC_PORT_RELEASE_STORE_FILE`, `VC_PORT_RELEASE_STORE_PASSWORD`, `VC_PORT_RELEASE_KEY_ALIAS`, and `VC_PORT_RELEASE_KEY_PASSWORD`. F-Droid rebuilds from source and signs with the F-Droid key.

Store metadata lives in `android/fastlane/metadata/android/`. Inclusion notes: [FOSS.md](../FOSS.md).
