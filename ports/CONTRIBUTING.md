# Contributing

**Footnote:** A programming noob still doing a five-year IT engineering degree (graduate summer 2027). Just trying to make something better that he likes to use, without much knowledge. Open to suggestions and advice.

1. Keep the apps offline by default. Do not add Google Play services, Firebase, ads, analytics, or crash reporters.
2. Android store builds use the `fdroid` product flavor (`./gradlew :app:assembleFdroidRelease`). Looks APKs (`assembleStyledRelease`, `assembleLooksgithubRelease`) are GitHub Release previews with the same `applicationId`, not a separate package and not the F-Droid app. Both are offline on master.
3. New native code must build with the NDK from CMake. Do not commit prebuilt `.so` / `.a` blobs.
4. Do not name the app VeraCrypt or anything confusingly similar.
5. Report security issues as described in [SECURITY.md](../SECURITY.md). Do not add Play Integrity, obfuscation, or an open-time hidden-volume checkbox.
6. Put Android/iOS/wrap work in `ports/`. Do not copy the VeraCrypt tree into `ports/`.
7. After editing `src/` hunks, run `scripts/refresh-overlay.sh`. After a VeraCrypt merge, run `scripts/check-upstream-layout.sh`. See [UPSTREAM.md](UPSTREAM.md).
8. Run `ports/tests/run-all.sh` before a release. See [tests/TESTING.md](tests/TESTING.md). That suite does not replace a real-device volume mount.
9. Do not open drive-by pull requests on official VeraCrypt (`veracrypt/VeraCrypt`) to burn model quota. If an overlay fix belongs upstream, send one small human-reviewed patch (the macOS `File.cpp` `sys/disk.h` include is the current candidate).
