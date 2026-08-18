#!/usr/bin/env python3
"""Host tests for panic-wipe / keyfile overwrite semantics.

Mirrors Hardening.wipeFile and KeyfileIo.wipe: overwrite then unlink.
No Android Context or iOS FileManager required.
"""

from __future__ import annotations

import os
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from repo_paths import resolve  # noqa: E402


def wipe_file(path: Path) -> None:
    if not path.exists():
        return
    if path.is_dir():
        for child in path.iterdir():
            wipe_file(child)
        path.rmdir()
        return
    length = path.stat().st_size
    if 0 < length <= 64 * 1024 * 1024:
        path.write_bytes(b"\x00" * length)
    path.unlink()


def wipe_dir(path: Path) -> None:
    if path.exists():
        wipe_file(path)


class WipeTests(unittest.TestCase):
    def test_overwrite_then_delete_file(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            target = Path(tmp) / "secret.bin"
            payload = b"PLAINTEXT-LEAK" * 8
            target.write_bytes(payload)
            inode_dir = os.path.dirname(target)
            wipe_file(target)
            self.assertFalse(target.exists())
            # Directory listing must not still contain the name.
            self.assertNotIn("secret.bin", os.listdir(inode_dir))

    def test_missing_file_is_ok(self) -> None:
        wipe_file(Path("/tmp/vcport-no-such-wipe-file"))

    def test_recursive_session_dirs(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            cache = Path(tmp)
            for name in ("keyfiles", "unwrapped", "share", "inbox", "wraps"):
                d = cache / name
                d.mkdir()
                (d / "a.bin").write_bytes(b"aaaa")
                nested = d / "nested"
                nested.mkdir()
                (nested / "b.bin").write_bytes(b"bbbb")
            leftover = cache / "wrap-in-1"
            leftover.write_bytes(b"cccc")
            bio = cache / "vcbio123.key"
            bio.write_bytes(b"dddd")
            incoming = cache / "vc-in-abc.bin"
            incoming.write_bytes(b"eeee")
            keep = cache / "other.txt"
            keep.write_bytes(b"keep-me")

            for name in ("keyfiles", "unwrapped", "share", "wraps"):
                wipe_dir(cache / name)
            for child in cache.iterdir():
                if child.is_file() and (
                    child.name.startswith("wrap-in-")
                    or child.name.startswith("vcbio")
                    or child.name.startswith("vc-in-")
                ):
                    wipe_file(child)

            self.assertFalse((cache / "keyfiles").exists())
            self.assertFalse((cache / "unwrapped").exists())
            self.assertFalse((cache / "share").exists())
            self.assertFalse((cache / "wraps").exists())
            self.assertFalse(leftover.exists())
            self.assertFalse(bio.exists())
            self.assertFalse(incoming.exists())
            self.assertTrue(keep.exists())
            self.assertEqual(keep.read_bytes(), b"keep-me")
            self.assertTrue((cache / "inbox").exists())

    def test_panic_also_wipes_inbox(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            cache = Path(tmp)
            inbox = cache / "inbox"
            inbox.mkdir()
            (inbox / "shot.bin").write_bytes(b"inbox")
            containers = cache / "containers"
            containers.mkdir()
            (containers / "volume.hc").write_bytes(b"vol")
            (cache / "stray").write_bytes(b"stray")
            wipe_dir(inbox)
            wipe_dir(containers)
            for child in list(cache.iterdir()):
                if child.is_file():
                    wipe_file(child)
            self.assertFalse(inbox.exists())
            self.assertFalse(containers.exists())
            self.assertFalse((cache / "stray").exists())

    def test_keyfile_size_cap_constant(self) -> None:
        kotlin = resolve(
            "ports/android/app/src/main/java/dev/shivampingale/vcport/UnlockFactors.kt"
        ).read_text()
        self.assertIn("MAX_KEYFILE = 1024 * 1024", kotlin)


if __name__ == "__main__":
    unittest.main(verbosity=2)
