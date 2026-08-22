# Privacy

VC Port does not collect personal data.

- No accounts, ads, analytics, crash reporters, or trackers
- Volume passwords and wrap passwords stay on the device
- Android backups of app data are disabled
- Every Android flavor on master has no `INTERNET` permission
- The iOS build does not contact the network (`VCPortEnableUpdateCheck` is false)
- Screenshots and Recents thumbnails are blocked (`FLAG_SECURE`). The app still appears in Recents as a blank card so you can paste Copy once elsewhere and come back.
- Android cloud backup and device-to-device transfer of app data are excluded

High-threat / seizure profile: [THREAT-MODEL.md](THREAT-MODEL.md).

Master phone apps do not fetch `version.json`. This branch also stays offline (`ENABLE_UPDATE_CHECK` is false). `experimental-biometrics` is **stale**.
