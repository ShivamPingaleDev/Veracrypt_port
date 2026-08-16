# VC Port for iOS

The iOS client is a SwiftUI app (`VCPort`) that talks to the shared VeraCrypt volume core in `ports/shared`.

## Create the Xcode project

1. Open Xcode and create an iOS App named `VCPort` with SwiftUI, bundle id `dev.shivampingale.vcport`.
2. Add every file under `ports/ios/VCPort/` to the target.
3. Set the Objective-C bridging header to `VCPort/VCPort-Bridging-Header.h`.
4. Add a CMake (External Build) target, or add `ports/shared` as an Xcode CMake package:
   ```
   cmake -G Xcode -DCMAKE_SYSTEM_NAME=iOS -DCMAKE_OSX_ARCHITECTURES=arm64 ../../shared
   ```
   Link `libvc_mobile.a` into the app.
5. Add `NSFaceIDUsageDescription` to Info.plist:
   `Face ID unlocks stored VeraCrypt volume passwords on this device.`
6. Enable Face ID / Keychain Sharing for the app target.

The File Provider extension should use the same `vc_mobile` library so Files.app can browse an unlocked container. The first release lists the FAT root from the in-app browser.

License: this derived work is not named VeraCrypt. See `License.txt` in the repository root.
