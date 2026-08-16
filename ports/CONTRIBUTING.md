# Contributing

1. Keep the apps offline by default. Do not add Google Play services, Firebase, ads, analytics, or crash reporters.
2. Android store builds use the `fdroid` product flavor (`./gradlew :app:assembleFdroidRelease`).
3. New native code must build with the NDK from CMake. Do not commit prebuilt `.so` / `.a` blobs.
4. Do not name the app VeraCrypt or anything confusingly similar.
5. Keep the TrueCrypt attribution visible in the About UI and in `NOTICE`.
