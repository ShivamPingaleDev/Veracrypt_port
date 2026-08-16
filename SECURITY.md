# Security

VC Port is a derived work of VeraCrypt. It is **not unbreakable**. A compelled
password, a persistent implant, or a RAM image of an unlocked volume still wins.
See [ports/THREAT-MODEL.md](ports/THREAT-MODEL.md).

## Contact

Shivam Mangesh Pingale

- shivampingaledev@proton.me
- shivampingaledev@gmail.com

There is no bug bounty.

## Scope

Please report:

- Volume-open, wrap/unwrap, or key-handling bugs in `ports/shared/`
- Android / iOS session wipe, backup, or share-sheet leaks
- StayOffline / update-check network mistakes
- Attribution or license-text errors that would mislead a user

Out of scope: “please make this unbreakable,” “foolproof against Unit 8200 / TAO / Lazarus / CIA,” Play Integrity / SafetyNet ideas,
obfuscation, open-time hidden-volume checkboxes, and anything that needs a compromised OS
or compelled biometrics to matter. There is no key escrow in this tree. There is also no
defence that stops a nation-state implant.

## Source and binaries

This repository is public: https://github.com/ShivamPingaleDev/Veracrypt_port

TrueCrypt License 3.0 still requires publicly available source whenever
binaries are distributed. Do not make the tree private again and then ship
APKs, IPAs, or GitHub Release attachments.

GitHub Actions APKs are **debug-signed previews**. Do not attach them to a
GitHub Release, and do not copy their SHA-256 into `ports/version.json`.
F-Droid (or `VC_PORT_RELEASE_STORE_FILE`) must sign anything called production.
