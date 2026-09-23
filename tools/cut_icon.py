"""Build the adaptive launcher icon set from tools/icon-source/app_icon.png.

The source is a squircle with the bee art already flattened onto a flat
grey backdrop -- an icon meant to be looked at, not a bare foreground layer.
This strips that backdrop back out (by distance from the sampled background
colour, feathered rather than a hard cutoff, so the edge does not fringe),
centres what is left inside the adaptive icon's safe zone, and writes every
density Android actually asks for: the vector background swapped to the
sampled flat colour, a raster foreground and monochrome layer per mipmap
bucket, and flattened legacy `ic_launcher`/`ic_launcher_round` icons for
anything before API 26.

Run from the repo root: `python3 tools/cut_icon.py`.
"""
from PIL import Image, ImageDraw
import numpy as np

SRC = "tools/icon-source/app_icon.png"
RES = "app/src/main/res"

# Density buckets Android's own tooling generates. Legacy icons are 48dp,
# adaptive layers are 108dp -- both scaled by the same per-density factor.
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
LEGACY_DP = 48
LAYER_DP = 108

# A high-resolution master, downsampled per density with LANCZOS rather than
# re-rendered from the source at each size -- one resize pass, not five.
MASTER = LAYER_DP * 8  # 864px

# Google's adaptive-icon safe zone is a 66dp circle inside the 108dp canvas.
# The artwork already sat at about 62% of its own canvas with room round it,
# so this asks for a touch less to leave a visible margin rather than
# crowding the mask.
SAFE_FRACTION = 0.60


def cutout(src: Image.Image) -> Image.Image:
    arr = np.array(src.convert("RGBA")).astype(np.float32)
    bg = arr[10, arr.shape[1] // 2][:3]  # sampled from a flat top edge
    dist = np.sqrt(((arr[:, :, :3] - bg) ** 2).sum(axis=2))
    t0, t1 = 6.0, 30.0
    fade = np.clip((dist - t0) / (t1 - t0), 0, 1)
    out = arr.copy()
    out[:, :, 3] = arr[:, :, 3] * fade
    return Image.fromarray(out.astype(np.uint8), "RGBA"), tuple(int(c) for c in bg)


def trimmed(cut: Image.Image) -> Image.Image:
    alpha = np.array(cut)[:, :, 3]
    ys, xs = np.where(alpha > 10)
    box = (xs.min(), ys.min(), xs.max() + 1, ys.max() + 1)
    return cut.crop(box)


def on_canvas(art: Image.Image, size: int, scale: float = SAFE_FRACTION) -> Image.Image:
    """[art], scaled so its longer side is [scale] of [size], centred on a
    transparent size x size square."""
    factor = (size * scale) / max(art.size)
    w, h = max(1, round(art.width * factor)), max(1, round(art.height * factor))
    resized = art.resize((w, h), Image.LANCZOS)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.alpha_composite(resized, ((size - w) // 2, (size - h) // 2))
    return canvas


def monochrome(layer: Image.Image) -> Image.Image:
    arr = np.array(layer)
    out = np.zeros_like(arr)
    out[:, :, 0] = out[:, :, 1] = out[:, :, 2] = 255
    out[:, :, 3] = arr[:, :, 3]
    return Image.fromarray(out, "RGBA")


def circle_mask(im: Image.Image) -> Image.Image:
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).ellipse((0, 0, im.width, im.height), fill=255)
    out = im.copy()
    out.putalpha(Image.composite(im.split()[3], Image.new("L", im.size, 0), mask))
    return out


def main():
    src = Image.open(SRC)
    cut, bg_rgb = cutout(src)
    art = trimmed(cut)

    master_fg = on_canvas(art, MASTER)
    master_mono = monochrome(master_fg)

    for name, factor in DENSITIES.items():
        # Adaptive layers.
        layer_px = round(LAYER_DP * factor)
        fg = master_fg.resize((layer_px, layer_px), Image.LANCZOS)
        mono = master_mono.resize((layer_px, layer_px), Image.LANCZOS)
        fg.save(f"{RES}/mipmap-{name}/ic_launcher_foreground.webp", lossless=True)
        mono.save(f"{RES}/mipmap-{name}/ic_launcher_monochrome.webp", lossless=True)

        # Legacy fallback: background and foreground flattened together,
        # the way Android itself composites them on API < 26.
        legacy_px = round(LEGACY_DP * factor)
        bg_layer = Image.new("RGBA", (legacy_px, legacy_px), bg_rgb + (255,))
        fg_legacy = master_fg.resize((legacy_px, legacy_px), Image.LANCZOS)
        flat = bg_layer.copy()
        flat.alpha_composite(fg_legacy)
        flat.convert("RGB").save(f"{RES}/mipmap-{name}/ic_launcher.webp", lossless=True)
        circle_mask(flat).save(f"{RES}/mipmap-{name}/ic_launcher_round.webp", lossless=True)

    print("bg", bg_rgb)


if __name__ == "__main__":
    main()
