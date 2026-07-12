#!/usr/bin/env python3
"""Generates the launcher icon PNGs into src/android/res/drawable-*/ic_launcher.png.

The icon is drawn, not downloaded: a dark rounded square showing "13" over "45", which is what
the tall clock layout looks like. Run this only when the icon design changes; the generated PNGs
are part of the source tree so a normal build needs no Python and no image library.

Usage: python3 tools/make-icons.py
"""

import os
import sys

from PIL import Image, ImageDraw, ImageFont

DENSITIES = {
    "ldpi": 36,
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

BACKGROUND = (12, 12, 16, 255)
FOREGROUND = (255, 255, 255, 255)

FONT_CANDIDATES = [
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    "/usr/share/fonts/TTF/DejaVuSans.ttf",
    "/usr/share/fonts/dejavu/DejaVuSans.ttf",
    "/usr/share/fonts/truetype/freefont/FreeSans.ttf",
    "/usr/share/fonts/gnu-free/FreeSans.ttf",
]

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "android", "res")

# Drawn at 8x and downsampled, which keeps the small densities clean.
SUPERSAMPLE = 8


def load_font(size):
    for path in FONT_CANDIDATES:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    raise SystemExit("no usable TTF font found; install a DejaVu or FreeFont package")


def centered(draw, text, font, box):
    left, top, right, bottom = draw.textbbox((0, 0), text, font=font)
    x = box[0] + (box[2] - box[0] - (right - left)) / 2 - left
    y = box[1] + (box[3] - box[1] - (bottom - top)) / 2 - top
    return x, y


def render(size):
    big = size * SUPERSAMPLE
    image = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle([0, 0, big - 1, big - 1], radius=big * 0.22, fill=BACKGROUND)

    font = load_font(int(big * 0.40))
    for text, top, bottom in (("13", big * 0.10, big * 0.52), ("45", big * 0.48, big * 0.90)):
        x, y = centered(draw, text, font, (big * 0.08, top, big * 0.92, bottom))
        draw.text((x, y), text, font=font, fill=FOREGROUND)

    return image.resize((size, size), Image.LANCZOS)


def main():
    for density, size in DENSITIES.items():
        directory = os.path.join(RES, "drawable-" + density)
        os.makedirs(directory, exist_ok=True)
        path = os.path.join(directory, "ic_launcher.png")
        render(size).save(path, "PNG", optimize=True)
        print("wrote", os.path.relpath(path, ROOT))
    return 0


if __name__ == "__main__":
    sys.exit(main())
