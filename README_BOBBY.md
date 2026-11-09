# BOBBY 3D Companion — Development Notes

**Branch:** `bobby-development`  
**Android target:** API 26+ (Android 8.0+)  
**Existing app namespace:** `ai.diablo.bobby`  
**Build:** GitHub Actions; no local Android Studio or paid 3D service is required for the asset-generation workflow.

## What is in this milestone

- `scripts/generate_outfits.py` creates a deterministic JSON catalogue of 1,000 looks from 10 top, 10 bottom and 10 shoe styles. Hair and accessory part choices are reused across the catalogue. These are catalogue combinations, not 1,000 separately sculpted outfits.
- `blender/build_bobby.py` creates a stylized primitive-based humanoid prototype, a 66-bone armature, and five reusable animation actions, then exports a GLB.
- `blender/generate_animations.py` creates 1,000 composable animation-pattern descriptors (25 base motions × 40 modifier layers). These are parameters for later runtime composition, not 1,000 individually hand-keyframed clips.
- `blender/bobby_rig.py` validates that the generated rigged GLB exists.
- `.github/workflows/bobby-build.yml` installs Blender on a free GitHub-hosted runner, generates the files, validates the GLB header and size, and uploads a `bobby-3d-assets` artifact.

## Run the asset builder

Open the repository's **Actions** tab, select **BOBBY Free 3D Asset Builder**, then choose **Run workflow** on `bobby-development`.

The workflow artifact contains:

- `outfits_1000.json`
- `animation_patterns_1000.json`
- `bobby_rigged.glb`

It can also run automatically when the Blender pipeline or outfit generator changes on `bobby-development`.

## Important implementation boundary

The current Android avatar renderer is `app/src/main/java/ai/diablo/bobby/Bobby3DHumanView.kt`, an OpenGL ES 2.0 procedural renderer. The generated GLB is currently a **build artifact**, not yet loaded by the Android app. A later integration milestone must add and test a compatible GLB runtime, connect the dashboard controls to real animation/outfit state, and verify performance on Android 8 / 4 GB devices before claiming the complete 3D companion is finished.

The current GLB is an original stylized prototype made from simple meshes. It is not a photoreal human model, and the prototype actions/expression controls still need visual QA and refinement. Keep the existing BOBBY app and dashboard; do not replace them with a separate demo.

## Existing Android build

The existing Android workflow remains `.github/workflows/android-build.yml` and builds the current app. The Blender workflow is separate so asset generation can be validated without breaking the Android APK pipeline.
