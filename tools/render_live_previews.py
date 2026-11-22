#!/usr/bin/env python3
"""Rendered previews of rev lights, the telemetry screen, and a turning knob.

These are not emulator photographs. The phone UI is drawn from the same wheel
PNG and template JSON the app ships. Each picture is labeled as a render.
"""

from __future__ import annotations

import json
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
WHEELS = ROOT / "app" / "src" / "main" / "assets" / "wheels"
OUT = Path("/opt/cursor/artifacts")
DOCS = ROOT / "docs" / "screenshots"
LABEL = "Rendered preview — not an emulator photograph"


def font(size: int, bold: bool = False):
    names = (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    )
    for path in names:
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def load(wheel_id: str):
    doc = json.loads((WHEELS / f"{wheel_id}.json").read_text())
    im = Image.open(WHEELS / doc["image"]).convert("RGBA")
    return doc, im


def color_at(index: int, count: int):
    t = 0 if count <= 1 else index / (count - 1)
    stops = [
        (0.00, (61, 220, 87)),
        (0.40, (198, 240, 0)),
        (0.62, (255, 138, 0)),
        (0.82, (225, 6, 0)),
        (1.00, (61, 107, 255)),
    ]
    for (a, ca), (b, cb) in zip(stops, stops[1:]):
        if t <= b:
            u = 0 if b == a else (t - a) / (b - a)
            return tuple(int(ca[i] + (cb[i] - ca[i]) * u) for i in range(3))
    return stops[-1][1]


def lit_count(level: float, count: int) -> int:
    return max(0, min(count, round(level * count)))


def draw_leds(base: Image.Image, doc: dict, level: float):
    lights = doc.get("revLights")
    if not lights:
        return
    d = ImageDraw.Draw(base)
    w, h = base.size
    x0, y0 = lights["left"] * w, lights["top"] * h
    x1, y1 = lights["right"] * w, lights["bottom"] * h
    count = int(lights["count"])
    d.rounded_rectangle((x0, y0, x1, y1), radius=(y1 - y0) / 2, fill=(16, 16, 18, 230))
    gap = (x1 - x0) * 0.06 / count
    cell = max(1, ((x1 - x0) - gap * (count + 1)) / count)
    pad = (y1 - y0) * 0.16
    lit = lit_count(level, count)
    for i in range(count):
        rgb = color_at(i, count)
        fill = rgb + ((255,) if i < lit else (40,))
        left = x0 + gap + i * (cell + gap)
        d.rounded_rectangle((left, y0 + pad, left + cell, y1 - pad), radius=cell / 2, fill=fill)


def draw_dash(base: Image.Image, doc: dict, live: dict | None):
    box = doc["display"]
    d = ImageDraw.Draw(base)
    w, h = base.size
    x0, y0 = box["left"] * w, box["top"] * h
    x1, y1 = box["right"] * w, box["bottom"] * h
    d.rounded_rectangle((x0, y0, x1, y1), radius=8, fill=(16, 20, 24, 242), outline=(58, 69, 80, 255))
    bw, bh = x1 - x0, y1 - y0
    if live is None:
        gear, speed, rpm, lap, delta, extra, delta_color = "–", "—", "—", "--:--.---", "0.00", "NO SIGNAL", (150, 156, 164)
        foot = "P–   F —"
        aids = "TC –   ABS –"
    else:
        gear = live["gear"]
        speed = live["speed"]
        rpm = live["rpm"]
        lap = live["lap"]
        delta = live["delta"]
        extra = ""
        delta_color = (80, 220, 120) if live["delta_ms"] <= 0 else (255, 90, 80)
        foot = live["foot"]
        aids = live["aids"]
    cx, cy = x0 + bw * 0.32, y0 + bh * 0.55
    d.text((cx, y0 + bh * 0.08), aids, fill=(160, 210, 255), font=font(max(10, int(bh * 0.12))), anchor="ma")
    d.text((cx, cy), gear, fill=(255, 255, 255), font=font(max(18, int(bh * 0.42)), True), anchor="mm")
    d.text((cx, y0 + bh * 0.82), f"{speed} km/h", fill=(170, 214, 255), font=font(max(11, int(bh * 0.14))), anchor="ma")
    rx = x0 + bw * 0.74
    d.text((rx, y0 + bh * 0.22), lap, fill=(220, 220, 220), font=font(max(11, int(bh * 0.14))), anchor="mm")
    d.text((rx, y0 + bh * 0.46), delta, fill=delta_color, font=font(max(11, int(bh * 0.14))), anchor="mm")
    d.text((rx, y0 + bh * 0.68), f"{rpm} rpm", fill=(255, 196, 64), font=font(max(11, int(bh * 0.14))), anchor="mm")
    d.text((rx, y0 + bh * 0.88), foot, fill=(160, 210, 255), font=font(max(10, int(bh * 0.12))), anchor="mm")
    if extra:
        d.text((cx, y0 + bh * 0.28), extra, fill=(150, 156, 164), font=font(max(10, int(bh * 0.11))), anchor="mm")


def spin_knob(im: Image.Image, control: dict, steps: int) -> Image.Image:
    w, h = im.size
    cx, cy = control["cx"] * w, control["cy"] * h
    rad = control["r"] * w
    positions = int(control.get("positions", 12))
    clockwise = bool(control.get("clockwise", True))
    step = 360 / positions
    degrees = steps * step * (1 if clockwise else -1)
    side = int(rad * 2.6)
    left, top = int(cx - side / 2), int(cy - side / 2)
    crop = im.crop((left, top, left + side, top + side))
    turned = crop.rotate(-degrees, resample=Image.Resampling.BICUBIC, center=(side / 2, side / 2))
    mask = Image.new("L", (side, side), 0)
    ImageDraw.Draw(mask).ellipse(
        (side / 2 - rad * 0.62, side / 2 - rad * 0.62, side / 2 + rad * 0.62, side / 2 + rad * 0.62),
        fill=255,
    )
    out = im.copy()
    out.paste(turned, (left, top), mask)
    # Pointer at the selected mark. 0° is 3 o'clock, positive is clockwise, y grows down.
    start = float(control.get("startAngle", -90))
    angle = math.radians(start + degrees)
    tip = (cx + math.cos(angle) * rad * 0.92, cy + math.sin(angle) * rad * 0.92)
    draw = ImageDraw.Draw(out)
    draw.line((cx, cy, tip[0], tip[1]), fill=(255, 255, 255, 255), width=3)
    draw.ellipse((tip[0] - 5, tip[1] - 5, tip[0] + 5, tip[1] + 5), fill=(245, 197, 24, 255))
    draw.ellipse((cx - rad * 0.16, cy - rad * 0.16, cx + rad * 0.16, cy + rad * 0.16), fill=(0, 0, 0, 200))
    draw.text((cx, cy), str(steps + 1), fill=(255, 255, 255, 255), font=font(max(12, int(rad * 0.28)), True), anchor="mm")
    return out


def caption(im: Image.Image, title: str) -> Image.Image:
    bar = 36
    canvas = Image.new("RGBA", (im.width, im.height + bar), (12, 14, 16, 255))
    canvas.paste(im, (0, bar), im)
    d = ImageDraw.Draw(canvas)
    d.text((12, 8), f"{LABEL}  ·  {title}", fill=(245, 197, 24, 255), font=font(16, True))
    return canvas


def fit_width(im: Image.Image, width: int) -> Image.Image:
    if im.width == width:
        return im
    height = int(im.height * width / im.width)
    return im.resize((width, height), Image.Resampling.LANCZOS)


def save(im: Image.Image, name: str):
    OUT.mkdir(parents=True, exist_ok=True)
    DOCS.mkdir(parents=True, exist_ok=True)
    rgb = im.convert("RGB")
    rgb.save(OUT / name, quality=90)
    rgb.save(DOCS / name, quality=90)
    print(name, rgb.size)


def main():
    ks_doc, ks = load("moza_ks")
    ks_view = ks.copy()
    draw_leds(ks_view, ks_doc, 0.55)
    save(caption(fit_width(ks_view, 1200), "MOZA KS rev lights at about half throttle"), "rev-lights-ks.jpg")

    sf_doc, sf = load("ferrari_sf1000")
    sf_view = sf.copy()
    draw_leds(sf_view, sf_doc, 0.72)
    draw_dash(
        sf_view,
        sf_doc,
        {
            "gear": "4",
            "speed": "187",
            "rpm": "7400",
            "lap": "1:26.321",
            "delta": "-0.42",
            "delta_ms": -420,
            "foot": "P3   F 55%",
            "aids": "TC 4   ABS 2",
        },
    )
    save(caption(fit_width(sf_view, 1400), "SF1000 live telemetry from Assetto Corsa"), "telemetry-sf1000.jpg")

    fsr_doc, fsr = load("moza_fsr")
    fsr_view = fsr.copy()
    draw_leds(fsr_view, fsr_doc, 0.4)
    draw_dash(
        fsr_view,
        fsr_doc,
        {
            "gear": "3",
            "speed": "142",
            "rpm": "6800",
            "lap": "0:48.102",
            "delta": "+0.18",
            "delta_ms": 180,
            "foot": "P1   F 71%",
            "aids": "TC 2   ABS 1",
        },
    )
    save(caption(fit_width(fsr_view, 1400), "MOZA FSR screen and shift lights"), "telemetry-fsr.jpg")

    turbo = next(c for c in ks_doc["controls"] if c["id"] == "turbo")
    before = spin_knob(ks, turbo, 0)
    after = spin_knob(ks, turbo, 4)
    w, h = ks.size
    cx, cy, rad = turbo["cx"] * w, turbo["cy"] * h, turbo["r"] * w * 1.35
    def zoom(im):
        box = (int(cx - rad), int(cy - rad), int(cx + rad), int(cy + rad))
        return im.crop(box).resize((420, 420), Image.Resampling.LANCZOS)
    pair = Image.new("RGBA", (860, 420), (12, 14, 16, 255))
    pair.paste(zoom(before), (0, 0))
    pair.paste(zoom(after), (440, 0))
    d = ImageDraw.Draw(pair)
    d.text((16, 12), "Position 1", fill=(255, 255, 255), font=font(20, True))
    d.text((456, 12), "Position 5", fill=(245, 197, 24), font=font(20, True))
    save(caption(pair, "MOZA KS turbo knob before and after four detents"), "knob-ks-before-after.jpg")


if __name__ == "__main__":
    main()
