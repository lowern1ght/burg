"""Relay the NPC textures from the villager mesh's UV onto the player mesh's UV.

`NpcModel.createBodyLayer()` used to build a villager: head 8x10x8, a nose, a torso 6 deep and
a 20-tall robe hanging past the legs. It now builds a person on the player's own layout. The
textures did not move with it, and the mismatch is not subtle — the old mesh MIRRORED its right
arm and leg instead of owning left ones, so the player layout's (32,48) and (16,48) are empty in
every file and a citizen renders with no left limbs at all.

This is a mechanical relay, not artwork. Every face of every box is copied from where the
villager net put it to where the player net wants it, and the right limbs are mirrored into the
new left slots so the silhouette matches what the mirrored mesh used to draw.

Two things it CANNOT do, and both want a human afterwards:

  * The head loses two rows (10 -> 8). A villager's face is drawn for a taller box, so the crop
    takes a row off the hair and a row off the chin. Faces will want tidying by hand.
  * A long robe does not exist on a human. The garment's top 12 rows become a tunic on the body's
    outer layer and the skirt is dropped, because there is nowhere for it to go.

Writes to `npc_uv_out/` and never over the originals: the old files are the only copy of the
hand-drawn work, and a bad relay must be discardable.

    python remap_npc_uv.py            # relay + report
    python remap_npc_uv.py --check    # report on what is already in npc_uv_out/
"""

import os
import sys

from PIL import Image

SRC_DIR = os.path.join("..", "common", "src", "main", "resources", "assets",
                       "onceuponatown", "textures", "entity", "npc")
OUT_DIR = "npc_uv_out"

# Files that were AUTHORED on the player layout and have nothing to relay. `make_female_skins.py`
# draws straight onto the new UV, so running the relay over one would read its face out of the
# villager net's coordinates and report on the garbage that came back.
ALREADY_PLAYER_UV = ("citizen_skin_f",)


def faces(u, v, w, h, d):
    """The six face rectangles of a Minecraft box net at texOffs(u, v).

    A box unwraps top/bottom in a d-tall strip, then right/front/left/back in an h-tall one.
    Total net is 2d+2w wide and d+h tall — the same convention CubeListBuilder emits, which is
    why this can be read straight off a texOffs and a box size.
    """
    return {
        "top":    (u + d,         v,     w, d),
        "bottom": (u + d + w,     v,     w, d),
        "right":  (u,             v + d, d, h),
        "front":  (u + d,         v + d, w, h),
        "left":   (u + d + w,     v + d, d, h),
        "back":   (u + 2 * d + w, v + d, w, h),
    }


# name -> (source box, destination box, vertical anchor)
#
# Anchor is 'center' everywhere except a garment: cloth hangs from the shoulders, so a robe
# cropped from the middle would lose its collar and keep its hem, which reads as a bib.
SKIN_PARTS = [
    ("head",   (0, 0, 8, 10, 8),   (0, 0, 8, 8, 8),    "center", False),
    ("body",   (16, 20, 8, 12, 6), (16, 16, 8, 12, 4), "center", False),
    ("r_arm",  (44, 22, 4, 12, 4), (40, 16, 4, 12, 4), "center", False),
    ("l_arm",  (44, 22, 4, 12, 4), (32, 48, 4, 12, 4), "center", True),
    ("r_leg",  (0, 22, 4, 12, 4),  (0, 16, 4, 12, 4),  "center", False),
    ("l_leg",  (0, 22, 4, 12, 4),  (16, 48, 4, 12, 4), "center", True),
]

# A garment goes on the OUTER layer only, and this is not a detail.
#
# The clothes layer re-renders the same mesh with the garment texture, so anything a garment
# paints on a base region is drawn in exactly the same place as the skin underneath it —
# z-fighting, a flickering seam that looks like a broken model rather than a shirt. The outer
# cubes are inflated 0.25, which is what makes cloth sit proud of the body.
#
# The villager's fused crossed-arms cube shared texOffs(44,22) with the real arms, so every
# garment carries a sleeve there. Those go to the arms' outer layer, not their base.
GARMENT_PARTS = [
    ("tunic",       (0, 38, 8, 20, 6),  (16, 32, 8, 12, 4), "top",    False),
    ("r_sleeve",    (44, 22, 4, 12, 4), (40, 32, 4, 12, 4), "center", False),
    ("l_sleeve",    (44, 22, 4, 12, 4), (48, 48, 4, 12, 4), "center", True),
]

# Which faces swap when a limb is mirrored. Every face is also flipped horizontally: that pair
# of operations is what `CubeListBuilder.mirror()` does, and reproducing it in the texture is
# what lets a dedicated left-limb region look like the mirrored right one it replaces.
MIRROR_SWAP = {"right": "left", "left": "right",
               "front": "front", "back": "back",
               "top": "top", "bottom": "bottom"}

# The new mesh, box by box, read off `NpcModel.createBodyLayer()`: texOffs and size, base cube
# then second layer. THE single owner of this table — `make_female_skins` and
# `make_npc_textures` both import it, because for one afternoon two copies of the old villager
# table disagreed and a checker reported 126 phantom "invisible pixels" on every file in the mod.
PLAYER_BOXES = {
    "head":        (0, 0, 8, 8, 8),
    "hat":         (32, 0, 8, 8, 8),
    "body":        (16, 16, 8, 12, 4),
    "body_outer":  (16, 32, 8, 12, 4),
    "r_arm":       (40, 16, 4, 12, 4),
    "r_arm_outer": (40, 32, 4, 12, 4),
    "l_arm":       (32, 48, 4, 12, 4),
    "l_arm_outer": (48, 48, 4, 12, 4),
    "r_leg":       (0, 16, 4, 12, 4),
    "r_leg_outer": (0, 32, 4, 12, 4),
    "l_leg":       (16, 48, 4, 12, 4),
    "l_leg_outer": (0, 48, 4, 12, 4),
}


def player_sampled():
    """Every texel any face of any box on the player mesh actually reads.

    Anything opaque outside this set is paint nobody will ever see. Note that it is NOT the
    union of `NEW_REGIONS`: a region is the bounding rectangle of a net and a net has four empty
    corners, so the rectangle over-counts by 128 texels on the head alone.
    """
    used = set()
    for dims in PLAYER_BOXES.values():
        for _, (x, y, w, h) in faces(*dims).items():
            for yy in range(y, y + h):
                for xx in range(x, x + w):
                    used.add((xx, yy))
    return used


# The bounding rectangles of those nets, for the per-region report.
NEW_REGIONS = {
    "head": (0, 0, 32, 16),        "hat": (32, 0, 64, 16),
    "body": (16, 16, 40, 32),      "body_outer": (16, 32, 40, 48),
    "r_arm": (40, 16, 56, 32),     "r_arm_outer": (40, 32, 56, 48),
    "l_arm": (32, 48, 48, 64),     "l_arm_outer": (48, 48, 64, 64),
    "r_leg": (0, 16, 16, 32),      "r_leg_outer": (0, 32, 16, 48),
    "l_leg": (16, 48, 32, 64),     "l_leg_outer": (0, 48, 16, 64),
}

# A skin must fill these or the person has a hole in them. Garments are allowed to be sparse —
# a tunic covers the torso and nothing else — so they are checked separately.
SKIN_MUST_FILL = ["head", "body", "r_arm", "l_arm", "r_leg", "l_leg"]


def fit(tile, w, h, anchor):
    """Crop a face down to the destination size, centred or hung from the top."""
    sw, sh = tile.size
    if sw == w and sh == h:
        return tile
    left = max(0, (sw - w) // 2)
    top = 0 if anchor == "top" else max(0, (sh - h) // 2)
    return tile.crop((left, top, left + min(w, sw), top + min(h, sh)))


def relay(src, is_skin):
    out = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for name, sbox, dbox, anchor, mirror in (SKIN_PARTS if is_skin else GARMENT_PARTS):
        sf = faces(*sbox)
        df = faces(*dbox)
        for face, (dx, dy, dw, dh) in df.items():
            # Read from the mirrored partner when the limb flips, so a left arm shows what the
            # mirrored right arm used to show rather than the same face twice.
            sface = MIRROR_SWAP[face] if mirror else face
            sx, sy, sw, sh = sf[sface]
            tile = src.crop((sx, sy, sx + sw, sy + sh))
            if mirror:
                tile = tile.transpose(Image.FLIP_LEFT_RIGHT)
            tile = fit(tile, dw, dh, anchor)
            out.paste(tile, (dx, dy))
    return out


def report(im, label, is_skin):
    rows = []
    holes = []
    for name, (x0, y0, x1, y1) in NEW_REGIONS.items():
        px = list(im.crop((x0, y0, x1, y1)).getdata())
        op = sum(1 for p in px if p[3] > 8)
        rows.append(f"{name}={op}/{len(px)}")
        if is_skin and name in SKIN_MUST_FILL and op == 0:
            holes.append(f"{name} empty")
        # A garment on a base region z-fights the skin it is drawn over.
        if not is_skin and name in SKIN_MUST_FILL and op != 0:
            holes.append(f"{name} on the base layer ({op}px)")
    print(f"  {label}")
    print("    " + "  ".join(rows))
    return holes


def main():
    check_only = "--check" in sys.argv
    if not check_only:
        os.makedirs(OUT_DIR, exist_ok=True)

    files = sorted(f for f in os.listdir(SRC_DIR) if f.endswith(".png")
                   and not f.startswith(ALREADY_PLAYER_UV))
    if not files:
        print(f"no textures found in {SRC_DIR}")
        return 1

    all_holes = {}
    for f in files:
        is_skin = "skin" in f
        if check_only:
            path = os.path.join(OUT_DIR, f)
            if not os.path.exists(path):
                print(f"  {f}: not relayed yet")
                continue
            im = Image.open(path).convert("RGBA")
        else:
            src = Image.open(os.path.join(SRC_DIR, f)).convert("RGBA")
            im = relay(src, is_skin)
            im.save(os.path.join(OUT_DIR, f))
        holes = report(im, f, is_skin)
        if holes:
            all_holes[f] = holes

    print()
    if all_holes:
        # The whole point of the relay was that the left limbs were empty. If they still are,
        # the relay did not work and saying so beats shipping a one-armed town.
        for f, holes in all_holes.items():
            print(f"FAIL  {f}: {'; '.join(holes)}")
        return 1

    print(f"OK — {len(files)} file(s) relayed to {OUT_DIR}/, no holes in any skin.")
    print("Look at them before copying over the originals: the head lost two rows and the")
    print("faces will want tidying by hand.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
