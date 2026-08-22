# UI walk (one command)

Local phones together:

```
ports/scripts/run-ui-walk.sh
```

`SLOW=1` also scribbles the Android entropy pad (`SlowHumanSessionTest`). Does not tap Panic wipe or Check for updates.

This is **not** GitHub Actions (no emulator there). CI builds APK/IPA and runs **host Python contracts** only. Run this walk on this Mac after each new feature.

Android walk boots AVD `vcport-api35` itself when `adb` is empty: Java 17 from `JAVA_HOME`, `/usr/libexec/java_home`, or Homebrew `openjdk@17`; headless SwiftShader + `nohup` so qemu is not killed when the launching shell exits. After boot it keeps the AVD awake (`svc power stayon`). If the device is gone before Gradle, the walk starts the emulator again and retries once on “no connected devices.” Waits for `adb` `device` + `sys.boot_completed`, not the emulator launcher PID. If qemu for that AVD is already running, the walk waits instead of starting a second emulator. `VC_PORT_EMU_WINDOW=1` keeps a window. Do not treat a missing emulator as SKIP — the walk fails with the qemu log.

## 10 phases (session test)

1. Basket + Create (cipher/KDF/PIM/disguise)
2. Nested volume with **adjustable Nested size**
3. Save wipes secrets
4. Open / fill folders
5. Home leave + reopen
6. Several mounts + Copy/Move to volume
7. Hidden-volume files
8. Header backup / restore / KDF / keyfiles
9. Read-only open + wipe refused + **read-only banner**
10. SHA-256 in volume, PIM estimate, idle timeout (hook; does not wait a real minute)

## What is in the walk

| Phone | Always | On master |
| --- | --- | --- |
| Android | 10-phase `AppInterfaceSessionTest` | Fake USB Open + View in app; in-app preview kinds/text |
| iOS | 10-phase `AppInterfaceSessionTests` | No whole-disk USB + file-container View in app; preview kinds/text |

Fake USB is an injected MBR file on the emulator, not a physical stick. iOS never compiles whole-disk slots (`-DVC_PORT_OTG=OFF`).

## Whole-disk USB (do not mix this up)

| Place | Whole-disk USB Open |
| --- | --- |
| **master** Android | Yes (no auto-mount) |
| **master** iOS | No |
| **experimental-otg-master** | Ignored for freeze; same USB code as master. Do not add commits. |
| macOS in this repo | No Mac app. Host `vc_otg_usb_test` is a file-backed simulator, not a real stick mount |

## Last local proof (2026-08-23, this Mac, freeze tree on master)

| Phone | Result |
| --- | --- |
| Android `vcport-api35` `UiWalkSuite` | **PASS** — session 3m32s, Fake USB 20.4s, in-app preview 0.4s |
| iOS Simulator | **PASS** — 5 tests, 65.5s (`AppInterfaceSessionTests`, `InAppPreviewTests`, `OtgAbsentAndPreviewTests`) |

Host Python contracts: **154 tests OK**. Copy once no longer blocks Create on the emulator clipboard. Panic wipe is a top-bar warning icon (`panic_wipe`). Fake USB waits/scrolls to `scan_usb`.

**master** `26b61341` (2026-08-21, 9-step only; no View in app, no whole-disk USB)

| Phone | Result |
| --- | --- |
| Android `vcport-api35` | PASS `AppInterfaceSessionTest` (~105s on device) |
| iOS iPhone 17 Pro sim | PASS `AppInterfaceSessionTests` (45.2s) |

**experimental-otg-master** `db1d1d8a` plus UI-walk hook (ignored for freeze)

| Phone | Result |
| --- | --- |
| Android | PASS 9-step (101.6s) + FakeUsbUiTest (15.8s) |
| iOS | PASS 9-step (36.1s) + InAppPreviewTests + OtgAbsentAndPreviewTests (8.7s) |
