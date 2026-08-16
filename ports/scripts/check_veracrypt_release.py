#!/usr/bin/env python3
"""Notice when the official VeraCrypt team publishes a release.

This does not download their tree into the phone app, and it does not merge
git for you. The pin is hardcoded in ports/version.json:

  upstream_git       https://github.com/veracrypt/VeraCrypt.git
  upstream_releases  GitHub latest-release API
  upstream_tag       VeraCrypt_X.Y.Z we currently compile
  upstream_commit    40-char git sha of that tag

When they publish, a maintainer runs scripts/sync-upstream.sh, then rebuilds
VC Port. F-Droid builds never call this (no INTERNET).

  python3 ports/scripts/check_veracrypt_release.py --pin-only
  python3 ports/scripts/check_veracrypt_release.py          # HTTPS, exit 2 if newer
"""

from __future__ import annotations

import argparse
import json
import ssl
import sys
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from tree_paths import PORTS, VERSION

OFFICIAL_GIT = "https://github.com/veracrypt/VeraCrypt.git"
OFFICIAL_RELEASES = "https://api.github.com/repos/veracrypt/VeraCrypt/releases/latest"


def load_version() -> dict:
    return json.loads(VERSION.read_text(encoding="utf-8"))


def version_from_tag(tag: str) -> str:
    """VeraCrypt_1.26.29 / VeraCrypt-1.26.29 / 1.26.29 -> 1.26.29"""
    t = (tag or "").strip()
    for prefix in ("VeraCrypt_", "VeraCrypt-", "VeraCrypt "):
        if t.startswith(prefix):
            t = t[len(prefix) :].strip()
            break
    return t.split()[0] if t else ""


def compare_version(a: str, b: str) -> int:
    def parts(s: str) -> list[int]:
        out: list[int] = []
        n = 0
        in_num = False
        for ch in s + ".":
            if ch.isdigit():
                n = n * 10 + ord(ch) - 48
                in_num = True
            elif in_num:
                out.append(n)
                n = 0
                in_num = False
        return out

    pa, pb = parts(a), parts(b)
    n = max(len(pa), len(pb))
    pa += [0] * (n - len(pa))
    pb += [0] * (n - len(pb))
    if pa < pb:
        return -1
    if pa > pb:
        return 1
    return 0


def pin_problems(v: dict) -> list[str]:
    problems: list[str] = []
    if v.get("upstream_git") != OFFICIAL_GIT:
        problems.append(f"upstream_git must be {OFFICIAL_GIT}")
    if v.get("upstream_releases") != OFFICIAL_RELEASES:
        problems.append(f"upstream_releases must be {OFFICIAL_RELEASES}")
    tag = str(v.get("upstream_tag") or "")
    ver = str(v.get("upstream_version") or "")
    if version_from_tag(tag) != ver:
        problems.append(f"upstream_tag {tag} does not match upstream_version {ver}")
    commit = str(v.get("upstream_commit") or "")
    if len(commit) != 40:
        problems.append("upstream_commit must be 40 hex chars")
    pin = (PORTS / "UPSTREAM_COMMIT").read_text(encoding="utf-8").strip()
    if pin != commit:
        problems.append("ports/UPSTREAM_COMMIT does not match version.json")
    return problems


def fetch_latest_tag(url: str) -> str:
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": "VCPort-OfflineUpdate/check_veracrypt_release",
            "Accept": "application/vnd.github+json",
        },
    )
    ctx = ssl.create_default_context()
    with urllib.request.urlopen(req, timeout=20, context=ctx) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    return str(data.get("tag_name") or "")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--pin-only",
        action="store_true",
        help="validate hardcoded official URLs; no network",
    )
    args = parser.parse_args()
    v = load_version()
    problems = pin_problems(v)
    if problems:
        print("upstream pin invalid:", file=sys.stderr)
        for p in problems:
            print(f"  {p}", file=sys.stderr)
        return 1
    if args.pin_only:
        print(
            f"pin ok: {v['upstream_tag']} {v['upstream_commit'][:12]} "
            f"from {v['upstream_git']}"
        )
        return 0

    tag = fetch_latest_tag(str(v["upstream_releases"]))
    remote = version_from_tag(tag)
    local = str(v["upstream_version"])
    print(f"this tree: {local} ({v['upstream_tag']})")
    print(f"github latest: {remote} ({tag})")
    if not remote:
        print("could not parse official release tag", file=sys.stderr)
        return 1
    if compare_version(remote, local) > 0:
        print("VeraCrypt published a newer release.")
        print("Do not patch the APK. Merge source, then rebuild:")
        print("  scripts/sync-upstream.sh")
        print("  scripts/refresh-overlay.sh")
        print("  python3 ports/scripts/sync_source_pin.py --write")
        return 2
    print("No newer official VeraCrypt release than this pin.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
