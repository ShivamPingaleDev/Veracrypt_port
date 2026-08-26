# Educational scratch Rust lab

**Branch:** `experimental-pure-rust`  
**Not** VC Port. **Not** VeraCrypt-compatible. Hand-written code for learning.

## What this is

End-to-end Rust exercises from scratch:

| Module | File | You learn |
| --- | --- | --- |
| Hex | `src/hex.rs` | Encoding bytes for digests |
| SHA-256 | `src/sha256.rs` | Merkle–Damgård hash by hand |
| Toy container | `src/toy_container.rs` | Header + length + stream transform |
| CLI | `src/main.rs` | `clap` subcommands |

Optional: `--features compare-crates` checks your SHA-256 against the `sha2` crate.

## Dependencies

See [DEPENDENCIES.md](DEPENDENCIES.md) for the full crate roadmap (Argon2, HMAC, AES, XTS, testing, fuzz).

Default `Cargo.toml` pins only:

- `clap` — CLI
- `thiserror` — error types

## Build & test

```bash
cd ports/pure-rust
cargo test                    # toy + hex pass; sha256 tests ignored until you finish them
cargo test -- --ignored       # run sha256 vectors after your fix
cargo test --features compare-crates -- --ignored  # compare to sha2 crate
cargo run -- hash ../../README.md
```

## Next chapters (you implement)

1. Hand PBKDF2-HMAC-SHA512 loop
2. One AES block by hand, then `aes` crate compare
3. Read `ports/shared/vc_mobile.h` — same concepts, production C API
4. Never ship toy XOR as real crypto

## License

Apache-2.0 (same as VC Port phone tree). Toy code is yours to study.
