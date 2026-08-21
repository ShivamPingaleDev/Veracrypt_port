# Feature backlog — privacy hardening vs what VC Port already has

Curated ideas for GrapheneOS / high-threat users. **VC Port** (`dev.shivampingale.vcport`), not VeraCrypt. Nothing here makes volumes “unbreakable.”

Legend: **Have** = shipped on `master` (both phones unless noted). **Partial** = exists but not as described. **Gap** = not built. **Good** = fits offline FOSS threat model. **Caution** = useful but trade-offs or conflicts.

---

## 1. Security & anti-forensics

| Idea | Status | Good? | Notes |
| --- | --- | --- | --- |
| **Inactivity / screen-off session timer** (dismount after 1/5/15 min idle or on screen lock) | **Gap** | **Good** | **Have:** `onStop` / Home calls `dismountOnLeave()` — closes mounted volume and wipes unlock fields; Create wizard is **kept** on purpose (Copy once → Notes). **No** idle timer, **no** screen-lock listener. **Should do:** optional Settings toggle: “Dismount open volumes when screen locks” / “after N minutes idle.” Keep Create-wizard-on-Home as default or a second toggle. iOS: `scenePhase` + `UIApplication.protectedDataWillBecomeUnavailable`. |
| **Quick Settings panic tile (Android)** | **Gap** | **Good** | **Have:** Panic wipe in Tools (`Hardening.panic` — cache, containers, clipboard, preview dir). **No** tile, shortcut, or `TileService`. **Should do:** `TileService` + documented launcher shortcut; must not require opening Compose first. Tests must still not tap Panic in CI unless a dedicated test is added. |
| **Metadata / timestamp scrub on export** | **Gap** | **Good (optional)** | **Have:** export/share writes decrypted bytes to cache then system share sheet; `Hardening.wipeFile` on session/panic. **No** EXIF strip, **no** host mtime/atime scrub. **Should do:** optional “Scrub metadata on export” (strip EXIF from JPEG, set neutral timestamps on temp file). Default off; document that share targets can still copy metadata themselves. |
| **Ephemeral cache enforcement** | **Partial** | **Good** | **Have:** preview under `cache/preview` (`InAppPreview.wipe()` on close/dismount/panic); share/inbox/containers wiped on panic; `wipeFile` zero-fill ≤64 MiB then delete; copy-to-device temps prefixed `to-device-` / `xfer-`. **No** `EncryptedFile` wrapper (Keystore-backed files are a different model). **Should do:** audit all export/import temps; wipe on **every** close, not only panic; document limits (very large files may skip full overwrite). |

---

## 2. Cryptographic & operational tooling

| Idea | Status | Good? | Notes |
| --- | --- | --- | --- |
| **PIM iteration calculator / estimator** | **Gap** | **Good** | **Have:** PIM fields everywhere; **Benchmark** runs encryption speed (Tools), not PBKDF2 iteration counts per KDF/PIM. **Should do:** read-only helper: “PIM 0 → N iterations for HMAC-SHA-512 / … / Argon2” using VeraCrypt’s own `GetIterationCount` / Argon2 params from pinned `src/`. Show expected unlock delay, not “more secure forever.” |
| **In-container file hashing (SHA-256 / SHA-512 / BLAKE2)** | **Partial** | **Good** | **Have:** basket SHA-256 session + `BASKET.sha256` written **into** new volumes; **Properties** shows name/size/FAT timestamp only. **No** per-file hash on Mounted tab before export. **Should do:** “Hash selected file” → compute inside volume via native read, show digest in status (never write hash to host disk unless user copies). |
| **Explicit read-only / write-protect mode** | **Have** | **Good** | **Have:** Read-only checkbox on Open (Android `read_only` tag, iOS `read_only`); native rejects mkdir/import/delete; session tests use it. **Gap:** no persistent **banner** while mounted read-only. **Should do:** small banner on Mounted tab when `readOnlyOpen` is true. |

---

## 3. Usability (without weakening security)

| Idea | Status | Good? | Notes |
| --- | --- | --- | --- |
| **Volume path bookmarks (paths only, no credentials)** | **Gap** | **Caution** | **Have:** container copied to `cache/containers/` for Open; **no** saved URIs. **Conflict:** `THREAT-MODEL.md` and workspace rules — **no persistable SAF grants**. Recent **display names** only (session or encrypted local list) may be OK; **takePersistableUriPermission** is not. **Should do:** if ever added, “recent container **labels**” + re-pick each time, or encrypted bookmark store with no passwords — not silent auto-open. |
| **Hardware entropy (accelerometer/gyro) for Create** | **Gap** | **Caution** | **Have:** touch-scribble pad → `vc_entropy_add`; system CSPRNG already mixed in native create path. **No** MotionManager / CoreMotion. Sensors need permission on some Android versions; GrapheneOS users may dislike motion access for a vault app. **Should do:** only if opt-in and disclosed; never replace scribble-only gate. |
| **Batch import/export queue with progress + ETA** | **Partial** | **Good** | **Have:** native `vc_progress_*` + Work overlay (percent + phase) for create, copy-into-volume, wipe free space, KDF; multi-select copy/move exists. **No** queued job list, **no** ETA, **no** survive rotation. **Should do:** single “Transfer queue” UI listing pending files; reuse existing progress hooks; stay in-process (no WorkManager network). |

---

## 4. Build & trust

| Idea | Status | Good? | Notes |
| --- | --- | --- | --- |
| **Reproducible build `verify-build.sh` (Docker Gradle → match release SHA-256)** | **Gap** | **Good** | **Have:** CI builds APK/IPA; host `run-all.sh`; upstream VeraCrypt has reproducible **desktop** packaging notes — **no** phone reproducible recipe in this repo. **Should do:** pinned NDK + JDK Docker image, `assembleFossRelease`, compare APK hash to GitHub Release artifact; document iOS unsigned IPA limits (Xcode version affects binary). Hard but high trust value for GrapheneOS crowd. |

---

## What is already strong (do not regress)

- **Panic wipe** + **Dismount on Home** (`dismountOnLeave`) + **lockSession** (full wipe including Create secrets)
- **FLAG_SECURE**, no INTERNET (master), 30s clipboard, no autofill history
- **Read-only open**, hidden-volume protection, header backup/restore, KDF change, keyfiles
- **Container cache copy** (no `/proc/self/fd` native paths)
- **In-app preview** (experimental branch): stays in-process; preview cache wiped on close/panic
- **Cross-device `.hc` compatibility** (desktop ↔ Android ↔ iOS), disguise filenames

---

## Suggested priority (if building next)

1. **Anti-forensic session controls** — screen-lock dismount + optional idle timer (biggest real-world gap vs “walked away unlocked”).
2. **Android panic Quick Settings tile** — low UI cost, high seizure scenario value.
3. **Read-only banner** + **PIM estimator** — small, honest crypto UX.
4. **In-container hash before export** — fits Mounted tab without new permissions.
5. **Reproducible APK verify script** — trust, not end-user feature.
6. **Defer or reject:** persistable SAF bookmarks; motion entropy unless explicitly opt-in.

---

## Free app, optional support

VC Port stays free and FOSS. Optional support links are on GitHub ([SUPPORT.md](SUPPORT.md)) — not in the app. No paid features.
