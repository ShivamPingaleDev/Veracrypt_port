# Overlay on official VeraCrypt

`src/` is unmodified VeraCrypt at `ports/UPSTREAM_COMMIT`. Do not edit it for phone work.

| Layer | Path | Role |
| --- | --- | --- |
| 0 | `src/` | Official VeraCrypt |
| 1 | `ports/overlay/src/` | Same relative paths; CMake compiles these instead, and their headers win on the include path |
| 2 | `ports/` | Android, iOS, wrap, tests |

Replacements today:

| Overlay path | Why |
| --- | --- |
| `Platform/Unix/File.cpp` | `TC_IOS` must not use macOS `sys/disk.h` / DKIOC |
| `Common/Token.cpp` | No PKCS#11 / EMV smart-card keyfiles |
| `Common/SecurityToken.h` | Same: official `Keyfile.cpp` includes this name |
| `Common/EMVToken.h` | Same |

File keyfiles still use official `src/Volume/Keyfile.cpp`. Phone builds define `TC_PORT_NO_TOKEN`.

When VeraCrypt publishes:

```bash
scripts/sync-upstream.sh --check
scripts/sync-upstream.sh
scripts/refresh-overlay.sh
scripts/check-upstream-layout.sh
```

If they change a replaced file, rebase the copy under `ports/overlay/src/` (`src-port.patch` is the last snapshot) and leave `src/` as theirs.

Mac/Linux GUI extras from this fork are frozen under `archive/desktop/` and are not built.
