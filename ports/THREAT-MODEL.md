# High-threat model (whistleblower / seized device)

Nothing in this tree is **unbreakable**. A state that can compel a password, plant a persistent implant, or image RAM while the volume is open still wins. The controls below follow OWASP MASVS (storage, crypto, network, privacy), NIST SP 800-63 (biometrics are not a secret), and journalist guidance from EFF / Freedom of the Press Foundation: raise the cost of a *casual* forensic pass and stop the app from making the seizure easier.

## Adversary

| Adversary | What they can do | What this app tries to do | What still wins |
| --- | --- | --- | --- |
| Border / police seizure (device off or locked) | Image flash, cloud backup, recents screenshots | No backups, FLAG_SECURE (Recents is a blank card, not a screenshot), panic wipe, no SAF export | Compelled password; hardware implants |
| Compelled biometrics | Finger on the sensor | Biometrics optional; warned; not the default “Remember” | A remembered factor set still opens if they get the finger |
| Network MITM (user CA / captive portal) | Fake TLS | No INTERNET on master phone builds | A compromised system CA store |
| Malware with root / MDM | Read RAM, keylog, screenshots | Dismount open volume on background; no dumps (`PR_SET_DUMPABLE`); mlock wrap keys | Rooted implant while unlocked; Create wizard (including nested password) stays in RAM until Dismount/Panic so Copy once → Notes can finish |
| Forensic leftovers | Cache, clipboard, URI grants | Wipe session files; 30s clipboard; no persistable SAF grants; 0600 wrap files | Unmount delay; other apps you shared *to* |
| Store / update supply chain | Trojan APK | Rebuild from public source; no GMS; no obfuscation (reviewable) | A malicious mirror or a debug-signed preview you treated as production |
| Nation-state APT / intel service | Implant, compiler/OS compromise, 0-days, compelled password, TEMPEST, supply chain | No key escrow; no LEA/intel backdoor; no INTERNET on master phone builds; public source | **They still win.** Unit 8200, TAO, Office 121 / Lazarus, GRU/SVR, MSS, CIA/FBI with a warrant or implant — this app does not stop them |

## Nation-state (out of scope)

There is **no** foolproof build against Unit 8200, Tailored Access Operations, Office 121 / Lazarus, or any other government shop. Claiming that would be a lie.

What this tree **does** refuse:

- Key escrow, golden keys, or a silent decrypt path for police or intelligence
- Telemetry, crash reporters, or a listening socket they could ride
- Fetching and running remote code

What still wins against those groups, every time:

- A password they compel, or a keyfile they seize
- An implant on the phone or the compiler that built the APK
- RAM while a volume is open
- A 0-day in Android, iOS, or the VeraCrypt core we did not write

Use GrapheneOS, a strong passphrase, a keyfile not stored on the phone, and a self-built FOSS APK. That raises the cost of a *casual* seizure. It does not make you invisible to a determined service.

## FOSS-compatible high-threat profile (do this)

1. Build and install the **FOSS flavor** (`assembleFossRelease`) — no `INTERNET`.
2. Run it on **GrapheneOS** (or equivalent) with a locked bootloader, no Google services, and a strong OS passphrase.
3. Keep the **volume password in your head**. Put the keyfile on a *different* token, not on the phone.
4. Keep volume passwords in your head. Put keyfiles on a *different* token, not on the phone. Master has no fingerprint / Face ID unlock (`experimental-biometrics` does). Volume-path history never hits `History.xml`.
5. A **VeraCrypt hidden / nested volume** can be created here or on a computer. This client **opens whichever password you type** — there is no open-time “hidden” checkbox. Filling the outer volume overwrites the nested one.
6. Prefer a **self-built** APK over GitHub debug-signed previews.
7. Make the git repo **public** before you distribute binaries (TrueCrypt License 3.0).

## Deliberately not added (anti-FOSS or fake security)

- Google Play Integrity / SafetyNet / SafetyNet-like root detection (breaks GrapheneOS and user-built FOSS)
- Code obfuscation or packed native libs (unverifiable)
- “Unbreakable” / “foolproof against Unit 8200 / CIA / Lazarus” marketing
- An open-time hidden-volume checkbox (deniability leak; opening already follows the password you type)
- Pinning GitHub’s TLS keys (they rotate; pinning would brick updates)
- Root/Play Integrity theatre that pretends to detect nation-state implants

## Industry mappings

- **OWASP MASVS-STORAGE**: no backups, wipe session files, Keystore/StrongBox, no exported DocumentsProvider
- **OWASP MASVS-CRYPTO**: Argon2id wrap KDF 32 MiB, AES-256, HMAC-SHA256, constant-time MAC compare, CSPRNG passwords
- **OWASP MASVS-NETWORK**: no cleartext, system trust anchors only, no incoming sockets, no background traffic. Master phone builds have no INTERNET. Live Check for updates (≤20s HTTPS window to three hardcoded hosts) is on `experimental-biometrics`. Fetched JSON is never executed. This does not detect unknown bugs in VeraCrypt itself.
- **OWASP MASVS-PRIVACY**: no telemetry, no crash reporters. Stay offline by default.
- **NIST SP 800-63**: biometrics are not a knowledge factor
- **FOSS hygiene**: no GMS, no trackers, Gradle wrapper with published SHA-256
