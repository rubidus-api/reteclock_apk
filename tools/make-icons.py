#!/usr/bin/env python3
"""Generates the launcher icon PNGs into src/android/res/drawable-*/ic_launcher.png, the 512x512
store icon, and the 1024x500 feature graphic F-Droid shows at the top of the app page.

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

# F-Droid shows a 512x512 icon on the app page; it reads it from the fastlane tree.
STORE_ICON = os.path.join(
    ROOT, "fastlane", "metadata", "android", "en-US", "images", "icon.png"
)
STORE_ICON_SIZE = 512

# F-Droid puts this across the top of the app page. Drawn from the same parts as the icon, so the
# listing and the launcher agree with each other and with the clock itself.
FEATURE_GRAPHIC = os.path.join(
    ROOT, "fastlane", "metadata", "android", "en-US", "images", "featureGraphic.png"
)
FEATURE_SIZE = (1024, 500)

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


def render_feature_graphic():
    """The clock face as it really looks, on the black it really uses."""
    width, height = FEATURE_SIZE
    big = SUPERSAMPLE
    image = Image.new("RGB", (width * big, height * big), BACKGROUND[:3])
    draw = ImageDraw.Draw(image)

    time_font = load_font(int(height * big * 0.52))
    side_font = load_font(int(height * big * 0.13))

    # Same arrangement as the wide layout: the time on the left, the details in a column right.
    x, y = centered(draw, "13:45", time_font,
                    (0, 0, width * big * 0.62, height * big))
    draw.text((x, y), "13:45", font=time_font, fill=FOREGROUND[:3])

    lines = ["25s", "Sun", "Jul 12", "2026"]
    line_height = height * big * 0.19
    top = height * big / 2 - line_height * len(lines) / 2
    for i, line in enumerate(lines):
        lx, ly = centered(draw, line, side_font,
                          (width * big * 0.62, top + line_height * i,
                           width * big, top + line_height * (i + 1)))
        draw.text((lx, ly), line, font=side_font, fill=FOREGROUND[:3])

    return image.resize(FEATURE_SIZE, Image.LANCZOS)


def main():
    for density, size in DENSITIES.items():
        directory = os.path.join(RES, "drawable-" + density)
        os.makedirs(directory, exist_ok=True)
        path = os.path.join(directory, "ic_launcher.png")
        render(size).save(path, "PNG", optimize=True)
        print("wrote", os.path.relpath(path, ROOT))

    os.makedirs(os.path.dirname(STORE_ICON), exist_ok=True)
    render(STORE_ICON_SIZE).save(STORE_ICON, "PNG", optimize=True)
    print("wrote", os.path.relpath(STORE_ICON, ROOT))

    render_feature_graphic().save(FEATURE_GRAPHIC, "PNG", optimize=True)
    print("wrote", os.path.relpath(FEATURE_GRAPHIC, ROOT))
    return 0


if __name__ == "__main__":
    sys.exit(main())
