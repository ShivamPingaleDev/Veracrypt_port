# Keeping up with VeraCrypt source updates

This port is a **thin overlay** on official VeraCrypt. The phone apps never
download their tree, never patch themselves, and never install an APK.

When the VeraCrypt team **publishes a release**, a human merges that git into
this repo and ships a **new VC Port build**. The published endpoints are
hardcoded in `ports/version.json` (copied into the apps at compile time):

| Field | Hardcoded value |
| --- | --- |
| `upstream_git` | `https://github.com/veracrypt/VeraCrypt.git` |
| `upstream_releases` | `https://api.github.com/repos/veracrypt/VeraCrypt/releases/latest` |
| `upstream_tag` | e.g. `VeraCrypt_1.26.29` |
| `upstream_version` | e.g. `1.26.29` |
| `upstream_commit` | 40-char sha of that tag (`ports/UPSTREAM_COMMIT` must match) |

```
Layer 2  ports/          VC Port apps, wrap, FOSS, overlay tooling
Layer 1  src/ (owned)    New files: MacOSXAuthorization, MacOSXBiometric, OfflineUpdate, PortVersion
Layer 1  src/ (patched)  Short hunks in CoreService, FuseService, MainFrame, …
Layer 0  src/            Unmodified VeraCrypt (crypto, volume, Windows, Linux, …)
```

Pin today: VeraCrypt 1.26.29 / `b48e31f5…` (see `ports/UPSTREAM_COMMIT`).

## What the apps do

| Build | Network | Updates |
| --- | --- | --- |
| Android (all flavors on master) | none | Refuses. A newer app is a git rebuild you sign. |
| iOS (master) | none | Refuses. A newer IPA is a rebuild you sign. |
| Desktop (StayOffline off) | one or two HTTPS GETs from Help → Check for updates | Reads **our** `version.json`, then the **official** GitHub latest release. Shows versions and SHA-256. **Does not install.** |
| `experimental-biometrics` (Android GitHub / Looks GitHub / iOS opt-in) | one or two HTTPS GETs you tap for | Same live Check for updates as before: `version.json` + official latest. **Does not install.** |

If official VeraCrypt is newer than the baked-in pin, rebuild from source after `scripts/sync-upstream.sh`. The APK cannot rewrite `src/`. Live phone Check for updates is not on master.

Weekly CI (`upstream-overlay.yml`) runs `ports/scripts/check_veracrypt_release.py`. Exit 2 means they published; merge, do not hot-patch a binary.

## Do not

- Copy a whole VeraCrypt tree into `ports/`
- Restore **patched** files from a pre-merge backup (that drops VeraCrypt’s own edits in the same file)
- Add Play / GMS / obfuscation
- Name the app VeraCrypt
- Auto-install or silently fetch `src/` onto a phone

## Update procedure (maintainer, when they publish)

```bash
python3 ports/scripts/check_veracrypt_release.py          # HTTPS; exit 2 if they published
scripts/sync-upstream.sh --check   # fetch, then drop the network; exit 2 if git moved
scripts/sync-upstream.sh           # 3-way merge; restore owned files only
scripts/refresh-overlay.sh         # rewrite owned/patched lists + src-port.patch
scripts/check-upstream-layout.sh   # missing cmake units, or new Crypto/Volume files
python3 ports/scripts/sync_source_pin.py --write
# set upstream_tag / upstream_version in ports/version.json if the tag name changed
# resolve any conflicts in patched files using ports/overlay/src-port.patch as a hint
git diff
git commit
```

If `check-upstream-layout.sh` prints `NEW upstream source not in mobile cmake`, open `ports/shared/upstream-sources.cmake` and either add the file or document a skip in `scripts/check-upstream-layout.sh`.

## Inventories

| File | Role |
| --- | --- |
| `ports/overlay/owned.txt` | Files VeraCrypt does not have. Restore after merge. |
| `ports/overlay/patched.txt` | VeraCrypt files we edit. 3-way merge only. |
| `ports/overlay/src-port.patch` | Snapshot of those hunks vs the pin |
| `ports/overlay/mobile-skip.txt` | Crypto/Volume files we intentionally do not compile |
| `ports/shared/upstream-sources.cmake` | Exact `.c`/`.cpp` mobile compiles |
| `ports/scripts/check_veracrypt_release.py` | Compare pin to official GitHub latest release |
| `src/Main/PortVersion.h` | Compile-time copies of the same pin (desktop) |

`ports/` itself is not in upstream. A merge never replaces it.

## Where to put new work

| Kind of change | Put it |
| --- | --- |
| Android / iOS UI, wrap, JNI, FOSS flavor | `ports/` |
| New macOS-only helper | new file under `src/…`, add to `owned.txt` via refresh |
| Must touch VeraCrypt UI/core | smallest hunk, then `refresh-overlay.sh` |
| New cipher VeraCrypt added | `upstream-sources.cmake` if mobile needs it |
