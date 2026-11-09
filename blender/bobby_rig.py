"""Rig entry point retained for the documented pipeline.

The armature is built as part of build_bobby.py so mesh and skeleton stay
in sync. Run this script after build_bobby.py to validate the exported rig
source scene; it checks the generated GLB exists and is non-empty.
"""
from pathlib import Path
asset=Path("app/src/main/assets/models/bobby_rigged.glb")
if not asset.exists() or asset.stat().st_size < 1024:
    raise SystemExit("Missing rigged GLB. Run blender/build_bobby.py first.")
print(f"Rigged BOBBY GLB present: {asset.stat().st_size} bytes")
