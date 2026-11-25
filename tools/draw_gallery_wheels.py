#!/usr/bin/env python3
"""Original drawn wheel plates for the 0.6.1 preview.

The pictures are painted here. They are not the reference photos.
Buttons, rotaries, the screen, and the rev-light slot are the same
rectangles the app uses, so the hotspots sit on the art.
"""

from __future__ import annotations

import json
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app" / "src" / "main" / "assets" / "wheels"
W, H = 1000, 660


def font(size: int, bold: bool = False):
    path = (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
        if bold
        else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
    )
    return ImageFont.truetype(path, size)


def hex_rgb(value: str):
    value = value.lstrip("#")
    return tuple(int(value[i : i + 2], 16) for i in (0, 2, 4))


def px(x, y=None):
    if y is None:
        return x * W
    return x * W, y * H


class Plate:
    def __init__(self):
        self.im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        self.draw = ImageDraw.Draw(self.im)
        self.controls = []
        self.next_button = 3
        self.display = None
        self.lights = None

    def carbon(self, mask: Image.Image, seed: int):
        rng = random.Random(seed)
        noise = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        pix = noise.load()
        for y in range(0, H, 2):
            for x in range(0, W, 2):
                if mask.getpixel((x, y))[3] < 20:
                    continue
                n = rng.randint(18, 46)
                if rng.random() < 0.04:
                    n = rng.randint(70, 110)
                pix[x, y] = (n, n, n + 2, 255)
                if x + 1 < W:
                    pix[x + 1, y] = pix[x, y]
        noise = noise.filter(ImageFilter.SMOOTH)
        self.im.alpha_composite(noise)

    def fill_mask(self, drawer):
        mask = Image.new("L", (W, H), 0)
        ImageDraw.Draw(mask)
        # drawer paints into an RGBA layer, then we keep its alpha as the mask
        layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        drawer(d)
        self.im.alpha_composite(layer)
        alpha = layer.getchannel("A")
        shade = Image.new("RGBA", (W, H), (22, 24, 28, 255))
        shade.putalpha(alpha)
        base = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        base.alpha_composite(shade)
        self.im = Image.alpha_composite(base, self.im)
        self.draw = ImageDraw.Draw(self.im)
        rgb = Image.new("RGBA", (W, H), (255, 255, 255, 255))
        rgb.putalpha(alpha)
        return rgb

    def grips(self, y=0.56):
        for cx, tilt in ((0.145, 18), (0.855, -18)):
            layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
            d = ImageDraw.Draw(layer)
            x, yy = px(cx, y)
            d.rounded_rectangle((x - 78, yy - 150, x + 78, yy + 150), 70, fill=(12, 12, 14, 255))
            d.rounded_rectangle((x - 62, yy - 132, x + 62, yy + 132), 58, fill=(28, 28, 32, 255))
            d.arc((x - 40, yy - 90, x + 40, yy + 40), 200, 340, fill=(70, 70, 76, 180), width=8)
            self.im.alpha_composite(layer.rotate(tilt, center=(x, yy), resample=Image.BICUBIC))
        self.draw = ImageDraw.Draw(self.im)

    def paddles(self):
        d = self.draw
        left = [(38, 250), (128, 230), (150, 300), (128, 430), (38, 450), (22, 340)]
        right = [(W - x, y) for x, y in left]
        d.polygon(left, fill=(18, 18, 20, 255))
        d.line(left + [left[0]], fill=(90, 90, 96, 255), width=3)
        d.polygon(right, fill=(18, 18, 20, 255))
        d.line(right + [right[0]], fill=(90, 90, 96, 255), width=3)
        d.polygon([(48, 280), (118, 268), (132, 340), (48, 350)], fill=(40, 170, 190, 255))
        d.polygon([(W - 48, 280), (W - 118, 268), (W - 132, 340), (W - 48, 350)], fill=(190, 48, 60, 255))

    def badge(self, text, cx, cy, r=0.045, fill=(186, 24, 32)):
        x, y = px(cx, cy)
        rad = r * W
        self.draw.ellipse((x - rad, y - rad, x + rad, y + rad), fill=fill + (255,))
        self.draw.ellipse((x - rad + 4, y - rad + 4, x + rad - 4, y + rad - 4), outline=(255, 255, 255, 230), width=2)
        f = font(max(12, int(rad * 0.42)), True)
        box = self.draw.textbbox((0, 0), text, font=f)
        self.draw.text((x - (box[2] - box[0]) / 2, y - (box[3] - box[1]) / 2 - 1), text, fill=(255, 255, 255, 255), font=f)

    def screen(self, left, top, right, bottom, round_px=16):
        self.display = {"left": left, "top": top, "right": right, "bottom": bottom}
        box = (left * W - 8, top * H - 8, right * W + 8, bottom * H + 8)
        self.draw.rounded_rectangle(box, round_px + 4, fill=(8, 10, 14, 255), outline=(70, 74, 82, 255), width=3)
        inner = (left * W, top * H, right * W, bottom * H)
        self.draw.rounded_rectangle(inner, round_px, fill=(6, 18, 36, 255))

    def rev_slot(self, left, top, right, bottom, count):
        self.lights = {"count": count, "left": left, "top": top, "right": right, "bottom": bottom}
        self.draw.rounded_rectangle(
            (left * W, top * H, right * W, bottom * H),
            radius=(bottom - top) * H / 2,
            fill=(10, 10, 12, 255),
            outline=(50, 52, 58, 255),
            width=2,
        )

    def button(self, id_, label, cx, cy, r, color, number=None):
        number = self.next_button if number is None else number
        self.next_button = max(self.next_button, number + 1)
        self.controls.append(
            {
                "id": id_,
                "label": label,
                "kind": "button",
                "shape": "circle",
                "cx": round(cx, 4),
                "cy": round(cy, 4),
                "r": round(r, 4),
                "color": color,
                "defaultButton": number,
            }
        )
        x, y = px(cx, cy)
        rad = r * W
        rgb = hex_rgb(color)
        self.draw.ellipse((x - rad, y - rad, x + rad, y + rad), fill=rgb + (255,), outline=(255, 255, 255, 210), width=2)
        f = font(max(9, int(rad * 0.62)), True)
        box = self.draw.textbbox((0, 0), label, font=f)
        tw, th = box[2] - box[0], box[3] - box[1]
        ink = (16, 16, 18, 255) if sum(rgb) > 420 else (255, 255, 255, 255)
        self.draw.text((x - tw / 2, y - th / 2 - 1), label, fill=ink, font=f)

    def rotary(self, id_, label, cx, cy, r, color, slot, positions=12, ring=None):
        dec, inc = self.next_button, self.next_button + 1
        self.next_button += 2
        self.controls.append(
            {
                "id": id_,
                "label": label,
                "kind": "rotary",
                "shape": "circle",
                "cx": round(cx, 4),
                "cy": round(cy, 4),
                "r": round(r, 4),
                "color": color,
                "positions": positions,
                "knobSlot": slot,
                "decButton": dec,
                "incButton": inc,
                "startAngle": -60.0,
                "clockwise": True,
                "gesture": "dial",
            }
        )
        x, y = px(cx, cy)
        rad = r * W
        rgb = hex_rgb(ring or color)
        self.draw.ellipse((x - rad, y - rad, x + rad, y + rad), fill=(16, 16, 18, 255))
        self.draw.ellipse((x - rad, y - rad, x + rad, y + rad), outline=rgb + (255,), width=max(4, int(rad * 0.16)))
        for i in range(positions):
            ang = math.radians(-60 + i * (360 / positions))
            inner, outer = rad * 0.72, rad * 0.90
            self.draw.line(
                (x + math.cos(ang) * inner, y + math.sin(ang) * inner, x + math.cos(ang) * outer, y + math.sin(ang) * outer),
                fill=(220, 220, 224, 255),
                width=2,
            )
        self.draw.ellipse((x - rad * 0.28, y - rad * 0.28, x + rad * 0.28, y + rad * 0.28), fill=(32, 32, 36, 255))
        f = font(max(10, int(rad * 0.28)), True)
        box = self.draw.textbbox((0, 0), label, font=f)
        self.draw.text((x - (box[2] - box[0]) / 2, y - rad - 16), label, fill=(230, 230, 234, 255), font=f)

    def save(self, spec):
        used = set()
        for control in self.controls:
            keys = ("decButton", "incButton") if control.get("kind") == "rotary" else ("defaultButton",)
            for key in keys:
                number = int(control.get(key) or 0)
                if number >= 3:
                    used.add(number)
        def take():
            number = 3
            while number in used:
                number += 1
            used.add(number)
            return number
        for control in self.controls:
            keys = ("decButton", "incButton") if control.get("kind") == "rotary" else ("defaultButton",)
            for key in keys:
                if int(control.get(key) or 0) in (1, 2):
                    control[key] = take()
        image = f"{spec['id']}.png"
        self.im.save(OUT / image)
        doc = {
            "schema": "racing-wheel-pro.template/v1",
            "id": spec["id"],
            "name": spec["name"],
            "maker": spec["maker"],
            "manufacturer": spec["manufacturer"],
            "style": spec["style"],
            "image": image,
            "imageWidth": W,
            "imageHeight": H,
            "blurb": spec["blurb"],
            "hitSlop": 0.008,
            "wheelBounds": spec["bounds"],
            "shifters": {
                "up": {"width": 0.18, "height": 0.34, "centerY": 0.52},
                "down": {"width": 0.18, "height": 0.34, "centerY": 0.52},
            },
            "controls": self.controls,
        }
        if self.display:
            doc["display"] = self.display
        if self.lights:
            doc["revLights"] = self.lights
        (OUT / f"{spec['id']}.json").write_text(json.dumps(doc, indent=2) + "\n")
        print(spec["id"], "buttons", self.next_button - 1)


def formula_body(p: Plate, seed: int):
    def paint(d: ImageDraw.ImageDraw):
        d.rounded_rectangle((150, 150, 850, 560), 70, fill=(30, 32, 36, 255))
        d.polygon([(210, 190), (790, 190), (840, 250), (800, 520), (200, 520), (160, 250)], fill=(30, 32, 36, 255))
    mask = p.fill_mask(paint)
    p.carbon(mask, seed)
    p.grips()
    p.paddles()


def round_body(p: Plate, seed: int, stripe=False, deep=False):
    mask_l = Image.new("L", (W, H), 0)
    md = ImageDraw.Draw(mask_l)
    md.ellipse((150, 40, 850, 620), fill=255)
    md.ellipse((248, 128, 752, 532), fill=0)
    md.polygon([(478, 70), (522, 70), (545, 300), (455, 300)], fill=255)
    md.polygon([(230, 470), (390, 350), (500, 470), (360, 560)], fill=255)
    md.polygon([(770, 470), (610, 350), (500, 470), (640, 560)], fill=255)
    md.ellipse((400, 255, 600, 445), fill=255)
    body = Image.new("RGBA", (W, H), (20, 20, 22, 255))
    body.putalpha(mask_l)
    p.im.alpha_composite(body)
    mask = Image.new("RGBA", (W, H), (255, 255, 255, 255))
    mask.putalpha(mask_l)
    p.carbon(mask, seed)
    p.draw = ImageDraw.Draw(p.im)
    # metal spokes and hub
    p.draw.polygon([(486, 78), (514, 78), (536, 292), (464, 292)], fill=(92, 94, 100, 255))
    p.draw.polygon([(248, 458), (378, 362), (488, 458), (352, 548)], fill=(92, 94, 100, 255))
    p.draw.polygon([(752, 458), (622, 362), (512, 458), (648, 548)], fill=(92, 94, 100, 255))
    p.draw.ellipse((408, 262, 592, 438), fill=(58, 60, 66, 255), outline=(180, 180, 186, 255), width=4)
    if deep:
        p.draw.ellipse((430, 284, 570, 416), fill=(24, 24, 28, 255))
    p.draw.arc((168, 56, 832, 604), 0, 360, fill=(176, 28, 36, 255), width=5)
    if stripe:
        p.draw.polygon([(488, 46), (512, 46), (506, 118), (494, 118)], fill=(196, 24, 32, 255))
    p.paddles()


def alfa_f1_triple():
    p = Plate()
    formula_body(p, 1)
    p.rev_slot(0.34, 0.20, 0.66, 0.255, 15)
    p.screen(0.34, 0.27, 0.66, 0.46)
    pairs = [
        ("n", "N", 0.30, 0.32, "#F5C400"),
        ("entry", "ENT", 0.30, 0.42, "#3DDC57"),
        ("radio", "RAD", 0.30, 0.52, "#3EC6FF"),
        ("box", "BOX", 0.38, 0.54, "#C8CDD4"),
        ("ok", "OK", 0.62, 0.54, "#3DDC57"),
        ("pit", "PIT", 0.70, 0.32, "#FF4D4D"),
        ("drs", "DRS", 0.70, 0.42, "#FF8A00"),
        ("diff", "DIF", 0.70, 0.52, "#C040C8"),
        ("plus", "+1", 0.38, 0.34, "#3EC6FF"),
        ("minus", "-1", 0.62, 0.34, "#F5C400"),
    ]
    for i, (id_, label, cx, cy, color) in enumerate(pairs, start=1):
        p.button(id_, label, cx, cy, 0.028, color, i)
    p.next_button = 11
    p.rotary("mix", "MIX", 0.36, 0.70, 0.055, "#3EC6FF", 0)
    p.rotary("mode", "MODE", 0.50, 0.72, 0.062, "#F5C400", 1)
    p.rotary("eng", "ENG", 0.64, 0.70, 0.055, "#E8E8EC", 2)
    p.save(
        {
            "id": "alfa_f1_triple",
            "name": "Alfa F1 Triple",
            "maker": "Alfa Romeo",
            "manufacturer": "Alfa Romeo",
            "style": "formula",
            "blurb": "Formula wheel with a dash, a shift-light row, and three rotary dials.",
            "bounds": {"left": 0.16, "top": 0.16, "right": 0.84, "bottom": 0.86},
        }
    )


def alfa_classic():
    p = Plate()
    round_body(p, 2, stripe=True, deep=True)
    p.badge("ALFA", 0.50, 0.52, 0.055)
    p.save(
        {
            "id": "alfa_classic",
            "name": "Alfa Classic",
            "maker": "Alfa Romeo",
            "manufacturer": "Alfa Romeo",
            "style": "road",
            "blurb": "Three-spoke deep-dish rim with a red marker at the top.",
            "bounds": {"left": 0.16, "top": 0.08, "right": 0.84, "bottom": 0.93},
        }
    )


def alfa_road():
    p = Plate()
    round_body(p, 3, stripe=False, deep=False)
    p.badge("ALFA", 0.50, 0.52, 0.07, fill=(24, 24, 28))
    p.draw.rounded_rectangle((430, 300, 570, 390), 18, outline=(200, 200, 206, 255), width=2)
    buttons = [
        ("res", "RES", 0.34, 0.40, "#C8CDD4"),
        ("set", "SET", 0.34, 0.50, "#C8CDD4"),
        ("cancel", "CNL", 0.34, 0.60, "#FF4D4D"),
        ("phone", "TEL", 0.66, 0.40, "#3DDC57"),
        ("voice", "MIC", 0.66, 0.50, "#3EC6FF"),
        ("cruise", "LIM", 0.66, 0.60, "#F5C400"),
        ("up", "UP", 0.58, 0.38, "#E8E8EC"),
        ("okb", "OK", 0.58, 0.48, "#3DDC57"),
    ]
    for i, (id_, label, cx, cy, color) in enumerate(buttons, start=1):
        p.button(id_, label, cx, cy, 0.026, color, i)
    p.save(
        {
            "id": "alfa_road",
            "name": "Alfa Road",
            "maker": "Alfa Romeo",
            "manufacturer": "Alfa Romeo",
            "style": "road",
            "blurb": "Road rim with a centre pad and cruise and phone buttons on the spokes.",
            "bounds": {"left": 0.16, "top": 0.08, "right": 0.84, "bottom": 0.93},
        }
    )


def alfa_f1_multi():
    p = Plate()
    formula_body(p, 4)
    p.rev_slot(0.30, 0.18, 0.70, 0.23, 16)
    p.screen(0.32, 0.25, 0.68, 0.46)
    for i, (id_, label, cx, cy, color) in enumerate(
        [
            ("n", "N", 0.28, 0.32, "#F5C400"),
            ("pit", "PIT", 0.28, 0.44, "#FF4D4D"),
            ("box", "BOX", 0.36, 0.50, "#3EC6FF"),
            ("drs", "DRS", 0.72, 0.32, "#3DDC57"),
            ("ok", "OK", 0.72, 0.44, "#E8E8EC"),
            ("radio", "RAD", 0.64, 0.50, "#C040C8"),
        ],
        start=1,
    ):
        p.button(id_, label, cx, cy, 0.028, color, i)
    p.next_button = 7
    p.rotary("multi", "MULTI", 0.50, 0.64, 0.078, "#FF8A00", 0, positions=12)
    p.rotary("bb", "BB", 0.34, 0.74, 0.04, "#3EC6FF", 1)
    p.rotary("eb", "EB", 0.66, 0.74, 0.04, "#3DDC57", 2)
    p.save(
        {
            "id": "alfa_f1_multi",
            "name": "Alfa F1 Multi",
            "maker": "Alfa Romeo",
            "manufacturer": "Alfa Romeo",
            "style": "formula",
            "blurb": "Formula wheel with a colour dash and a large centre dial.",
            "bounds": {"left": 0.16, "top": 0.16, "right": 0.84, "bottom": 0.88},
        }
    )


def alfa_f1_dash():
    p = Plate()
    formula_body(p, 5)
    p.rev_slot(0.28, 0.17, 0.72, 0.22, 18)
    p.screen(0.30, 0.24, 0.70, 0.48)
    buttons = [
        ("n", "N", 0.22, 0.36, "#3EC6FF"),
        ("ov", "OV", 0.22, 0.48, "#F5C400"),
        ("plus", "+10", 0.30, 0.56, "#E8E8EC"),
        ("pit", "PIT", 0.78, 0.32, "#FF4D4D"),
        ("r", "R", 0.78, 0.44, "#FF4D4D"),
        ("radio", "RAD", 0.70, 0.56, "#3DDC57"),
        ("entry", "ENT", 0.36, 0.56, "#C8CDD4"),
        ("diff", "DIF", 0.64, 0.56, "#C040C8"),
    ]
    for i, (id_, label, cx, cy, color) in enumerate(buttons, start=1):
        p.button(id_, label, cx, cy, 0.027, color, i)
    p.next_button = 9
    p.rotary("soc", "SOC", 0.38, 0.72, 0.05, "#3DDC57", 0)
    p.rotary("pu", "PU", 0.62, 0.72, 0.05, "#FF8A00", 1)
    p.save(
        {
            "id": "alfa_f1_dash",
            "name": "Alfa F1 Dash",
            "maker": "Alfa Romeo",
            "manufacturer": "Alfa Romeo",
            "style": "formula",
            "blurb": "Formula wheel with a lap and fuel dash, N, OV, PIT and R, and SOC and PU dials.",
            "bounds": {"left": 0.16, "top": 0.15, "right": 0.84, "bottom": 0.88},
        }
    )


def mclaren_mp4():
    p = Plate()
    formula_body(p, 6)
    p.rev_slot(0.32, 0.17, 0.68, 0.22, 14)
    p.screen(0.33, 0.24, 0.67, 0.46)
    p.badge("MP4", 0.50, 0.62, 0.032, fill=(255, 120, 0))
    buttons = [
        ("plus10", "+10", 0.24, 0.30, "#F5C400"),
        ("n", "N", 0.24, 0.42, "#3DDC57"),
        ("ot", "OT", 0.24, 0.54, "#FF8A00"),
        ("x", "X", 0.32, 0.54, "#E8E8EC"),
        ("pls", "PLS", 0.76, 0.30, "#3EC6FF"),
        ("d", "D", 0.76, 0.42, "#FF4D4D"),
        ("radio", "RAD", 0.76, 0.54, "#C8CDD4"),
        ("box", "BOX", 0.68, 0.54, "#F5C400"),
    ]
    for i, (id_, label, cx, cy, color) in enumerate(buttons, start=1):
        p.button(id_, label, cx, cy, 0.028, color, i)
    p.next_button = 9
    p.rotary("eng", "ENG", 0.38, 0.74, 0.048, "#FF8A00", 0)
    p.rotary("mix", "MIX", 0.50, 0.76, 0.048, "#3EC6FF", 1)
    p.rotary("diff", "DIF", 0.62, 0.74, 0.048, "#E8E8EC", 2)
    p.save(
        {
            "id": "mclaren_mp4_30",
            "name": "MP4-30",
            "maker": "McLaren",
            "manufacturer": "McLaren",
            "style": "formula",
            "blurb": "2015-style formula wheel with a dash, +10, N, OT, X, D and PLS, and three dials.",
            "bounds": {"left": 0.16, "top": 0.15, "right": 0.84, "bottom": 0.88},
        }
    )


def fanatec_formula():
    p = Plate()
    formula_body(p, 7)
    p.screen(0.42, 0.20, 0.58, 0.30, round_px=8)
    dials = [
        ("e1", "E1", 0.30, 0.28),
        ("e2", "E2", 0.38, 0.24),
        ("e3", "E3", 0.62, 0.24),
        ("e4", "E4", 0.70, 0.28),
        ("m1", "M1", 0.36, 0.48),
        ("m2", "M2", 0.64, 0.48),
        ("b1", "B1", 0.42, 0.70),
        ("b2", "B2", 0.58, 0.70),
    ]
    for i, (id_, label, cx, cy) in enumerate(dials):
        # rotary() consumes two button numbers; set the counter first
        p.rotary(id_, label, cx, cy, 0.042, "#FF5A14", i)
    for i, (id_, label, cx, cy, color) in enumerate(
        [
            ("funk", "FN", 0.28, 0.58, "#3EC6FF"),
            ("ok", "OK", 0.36, 0.60, "#3DDC57"),
            ("radio", "RAD", 0.64, 0.60, "#F5C400"),
            ("pit", "PIT", 0.72, 0.58, "#FF4D4D"),
        ],
        start=p.next_button,
    ):
        p.button(id_, label, cx, cy, 0.026, color, i)
    p.save(
        {
            "id": "fanatec_formula",
            "name": "Formula Orange",
            "maker": "Fanatec",
            "manufacturer": "Fanatec",
            "style": "formula",
            "blurb": "Formula plate with a small screen and orange rotary dials.",
            "bounds": {"left": 0.16, "top": 0.16, "right": 0.84, "bottom": 0.88},
        }
    )


def bmw_m4():
    p = Plate()
    formula_body(p, 8)
    p.badge("M4", 0.50, 0.40, 0.04, fill=(28, 70, 160))
    p.rotary("tc", "TC", 0.32, 0.58, 0.07, "#FF2A2A", 0, ring="#FF2A2A")
    p.rotary("abs", "ABS", 0.50, 0.66, 0.07, "#2F7BFF", 1, ring="#2F7BFF")
    p.rotary("map", "MAP", 0.68, 0.58, 0.07, "#29D36A", 2, ring="#29D36A")
    buttons = [
        ("rad", "RAD", 0.28, 0.28, "#3EC6FF"),
        ("tcbtn", "TC", 0.38, 0.26, "#FF4D4D"),
        ("als", "ALS", 0.62, 0.26, "#3DDC57"),
        ("hi", "HI", 0.72, 0.28, "#F5C400"),
        ("wip", "WIP", 0.28, 0.42, "#E8E8EC"),
        ("psl", "PSL", 0.72, 0.42, "#C040C8"),
        ("n", "N", 0.40, 0.78, "#F5C400"),
        ("ok", "OK", 0.60, 0.78, "#3DDC57"),
    ]
    start = p.next_button
    for i, (id_, label, cx, cy, color) in enumerate(buttons):
        p.button(id_, label, cx, cy, 0.026, color, start + i)
    p.save(
        {
            "id": "bmw_m4_gt3",
            "name": "M4 GT3",
            "maker": "BMW",
            "manufacturer": "BMW",
            "style": "gt",
            "blurb": "GT wheel with three lit rotary dials: red, blue, and green.",
            "bounds": {"left": 0.16, "top": 0.16, "right": 0.84, "bottom": 0.88},
        }
    )


def fanatec_gt():
    p = Plate()
    formula_body(p, 9)
    # round centre screen
    p.screen(0.40, 0.36, 0.60, 0.66, round_px=80)
    p.rev_slot(0.38, 0.285, 0.62, 0.325, 12)
    top = [
        ("flash", "FLS", 0.30, 0.27, "#F5C400"),
        ("radio", "RAD", 0.38, 0.25, "#3EC6FF"),
        ("left", "L", 0.46, 0.24, "#E8E8EC"),
        ("start", "GO", 0.54, 0.24, "#3DDC57"),
        ("right", "RGT", 0.62, 0.25, "#E8E8EC"),
        ("night", "NIT", 0.70, 0.27, "#C040C8"),
    ]
    bottom = [
        ("b1", "1", 0.34, 0.74, "#FF4D4D"),
        ("b2", "2", 0.42, 0.76, "#F5C400"),
        ("b3", "3", 0.50, 0.77, "#3DDC57"),
        ("b4", "4", 0.58, 0.76, "#3EC6FF"),
        ("b5", "5", 0.66, 0.74, "#E8E8EC"),
        ("wiper", "WIP", 0.74, 0.70, "#C8CDD4"),
    ]
    side = [
        ("ign", "IGN", 0.30, 0.48, "#FF4D4D"),
        ("ok", "OK", 0.70, 0.48, "#3DDC57"),
        ("pit", "PIT", 0.30, 0.60, "#FF8A00"),
        ("box", "BOX", 0.70, 0.60, "#3EC6FF"),
    ]
    n = 1
    for group in (top, bottom, side):
        for id_, label, cx, cy, color in group:
            p.button(id_, label, cx, cy, 0.026, color, n)
            n += 1
    p.save(
        {
            "id": "fanatec_gt",
            "name": "GT Round",
            "maker": "Fanatec",
            "manufacturer": "Fanatec",
            "style": "gt",
            "blurb": "GT wheel with a round centre screen and lit button rows.",
            "bounds": {"left": 0.16, "top": 0.16, "right": 0.84, "bottom": 0.88},
        }
    )


def main():
    alfa_f1_triple()
    alfa_classic()
    alfa_road()
    alfa_f1_multi()
    alfa_f1_dash()
    mclaren_mp4()
    fanatec_formula()
    bmw_m4()
    fanatec_gt()


if __name__ == "__main__":
    main()
