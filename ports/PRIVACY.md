# Privacy

VC Port does not collect personal data.

- No accounts, ads, analytics, crash reporters, or trackers
- Volume passwords and wrap passwords stay on the device
- Biometric unlock uses the platform keystore / Secure Enclave
- Android backups of app data are disabled
- The F-Droid flavor has no `INTERNET` permission
- The default iOS build does not contact the network (`VCPortEnableUpdateCheck` is false)
- Screenshots and recents are blocked (`FLAG_SECURE` / privacy-sensitive fields)
- Android cloud backup and device-to-device transfer of app data are excluded
- Optional GitHub update check uses **system CAs only** (no user-installed CAs)

High-threat / seizure profile: [THREAT-MODEL.md](THREAT-MODEL.md).

The optional GitHub Android flavor may make **one HTTPS GET** of `version.json` if you tap **Check for updates**. Nothing else is sent.
