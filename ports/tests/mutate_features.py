#!/usr/bin/env python3
"""Large-scale mutation campaign for the phone feature source.

One mutant is one operator or boolean return, on Android or iPhone.
The host suite runs against that single change. A failure kills the mutant.
The original file is written back after every mutant, including on error.

This does not boot an emulator and does not upload the report.

    python3 ports/tests/mutate_features.py
    python3 ports/tests/mutate_features.py --count
"""

from __future__ import annotations

import argparse
import io
import sys
import traceback
import unittest
from dataclasses import dataclass
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from repo_paths import PORTS  # noqa: E402

SWAPS = (
    ("<=", "<"),
    (">=", ">"),
    ("!=", "=="),
    ("==", "!="),
    ("&&", "||"),
    ("||", "&&"),
    ("return false", "return true"),
    ("return true", "return false"),
    ("return false", "return true"),
)

# Swift uses the same tokens. `return false` is Kotlin; Swift uses `return false` too
# in some spots and `return true`. Also flip Swift Bool literals that are alone.
SWIFT_EXTRA = (
    ("return false", "return true"),
    ("return true", "return false"),
)

SUITE_NAMES = (
    "test_features",
    "test_contracts",
    "test_agile",
    "test_contrib",
    "test_quality",
    "test_phases",
    "test_factors",
    "test_wipe",
)


@dataclass(frozen=True)
class Mutant:
    path: Path
    index: int
    old: str
    new: str
    line: int

    def label(self) -> str:
        rel = self.path
        try:
            rel = self.path.relative_to(PORTS.parent)
        except ValueError:
            pass
        return f"{rel}:{self.line} {self.old!r} -> {self.new!r}"


def feature_files() -> list[Path]:
    roots = [
        PORTS / "android/app/src/main/java/dev/shivampingale/vcport",
        PORTS / "ios/VCPort",
    ]
    found: list[Path] = []
    for root in roots:
        if not root.is_dir():
            continue
        for path in sorted(root.rglob("*")):
            if path.suffix not in {".kt", ".swift"}:
                continue
            if "Test" in path.name or path.name.endswith("Tests.swift"):
                continue
            found.append(path)
    return found


def _in_comment_or_string(line: str, index: int) -> bool:
    if line.lstrip().startswith(("//", "*", "/*")):
        return True
    quotes = 0
    i = 0
    while i < index and i < len(line):
        if line[i] == '"' and (i == 0 or line[i - 1] != "\\"):
            quotes += 1
        i += 1
    if quotes % 2 == 1:
        return True
    slash = line.find("//")
    return slash >= 0 and slash < index


def mutants_in(path: Path) -> list[Mutant]:
    text = path.read_text(encoding="utf-8")
    found: list[Mutant] = []
    line_starts = [0]
    for i, ch in enumerate(text):
        if ch == "\n":
            line_starts.append(i + 1)

    def line_no(index: int) -> int:
        line = 1
        for start in line_starts:
            if start <= index:
                line += 1
            else:
                break
        return line - 1

    tokens = SWAPS
    occupied: set[int] = set()
    for old, new in tokens:
        start = 0
        while True:
            index = text.find(old, start)
            if index < 0:
                break
            start = index + 1
            if index in occupied:
                continue
            # Do not treat the inner "==" of "!=" or "<=" of "<=".
            if old == "==" and index > 0 and text[index - 1] in "!<>":
                continue
            if old in {"<", ">"} and False:
                continue
            line_start = text.rfind("\n", 0, index) + 1
            line_end = text.find("\n", index)
            if line_end < 0:
                line_end = len(text)
            line = text[line_start:line_end]
            col = index - line_start
            if _in_comment_or_string(line, col):
                continue
            # A longer token already claimed these characters.
            if any(i in occupied for i in range(index, index + len(old))):
                continue
            for i in range(index, index + len(old)):
                occupied.add(i)
            found.append(Mutant(path, index, old, new, line_no(index)))
    return found


def load_suite() -> unittest.TestSuite:
    loader = unittest.TestLoader()
    return loader.loadTestsFromNames(SUITE_NAMES)


def run_suite() -> bool:
    suite = load_suite()
    stream = io.StringIO()
    result = unittest.TextTestRunner(stream=stream, verbosity=0).run(suite)
    return not result.wasSuccessful()


def campaign(limit: int | None, count_only: bool) -> int:
    originals: dict[Path, str] = {}
    all_mutants: list[Mutant] = []
    for path in feature_files():
        originals[path] = path.read_text(encoding="utf-8")
        all_mutants.extend(mutants_in(path))
    if limit is not None:
        all_mutants = all_mutants[:limit]
    print(f"files {len(originals)}")
    print(f"mutants {len(all_mutants)}")
    if count_only:
        by: dict[str, int] = {}
        for mutant in all_mutants:
            by[mutant.path.name] = by.get(mutant.path.name, 0) + 1
        for name, n in sorted(by.items(), key=lambda item: -item[1]):
            print(f"  {n:4d}  {name}")
        return 0

    killed = 0
    survived: list[str] = []
    try:
        for n, mutant in enumerate(all_mutants, start=1):
            original = originals[mutant.path]
            text = original[: mutant.index] + mutant.new + original[mutant.index + len(mutant.old) :]
            mutant.path.write_text(text, encoding="utf-8")
            try:
                dead = run_suite()
            except Exception:
                traceback.print_exc()
                mutant.path.write_text(original, encoding="utf-8")
                print("campaign aborted: the suite runner failed")
                return 2
            finally:
                mutant.path.write_text(original, encoding="utf-8")
            if dead:
                killed += 1
                mark = "KILLED"
            else:
                survived.append(mutant.label())
                mark = "SURVIVED"
            if n % 25 == 0 or mark == "SURVIVED":
                print(f"{n:4d}/{len(all_mutants)}  {mark}  {mutant.label()}", flush=True)
    finally:
        for path, text in originals.items():
            path.write_text(text, encoding="utf-8")
    total = len(all_mutants)
    score = (100.0 * killed / total) if total else 0.0
    print("")
    print(f"killed {killed}")
    print(f"survived {len(survived)}")
    print(f"score {score:.1f}%")
    if survived:
        print("survived mutants:")
        for label in survived:
            print(f"  {label}")
    return 0 if not survived else 1


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--count", action="store_true")
    parser.add_argument("--limit", type=int, default=0)
    args = parser.parse_args()
    limit = args.limit if args.limit > 0 else None
    return campaign(limit, args.count)


if __name__ == "__main__":
    sys.exit(main())
