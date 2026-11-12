# BOBBY 3D Character Model Drop Zone

Upload the user's original BOBBY character STL file into this folder.

## Required upload path

`app/src/main/assets/models/bobby/bobby-source.stl`

Keep the original STL unchanged. Do not replace or delete it during conversion.

## Important: STL is source geometry, not the final Android character

STL normally contains triangle geometry only. It does not reliably contain:
- skin or clothing textures/materials;
- a humanoid skeleton/armature;
- facial blendshapes;
- animation clips;
- reliable real-world scale or orientation metadata.

The Android app should ultimately load an optimised, rigged GLB asset, expected at:

`app/src/main/assets/models/bobby/bobby.glb`

The GLB will be produced from the original STL after inspecting its topology, scale, orientation, mesh parts and polygon count. If the STL is a single fused mesh, separate clothing/hair/face parts and rigging may need additional modelling work. Do not assume that a single STL can be automatically turned into a fully animated realistic human.

## Upload instructions

1. Upload the original STL as `bobby-source.stl` in this folder on the `bobby-development` branch.
2. Do not upload a ZIP unless the model is split into multiple source files; if so, keep the original files and describe them in a note.
3. After upload, tell the assistant: **"BOBBY STL uploaded — inspect it."**

## Conversion and integration checklist

- [ ] Inspect file size, mesh bounds, units, orientation and polygon count.
- [ ] Check for multiple shells / separate body, hair and clothing meshes.
- [ ] Repair mesh issues if possible without changing the source.
- [ ] Determine whether the model needs retopology and UV mapping.
- [ ] Create materials and textures if the STL has none.
- [ ] Create a humanoid armature and skin weights.
- [ ] Add idle, turn, gesture and talking/lip-sync animation as feasible.
- [ ] Export a mobile-optimised GLB.
- [ ] Integrate with BOBBY's Android 3D viewer and customisation system.
- [ ] Build and test on the Android 8 target device.

## Mobile performance

The target device is an older Android phone. Keep a high-quality source copy, but create a separate optimised runtime GLB with controlled polygon count, texture sizes and animation complexity. Never overwrite the original STL.
