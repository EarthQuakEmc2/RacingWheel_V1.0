#!/usr/bin/env python3
"""Render landscape UI previews from the same wheel JSON the Android app loads.

These are not emulator screenshots. They composite the real template art, hotspot
rings, and the app's carbon / signal-colour chrome so the layout can be checked
without a device.
"""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
WHEELS = ROOT / "app" / "src" / "main" / "assets" / "wheels"
OUT = Path("/opt/cursor/artifacts")

W, H = 1600, 900
CARBON = (11, 13, 16, 255)
PANEL = (20, 24, 29, 255)
INK = (232, 230, 227, 255)
MUTED = (142, 150, 158, 255)
YELLOW = (245, 197, 24, 255)
RED = (225, 6, 0, 255)
GREEN = (61, 220, 87, 255)
CYAN = (0, 229, 255, 255)


def font(size: int):
    for path in (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    ):
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def hex_rgb(value: str):
    value = value.removeprefix("#")
    return tuple(int(value[i : i + 2], 16) for i in (0, 2, 4)) + (255,)


def fit(im: Image.Image, box):
    x, y, w, h = box
    scale = min(w / im.width, h / im.height)
    nw, nh = int(im.width * scale), int(im.height * scale)
    resized = im.resize((nw, nh), Image.Resampling.LANCZOS)
    ox, oy = x + (w - nw) // 2, y + (h - nh) // 2
    return resized, (ox, oy, nw, nh)


def chrome(title: str, link: str):
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
    f = font(18)
    d.text((16, 42), "RACING WHEEL PRO", fill=INK, font=f)
    d.text((250, 42), title, fill=YELLOW, font=f)
    d.text((W - 280, 42), link, fill=INK, font=font(16))
    return im


def draw_hotspots(base, placed, doc, labels=True):
    ox, oy, nw, nh = placed
    d = ImageDraw.Draw(base)
    f = font(14)
    for c in doc["controls"]:
        cx = ox + c["cx"] * nw
        cy = oy + c["cy"] * nh
        rad = c["r"] * nw
        col = hex_rgb(c["color"])
        d.ellipse((cx - rad, cy - rad, cx + rad, cy + rad), outline=col, width=3)
        if labels:
            d.text((cx + rad + 3, cy - 8), c["label"], fill=YELLOW, font=f)


def pedal(d, box, title, amount, color):
    x, y, w, h = box
    d.rounded_rectangle((x, y, x + w, y + h), 8, fill=(28, 32, 38, 255))
    fill_h = int(h * amount)
    d.rounded_rectangle((x, y + h - fill_h, x + w, y + h), 8, fill=color)
    f = font(16)
    d.text((x + 12, y + h - 36), title, fill=INK, font=f)
    d.text((x + 12, y + h - 58), f"{int(amount * 100)}", fill=INK, font=font(22))


def drive(doc, amount_thr=0.62, amount_brk=0.18, hotspots=False, name="drive"):
    im = chrome(doc["name"], "192.168.1.20   8 ms   100 Hz")
    d = ImageDraw.Draw(im)
    wheel = Image.open(WHEELS / doc["image"]).convert("RGBA")
    zone = 150
    pedal(d, (16, 96, zone - 24, H - 140), "BRAKE", amount_brk, RED)
    pedal(d, (W - zone + 8, 96, zone - 24, H - 140), "THROTTLE", amount_thr, GREEN)
    placed_img, placed = fit(wheel, (zone, 90, W - 2 * zone, H - 150))
    im.alpha_composite(placed_img, (placed[0], placed[1]))
    if hotspots:
        draw_hotspots(im, placed, doc, labels=True)
    d = ImageDraw.Draw(im)
    d.rectangle((0, H - 36, W, H), fill=PANEL)
    d.text((16, H - 28), "Assetto Corsa  ·  game rotation", fill=MUTED, font=font(15))
    d.text((W - 280, H - 28), "CENTER    SHOW HITS    SWEEP", fill=INK, font=font(15))
    OUT.mkdir(parents=True, exist_ok=True)
    im.convert("RGB").save(OUT / name, quality=92)
    print(name)


def connect_screen():
    im = chrome("LINK", "No PC")
    d = ImageDraw.Draw(im)
    f = font(28)
    d.text((40, 120), "Looking for a PC on this Wi-Fi…", fill=INK, font=f)
    d.text((40, 170), "This phone  192.168.1.42", fill=MUTED, font=font(18))
    d.rounded_rectangle((40, 230, 900, 320), 10, fill=PANEL)
    d.text((60, 250), "RIG-01", fill=INK, font=font(22))
    d.text((60, 282), "192.168.1.20    TCP 41512    UDP 41511", fill=MUTED, font=font(16))
    d.text((760, 262), "CONNECT", fill=YELLOW, font=font(16))
    d.text((40, 360), "USB: adb reverse tcp:41512 tcp:41512 && adb reverse tcp:41511 tcp:41511", fill=MUTED, font=font(16))
    im.convert("RGB").save(OUT / "ui-connect.jpg", quality=92)
    print("ui-connect.jpg")


def main():
    docs = {}
    for path in sorted(WHEELS.glob("*.json")):
        docs[path.stem] = json.loads(path.read_text())
    drive(docs["moza_fsr"], hotspots=False, name="ui-drive-fsr.jpg")
    drive(docs["moza_fsr"], hotspots=True, name="ui-editor-fsr.jpg")
    drive(docs["moza_ks"], 0.2, 0.05, True, "ui-hotspots-ks.jpg")
    drive(docs["moza_fsr2"], 0.8, 0.4, True, "ui-hotspots-fsr2.jpg")
    drive(docs["moza_gs"], 0.45, 0.1, True, "ui-hotspots-gs.jpg")
    connect_screen()
    setup = chrome("SETUP", "192.168.1.20   8 ms")
    d = ImageDraw.Draw(setup)
    d.rectangle((0, 78, 220, H), fill=PANEL)
    for i, (label, on) in enumerate((("STEERING", True), ("PEDALS", False), ("BUTTONS", False), ("PROFILE", False))):
        d.text((24, 110 + i * 36), label, fill=YELLOW if on else MUTED, font=font(16))
    rows = [
        ("Rotation range", "180° lock to lock"),
        ("Sensitivity", "1.00"),
        ("Dead zone", "1.0°"),
        ("Linearity", "1.00  (1 = linear)"),
        ("Smoothing", "0.35"),
        ("Sensor", "auto"),
        ("Zero reference", "auto"),
    ]
    y = 110
    for label, value in rows:
        d.text((250, y), label, fill=INK, font=font(18))
        d.text((1100, y), value, fill=YELLOW, font=font(16))
        d.rectangle((250, y + 28, 1400, y + 34), fill=(60, 20, 20, 255))
        d.rectangle((250, y + 28, 700, y + 34), fill=RED)
        y += 52
    setup.convert("RGB").save(OUT / "ui-setup.jpg", quality=92)
    print("ui-setup.jpg")


if __name__ == "__main__":
    main()
