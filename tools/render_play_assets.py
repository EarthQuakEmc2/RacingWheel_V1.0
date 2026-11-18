#!/usr/bin/env python3
"""Play listing images rendered from the app chrome and the wheel templates.

Screenshots are 1920x1080 landscape phone frames. Titles use the store-build
generic names. The wheel photographs are the ones shipped in the app; see
play-store/PUBLISHING.md before uploading them.
"""

from __future__ import annotations

import json
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
WHEELS = ROOT / "app" / "src" / "main" / "assets" / "wheels"
OUT = ROOT / "play-store"
W, H = 1920, 1080
CARBON = (11, 13, 16, 255)
PANEL = (20, 24, 29, 255)
RAISED = (28, 34, 41, 255)
INK = (232, 230, 227, 255)
MUTED = (142, 150, 158, 255)
YELLOW = (245, 197, 24, 255)
RED = (225, 6, 0, 255)
GREEN = (61, 220, 87, 255)
CYAN = (0, 229, 255, 255)
BLACK = (0, 0, 0, 255)

FREE = {"moza_ks", "ferrari_sf1000", "mercedes_amg_f1", "mclaren_ascher_dark"}


def font(size: int, bold: bool = False):
    paths = (
        ["/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf"]
        if bold
        else ["/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf"]
    )
    for path in paths:
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def family(style: str) -> str:
    if style == "formula":
        return "F1"
    if style == "road":
        return "Road"
    if style == "cup":
        return "Cup"
    return "GT3"


def generic_names(docs: list[dict]) -> dict[str, str]:
    buckets: dict[str, list[str]] = {}
    for doc in docs:
        buckets.setdefault(family(doc.get("style", "")), []).append(doc["id"])
    names = {}
    for fam, ids in buckets.items():
        for index, wheel_id in enumerate(sorted(ids)):
            suffix = chr(ord("A") + index) if index < 26 else str(index + 1)
            names[wheel_id] = f"{fam} Wheel {suffix}"
    return names


def load():
    docs = []
    for path in sorted(WHEELS.glob("*.json")):
        docs.append(json.loads(path.read_text()))
    return docs


def chrome(title: str) -> Image.Image:
    im = Image.new("RGBA", (W, H), CARBON)
    d = ImageDraw.Draw(im)
    colors = [GREEN] * 6 + [YELLOW] * 4 + [RED] * 4 + [(224, 64, 251, 255), (47, 128, 237, 255), CYAN]
    x = 18
    usable = W - 36
    gap = 6
    lw = (usable - gap * (len(colors) - 1)) // len(colors)
    for color in colors:
        d.rounded_rectangle((x, 10, x + lw, 24), 2, fill=color)
        x += lw + gap
    d.rectangle((0, 36, W, 96), fill=PANEL)
    d.text((20, 52), "RACING WHEEL PRO", fill=INK, font=font(22, True))
    d.text((280, 52), title, fill=YELLOW, font=font(22, True))
    d.rounded_rectangle((W - 250, 48, W - 24, 84), 16, fill=(40, 16, 16, 255))
    d.ellipse((W - 236, 58, W - 220, 74), fill=RED)
    d.text((W - 208, 54), "No PC", fill=INK, font=font(18))
    return im


def fit(im: Image.Image, box):
    x, y, w, h = box
    scale = min(w / im.width, h / im.height)
    nw, nh = max(1, int(im.width * scale)), max(1, int(im.height * scale))
    resized = im.resize((nw, nh), Image.Resampling.LANCZOS)
    ox, oy = x + (w - nw) // 2, y + (h - nh) // 2
    return resized, (ox, oy, nw, nh)


def detent(control, index=0):
    positions = int(control.get("positions", 12))
    step = 360.0 / positions
    clockwise = bool(control.get("clockwise", True))
    signed = step if clockwise else -step
    return (float(control.get("startAngle", -90)) + signed * index) % 360.0


def draw_pointer(base, placed, control):
    ox, oy, nw, nh = placed
    cx = ox + control["cx"] * nw
    cy = oy + control["cy"] * nh
    rad = control["r"] * nw
    theta = math.radians(detent(control, 0))
    tip = (cx + math.cos(theta) * rad * 0.92, cy + math.sin(theta) * rad * 0.92)
    d = ImageDraw.Draw(base)
    d.line((cx, cy, tip[0], tip[1]), fill=YELLOW, width=4)
    d.ellipse((tip[0] - 5, tip[1] - 5, tip[0] + 5, tip[1] + 5), fill=YELLOW)
    d.ellipse((cx - 7, cy - 7, cx + 7, cy + 7), fill=YELLOW)
    gap = rad * 0.62
    chip = max(8, rad * 0.24)
    for side, glyph in ((-1, "-"), (1, "+")):
        x = cx + side * gap
        y = cy
        d.ellipse((x - chip, y - chip, x + chip, y + chip), fill=BLACK, outline=YELLOW)
        d.text((x - 5, y - 10), glyph, fill=INK, font=font(16, True))


def pedal(d, box, title, amount, color):
    x, y, w, h = box
    d.rounded_rectangle((x, y, x + w, y + h), 10, fill=RAISED)
    fill_h = int(h * amount)
    d.rounded_rectangle((x, y + h - fill_h, x + w, y + h), 10, fill=color)
    d.text((x + 16, y + h - 64), title, fill=INK, font=font(18, True))
    d.text((x + 16, y + h - 96), str(int(amount * 100)), fill=INK, font=font(28, True))


def drive(doc, name, pointers=False):
    im = chrome(name)
    d = ImageDraw.Draw(im)
    wheel = Image.open(WHEELS / doc["image"]).convert("RGBA")
    zone = 170
    pedal(d, (18, 112, zone - 28, H - 180), "BRAKE", 0.22, RED)
    pedal(d, (W - zone + 8, 112, zone - 28, H - 180), "THROTTLE", 0.64, GREEN)
    placed_img, placed = fit(wheel, (zone, 110, W - 2 * zone, H - 190))
    im.alpha_composite(placed_img, (placed[0], placed[1]))
    if pointers:
        for control in doc["controls"]:
            if control.get("kind") == "rotary":
                draw_pointer(im, placed, control)
    d = ImageDraw.Draw(im)
    d.rectangle((0, H - 48, W, H), fill=PANEL)
    d.text((20, H - 36), "Any game  ·  game rotation", fill=MUTED, font=font(18))
    d.text((W - 520, H - 36), "CENTER     SHOW HITS     SWEEP", fill=INK, font=font(18, True))
    return im


def garage(docs, names):
    im = chrome("WHEELS")
    d = ImageDraw.Draw(im)
    d.text((24, 112), "WHEEL TEMPLATES", fill=YELLOW, font=font(16, True))
    d.text((24, 140), "Pick a layout, then the wheel in your hands.", fill=MUTED, font=font(18))
    tabs = ["FORMULA", "GT", "CUP", "ROAD"]
    x = 24
    for i, tab in enumerate(tabs):
        width = 150
        fill = YELLOW if i == 1 else RAISED
        ink = BLACK if i == 1 else INK
        d.rounded_rectangle((x, 180, x + width, 220), 16, fill=fill)
        d.text((x + 18, 188), tab, fill=ink, font=font(16, True))
        x += width + 10
    d.rounded_rectangle((24, 236, W - 24, 292), 8, fill=RAISED)
    d.rounded_rectangle((40, 250, 62, 274), 3, fill=YELLOW)
    d.text((76, 248), "Unlock Racing Wheel Premium - $3.99", fill=YELLOW, font=font(22, True))
    shown = []
    for doc in docs:
        if family(doc.get("style", "")) == "GT3":
            shown.append(doc)
    shown.sort(key=lambda doc: (doc["id"] not in FREE, doc["id"]))
    shown = shown[:4]
    card_w, card_h = 440, 700
    left = 24
    top = 312
    for doc in shown:
        locked = doc["id"] not in FREE
        d.rounded_rectangle((left, top, left + card_w, top + card_h), 12, fill=PANEL, outline=YELLOW if not locked else (46, 54, 63, 255))
        wheel = Image.open(WHEELS / doc["image"]).convert("RGBA")
        if locked:
            gray = Image.new("RGBA", wheel.size, (0, 0, 0, 0))
            src = wheel.copy()
            src.putalpha(90)
            gray.alpha_composite(src)
            wheel = gray
        thumb, box = fit(wheel, (left + 16, top + 16, card_w - 32, 460))
        im.alpha_composite(thumb, (box[0], box[1]))
        if locked:
            d.rounded_rectangle((left + card_w - 150, top + 28, left + card_w - 20, top + 60), 4, fill=YELLOW)
            d.text((left + card_w - 136, top + 34), "PREMIUM", fill=BLACK, font=font(16, True))
        title = names[doc["id"]]
        d.text((left + 18, top + 500), title, fill=INK, font=font(22, True))
        blurb = "Locked · Racing Wheel Premium" if locked else f"{sum(1 for c in doc['controls'] if c.get('kind')!='rotary')} buttons"
        d.text((left + 18, top + 540), blurb, fill=YELLOW if locked else CYAN, font=font(18))
        left += card_w + 16
    return im


def connect():
    im = chrome("LINK")
    d = ImageDraw.Draw(im)
    d.text((40, 140), "PC LINK", fill=YELLOW, font=font(18, True))
    d.text((40, 180), "Looking for a PC on this Wi-Fi…", fill=INK, font=font(32, True))
    d.text((40, 240), "This phone  192.168.1.42", fill=MUTED, font=font(20))
    d.rounded_rectangle((40, 300, 1100, 400), 10, fill=RAISED)
    d.text((64, 322), "RIG", fill=INK, font=font(24, True))
    d.text((64, 358), "192.168.1.20    TCP 41512    UDP 41511", fill=MUTED, font=font(18))
    d.text((920, 340), "CONNECT", fill=YELLOW, font=font(18, True))
    d.rounded_rectangle((40, 440, 520, 510), 8, outline=(80, 90, 100, 255))
    d.text((60, 460), "PC IP address", fill=MUTED, font=font(18))
    for i, label in enumerate(("CONNECT IP", "USB / ADB", "DISCONNECT")):
        x = 560 + i * 230
        d.rounded_rectangle((x, 440, x + 210, 510), 8, fill=RAISED)
        d.text((x + 24, 462), label, fill=INK, font=font(18, True))
    return im


def setup():
    im = chrome("SETUP")
    d = ImageDraw.Draw(im)
    d.rectangle((0, 96, 280, H), fill=PANEL)
    for i, (label, on) in enumerate((("STEERING", True), ("PEDALS", False), ("BUTTONS", False), ("PROFILE", False))):
        d.text((28, 130 + i * 56), label, fill=YELLOW if on else MUTED, font=font(20, True))
    d.text((320, 130), "STEERING", fill=YELLOW, font=font(18, True))
    rows = [
        ("Rotation range", "180° lock to lock", 0.16),
        ("Sensitivity", "1.00", 0.44),
        ("Dead zone", "1.0°", 0.08),
        ("Linearity", "1.00  (1 = linear)", 0.33),
        ("Smoothing", "0.35", 0.35),
    ]
    y = 190
    for label, value, amount in rows:
        d.text((320, y), label, fill=INK, font=font(18))
        d.text((1500, y), value, fill=MUTED, font=font(16))
        d.rounded_rectangle((320, y + 36, 1700, y + 48), 4, fill=(40, 46, 54, 255))
        d.rounded_rectangle((320, y + 36, 320 + int(1380 * amount), y + 48), 4, fill=YELLOW)
        y += 90
    return im


def feature_graphic():
    im = Image.new("RGBA", (1024, 500), CARBON)
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 1024, 8), fill=RED)
    # Abstract wheel, no badges.
    cx, cy, r = 760, 250, 170
    d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=INK, width=10)
    d.ellipse((cx - 46, cy - 46, cx + 46, cy + 46), outline=YELLOW, width=6)
    d.line((cx, cy - r + 8, cx, cy + r - 8), fill=(60, 68, 76, 255), width=8)
    d.line((cx - r + 8, cy, cx + r - 8, cy), fill=(60, 68, 76, 255), width=8)
    d.pieslice((cx - r, cy - r, cx + r, cy + r), 200, 250, fill=(225, 6, 0, 40))
    d.text((48, 150), "RACING", fill=INK, font=font(72, True))
    d.text((48, 230), "WHEEL PRO", fill=YELLOW, font=font(72, True))
    d.text((52, 340), "A phone in your hands. A wheel in the game.", fill=MUTED, font=font(24))
    d.text((52, 390), "Free to drive.  Premium wheels $3.99 once.", fill=INK, font=font(22))
    return im


def icon():
    im = Image.new("RGBA", (512, 512), (11, 13, 16, 255))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((0, 0, 512, 512), 96, fill=(11, 13, 16, 255))
    d.ellipse((56, 56, 456, 456), outline=RED, width=36)
    d.ellipse((150, 150, 362, 362), fill=(11, 13, 16, 255))
    d.rectangle((228, 200, 284, 340), fill=YELLOW)
    d.rectangle((70, 228, 170, 284), fill=GREEN)
    d.rectangle((342, 228, 442, 284), fill=(47, 128, 237, 255))
    return im


def main():
    docs = load()
    names = generic_names(docs)
    by_id = {doc["id"]: doc for doc in docs}
    shots = OUT / "screenshots"
    shots.mkdir(parents=True, exist_ok=True)
    images = {
        shots / "phone-drive.png": drive(by_id["moza_ks"], names["moza_ks"], pointers=True),
        shots / "phone-garage.png": garage(docs, names),
        shots / "phone-connect.png": connect(),
        shots / "phone-setup.png": setup(),
        shots / "phone-knob.png": drive(by_id["mercedes_amg_f1"], names["mercedes_amg_f1"], pointers=True),
        OUT / "feature-graphic.png": feature_graphic(),
        OUT / "icon-512.png": icon(),
    }
    for path, image in images.items():
        image.convert("RGB").save(path)
        print(path.name, image.size)


if __name__ == "__main__":
    main()
