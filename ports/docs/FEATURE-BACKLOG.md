# Feature backlog

VC Port stays **100% free**. Optional support is GitHub-only (README / SUPPORT.md). No paid unlocks. No stronger crypto for donors. **No browser links inside the app.**

**Inspiration from other apps** (Arcanum, Disk Decipher — scoped to our VeraCrypt phone use case): [INSPIRATION.md](INSPIRATION.md).

## Feature freeze (beta)

Started after **0.3.10**; freeze maintenance shipped in **0.3.11**. Session keyfile and Create-save wipe shipped in **0.3.12**. GitHub releases are **beta** demos — not 1.0, not a store build, not production-ready.

**One shipping branch:** freeze is **`master` only**. Ignore `experimental-otg-master` (OTG is already on master). Do not add commits to stale `experimental-biometrics` or `vcport-github`.

**Frozen:** Open, Create (nested size), Mounted (8 slots + Empty popup), Tools headers, panic, idle-on-open, Android OTG, in-app preview. No new user-facing volume features.

**Still allowed:** bug fixes, host/UI-walk regressions, splitting the two giant screens, cache/space error copy.

**Not in this freeze:** metadata scrub, File Provider, favorites, in-app updates, biometrics.

Avoid: persistable SAF bookmarks (conflicts with threat model), accelerometer entropy (opt-in conflict with GrapheneOS skepticism).

## Architecture (lock in)

- **One Open suite.** Volume tab and Mounted Empty popup both call `openVolumeWithFactors` / `openVolume()` through `OpenVolumeForm` / `openVolumeForm`. New mount options go in that one form.
- **One session closer.** Idle, screen-lock, panic, and Tools Wipe cached passwords call `closeOpenVolumes`. Home / Recents uses `dismountOnLeave` (saves mounted containers, then clears). Panic skips write-back. Do not grow a fifth path.
- **Mount save-back.** Open copies container to cache; mutations auto-flush and write-back to the picked URI; × / Dismount save then close. See `.cursor/rules/mount-container-save.mdc`.
- **Native work stays off the UI thread; WorkOverlay is the only progress UI.** No silent background jobs.
- **Host contracts stay grep + lifecycle.** They catch tab order and `/proc/self/fd`. They do not replace the emulator walk.

## Nested volume size

**Already there.** Create → Nested volume → **Nested size** + unit picker (`create_hidden_size`). Must be ≥ 2 MiB and **less than half** the outer size; outer must be ≥ 8 MiB.

## Already there (strong)

- Home / background dismount (Create wizard kept)
- Panic wipe
- Read-only open (checkbox + native write block)
- Progress overlay (percent + phase)
- Basket SHA-256 → `BASKET.sha256`
- Preview / export temps wiped on panic/close
- `ports/scripts/verify-build.sh` — this git tag → SHA-256 of the FOSS APK and unsigned IPA. GitHub APKs stay debug-signed previews; do not write those hashes into `version.json`.

## This pass (good ideas, no conflict)

| Feature | Status |
| --- | --- |
| Inactivity + screen-lock dismount | **Built** — Open-volume idle (typed minutes/hours, 0 = Off); screen-off / lock also closes volumes |
| Android Quick Settings panic tile | **Built** — optional tile; wipes even if you never open Tools |
| PIM iteration estimator | **Built** — Tools helper; not a crack-time claim; not Benchmark |
| In-container SHA-256 before export | **Built** — hash selected file(s) inside the volume, wipe the temp |
| Read-only banner | **Built** — Mounted tab shouts when the slot is read-only |
| SAF cache/space error | **Built** — names need vs free in app storage |
| Mega-screen split | **Built** — Open / Mounted / Create / Tools are their own files |
| `verify-build.sh` | **Built** — reviewer rebuilds FOSS and compares SHA-256 |
| Metadata scrub on export | **Later** — optional EXIF/timestamp; easy to get wrong |
| Batch queue + ETA | **Built** — one worker, visible job list, cancel at the next file, ETA when sizes are known |

## Domain plan (October 2026)

Phone vault apps in this domain open the same VeraCrypt file a desktop opens. FAT and exFAT are the filesystems that do that without a driver. ext4 does not open on Windows or macOS. Cloud disks, LUKS, and biometric password storage are a different product.

Do not add Expand Volume. Growing the container file does not grow the FAT or exFAT inside it. Desktop VeraCrypt warns about that. Arcanum removed the action after it reported free space no write could use.

A FAT volume cannot hold one file over 4 GiB. Import already refuses that size on the FAT path. The person should see “too large for FAT, use exFAT”, not a memory error. That copy change waits for the walk. exFAT is the large-file choice, not ext4.

Free space shown to the person must be free space in the filesystem, not the container’s byte length.

## Priority

1. Keep the freeze. No new volume screen until the walk covers it.
2. Production-signed FOSS APK (your keystore) and a signed IPA from this Mac. GitHub files stay previews.
3. When the freeze lifts: filesystem free space, then the FAT size sentence, then metadata scrub default-off on export copies only.
4. Later, and not a screen: SHA-512 instructions on phones that have them, with the software hash left in place. Do not change PIM or Argon2 memory.

## Merge note

Shipped in **0.3.9** on `master` after a local 10-phase Android UI walk. Freeze work shipped in **0.3.11**; session keyfile and Create-save wipe in **0.3.12**. The transfer queue shipped in **0.3.13**. Phone idle workers shipped in **0.3.14**. Stable alpha **0.3.15** ships the production APK and the debug APK as separate files. Later: metadata scrub.
