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

Out of scope: “please make this unbreakable,” Play Integrity / SafetyNet ideas,
obfuscation, hidden-volume UI toggles, and anything that needs a compromised OS
or compelled biometrics to matter.

## Source while private

TrueCrypt License 3.0 requires publicly available source when binaries are
distributed. Do not ship new APKs, IPAs, or GitHub Release attachments while
this repository is private.
