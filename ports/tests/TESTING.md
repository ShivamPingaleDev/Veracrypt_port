# How VC Port is tested

Host tests stand in for a phone, an iOS simulator, and a FUSE-T mount.

```
ports/tests/run-all.sh
python3 ports/tests/test_quality.py
```

## The map

| Kind | Question it answers | What actually runs |
| --- | --- | --- |
| **Unit** | Does one function do the right thing? | `test_factors.py` (VCF2), `test_wipe.py`, tag/version tables in `test_quality.py`, password generator in `test_wrap_main.cpp` |
| **Module** | Does one C/Kotlin/Swift unit keep its contract? | `run_wrap_test.sh`, `run_volume_test.sh`, SourcePin / UpdateChecker |
| **White-box** | Do we look at the code paths? | Wrap wrong password / tamper MAC, F-Droid `check()` throws |
| **Black-box** | Does it behave from the outside? | Wrap in → unwrap out; create → open → list → export |
| **Integration** | Do two layers talk? | JNI/C API; Kotlin/Swift VCF2; version.json → PortVersion.h / Info.plist |
| **Functional** | Can a user finish a job? | Copy, wipe, panic, keyfiles, progress overlay — no open-time hidden checkbox |
| **System** | Whole tree on a laptop | `run-phases.sh` |
| **Smoke** | Does the pin parse? | `check_veracrypt_release.py --pin-only` |
| **Regression** | Frozen pin / FOSS rule | Honesty freeze; app is still VC Port |
| **Contract** | Clients stay in lockstep | `test_contracts.py` |
| **Security / tamper** | Ciphertext and leftovers | Wrap HMAC; FLAG_SECURE; no F-Droid INTERNET |
| **Negative / boundary** | Bad input | Generator length 8/65; import 256 MiB; keyfile 1 MiB |
| **Compatibility** | Same volume on a computer | AES(Twofish(Serpent)) / HMAC-SHA-512; FAT only |
| **Recovery** | Header tools | Backup/restore in the volume fixture |
| **Acceptance** | Ship checklist | Phase 10 public repo + version tag |
| **Static** | Read source without executing crypto | File greps |
| **Exploratory / device** | Human on a phone | Listed below — not faked |

## Deterministic extras (`test_quality.py`)

No Hypothesis, no wall-clock, no network. Seeded RNG and tables only.

| Technique | What it does |
| --- | --- |
| **Table-driven** | Known tag/version rows |
| **Property** | Version compare is reflexive, antisymmetric, transitive |
| **Metamorphic** | Prefix strip is identity on a numeric version |
| **Differential** | Python / Kotlin / Swift share tag prefixes |
| **Bounded fuzz** | Random tags never throw; wrap garbage fails before a pile of Argon2 |

A corrupted biometric vault must decode to empty factors, not crash.

## What still needs a real device

- Opening a real VeraCrypt volume on Android/iOS
- Biometric prompt (StrongBox / Face ID / Touch ID)
- Share sheet and USB/OTG roundtrip
- FLAG_SECURE screenshot (adb capture is black by design)
- FUSE-T mount / hdiutil attach on a Mac

Do not add Play Integrity, obfuscation, or an open-time hidden-volume checkbox
to “make tests pass.” Those fail the threat model.

Wrap and volume tests are both white-box (return codes) and black-box (files on
disk). Wrong password must not yield plaintext; a flipped byte in `.vcpw` is
rejected.
