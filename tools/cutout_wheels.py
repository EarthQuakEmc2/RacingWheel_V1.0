#!/usr/bin/env python3
"""Cut studio backgrounds out of the wheel template PNGs.

The canvas size and the wheel's position stay the same, so normalized hotspots
still land on the same pixels. BiRefNet (rembg) supplies the mask. Alpha matting
softens the edge. A short cleanup drops corner gallery badges, arrows, and
crumbs that are not part of the wheel. Spokes that show the backdrop stay open.
"""

from __future__ import annotations

import json
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app" / "src" / "main" / "assets" / "wheels"
WEB = ROOT / "website" / "public" / "wheels"
SRC = ROOT / "tools" / "sources"

# Per-image matting tweaks. Empty unless a wheel lost a rim, paddle, or grip.
OVERRIDES: dict[str, dict] = {}


def load_hotspots() -> dict[str, list[dict]]:
    """Map image filename -> control dicts (cx, cy, r are normalized)."""
    out: dict[str, list[dict]] = {}
    for path in APP.glob("*.json"):
        doc = json.loads(path.read_text())
        out.setdefault(doc["image"], []).extend(doc["controls"])
    return out


def prepare(im: Image.Image) -> Image.Image:
    """Flatten partial transparency onto the border color so the model sees a plate.

    A new image is built from the pixels, which also drops EXIF orientation.
    rembg would otherwise transpose from EXIF and shift the wheel under the hotspots.
    """
    arr = np.array(im.convert("RGBA"))
    alpha = arr[:, :, 3]
    if float((alpha < 250).mean()) < 0.002:
        return Image.fromarray(arr, "RGBA")
    h, w = alpha.shape
    border = np.concatenate(
        [
            arr[:6, :, :3].reshape(-1, 3),
            arr[-6:, :, :3].reshape(-1, 3),
            arr[:, :6, :3].reshape(-1, 3),
            arr[:, -6:, :3].reshape(-1, 3),
        ]
    )
    border_a = np.concatenate(
        [
            alpha[:6, :].reshape(-1),
            alpha[-6:, :].reshape(-1),
            alpha[:, :6].reshape(-1),
            alpha[:, -6:].reshape(-1),
        ]
    )
    opaque = border_a > 240
    if int(opaque.sum()) > 40:
        bg = np.median(border[opaque], axis=0)
    else:
        bg = np.array([255, 255, 255], dtype=np.float32)
    rgb = arr[:, :, :3].astype(np.float32)
    a = (alpha.astype(np.float32) / 255.0)[..., None]
    comp = rgb * a + bg.astype(np.float32) * (1.0 - a)
    flat = np.dstack([comp.astype(np.uint8), np.full((h, w), 255, np.uint8)])
    return Image.fromarray(flat, "RGBA")


def _touches_transparency(component: np.ndarray, alpha: np.ndarray) -> float:
    dil = ndimage.binary_dilation(component)
    edge = dil & ~component
    if not edge.any():
        return 0.0
    return float((alpha[edge] < 16).mean())


def _hits_hotspot(xs: np.ndarray, ys: np.ndarray, controls: list[dict], w: int, h: int) -> bool:
    if not controls:
        return False
    pts = np.stack([xs.astype(np.float32), ys.astype(np.float32)], axis=1)
    for c in controls:
        cx, cy = c["cx"] * w, c["cy"] * h
        rad = max(6.0, c["r"] * w * 0.85)
        if np.any((pts[:, 0] - cx) ** 2 + (pts[:, 1] - cy) ** 2 <= rad * rad):
            return True
    return False


def cleanup(cut: Image.Image, controls: list[dict], original: Image.Image | None = None) -> Image.Image:
    arr = np.array(cut.convert("RGBA"))
    alpha = arr[:, :, 3]
    rgb = arr[:, :, :3].astype(np.int16)
    h, w = alpha.shape
    # Dust.
    alpha[alpha < 8] = 0

    fg = alpha > 24
    labels, n = ndimage.label(fg)
    if n == 0:
        arr[:, :, 3] = alpha
        return Image.fromarray(arr, "RGBA")
    sizes = ndimage.sum(fg, labels, range(1, n + 1))
    main = int(np.argmax(sizes)) + 1
    main_area = float(sizes[main - 1])
    ys, xs = np.where(labels == main)
    # Shrink the main box so a badge stuck to the outer edge is not "inside".
    pad_x = int(w * 0.03)
    pad_y = int(h * 0.03)
    box = (xs.min() + pad_x, ys.min() + pad_y, xs.max() - pad_x, ys.max() - pad_y)

    drop = np.zeros(n + 1, dtype=bool)
    for i in range(1, n + 1):
        if i == main:
            continue
        area = float(sizes[i - 1])
        cy, cx = ndimage.center_of_mass(labels == i)
        inside = box[0] <= cx <= box[2] and box[1] <= cy <= box[3]
        if area >= main_area * 0.02 or inside:
            continue
        drop[i] = True
    if drop.any():
        alpha[drop[labels]] = 0

    # Thin light overlays in the margin: gallery arrows and similar strokes.
    # A solid paddle is thicker, so most of its pixels do not touch transparency.
    chroma = rgb.max(axis=2) - rgb.min(axis=2)
    bright = (rgb.min(axis=2) > 168) & (chroma < 48) & (alpha > 40)
    blabels, bn = ndimage.label(bright)
    mx, my = w * 0.18, h * 0.18
    for i in range(1, bn + 1):
        ys, xs = np.where(blabels == i)
        area = int(xs.size)
        if area < 36 or area > 4000:
            continue
        cx, cy = float(xs.mean()), float(ys.mean())
        in_margin = cx < mx or cx > w - mx or cy < my or cy > h - my
        if not in_margin:
            continue
        if _hits_hotspot(xs, ys, controls, w, h):
            continue
        touch = _touches_transparency(blabels == i, alpha)
        if touch < 0.45:
            continue
        alpha[ys, xs] = 0
        # Pull the pale fringe of the same stroke.
        fringe = ndimage.binary_dilation(blabels == i, iterations=2)
        pale = fringe & (rgb.min(axis=2) > 150) & (chroma < 55)
        alpha[pale] = 0

    # Dark gallery badges (the rounded "1" / "2" squares) left in a corner.
    corner = np.zeros((h, w), dtype=bool)
    cw, ch = int(w * 0.20), int(h * 0.22)
    corner[:ch, :cw] = True
    corner[:ch, w - cw :] = True
    corner[h - ch :, :cw] = True
    corner[h - ch :, w - cw :] = True
    dark = (rgb.max(axis=2) < 55) & (alpha > 40) & corner
    dlabels, dn = ndimage.label(dark)
    for i in range(1, dn + 1):
        ys, xs = np.where(dlabels == i)
        area = int(xs.size)
        if area < 80 or area > 6000:
            continue
        if _hits_hotspot(xs, ys, controls, w, h):
            continue
        # A grip that continues into the wheel touches the inner edge of the
        # corner window. A badge floats inside the window.
        touches_inner = (
            (xs.min() < cw and xs.max() >= cw - 2)
            or (xs.max() > w - cw and xs.min() <= w - cw + 1)
            or (ys.min() < ch and ys.max() >= ch - 2)
            or (ys.max() > h - ch and ys.min() <= h - ch + 1)
        )
        if touches_inner:
            continue
        touch = _touches_transparency(dlabels == i, alpha)
        if touch < 0.35:
            continue
        # Expand slightly to take the white numeral, which is not part of `dark`.
        blob = ndimage.binary_dilation(dlabels == i, iterations=3) & corner
        alpha[blob] = 0

    arr[:, :, 3] = alpha
    # Light buttons on a light backdrop get punched out as small round holes.
    # Spoke openings are larger, or they are pure backdrop white. Restore only
    # the small, slightly off-white disks that sit in the wheel.
    if original is not None:
        restore_light_buttons(arr, original)
    return Image.fromarray(arr, "RGBA")


def restore_light_buttons(arr: np.ndarray, original: Image.Image) -> None:
    orig = np.array(original.convert("RGBA"))
    if orig.shape[:2] != arr.shape[:2]:
        return
    alpha = arr[:, :, 3]
    trans = alpha < 16
    labels, n = ndimage.label(trans)
    if n == 0:
        return
    h, w = alpha.shape
    border = set(np.unique(np.concatenate([labels[0], labels[-1], labels[:, 0], labels[:, -1]])))
    for i in range(1, n + 1):
        if i in border:
            continue
        mask = labels == i
        area = int(mask.sum())
        if area < 200 or area > 800:
            continue
        ys, xs = np.where(mask)
        bw = int(xs.max() - xs.min() + 1)
        bh = int(ys.max() - ys.min() + 1)
        circularity = area / float(bw * bh)
        aspect = max(bw, bh) / float(max(1, min(bw, bh)))
        if circularity < 0.72 or aspect > 1.35:
            continue
        rgb = orig[mask, :3].astype(np.int16)
        luma = float(rgb.mean())
        chroma = float((rgb.max(axis=1) - rgb.min(axis=1)).mean())
        # Pure backdrop is ~255. A white button is a little darker and flat.
        if not (160 <= luma <= 242 and chroma < 28):
            continue
        arr[mask, :3] = orig[mask, :3]
        arr[mask, 3] = 255


def sources() -> list[tuple[str, Path]]:
    jobs = []
    for path in sorted(APP.glob("*.png")):
        src = SRC / path.name
        # MOZA plates in tools/sources still have the original white field.
        jobs.append((path.name, src if src.exists() else path))
    return jobs


def destinations(name: str) -> list[Path]:
    paths = [APP / name]
    if (WEB / name).exists() or not name.startswith("moza_"):
        paths.append(WEB / name)
    if (SRC / name).exists():
        paths.append(SRC / name)
    return paths


def cut_all(session, only: set[str] | None = None) -> None:
    from rembg import remove

    hotspots = load_hotspots()
    for name, path in sources():
        if only and Path(name).stem not in only and name not in only:
            continue
        spec = OVERRIDES.get(name, {})
        im = Image.open(path)
        original_size = im.size
        plate = prepare(im)
        cut = remove(
            plate,
            session=session,
            alpha_matting=True,
            alpha_matting_foreground_threshold=int(spec.get("fg", 240)),
            alpha_matting_background_threshold=int(spec.get("bg", 10)),
            alpha_matting_erode_size=int(spec.get("erode", 8)),
            post_process_mask=False,
        )
        if cut.size != original_size:
            raise SystemExit(f"{name}: canvas changed {original_size} -> {cut.size}")
        cut = cleanup(cut, hotspots.get(name, []), im)
        if cut.size != original_size:
            raise SystemExit(f"{name}: cleanup changed canvas {original_size} -> {cut.size}")
        for dest in destinations(name):
            dest.parent.mkdir(parents=True, exist_ok=True)
            cut.save(dest, format="PNG", optimize=True)
        alpha = np.array(cut)[:, :, 3]
        print(
            f"{name}: {original_size[0]}x{original_size[1]} "
            f"transparent={float((alpha == 0).mean()):.3f} "
            f"soft={float(((alpha > 8) & (alpha < 248)).mean()):.3f}",
            flush=True,
        )


def main() -> None:
    import argparse

    from rembg import new_session

    parser = argparse.ArgumentParser()
    parser.add_argument("--only", nargs="*", help="filenames or stems to redo")
    parser.add_argument("--model", default="birefnet-general")
    args = parser.parse_args()
    only = set(args.only or [])
    print(f"loading {args.model}", flush=True)
    session = new_session(args.model)
    cut_all(session, only or None)


if __name__ == "__main__":
    main()
