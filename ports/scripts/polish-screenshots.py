#!/usr/bin/env python3
"""Crop FLAG_SECURE black letterbox bands and add a subtle matte for README shots."""
from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image

SHOTS = Path(__file__).resolve().parents[1] / "docs" / "screenshots"
PAD = 10
MATTE_LIGHT = (232, 238, 244)  # WindowGray — light Original theme
MATTE_DARK = (33, 38, 48)  # visible on GitHub dark without harsh pure black
ROW_BLACK = 24  # max RGB sum to treat a row as letterbox black


def row_mostly_black(im: Image.Image, y: int) -> bool:
    w = im.width
    step = max(1, w // 64)
    dark = 0
    total = 0
    for x in range(0, w, step):
        r, g, b = im.getpixel((x, y))
        total += 1
        if r + g + b <= ROW_BLACK:
            dark += 1
    return dark >= total * 0.92


def content_bounds(im: Image.Image) -> tuple[int, int]:
    w, h = im.size
    top = 0
    for y in range(h):
        if not row_mostly_black(im, y):
            top = y
            break
    bottom = h - 1
    for y in range(h - 1, -1, -1):
        if not row_mostly_black(im, y):
            bottom = y
            break
    return top, bottom


def matte_for(path: Path) -> tuple[int, int, int]:
    if path.name.startswith("08-") or "skin-signal" in path.name:
        return MATTE_DARK
    return MATTE_LIGHT


def polish(path: Path) -> bool:
    im = Image.open(path).convert("RGB")
    top, bottom = content_bounds(im)
    if bottom <= top:
        print(f"skip {path.name}: no content bounds")
        return False
    cropped = im.crop((0, top, im.width, bottom + 1))
    matte = matte_for(path)
    out = Image.new(
        "RGB",
        (cropped.width + PAD * 2, cropped.height + PAD * 2),
        matte,
    )
    out.paste(cropped, (PAD, PAD))
    out.save(path, format="PNG", optimize=True)
    print(f"polished {path.name}: {im.size} -> {out.size}")
    return True


def main(argv: list[str]) -> int:
    targets = [Path(p) for p in argv[1:]] if len(argv) > 1 else sorted(SHOTS.glob("*.png"))
    ok = 0
    for path in targets:
        if not path.is_file() or "thumbs" in path.parts:
            continue
        if polish(path):
            ok += 1
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
