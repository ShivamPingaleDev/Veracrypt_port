# Inspiration for future VC Port features

Ideas borrowed from two mature phone encryption apps, **filtered for VC Port’s use case**:

- Same **VeraCrypt** containers as desktop (`.hc`, etc.)
- **Offline** FOSS builds (no `INTERNET` on `master`)
- Inner filesystem is **FAT / exFAT** today (not ext4, NTFS, APFS)
- **High-threat** posture: no biometrics-as-password, no cloud backends, no “open in Files” without understanding temp plaintext

**Sources (external, not affiliated):**

| Project | Link | Role |
| --- | --- | --- |
| **Arcanum** | [github.com/Esdex/Arcanum](https://github.com/Esdex/Arcanum) | Android VeraCrypt vault manager (F-Droid) |
| **Disk Decipher** | [disk-decipher.app](https://disk-decipher.app/) | iOS / macOS encrypted disk images (VeraCrypt, LUKS, DMG, …) |

Shipped backlog and freeze rules live in [FEATURE-BACKLOG.md](FEATURE-BACKLOG.md). This file is **research notes** — not a commitment.

---

## How to read the tables

| Label | Meaning |
| --- | --- |
| **Borrow** | Fits VC Port threat model and VeraCrypt-only scope; worth designing |
| **Consider** | Useful but needs a narrow design or threat-model exception |
| **Skip** | Wrong product (other formats, cloud, or conflicts with our security story) |

---

## 1. Transfer queue (top priority)

Both apps do one file at a time through the UI today; users still ask for **batch copy/move** with visible progress. Arcanum’s file manager and Disk Decipher’s drag-and-drop are the UX reference; neither ships a full job queue yet — this is a **gap we can lead on**.

| Idea | From | VC Port fit | Notes |
| --- | --- | --- | --- |
| **Batch queue** — import / export / copy-to-volume / copy-to-device as a **job list** | User need + Arcanum file ops | **Borrow** | Already on backlog. Reuse `WorkOverlay` + native progress hooks; one worker thread; cancel single job or whole queue |
| **Per-job ETA** — bytes done / total, phase label (“Decrypting”, “Writing to device”) | Arcanum progress patterns | **Borrow** | Show ETA only when total size is known; never claim crack-time for passwords |
| **Queue from multi-select** — select N files in Mounted → one “Copy to device” action | Disk Decipher multi-file mindset | **Borrow** | Same queue UI for “Add files to basket” on Create |
| **Retry / skip failed** — one bad file does not abort the whole batch | Common backup-app pattern | **Consider** | Log failure reason (SAF denied, no space); offer “retry” |
| **Pause when screen locks** | Arcanum auto-unmount | **Consider** | Align with idle dismount: pause queue, resume after re-open (or cancel on panic) |

**Design sketch**

```text
Mounted → Select files → Copy to device
  → Queue sheet: [ job1 45% ] [ job2 waiting ] [ job3 waiting ]
  → WorkOverlay on active job; list stays scrollable
  → On panic / dismount: cancel all, wipe temps (existing closeOpenVolumes path)
```

**Do not:** silent background sync, WorkManager jobs that survive process death with plaintext paths, or parallel encrypt writers on the same volume.

---

## 2. In-vault browsing and media

| Idea | From | VC Port fit | Notes |
| --- | --- | --- | --- |
| **In-app preview** — images, PDF, plain text inside the volume | Both | **Built** (preview path); extend formats slowly |
| **Thumbnail / tiled directory** — small previews for images/PDF in the file list | Disk Decipher | **Consider** | Cache thumbs in session cache only; wipe on dismount |
| **In-app gallery + fullscreen** | Arcanum | **Consider** | Good for photo-heavy volumes; keep FLAG_SECURE |
| **Audio / video playback** (stream from container, background audio) | Arcanum, Disk Decipher | **Consider** | Higher effort; temp decrypt buffer policy must match threat model |
| **Favorites / pinned paths** per mounted slot | Disk Decipher UX | **Consider** | Session-only or encrypted prefs — never store paths in plain Room like Arcanum metadata |
| **Rename / delete / new folder** in volume | Arcanum file manager | **Partial** | Copy/move exists; expand mutating ops with same progress UI |

---

## 3. Import / export hygiene

| Idea | From | VC Port fit | Notes |
| --- | --- | --- | --- |
| **Metadata scrub on export** — strip EXIF, timestamps on *copies* leaving the volume | Arcanum (`metadata-extractor`) | **Borrow** (later) | Default **off**; explicit toggle; only on export-to-device, never on container |
| **Encrypted ZIP export** of selected files | Disk Decipher | **Skip** | Different format; not VeraCrypt-compatible |
| **Basket SHA-256 manifest** | VC Port | **Built** (`BASKET.sha256`) | Extend to queue completion report |
| **Share sheet: send encrypted container as-is** | Both | **Built** (“Share encrypted”) | Keep decrypted share behind mount + warning |

---

## 4. Container handling (without new formats)

| Idea | From | VC Port fit | Notes |
| --- | --- | --- | --- |
| **Linked / non-copy open** — open container where it lives (SAF URI → cache copy today) | Disk Decipher “linked disk” | **Consider** | We already **must** copy to cache (`ensureContainerPath`); document why; optional “refresh from source” before open |
| **Storage usage per volume** — bytes used / free inside FAT | Arcanum | **Borrow** | Read-only stat from mounted FS |
| **Move vault** app storage ↔ OTG / SD | Arcanum | **Partial** | Android OTG experimental; improve error copy and SAF tree export |
| **Create: ext4 inside container** | Arcanum clean-room ext4 | **Consider** (long) | Linux-origin volumes; big FS effort; exFAT better for cross-device **new** volumes |
| **LUKS / DMG / ProxyCrypt / sparsebundle** | Disk Decipher | **Skip** | Not VeraCrypt; different codebases |
| **Remote disk providers** (WebDAV, S3, Dropbox, …) | Disk Decipher | **Skip** | Needs network + cloud; off `master` threat model |
| **FIDO2 / smart-card keyfiles** | Disk Decipher | **Skip** (for now) | Niche; hardware stack |

---

## 5. Mount session and multi-volume

| Idea | From | VC Port fit | Notes |
| --- | --- | --- | --- |
| **8 mount slots, session-only** | VC Port | **Built** | Arcanum multi-vault; Disk Decipher multi-disk |
| **Read-only banner** | VC Port | **Built** | Keep prominent on Mounted |
| **Per-slot label** (user nickname for container) | Arcanum | **Consider** | Session-only unless encrypted prefs |
| **Open default tab** per workflow | Arcanum | **Consider** | Low cost; accessibility |
| **Quick re-open last container** (password still required) | Common pattern | **Skip** | Conflicts with “no remembered volume password” on master |

---

## 6. File Provider / “see volume in Files app”

| Idea | From | VC Port fit | Notes |
| --- | --- | --- | --- |
| **File Provider extension** — mounted volume visible in iOS Files / Finder | Disk Decipher 6.0 | **Consider** (high risk) | Decrypted temps on host; extension lifetime unpredictable on iOS; [their own docs](https://disk-decipher.app/file-provider/) warn about plaintext until unmount |
| **Read-only SAF picker while mounted** | Arcanum | **Consider** | Smaller surface than full Provider; still URI grants |
| **DocumentsProvider for mounted volume** | Android pattern | **Skip** on master | [THREAT-MODEL.md](../THREAT-MODEL.md) — no exported DocumentsProvider on high-threat profile |

**If ever built:** read-only default, no persistable grants, revoke on dismount, secure-delete temps, user must read a one-screen warning.

---

## 7. Security and privacy — what **not** to copy

Arcanum is strong in product security; several features are **wrong for VC Port master**:

| Idea | From | VC Port stance |
| --- | --- | --- |
| **Biometric unlock** (password stored behind fingerprint) | Both | **Skip** on master — compelled biometrics; see [THREAT-MODEL.md](../THREAT-MODEL.md) |
| **App-level PIN / Argon2id app gate** | Arcanum | **Skip** — separate from VeraCrypt password; adds duress surface |
| **Panic PIN** (fake PIN → wipe) | Arcanum | **Skip** — we have **Panic wipe** button + QS tile; no decoy PIN |
| **Calculator disguise / second launcher** | Arcanum | **Skip** — different threat model |
| **Remember volume password in Keystore** | Arcanum biometrics path | **Skip** on master |
| **Screen capture toggle** (user disables FLAG_SECURE) | Arcanum | **Skip** — FLAG_SECURE stays on in UI tests and production story |

**Already aligned with Arcanum:** no network permission, panic wipe, idle / lock dismount, read-only mount, hidden volume support via password (no open-time checkbox).

---

## 8. Platform and polish

| Idea | From | VC Port fit | Notes |
| --- | --- | --- | --- |
| **Localization** (many languages) | Both | **Consider** | Strings only; no network |
| **Dynamic Color / Material You** | Arcanum | **Consider** | Alongside Original + Dark mode |
| **macOS / visionOS** | Disk Decipher | **Skip** | Phone port scope |
| **Launch URL / open container from another app** | Disk Decipher | **Consider** | `content://` / SAF only; no http handler |
| **Share extension** (files into basket) | Arcanum | **Consider** | Android `ACTION_SEND`; iOS share extension — copy to cache first |
| **Drag and drop** (desktop / iPad) | Disk Decipher | **Consider** | iPad multitasking; queue integration |

---

## 9. Suggested implementation order (post-freeze)

When the **0.3.x freeze** lifts for user-facing volume features:

1. **Transfer queue** + multi-select → queue (Android + iOS parity)
2. **Storage usage** + clearer OTG / SAF space errors (extend existing copy)
3. **Metadata scrub** on export (opt-in)
4. **Thumbnails / tiled list** (session cache, wiped on close)
5. **Share extension** → basket / import queue
6. Long horizon: **ext4 read** inside container **or** document exFAT-only for Linux volumes

---

## 10. Quick comparison (our use case only)

| Capability | VC Port 0.3.12 | Arcanum | Disk Decipher |
| --- | --- | --- | --- |
| VeraCrypt open/create | Yes | Yes | Yes |
| FAT/exFAT in container | Yes | Yes | Yes (+ paid ext/NTFS/APFS) |
| ext4 in container | No | Yes (read/write) | Yes (read) |
| LUKS / DMG | No | No | Yes |
| Cloud remote images | No | No | Yes |
| Batch transfer queue | No (planned) | No | No |
| File Provider | No | Partial | Yes |
| Biometrics | No (master) | Yes | Yes |
| Panic wipe | Yes | Yes (panic PIN) | Passcode app lock |
| Offline FOSS build | Yes | Yes (F-Droid) | No (App Store) |
| Android OTG | Experimental | Next release | N/A (iOS limits) |

---

## References

- Arcanum README: [github.com/Esdex/Arcanum](https://github.com/Esdex/Arcanum)
- Disk Decipher overview: [disk-decipher.app](https://disk-decipher.app/)
- Disk Decipher File Provider: [disk-decipher.app/file-provider/](https://disk-decipher.app/file-provider/)
- VC Port backlog: [FEATURE-BACKLOG.md](FEATURE-BACKLOG.md)
- VC Port threat model: [THREAT-MODEL.md](../THREAT-MODEL.md)
