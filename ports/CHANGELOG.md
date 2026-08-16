# VC Port changelog

## 0.2.1

High-threat hardening. Not unbreakable.

- Always-on screenshot block, recents hidden, no cloud/D2D backup
- Panic wipe and background session lock
- No exported DocumentsProvider; FileProvider no longer exposes all of cache
- System TLS CAs only; StrongBox when present
- Wrap: Argon2id 32 MiB, mode 0600, mlock keys
- Compelled-biometrics warning; FOSS profile in THREAT-MODEL.md

## 0.2.0

First tagged GitHub release.

- F-Droid flavor with no `INTERNET` permission; GitHub flavor with tap-to-check updates only
- Unlock mix: text password, PIM, keyfiles, and a biometric/device secret mixed as a VeraCrypt keyfile
- Privacy Manifest, encryption export flag, and AltStore source stub on iOS
- Fastlane metadata and an fdroiddata recipe
- Portable wrap/unwrap host test for CI

APKs from GitHub Actions are **debug-signed previews**. F-Droid (or your own keystore) must sign production builds.

## 0.1.0

Initial VC Port work on `master` (untagged): Apple silicon FUSE-T, Touch ID, native admin auth, Android/iOS clients, wrap helper.
