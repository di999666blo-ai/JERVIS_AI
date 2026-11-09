#!/usr/bin/env python3
"""Generate a deterministic 1,000-look catalogue for BOBBY.

The 1,000 records are combinations of 10 tops x 10 bottoms x 10 shoes.
Hair and accessory selections are assigned deterministically across those
looks; they are reusable parts, not 1,000 separately modelled meshes.
"""
import json
from pathlib import Path

PARTS = {
    "tops": ["fitted_top", "jacket", "crop_top", "bodice", "blouse", "wrap_top", "hoodie", "high_neck", "vest", "evening_top"],
    "bottoms": ["skirt", "wide_leg", "shorts", "maxi_skirt", "fitted_pants", "pleated_skirt", "layered_skirt", "tailored_shorts", "straight_pants", "two_layer"],
    "shoes": ["flats", "trainers", "heels", "ankle_boots", "loafers", "platforms", "sandals", "high_tops", "knee_boots", "dress_shoes"],
    "hair": ["bob", "long_straight", "curly", "ponytail", "braids", "pixie", "waves", "bun", "layered", "fringe"],
    "accessories": ["none", "necklace", "earrings", "glasses", "hair_clip", "bracelet", "belt", "scarf", "headband", "handbag"],
}

def main():
    output = Path("app/src/main/assets/outfits_1000.json")
    output.parent.mkdir(parents=True, exist_ok=True)
    looks = []
    index = 0
    for top in range(10):
        for bottom in range(10):
            for shoe in range(10):
                looks.append({
                    "id": index,
                    "name": f"BOBBY Look {index + 1:04d}",
                    "top": PARTS["tops"][top],
                    "bottom": PARTS["bottoms"][bottom],
                    "shoes": PARTS["shoes"][shoe],
                    "hair": PARTS["hair"][(index * 7 + top + bottom) % 10],
                    "accessory": PARTS["accessories"][(index * 3 + shoe + bottom) % 10],
                    "indices": {"top": top, "bottom": bottom, "shoes": shoe},
                })
                index += 1
    payload = {"schema_version": 1, "count": len(looks), "parts": PARTS, "looks": looks}
    output.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print(f"Generated {len(looks)} looks -> {output}")

if __name__ == "__main__":
    main()
