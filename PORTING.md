# VeraCrypt port — Apple silicon, Android, iOS

This tree is a working fork of [VeraCrypt](https://github.com/veracrypt/VeraCrypt) with:

1. **macOS Apple silicon improvements** (FUSE-T based)
2. **Touch ID volume unlock** on macOS
3. **Native administrator authentication** so a standard user can type the original admin password (or use Touch ID)
4. **Android and iOS clients** with biometric unlock, using the VeraCrypt volume core

The mobile apps are named **VC Port**. The VeraCrypt license does not allow a derived work to be called VeraCrypt.

Mobile-only GitHub repo: https://github.com/ShivamPingaleDev/VCPort  
Full tree (macOS + VeraCrypt src + mobile): https://github.com/ShivamPingaleDev/Veracrypt_port

Contact: Shivam Mangesh Pingale — shivampingaledev@proton.me · shivampingaledev@gmail.com

**Footnote:** A programming noob with a five-year IT engineering degree that did not work out. Just trying to make something better that he likes to use, without much knowledge. Open to suggestions and advice.

## FUSE-T vs FSKit (Apple silicon)

**FUSE-T cannot be avoided yet.** VeraCrypt on macOS does not mount the guest filesystem itself. It:

1. Decrypts the volume in user space
2. Exposes a virtual disk *file* through FUSE
3. Attaches that file with `hdiutil` so macOS mounts FAT/exFAT/APFS/HFS+

Apple **FSKit** (macOS 15.4+, expanded in macOS 26) is a userspace filesystem API, not a virtual block-device API. It is not a drop-in replacement for that auxiliary image, and it still lacks VFS operations VeraCrypt would need for a native module.

What we do instead:

| macOS | Approach |
| --- | --- |
| Apple silicon (recommended) | FUSE-T (`make WITHFUSET=1`) |
| FUSE-T with FSKit backend present | Use `-o backend=fskit` automatically |
| FUSE-T with SMB backend (`go-smb2`) | Use `-o backend=smb` (avoids Network Volume prompts) |
| FUSE-T NFS-only (Homebrew 1.0.44) | Keep the default NFS backend — **do not force SMB** (that hung mounts) |
| Intel, Reduced Security allowed | macFUSE remains available |

A native FSKit backend is tracked as future work once Apple exposes a stable block-image or loopback path.

## macOS: standard user + admin password

Upstream VeraCrypt elevates with `sudo -S` and a **single password field**. `sudo` authenticates the *current* user. A standard user who types the original administrator password therefore fails (`user is not in the sudoers file` / `Failed to obtain administrator privileges`).

This fork uses **Authorization Services** on macOS:

- System authentication dialog with **admin user picker**
- **Touch ID** for administrator authorization on Apple silicon
- The elevated core service is started as root over a Unix socket (`--elevated-socket`)
- `SUDO_UID` / `SUDO_GID` are passed through so FUSE objects stay owned by the logged-in user

`sudo` remains a fallback if Authorization Services is unavailable.

## macOS: Touch ID for volume passwords

On the mount dialog:

- **Remember password with Touch ID** stores the volume password + PIM in the Keychain, wrapped with `kSecAccessControlBiometryCurrentSet` (invalidated if fingerprints change)
- **Unlock with Touch ID** retrieves it through LocalAuthentication
- Secrets never leave the Secure Enclave-backed Keychain in plaintext at rest

This is convenience, not a replacement for a strong volume password. The mount dialog warns that biometrics can be compelled.

**Tools menu (macOS and Linux GUI):** wrap/unwrap a single file (`.vcpw`, same Argon2id wrap as Android/iOS), share an encrypted container as-is, and panic wipe (dismount all, wipe password cache, clipboard, and stored Touch ID secrets).

## Build macOS (Apple silicon, FUSE-T)

```bash
cd src
make WXSTATIC=1 WX_ROOT=/path/to/wxWidgets WITHFUSET=1 LOCAL_DEVELOPMENT_BUILD=true
```

Install [FUSE-T](https://www.fuse-t.org/) first. The FUSE-T VeraCrypt build is the supported Apple silicon path.

## Android

Project: `ports/android`  
Native core: `ports/shared` (VeraCrypt `Volume` + Crypto via NDK)

One APK ships four ABIs: `armeabi-v7a` (32-bit ARM), `arm64-v8a` (ARM64), `x86` (32-bit Intel emulators), `x86_64`. Crypto extras follow the slice (ARMv8 AES, x64 AVX2, x86 SSE2). There is no 32-bit iOS.

F-Droid / FOSS (no `INTERNET` permission, no Play libraries):

```bash
cd ports/android
./gradlew :app:assembleFdroidRelease
```

Biometric unlock uses Android Keystore + `BiometricPrompt` (strong biometrics, StrongBox when present). High-threat defaults: [ports/THREAT-MODEL.md](ports/THREAT-MODEL.md). There is no DocumentsProvider export (that was a seizure/SAF leak). Browse FAT from the in-app list only.

The in-app file list has a **Share decrypted** action that extracts the file from a FAT volume and opens the system share sheet. **Share encrypted file** sends `.hc` / `.tc` / `.vera` as-is (no unlock). **Wrap a single file** password-encrypts one file into a `.vcpw` blob (Argon2id + AES-256-CTR + HMAC-SHA256). The password generator stays in memory, is never logged, and clipboard copies expire. Other apps can also send files into VC Port (`ACTION_SEND` / `VIEW`).

Store metadata: `ports/android/fastlane/`. Inclusion notes: [ports/FOSS.md](ports/FOSS.md). How to keep the repos public: [ports/PUBLIC.md](ports/PUBLIC.md). Emulator UI shots: [ports/docs/screenshots/](ports/docs/screenshots/).

## iOS

There is no F-Droid for iPhone. `ports/ios/build-native.sh` builds `libvc_mobile` for the current SDK: device `arm64`, simulator `arm64` (Apple silicon) or `x86_64` (Intel Mac). Each Apple user **signs their own** IPA with their Apple ID (AltStore / SideStore or Xcode Team). The GitHub IPA is unsigned on purpose. See [ports/FOSS.md](ports/FOSS.md), [ports/PUBLIC.md](ports/PUBLIC.md), and `ports/ios/README.md`.

The SwiftUI app uses the same `vc_mobile` C API and Keychain + Face ID / Touch ID. Unlock factors can be combined: biometric password (a Keychain-held keyfile), optional text password, more keyfiles, and PIM.

**Share encrypted file** sends `.hc` / `.tc` / `.vera` as-is (no password). **Wrap a single file** creates a `.vcpw` wrap. The password generator never writes history. **Share decrypted** on a listed file presents `UIActivityViewController` after extract. Incoming “Open in VC Port” files are handled with `onOpenURL` and the document types in `ports/ios/VCPort/Info.plist`.

## Offline-first updates

The apps **do not** contact the network on launch or in the background.

| Action | Network |
| --- | --- |
| Mount, encrypt, browse, biometrics | None |
| Settings → Stay offline (default on) | Help/website links ask first |
| Help → Check for updates | One HTTPS GET of `ports/version.json`, then disconnect |
| Download page (only if you agree) | Browser, then offline again |

When VeraCrypt itself ships a new source tree, developers run:

```bash
scripts/sync-upstream.sh --check   # temporary fetch, then offline
scripts/sync-upstream.sh           # 3-way merge; restore owned files only
scripts/refresh-overlay.sh
scripts/check-upstream-layout.sh
```

How the overlay is layered, and which files are owned vs patched: [ports/UPSTREAM.md](ports/UPSTREAM.md).

`ports/overlay/owned.txt` is restored after a merge. `ports/overlay/patched.txt` is **not** overwritten (that would drop VeraCrypt’s own edits). `ports/UPSTREAM_COMMIT` is the last synced revision.

The Android/iOS tree is also published on its own at https://github.com/ShivamPingaleDev/VCPort (`ports/` as the repo root).

There is no automatic updater and no always-on connection.

## License

Original TrueCrypt 7.1a code: TrueCrypt License 3.0  
VeraCrypt modifications: Apache License 2.0 (`License.txt`)  
Port additions in this repository: Apache License 2.0
