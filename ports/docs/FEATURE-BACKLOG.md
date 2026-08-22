# Feature backlog

VC Port stays **100% free**. Optional support is GitHub-only. No paid unlocks. No stronger crypto for donors.

Avoid: persistable SAF bookmarks (conflicts with threat model), accelerometer entropy (opt-in conflict with GrapheneOS skepticism).

## Nested volume size

**Already there.** Create → Nested volume → **Nested size** + unit picker (`create_hidden_size`). Must be ≥ 2 MiB and **less than half** the outer size; outer must be ≥ 8 MiB.

## Already there (strong)

- Home / background dismount (Create wizard kept)
- Panic wipe
- Read-only open (checkbox + native write block)
- Progress overlay (percent + phase)
- Basket SHA-256 → `BASKET.sha256`
- Preview / export temps wiped on panic/close

## This pass (good ideas, no conflict)

| Feature | Status |
| --- | --- |
| Inactivity + screen-lock dismount | **Built** — Tools idle Off/1/5/15 min; screen-off / lock also closes volumes |
| Android Quick Settings panic tile | **Built** — optional tile; wipes even if you never open Tools |
| PIM iteration estimator | **Built** — Tools helper; not a crack-time claim; not Benchmark |
| In-container SHA-256 before export | **Built** — hash selected file(s) inside the volume, wipe the temp |
| Read-only banner | **Built** — Mounted tab shouts when the slot is read-only |
| Metadata scrub on export | **Later** — optional EXIF/timestamp; easy to get wrong |
| Batch queue + ETA | **Later** — reuse progress hooks |
| `verify-build.sh` | **Later** — trust, not user-facing crypto |

## Priority

1. Idle + screen lock, panic tile
2. Read-only banner, in-volume hash, PIM estimator
3. Later: reproducible APK verify, export metadata scrub, transfer queue

## Merge note

Shipped in **0.3.9** on `master` after a local 10-phase Android UI walk. Good-ideas in “This pass” are **built**. Later: metadata scrub, batch queue, `verify-build.sh`.
