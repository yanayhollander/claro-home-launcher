#!/usr/bin/env python3
"""Prepare fictional contact assets and an offline vCard; never modify a device."""

import argparse
import base64
import hashlib
import io
import json
import re
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont, ImageOps

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "marketing/store/demo"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--portrait-sheet", type=Path, required=True)
    args = parser.parse_args()
    config = json.loads((BASE / "contacts.json").read_text())
    portraits = BASE / "portraits"
    portraits.mkdir(parents=True, exist_ok=True)
    cards = []
    overview = Image.new("RGB", (1440, 1300), "#FFFAEC")
    draw = ImageDraw.Draw(overview)
    title = ImageFont.truetype("/System/Library/Fonts/Avenir Next.ttc", 48, index=0)
    label = ImageFont.truetype("/System/Library/Fonts/Avenir Next.ttc", 29, index=0)
    small = ImageFont.truetype("/System/Library/Fonts/Avenir Next.ttc", 22, index=5)
    draw.text((64, 42), "Meet the Claro demo contacts", font=title, fill="#172D42")
    draw.text((66, 115), "AI-generated portraits / fictional names / reserved demo numbers",
              font=small, fill="#425363")
    with Image.open(args.portrait_sheet) as source:
        if source.width % 3 or source.height % 2 or source.width // 3 != source.height // 2:
            raise ValueError("Expected a 3-by-2 grid of equal square portraits")
        source.convert("RGB").save(BASE / "generated-portrait-sheet.png", optimize=True)
        edge = source.width // 3
        for index, person in enumerate(config["contacts"]):
            if not re.fullmatch(r"\+120255501\d\d", person["phone"]):
                raise ValueError("Demo phone numbers must use the reserved fictional range")
            col, row = person["portraitCell"]
            if col not in range(3) or row not in range(2):
                raise ValueError("Invalid portrait cell")
            photo = source.crop((col * edge, row * edge, (col + 1) * edge, (row + 1) * edge))
            photo = photo.convert("RGB")
            photo.save(portraits / f"{person['id']}.jpg", quality=94, optimize=True)
            thumbnail = io.BytesIO()
            photo.resize((256, 256), Image.Resampling.LANCZOS).save(
                thumbnail, format="JPEG", quality=90, optimize=True
            )
            name = f"{person['givenName']} {person['familyName']}"
            vcard = [
                "BEGIN:VCARD",
                "VERSION:3.0",
                f"UID:claro-fictional-{person['id']}",
                f"N:{person['familyName']};{person['givenName']};;;",
                f"FN:{name}",
                f"TEL;TYPE=CELL:{person['phone']}",
                "NOTE:Fictional Claro screenshot contact. AI-generated portrait. Do not call.",
                "PHOTO;ENCODING=b;TYPE=JPEG:" + base64.b64encode(thumbnail.getvalue()).decode("ascii"),
                "END:VCARD",
            ]
            for line in vcard:
                # RFC 2425 folding: 75 octets, including continuation whitespace.
                cards.append(line[:75])
                line = line[75:]
                while line:
                    cards.append(" " + line[:74])
                    line = line[74:]
            x, y = 66 + (index % 3) * 460, 190 + (index // 3) * 530
            overview.paste(ImageOps.fit(photo, (388, 388)), (x, y))
            draw.text((x, y + 406), name, font=label, fill="#172D42")
            draw.text((x, y + 451), person["phone"], font=small, fill="#425363")
    (BASE / "claro-fictional-contacts.vcf").write_bytes(("\r\n".join(cards) + "\r\n").encode("utf-8"))
    overview.save(BASE / "demo-contacts-review.png", optimize=True)
    (BASE / "provenance.json").write_text(json.dumps({
        "fictional": True,
        "portraitSource": config["portraitSource"],
        "sourceSha256": hashlib.sha256(args.portrait_sheet.read_bytes()).hexdigest(),
        "contacts": len(config["contacts"]),
        "publicationReady": False,
        "captureStatus": config["captureStatus"],
    }, indent=2) + "\n")
    print(f"Prepared {len(config['contacts'])} fictional contacts, portraits and offline vCard in {BASE}")


if __name__ == "__main__":
    main()
