# Rust dependencies roadmap (educational)

Hand-write first. Add crates only to **compare** or when the exercise is “use a library correctly,” not to skip learning.

## Tier 0 — `std` only (default `vcport-pure-rust` build)

| Topic | In this repo | External crate |
| --- | --- | --- |
| CLI | `clap` | — |
| Errors | `thiserror` | — |
| Hex | `src/hex.rs` (hand) | — |
| SHA-256 | `src/sha256.rs` (hand) | — |
| Toy container | `src/toy_container.rs` (hand) | — |

Install Rust: https://rustup.rs — then `rustup default stable`.

## Tier 1 — compare your implementations (`--features compare-crates`)

| Crate | Version pin | Use |
| --- | --- | --- |
| `sha2` | 0.10 | Compare `Sha256::digest` vs `sha256_hand` |
| `aes` | 0.8 | Block cipher exercises (ECB demo only — not for real volumes) |
| `hex` | 0.4 | Compare encode/decode vs `hex.rs` |

```bash
cargo test -p vcport-pure-rust --features compare-crates
```

## Tier 2 — toward real KDF / MAC (add when you reach those chapters)

| Crate | Version | Role | VeraCrypt relation |
| --- | --- | --- | --- |
| `digest` | 0.10 | Traits for hashers | Shared trait layer for `sha2` |
| `hmac` | 0.12 | HMAC-SHA-512 | Header MAC, KDF chains |
| `argon2` | 0.5 | Argon2id | Modern VeraCrypt KDF |
| `pbkdf2` | 0.12 | PBKDF2-HMAC | Legacy headers |
| `rand` | 0.8 | `OsRng` | Salts, keyfiles |
| `getrandom` | 0.2 | OS entropy | Under `rand` on phones |

**Exercise:** implement PBKDF2 loop by hand once, then diff against `pbkdf2` crate.

## Tier 3 — modes and containers (long horizon)

| Crate | Notes |
| --- | --- |
| `cipher` | Block cipher traits (`aes` uses it) |
| `xts-mode` or hand XTS | Phone volumes use XTS — study before using |
| `byteorder` | Little-endian FAT fields |
| `serde` + `serde_json` | Serialize lab configs, not user secrets |

FAT/exFAT inside a volume: **no good “drop in” crate** for VeraCrypt parity — expect to read specs and hand-write like `ports/shared/vc_mobile.cpp`.

## Tier 4 — CLI / testing / fuzz (when labs grow)

| Crate | Use |
| --- | --- |
| `anyhow` | Ergonomic errors in main (optional; we use `thiserror` in libs) |
| `assert_cmd` | CLI integration tests |
| `proptest` | Property tests on your hash/container |
| `cargo-fuzz` + `libfuzzer-sys` | Same idea as `ports/shared/fuzz_wrap.cc` |

## Tier 5 — **do not** add for educational parity with VC Port

| Crate / tool | Why skip |
| --- | --- |
| `openssl` / `ring` for everything | Hides the bytes you need to see |
| `veracrypt` (none exists) | Real code is C++ in `src/` |
| Play Integrity / attestation | Out of threat model |
| Async (`tokio`) | Crypto lab is CPU-bound sync |

## `Cargo.lock`

Commit `Cargo.lock` on the pure-rust branch so classroom machines get the same crate graph.

## System packages (Linux/macOS)

| Tool | Purpose |
| --- | --- |
| `rustup` + `stable` toolchain | `rustc`, `cargo`, `rustfmt`, `clippy` |
| `cmake` | Only for C `vcport-lab` linking `libvc_mobile` |
| `clang` / `gcc` | C scratch and NDK-style builds |

```bash
rustup component add rustfmt clippy
cargo install cargo-outdated   # optional: see stale pins
```

## Branch

Track **experimental-pure-rust** on GitHub. Merge doc fixes from `master`; keep crate pins updated on the rust branch first, then cherry-pick to `master` if needed.
