#!/usr/bin/env python3
"""Render five original Claro brand directions as SVG and high-resolution PNG."""

import json
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "marketing/brand/concepts"
AVENIR = "/System/Library/Fonts/Avenir Next.ttc"
GEORGIA = "/System/Library/Fonts/Supplemental/Georgia.ttf"
GEORGIA_BOLD = "/System/Library/Fonts/Supplemental/Georgia Bold.ttf"
ROUNDED = "/System/Library/Fonts/Supplemental/Arial Rounded Bold.ttf"
INK = "#16243B"
CONCEPTS = [
    {
        "id": "01-familiar-home",
        "name": "Familiar Home",
        "background": "#075BD8",
        "foreground": "#FFFFFF",
        "accent": "#FFCE66",
        "tagline": ["Your phone.", "Familiar again."],
        "character": "Familiar, clear, reassuring",
        "type": "modern",
    },
    {
        "id": "02-clear-connection",
        "name": "Clear Connection",
        "background": "#115E59",
        "foreground": "#FFFBF2",
        "accent": "#FFC49A",
        "tagline": ["Closer to", "what matters."],
        "character": "Calm, personal, connected",
        "type": "serif",
    },
    {
        "id": "03-big-and-simple",
        "name": "Big & Simple",
        "background": "#FFD166",
        "foreground": "#172D42",
        "accent": "#FFFFFF",
        "tagline": ["Big buttons.", "Less fuss."],
        "character": "Bold, practical, unmistakable",
        "type": "rounded",
    },
    {
        "id": "04-human-touch",
        "name": "Human Touch",
        "background": "#56366F",
        "foreground": "#FFF8F2",
        "accent": "#FFBFCB",
        "tagline": ["Simple to use.", "Easy to love."],
        "character": "Warm, friendly, expressive",
        "type": "rounded",
    },
    {
        "id": "05-a-brighter-day",
        "name": "A Brighter Day",
        "background": "#FFF3DE",
        "foreground": "#9D3F2B",
        "accent": "#E7A836",
        "tagline": ["A clearer start,", "every day."],
        "character": "Optimistic, spacious, welcoming",
        "type": "serif",
    },
]


def typeface(concept, size, bold=False):
    family = concept["type"]
    if family == "serif":
        return ImageFont.truetype(GEORGIA_BOLD if bold else GEORGIA, size)
    if family == "rounded" and bold:
        return ImageFont.truetype(ROUNDED, size)
    return ImageFont.truetype(AVENIR, size, index=0 if bold else 7)


class Vector:
    def __init__(self, background):
        self.size = 2160
        self.scale = self.size / 108
        self.image = Image.new("RGB", (self.size, self.size), background)
        self.draw = ImageDraw.Draw(self.image)
        self.svg = [
            '<svg xmlns="http://www.w3.org/2000/svg" width="2048" height="2048" viewBox="0 0 108 108">',
            f'<rect width="108" height="108" fill="{background}"/>',
        ]

    def rect(self, box, radius, color):
        self.draw.rounded_rectangle(
            tuple(value * self.scale for value in box),
            radius=radius * self.scale,
            fill=color,
        )
        x, y, right, bottom = box
        self.svg.append(
            f'<rect x="{x}" y="{y}" width="{right-x}" height="{bottom-y}" rx="{radius}" fill="{color}"/>'
        )

    def circle(self, x, y, radius, color):
        self.draw.ellipse(
            tuple(value * self.scale for value in (x - radius, y - radius, x + radius, y + radius)),
            fill=color,
        )
        self.svg.append(f'<circle cx="{x}" cy="{y}" r="{radius}" fill="{color}"/>')

    def line(self, points, width, color):
        self.draw.line(
            [(x * self.scale, y * self.scale) for x, y in points],
            fill=color,
            width=round(width * self.scale),
            joint="curve",
        )
        self.svg.append(
            f'<polyline points="{" ".join(f"{x},{y}" for x,y in points)}" '
            f'fill="none" stroke="{color}" stroke-width="{width}" stroke-linecap="round" stroke-linejoin="round"/>'
        )
        for x, y in points:
            self.circle(x, y, width / 2, color)

    def arc(self, box, start, end, width, color):
        x, y, right, bottom = box
        cx, cy = (x + right) / 2, (y + bottom) / 2
        rx, ry = (right - x) / 2, (bottom - y) / 2
        self.draw.arc(
            tuple(value * self.scale for value in box),
            start,
            end,
            fill=color,
            width=round(width * self.scale),
        )
        # Pillow places an arc's stroke inside its bounds; SVG centers it.
        rx -= width / 2
        ry -= width / 2
        endpoints = [
            (cx + rx * math.cos(math.radians(angle)), cy + ry * math.sin(math.radians(angle)))
            for angle in (start, end)
        ]
        (sx, sy), (ex, ey) = endpoints
        large = int(end - start > 180)
        self.svg.append(
            f'<path d="M{sx},{sy} A{rx},{ry} 0 {large} 1 {ex},{ey}" '
            f'fill="none" stroke="{color}" stroke-width="{width}"/>'
        )
        for px, py in endpoints:
            self.circle(px, py, width / 2, color)

    def polygon(self, points, color):
        self.draw.polygon([(x * self.scale, y * self.scale) for x, y in points], fill=color)
        self.svg.append(
            f'<polygon points="{" ".join(f"{x},{y}" for x,y in points)}" fill="{color}"/>'
        )

    def heart(self, color):
        curves = [
            ((54, 64), (49, 59), (34, 50), (36, 42)),
            ((36, 42), (38, 33), (49, 33), (54, 41)),
            ((54, 41), (59, 33), (70, 33), (72, 42)),
            ((72, 42), (74, 50), (59, 59), (54, 64)),
        ]
        points = []
        for a, b, c, d in curves:
            for step in range(41):
                t = step / 40
                points.append(tuple(
                    (1-t)**3*a[i] + 3*(1-t)**2*t*b[i] + 3*(1-t)*t*t*c[i] + t**3*d[i]
                    for i in (0, 1)
                ))
        self.draw.polygon([(x * self.scale, y * self.scale) for x, y in points], fill=color)
        path = "M54,64 " + " ".join(
            f"C{b[0]},{b[1]} {c[0]},{c[1]} {d[0]},{d[1]}"
            for _, b, c, d in curves
        )
        self.svg.append(f'<path d="{path}Z" fill="{color}"/>')


def logo(concept, index):
    background, foreground, accent = (
        concept[key] for key in ("background", "foreground", "accent")
    )
    vector = Vector(background)
    if index == 0:
        vector.line([(28, 45), (54, 24), (80, 45)], 6, foreground)
        for x, y, color in [(34, 49, foreground), (57, 49, accent), (34, 70, foreground), (57, 70, foreground)]:
            vector.rect((x, y, x + 17, y + 15), 3, color)
    elif index == 1:
        vector.arc((22, 22, 86, 86), 44, 316, 12, foreground)
        vector.circle(80, 54, 6, accent)
    elif index == 2:
        vector.rect((31, 17, 77, 91), 11, foreground)
        vector.rect((37, 25, 71, 72), 5, "#FFFAEC")
        vector.line([(46, 32), (62, 32)], 3, foreground)
        for x in (43, 57):
            for y in (41, 52, 63):
                vector.rect((x, y, x + 8, y + 5), 1.5, foreground)
        vector.circle(54, 81, 3.5, background)
    elif index == 3:
        vector.rect((20, 23, 88, 76), 15, foreground)
        vector.polygon([(33, 68), (33, 89), (58, 72)], foreground)
        vector.heart(background)
        vector.circle(81, 27, 8, accent)
    else:
        vector.circle(54, 49, 15, accent)
        vector.rect((32, 54, 76, 67), 0, background)
        vector.line([(22, 61), (86, 61)], 6, foreground)
        vector.line([(36, 76), (72, 76)], 6, foreground)
        vector.line([(54, 18), (54, 12)], 4, foreground)
        vector.line([(31, 26), (27, 22)], 4, foreground)
        vector.line([(77, 26), (81, 22)], 4, foreground)
        vector.line([(22, 44), (16, 44)], 4, foreground)
        vector.line([(86, 44), (92, 44)], 4, foreground)
    return vector


def label(image, concept, xy, value, size, color, bold=False):
    draw = ImageDraw.Draw(image)
    draw.text(xy, value, font=typeface(concept, size, bold), fill=color, anchor="lt")


def feature(concept, icon):
    background, foreground, accent = (
        concept[key] for key in ("background", "foreground", "accent")
    )
    image = Image.new("RGB", (2048, 1000), background)
    draw = ImageDraw.Draw(image)
    # A quiet inset establishes a brand field without pretending to be app UI.
    draw.rounded_rectangle((1310, 130, 1925, 870), radius=80, outline=accent, width=3)
    image.paste(icon.resize((560, 560), Image.Resampling.LANCZOS), (1340, 195))
    label(image, concept, (120, 110), "claro", 115, foreground, True)
    label(image, concept, (126, 260), "H O M E  L A U N C H E R", 25, foreground)
    for y, line in zip((425, 575), concept["tagline"]):
        label(image, concept, (120, y), line, 112, foreground, True)
    label(image, concept, (126, 825), "CLASSIC KEYPAD  /  TOUCH HOME", 27, foreground)
    return image.resize((1024, 500), Image.Resampling.LANCZOS)


def board(concept, icon, poster, index):
    image = Image.new("RGB", (1600, 1150), "#F5F6F8")
    draw = ImageDraw.Draw(image)
    label(image, concept, (64, 48), f"0{index+1}  /  {concept['name']}", 44, INK, True)
    label(image, concept, (66, 115), concept["character"], 25, "#59677A")
    image.paste(icon.resize((340, 340), Image.Resampling.LANCZOS), (65, 200))
    wordmark_color = concept["foreground"] if index in (2, 4) else concept["background"]
    label(image, concept, (475, 215), "claro", 98, wordmark_color, True)
    label(image, concept, (480, 340), "H O M E  L A U N C H E R", 22, INK)
    label(image, concept, (480, 410), " ".join(concept["tagline"]), 43, INK, True)
    for i, key in enumerate(("background", "foreground", "accent")):
        x = 485 + i * 230
        draw.ellipse((x, 495, x + 28, 523), fill=concept[key], outline="#CCD1D9", width=1)
        label(image, concept, (x + 42, 500), concept[key], 20, "#59677A")
    image.paste(poster, (65, 590))
    label(image, concept, (1150, 625), "THE DIRECTION", 22, INK, True)
    descriptions = {
        0: ["A home you recognize.", "Clear tiles, a warm highlight.", "Confident blue."],
        1: ["An open C, a point of contact.", "Quiet green, soft peach.", "A more personal feel."],
        2: ["The familiar phone, simplified.", "Sunny color, bold lettering.", "Practical at first glance."],
        3: ["Conversation with heart.", "Soft corners, warm plum.", "Friendly rather than clinical."],
        4: ["A fresh start to the day.", "A sunrise, warm paper tones.", "Airy, human, unhurried."],
    }
    for y, line in zip((700, 750, 800), descriptions[index]):
        label(image, concept, (1150, y), line, 20, "#59677A")
    label(image, concept, (1150, 930), "Original vector logo", 21, INK, True)
    label(image, concept, (1150, 970), "Store icon + feature concept", 20, "#59677A")
    return image


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    selected = json.loads((ROOT / "marketing/brand/selected.json").read_text())["concept"]
    if selected not in {concept["id"] for concept in CONCEPTS}:
        raise ValueError(f"Unknown selected brand concept: {selected}")
    comparison = Image.new("RGB", (2000, 2420), "#F5F6F8")
    heading = CONCEPTS[0]
    label(comparison, heading, (75, 58), "Five ways to feel at home.", 64, INK, True)
    label(comparison, heading, (78, 155), "CLARO HOME LAUNCHER  /  LOGO, COLOR & VOICE", 26, "#59677A")
    draw = ImageDraw.Draw(comparison)
    for index, concept in enumerate(CONCEPTS):
        directory = OUTPUT / concept["id"]
        directory.mkdir(parents=True, exist_ok=True)
        vector = logo(concept, index)
        icon = vector.image.resize((1024, 1024), Image.Resampling.LANCZOS)
        (directory / "logo.svg").write_text("\n".join(vector.svg + ["</svg>"]) + "\n")
        icon.save(directory / "logo-1024.png", optimize=True)
        icon.resize((512, 512), Image.Resampling.LANCZOS).convert("RGBA").save(
            directory / "icon-512.png", optimize=True
        )
        poster = feature(concept, icon)
        poster.save(directory / "feature-1024x500.png", optimize=True)
        board(concept, icon, poster, index).save(directory / "brand-board.png", optimize=True)
        if concept["id"] == selected:
            assets = ROOT / "marketing/store/assets"
            assets.mkdir(parents=True, exist_ok=True)
            (ROOT / "marketing/brand/claro-mark.svg").write_text(
                "\n".join(vector.svg + ["</svg>"]) + "\n"
            )
            vector.image.resize((2048, 2048), Image.Resampling.LANCZOS).save(
                assets / "logo-2048.png", optimize=True
            )
            icon.resize((512, 512), Image.Resampling.LANCZOS).convert("RGBA").save(
                assets / "icon-512.png", optimize=True
            )
            poster.save(assets / "feature-1024x500.png", optimize=True)
            board(concept, icon, poster, index).save(assets / "brand-review.png", optimize=True)
        top = 260 + index * 420
        draw.rounded_rectangle((65, top, 1935, top + 385), radius=32, fill=concept["background"])
        comparison.paste(icon.resize((280, 280), Image.Resampling.LANCZOS), (125, top + 48))
        label(comparison, concept, (460, top + 45), f"0{index+1}  {concept['name'].upper()}", 26, concept["foreground"], True)
        label(comparison, concept, (455, top + 100), "claro", 76, concept["foreground"], True)
        label(comparison, concept, (845, top + 115), "HOME LAUNCHER", 24, concept["foreground"])
        label(comparison, concept, (460, top + 230), " ".join(concept["tagline"]), 48, concept["foreground"], True)
        label(comparison, concept, (462, top + 315), concept["character"], 24, concept["foreground"])
    comparison.save(OUTPUT / "five-brand-directions.png", optimize=True)
    (OUTPUT / "concepts.json").write_text(json.dumps(CONCEPTS, indent=2) + "\n")
    print(f"Rendered five brand directions to {OUTPUT}")


if __name__ == "__main__":
    main()
