# Contributing

1. Keep the apps offline by default. Do not add Google Play services, Firebase, ads, analytics, or crash reporters.
2. Android store builds use the `fdroid` product flavor (`./gradlew :app:assembleFdroidRelease`).
3. New native code must build with the NDK from CMake. Do not commit prebuilt `.so` / `.a` blobs.
4. Do not name the app VeraCrypt or anything confusingly similar.
6. Put Android/iOS/wrap work in `ports/`. Do not copy the VeraCrypt tree into `ports/`.
7. After editing `src/` hunks, run `scripts/refresh-overlay.sh`. After a VeraCrypt merge, run `scripts/check-upstream-layout.sh`. See [UPSTREAM.md](UPSTREAM.md).
8. Run `ports/tests/run-all.sh` before a release. That suite is the remote stand-in for phones and FUSE-T (wrap/crypto, version pins, FOSS/high-threat contracts). It does not replace a real-device volume mount.
