# Privacy

VC Port does not collect personal data.

- No accounts, ads, analytics, crash reporters, or trackers
- Volume passwords and wrap passwords stay on the device
- Biometric unlock uses the platform keystore / Secure Enclave
- Android backups of app data are disabled
- The F-Droid flavor has no `INTERNET` permission
- The default iOS build does not contact the network (`VCPortEnableUpdateCheck` is false)
- Screenshots and Recents thumbnails are blocked (`FLAG_SECURE`). The app still appears in Recents as a blank card so you can paste Copy once elsewhere and come back.
- Android cloud backup and device-to-device transfer of app data are excluded
- Optional GitHub update check uses **system CAs only** (no user-installed CAs)

High-threat / seizure profile: [THREAT-MODEL.md](THREAT-MODEL.md).

The optional GitHub Android flavor and Looks GitHub flavor may make **one HTTPS GET** of `version.json` if you tap **Check for updates**. Nothing else is sent.
