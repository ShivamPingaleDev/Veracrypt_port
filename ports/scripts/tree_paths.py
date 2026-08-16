"""Veracrypt_port full tree vs VCPort (ports/ as root)."""

from __future__ import annotations

from pathlib import Path

_SCRIPTS = Path(__file__).resolve().parent
_PARENT = _SCRIPTS.parent

if not (_PARENT / "android").is_dir() or not (_PARENT / "version.json").is_file():
    raise RuntimeError(f"expected android/ and version.json next to {_SCRIPTS}")

PORTS = _PARENT
ROOT = _PARENT.parent if _PARENT.name == "ports" else _PARENT
VERSION = PORTS / "version.json"
