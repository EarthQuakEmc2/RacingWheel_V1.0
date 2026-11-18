#!/usr/bin/env python3
"""Draw hotspot rings on the transparent wheel plates and check they still hit the wheel.

Writes docs/align/align_<id>.png. A control fails when its center sits in a small
transparent hole, which means the cutout removed that button or knob. A center
in a large spoke opening was already on the photographed backdrop.
"""

from __future__ import annotations

import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

ROOT = Path(__file__).resolve().parents[1]
WHEELS = ROOT / "app" / "src" / "main" / "assets" / "wheels"
ALIGN = ROOT / "docs" / "align"


def checker(w: int, h: int) -> Image.Image:
    cell = 12
    ys, xs = np.indices((h, w))
    dark = ((xs // cell) + (ys // cell)) % 2 == 0
    bg = np.empty((h, w, 4), np.uint8)
    bg[dark] = (20, 22, 26, 255)
    bg[~dark] = (210, 48, 160, 255)
    return Image.fromarray(bg, "RGBA")


def main() -> None:
    ALIGN.mkdir(parents=True, exist_ok=True)
    failed: list[str] = []
    for path in sorted(WHEELS.glob("*.json")):
        doc = json.loads(path.read_text())
        im = Image.open(WHEELS / doc["image"]).convert("RGBA")
        w, h = im.size
        if (w, h) != (doc["imageWidth"], doc["imageHeight"]):
            failed.append(f"{doc['id']}: canvas {w}x{h} != json {doc['imageWidth']}x{doc['imageHeight']}")
        alpha = np.array(im)[:, :, 3]
        holes, _n_holes = ndimage.label(alpha < 16)
        vis = checker(w, h)
        vis.alpha_composite(im)
        dr = ImageDraw.Draw(vis)
        for c in doc["controls"]:
            cx, cy = c["cx"] * w, c["cy"] * h
            rad = max(4.0, c["r"] * w)
            yy, xx = np.ogrid[:h, :w]
            disk = (xx - cx) ** 2 + (yy - cy) ** 2 <= (rad * 0.45) ** 2
            cover = float((alpha[disk] > 128).mean()) if int(disk.sum()) else 0.0
            if cover < 0.55:
                hid = int(holes[int(round(cy)), int(round(cx))])
                area = int((holes == hid).sum()) if hid else 0
                # Large openings and the outside field were backdrop in the photo.
                if hid and area < 1500:
                    failed.append(
                        f"{doc['id']} {c['id']} cover={cover:.2f} hole={area}px at ({cx:.0f},{cy:.0f})"
                    )
            col = c["color"]
            col = c["color"]
            dr.ellipse([cx - rad, cy - rad, cx + rad, cy + rad], outline=col, width=2)
            dr.line([(cx - 5, cy), (cx + 5, cy)], fill="#FFFFFF")
            dr.line([(cx, cy - 5), (cx, cy + 5)], fill="#FFFFFF")
            dr.text((cx + rad + 2, cy - 8), c["label"], fill="#FFE14A")
        vis.convert("RGB").save(ALIGN / f"align_{doc['id']}.png")
        print(f"align_{doc['id']}.png")
    if failed:
        print("FAILED")
        for line in failed:
            print(" ", line)
        raise SystemExit(1)
    print("all hotspots land on the wheel")


if __name__ == "__main__":
    main()
