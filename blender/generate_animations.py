"""Write the 1,000 composable animation-pattern manifest.

The GLB currently carries five reusable base actions. This script creates
25 x 40 deterministic parameter combinations for the app's animation
controller; it does not pretend these are 1,000 hand-keyframed clips.
"""
import json
from pathlib import Path

BASE = ["idle", "walk", "run_in_place", "dance", "sit", "talk", "wave", "greet", "think", "listen",
        "happy", "sad", "curious", "surprised", "confident", "shy", "stretch", "look_left", "look_right",
        "nod", "shake_head", "point", "hands_on_hips", "celebrate", "relax"]
LAYERS = ["slow", "normal", "energetic", "soft", "strong", "left_bias", "right_bias", "head_tilt",
          "shoulder_sway", "hip_sway", "arm_open", "arm_cross", "hand_wave", "blink", "smile",
          "brow_raise", "gaze_left", "gaze_right", "gaze_up", "gaze_down", "breath_deep", "breath_light",
          "hair_sway", "cloth_sway", "turn_small", "turn_large", "bounce", "lean_forward", "lean_back",
          "weight_shift", "wrist_flex", "finger_spread", "finger_relax", "foot_tap", "knee_bend",
          "torso_twist", "head_nod", "head_shake", "expression_warm", "expression_playful"]

def main():
    patterns=[]
    for base_index, base in enumerate(BASE):
        for layer_index, layer in enumerate(LAYERS):
            patterns.append({
                "id": base_index*len(LAYERS)+layer_index,
                "base": base,
                "layer": layer,
                "speed": round(0.75 + (layer_index % 8)*0.1, 2),
                "amplitude": round(0.65 + ((base_index*3+layer_index) % 8)*0.08, 2),
                "loop": base not in {"greet", "wave", "celebrate"},
            })
    out=Path("app/src/main/assets/animation_patterns_1000.json")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({"schema_version":1,"count":len(patterns),"patterns":patterns},indent=2)+"\n",encoding="utf-8")
    print(f"Generated {len(patterns)} animation patterns -> {out}")

if __name__ == "__main__":
    main()
