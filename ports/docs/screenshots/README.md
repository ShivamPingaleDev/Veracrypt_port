# VC Port UI shots

Real emulator UI — **no passwords, no opened folders, no fake store mockups**. Not unbreakable.

| Platform | How captured |
| --- | --- |
| **Android** | Compose `captureToImage` on AVD `vcport-api35`. `FLAG_SECURE` stays on (`adb screencap` is black on purpose). |
| **iPhone** | `testPublishTabScreenshots` on iPhone Simulator — window draw, empty tabs only. |

**Refresh everything:**

```bash
ports/scripts/capture-screenshots.sh
```

Writes full PNGs here and smaller previews in `thumbs/` (for the GitHub README).

## Files

| File | Tab |
| --- | --- |
| `01-volume.png` / `ios-01-volume.png` | Volume |
| `03-create.png` / `ios-03-create.png` | Create |
| `05-mounted.png` / `ios-05-mounted.png` | Mounted |
| `04-tools.png` / `ios-04-tools.png` | Tools |
| `08-skin-signal.png` | Android dark mode |

Fastlane `phoneScreenshots/` stays empty until a physical phone capture exists.

Contact: Shivam Mangesh Pingale — shivampingaledev@proton.me · shivampingaledev@gmail.com
