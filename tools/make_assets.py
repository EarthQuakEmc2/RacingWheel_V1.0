#!/usr/bin/env python3
"""Build wheel template JSON and transparent PNGs from the MOZA reference renders.

Coordinates are pixel centers measured on the source images (color peaks for
buttons, grid alignment for rotary knobs). Radius is in source pixels.
Normalized fields in the JSON are relative to the image (r is a fraction of width).
"""

from __future__ import annotations

import json
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SRC = {
    "moza_fsr_screen.png": ROOT / "tools" / "sources" / "moza_fsr_screen.png",
    "moza_ks_round.png": ROOT / "tools" / "sources" / "moza_ks_round.png",
    "moza_fsr2_dash.png": ROOT / "tools" / "sources" / "moza_fsr2_dash.png",
    "moza_gs_5knob.png": ROOT / "tools" / "sources" / "moza_gs_5knob.png",
}
OUT = ROOT / "app" / "src" / "main" / "assets" / "wheels"
PREVIEW = Path("/tmp/wheelmap")


def controls_fsr():
    # 1280 x 720
    return [
        ("drs", "DRS", "button", "#00E5D0", 291, 221, 18),
        ("box", "BOX", "button", "#F5C400", 357, 181, 18),
        ("s1", "S1", "button", "#E040B0", 427, 170, 18),
        ("k", "K", "button", "#FF3355", 459, 301, 17),
        ("enc_l", "EL", "rotary", "#9AA0A8", 438, 250, 16),
        ("pl", "PL", "button", "#C040C8", 853, 170, 17),
        ("blu", "BLU", "button", "#3A78E0", 923, 181, 17),
        ("p", "P", "button", "#F5C400", 988, 221, 18),
        ("tr", "TR", "button", "#21C45A", 822, 301, 17),
        ("enc_r", "ER", "rotary", "#9AA0A8", 842, 250, 16),
        ("r", "R", "button", "#FF2A2A", 573, 412, 18),
        ("n", "N", "button", "#3DDC57", 708, 412, 18),
        ("map", "MAP", "rotary", "#7CFF4A", 490, 430, 42),
        ("turbo", "TURBO", "rotary", "#F5C400", 790, 430, 42),
        ("tc", "TC", "rotary", "#3DDC57", 545, 518, 40),
        ("ers", "ERS", "rotary", "#F0A000", 640, 480, 36),
        ("abs", "ABS", "rotary", "#FF3355", 735, 518, 40),
    ]


def controls_ks():
    # 601 x 397 — GT wheel, 3 rotaries. Centers read off a 20px grid.
    return [
        ("fl", "FL", "button", "#00E5E5", 132, 96, 13),
        ("box", "BOX", "button", "#3EC6FF", 172, 114, 13),
        ("p", "P", "button", "#FF4FA3", 172, 152, 13),
        ("drs_l", "DRS", "button", "#8B9098", 165, 188, 12),
        ("s1", "S1", "button", "#C040C8", 208, 180, 13),
        ("r", "R", "button", "#F5C400", 165, 230, 13),
        ("tc_btn", "TC", "button", "#F5C400", 460, 98, 13),
        ("drs_r", "DRS", "button", "#FF6A00", 432, 118, 13),
        ("wp", "WP", "button", "#00E5E5", 426, 155, 13),
        ("pc", "PC", "button", "#C040C8", 376, 182, 13),
        ("n", "N", "button", "#3DDC57", 427, 235, 13),
        ("turbo", "TURBO", "rotary", "#FF8A00", 298, 222, 34),
        ("tc", "TC", "rotary", "#C8CDD4", 228, 255, 18),
        ("abs", "ABS", "rotary", "#C8CDD4", 368, 255, 18),
    ]


def controls_fsr2():
    # 666 x 437
    return [
        ("drs", "DRS", "button", "#00E5E5", 84, 122, 12),
        ("s2", "S2", "button", "#E040B0", 129, 93, 11),
        ("s1", "S1", "button", "#C040C8", 177, 86, 12),
        ("k", "K", "button", "#FF3355", 197, 175, 12),
        ("k2", "K2", "button", "#C040C8", 475, 86, 12),
        ("p", "P", "button", "#F5C400", 568, 122, 12),
        ("tr", "TR", "button", "#21C45A", 454, 176, 12),
        ("r", "R", "button", "#FF2A2A", 278, 255, 14),
        ("n", "N", "button", "#3DDC57", 373, 254, 14),
        ("mgu", "MGU", "rotary", "#7CFF4A", 220, 255, 32),
        ("mix", "MIX", "rotary", "#F5C400", 435, 255, 32),
        ("ers", "ERS", "rotary", "#F0A000", 325, 288, 26),
        ("tc", "TC", "rotary", "#3DDC57", 258, 318, 30),
        ("abs", "ABS", "rotary", "#FF3355", 390, 318, 30),
    ]


def controls_gs():
    # 665 x 402 — five knobs
    return [
        ("l_blu", "BLU", "button", "#2F80ED", 141, 96, 12),
        ("l_mag", "MAG", "button", "#E040FB", 181, 89, 11),
        ("l_cya", "CYA", "button", "#00E5E5", 102, 119, 12),
        ("l_red", "RED", "button", "#FF3355", 209, 166, 12),
        ("l_yel", "YEL", "button", "#F5C400", 198, 246, 12),
        ("r_mag", "MAG", "button", "#E040FB", 467, 89, 11),
        ("r_red", "RED", "button", "#FF3355", 507, 96, 12),
        ("r_yel", "YEL", "button", "#F5C400", 547, 119, 12),
        ("r_cya", "CYA", "button", "#00E5E5", 439, 166, 12),
        ("r_grn", "GRN", "button", "#3DDC57", 451, 246, 12),
        ("map", "MAP", "rotary", "#FF7AD9", 292, 168, 24),
        ("turbo", "TURBO", "rotary", "#FF5A7A", 356, 168, 24),
        ("tc", "TC", "rotary", "#7CFF4A", 268, 238, 23),
        ("abs", "ABS", "rotary", "#F0A000", 325, 252, 22),
        ("diff", "DIFF", "rotary", "#7CFF4A", 386, 238, 23),
    ]


TEMPLATES = [
    {
        "id": "moza_fsr",
        "name": "MOZA FSR",
        "maker": "MOZA Racing",
        "manufacturer": "MOZA",
        "style": "formula",
        "image": "moza_fsr_screen.png",
        "blurb": "Formula wheel with a dashboard screen, face rotaries, and grip buttons.",
        "controls": controls_fsr,
    },
    {
        "id": "moza_ks",
        "name": "MOZA KS",
        "maker": "MOZA Racing",
        "manufacturer": "MOZA",
        "style": "gt",
        "image": "moza_ks_round.png",
        "blurb": "GT wheel with twin button stacks and three rotaries.",
        "controls": controls_ks,
    },
    {
        "id": "moza_fsr2",
        "name": "MOZA FSR2",
        "maker": "MOZA Racing",
        "manufacturer": "MOZA",
        "style": "formula",
        "image": "moza_fsr2_dash.png",
        "blurb": "Formula wheel with a wide dash screen and five face rotaries.",
        "controls": controls_fsr2,
    },
    {
        "id": "moza_gs",
        "name": "MOZA GS",
        "maker": "MOZA Racing",
        "manufacturer": "MOZA",
        "style": "gt",
        "image": "moza_gs_5knob.png",
        "blurb": "GT wheel with five rotaries across the spoke.",
        "controls": controls_gs,
    },
]


def knock_out_white(im: Image.Image) -> Image.Image:
    """Flood-fill near-white background from the corners so the dash stays opaque."""
    arr = np.array(im.convert("RGBA"))
    h, w = arr.shape[:2]
    rgb = arr[:, :, :3].astype(np.int16)
    mn = rgb.min(axis=2)
    mx = rgb.max(axis=2)
    near_white = (mn > 236) & ((mx - mn) < 18)
    seen = np.zeros((h, w), dtype=bool)
    q: deque[tuple[int, int]] = deque()
    for x, y in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
        if near_white[y, x]:
            q.append((y, x))
            seen[y, x] = True
    while q:
        y, x = q.popleft()
        arr[y, x, 3] = 0
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                ny, nx = y + dy, x + dx
                if 0 <= ny < h and 0 <= nx < w and not seen[ny, nx] and near_white[ny, nx]:
                    seen[ny, nx] = True
                    q.append((ny, nx))
    # Feather pixels that touch transparency and are still very light.
    alpha = arr[:, :, 3]
    for _ in range(2):
        transparent = alpha == 0
        pad = np.pad(transparent, 1, constant_values=False)
        neighbor = (
            pad[0:-2, 0:-2]
            | pad[0:-2, 1:-1]
            | pad[0:-2, 2:]
            | pad[1:-1, 0:-2]
            | pad[1:-1, 2:]
            | pad[2:, 0:-2]
            | pad[2:, 1:-1]
            | pad[2:, 2:]
        )
        fade = neighbor & (mn > 228) & ((mx - mn) < 22) & (alpha > 0)
        alpha[fade] = 0
        arr[:, :, 3] = alpha
    return Image.fromarray(arr)


def build_controls(raw, w, h):
    button_n = 1
    slot = 0
    out = []
    for item in raw:
        cid, label, kind, color, x, y, r = item
        entry = {
            "id": cid,
            "label": label,
            "kind": kind,
            "shape": "circle",
            "cx": round(x / w, 5),
            "cy": round(y / h, 5),
            "r": round(r / w, 5),
            "color": color,
        }
        if kind == "button":
            entry["defaultButton"] = button_n
            button_n += 1
        else:
            entry["positions"] = 12
            entry["knobSlot"] = slot
            entry["decButton"] = button_n
            button_n += 1
            entry["incButton"] = button_n
            button_n += 1
            slot += 1
        out.append(entry)
    return out, button_n - 1


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    PREVIEW.mkdir(parents=True, exist_ok=True)
    for spec in TEMPLATES:
        src = SRC[spec["image"]]
        im = Image.open(src).convert("RGBA")
        w, h = im.size
        controls, outputs = build_controls(spec["controls"](), w, h)
        doc = {
            "schema": "racing-wheel-pro.template/v1",
            "id": spec["id"],
            "name": spec["name"],
            "maker": spec["maker"],
            "manufacturer": spec.get("manufacturer", ""),
            "style": spec["style"],
            "image": spec["image"],
            "imageWidth": w,
            "imageHeight": h,
            "blurb": spec["blurb"],
            "hitSlop": 0.008,
            "outputButtons": outputs,
            "controls": controls,
        }
        (OUT / f"{spec['id']}.json").write_text(json.dumps(doc, indent=2) + "\n")
        cut = knock_out_white(im)
        cut.save(OUT / spec["image"])
        # Alignment proof on the original plate.
        vis = im.convert("RGBA")
        dr = ImageDraw.Draw(vis)
        for c in controls:
            cx, cy = c["cx"] * w, c["cy"] * h
            rad = c["r"] * w
            col = c["color"]
            dr.ellipse([cx - rad, cy - rad, cx + rad, cy + rad], outline=col, width=2)
            dr.line([(cx - 4, cy), (cx + 4, cy)], fill="#FFFFFF")
            dr.line([(cx, cy - 4), (cx, cy + 4)], fill="#FFFFFF")
            dr.text((cx + rad + 2, cy - 6), c["label"], fill="#FFE14A")
        vis.save(PREVIEW / f"align_{spec['id']}.png")
        face = sum(1 for c in controls if c["kind"] == "button")
        rot = sum(1 for c in controls if c["kind"] == "rotary")
        print(f"{spec['id']}: {w}x{h} buttons={face} rotaries={rot} outputs={outputs}")


if __name__ == "__main__":
    main()
