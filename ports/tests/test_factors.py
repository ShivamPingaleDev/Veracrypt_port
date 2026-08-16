#!/usr/bin/env python3
"""VCF2 factor-bundle codec tests (Android Kotlin + iOS Swift contract).

The mobile UIs store password + PIM + biometric secret + keyfile paths in a
text bundle. This host test is the shared spec so the two implementations
cannot drift without CI noticing. No Keystore / Keychain / biometric hardware.
"""

from __future__ import annotations

import base64
import unittest
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


@dataclass
class FactorBundle:
    pim: int = 0
    password: str = ""
    biometric_key: bytes | None = None
    keyfiles: list[str] = field(default_factory=list)


def encode(bundle: FactorBundle) -> bytes:
    pw = base64.b64encode(bundle.password.encode("utf-8")).decode("ascii")
    bio = (
        base64.b64encode(bundle.biometric_key).decode("ascii")
        if bundle.biometric_key
        else ""
    )
    lines = ["VCF2", str(bundle.pim), pw, bio, *bundle.keyfiles]
    return ("\n".join(lines) + "\n").encode("utf-8")


def decode(raw: bytes) -> FactorBundle:
    text = raw.decode("utf-8")
    if not text.startswith("VCF2\n"):
        parts = text.split("\n", 1)
        pim = int(parts[0]) if parts and parts[0].isdigit() else 0
        password = parts[1] if len(parts) > 1 else ""
        return FactorBundle(pim=pim, password=password)
    lines = text.split("\n")
    pim = int(lines[1]) if len(lines) > 1 and lines[1] else 0
    password = ""
    if len(lines) > 2 and lines[2]:
        password = base64.b64decode(lines[2]).decode("utf-8")
    bio = None
    if len(lines) > 3 and lines[3]:
        bio = base64.b64decode(lines[3])
    uris = [line for line in lines[4:] if line]
    return FactorBundle(pim=pim, password=password, biometric_key=bio, keyfiles=uris)


class FactorCodecTests(unittest.TestCase):
    def test_roundtrip_password_only(self) -> None:
        src = FactorBundle(pim=0, password="correct horse battery")
        got = decode(encode(src))
        self.assertEqual(got.password, src.password)
        self.assertEqual(got.pim, 0)
        self.assertIsNone(got.biometric_key)
        self.assertEqual(got.keyfiles, [])

    def test_roundtrip_full_mix(self) -> None:
        src = FactorBundle(
            pim=485,
            password="päss wörd 🔒",
            biometric_key=bytes(range(64)),
            keyfiles=["content://a/b", "/tmp/keyfile.bin"],
        )
        got = decode(encode(src))
        self.assertEqual(got.pim, 485)
        self.assertEqual(got.password, src.password)
        self.assertEqual(got.biometric_key, src.biometric_key)
        self.assertEqual(got.keyfiles, src.keyfiles)

    def test_empty_password_and_pim(self) -> None:
        src = FactorBundle(pim=12, password="")
        got = decode(encode(src))
        self.assertEqual(got.pim, 12)
        self.assertEqual(got.password, "")

    def test_legacy_vcf1_plain_password(self) -> None:
        got = decode(b"7\nlegacy-password")
        self.assertEqual(got.pim, 7)
        self.assertEqual(got.password, "legacy-password")
        self.assertIsNone(got.biometric_key)

    def test_legacy_pim_only(self) -> None:
        got = decode(b"0\n")
        self.assertEqual(got.pim, 0)
        self.assertEqual(got.password, "")

    def test_password_not_stored_as_raw_utf8_in_vcf2(self) -> None:
        blob = encode(FactorBundle(password="visible-secret"))
        self.assertTrue(blob.startswith(b"VCF2\n"))
        self.assertNotIn(b"visible-secret", blob)

    def test_empty_bio_line_is_none(self) -> None:
        blob = encode(FactorBundle(password="x", biometric_key=None))
        got = decode(blob)
        self.assertIsNone(got.biometric_key)

    def test_keyfile_blank_lines_dropped(self) -> None:
        blob = b"VCF2\n0\n" + base64.b64encode(b"pw") + b"\n\n\nfile-a\n\nfile-b\n"
        got = decode(blob)
        self.assertEqual(got.keyfiles, ["file-a", "file-b"])


class MobileSourceLockTests(unittest.TestCase):
    def test_kotlin_and_swift_share_vcf2(self) -> None:
        kotlin = (ROOT / "ports/android/app/src/main/java/dev/shivampingale/vcport/UnlockFactors.kt").read_text()
        swift = (ROOT / "ports/ios/VCPort/UnlockFactors.swift").read_text()
        for src in (kotlin, swift):
            self.assertIn("VCF2\\n", src)
            self.assertIn("randomBiometricKey", src)
        self.assertIn("Base64", kotlin)
        self.assertIn("base64EncodedString", swift)
        self.assertIn("ByteArray(64)", kotlin)
        self.assertIn("count: 64", swift)

    def test_kotlin_legacy_fallback_still_present(self) -> None:
        kotlin = (ROOT / "ports/android/app/src/main/java/dev/shivampingale/vcport/UnlockFactors.kt").read_text()
        self.assertIn('startsWith("VCF2\\n")', kotlin)

    def test_swift_legacy_fallback_still_present(self) -> None:
        swift = (ROOT / "ports/ios/VCPort/UnlockFactors.swift").read_text()
        self.assertIn('hasPrefix("VCF2\\n")', swift)


class SemverCompareTests(unittest.TestCase):
    """Mirrors Android/iOS UpdateChecker.compare (numeric dotted parts)."""

    @staticmethod
    def compare(a: str, b: str) -> int:
        # Match Kotlin/Swift: split on '.' and '-', keep integer parts.
        def parts(s: str) -> list[int]:
            out = []
            buf = ""
            for ch in s + ".":
                if ch.isdigit():
                    buf += ch
                else:
                    if buf:
                        out.append(int(buf))
                        buf = ""
                    # skip non-digits including letters in pre-release
            return out

        pa = parts(a)
        pb = parts(b)
        n = max(len(pa), len(pb))
        for i in range(n):
            x = pa[i] if i < len(pa) else 0
            y = pb[i] if i < len(pb) else 0
            if x != y:
                return -1 if x < y else 1
        return 0

    def test_equal(self) -> None:
        self.assertEqual(self.compare("0.2.2", "0.2.2"), 0)

    def test_patch_newer(self) -> None:
        self.assertGreater(self.compare("0.2.3", "0.2.2"), 0)

    def test_minor_newer(self) -> None:
        self.assertGreater(self.compare("0.3.0", "0.2.9"), 0)

    def test_older(self) -> None:
        self.assertLess(self.compare("0.1.9", "0.2.0"), 0)

    def test_uneven_length(self) -> None:
        self.assertEqual(self.compare("0.2", "0.2.0"), 0)
        self.assertGreater(self.compare("0.2.1", "0.2"), 0)


if __name__ == "__main__":
    unittest.main(verbosity=2)
