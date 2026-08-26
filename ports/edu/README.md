# Educational labs (not VC Port product)

**Purely for learning.** Hand-written C and Rust — from scratch exercises that build toward understanding what the phone apps do, without shipping another product.

| Branch | Folder | What you build |
| --- | --- | --- |
| `experimental-pure-c` | [pure-c/scratch/](pure-c/scratch/) | C: hex, SHA-256, toy encrypted container, CLI |
| `experimental-pure-c` | [pure-c/vcport-lab/](pure-c/vcport-lab/) | C: same `libvc_mobile` as phones (optional advanced lab) |
| `experimental-pure-rust` | [pure-rust/](pure-rust/) | Rust: same curriculum as scratch C, then optional crate comparisons |

**`master`** may carry these folders for docs and sync; **do not** treat them as the POC phone release. Phone shipping stays `master` → APK/IPA.

## End-to-end curriculum (both languages)

1. **Bytes & hex** — read/write files, print digests as hex (`hex.c` / `hex.rs`)
2. **SHA-256 by hand** — implement FIPS-180-4 yourself, test against known vectors
3. **Toy container** — magic header + length + XOR stream (learn *structure*, not security)
4. **CLI** — `hash`, `create`, `list` subcommands
5. **Compare crates** (Rust only, optional feature) — `sha2`, `aes` vs your code
6. **Advanced C lab** — link real `libvc_mobile` ([vcport-lab](pure-c/vcport-lab/))
7. **Read VC Port** — `ports/shared/vc_mobile.h`, then official VeraCrypt `src/`

## Build

```bash
# C scratch (no VeraCrypt link)
make -C ports/pure-c/scratch test

# Rust scratch (std + hand code; no crates required)
cargo test --manifest-path ports/pure-rust/Cargo.toml
cargo run --manifest-path ports/pure-rust/Cargo.toml -- hash README.md

# Rust with optional crate comparison
cargo test --manifest-path ports/pure-rust/Cargo.toml --features compare-crates

# C vcport-lab (needs cmake, VeraCrypt src tree)
make -C ports/pure-c/vcport-lab test
```

## Rules

- Hand-write the learning modules; do not paste VeraCrypt `src/` into scratch folders.
- Scratch toy crypto is **not** VeraCrypt-compatible and **not** secure.
- No network, no store metadata, no release APK from these folders.

See [pure-rust/DEPENDENCIES.md](pure-rust/DEPENDENCIES.md) for the full Rust crate roadmap.
