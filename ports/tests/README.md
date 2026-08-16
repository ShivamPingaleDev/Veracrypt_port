# VC Port host tests

These tests exist because there is no device farm. They run on a remote Mac or
Linux box, and in GitHub Actions, without a phone, an iOS simulator, or a
FUSE-T mount.

```
ports/tests/run-phases.sh   # 10-phase pass (honesty → wrap/volume → folders → Android → iOS → desktop → manifest → CI → 0.3.0 → public tag)
ports/tests/run-all.sh      # same runner; used by GitHub Actions
```

## What is covered

| Surface | Host coverage |
| --- | --- |
| Shared wrap (`.vcpw`) | Argon2id 32 MiB, AES-CTR roundtrip, wrong password, tamper, 0600 mode, path sanitization, password generator. Tools menu on desktop GUI. |
| Android F-Droid + GitHub | Version pin, wrap, panic, share, stay offline, no INTERNET in main/F-Droid, GitHub opt-in INTERNET, FLAG_SECURE, backup exclude |
| iOS | Version pin, wrap, panic, share, stay offline, `UIFileSharingEnabled=false` |
| macOS desktop | `StayOffline`, wrap/unwrap/share/panic in Tools, Touch ID warning, FUSE-T does not force `backend=smb` |
| Factor mix | VCF2 encode/decode spec shared by Kotlin and Swift |
| Overlay | CMake vs Crypto/Volume layout; inventories vs pin when git history is complete |

## What still needs hardware

- Opening a real VeraCrypt volume on Android/iOS
- Biometric prompt, StrongBox, Face ID / Touch ID
- FUSE-T mount / hdiutil attach on macOS
- SAF / share-sheet roundtrip on a phone

Those are listed so a later device test does not get skipped by accident.
