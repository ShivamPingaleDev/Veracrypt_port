# Keeping up with VeraCrypt source updates

This port is a **thin overlay** on [veracrypt/VeraCrypt](https://github.com/veracrypt/VeraCrypt), not a fork that rewrites `src/`. Mobile code lives in `ports/` and compiles VeraCrypt units by path (`VC_SRC`). Desktop Apple-silicon work is a small set of new files plus short hunks in existing VeraCrypt files.

```
Layer 2  ports/          VC Port apps, wrap, FOSS, overlay tooling
Layer 1  src/ (owned)    New files: MacOSXAuthorization, MacOSXBiometric, OfflineUpdate, PortVersion
Layer 1  src/ (patched)  Short hunks in CoreService, FuseService, MainFrame, …
Layer 0  src/            Unmodified VeraCrypt (crypto, volume, Windows, Linux, …)
```

Pin: `ports/UPSTREAM_COMMIT` (currently VeraCrypt 1.26.29 / `b48e31f5…`).

## Do not

- Copy a whole VeraCrypt tree into `ports/`
- Restore **patched** files from a pre-merge backup (that drops VeraCrypt’s own edits in the same file)
- Add Play / GMS / obfuscation
- Name the app VeraCrypt

## Update procedure

```bash
scripts/sync-upstream.sh --check   # fetch, then drop the network; exit 2 if they moved
scripts/sync-upstream.sh           # 3-way merge; restore owned files only
scripts/refresh-overlay.sh         # rewrite owned/patched lists + src-port.patch
scripts/check-upstream-layout.sh   # missing cmake units, or new Crypto/Volume files
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

`ports/` itself is not in upstream. A merge never replaces it.

## Where to put new work

| Kind of change | Put it |
| --- | --- |
| Android / iOS UI, wrap, JNI, F-Droid | `ports/` |
| New macOS-only helper | new file under `src/…`, add to `owned.txt` via refresh |
| Must touch VeraCrypt UI/core | smallest hunk, then `refresh-overlay.sh` |
| New cipher VeraCrypt added | `upstream-sources.cmake` if mobile needs it |
