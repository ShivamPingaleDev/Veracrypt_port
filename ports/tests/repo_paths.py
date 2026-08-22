"""Resolve Veracrypt_port (full tree) vs VCPort (this directory is ports/)."""

from __future__ import annotations

import unittest
from pathlib import Path

_TESTS = Path(__file__).resolve().parent
_PORTS_OR_ROOT = _TESTS.parent

if not (_PORTS_OR_ROOT / "android").is_dir() or not (_PORTS_OR_ROOT / "shared").is_dir():
    raise RuntimeError(f"VC Port tests expected android/ and shared/ next to {_TESTS}")

PORTS = _PORTS_OR_ROOT
ROOT = _PORTS_OR_ROOT.parent if _PORTS_OR_ROOT.name == "ports" else _PORTS_OR_ROOT
FULL_TREE = (ROOT / "src" / "Main").is_dir()


def resolve(rel: str) -> Path:
    rel_path = Path(rel)
    candidates: list[Path] = []
    if rel_path.parts[:1] == ("ports",):
        candidates.append(PORTS.joinpath(*rel_path.parts[1:]))
    candidates.append(ROOT / rel_path)
    if PORTS != ROOT:
        candidates.append(PORTS / rel_path)
    for path in candidates:
        if path.exists():
            return path
    return candidates[0]


def read(rel: str) -> str:
    path = resolve(rel)
    if not path.is_file():
        if rel.startswith("src/") or rel.startswith(".github/"):
            raise unittest.SkipTest(f"missing {rel} (Veracrypt_port full tree)")
        raise FileNotFoundError(path)
    return path.read_text(encoding="utf-8")


def _join_existing(rels: list[str]) -> str:
    parts: list[str] = []
    for rel in rels:
        path = resolve(rel)
        if path.is_file():
            parts.append(path.read_text(encoding="utf-8"))
    return "\n".join(parts)


def read_android_ui() -> str:
    """MainActivity plus extracted Open/Mounted/Create/Tools screens."""
    return _join_existing(
        [
            "ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt",
            "ports/android/app/src/main/java/dev/shivampingale/vcport/OpenVolumeForm.kt",
            "ports/android/app/src/main/java/dev/shivampingale/vcport/VaultPane.kt",
            "ports/android/app/src/main/java/dev/shivampingale/vcport/CreateVolumeForm.kt",
            "ports/android/app/src/main/java/dev/shivampingale/vcport/ToolsPane.kt",
            "ports/android/app/src/main/java/dev/shivampingale/vcport/SizeUnits.kt",
        ]
    )


def read_ios_ui() -> str:
    """ContentView plus extracted Open/Mounted/Create/Tools extensions."""
    view_dir = resolve("ports/ios/VCPort")
    files = [view_dir / "ContentView.swift"]
    files.extend(sorted(view_dir.glob("ContentView+*.swift")))
    files.append(view_dir / "AppStorageSpace.swift")
    files.append(view_dir / "PasswordEntropy.swift")
    return "\n".join(p.read_text(encoding="utf-8") for p in files if p.is_file())
