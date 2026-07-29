"""Export the rig to JSON for the HTML viewer, so the viewer cannot drift from the game.

The viewer is a SECOND renderer, and two copies of one rule drift — the copy nobody is
watching is the one that goes wrong. This repo has paid for that twice already (`--check`
measuring against a retired mesh table and reporting 126–304 phantom faults on every file;
two copies of the shape rule before `solids.py` was made the single owner).

So the viewer does not carry a box table. It reads this file, which is generated from
`npc_uv.PLAYER_BOXES` — the single owner — plus the cube geometry and inflations taken from
`NpcModel.createBodyLayer`. Change the rig and re-run this; nothing has to be retyped.

    python export_rig.py            # writes viewer/rig.json and viewer/manifest.json
"""

import base64
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from npc_uv import PLAYER_BOXES, faces  # noqa: E402

TOOLS = Path(__file__).resolve().parent
REPO = TOOLS.parent
TEX = REPO / "common/src/main/resources/assets/onceuponatown/textures/entity/npc"
OUT = TOOLS / "viewer"

# Cube geometry, straight out of NpcModel.createBodyLayer / HumanoidModel.createMesh.
#
# origin is the corner passed to addBox in model space (x right, y DOWN, z forward), size is
# w/h/d, pivot is the PartPose the part hangs from, inflate is its CubeDeformation. A second
# layer is the same box with a bigger inflation — which is exactly why silhouette is a texture
# question and not a geometry one: the shell is already there.
PARTS = {
    "head":      dict(origin=(-4, -8, -4), size=(8, 8, 8),  pivot=(0, 0, 0),   inflate=0.0),
    "hat":       dict(origin=(-4, -8, -4), size=(8, 8, 8),  pivot=(0, 0, 0),   inflate=0.5),
    "body":      dict(origin=(-4, 0, -2),  size=(8, 12, 4), pivot=(0, 0, 0),   inflate=0.0),
    "body_outer":dict(origin=(-4, 0, -2),  size=(8, 12, 4), pivot=(0, 0, 0),   inflate=0.25),
    "r_arm":     dict(origin=(-3, -2, -2), size=(4, 12, 4), pivot=(-5, 2, 0),  inflate=0.0),
    "r_arm_outer":dict(origin=(-3, -2, -2),size=(4, 12, 4), pivot=(-5, 2, 0),  inflate=0.25),
    "l_arm":     dict(origin=(-1, -2, -2), size=(4, 12, 4), pivot=(5, 2, 0),   inflate=0.0),
    "l_arm_outer":dict(origin=(-1, -2, -2),size=(4, 12, 4), pivot=(5, 2, 0),   inflate=0.25),
    "r_leg":     dict(origin=(-2, 0, -2),  size=(4, 12, 4), pivot=(-1.9, 12, 0), inflate=0.0),
    "r_leg_outer":dict(origin=(-2, 0, -2), size=(4, 12, 4), pivot=(-1.9, 12, 0), inflate=0.25),
    "l_leg":     dict(origin=(-2, 0, -2),  size=(4, 12, 4), pivot=(1.9, 12, 0), inflate=0.0),
    "l_leg_outer":dict(origin=(-2, 0, -2), size=(4, 12, 4), pivot=(1.9, 12, 0), inflate=0.25),
}

# Which texture layer paints which part. This IS the game's layer model, and the viewer must
# obey it or it shows something the game cannot draw:
#   base     one PNG per body — skin, face, shift, hose. NOT tintable: the base render pass
#            gets a hardcoded -1 (see the burg-skins skill, law 6).
#   garment  its own PNG on the outer cubes, tinted per person.
#   hair     its own PNG on the `hat` shell, tinted per person. Beard shares it.
OUTER = ["body_outer", "r_arm_outer", "l_arm_outer", "r_leg_outer", "l_leg_outer"]

LAYERS = {
    "base":     ["head", "body", "r_arm", "l_arm", "r_leg", "l_leg"],
    "garment":  OUTER,
    # The braid that buys the top wealth rung. Its own pass on the same outer cubes, its own
    # tint, so it competes for none of the cloth's colour volume.
    "trim":     OUTER,
    # Three paintings on the one `hat` shell. Drawn in this order so a covering sits over hair.
    "hair":     ["hat"],
    "beard":    ["hat"],
    "headwear": ["hat"],
}

TINTABLE = {"base": False, "garment": True, "trim": True,
            "hair": True, "beard": True, "headwear": True}


def rig():
    out = {"textureSize": [64, 64], "parts": {}, "layers": LAYERS, "tintable": TINTABLE}
    for name, box in PLAYER_BOXES.items():
        u, v, w, h, d = box
        geom = PARTS.get(name)
        if geom is None:
            print(f"  ! {name} is in PLAYER_BOXES with no geometry here -- skipped")
            continue
        out["parts"][name] = {
            "uv": {k: list(r) for k, r in faces(u, v, w, h, d).items()},
            "origin": list(geom["origin"]),
            "size": list(geom["size"]),
            "pivot": list(geom["pivot"]),
            "inflate": geom["inflate"],
        }
    missing = set(PARTS) - set(out["parts"])
    if missing:
        print(f"  ! geometry with no PLAYER_BOXES entry: {sorted(missing)}")
    return out


def manifest():
    """What there is to look at. Grouped by the slot it fills, so the viewer can build its
    dropdowns without knowing any filenames."""
    def sorted_names(pattern):
        return sorted(p.name for p in TEX.glob(pattern))

    bodies = sorted_names("citizen_body_*.png") or sorted_names("citizen_m_*.png")
    return {
        "textureDir": "../../common/src/main/resources/assets/onceuponatown/textures/entity/npc",
        "slots": {
            "base":    bodies + sorted_names("citizen_skin_*.png") + ["default_skin.png"],
            "garment": [""] + sorted_names("*_clothes.png"),
            # Hair, beard and covering are three separate paintings on the SAME `hat` shell,
            # so each gets its own slot rather than being one dropdown -- a person wears a
            # hairstyle AND may wear a covering over it.
            "hair":     [""] + sorted_names("citizen_hair_*.png"),
            "beard":    [""] + sorted_names("citizen_beard_*.png"),
            "headwear": [""] + sorted_names("citizen_headwear_*.png"),
            "trim":     [""] + sorted_names("citizen_trim.png"),
        },
        # The researched range, poor end first. Same values as NpcLook.TINTS and people.Wealth,
        # and they belong in one place -- if these drift from the Java the viewer lies.
        "wealthTints": [
            {"name": "destitute",   "argb": "#FFB0A498"},
            {"name": "poor",        "argb": "#FFD8C9A8"},
            {"name": "comfortable", "argb": "#FFC08A63"},
            {"name": "rich",        "argb": "#FF8C6FA8"},
        ],
        "hairTints": [
            {"name": "black",      "argb": "#FF2B2118"},
            {"name": "dark brown", "argb": "#FF4A3524"},
            {"name": "mid brown",  "argb": "#FF6B4A2E"},
            {"name": "fair",       "argb": "#FFB08B54"},
            {"name": "grey",       "argb": "#FF9A948C"},
        ],
    }


def textures(man):
    """Every PNG the viewer can show, as a data URI.

    Embedded rather than fetched, and that is not laziness. `fetch` from `file://` is blocked,
    and an `<img>` loaded from `file://` TAINTS the canvas so `getImageData` throws -- which
    would kill the tint, which is the one thing the viewer has to get exactly right. Inlining
    makes the file open on a double click with no server. It costs almost nothing: the whole
    body set compresses to about 50 KB.
    """
    out = {}
    for names in man["slots"].values():
        for name in names:
            if not name or name in out:
                continue
            path = TEX / name
            if not path.exists():
                print(f"  ! manifest names {name}, which is not on disk -- skipped")
                continue
            b64 = base64.b64encode(path.read_bytes()).decode("ascii")
            out[name] = "data:image/png;base64," + b64
    return out


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    r, m = rig(), manifest()
    t = textures(m)

    template = (OUT / "viewer_template.html").read_text(encoding="utf-8")
    data = json.dumps({"rig": r, "manifest": m, "textures": t}, separators=(",", ":"))
    marker = "/*__DATA__*/"
    if marker not in template:
        raise SystemExit(f"! the template has no {marker} to inject into")
    html = template.replace(marker, "const DATA = " + data + ";", 1)
    target = OUT / "citizens.html"
    target.write_text(html, encoding="utf-8")

    print(f"+ rig: {len(r['parts'])} parts, {len(LAYERS)} layers")
    for slot, names in m["slots"].items():
        real = [n for n in names if n]
        print(f"+ {slot}: {len(real)} option(s)" + (f" -> {real[:3]}" if real else ""))
    print(f"+ {len(t)} texture(s) embedded")
    print(f"+ {target}  ({target.stat().st_size // 1024} KB, opens with a double click)")
