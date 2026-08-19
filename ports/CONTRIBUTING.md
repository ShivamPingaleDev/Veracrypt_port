# Contributing

**Footnote:** A programming noob still doing a five-year IT engineering degree (graduate summer 2027). Just trying to make something better that he likes to use, without much knowledge. Open to suggestions and advice.

1. Keep the apps offline by default. Do not add Google Play services, Firebase, ads, analytics, or crash reporters.
2. Android production builds use the `foss` product flavor (`./gradlew :app:assembleFossRelease`). The `github` flavor is the same app id, also offline on master.
3. New native code must build with the NDK from CMake. Do not commit prebuilt `.so` / `.a` blobs.
4. Do not name the app VeraCrypt or anything confusingly similar.
5. Report security issues as described in [SECURITY.md](../SECURITY.md). Do not add Play Integrity, obfuscation, or an open-time hidden-volume checkbox.
6. Put Android/iOS/wrap work in `ports/`. Do not copy the VeraCrypt tree into `ports/`.
7. Do not edit official `src/` for phones. Put replacements in `ports/overlay/src/` and run `scripts/refresh-overlay.sh`. After a VeraCrypt merge, run `scripts/check-upstream-layout.sh`. See [UPSTREAM.md](UPSTREAM.md).
8. Run `ports/tests/run-all.sh` before a release. See [tests/TESTING.md](tests/TESTING.md). That suite does not replace a real-device volume mount.
9. Do not open drive-by pull requests on official VeraCrypt (`veracrypt/VeraCrypt`) to burn model quota. If an overlay fix belongs upstream, send one small human-reviewed patch (the macOS `File.cpp` `sys/disk.h` include is the current candidate).
10. The volume core stays under TrueCrypt License 3.0 **and** Apache-2.0. That is inherited from VeraCrypt, not a product choice of abandoned TrueCrypt. Do not try to relicense the core to drop TrueCrypt 3.0 and keep `.hc` compatibility. TrueCrypt 3.0 is not OSI / Debian-free; say so if you package this.
11. Host check before a PR: `python3 -m unittest ports.tests.test_contracts ports.tests.test_phases`. Small patches get a human reply. Open with one file when you can.
