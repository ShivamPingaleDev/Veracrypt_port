#!/usr/bin/env python3
"""Keep device pins in sync with ports/version.json.

    python3 ports/scripts/sync_source_pin.py --check
    python3 ports/scripts/sync_source_pin.py --write

Android Gradle already reads version.json at build time. This script updates
the files that cannot: PortVersion.h, iOS Info.plist / project.yml, and
F-Droid CurrentVersion. It does not rewrite historical F-Droid Builds entries.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from tree_paths import PORTS, ROOT, VERSION


def load_version() -> dict:
    return json.loads(VERSION.read_text(encoding="utf-8"))


def expected(v: dict) -> dict[str, str]:
    return {
        "port_version": str(v["port_version"]),
        "android_version_code": str(int(v["android_version_code"])),
        "upstream_version": str(v["upstream_version"]),
        "upstream_commit": str(v["upstream_commit"]),
        "upstream_git": str(v["upstream_git"]),
        "upstream_releases": str(v["upstream_releases"]),
        "upstream_tag": str(v["upstream_tag"]),
        "source_repo": str(v["source_repo"]),
        "update_manifest": str(v["update_manifest"]),
    }


def rewrite_define(text: str, name: str, value: str) -> str:
    return re.sub(
        rf'(#define {re.escape(name)}\s+")[^"]*(")',
        rf"\g<1>{value}\2",
        text,
        count=1,
    )


def rewrite_plist_string(text: str, key: str, value: str) -> str:
    return re.sub(
        rf"(<key>{re.escape(key)}</key>\s*<string>)[^<]*(</string>)",
        rf"\g<1>{value}\2",
        text,
        count=1,
    )


def rewrite_yaml_field(text: str, key: str, value: str) -> str:
    return re.sub(
        rf"^({re.escape(key)}:\s*).*$",
        rf"\g<1>{value}",
        text,
        count=1,
        flags=re.M,
    )


def apply_write(v: dict) -> None:
    e = expected(v)
    header_path = ROOT / "src/Main/PortVersion.h"
    if header_path.is_file():
        h = header_path.read_text(encoding="utf-8")
        h = rewrite_define(h, "VC_PORT_VERSION", e["port_version"])
        h = rewrite_define(h, "VC_PORT_UPSTREAM_VERSION", e["upstream_version"])
        h = rewrite_define(h, "VC_PORT_UPSTREAM_COMMIT", e["upstream_commit"])
        h = rewrite_define(h, "VC_PORT_UPSTREAM_TAG", e["upstream_tag"])
        h = rewrite_define(h, "VC_PORT_UPSTREAM_GIT", e["upstream_git"])
        h = rewrite_define(h, "VC_PORT_UPSTREAM_RELEASES", e["upstream_releases"])
        h = rewrite_define(h, "VC_PORT_SOURCE_REPO", e["source_repo"])
        h = rewrite_define(h, "VC_PORT_UPDATE_MANIFEST_URL", e["update_manifest"])
        header_path.write_text(h, encoding="utf-8")

    plist = PORTS / "ios/VCPort/Info.plist"
    p = plist.read_text(encoding="utf-8")
    p = rewrite_plist_string(p, "CFBundleShortVersionString", e["port_version"])
    p = rewrite_plist_string(p, "CFBundleVersion", e["android_version_code"])
    p = rewrite_plist_string(p, "VCPortSourceRepo", e["source_repo"])
    p = rewrite_plist_string(p, "VCPortUpdateManifest", e["update_manifest"])
    p = rewrite_plist_string(p, "VCPortUpstreamVersion", e["upstream_version"])
    p = rewrite_plist_string(p, "VCPortUpstreamCommit", e["upstream_commit"])
    p = rewrite_plist_string(p, "VCPortUpstreamTag", e["upstream_tag"])
    p = rewrite_plist_string(p, "VCPortUpstreamGit", e["upstream_git"])
    p = rewrite_plist_string(p, "VCPortUpstreamReleases", e["upstream_releases"])
    plist.write_text(p, encoding="utf-8")

    yml = PORTS / "ios/project.yml"
    y = yml.read_text(encoding="utf-8")
    y = rewrite_yaml_field(y, "MARKETING_VERSION", e["port_version"])
    y = rewrite_yaml_field(y, "CURRENT_PROJECT_VERSION", e["android_version_code"])
    yml.write_text(y, encoding="utf-8")

    fdroid = PORTS / "fdroiddata/metadata/dev.shivampingale.vcport.yml"
    f = fdroid.read_text(encoding="utf-8")
    f = rewrite_yaml_field(f, "CurrentVersion", e["port_version"])
    f = rewrite_yaml_field(f, "CurrentVersionCode", e["android_version_code"])
    fdroid.write_text(f, encoding="utf-8")


def check(v: dict) -> list[str]:
    e = expected(v)
    problems: list[str] = []
    header_path = ROOT / "src/Main/PortVersion.h"
    if header_path.is_file():
        header = header_path.read_text(encoding="utf-8")
        for name, value in (
            ("VC_PORT_VERSION", e["port_version"]),
            ("VC_PORT_UPSTREAM_VERSION", e["upstream_version"]),
            ("VC_PORT_UPSTREAM_COMMIT", e["upstream_commit"]),
            ("VC_PORT_UPSTREAM_TAG", e["upstream_tag"]),
            ("VC_PORT_UPSTREAM_GIT", e["upstream_git"]),
            ("VC_PORT_UPSTREAM_RELEASES", e["upstream_releases"]),
            ("VC_PORT_SOURCE_REPO", e["source_repo"]),
            ("VC_PORT_UPDATE_MANIFEST_URL", e["update_manifest"]),
        ):
            if f'"{value}"' not in header or name not in header:
                problems.append(f"PortVersion.h missing {name}={value}")
    plist = (PORTS / "ios/VCPort/Info.plist").read_text(encoding="utf-8")
    for key, value in (
        ("CFBundleShortVersionString", e["port_version"]),
        ("CFBundleVersion", e["android_version_code"]),
        ("VCPortSourceRepo", e["source_repo"]),
        ("VCPortUpdateManifest", e["update_manifest"]),
        ("VCPortUpstreamVersion", e["upstream_version"]),
        ("VCPortUpstreamCommit", e["upstream_commit"]),
        ("VCPortUpstreamTag", e["upstream_tag"]),
        ("VCPortUpstreamGit", e["upstream_git"]),
        ("VCPortUpstreamReleases", e["upstream_releases"]),
    ):
        if f"<key>{key}</key>" not in plist or f"<string>{value}</string>" not in plist:
            problems.append(f"Info.plist missing {key}={value}")
    yml = (PORTS / "ios/project.yml").read_text(encoding="utf-8")
    if f"MARKETING_VERSION: {e['port_version']}" not in yml:
        problems.append("project.yml MARKETING_VERSION")
    if f"CURRENT_PROJECT_VERSION: {e['android_version_code']}" not in yml:
        problems.append("project.yml CURRENT_PROJECT_VERSION")
    fdroid = (PORTS / "fdroiddata/metadata/dev.shivampingale.vcport.yml").read_text(
        encoding="utf-8"
    )
    if f"CurrentVersion: {e['port_version']}" not in fdroid:
        problems.append("fdroiddata CurrentVersion")
    if f"CurrentVersionCode: {e['android_version_code']}" not in fdroid:
        problems.append("fdroiddata CurrentVersionCode")
    gradle = (PORTS / "android/app/build.gradle").read_text(encoding="utf-8")
    if "versionJson.port_version" not in gradle or "android_version_code" not in gradle:
        problems.append("build.gradle does not read version.json")
    if "UPSTREAM_GIT" not in gradle or "UPSTREAM_RELEASES" not in gradle:
        problems.append("build.gradle missing official VeraCrypt BuildConfig fields")
    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--write", action="store_true")
    args = parser.parse_args()
    if args.check == args.write:
        print("use exactly one of --check or --write", file=sys.stderr)
        return 2
    v = load_version()
    if args.write:
        apply_write(v)
        print("wrote source pins from ports/version.json")
        return 0
    problems = check(v)
    if problems:
        print("source pin out of date:", file=sys.stderr)
        for p in problems:
            print(f"  {p}", file=sys.stderr)
        print("run: python3 ports/scripts/sync_source_pin.py --write", file=sys.stderr)
        return 1
    print("source pins match ports/version.json")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
