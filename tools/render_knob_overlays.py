#!/usr/bin/env python3
"""Draw the same knob pointer the app draws, on the real wheel art.

Angle convention matches KnobMath: degrees clockwise from 3 o'clock, y down.
Detent 0 is startAngle. Output is a contact sheet per wheel plus one combined
sheet of the numbered dials used to check the calibration.
"""

from __future__ import annotations

import json
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
WHEELS = ROOT / "app" / "src" / "main" / "assets" / "wheels"
OUT = ROOT / "docs" / "knobs"


def font(size: int):
    for path in (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
    ):
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def detent_deg(start: float, index: int, positions: int, clockwise: bool) -> float:
    step = 360.0 / max(positions, 1)
    signed = step if clockwise else -step
    deg = (start + signed * (index % max(positions, 1))) % 360.0
    return deg


def draw_pointer(im: Image.Image, control: dict, index: int, color=(245, 197, 24)):
    w, h = im.size
    cx = control["cx"] * w
    cy = control["cy"] * h
    rad = control["r"] * w
    deg = detent_deg(
        float(control.get("startAngle", -90)),
        index,
        int(control.get("positions", 12)),
        bool(control.get("clockwise", True)),
    )
    theta = math.radians(deg)
    tip = (cx + math.cos(theta) * rad * 0.92, cy + math.sin(theta) * rad * 0.92)
    d = ImageDraw.Draw(im)
    d.line((cx, cy, tip[0], tip[1]), fill=color + (255,), width=max(2, int(rad * 0.08)))
    d.ellipse((tip[0] - 4, tip[1] - 4, tip[0] + 4, tip[1] + 4), fill=color + (255,))
    d.ellipse((cx - 5, cy - 5, cx + 5, cy + 5), fill=(0, 0, 0, 255), outline=color + (255,))
    gap = rad * 0.62
    f = font(max(11, int(rad * 0.28)))
    for side, glyph in ((-1, "-"), (1, "+")):
        x = cx + side * gap
        y = cy
        r = max(7, rad * 0.24)
        d.ellipse((x - r, y - r, x + r, y + r), fill=(0, 0, 0, 210), outline=color + (255,))
        d.text((x - 4, y - 8), glyph, fill=(255, 255, 255, 255), font=f)


def crop_knob(im: Image.Image, control: dict, scale=280) -> Image.Image:
    w, h = im.size
    cx = control["cx"] * w
    cy = control["cy"] * h
    rad = max(12, control["r"] * w)
    pad = rad * 2.6
    box = (int(cx - pad), int(cy - pad), int(cx + pad), int(cy + pad))
    return im.crop(box).resize((scale, scale), Image.Resampling.LANCZOS)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    focus = []
    for path in sorted(WHEELS.glob("*.json")):
        doc = json.loads(path.read_text())
        im = Image.open(WHEELS / doc["image"]).convert("RGBA")
        rotaries = [c for c in doc["controls"] if c.get("kind") == "rotary"]
        if not rotaries:
            continue
        painted = im.copy()
        for control in rotaries:
            # Show detent 0, the first printed position.
            draw_pointer(painted, control, 0)
        tiles = []
        f = font(15)
        for control in rotaries:
            tile = crop_knob(painted, control)
            label = Image.new("RGBA", (tile.width, 36), (8, 10, 12, 255))
            ImageDraw.Draw(label).text(
                (6, 8),
                f"{control['id']}  p0 @ {control.get('startAngle')}  n={control.get('positions')}",
                fill=(245, 197, 24, 255),
                font=f,
            )
            frame = Image.new("RGBA", (tile.width, tile.height + 36), (8, 10, 12, 255))
            frame.paste(label, (0, 0))
            frame.paste(tile, (0, 36))
            tiles.append(frame)
            if doc["id"] in {"moza_ks", "ferrari_sf1000", "mercedes_amg_f1", "mclaren_ascher_dark"}:
                focus.append(frame)
        cols = min(4, len(tiles))
        rows = (len(tiles) + cols - 1) // cols
        sheet = Image.new("RGB", (cols * tiles[0].width, rows * tiles[0].height), (8, 10, 12))
        for i, tile in enumerate(tiles):
            sheet.paste(tile.convert("RGB"), ((i % cols) * tile.width, (i // cols) * tile.height))
        sheet.save(OUT / f"{doc['id']}.jpg", quality=88)
    if focus:
        cols = 3
        w, h = focus[0].size
        rows = (len(focus) + cols - 1) // cols
        board = Image.new("RGB", (cols * w, rows * h), (8, 10, 12))
        for i, tile in enumerate(focus):
            board.paste(tile.convert("RGB"), ((i % cols) * w, (i // cols) * h))
        board.save(OUT / "free-wheels-detent-0.jpg", quality=90)
        print("focus", board.size, "tiles", len(focus))
    print("wrote", OUT)


if __name__ == "__main__":
    main()
