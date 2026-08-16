# High-threat model (whistleblower / seized device)

Nothing in this tree is **unbreakable**. A state that can compel a password, plant a persistent implant, or image RAM while the volume is open still wins. The controls below follow OWASP MASVS (storage, crypto, network, privacy), NIST SP 800-63 (biometrics are not a secret), and journalist guidance from EFF / Freedom of the Press Foundation: raise the cost of a *casual* forensic pass and stop the app from making the seizure easier.

## Adversary

| Adversary | What they can do | What this app tries to do | What still wins |
| --- | --- | --- | --- |
| Border / police seizure (device off or locked) | Image flash, cloud backup, recents screenshots | No backups, FLAG_SECURE, recents hidden, panic wipe, no SAF export | Compelled password; hardware implants |
| Compelled biometrics | Finger on the sensor | Biometrics optional; warned; not the default “Remember” | A remembered factor set still opens if they get the finger |
| Network MITM (user CA / captive portal) | Fake TLS | System CAs only, no cleartext; F-Droid flavor has no INTERNET | A compromised system CA store |
| Malware with root / MDM | Read RAM, keylog, screenshots | Wipe on background; no dumps (`PR_SET_DUMPABLE`); mlock wrap keys | Rooted implant while unlocked |
| Forensic leftovers | Cache, clipboard, URI grants | Wipe session files; 30s clipboard; no persistable SAF grants; 0600 wrap files | Unmount delay; other apps you shared *to* |
| Store / update supply chain | Trojan APK | F-Droid from source; no GMS; no obfuscation (reviewable) | A malicious F-Droid mirror you did not verify |

## FOSS-compatible high-threat profile (do this)

1. Build and install the **F-Droid flavor** (`assembleFdroidRelease`) — no `INTERNET`.
2. Run it on **GrapheneOS** (or equivalent) with a locked bootloader, no Google services, and a strong OS passphrase.
3. Keep the **volume password in your head**. Put the keyfile on a *different* token, not on the phone.
4. Do **not** tap Remember / biometrics if fingerprints can be compelled in your jurisdiction.
5. Use a **VeraCrypt hidden volume** created on a computer (this client opens whichever password you type; there is no “hidden” checkbox, which would be evidence).
6. Prefer **F-Droid** or a self-built APK over GitHub debug-signed previews.
7. Make the git repo **public** before you distribute binaries (TrueCrypt License 3.0).

## Deliberately not added (anti-FOSS or fake security)

- Google Play Integrity / SafetyNet / SafetyNet-like root detection (breaks GrapheneOS and F-Droid)
- Code obfuscation or packed native libs (unverifiable)
- “Unbreakable” marketing
- A hidden-volume *toggle* in the UI (deniability leak)
- Pinning GitHub’s TLS keys (they rotate; pinning would brick updates)

## Industry mappings

- **OWASP MASVS-STORAGE**: no backups, wipe session files, Keystore/StrongBox, no exported DocumentsProvider
- **OWASP MASVS-CRYPTO**: Argon2id wrap KDF 32 MiB, AES-256, HMAC-SHA256, constant-time MAC compare, CSPRNG passwords
- **OWASP MASVS-NETWORK**: no cleartext, system trust anchors only
- **OWASP MASVS-PRIVACY**: no telemetry, no crash reporters, StayOffline default
- **NIST SP 800-63**: biometrics are not a knowledge factor
- **F-Droid Inclusion Policy**: no GMS, no trackers, Gradle wrapper with published SHA-256
