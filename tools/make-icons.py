#!/usr/bin/env python3
"""Generates every launcher icon the app ships: the legacy PNGs in src/android/res/drawable-*/, the
adaptive icon's foreground and monochrome layers for API 26 and up, the 512x512 store icon, and the
1024x500 feature graphic F-Droid shows at the top of the app page.

The icon is drawn, not downloaded: a mid-grey rounded square carrying the name RETE across the top
and the time 13:24 below it in seven-segment digits, so the launcher says both what the app is
called and what it does. It is monochrome on purpose --- a grey plate and white lettering, no third
colour --- which is also what lets the same drawing serve as the themed icon Android 13 asks for.
On the themed layer the lettering is a hole rather than ink, because there the system supplies both
the colour of the plate and what shows through it.

The digits are polygons rather than a font, since no seven-segment face can be assumed to exist on
the machine that runs this.

Run this only when the icon design changes; the generated PNGs are part of the source tree, so a
normal build needs no Python and no image library.

Usage: python3 tools/make-icons.py
"""

import os
import sys

from PIL import Image, ImageDraw, ImageFont

# The launcher icon is 48dp; these are its pixel sizes per density bucket.
DENSITIES = {
    "ldpi": 36,
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

# An adaptive icon layer is 108dp, of which only the middle 66dp is guaranteed to survive the shape
# the launcher masks it with. Nothing below hdpi is generated: no device that reached API 26 is
# coarser than that, and mdpi is kept only as the fallback the framework may still ask for.
ADAPTIVE_DENSITIES = {
    "mdpi": 108,
    "hdpi": 162,
    "xhdpi": 216,
    "xxhdpi": 324,
    "xxxhdpi": 432,
}
SAFE_ZONE = 66.0 / 108.0

# The icon is a dark blue-grey rounded square with white lettering on it, which is how a monochrome
# icon sits among its neighbours: the plate carries the weight and the letters are the light part.
# It was the other way round until 0.30.2 — a white plate with near-black lettering — and a white
# square is the brightest thing on most home screens whatever else is on them. The colour is the one
# retekey's icon uses, so the two apps by the same hand look like a pair.
PLATE = (38, 50, 56, 255)  # #263238, the same plate retekey is drawn on
LETTERING = (255, 255, 255, 255)  # the wordmark and the time on it

# The clock's own two colours, which the feature graphic shows as they really are.
INK = (12, 12, 16, 255)
BACKGROUND = INK
FOREGROUND = (255, 255, 255, 255)

PLATE_RADIUS = 0.22  # corner radius of the plate, as a fraction of its side

FONT_CANDIDATES = [
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    "/usr/share/fonts/TTF/DejaVuSans.ttf",
    "/usr/share/fonts/dejavu/DejaVuSans.ttf",
    "/usr/share/fonts/truetype/freefont/FreeSans.ttf",
    "/usr/share/fonts/gnu-free/FreeSans.ttf",
]

# The wordmark wants weight; fall back to the regular faces if no bold one is installed.
BOLD_FONT_CANDIDATES = [
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "/usr/share/fonts/TTF/DejaVuSans-Bold.ttf",
    "/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf",
    "/usr/share/fonts/truetype/freefont/FreeSansBold.ttf",
    "/usr/share/fonts/gnu-free/FreeSansBold.ttf",
] + FONT_CANDIDATES

WORDMARK = "RETE"
ICON_TIME = "13:24"
TRACKING = 0.14  # extra letter spacing in the wordmark, as a fraction of the font size

# Where the two lines sit inside the square they are drawn in, as fractions of its side.
WORD_BOX = (0.13, 0.18, 0.87, 0.38)
TIME_BOX = (0.10, 0.48, 0.90, 0.79)

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


def load_font(size, candidates=FONT_CANDIDATES):
    for path in candidates:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    raise SystemExit("no usable TTF font found; install a DejaVu or FreeFont package")


def centered(draw, text, font, box):
    left, top, right, bottom = draw.textbbox((0, 0), text, font=font)
    x = box[0] + (box[2] - box[0] - (right - left)) / 2 - left
    y = box[1] + (box[3] - box[1] - (bottom - top)) / 2 - top
    return x, y


def tracked_width(draw, text, font, tracking):
    """Width of text once the extra letter spacing is counted, which textlength does not know."""
    width = sum(draw.textlength(char, font=font) for char in text)
    return width + tracking * font.size * (len(text) - 1)


def fit_tracked(draw, text, box, candidates, tracking):
    """The largest font size at which the tracked text still fits inside box."""
    width = box[2] - box[0]
    height = box[3] - box[1]
    probe_size = 100
    probe = load_font(probe_size, candidates)
    left, top, right, bottom = draw.textbbox((0, 0), text, font=probe)
    scale = min(
        width / tracked_width(draw, text, probe, tracking), height / (bottom - top)
    )
    return load_font(max(1, int(probe_size * scale)), candidates)


def draw_tracked(draw, text, font, box, tracking, fill):
    """Draws text centred in box, one letter at a time so the spacing can be widened."""
    left, top, right, bottom = draw.textbbox((0, 0), text, font=font)
    x = box[0] + (box[2] - box[0] - tracked_width(draw, text, font, tracking)) / 2
    y = box[1] + (box[3] - box[1] - (bottom - top)) / 2 - top
    for char in text:
        draw.text((x, y), char, font=font, fill=fill)
        x += draw.textlength(char, font=font) + tracking * font.size


# Which of the seven segments each digit lights, named the usual way: a is the top bar, b and c the
# right-hand pair, d the bottom bar, e and f the left-hand pair, g the middle bar.
SEGMENTS = {
    "0": "abcdef",
    "1": "bc",
    "2": "abged",
    "3": "abgcd",
    "4": "fgbc",
    "5": "afgcd",
    "6": "afgedc",
    "7": "abc",
    "8": "abcdefg",
    "9": "abcdfg",
}


def draw_segment_digit(draw, digit, box, thickness, fill):
    """Draws one seven-segment digit inside box = (x, y, x2, y2)."""
    x, y, x2, y2 = box
    half = thickness / 2
    # Every segment is inset by half a stroke *plus* the gap, so a horizontal one stops short of
    # where the vertical one beside it begins. Insetting by the gap alone — which is what this did
    # until 0.31.1 — left the two overlapping by half a stroke at every corner, and a digit came out
    # as one solid blob rather than seven lit bars. It reads as a blob wherever the shape is light
    # on a dark ground, which is what the themed icon is.
    gap = thickness * 0.18

    def horizontal(cy):
        left = x + half + gap
        right = x2 - half - gap
        return [
            (left, cy),
            (left + half, cy - half),
            (right - half, cy - half),
            (right, cy),
            (right - half, cy + half),
            (left + half, cy + half),
        ]

    def vertical(cx, top, bottom):
        high = top + half + gap
        low = bottom - half - gap
        return [
            (cx, high),
            (cx + half, high + half),
            (cx + half, low - half),
            (cx, low),
            (cx - half, low - half),
            (cx - half, high + half),
        ]

    middle = (y + y2) / 2
    shapes = {
        "a": horizontal(y + half),
        "g": horizontal(middle),
        "d": horizontal(y2 - half),
        "f": vertical(x + half, y, middle),
        "b": vertical(x2 - half, y, middle),
        "e": vertical(x + half, middle, y2),
        "c": vertical(x2 - half, middle, y2),
    }
    for name in SEGMENTS[digit]:
        draw.polygon(shapes[name], fill=fill)


def draw_segment_time(draw, text, box, fill):
    """Lays a string like "13:24" of digits and colons across box = (x, y, x2, y2)."""
    x, y, x2, y2 = box
    height = y2 - y
    digit_width = height * 0.63
    colon_width = digit_width * 0.34
    spacing = digit_width * 0.16
    thickness = height * 0.13

    # A one lights only its right-hand pair, so on a full-width cell it drifts away from the digit
    # beside it. Give it a narrower cell instead and the line reads evenly.
    cells = []
    for char in text:
        if char == ":":
            cells.append((char, colon_width))
        elif char == "1":
            cells.append((char, digit_width * 0.55))
        else:
            cells.append((char, digit_width))
    total = sum(width for _, width in cells) + spacing * (len(cells) - 1)

    cursor = x + (x2 - x - total) / 2
    for char, width in cells:
        if char == ":":
            dot = thickness * 0.95
            cx = cursor + width / 2
            for cy in (y + height * 0.32, y + height * 0.68):
                draw.ellipse(
                    [cx - dot / 2, cy - dot / 2, cx + dot / 2, cy + dot / 2], fill=fill
                )
        else:
            draw_segment_digit(
                draw, char, (cursor, y, cursor + width, y2), thickness, fill
            )
        cursor += width + spacing


def draw_face(draw, origin, side, fill, bold=True):
    """The name over the time, drawn inside the square at origin with the given side.

    `bold` is the one thing the themed layer does differently. A themed home screen paints this
    drawing as a light shape on a colour, and the launcher on the phone that reported it adds an
    outline of its own; both make a glyph read heavier than the same glyph does as dark ink on the
    plate. The wordmark is therefore set in the regular face there and the bold one here, so the two
    arrive at the same weight rather than at the same file.
    """

    def place(fractions):
        left, top, right, bottom = fractions
        return (
            origin[0] + side * left,
            origin[1] + side * top,
            origin[0] + side * right,
            origin[1] + side * bottom,
        )

    word_box = place(WORD_BOX)
    font = fit_tracked(draw, WORDMARK, word_box,
                       BOLD_FONT_CANDIDATES if bold else FONT_CANDIDATES, TRACKING)
    draw_tracked(draw, WORDMARK, font, word_box, TRACKING, fill)
    draw_segment_time(draw, ICON_TIME, place(TIME_BOX), fill)


def render(size):
    """The legacy launcher icon and the store icon: the plate, with the lettering on it."""
    big = size * SUPERSAMPLE
    image = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle(
        [0, 0, big - 1, big - 1], radius=big * PLATE_RADIUS, fill=PLATE
    )
    draw_face(draw, (0, 0), big, LETTERING)
    return image.resize((size, size), Image.LANCZOS)


def render_adaptive_foreground(size):
    """The API 26 foreground layer: the lettering alone, inside the safe zone.

    The plate is not drawn here --- the background layer is the plate, and the launcher masks it to
    whatever shape it uses, which is the whole point of an adaptive icon.
    """
    big = size * SUPERSAMPLE
    image = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    side = big * SAFE_ZONE
    draw_face(ImageDraw.Draw(image), ((big - side) / 2, (big - side) / 2), side, LETTERING)
    return image.resize((size, size), Image.LANCZOS)


def render_monochrome(size):
    """The themed layer Android 13 asks for: the lettering alone, opaque, on nothing.

    What a themed home screen does with this layer is the part that is easy to get backwards, and
    this project did get it backwards in 0.26.0: the system fills the icon's *background* with the
    theme's light accent and paints whatever is **opaque here** in the theme's dark on-colour. So an
    opaque plate with the letters punched out of it comes out as a dark plate with bright letters —
    the inverse of every other icon on the screen, which is what the reporter of #28 photographed.

    The layer is therefore the same shape as the adaptive foreground: the lettering, solid, inside
    the safe zone, and nothing else. Colour is irrelevant — only the alpha is read — but black keeps
    the file readable to a person opening it.

    The one difference from the foreground is weight: the wordmark is set in the regular face rather
    than the bold one, because a launcher that paints this layer light on a colour — and, on at
    least one phone, outlines it as well — makes it read heavier than the same drawing does as ink
    on the plate. Same drawing, corrected for how it is painted.
    """
    big = size * SUPERSAMPLE
    side = big * SAFE_ZONE
    image = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw_face(ImageDraw.Draw(image), ((big - side) / 2, (big - side) / 2), side,
              (0, 0, 0, 255), False)
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
    x, y = centered(draw, ICON_TIME, time_font, (0, 0, width * big * 0.62, height * big))
    draw.text((x, y), ICON_TIME, font=time_font, fill=FOREGROUND[:3])

    lines = ["25s", "Sun", "Jul 12", "2026"]
    line_height = height * big * 0.19
    top = height * big / 2 - line_height * len(lines) / 2
    for i, line in enumerate(lines):
        lx, ly = centered(draw, line, side_font,
                          (width * big * 0.62, top + line_height * i,
                           width * big, top + line_height * (i + 1)))
        draw.text((lx, ly), line, font=side_font, fill=FOREGROUND[:3])

    return image.resize(FEATURE_SIZE, Image.LANCZOS)


def write(image, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path, "PNG", optimize=True)
    print("wrote", os.path.relpath(path, ROOT))


def main():
    for density, size in DENSITIES.items():
        write(render(size), os.path.join(RES, "drawable-" + density, "ic_launcher.png"))

    for density, size in ADAPTIVE_DENSITIES.items():
        directory = os.path.join(RES, "drawable-" + density)
        write(
            render_adaptive_foreground(size),
            os.path.join(directory, "ic_launcher_foreground.png"),
        )
        write(
            render_monochrome(size),
            os.path.join(directory, "ic_launcher_monochrome.png"),
        )

    write(render(STORE_ICON_SIZE), STORE_ICON)
    write(render_feature_graphic(), FEATURE_GRAPHIC)
    return 0


if __name__ == "__main__":
    sys.exit(main())
