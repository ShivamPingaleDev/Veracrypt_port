# VC Port for Android

Kotlin / Jetpack Compose client with:

- VeraCrypt volume core via NDK (`shared/`)
- Password, PIM, keyfiles, and mount options (backup header, read-only, TrueCrypt Mode, hidden-volume protection). Fingerprint unlock is on `experimental-biometrics`, not master.
- In-app FAT or exFAT folder browse, extract, copy/move (including Copy to volume / Move to volume between several mounted containers, and Copy/Move several files from or to the device), New folder / Rename / Delete / Properties, and wipe free space. Files larger than 4 GiB need exFAT. This app cannot mount a whole USB disk. Several containers can stay mounted in one session (up to 8).
- System share sheet for decrypted files inside a volume, and **Share encrypted file** to send `.hc` / `.tc` / `.vera` as-is (no unlock)
- In-memory password generator that never saves history
- Incoming share / open: other apps can send a file into VC Port
- In-app FAT browser only (no DocumentsProvider; that was a seizure leak). Copy/move uses the system file picker. Android will not let a third-party app attach a volume as a folder other apps can browse.

FOSS production flavor (no `INTERNET` permission, no Play libraries):

```bash
cd android
./gradlew :app:assembleFossRelease
```

GitHub preview flavor (same app id, also offline on master):

```bash
./gradlew :app:assembleGithubRelease
```

Appearance is Original plus Dark mode in both. Cyberpunk, Matrix, and MAGI are archived under `archive/looks/` and are not built.

Open `android/` in Android Studio if you prefer. The native library is `libvcport.so`, built from `shared/CMakeLists.txt`.

Release signing: do **not** commit a keystore. CI and GitHub APKs stay **debug-signed previews**. For a local production APK, set `VC_PORT_RELEASE_STORE_FILE`, `VC_PORT_RELEASE_STORE_PASSWORD`, `VC_PORT_RELEASE_KEY_ALIAS`, and `VC_PORT_RELEASE_KEY_PASSWORD`. You sign production yourself.

Store metadata lives in `android/fastlane/metadata/android/`. Inclusion notes: [FOSS.md](../FOSS.md).
