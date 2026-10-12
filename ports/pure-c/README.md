# Pure C educational tracks — experimental-pure-c branch

Two paths on the same branch:

| Track | Folder | Links VeraCrypt? |
| --- | --- | --- |
| **Scratch (start here)** | [scratch/](scratch/) | No — hand-written hex, SHA-256, toy container |
| **vcport-lab (advanced)** | [vcport-lab/](vcport-lab/) | Yes — same `libvc_mobile` as Android/iOS |
| **veracrypt (C rewrite)** | [veracrypt/](veracrypt/) | **55-step** plan to a `vc_mobile.h`-shaped library ([MASTER](veracrypt/PURE_C_MASTER_ROADMAP.md), steps 1–16 done) |

Curriculum index: [../edu/README.md](../edu/README.md)

Rust parallel: `experimental-pure-rust` → [../pure-rust/](../pure-rust/)

**Not a product.** Not 1.0. Toy scratch crypto is not secure.

## macOS privacy prompts while developing (experimental-pure-c)

On Apple Silicon/macOS you may see dialogs that sound like “security risks.” Most are **normal TCC (Transparency, Consent, and Control)** or **developer-tool** gates—not because the pure-C lab is malicious.

| Prompt | Typical cause | Safer response |
| --- | --- | --- |
| **`test_*` wants Desktop access** | The repo lives under **Desktop** (`~/Desktop/Veracrypt_port`). The IDE or test runner loads binaries *from* that tree even though tests write temp containers under **`/tmp`**. | **Allow** only for **Terminal** or **Cursor** if you trust this repo; or **clone the repo to `~/Developer`** and reopen it (fewer Desktop-scoped prompts). |
| **Developer Tools Access** (password) | **lldb** / crash backtrace attaching to a `build/test_*` binary. | **Cancel** unless you are actively debugging; run `make test` without a debugger attached. |
| **`osascript` / `sudo` wants changes** | Usually **phone build scripts** (`ports/ios`, `ports/scripts`), not `ports/pure-c/veracrypt/make test`. | **Do not enter your password** until you know which script ran; pure-C unit tests do **not** need root. |

**Lab scope:** `cd ports/pure-c/veracrypt && make test` only compiles and runs local test binaries; it does not install a driver, disable SIP, or change system crypto policy. Stay on branch **`experimental-pure-c`** for this work—keep it separate from **`master`** phone releases (see [CONTRIBUTING.md](../CONTRIBUTING.md) rule 12).
