# VC Port UI shots

Android PNGs are **real** production debug UI from emulator AVD `vcport-api35`, captured with Compose `captureToImage` while **FLAG_SECURE stays on**. `adb screencap` is black on purpose (seizure / recents).

iPhone PNGs are **real** Debug UI from iPad Simulator, captured from the running app window. Empty tabs only.

They are not store mockups. Fastlane `phoneScreenshots/` stays empty until a physical phone capture exists.

No volume password, generated secret, or opened-folder listing is in these frames. Not unbreakable.

Android: Volume (`01-volume.png`), Create (`03-create.png`), Tools (`04-tools.png`), Mounted (`05-mounted.png`), Dark mode (`08-skin-signal.png`). iPhone: `ios-01-volume.png`, `ios-03-create.png`, `ios-04-tools.png`, `ios-05-mounted.png`. The wrap UI is gone; there is no wrap screenshot. The pick is stored on this phone only. Cyberpunk / Matrix / MAGI shots live under `archive/looks/screenshots/` and are not built.

Contact: Shivam Mangesh Pingale — shivampingaledev@proton.me · shivampingaledev@gmail.com
