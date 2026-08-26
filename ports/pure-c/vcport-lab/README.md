# Personal C lab (experimental-pure-c)

Not a product. Not 1.0. Do not ship instead of the phone apps.

This branch is **stable alpha `master` (0.3.8)** plus a small C CLI that uses the **same** `libvc_mobile` as Android/iOS:

- Official VeraCrypt `src/` at the pin
- Phone overlay `ports/overlay/src/` (`File.cpp` — no `/proc/self/fd`)
- FAT / exFAT in-app via `ports/shared/vc_mobile.h`
- No second crypto stack, no Kotlin/Swift GUI here

`experimental/` is gitignored. This lab lives in `ports/pure-c/`.

## Match to the phone apps

| Technique | Stable alpha (`master`) | This lab |
| --- | --- | --- |
| Open a container | `vc_open` | same C API |
| Create FAT/exFAT | `vc_create_volume` | same |
| Nested folders | `vc_list_dir` / import / export | same |
| Native path | real file path, never `/proc/self/fd` | same |
| Whole-disk USB | not on master | stub only (see OTG) |
| In-app preview | not on master | kind names only (see OTG) |

## Vague OTG (from `experimental-otg-master`)

Full USB SCSI + `/vcport-otg-dev/N` Open is **Android-only** on `experimental-otg-master`. Here it is only sketched:

- `otg.c` knows the `/vcport-otg-dev/` prefix and always reports **not a bound disk** (no USB host in this CLI)
- `preview.c` uses the same filename kinds as View in app (image / text / pdf / audio / video / unsupported). It does not decode pixels or play sound
- See OTG Master for the Android whole-disk idea. This lab does not vendor that GPL tree

## Build

```bash
cd ports/pure-c
make
make test
./build/vcport-c preview-kind NOTE.TXT
./build/vcport-c otg-path /vcport-otg-dev/0
```

Needs the same `src/` tree as the phones (`VC_SRC` or clone `ShivamPingaleDev/Veracrypt_port`).
