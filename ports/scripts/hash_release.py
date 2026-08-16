#!/usr/bin/env python3
"""Hash a release-signed VC Port APK. Never write a debug CI APK into version.json.

    python3 ports/scripts/hash_release.py path/to/app-release.apk
    python3 ports/scripts/hash_release.py path/to/app-release.apk --write
    python3 ports/scripts/hash_release.py --source
    python3 ports/scripts/hash_release.py --source --write

Refuses:
- missing files
- filenames containing 'debug'
- APKs whose signer CN is Android Debug
- --write/--write-source when the git worktree is dirty (hashes must match a commit)
"""

from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from tree_paths import ROOT, VERSION


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def apk_cert_text(path: Path) -> str:
    with zipfile.ZipFile(path) as z:
        names = [
            n
            for n in z.namelist()
            if n.startswith("META-INF/") and n.upper().endswith((".RSA", ".DSA", ".EC"))
        ]
        if not names:
            raise SystemExit("no APK signing cert in META-INF (not a signed APK)")
        der = z.read(names[0])
    proc = subprocess.run(
        ["openssl", "pkcs7", "-inform", "DER", "-print_certs", "-noout"],
        input=der,
        capture_output=True,
    )
    blob = (proc.stdout or b"") + (proc.stderr or b"")
    text = blob.decode("utf-8", errors="replace")
    if proc.returncode != 0 and "subject" not in text.lower():
        proc = subprocess.run(
            ["openssl", "x509", "-inform", "DER", "-noout", "-subject"],
            input=der,
            capture_output=True,
        )
        text = ((proc.stdout or b"") + (proc.stderr or b"")).decode("utf-8", errors="replace")
        if proc.returncode != 0:
            raise SystemExit("openssl could not read the APK cert; install openssl")
    return text


def refuse_debug(path: Path) -> None:
    if "debug" in path.name.lower():
        raise SystemExit("refusing debug-named APK; GitHub Actions APKs are previews")
    cert = apk_cert_text(path)
    if "android debug" in cert.lower() or "CN=Android Debug" in cert:
        raise SystemExit("refusing Android Debug signer; do not copy CI APK hashes into version.json")


def git_dirty() -> bool:
    proc = subprocess.run(
        ["git", "status", "--porcelain"],
        cwd=ROOT,
        capture_output=True,
        text=True,
        check=True,
    )
    return bool(proc.stdout.strip())


def source_sha256() -> str:
    proc = subprocess.run(
        ["git", "archive", "--format=tar", "HEAD"],
        cwd=ROOT,
        capture_output=True,
        check=True,
    )
    return hashlib.sha256(proc.stdout).hexdigest()


def write_version(field: str, digest: str) -> None:
    if git_dirty():
        raise SystemExit("refusing --write on a dirty worktree; commit first so the hash matches git")
    data = json.loads(VERSION.read_text(encoding="utf-8"))
    data[field] = digest
    VERSION.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {field} to {VERSION}")


def main() -> int:
    p = argparse.ArgumentParser(description="SHA-256 a release-signed VC Port APK or the git source archive")
    p.add_argument("apk", nargs="?", type=Path)
    p.add_argument("--source", action="store_true", help="hash git archive of HEAD into source_sha256")
    p.add_argument("--write", action="store_true", help="update ports/version.json (clean tree only)")
    args = p.parse_args()
    if args.source and args.apk:
        raise SystemExit("pass an APK or --source, not both")
    if not args.source and not args.apk:
        raise SystemExit("pass an APK path or --source")
    if args.source:
        digest = source_sha256()
        print(digest)
        if args.write:
            write_version("source_sha256", digest)
        return 0
    apk = args.apk.expanduser().resolve()
    if not apk.is_file():
        raise SystemExit(f"missing APK: {apk}")
    refuse_debug(apk)
    digest = sha256_file(apk)
    print(digest)
    if args.write:
        write_version("android_apk_sha256", digest)
    return 0


if __name__ == "__main__":
    sys.exit(main())
