# VC Port host tests

There is no device farm. These run on a laptop or in GitHub Actions without a
phone, an iOS simulator.

```
ports/tests/run-all.sh              # full host pass (CI)
python3 ports/tests/test_quality.py # taxonomy + property/fuzz
```

What each testing word means, and what still needs hardware: [TESTING.md](TESTING.md).

| Surface | Host coverage |
| --- | --- |
| Shared wrap (`.vcpw`) | Argon2id 32 MiB, AES-CTR roundtrip, wrong password, tamper, 0600, path sanitization, password generator |
| Android FOSS + GitHub | Version pin, wrap, panic, share, stay offline, no INTERNET, FLAG_SECURE |
| iOS | Version pin, wrap, panic, share, stay offline, `UIFileSharingEnabled=false` |
| Factor mix | VCF2 encode/decode spec shared by Kotlin and Swift |
| Overlay | CMake vs Crypto/Volume layout; inventories vs pin when git history is complete |
