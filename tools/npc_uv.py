"""The player mesh's UV, and nothing else. THE single owner of the region table.

Moved here out of `remap_npc_uv.py`, mechanically and with no change of behaviour, because that
module's original job — relaying the villager net onto the player net — is finished and retired
(see the note at the top of it) while this table is now imported by every NPC texture tool in
`tools/`. A retired script is a bad place to keep a live rule.

Why one owner and not a copy per tool: for one afternoon two copies of the OLD villager table
disagreed and `make_npc_textures --check` reported 126 phantom "invisible pixels" on every
garment in the mod and 304 on every skin, `default_skin.png` included. It is the same failure
`solids.py` was created to end on the geometry side.

Read off `NpcModel.createBodyLayer()`. If that method changes, this file changes with it and
every tool follows; nothing else in `tools/` may write these numbers down.
"""


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


# The mesh, box by box, read off `NpcModel.createBodyLayer()`: texOffs and size, base cube then
# second layer.
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

# The bounding rectangles of those nets, for per-region reports. NOT the same thing as the
# sampled set below: a rectangle over-counts, because a net has four empty corners — 128 texels
# of them on the head alone.
NEW_REGIONS = {
    "head": (0, 0, 32, 16),        "hat": (32, 0, 64, 16),
    "body": (16, 16, 40, 32),      "body_outer": (16, 32, 40, 48),
    "r_arm": (40, 16, 56, 32),     "r_arm_outer": (40, 32, 56, 48),
    "l_arm": (32, 48, 48, 64),     "l_arm_outer": (48, 48, 64, 64),
    "r_leg": (0, 16, 16, 32),      "r_leg_outer": (0, 32, 16, 48),
    "l_leg": (16, 48, 32, 64),     "l_leg_outer": (0, 48, 16, 64),
}

# A mirror of a box about X: every face flips horizontally and the two side faces swap.
#
# `left_arm` and `left_leg` are NOT declared `.mirror()` in `NpcModel` — they carry their own
# texOffs, (32,48) and (16,48) — so the symmetry has to be put into the TEXTURE, and this is the
# transform that does it. The retired mesh mirrored its right limbs instead of owning left ones,
# which is why every skin the mod shipped had an empty left arm and left leg for a whole
# revision.
MIRROR_SWAP = {"right": "left", "left": "right", "front": "front",
               "back": "back", "top": "top", "bottom": "bottom"}


def player_sampled():
    """Every texel any face of any box on the player mesh actually reads.

    Anything opaque outside this set is paint nobody will ever see.
    """
    used = set()
    for dims in PLAYER_BOXES.values():
        for _, (x, y, w, h) in faces(*dims).items():
            for yy in range(y, y + h):
                for xx in range(x, x + w):
                    used.add((xx, yy))
    return used
