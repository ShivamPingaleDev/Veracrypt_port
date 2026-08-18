# Archived desktop extras (not built)

This folder is a **cold archive** of the Mac/Linux GUI extras this fork used to ship
on top of VeraCrypt: FUSE-T tweaks, Touch ID volume unlock, Authorization Services,
wrap/panic/share in the computer Tools menu, StayOffline, and desktop Check for updates.

**Nothing here is compiled.** VC Port is the Android and iOS apps in `ports/`.
The `src/` tree in the repo root is official VeraCrypt source again, except two
mobile-only hunks:

- `src/Platform/Unix/File.cpp` (`TC_IOS` device ioctl guards)
- `src/Volume/Keyfile.cpp` (no PKCS#11/EMV token stack on phone builds)

To restore the old computer extras, copy the files under `src/` here back into
the repo root and re-apply `overlay/src-port.desktop.patch` on top of
`ports/UPSTREAM_COMMIT`. Prefer using official VeraCrypt on a computer instead.

`PORTING.macos.md` is the old Mac-first porting notes.
