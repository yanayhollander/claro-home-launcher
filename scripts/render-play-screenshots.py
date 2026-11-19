#!/usr/bin/env python3
"""Export Claro review compositions or clean store candidates from native captures."""

import argparse
import hashlib
import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "marketing/store/screenshot-templates"
ASSETS = BASE / "theme"
PREVIEWS = BASE / "previews"
SCREENSHOTS = ROOT / "marketing/store/screenshots"
GOLD = "#FFD166"
NAVY = "#172D42"
CREAM = "#FFFAEC"
ROUNDED = "/System/Library/Fonts/Supplemental/Arial Rounded Bold.ttf"
AVENIR = "/System/Library/Fonts/Avenir Next.ttc"
SIZE = (1080, 1920)
THEMES = {
    "gold": {"background": GOLD, "ink": NAVY, "secondary": "#36434A", "field": "#FFDC89"},
    "navy": {"background": NAVY, "ink": CREAM, "secondary": "#DAE3EB", "field": "#23425B"},
    "cream": {"background": CREAM, "ink": NAVY, "secondary": "#425363", "field": "#FFE3A0"},
}


def font(size, bold=False):
    return ImageFont.truetype(ROUNDED if bold else AVENIR, size, index=0 if bold else 5)


def text(image, xy, value, size, color, bold=False):
    ImageDraw.Draw(image).text(xy, value, font=font(size, bold), fill=color, anchor="lt")


def background(name):
    theme = THEMES[name]
    image = Image.new("RGB", SIZE, theme["background"])
    draw = ImageDraw.Draw(image)
    if name == "navy":
        draw.ellipse((-540, 690, 1030, 2260), fill=theme["field"])
        draw.ellipse((660, -420, 1580, 500), outline="#3B566B", width=2)
        shapes = '<circle cx="245" cy="1475" r="785" fill="#23425B"/><circle cx="1120" cy="40" r="460" fill="none" stroke="#3B566B" stroke-width="2"/>'
    else:
        draw.ellipse((250, 510, 1790, 2050), fill=theme["field"])
        draw.ellipse((-710, 1190, 330, 2230), outline=NAVY, width=2)
        shapes = f'<circle cx="1020" cy="1280" r="770" fill="{theme["field"]}"/><circle cx="-190" cy="1710" r="520" fill="none" stroke="{NAVY}" stroke-width="2"/>'
    svg = (
        '<svg xmlns="http://www.w3.org/2000/svg" width="1080" height="1920" viewBox="0 0 1080 1920">'
        f'<rect width="1080" height="1920" fill="{theme["background"]}"/>{shapes}</svg>\n'
    )
    (ASSETS / f"background-{name}.svg").write_text(svg)
    image.save(ASSETS / f"background-{name}.png", optimize=True)
    return image


def frame(screen_size):
    screen_width, screen_height = screen_size
    width, height = screen_width + 84, screen_height + 80
    image = Image.new("RGBA", (width, height))
    draw = ImageDraw.Draw(image)
    body = (20, 18, width - 20, height - 18)
    draw.rounded_rectangle(body, radius=72, fill="#131D29", outline="#6B7886", width=3)
    draw.rounded_rectangle(
        (25, 23, width - 25, height - 23), radius=68, outline="#344555", width=2
    )
    draw.rounded_rectangle((width - 19, 215, width - 13, 288), radius=3, fill="#72808B")
    draw.rounded_rectangle((width - 19, 322, width - 13, 440), radius=3, fill="#72808B")
    draw.rounded_rectangle((14, 240, 20, 347), radius=3, fill="#72808B")
    screen_box = (42, 40, 42 + screen_width, 40 + screen_height)
    draw.rounded_rectangle(screen_box, radius=45, fill=(0, 0, 0, 0))
    draw.rounded_rectangle(
        (width // 2 - 30, 28, width // 2 + 30, 32), radius=2, fill="#5E6B79"
    )
    draw.ellipse((width // 2 + 48, 26, width // 2 + 56, 34), fill="#426478")
    return image, screen_box


def phone(source):
    screen_width = 636
    screen_height = round(source.height * screen_width / source.width)
    casing, box = frame((screen_width, screen_height))
    layer = Image.new("RGBA", casing.size)
    mask = Image.new("L", (screen_width, screen_height))
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, screen_width - 1, screen_height - 1), radius=45, fill=255
    )
    resized = source.convert("RGB").resize((screen_width, screen_height), Image.Resampling.LANCZOS)
    layer.paste(resized, (box[0], box[1]), mask)
    layer.alpha_composite(casing)
    return layer, casing, mask


def fit_font(lines, max_width, starting=91):
    size = starting
    while size > 60:
        if all(font(size, True).getlength(line) <= max_width for line in lines):
            return size
        size -= 1
    raise ValueError(f"Headline does not fit: {lines}")


def render(board, config, backgrounds, logo, review=True):
    theme = THEMES[board["theme"]]
    image = backgrounds[board["theme"]].convert("RGBA")
    badge = logo.resize((60, 60), Image.Resampling.LANCZOS)
    image.alpha_composite(badge, (80, 68))
    text(image, (158, 85), "CLARO HOME LAUNCHER", 27, theme["ink"], True)
    size = fit_font(board["headline"], 925)
    for row, line in enumerate(board["headline"]):
        text(image, (80, 165 + row * 106), line, size, theme["ink"], True)
    text(image, (84, 392), board["caption"], 29, theme["secondary"])
    source_path = (ROOT / board["source"]).resolve()
    if not source_path.is_relative_to(ROOT) or not source_path.is_file():
        raise ValueError(f"Screenshot must be a file inside the repository: {source_path}")
    with Image.open(source_path) as source:
        if source.width < 320 or source.height < 320:
            raise ValueError(f"Source capture is too small: {source_path}")
        device, casing, mask = phone(source)
    if board is config["boards"][0]:
        casing.save(ASSETS / "phone-frame.png", optimize=True)
        mask.save(ASSETS / "phone-screen-mask.png", optimize=True)
    device = device.resize(
        (652, round(device.height * 652 / device.width)), Image.Resampling.LANCZOS
    ).rotate(board["angle"], Image.Resampling.BICUBIC, expand=True)
    x, y = (SIZE[0] - device.width) // 2, 478
    if y + device.height > 1878:
        raise ValueError(f"Phone would be clipped on {board['id']}")
    shadow = Image.new("RGBA", SIZE)
    shadow.paste((0, 0, 0, 85), (x + 8, y + 24), device.getchannel("A"))
    shadow = shadow.filter(ImageFilter.GaussianBlur(25))
    image.alpha_composite(shadow)
    image.alpha_composite(device, (x, y))
    if review:
        text(image, (82, 1885), "CREATIVE REVIEW / NATIVE DEMO CAPTURE", 18, theme["ink"], True)
    output = (PREVIEWS if review else SCREENSHOTS) / f"{board['id']}.png"
    image.convert("RGB").save(output, optimize=True)
    return {
        "id": board["id"],
        "source": board["source"],
        "sourceSha256": hashlib.sha256(source_path.read_bytes()).hexdigest(),
        "output": str(output.relative_to(ROOT)),
        "size": list(SIZE),
        "sha256": hashlib.sha256(output.read_bytes()).hexdigest(),
        "reviewWatermark": review,
        "publicationReady": False,
    }


def contact_sheet(config, review=True):
    sheet = Image.new("RGB", (1920, 2500), CREAM)
    text(sheet, (70, 50), "Big buttons. Less fuss.", 66, NAVY, True)
    text(sheet, (74, 145), "CLARO / GOLD & NAVY SCREENSHOT SYSTEM", 27, NAVY)
    for index, board in enumerate(config["boards"]):
        columns = min(3, len(config["boards"]) - index // 3 * 3)
        x = 70 + (3 - columns) * 605 // 2 + (index % 3) * 605
        y = 225 + (index // 3) * 1120
        with Image.open((PREVIEWS if review else SCREENSHOTS) / f"{board['id']}.png") as preview:
            sheet.paste(preview.resize((570, 1013), Image.Resampling.LANCZOS), (x, y))
        text(sheet, (x, y + 1035), f"0{index+1}  {board['id'][3:].replace('-', ' ').title()}", 25, NAVY, True)
    label = "FRESH NATIVE CAPTURES" if review else "CLEAN STORE CANDIDATES"
    text(sheet, (74, 2460), f"{label} / fictional contacts / final publication review pending", 22, NAVY)
    sheet.save(BASE / ("screenshot-system-review.png" if review else "store-candidates-review.png"), optimize=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--clean", action="store_true",
        help="Export watermark-free store candidates; does not approve or publish them",
    )
    args = parser.parse_args()
    review = not args.clean
    config = json.loads((BASE / "templates.json").read_text())
    selected = json.loads((ROOT / "marketing/brand/selected.json").read_text())
    if config["brand"] != selected["concept"] or tuple(config["size"]) != SIZE:
        raise ValueError("Screenshot templates must match the selected brand and Play dimensions")
    if config["publicationReady"]:
        raise ValueError("Final creative approval and signed-release verification are required before publication")
    manifest_path = (ROOT / config["captureManifest"]).resolve()
    if not manifest_path.is_relative_to(ROOT):
        raise ValueError("Capture manifest must be inside the repository")
    manifest = json.loads(manifest_path.read_text())
    captures = {capture["path"]: capture["sha256"] for capture in manifest["captures"]}
    for board in config["boards"]:
        source = (ROOT / board["source"]).resolve()
        if not source.is_relative_to(ROOT) or not source.is_file():
            raise ValueError(f"Native capture is missing or outside the repository: {source}")
        if hashlib.sha256(source.read_bytes()).hexdigest() != captures.get(board["source"]):
            raise ValueError(f"Native capture is not recorded or its hash changed: {board['source']}")
    for directory in (ASSETS, PREVIEWS if review else SCREENSHOTS):
        directory.mkdir(parents=True, exist_ok=True)
    backgrounds = {name: background(name) for name in THEMES}
    with Image.open(ROOT / "marketing/brand/concepts/03-big-and-simple/icon-512.png") as original:
        logo = original.convert("RGBA")
    outputs = [render(board, config, backgrounds, logo, review) for board in config["boards"]]
    for result in outputs:
        path = ROOT / result["output"]
        with Image.open(path) as image:
            if image.size != SIZE or image.mode != "RGB" or path.stat().st_size > 8 * 1024 * 1024:
                raise ValueError(f"Invalid Play image dimensions, format or size: {path}")
    manifest_output = (BASE if review else SCREENSHOTS) / "export-manifest.json"
    manifest_output.write_text(
        json.dumps({
            "publicationReady": False,
            "reviewWatermark": review,
            "captureStatus": config["captureStatus"],
            "captureManifest": config["captureManifest"],
            "exports": outputs,
        }, indent=2) + "\n"
    )
    contact_sheet(config, review)
    destination = PREVIEWS if review else SCREENSHOTS
    print(f"Rendered {len(outputs)} {'review compositions' if review else 'clean store candidates'} to {destination}")


if __name__ == "__main__":
    main()
