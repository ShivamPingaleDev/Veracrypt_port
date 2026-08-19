> “We must defend our own privacy if we expect to have any.”
> — Eric Hughes, *A Cypherpunk’s Manifesto* (1993)

# VC Port

VC Port lets you open the same locked files on your **phone** that you already use on a computer.

This app is **not** called VeraCrypt. We are not allowed to use that name. It is **not unbreakable**.

There is **one app**: **VC Port**. GitHub has two names for the same work:

- **[Veracrypt_port](https://github.com/ShivamPingaleDev/Veracrypt_port)** — use this. Full source and the APK / IPA.
- **[VCPort](https://github.com/ShivamPingaleDev/VCPort)** — only the phone folders, no VeraCrypt `src/`. Not a second app. Do not install from there.

Commit list: [ports/HISTORY.md](ports/HISTORY.md).

The repo is named Veracrypt_port because the volume engine is VeraCrypt source. The app on the phone is still called VC Port.

## How it works

A locked file (a *volume*) is just a file on disk. On a computer, VeraCrypt can attach it as a drive letter. A phone cannot do that.

This app does the next-best thing:

1. You pick the locked file. Any name is fine (`.hc`, `.jpg`, …). The name is only a disguise.
2. You type the password (and PIM / keyfiles if you use them). Nothing is stored.
3. The app unlocks the file in RAM and shows the folders on the **Mounted** tab. That is not a system drive — only this app can see it.
4. You copy files in or out (one or several; from the phone, into the volume, or between two open volumes). The file on disk stays locked. **Files** / **Files.app** cannot browse the unlocked folder — Android and iOS do not let this app attach a drive letter.
5. **Dismount**, Home, or **Panic wipe** closes it and clears secrets on this phone. The locked file itself is not deleted.

**Create** makes a new locked file. After you save it, type the password again to open it. Same password opens it on a PC or Mac.

A compelled password still wins. Prefer a long password and a keyfile.

## License (why TrueCrypt 3.0 is here)

This project did **not** pick abandoned TrueCrypt as a product. The volume engine is **VeraCrypt** (pin in `ports/version.json`). VeraCrypt is a TrueCrypt fork, so that core **inherits** dual license **Apache-2.0 and TrueCrypt License 3.0**. Phone UI code is Apache-2.0.

TrueCrypt License 3.0 is **not** OSI / FSF / Debian-free. Shipping the core still requires it: public source, TrueCrypt attribution, and you may not call this app VeraCrypt or TrueCrypt. Dropping that license would mean dropping VeraCrypt compatibility. That is the trade: same `.hc` files as a computer, plus a license many distros will not ship.

See [License.txt](License.txt) and [ports/FOSS.md](ports/FOSS.md).

## Phones (the main thing)

**Android** and **iPhone**. That is what this project is for.

- Open a locked file and look at the folders inside
- Keep several volumes mounted and copy or move files between them
- Copy or move several files between the phone and an open volume
- Make a new locked file
- Send the locked file as-is (no password on the send)
- Stay offline (this build has no `INTERNET` permission)
- Wipe this phone’s secrets if you need to

On **iPhone**, you **sign** the app yourself with **your Apple ID**. We do not sign it for you.

**0.3.7 is a stable alpha**, not 1.0, not a store build. Copies: [GitHub Release v0.3.7](https://github.com/ShivamPingaleDev/Veracrypt_port/releases/tag/v0.3.7). The APK there is a **debug-signed preview**. The IPA is **unsigned**. Production is a FOSS APK you build and sign with your own keystore, plus an IPA you sign with your Team ID. Installing one Android copy replaces the others. This repo does not ship a PC or Mac app — use official VeraCrypt on a computer.

## iPhone — install (you sign it)

Apple will not let us send you a finished app. You put it on the iPhone with **your Apple ID**. We never see that ID.

1. On a computer, install [AltStore](https://altstore.io/) or [SideStore](https://sidestore.io/).
2. Download `VCPort-0.3.7-unsigned-preview.ipa` from the [v0.3.7 release](https://github.com/ShivamPingaleDev/Veracrypt_port/releases/tag/v0.3.7).
3. Open that file in AltStore or SideStore. When it asks, sign in with **your Apple ID**.
4. On the iPhone: **Settings → General → VPN & Device Management** → tap your Apple ID → **Trust**.
5. Open **VC Port**.

A free Apple ID lasts **7 days**. Open AltStore or SideStore again before that so the app stays installed. A copy you sign will not work on someone else’s iPhone.

If you have a Mac and Xcode: open the project, pick your name as Team, plug in the iPhone, press Run. More detail: [ports/ios/README.md](ports/ios/README.md).

**Volume** — pick a locked file and type the password.

Android: ![Volume](ports/docs/screenshots/01-volume.png)
iPhone: ![Volume iPhone](ports/docs/screenshots/ios-01-volume.png)

**Create** — make a new locked file.

Android: ![Create](ports/docs/screenshots/03-create.png)
iPhone: ![Create iPhone](ports/docs/screenshots/ios-03-create.png)

**Mounted** — folders inside. Slots are this session only. Not a system drive.

Android: ![Mounted](ports/docs/screenshots/05-mounted.png)
iPhone: ![Mounted iPhone](ports/docs/screenshots/ios-05-mounted.png)

**Tools** — change the volume password, headers, appearance. No wrap UI on the phones.

Android: ![Tools](ports/docs/screenshots/04-tools.png)
iPhone: ![Tools iPhone](ports/docs/screenshots/ios-04-tools.png)

How to build the phones: [PORTING.md](PORTING.md). Official VeraCrypt `src/` is in this folder so volumes stay compatible. On a computer, use official VeraCrypt — this project does not ship a PC or Mac app.

## Appearance (least important)

Original is the VeraCrypt-like look. Dark mode is a dark theme. Not required. Not the point of the app.

![Dark mode](ports/docs/screenshots/08-skin-signal.png)

**Contact:** Shivam Mangesh Pingale — [shivampingaledev@proton.me](mailto:shivampingaledev@proton.me) · [shivampingaledev@gmail.com](mailto:shivampingaledev@gmail.com)

**Footnote:** A programming noob still doing a five-year IT engineering degree (graduate summer 2027). Just trying to make something better that he likes to use, without much knowledge. Open to suggestions and advice.

If this work is useful to you and you have room to **teach**, offer an **internship**, or **hire**: I am looking for that. Same email as Contact. No pressure.

Problems that can hurt people: [SECURITY.md](SECURITY.md). How to talk about this in public: [ports/PUBLIC.md](ports/PUBLIC.md).

This folder also has the original VeraCrypt source. Use it only if you accept [License.txt](License.txt). A copy must not be called VeraCrypt or TrueCrypt.

> “Cypherpunks write code.”
> — Eric Hughes, *A Cypherpunk’s Manifesto* (1993)
