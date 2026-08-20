# UI walk (one command)

Local phones together:

```
ports/scripts/run-ui-walk.sh
```

`SLOW=1` also scribbles the Android entropy pad (`SlowHumanSessionTest`). Does not tap Panic wipe or Check for updates.

This is **not** GitHub Actions. CI still builds APK/IPA and runs host tests only. Emulator UI stays on this Mac so a push does not burn runner minutes.

## What is in the walk

| Phone | Always | This branch (`experimental-otg-master`) |
| --- | --- | --- |
| Android | 9-step `AppInterfaceSessionTest` | Fake USB Open + View in app; in-app preview kinds/text |
| iOS | 9-step `AppInterfaceSessionTests` | No whole-disk USB + file-container View in app; preview kinds/text |

Fake USB is an injected MBR file on the emulator, not a physical stick. iOS never compiles whole-disk slots (`-DVC_PORT_OTG=OFF`).

## Whole-disk USB (do not mix this up)

| Place | Whole-disk USB Open |
| --- | --- |
| **master** Android | No |
| **master** iOS | No |
| **experimental-otg-master** Android | Yes (no auto-mount) |
| **experimental-otg-master** iOS | No |
| macOS in this repo | No Mac app. Host `vc_otg_usb_test` is a file-backed simulator, not a real stick mount |

## Last local proof (2026-08-21, this Mac)

**master** `26b61341` (9-step only; no View in app, no whole-disk USB)

| Phone | Result |
| --- | --- |
| Android `vcport-api35` | PASS `AppInterfaceSessionTest` (~105s on device) |
| iOS iPhone 17 Pro sim | PASS `AppInterfaceSessionTests` (45.2s) |

**experimental-otg-master** `db1d1d8a` plus this UI-walk hook commit

| Phone | Result |
| --- | --- |
| Android | PASS 9-step (101.6s) + FakeUsbUiTest (15.8s) |
| iOS | PASS 9-step (36.1s) + InAppPreviewTests + OtgAbsentAndPreviewTests (8.7s) |

The first combined iOS run on this branch failed because the session test left a volume mounted. `OtgAbsentAndPreviewTests` now calls `lockSession()` first. Combined re-run passed.
