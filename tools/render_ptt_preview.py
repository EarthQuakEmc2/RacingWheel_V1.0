#!/usr/bin/env python3
"""Rendered previews of the push-to-talk button. Not photographs of a handset."""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
WHEELS = ROOT / "app" / "src" / "main" / "assets" / "wheels"
DOCS = ROOT / "docs" / "screenshots"
ART = Path("/opt/cursor/artifacts")

W, H = 1600, 900
CARBON = (11, 13, 16, 255)
PANEL = (20, 24, 29, 255)
RAISED = (28, 34, 41, 255)
INK = (232, 230, 227, 255)
MUTED = (142, 150, 158, 255)
YELLOW = (245, 197, 24, 255)
RED = (225, 6, 0, 255)
CYAN = (0, 229, 255, 255)
GREEN = (61, 220, 87, 255)


def font(size: int, bold: bool = False):
    paths = (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    )
    for path in paths:
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def chrome(title: str):
    im = Image.new("RGBA", (W, H), CARBON)
    d = ImageDraw.Draw(im)
    colors = [GREEN] * 5 + [YELLOW] * 3 + [RED] * 3 + [(224, 64, 251, 255), (224, 64, 251, 255), (47, 128, 237, 255), CYAN]
    gap = 6
    x = 16
    usable = W - 32
    lw = (usable - gap * (len(colors) - 1)) // len(colors)
    for c in colors:
        d.rounded_rectangle((x, 8, x + lw, 20), 2, fill=c)
        x += lw + gap
    d.rectangle((0, 28, W, 78), fill=PANEL)
    d.text((16, 42), "RACING WHEEL PRO", fill=INK, font=font(18, True))
    d.text((250, 42), title, fill=YELLOW, font=font(18))
    d.text((W - 420, 42), "192.168.1.20   8 ms   100 Hz", fill=INK, font=font(16))
    return im


def fit(im: Image.Image, box):
    x, y, w, h = box
    scale = min(w / im.width, h / im.height)
    nw, nh = max(1, int(im.width * scale)), max(1, int(im.height * scale))
    resized = im.resize((nw, nh), Image.Resampling.LANCZOS)
    ox, oy = x + (w - nw) // 2, y + (h - nh) // 2
    return resized, (ox, oy, nw, nh)


def radio(base, center, radius, transmitting: bool, editor: bool):
    overlay = Image.new("RGBA", base.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(overlay)
    cx, cy = center
    d.ellipse((cx - radius, cy - radius, cx + radius, cy + radius), fill=(0, 0, 0, 200))
    if transmitting:
        d.ellipse((cx - radius, cy - radius, cx + radius, cy + radius), fill=(225, 6, 0, 140))
    ring = RED if transmitting else YELLOW
    width = 7 if transmitting else 4
    d.ellipse((cx - radius, cy - radius, cx + radius, cy + radius), outline=ring, width=width)
    label = "TX" if transmitting else "PTT"
    d.text((cx, cy), label, fill=(255, 255, 255, 255), font=font(18, True), anchor="mm")
    if editor:
        d.text((cx, cy - radius - 16), "RADIO", fill=YELLOW, font=font(16, True), anchor="mm")
    base.alpha_composite(overlay)


def pill(d, xy, text, color):
    x, y = xy
    f = font(16, True)
    box = d.textbbox((0, 0), text, font=f)
    w, h = box[2] - box[0] + 22, box[3] - box[1] + 12
    d.rounded_rectangle((x, y, x + w, y + h), 14, fill=color[:3] + (210,), outline=color, width=2)
    d.text((x + 11, y + 4), text, fill=(255, 255, 255, 255), font=f)
    return w


def save(im: Image.Image, name: str):
    DOCS.mkdir(parents=True, exist_ok=True)
    ART.mkdir(parents=True, exist_ok=True)
    rgb = im.convert("RGB")
    rgb.save(DOCS / name, quality=92)
    rgb.save(ART / name, quality=92)
    print(DOCS / name)


def drive():
    doc = json.loads((WHEELS / "moza_ks.json").read_text())
    im = chrome("MOZA KS")
    d = ImageDraw.Draw(im)
    wheel = Image.open(WHEELS / doc["image"]).convert("RGBA")
    zone = 150
    for box, title, amount, color in (
        ((16, 96, zone - 24, H - 160), "BRAKE", 0.18, RED),
        ((W - zone + 8, 96, zone - 24, H - 160), "THROTTLE", 0.62, GREEN),
    ):
        x, y, w, h = box
        d.rounded_rectangle((x, y, x + w, y + h), 8, fill=RAISED)
        fill_h = int(h * amount)
        d.rounded_rectangle((x, y + h - fill_h, x + w, y + h), 8, fill=color)
        d.text((x + 12, y + h - 36), title, fill=INK, font=font(16, True))
    slot = (zone, 90, W - 2 * zone, H - 160)
    plate, placed = fit(wheel, (0, 0, slot[2], slot[3]))
    canvas = Image.new("RGBA", (slot[2], slot[3]), (0, 0, 0, 0))
    canvas.alpha_composite(plate, (placed[0], placed[1]))
    # Default radio spot: cx 0.50, cy 0.80 of the fitted wheel.
    cx = placed[0] + 0.50 * placed[2]
    cy = placed[1] + 0.80 * placed[3]
    radio(canvas, (cx, cy), max(22, int(0.034 * placed[2])), transmitting=True, editor=False)
    im.alpha_composite(canvas, (slot[0], slot[1]))
    d = ImageDraw.Draw(im)
    x = 430
    x += pill(d, (x, 92), "TRANSMITTING", RED) + 10
    pill(d, (x, 92), "RX  ALEX", CYAN)
    d.rounded_rectangle((560, 132, 1040, 168), 8, fill=PANEL)
    d.text((576, 140), "OFFICIAL-MONZA-1    3 IN", fill=INK, font=font(16, True))
    d.text((860, 140), "SYNC    MUTE", fill=YELLOW, font=font(16, True))
    d.rectangle((0, H - 36, W, H), fill=PANEL)
    d.text((16, H - 28), "Assetto Corsa  ·  game rotation", fill=MUTED, font=font(15))
    d.text((W - 280, H - 28), "CENTER    SHOW HITS    SWEEP", fill=INK, font=font(15))
    save(im, "ptt-drive.jpg")


def editor():
    doc = json.loads((WHEELS / "moza_ks.json").read_text())
    im = chrome("LAYOUT EDITOR")
    wheel = Image.open(WHEELS / doc["image"]).convert("RGBA")
    slot = (20, 96, W - 380, H - 120)
    plate, placed = fit(wheel, (0, 0, slot[2], slot[3]))
    canvas = Image.new("RGBA", (slot[2], slot[3]), (0, 0, 0, 0))
    canvas.alpha_composite(plate, (placed[0], placed[1]))
    dwheel = ImageDraw.Draw(canvas)
    for control in doc["controls"]:
        cx = placed[0] + control["cx"] * placed[2]
        cy = placed[1] + control["cy"] * placed[3]
        rad = control["r"] * placed[2]
        color = tuple(int(control["color"].lstrip("#")[i : i + 2], 16) for i in (0, 2, 4))
        dwheel.ellipse((cx - rad, cy - rad, cx + rad, cy + rad), outline=color + (255,), width=3)
    cx = placed[0] + 0.50 * placed[2]
    cy = placed[1] + 0.80 * placed[3]
    radio(canvas, (cx, cy), max(26, int(0.034 * placed[2])), transmitting=False, editor=True)
    im.alpha_composite(canvas, (slot[0], slot[1]))
    d = ImageDraw.Draw(im)
    panel_x = W - 360
    d.rectangle((panel_x, 78, W, H), fill=PANEL)
    d.text((panel_x + 16, 96), "LAYOUT EDITOR", fill=YELLOW, font=font(16, True))
    d.text((panel_x + 16, 126), "Drag a hotspot. The radio ring is PTT.", fill=MUTED, font=font(14))
    y = 160
    for label in ("ADD BUTTON", "ADD KNOB", "ADD RADIO"):
        d.rounded_rectangle((panel_x + 16, y, panel_x + 150, y + 28), 6, fill=RAISED)
        d.text((panel_x + 28, y + 6), label, fill=YELLOW, font=font(13, True))
        y += 36
    d.text((panel_x + 16, y + 8), "RADIO", fill=YELLOW, font=font(14, True))
    y += 36
    for caption, value in (
        ("Room or server", "OFFICIAL-MONZA-1"),
        ("Your name", "Alex"),
        ("Relay", "wss://myracingwheel.site/api/voice/ws"),
    ):
        d.text((panel_x + 16, y), caption, fill=MUTED, font=font(12))
        d.rounded_rectangle((panel_x + 16, y + 16, W - 16, y + 46), 6, outline=(46, 54, 63, 255))
        d.text((panel_x + 24, y + 22), value, fill=INK, font=font(13))
        y += 58
    d.rounded_rectangle((panel_x + 16, y, panel_x + 90, y + 28), 6, fill=RAISED)
    d.text((panel_x + 28, y + 6), "JOIN", fill=YELLOW, font=font(13, True))
    d.rounded_rectangle((panel_x + 100, y, panel_x + 190, y + 28), 6, fill=RAISED)
    d.text((panel_x + 112, y + 6), "SYNC PC", fill=YELLOW, font=font(13, True))
    d.text((panel_x + 16, y + 40), "PC server  Official Monza #1", fill=MUTED, font=font(13))
    d.text((panel_x + 16, y + 62), "FOLLOW PC  ON", fill=YELLOW, font=font(13, True))
    d.text((panel_x + 16, y + 88), "Listen volume", fill=MUTED, font=font(12))
    d.rounded_rectangle((panel_x + 16, y + 112, W - 16, y + 120), 3, fill=(46, 54, 63, 255))
    d.rounded_rectangle((panel_x + 16, y + 112, panel_x + 180, y + 120), 3, fill=YELLOW)
    d.ellipse((panel_x + 170, y + 106, panel_x + 190, y + 126), fill=YELLOW)
    d.text((panel_x + 16, y + 136), "PTT   ptt   0.500, 0.800", fill=YELLOW, font=font(13, True))
    save(im, "ptt-editor.jpg")


if __name__ == "__main__":
    drive()
    editor()
