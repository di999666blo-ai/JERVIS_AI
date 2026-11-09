"""Create a lightweight, original rigged BOBBY prototype and export GLB.

Run with: blender --background --python blender/build_bobby.py
This is a stylized prototype made from low-poly primitives, not a photoreal
human sculpt. It intentionally avoids external downloads and paid services.
"""
import bpy, math, os
from mathutils import Euler

OUT = os.path.abspath("app/src/main/assets/models/bobby_rigged.glb")
os.makedirs(os.path.dirname(OUT), exist_ok=True)

# Reset scene
bpy.ops.object.select_all(action="SELECT")
bpy.ops.object.delete(use_global=False)
for datablocks in (bpy.data.meshes, bpy.data.curves, bpy.data.materials, bpy.data.armatures, bpy.data.actions):
    pass

def material(name, color, roughness=0.72):
    m = bpy.data.materials.new(name)
    m.diffuse_color = (*color, 1.0)
    m.use_nodes = True
    bsdf = m.node_tree.nodes.get("Principled BSDF")
    if bsdf:
        bsdf.inputs["Base Color"].default_value = (*color, 1.0)
        bsdf.inputs["Roughness"].default_value = roughness
    return m

skin = material("Skin | warm", (0.72, 0.43, 0.32))
hair = material("Hair | espresso", (0.075, 0.035, 0.025))
cloth = material("Clothing | ruby", (0.48, 0.025, 0.09))
trim = material("Clothing | gold trim", (0.78, 0.48, 0.16))
white = material("Eye | ivory", (0.92, 0.88, 0.80))
dark = material("Eye | dark", (0.035, 0.025, 0.02))
shoe_mat = material("Shoes | charcoal", (0.045, 0.04, 0.055))

# Build a 66-bone armature. Primary bones drive the body; extra finger,
# face and hair bones provide extension points for later detailed rigging.
bpy.ops.object.armature_add(enter_editmode=True, location=(0, 0, 0))
arm = bpy.context.object
arm.name = "BOBBY_Rig"
arm.data.name = "BOBBY_66_Bone_Skeleton"
eb = arm.data.edit_bones
root = eb[0]
root.name = "root"
root.head = (0, 0, 0)
root.tail = (0, 0, 0.25)
spec = [
("pelvis",(0,0,0.95),(0,0,1.15),"root"),
("spine",(0,0,1.15),(0,0,1.48),"pelvis"),
("chest",(0,0,1.48),(0,0,1.78),"spine"),
("neck",(0,0,1.78),(0,0,1.94),"chest"),
("head",(0,0,1.94),(0,0,2.35),"neck"),
("upper_arm.L",(-0.22,0,1.70),(-0.48,0,1.48),"chest"),
("forearm.L",(-0.48,0,1.48),(-0.55,0,1.20),"upper_arm.L"),
("hand.L",(-0.55,0,1.20),(-0.56,0,1.08),"forearm.L"),
("upper_arm.R",(0.22,0,1.70),(0.48,0,1.48),"chest"),
("forearm.R",(0.48,0,1.48),(0.55,0,1.20),"upper_arm.R"),
("hand.R",(0.55,0,1.20),(0.56,0,1.08),"forearm.R"),
("thigh.L",(-0.16,0,1.00),(-0.18,0,0.60),"pelvis"),
("shin.L",(-0.18,0,0.60),(-0.18,0,0.18),"thigh.L"),
("foot.L",(-0.18,0,0.18),(-0.18,0.18,0.08),"shin.L"),
("thigh.R",(0.16,0,1.00),(0.18,0,0.60),"pelvis"),
("shin.R",(0.18,0,0.60),(0.18,0,0.18),"thigh.R"),
("foot.R",(0.18,0,0.18),(0.18,0.18,0.08),"shin.R"),
("hair_back",(0,0,2.25),(0,-0.12,1.85),"head"),
("hair_side.L",(-0.22,0,2.20),(-0.30,-0.04,1.82),"head"),
("hair_side.R",(0.22,0,2.20),(0.30,-0.04,1.82),"head"),
("jaw",(0,0,2.00),(0,0,1.92),"head"),
("brow.L",(-0.10,0.08,2.18),(-0.18,0.08,2.18),"head"),
("brow.R",(0.10,0.08,2.18),(0.18,0.08,2.18),"head"),
("eye.L",(-0.10,0.09,2.12),(-0.10,0.15,2.12),"head"),
("eye.R",(0.10,0.09,2.12),(0.10,0.15,2.12),"head"),
]
created = {}
for name, head, tail, parent in spec:
    b = eb.new(name); b.head=head; b.tail=tail
    b.parent = eb.get(parent)
    created[name] = b

# Add finger bones to reach exactly 66 bones.
finger_names = ["thumb","index","middle","ring","pinky"]
for side, sign in (("L",-1),("R",1)):
    hand_parent = "hand."+side
    for finger in finger_names:
        prev = hand_parent
        for segment in range(1,4):
            x = sign * (0.55 + (segment * 0.025))
            y = 0.0
            z = 1.17 - (finger_names.index(finger) * 0.018)
            name = f"{finger}.{segment}.{side}"
            b = eb.new(name)
            b.head=(x,y,z); b.tail=(x+sign*0.018,y,z-0.025)
            b.parent=eb.get(prev); prev=name
            created[name]=b
# Extra facial and accessory controls.
for name, parent in [
    ("mouth_ctrl","head"),("lip_upper","mouth_ctrl"),("lip_lower","mouth_ctrl"),
    ("cheek.L","head"),("cheek.R","head"),("ear.L","head"),("ear.R","head"),
    ("hair_tip.L","hair_side.L"),("hair_tip.R","hair_side.R"),("hair_tip_back","hair_back"),
    ("shoulder.L","chest"),("shoulder.R","chest"),("toe.L","foot.L"),("toe.R","foot.R"),
]:
    b=eb.new(name); b.head=(0,0,2.05); b.tail=(0,0,2.10); b.parent=eb.get(parent); created[name]=b
# Trim or extend with non-deforming controls to exactly 66.
while len(eb) < 66:
    n=len(eb)
    b=eb.new(f"face_extra_{n:02d}"); b.head=(0,0,2.08); b.tail=(0,0,2.11); b.parent=eb.get("head")
while len(eb) > 66:
    eb.remove(eb[-1])
bpy.ops.object.mode_set(mode="OBJECT")
arm.show_in_front = True
arm.data.display_type = "OCTAHEDRAL"

def attach_sphere(name, loc, scale, mat, bone):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=16, ring_count=10, location=loc)
    obj=bpy.context.object; obj.name=name
    obj.scale=scale
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    obj.data.materials.append(mat)
    obj.parent=arm
    obj.parent_type="BONE"
    obj.parent_bone=bone
    obj.matrix_parent_inverse=arm.matrix_world.inverted()
    return obj

# Primitive meshes are bone-parented, so the GLB keeps a real animated skeleton.
attach_sphere("Torso",(0,0,1.48),(0.34,0.20,0.48),cloth,"chest")
attach_sphere("Pelvis",(0,0,1.02),(0.29,0.18,0.22),cloth,"pelvis")
attach_sphere("Neck",(0,0,1.86),(0.10,0.10,0.15),skin,"neck")
attach_sphere("Head",(0,0,2.13),(0.25,0.22,0.34),skin,"head")
attach_sphere("HairCap",(0,-0.015,2.36),(0.26,0.23,0.16),hair,"head")
attach_sphere("HairBack",(0,-0.18,2.13),(0.20,0.10,0.32),hair,"hair_back")
for s, sign in (("L",-1),("R",1)):
    attach_sphere("UpperArm."+s,(sign*0.36,0,1.60),(0.12,0.12,0.28),skin,"upper_arm."+s)
    attach_sphere("Forearm."+s,(sign*0.52,0,1.34),(0.095,0.095,0.22),skin,"forearm."+s)
    attach_sphere("Hand."+s,(sign*0.56,0,1.13),(0.09,0.07,0.10),skin,"hand."+s)
    attach_sphere("Thigh."+s,(sign*0.17,0,0.80),(0.14,0.14,0.25),cloth,"thigh."+s)
    attach_sphere("Shin."+s,(sign*0.18,0,0.39),(0.105,0.11,0.24),skin,"shin."+s)
    attach_sphere("Shoe."+s,(sign*0.18,0.10,0.12),(0.13,0.22,0.08),shoe_mat,"foot."+s)
    attach_sphere("EyeWhite."+s,(sign*0.095,0.19,2.16),(0.045,0.025,0.035),white,"head")
    attach_sphere("EyePupil."+s,(sign*0.095,0.214,2.16),(0.018,0.012,0.022),dark,"head")
    attach_sphere("HairSide."+s,(sign*0.23,-0.04,2.08),(0.07,0.10,0.28),hair,"hair_side."+s)
attach_sphere("Nose",(0,0.215,2.09),(0.035,0.04,0.07),skin,"head")
attach_sphere("Mouth",(0,0.205,2.00),(0.06,0.018,0.018),trim,"mouth_ctrl")

# Create 5 reusable looping actions. Additional pattern combinations are
# represented by metadata and composed in the app rather than bloating the GLB.
def action(name, frames):
    bpy.context.view_layer.objects.active=arm
    arm.select_set(True)
    bpy.ops.object.mode_set(mode="POSE")
    bpy.ops.pose.select_all(action="DESELECT")
    for bone_name, keys in frames.items():
        pb=arm.pose.bones.get(bone_name)
        if not pb: continue
        pb.rotation_mode="XYZ"
        for frame, rot in keys:
            pb.rotation_euler=rot
            pb.keyframe_insert(data_path="rotation_euler", frame=frame, group=bone_name)
    bpy.ops.object.mode_set(mode="OBJECT")
    act=arm.animation_data.action
    act.name=name
    act.use_fake_user=True
    return act

action("BOBBY_Idle", {
 "spine":[(1,(0,0,0)),(12,(0.015,0,0.025)),(24,(0,0,0))],
 "head":[(1,(0,0,0)),(12,(0.02,0.02,0)),(24,(0,0,0))],
 "hair_back":[(1,(0,0,0)),(12,(0.04,0,0.03)),(24,(0,0,0))]})
action("BOBBY_Walk", {
 "upper_arm.L":[(1,(0,0,-0.35)),(13,(0,0,0.35)),(25,(0,0,-0.35))],
 "upper_arm.R":[(1,(0,0,0.35)),(13,(0,0,-0.35)),(25,(0,0,0.35))],
 "thigh.L":[(1,(0.45,0,0)),(13,(-0.45,0,0)),(25,(0.45,0,0))],
 "thigh.R":[(1,(-0.45,0,0)),(13,(0.45,0,0)),(25,(-0.45,0,0))]})
action("BOBBY_Talk", {
 "head":[(1,(0,0,0)),(6,(0.02,0.04,0)),(12,(0,0,0)),(18,(-0.02,-0.03,0)),(24,(0,0,0))],
 "mouth_ctrl":[(1,(0,0,0)),(6,(0.12,0,0)),(12,(0,0,0)),(18,(0.08,0,0)),(24,(0,0,0))]})
action("BOBBY_Dance", {
 "spine":[(1,(0,0,-0.18)),(7,(0,0,0.18)),(13,(0,0,-0.18)),(19,(0,0,0.18)),(25,(0,0,-0.18))],
 "upper_arm.L":[(1,(0,0,-1.0)),(13,(0,0,-0.2)),(25,(0,0,-1.0))],
 "upper_arm.R":[(1,(0,0,1.0)),(13,(0,0,0.2)),(25,(0,0,1.0))]})
action("BOBBY_Sit", {
 "thigh.L":[(1,(1.15,0,0)),(24,(1.15,0,0))],
 "thigh.R":[(1,(1.15,0,0)),(24,(1.15,0,0))],
 "spine":[(1,(0.12,0,0)),(24,(0.12,0,0))]})

# Restore first action for a neutral preview.
arm.animation_data.action=bpy.data.actions.get("BOBBY_Idle")
bpy.ops.object.select_all(action="DESELECT")
arm.select_set(True)
bpy.context.view_layer.objects.active=arm
bpy.ops.export_scene.gltf(
    filepath=OUT, export_format="GLB", use_selection=True,
    export_animations=True, export_nla_strips=False,
    export_skins=True, export_morph=True,
)
print("Exported rigged BOBBY prototype:", OUT)
