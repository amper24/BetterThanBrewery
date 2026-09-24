#!/usr/bin/env python3
"""Rebuild the four translucent title glyphs + icon without Pillow or external assets.

These are transparent HUD vignettes, NOT post-processing shaders. Each PNG is an
original, deterministic pixel illustration so the versioned resource pack can
be regenerated with `python3 tools/make_intoxication_pack.py`.
"""
from __future__ import annotations

import json
import math
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parent.parent / "resource-pack"
TEXTURES = ROOT / "assets/betterthanbrewery/textures/font"
# Vanilla bitmap fonts cap each glyph at 256x256 source pixels.
W, H = 256, 144


def chunk(name: bytes, data: bytes) -> bytes:
    return struct.pack(">I", len(data)) + name + data + struct.pack(">I", zlib.crc32(name + data))


def png(path: Path, width: int, height: int, rows: list[bytes]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    assert len(rows) == height and all(len(row) == width * 4 for row in rows)
    pixels = b"".join(b"\x00" + row for row in rows)
    path.write_bytes(b"\x89PNG\r\n\x1a\n"
                     + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(pixels, 9)) + chunk(b"IEND", b""))


def smooth(lo: float, hi: float, value: float) -> float:
    t = min(1.0, max(0.0, (value - lo) / (hi - lo)))
    return t * t * (3 - 2 * t)


def clip(value: float) -> int:
    return max(0, min(255, round(value)))


def haze(stage: int) -> None:
    # Deep plum shadow, a golden bloom and an offset cyan/pink double vision.
    palettes = [
        ((42, 31, 51), (249, 176, 81), (120, 184, 181), 44),
        ((47, 29, 55), (241, 148, 83), (125, 191, 201), 63),
        ((44, 26, 65), (235, 107, 132), (101, 202, 205), 83),
        ((37, 19, 54), (245, 90, 133), (97, 191, 211), 106),
    ]
    shadow, gold, cyan, strength = palettes[stage - 1]
    rows: list[bytes] = []
    for y in range(H):
        row = bytearray()
        ny = 2 * (y + 0.5) / H - 1
        for x in range(W):
            nx = 2 * (x + 0.5) / W - 1
            radius = math.hypot(nx, ny) / math.sqrt(2)
            angle = math.atan2(ny, nx)
            edge = smooth(0.31, 1.06, radius)
            # Two uneven colored halo arcs, restricted to the periphery.
            first = math.exp(-((radius - (0.73 + 0.026 * math.sin(3 * angle + stage))) / 0.064) ** 2)
            second = math.exp(-((radius - (0.85 + 0.038 * math.cos(4 * angle - stage))) / 0.037) ** 2)
            gold_glow = (first * (0.55 + 0.35 * math.sin(2.5 * angle + stage * 0.6))
                         + second * 0.18) * smooth(0.40, 0.70, radius)
            cyan_glow = (second * (0.45 + 0.34 * math.cos(3 * angle + stage))
                         + first * 0.12) * smooth(0.49, 0.76, radius)
            grain = (((x * 73856093) ^ (y * 19349663) ^ (stage * 83492791)) & 15) / 15
            opacity = (strength * edge + (10 + stage * 3) * gold_glow
                       + (7 + stage * 4) * cyan_glow + grain * stage * edge * 2)
            # Important: fully transparent center keeps the world readable.
            if radius < 0.30:
                opacity = 0
            elif radius < 0.42:
                opacity *= smooth(0.30, 0.42, radius)
            color = [shadow[i] + (gold[i] - shadow[i]) * min(0.85, gold_glow * 0.76)
                     + (cyan[i] - shadow[i]) * min(0.85, cyan_glow * 0.64) for i in range(3)]
            row.extend((clip(color[0]), clip(color[1]), clip(color[2]), clip(opacity)))
        rows.append(bytes(row))
    png(TEXTURES / f"haze_{stage}.png", W, H, rows)


def icon() -> None:
    n = 128
    rows: list[bytes] = []
    for y in range(n):
        row = bytearray()
        for x in range(n):
            r = math.hypot(x - 64, y - 64)
            ambient = max(0.0, 1 - r / 96)
            red = 26 + 23 * ambient
            green = 23 + 10 * ambient
            blue = 38 + 26 * ambient
            # Warm, almost-neon rim around a violet potion bottle.
            ring = math.exp(-((r - 47) / 3.0) ** 2)
            red += 111 * ring
            green += 48 * ring
            blue += 78 * ring
            neck = 55 <= x <= 72 and 27 <= y <= 50
            body = ((x - 64) / 28) ** 2 + ((y - 75) / 36) ** 2 <= 1 and y >= 44
            cap = 52 <= x <= 75 and 22 <= y <= 31
            if body or neck:
                red, green, blue = 97, 103, 146
                if y > 68:
                    red, green, blue = 210 + (y - 68) * 0.25, 91 + (x - 64) * 0.6, 155
                if 48 <= x <= 52 and 56 <= y <= 94:
                    red, green, blue = 207, 212, 220
            if cap:
                red, green, blue = 250, 177, 99
            if (x - 71) ** 2 + (y - 77) ** 2 < 13 or (x - 57) ** 2 + (y - 90) ** 2 < 7:
                red, green, blue = 255, 222, 213
            row.extend((clip(red), clip(green), clip(blue), 255))
        rows.append(bytes(row))
    png(ROOT / "pack.png", n, n, rows)


def main() -> None:
    for stage in range(1, 5):
        haze(stage)
    icon()
    font = ROOT / "assets/betterthanbrewery/font/intoxication.json"
    font.parent.mkdir(parents=True, exist_ok=True)
    font.write_text(json.dumps({"providers": [
        {"type": "bitmap", "file": f"betterthanbrewery:font/haze_{stage}.png",
         "height": 104, "ascent": 52, "chars": [chr(0xE100 + stage)]}
        for stage in range(1, 5)
    ]}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Generated four transparent HUD glyphs in {ROOT}")


if __name__ == "__main__":
    main()
