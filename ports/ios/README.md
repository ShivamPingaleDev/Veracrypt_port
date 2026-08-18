# VC Port for iOS

SwiftUI client (`VCPort`) that talks to the shared VeraCrypt volume core in `shared/`.

There is **no F-Droid for iPhone**. The FOSS path is: build from source, then sideload with AltStore / SideStore, or submit to the App Store if you want. Details: [FOSS.md](../FOSS.md).

## Build from source

1. Point `VC_SRC` at a Veracrypt_port `src` tree, or clone it next to this repo as `veracrypt/`.
2. Install [XcodeGen](https://github.com/yonaskolb/XcodeGen) (`brew install xcodegen`).
3. From this directory:

```bash
./build-native.sh
xcodegen generate
open VCPort.xcodeproj
```

4. **Sign it yourself.** There is no Apple-signed VC Port from this project. Each Apple user signs **their own** copy:
   - In Xcode: Signing & Capabilities → your Team (free Apple ID = 7-day cert; paid Developer = 1 year). Run on the iPhone.
   - Or sideload `VCPort-*-unsigned-preview.ipa` with **AltStore / SideStore**, which signs it with **your** Apple ID.
   - Trust the developer cert under Settings → General → VPN & Device Management.
   - A signature you create will not install on someone else’s phone. AltStore `downloadURL` stays empty until a signed IPA exists.
5. The target already includes `PrivacyInfo.xcprivacy`, Keychain entitlements, and `ITSAppUsesNonExemptEncryption=true`. Fingerprint / Face ID unlock is on `experimental-biometrics`, not master.

`VCPortEnableUpdateCheck` in `Info.plist` is **false**. The app does not use the network. Live Check for updates is on `experimental-biometrics`.

## AltStore / SideStore

`altstore/source.json` is a source stub. After you have a signed IPA, add a `versions` entry with `downloadURL`, `size`, `version`, `buildVersion`, and `sha256`, then host the JSON (GitHub raw works). Users add that URL as a source in AltStore.

## App Store (optional)

Not required for FOSS. If you submit:

- Privacy Nutrition Label: data not collected
- Complete Apple’s encryption export questions (volume AES is not the HTTPS exemption; you likely need an ERN)
- Review notes must include the public source URL (TrueCrypt license)
- Do not use the VeraCrypt name

## Features

Unlock with any combination of **text password**, **keyfiles**, and **PIM**. Mount options match the desktop file-container dialog: backup header, read-only, TrueCrypt Mode, and hidden-volume protection. Fingerprint / Face ID unlock is on `experimental-biometrics`, not master.

Tap **Share encrypted file** to send `.hc` / `.tc` / `.vera` as-is (no password). **Wrap a single file** encrypts one file to `.vcpw`; the password generator never writes history. Tap **Share decrypted** on a listed file to extract it from an opened FAT folder and present the system share sheet. **Copy from device** / **Copy to device** (and Move) transfer one file through the system Files picker; Move from the device says so if the original cannot be deleted. **New folder**, **Rename**, **Delete**, **Properties**, and **Wipe free space** work inside the open FAT volume.

There is **no** File Provider extension. Files.app cannot browse an unlocked volume. Listing stays in the VC Port in-app browser.

License: this derived work is not named VeraCrypt. See `License.txt` in the repository root.

Portions of this product are based in part on TrueCrypt, freely available at http://www.truecrypt.org/
