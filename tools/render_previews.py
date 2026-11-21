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

# Keep these in step with ShifterZones.kt.
ZONE_W = 0.20
ZONE_H = 0.32
ZONE_CY = 0.50


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


def zone_box(doc, side, placed):
    ox, oy, nw, nh = placed
    bounds = doc.get("wheelBounds") or {"left": 0, "top": 0, "right": 1, "bottom": 1}
    spec = (doc.get("shifters") or {}).get(side) or {"width": ZONE_W, "height": ZONE_H, "centerY": ZONE_CY}
    ww = bounds["right"] - bounds["left"]
    wh = bounds["bottom"] - bounds["top"]
    zw = spec["width"] * ww
    zh = spec["height"] * wh
    cy = bounds["top"] + spec["centerY"] * wh
    top = cy - zh / 2
    bottom = cy + zh / 2
    if side == "up":
        left, right = bounds["left"], bounds["left"] + zw
    else:
        right, left = bounds["right"], bounds["right"] - zw
    return (ox + left * nw, oy + top * nh, ox + right * nw, oy + bottom * nh)


def paint_zones(canvas, placed, doc, handles=False):
    """Paddle rectangles with each overlapping button circle cut out."""
    ox, oy, nw, nh = placed
    hit = doc.get("hitSlop", 0.008)
    circles = []
    for control in doc.get("controls", []):
        rad = (control["r"] + hit + 0.012) * nw + 22
        px = ox + control["cx"] * nw
        py = oy + control["cy"] * nh
        circles.append((px, py, rad))
    label = font(26)
    for side, rgb, text in (("up", (0, 229, 255), "UP"), ("down", (245, 197, 24), "DN")):
        box = zone_box(doc, side, placed)
        mask = Image.new("L", canvas.size, 0)
        md = ImageDraw.Draw(mask)
        md.rounded_rectangle(box, 10, fill=150)
        for px, py, rad in circles:
            md.ellipse((px - rad, py - rad, px + rad, py + rad), fill=0)
        layer = Image.new("RGBA", canvas.size, rgb + (255,))
        layer.putalpha(mask)
        canvas.alpha_composite(layer)
        d = ImageDraw.Draw(canvas)
        d.rounded_rectangle(box, 10, outline=rgb + (230,), width=3)
        for px, py, rad in circles:
            if box[0] - rad < px < box[2] + rad and box[1] - rad < py < box[3] + rad:
                d.ellipse((px - rad, py - rad, px + rad, py + rad), outline=rgb + (230,), width=3)
        d.text(((box[0] + box[2]) / 2, (box[1] + box[3]) / 2), text, fill=(255, 255, 255, 230), font=label, anchor="mm")
        if handles:
            inner_x = box[2] if side == "up" else box[0]
            for spot in ((inner_x, (box[1] + box[3]) / 2), ((box[0] + box[2]) / 2, box[1]), ((box[0] + box[2]) / 2, box[3])):
                d.ellipse((spot[0] - 8, spot[1] - 8, spot[0] + 8, spot[1] + 8), fill=rgb + (255,))


def wheel_plate(wheel: Image.Image, slot, angle=0.0, zones=False, doc=None, handles=False):
    """Fit the wheel in slot-local space, optionally with paddle rectangles, then rotate clockwise."""
    _, _, sw, sh = slot
    canvas = Image.new("RGBA", (sw, sh), (0, 0, 0, 0))
    placed_img, (ox, oy, nw, nh) = fit(wheel, (0, 0, sw, sh))
    canvas.alpha_composite(placed_img, (ox, oy))
    if zones and doc is not None:
        paint_zones(canvas, (ox, oy, nw, nh), doc, handles=handles)
    if angle:
        canvas = canvas.rotate(-angle, resample=Image.Resampling.BICUBIC, center=(sw / 2, sh / 2))
    return canvas, (ox, oy, nw, nh)


def drive(doc, amount_thr=0.62, amount_brk=0.18, hotspots=False, name="drive", zones=False, angle=0.0, brake_gap=0, throttle_gap=0):
    im = chrome(doc["name"], "192.168.1.20   8 ms   100 Hz")
    d = ImageDraw.Draw(im)
    wheel = Image.open(WHEELS / doc["image"]).convert("RGBA")
    zone = 150
    pedal(d, (16 + brake_gap, 96, zone - 24, H - 140), "BRAKE", amount_brk, RED)
    pedal(d, (W - zone + 8 - throttle_gap, 96, zone - 24, H - 140), "THROTTLE", amount_thr, GREEN)
    slot = (zone + brake_gap // 4, 90, W - 2 * zone - (brake_gap + throttle_gap) // 4, H - 150)
    plate, placed_local = wheel_plate(wheel, slot, angle=angle, zones=zones, doc=doc)
    im.alpha_composite(plate, (slot[0], slot[1]))
    if hotspots and not angle:
        placed = (slot[0] + placed_local[0], slot[1] + placed_local[1], placed_local[2], placed_local[3])
        draw_hotspots(im, placed, doc, labels=True)
    d = ImageDraw.Draw(im)
    d.rectangle((0, H - 36, W, H), fill=PANEL)
    d.text((16, H - 28), "Assetto Corsa  ·  game rotation", fill=MUTED, font=font(15))
    d.text((620, H - 28), "Buttons clear", fill=CYAN, font=font(15))
    d.text((W - 280, H - 28), "CENTER    SHOW HITS    SWEEP", fill=INK, font=font(15))
    OUT.mkdir(parents=True, exist_ok=True)
    im.convert("RGB").save(OUT / name, quality=92)
    print(name)


def editor_shifters(doc):
    im = chrome("EDIT", "Layout")
    d = ImageDraw.Draw(im)
    wheel = Image.open(WHEELS / doc["image"]).convert("RGBA")
    slot = (16, 90, W - 340, H - 140)
    plate, _ = wheel_plate(wheel, slot, zones=True, doc=doc, handles=True)
    im.alpha_composite(plate, (slot[0], slot[1]))
    d = ImageDraw.Draw(im)
    d.rectangle((W - 320, 78, W, H), fill=PANEL)
    d.text((W - 304, 92), "LAYOUT EDITOR", fill=YELLOW, font=font(16))
    d.text((W - 304, 118), "Drag an edge, or use the sliders.", fill=MUTED, font=font(13))
    y = 150
    up = doc["shifters"]["up"]
    down = doc["shifters"]["down"]
    for title, spec in (("UP", up), ("DN", down)):
        d.text((W - 304, y), title, fill=YELLOW, font=font(16))
        y += 26
        for label, value, amount in (
            ("Width", f"{int(spec['width']*100)}% of wheel", spec["width"] / 0.45),
            ("Height", f"{int(spec['height']*100)}% of wheel", spec["height"] / 0.65),
            ("Vertical center", f"{int(spec['centerY']*100)}%", spec["centerY"]),
        ):
            d.text((W - 304, y), label, fill=INK, font=font(14))
            d.text((W - 90, y), value, fill=YELLOW, font=font(13))
            d.rectangle((W - 304, y + 20, W - 24, y + 28), fill=(60, 20, 20, 255))
            d.rectangle((W - 304, y + 20, W - 304 + int(280 * amount), y + 28), fill=RED)
            y += 46
    d.rounded_rectangle((W - 304, y, W - 140, y + 32), 6, fill=YELLOW)
    d.text((W - 292, y + 7), "RESET PADDLES", fill=(0, 0, 0, 255), font=font(13))
    im.convert("RGB").save(OUT / "ui-editor-shifters.jpg", quality=92)
    print("ui-editor-shifters.jpg")


def pedal_setup():
    im = chrome("SETUP", "192.168.1.20   8 ms")
    d = ImageDraw.Draw(im)
    d.rectangle((0, 78, 220, H), fill=PANEL)
    for i, (label, on) in enumerate((("STEERING", False), ("PEDALS", True), ("BUTTONS", False), ("PROFILE", False))):
        d.text((24, 110 + i * 36), label, fill=YELLOW if on else MUTED, font=font(16))
    rows = [
        ("Side zone width", "18% of screen", 0.45),
        ("Brake distance from edge", "4% of width", 0.40),
        ("Throttle distance from edge", "8% of width", 0.80),
    ]
    y = 110
    d.text((250, y), "THROTTLE AND BRAKE", fill=YELLOW, font=font(14))
    y += 36
    for label, value, amount in rows:
        d.text((250, y), label, fill=INK, font=font(18))
        d.text((1180, y), value, fill=YELLOW, font=font(16))
        d.rectangle((250, y + 28, 1500, y + 36), fill=(60, 20, 20, 255))
        d.rectangle((250, y + 28, 250 + int(1250 * amount), y + 36), fill=RED)
        y += 58
    d.rounded_rectangle((250, y, 430, y + 36), 6, fill=YELLOW)
    d.text((268, y + 8), "RESET EDGES", fill=(0, 0, 0, 255), font=font(14))
    y += 56
    d.text((250, y), "Live preview  ·  brake 4%  ·  throttle 8%", fill=MUTED, font=font(14))
    y += 28
    d.rounded_rectangle((250, y, 1500, y + 70), 8, fill=CARBON)
    # 4% and 8% of the preview width, pedals 12% wide.
    span = 1250
    brake_gap = int(span * 0.04)
    thr_gap = int(span * 0.08)
    pedal_w = int(span * 0.12)
    d.rectangle((250 + brake_gap, y + 10, 250 + brake_gap + pedal_w, y + 60), fill=RED)
    d.rectangle((1500 - thr_gap - pedal_w, y + 10, 1500 - thr_gap, y + 60), fill=GREEN)
    im.convert("RGB").save(OUT / "ui-setup-pedals.jpg", quality=92)
    print("ui-setup-pedals.jpg")


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
    drive(docs["moza_ks"], 0.4, 0.22, False, "ui-drive-shifters.jpg", zones=True)
    drive(docs["ferrari_sf1000"], 0.55, 0.2, False, "ui-drive-shifters-sf1000.jpg", zones=True)
    drive(docs["moza_ks"], 0.4, 0.22, False, "ui-drive-shifters-turned.jpg", zones=True, angle=28)
    editor_shifters(docs["moza_ks"])
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
    pedal_setup()
    shots = ROOT / "docs" / "screenshots"
    shots.mkdir(parents=True, exist_ok=True)
    for name in (
        "ui-drive-shifters.jpg",
        "ui-drive-shifters-sf1000.jpg",
        "ui-drive-shifters-turned.jpg",
        "ui-editor-shifters.jpg",
        "ui-setup-pedals.jpg",
    ):
        src = OUT / name
        if src.exists():
            (shots / name.removeprefix("ui-")).write_bytes(src.read_bytes())


if __name__ == "__main__":
    main()
