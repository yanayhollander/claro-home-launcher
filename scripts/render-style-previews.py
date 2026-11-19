#!/usr/bin/env python3
"""Refresh both in-app phone-style images from privacy-reviewed native captures.

Run python3 scripts/render-style-previews.py whenever either home layout changes.
Capture classic-home.png and touch-home.png in marketing/store/captures first.
"""

import hashlib
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
CAPTURES = ROOT / "marketing/store/captures"
RESOURCES = ROOT / "feature/onboarding-settings/src/main/res/drawable-nodpi"


def main():
    images = []
    for mode in ("classic", "touch"):
        source = CAPTURES / f"{mode}-home.png"
        with Image.open(source) as original:
            if original.width < 320 or original.height <= original.width:
                raise ValueError(f"Expected a native portrait home capture: {source}")
            image = original.convert("RGB")
            image.thumbnail((360, 900), Image.Resampling.LANCZOS)
        images.append((mode, source, image))

    RESOURCES.mkdir(parents=True, exist_ok=True)
    exports = []
    for mode, source, image in images:
        target = RESOURCES / f"phone_style_{mode}.png"
        image.save(target, optimize=True)
        exports.append({
            "source": str(source.relative_to(ROOT)),
            "sourceSha256": hashlib.sha256(source.read_bytes()).hexdigest(),
            "resource": str(target.relative_to(ROOT)),
            "resourceSha256": hashlib.sha256(target.read_bytes()).hexdigest(),
            "size": list(image.size),
        })
    (CAPTURES / "style-preview-manifest.json").write_text(
        json.dumps({"refreshCommand": "python3 scripts/render-style-previews.py", "exports": exports}, indent=2) + "\n"
    )
    print("Refreshed Classic and Touch in-app style-selection images.")


if __name__ == "__main__":
    main()
