"""The drawn citizen bodies: people in their underclothes, one hand-authored file each.

    python draw_citizens.py             # write the bodies + the contact sheet
    python draw_citizens.py --dry-run   # draw, measure and gate; write nothing
    python draw_citizens.py --check     # measure and gate what is already on disk

WHAT THIS REPLACES, AND WHY THE CROSS PRODUCT WENT AWAY
-------------------------------------------------------
`make_citizen_skins.py` emitted 48 files as 4 drawn complexions x 6 drawn faces x 2 cuts. It is
retired by this file and its 48 outputs are left on disk, unreferenced, the way the 12 relayed
skins before them were left.

The arithmetic is what retired it. 4 complexions x 6 faces is **24 visibly distinct bodies**, not
48 — the two cuts are the sexes, so a man is never mistakable for a woman and the cut buys no
variety within either. The larger figure quoted for that pipeline multiplied in hair, beard and
headwear, which is real silhouette variation but is contributed by `NpcHeadModels` and would be
contributed to a drawn body just the same. So the cross product bought 24 bodies at the cost of
being unable to draw any of them, and it lost the comparison against the register the owner
actually wants:

    measured over 31 reference skins    vs   the 48 generated bodies
    distinct colours per file    139 median (26..1076)      17 (16..17)
    saturation median                   0.31               0.29
    saturation p90                      0.54               0.47
    % pixels sat > 0.30                  48%                35%
    luminance span                       243                228
    draws a nose (head front row 5)    28 of 31            0 of 48

**The gap is not colour.** Saturation and luminance span were already in band, so the researched
undyed range in `NpcLook.TINTS` was never the problem and no new dye is needed. The gap is
SHADING DENSITY: 17 tones against 139. Three or four flat tones per region is what "generated"
looks like; the references have ramps, seams, wear, dirt and highlights that follow the form.
That is the whole difference, and it is a thing you draw.

The references at `~/Downloads/house.mrs/skins` are a STYLE REFERENCE that was MEASURED and
never copied. Not one pixel, region or palette entry of them is in this file or in its output;
every colour below is either measured off the mod's own `default_skin.png`, taken from the
researched range in `make_female_skins.GOWNS` / `NpcLook.TINTS`, or authored here.

WHY THESE ARE UNDERCLOTHES
--------------------------
`NpcClothesLayer` paints one tunic file per trade over any body, and that layer is what makes a
farmer distinguishable from a smith. Draw finished characters — a knight in mail, a lady in a
green gown — and the tunic over them is a mess, so the layer has to go, and then the drawn pool
has to cover the trades itself: 7 professions x 2 sexes is 14 files before any variety inside a
trade, and the combinatorics come straight back hand-drawn.

So a body is a person in their shift and hose, with the face, hands and feet. The trade's tunic
still goes over it and hair, beard and headwear stay geometry. Twelve to twenty bodies is then
enough, because each multiplies by 7 garments and by the ~100 outlines `NpcHeadModels` supplies.

    THERE IS NO GARMENT-VERSUS-DRAWN-CHARACTER CONFLICT TO RESOLVE. It only existed for finished
    characters. Underclothes are what a garment is drawn to go over, which is why the mask
    coupling below is a gate rather than a problem.

The mask is measured, not assumed. All eight `*_clothes.png` share ONE alpha mask, 252 texels,
and `garment_mask()` reads it off disk. What it leaves open is what a drawn body has to get
right:

    body_outer front  rows 0..5 cols 0-1 and 6-7 covered  -> the V shows cols 2..5
    body_outer sides  rows 0..5 open, 6..11 covered       -> the flank of the shift shows
    body_outer top    cols 0 and 7 only                   -> the shoulders show
    arm_outer         a wedge on rows 0..3 and the top    -> the whole sleeve and arm show
    leg_outer         EMPTY in all eight                  -> the hem and hose are always seen

WHY A NOSE, AFTER A FILE THAT GATED AGAINST ONE
-----------------------------------------------
`make_citizen_skins.py` refused a nose and cited vanilla: row 5 of the head front is flat in 8 of
9 player skins. That is true and it was the wrong corpus — vanilla's nine are deliberately
minimal. Of the 31 references, **28 draw a nose**, and re-measuring says HOW, which is the part
that matters:

    row 5, centre (cols 3-4) against flanks (cols 2,5)
        lit bridge          17 of 31      <- the majority idiom
        flat                 6
        shadowed centre      8

So a nose is a HIGHLIGHT on the bridge with the shadow at its flanks. The old objection — "a 2px
nose shadow over a 2px mouth merges into one dark blob" — was an objection to the minority form,
and it is answered by drawing the majority one: a light bridge cannot merge with a dark mouth.
`verify` now REQUIRES a nose and requires the bridge to be the lighter half.

The head second layer stays empty, and that is not an oversight. The references use `hat` in 31
of 31 because that is where a skin has to put hair and hood. Ours are cubes with concentric
deformations — beard +0.3, hair +0.35, `hat` +0.5, headwear +0.6 (`NpcHeadModels`) — so paint on
`hat` would sit inside the hair and headwear shells and z-fight them.

HOW A CELL IS WRITTEN
---------------------
Every face of every box is ASCII, and every texel is **two characters: a material letter and a
ramp step in hex**. `F0` is the lightest flesh, `Ff` the deepest; `S7` is the middle of the
linen; `..` is transparent. A 16-step ramp is what gets a body into the reference band, and a
one-character legend cannot address one — that is why the old files topped out at 17 tones with
four symbols per material.

The ramps are generated from two to four AUTHORED anchors each (`ramp()`), endpoints exact, so a
complexion is still a handful of numbers a human chose and its span is still gated. What the
extra steps buy is modelling, not colour: `MIN_SPAN`/`MAX_SPAN` are unchanged and measured
between the first and last flesh anchor exactly as before.

WHY THE LEFT SIDE IS NOT DRAWN
------------------------------
`l_arm`, `l_leg` and `l_leg_outer` are stamped as mirrors of the right, because `NpcModel` does
not declare them `.mirror()` — they carry their own texOffs — so the symmetry has to live in the
texture. `verify` compares the two byte for byte; the male set the mod shipped was empty on the
left for a whole revision.
"""

from __future__ import annotations

import argparse
import hashlib
import re
import sys
from pathlib import Path
from typing import Dict, List, Sequence, Tuple

from PIL import Image, ImageDraw

# The mesh table, from its single owner. Nothing here writes a texOffs down.
from npc_uv import (MIRROR_SWAP, NEW_REGIONS, PLAYER_BOXES as BOXES, faces as net,
                    player_sampled as sampled_pixels)

# The contact-sheet apparatus, shared rather than copied: `elevation` composites in the model's
# own order (base, then the outer cubes, then the garment) and is the only view that can tell you
# whether a shift reads as a shift.
from make_female_skins import checker, elevation, head_only, lum, strip

HERE = Path(__file__).resolve().parent
OUT = HERE.parent / (
    "common/src/main/resources/assets/onceuponatown/textures/entity/npc")
SHEET = HERE / "structures/out/npc"

# The only filenames this script may write. Everything else in that directory is the author's
# hand-drawn work, a garment, or a retired generation, and one bad glob destroys the only copy.
WRITEABLE = re.compile(r"^citizen_body_\d\d\.png$")

# What the previous pipeline left behind, for the comparison strip on the sheet. Read only.
RETIRED_GLOB = "citizen_[mw]_c*_f*.png"
GARMENT_FOR_SHEET = "farmer_clothes.png"

# The six regions a body must fill or the person has a hole in them, with the net area of each:
# 6 faces of a w x h x d box come to 2wd + 2dh + 2wh.
BASE_PARTS = {"head": 384, "body": 352, "r_arm": 224, "l_arm": 224,
              "r_leg": 224, "l_leg": 224}

# The garment's own cubes, plus `hat`. A base texel here z-fights the layer drawn over it; on
# `hat` it would sit inside the hair and headwear shells. See the header.
EMPTY_PARTS = ("hat", "body_outer", "r_arm_outer", "l_arm_outer")

# The cut's. Vanilla puts clothing on the legs' outer layer in 4 of its 9 player skins, and all
# eight of our garments leave it alone, so a hem there is always visible.
CUT_PARTS = ("r_leg_outer", "l_leg_outer")

# ── the gates ────────────────────────────────────────────────────────

MIN_SPAN, MAX_SPAN = 26.0, 40.0     # the anti-crush gate on a complexion, unchanged
MIN_FEATURE_CONTRAST = 30.0         # a mouth or an iris against its own cheek

# How far apart two faces are, summed over the head front as |luminance difference| per differing
# texel. NOT a count of differing cells — that was the first version of this gate and it passed
# six faces that came off the contact sheet looking like one.
MIN_FACE_SEPARATION = 80.0

# THE NUMBER THAT DEFINES THE DELIVERABLE. Distinct opaque colours inside the sampled regions.
# The references measure 139 median over 31 files; the floor is set below the band's lower edge so
# it is a floor and not a target, and there is a ceiling because past it the file stops being
# pixel art and starts being a photograph resized — 8 of the 31 references are in that state
# (390..1076 colours) and they are not the register the owner picked.
MIN_DISTINCT, MAX_DISTINCT = 100, 220

# A FEATURE IS CONTRAST, NOT A RAMP STEP, AND THIS GATE IS THE SECOND THING THIS FILE LEARNED.
# The first pass widened every ramp to 16 steps, drew the brows two steps off the cheek and the
# nose one step off its flank, hit 120 distinct colours — and came off the contact sheet with a
# face as flat as the generated one it replaced. Measured against the 31 references and against
# that first pass:
#
#     over the head front             references     first pass here
#     brow vs cheek                     60.7              21.3
#     nose bridge vs its flanks         57.3              11.3
#     face luminance range               197               188
#
# The RANGE was already right; the features were spending 2 of 16 steps on a 29-point ramp, which
# is 4 luminance points, and the file this replaces had already measured 7 points as invisible. So
# a feature is drawn out of the OCCLUSION ramp, which is deep enough to be seen and still a flesh
# tone. The floors are set between what the old set proved visible (23.7) and what the references
# spend, because a reference's brow row is often hair and ours may never be.
MIN_NOSE_CONTRAST = 30.0
MIN_BROW_CONTRAST = 25.0

# Per region, so density cannot be bought entirely on the face. Measured on the references:
# head 41 (theirs includes hair on the cube; ours is a bald scalp), torso 33, arms 28, legs 16,
# leg outer 21. Ours are set below what this file achieves, and `main` prints both.
MIN_REGION_DISTINCT = {"head": 20, "body": 22, "arms": 16, "legs": 16, "leg_outer": 8}


# ── ramps ────────────────────────────────────────────────────────────

def ramp(anchors: Sequence[Tuple[int, int, int]], n: int) -> List[Tuple[int, int, int]]:
    """`n` colours through the authored anchors, both endpoints exact.

    A ramp and not a mix: the steps are ordered, so `S7` is always between `S6` and `S8` and the
    art can lean on that. Two to four anchors is what a person authors; the steps in between are
    interpolation and carry no decisions.

    Refuses to return a ramp with a duplicate, because a collapsed step is a tone the art thinks
    it has and the file does not — which is exactly the failure the distinct-colour gate exists
    to catch, caught earlier and by name.
    """
    if n < 2:
        raise SystemExit("a ramp needs at least two steps")
    segs = len(anchors) - 1
    out: List[Tuple[int, int, int]] = []
    for i in range(n):
        t = i * segs / (n - 1)
        k = min(int(t), segs - 1)
        f = t - k
        a, b = anchors[k], anchors[k + 1]
        out.append(tuple(int(round(a[c] + (b[c] - a[c]) * f)) for c in range(3)))
    if len(set(out)) != n:
        dupes = [c for c in out if out.count(c) > 1]
        raise SystemExit(f"ramp of {n} over {anchors} collapses: {sorted(set(dupes))} repeat — "
                         f"widen the anchors or shorten the ramp")
    return out


# How many steps each material carries. The letters are the legend; a cell is a letter and one
# hex step. 14 materials, 128 steps between them, which is the pool the art draws from.
STEPS = {
    "F": 16,   # flesh, exposed — the face, the forearm, the hand
    "P": 12,   # flesh, covered — the throat and chest under the shift, which never see the sun
    "B": 6,    # a warm accent — the cheek, the ear, a knuckle
    "Q": 8,    # occlusion — the eye socket, the nose's flank, under the jaw, inside the elbow.
    #            A DEEP FLESH and not a black, and it is what draws a feature: see the header on
    #            why the first pass's features were invisible.
    "M": 4,    # mouth
    "O": 3,    # sclera, with a shaded corner
    "I": 4,    # iris
    "S": 16,   # the linen shift
    "G": 8,    # grime on linen — the belly, the cuff, the hem
    "R": 6,    # abraded linen — bleached and thinned where a tunic rubs
    "H": 16,   # wool hose
    "U": 8,    # mud on the hose — the knee and the shin
    "L": 12,   # shoe leather
    "K": 6,    # a cord: the neck lace, a belt, a garter
    "T": 6,    # a seam, stitched. Cooler and darker than the cloth either side of it
}

# Which materials are flesh, for the gates that say "nothing but flesh on the face".
FLESH_MATERIALS = ("F", "P", "B", "Q")

# Which are the shift and its wear, for the garment-mask coupling gate.
SHIFT_MATERIALS = ("S", "G", "R", "T", "K")


# ── the people ───────────────────────────────────────────────────────
#
# THE ROSTER — PROPOSED, TWO OF FOURTEEN DRAWN.
#
# Two are drawn deliberately: the register has to land before it is multiplied. Fourteen is inside
# the 12..20 band a drawn pool needs, and it is 7 per sex so the roll is uniform whichever sex the
# name generator's first coin flip produced. The axes, in the order of how much each changes a
# body — which is also the order to spend authoring effort in:
#
#   1. COMPLEXION FAMILY (4). All four must appear, and the DARK one has to be drawn rather than
#      multiplied down: that is the measurement the whole approach rests on (20 luminance points
#      of flesh modelling when multiplied, 36 when drawn).
#   2. SEX (2). The cut — a shift to mid-thigh over hose, or one to the ankle.
#   3. AGE AND BUILD. Where the fold columns sit and how deep the modelling runs: a heavy man's
#      shift drapes wider, an old face carries more of the occlusion ramp.
#   4. CONDITION. How worn, how dirty, how well made. This is `G`, `R` and `T` doing the work, and
#      it is the cheapest axis to vary without touching the shape.
#
#   men                                                        women
#   00 warm,  prime, weathered — grimy shift, muddy hose  [X]  01 light, slight — bleached linen [X]
#   02 dark,  prime, well kept — better linen, no dirt         03 warm,  prime — coarse, dirty hem
#   04 olive, older — patched shift, a deeper-lined face       05 olive, older — patched skirt
#   06 light, young — a shift too big for him, no shoes        07 dark,  prime — a woven girdle
#   08 warm,  heavy — wider folds, a leather belt              09 warm,  young — short sleeves
#   10 olive, young — a hard tan line at the sleeve            11 light, older — heavier, greyer
#   12 dark,  older — one sleeve mended in another linen       13 dark,  young — clean, barefoot
#
# Two per pass, and each pass looks at the sheet before the next. When the pool is full the
# fallback constant in `CitizenLook` goes away and the 48 generated files can be proposed for
# deletion — not before, and not without asking.
#
# One entry per drawn body. Each is a person: their own complexion, their own cloth, their own
# face, their own wear. `flesh` is anchored on the measured range where there is one —
# `default_skin.png` gives #be886c x562, #b78272 x262, #b37b62 x198, #a36b4d — and authored to
# the same span where there is not. The span between the FIRST and LAST anchor is what
# `MIN_SPAN`/`MAX_SPAN` gate, unchanged from the file this replaces: the dark complexion went
# from 20 points of modelling to 36 by being drawn rather than multiplied down, and that argument
# is now the whole approach.

PEOPLE = [
    dict(
        slug="00", sex="m", who="a weathered man, warm complexion",
        note="broad; his shift grimed at the belly and thin at the shoulder, hose muddy at "
             "the knee, a mended cuff",
        cut="short",          # shift to mid-thigh, sleeve to the elbow
        anchors={
            # measured off default_skin.png, kept exactly as the four anchors
            "F": [(0xbe, 0x88, 0x6c), (0xb7, 0x82, 0x72), (0xb3, 0x7b, 0x62), (0xa3, 0x6b, 0x4d)],
            # covered skin: paler in hue than the tanned face because it never sees the sun, and
            # it has to run DARKER at the far end too or the inner forearm comes out brighter
            # than the outer one and the arm reads inside out
            "P": [(0xd6, 0xa6, 0x8c), (0xc4, 0x8f, 0x76), (0xa8, 0x74, 0x5e), (0x8c, 0x5d, 0x48)],
            "B": [(0xc9, 0x8a, 0x70), (0xb0, 0x6a, 0x54)],
            "Q": [(0x8a, 0x59, 0x40), (0x5e, 0x3a, 0x29)],
            "M": [(0x8a, 0x50, 0x40), (0x63, 0x35, 0x28)],
            "O": [(0xff, 0xff, 0xff), (0xd4, 0xc9, 0xbd)],
            "I": [(0x63, 0x48, 0x35), (0x2e, 0x20, 0x18)],
            # the shift: unbleached linen, the CHEMISE range of make_female_skins widened. The
            # deep end is deeper than that range's shadow on purpose — the first pass ran out of
            # ramp at #6e6552 and the whole figure came off the sheet reading as a bright
            # nightshirt with no folds in it. 115 luminance points against the references' 180 in
            # the torso.
            "S": [(0xc6, 0xbb, 0x9e), (0xb8, 0xad, 0x92), (0x93, 0x89, 0x71), (0x4e, 0x47, 0x39)],
            "G": [(0x8f, 0x81, 0x68), (0x6b, 0x60, 0x50), (0x3f, 0x38, 0x2d)],
            "R": [(0xdc, 0xd6, 0xc4), (0xc4, 0xbd, 0xa8)],
            # hose: the grey-brown moorit of GOWNS[2], which is the wool a working man had
            "H": [(0x9a, 0x90, 0x84), (0x7f, 0x7b, 0x77), (0x62, 0x59, 0x50), (0x35, 0x2f, 0x29)],
            "U": [(0x7a, 0x6a, 0x52), (0x5a, 0x4c, 0x3a), (0x38, 0x2f, 0x24)],
            "L": [(0x5c, 0x48, 0x38), (0x4c, 0x3a, 0x30), (0x3d, 0x2d, 0x29), (0x23, 0x18, 0x14)],
            "K": [(0x7a, 0x64, 0x48), (0x53, 0x42, 0x2d)],
            "T": [(0x6b, 0x62, 0x52), (0x4e, 0x49, 0x3a)],
        },
    ),
    dict(
        slug="01", sex="w", who="a slight woman, light complexion",
        note="her shift to the ankle in better linen, frayed and dirty at the hem where it "
             "drags, a garter at the knee",
        cut="long",           # shift to the ankle, sleeve to the wrist
        anchors={
            "F": [(0xe2, 0xb5, 0x96), (0xd7, 0xa8, 0x87), (0xc9, 0x97, 0x7a), (0xc2, 0x93, 0x77)],
            "P": [(0xec, 0xc6, 0xab), (0xe0, 0xb8, 0x9c), (0xcc, 0x9c, 0x80), (0xb0, 0x7e, 0x64)],
            "B": [(0xdd, 0xa8, 0x8c), (0xc4, 0x81, 0x69)],
            "Q": [(0xa8, 0x73, 0x5a), (0x76, 0x49, 0x36)],
            "M": [(0xa8, 0x60, 0x54), (0x78, 0x3c, 0x34)],
            "O": [(0xff, 0xff, 0xff), (0xdb, 0xd1, 0xc9)],
            # slate, not brown. A DECISION and not a measurement — see the report note. The old
            # ban was on the villager relay's near-green #332411 monobrow-and-iris, not on a
            # light-eyed person, and one iris colour for the whole town is a repaint.
            "I": [(0x7d, 0x8a, 0x8f), (0x35, 0x3f, 0x44)],
            # her linen is bleached: better cloth, which is a fact about her and not about linen
            "S": [(0xde, 0xd8, 0xc6), (0xcf, 0xc9, 0xb4), (0xa6, 0x9f, 0x8b), (0x60, 0x5b, 0x4c)],
            "G": [(0xa8, 0x9a, 0x80), (0x82, 0x75, 0x5f), (0x4c, 0x44, 0x36)],
            "R": [(0xec, 0xe7, 0xd8), (0xd6, 0xd1, 0xc0)],
            # stockings in the undyed cream of GOWNS[0]
            "H": [(0xd8, 0xcf, 0xb4), (0xc3, 0xb8, 0x9a), (0x96, 0x8c, 0x72), (0x5c, 0x54, 0x43)],
            "U": [(0x8a, 0x7a, 0x5e), (0x66, 0x56, 0x40), (0x42, 0x38, 0x29)],
            "L": [(0x6b, 0x54, 0x42), (0x56, 0x41, 0x2f), (0x40, 0x2f, 0x22), (0x2a, 0x1d, 0x16)],
            "K": [(0x8a, 0x73, 0x55), (0x5e, 0x4c, 0x36)],
            "T": [(0x9a, 0x91, 0x7c), (0x76, 0x6e, 0x59)],
        },
    ),
]


def palette(person: dict) -> Dict[str, Tuple[int, int, int]]:
    """The whole legend for one person: every material's ramp, addressed as letter + hex step."""
    table: Dict[str, Tuple[int, int, int]] = {}
    for mat, n in STEPS.items():
        for i, rgb in enumerate(ramp(person["anchors"][mat], n)):
            table[mat + "0123456789abcdef"[i]] = rgb
    return table


# ── the drawing ──────────────────────────────────────────────────────
#
# A cell is a material letter and a hex step: `F0` lightest flesh .. `Ff` deepest. `..` is
# transparent. Light comes from above and from the figure's front-LEFT, which is the viewer's
# right in a front elevation, and it is held to consistently — a highlight that follows the form
# is the difference between modelling and a jittered course. He is deliberately not symmetric.


def cells(row: str) -> List[str]:
    return [row[i:i + 2] for i in range(0, len(row), 2)]


def flipc(rows: List[str]) -> List[str]:
    """A face mirrored left-right, CELL-wise.

    `make_female_skins.flip` reverses the characters of the row, which was right when a texel was
    one character and turns `F5` into `5F` now. Deriving the head's left side from its right is
    what keeps two hand-written blocks from drifting out of symmetry, so this had to be its own
    function rather than the imported one.
    """
    return ["".join(cells(r)[::-1]) for r in rows]


# ── person 00: the man ───────────────────────────────────────────────

# The face. Eyes on row 4, sclera at cols 1 and 6, iris at 2 and 5 — 9 of 9 vanilla and the
# reference mode. Brows in DEEP FLESH and never a hair colour: the relayed set inherited the
# villager's near-black #332411 bar and it is most of why those faces read as a villager. A nose
# on row 5 as a LIT BRIDGE with the shadow at its flanks, which is 17 of 31 references and the
# form that cannot merge with the mouth.
FACE_00 = [
    "FbF9F8F7F7F8FaFc",   # 0  the scalp under the hair cube's fringe, in its shadow
    "F8F4F1F0F0F2F5F9",   # 1  forehead, lit left of centre
    "F9F5F2F1F1F3F6Fa",   # 2
    "FcQ1Q0F5F6Q0Q2Fd",   # 3  the brows, out of the OCCLUSION ramp — 40+ points off the cheek
    "FdO0I1F4F6I2O1Fe",   # 4  the eyes, set in their sockets
    "FdFbFcF0F1Q0FeFf",   # 5  THE NOSE: a lit bridge, its shadow on ONE side. Symmetric flanks
    #                            read as a pair of bags under the eyes, which is what they did
    "FbB5F4M1M2F5B4Fc",   # 6  the mouth, and a cheek warm at the DEEP end of the blush ramp —
    #                            the light end came off the sheet as rouge
    "Q2FdF8F4F5F9FeQ3",   # 7  the chin catching light, the jaw beside it in shadow
]

# The rest of the head cube. FLESH, because the hair is a cube now: the old set was bald by
# accident, this is bald on purpose, and paint under a cube of hair shows as a seam at its edge.
# `left` is `flip(right)` so two hand-written blocks cannot drift apart.
HEAD_RIGHT_00 = [
    "FcFaF8F6F5F4F3F6",   # col 7 is the frontmost edge of a right face, so it takes the light
    "FcFaF7F5F4F2F0F4",
    "FdFbF8F6F5F3F1F4",
    "FdFbF9F7F6F4F2F5",   # the ear begins
    "FeFcFaQ0Q1F5F3F6",   # the ear's own shadow, cols 3-4, and it is a real shadow now
    "FeFdFbQ2FdF6F4F7",
    "FfFeFcFaF8F7F5F8",
    "Q3FfFeFcFaF9F7Fa",   # the jaw
]
HEAD_00 = {
    "front":  FACE_00,
    "top": [
        "FdFbF9F7F7F9FbFd",
        "FbF8F5F3F3F5F8Fb",
        "F9F5F2F1F1F2F5F9",
        "F8F4F1F0F0F1F4F8",
        "F8F4F1F0F0F1F4F8",
        "F9F6F3F1F1F3F6F9",
        "FbF9F7F5F5F7F9Fb",
        "FdFcFaF8F8FaFcFd",
    ],
    "back": [
        "FdFbF9F7F7F9FbFd",
        "FdFaF8F6F6F8FaFd",
        "FeFbF9F7F7F9FbFe",
        "FeFcFaF8F8FaFcFe",
        "FfFdFbF9F9FbFdFf",
        "Q0FeFcFaFaFcFeQ0",
        "Q1FfFdFbFbFdFfQ1",
        "Q3Q1FfFdFdFfQ1Q3",
    ],
    "right":  HEAD_RIGHT_00,
    "left":   flipc(HEAD_RIGHT_00),
    # The underside of the jaw and the neck. Occlusion, which is a deep flesh and not a black.
    "bottom": [
        "Q2Q2Q3Q4Q4Q3Q2Q2",
        "Q2Q3Q4Q5Q5Q4Q3Q2",
        "Q3Q4Q5Q6Q6Q5Q4Q3",
        "Q4Q5Q6Q7Q7Q6Q5Q4",
        "Q4Q5Q6Q7Q7Q6Q5Q4",
        "Q3Q4Q5Q6Q6Q5Q4Q3",
        "Q2Q3Q4Q5Q5Q4Q3Q2",
        "Q2Q2Q3Q4Q4Q3Q2Q2",
    ],
}

# The torso: a linen shift, gathered at a laced neck. What the trade's tunic leaves open is the
# V (front rows 0..5, cols 2..5), both flanks above the waist, and the shoulders' top face — so
# those are where the shift has to read. Rows 6..11 of the front are covered by every garment AND
# fully visible on a citizen with no trade, so they are drawn, not filled in.
#
# The highlight is a COLUMN, because cloth hangs and a fold is vertical. One course per panel
# moves it over; moving it every other course came off the contact sheet as a plaid.
SHIFT_00 = {
    "front": [
        "SdS9S5P5P6S6SaSe",   # 0  the neck facing; the throat is covered skin, not tanned
        "SbS7K4P2P3K5S8Sc",   # 1  an open collar, laced either side of it
        "SaS5S2S0S1S3S7Sb",   # 2
        "SaS4S1S0S2S4S8Sc",   # 3
        "SbS6S2S1S2S5S8Sc",   # 4
        "SbS7S3S1S3S6S9Sd",   # 5  the last course the V shows
        "ScS8S4S2S4S7SaSd",   # 6
        "ScS9S4S3S5S7SaSe",   # 7
        "K3K1K0K2K3K4K4K5",   # 8  a girdle: a shift was belted, and it is his own cord
        "SdS9S5S3G1G0S9Se",   # 9  grime as a PATCH on one side of the belly. A full course of
        "SeSbS7S5S6G2SbSf",   # 10   it read as a painted stripe across him
        "SfScS9S7S8SaSdSf",   # 11 into the hem
    ],
    "back": [
        "SeSbS7S5S6S8SbSf",
        "ScS8S5S3S4S6S9Sd",
        "T1T0S4S2S3S5S8Sc",   # a seam down the shoulder blade
        "SbS6S3S1S2S4S7Sb",
        "SbS7S4S2S3S5S8Sc",
        "ScS8S5S3S4S6S9Sd",
        "ScS9S6S4S5S7SaSd",
        "SdS9S6S5S6S8SaSe",
        "K4K2K1K0K2K3K5K5",
        "SeSbS8S6G2G1SbSf",
        "SeScS9S7S8SaScSf",
        "SfSdSbS9SaScSeSf",
    ],
    # The flank. Rows 0..5 are open on every garment, so this is seen as much as the front is,
    # and the side seam is the thing that says it is a made garment and not a tube.
    "right":  ["SbT0S6Sa", "SaT1S4S9", "S9T2S2S8", "SaT3S3S9",
               "SbT4S4Sa", "ScT5S5Sb", "ScS9S6Sb", "SdS9S6Sc",
               "K3K1K2K4", "SdSaS7Sd", "SeSbS8Se", "SfScSaSf"],
    "left":   ["SaS6T0Sb", "S9S4T1Sa", "S8S2T2S9", "S9S3T3Sa",
               "SaS4T4Sb", "SbS5T5Sc", "SbS6S9Sc", "ScS6S9Sd",
               "K4K2K1K3", "ScS7SaSd", "SdS8SbSe", "SeSaScSf"],
    # The shoulders. Only cols 0 and 7 are covered by a garment, so this is a lit surface — and
    # it is where a tunic rubs, so the linen is thinned to the bleached `R` right across it.
    "top":    ["SdS9S6S4S4S6S9Sd", "R0R2R4S1S1R5R3R1",
               "T1S4S2S0S0S2S4T1", "SbS7S4S2S3S5S8Sc"],
    "bottom": ["SfSeSeSdSdSeSeSf", "SeSeSdScScSdSeSe",
               "SeSdScScScScSdSe", "SfSeSdScScSdSeSf"],
}

# The sleeve to the elbow, then the bare forearm and a hand. Col 3 of the front face is the side
# against the torso, so it is the shadow column; col 0 is the outside, which takes the light.
SLEEVE_00 = {
    "front":  ["S5S1S6Sc", "S5S1S6Sc", "S4S0S5Sb", "S5S1S6Sc",
               "S6S2S7Sd", "G3G1G4G6",             # the cuff, dirty
               "F4F1F5Fc", "F4F1F5Fc", "F5F2F6Fd", "F5F2F6Fd",
               "F7F3F8Fe", "F9F6FaFf"],            # the wrist and the back of the hand
    "back":   ["S6S2S7Sd", "S5S1S6Sc", "S5S1S6Sc", "S4S0S5Sb",
               "S6S2S7Sd", "G4G2G5G7",
               "F5F2F6Fd", "F4F1F5Fc", "F5F2F6Fd", "F6F3F7Fe",
               "F8F4F9Ff", "FaF7FbFf"],
    # The outside of the arm: lit, and the two courses at the shoulder are where a tunic has worn
    # the linen thin — abrasion belongs on the CLOTH, not on the forearm below the cuff.
    "right":  ["R1R0S2S4", "R3R2S0S2", "S6S3S1S3", "S7S4S1S3",
               "S8S5S2S4", "G3G2G1G3",
               "F6F3F1F3", "F6F3F1F3", "F7F4F2F4", "F8F5F3F5",
               "F9F6F4F6", "FbF8F6F8"],
    # The inside of the arm: in the torso's shadow, and the skin here never sees the sun — so it
    # is the pale `P` ramp rather than the tanned `F` one, which is where those steps live.
    "left":   ["ScSaS9Sb", "ScSaS9Sb", "SdSbSaSc", "SdSbSaSc",
               "SeScSbSd", "G6G5G5G7",
               "P4P3P2P4", "P6P5P4P6", "Q0P8P7P9", "P9P8P7P9",
               "PaP9P8Pa", "PbPaP9Pb"],
    "top":    ["S9S5S5S9", "S6S1S1S6", "S6S1S1S6", "SaS6S6Sa"],
    # The palm: pale skin with the warm accent where a hand is worn — the heel and the pads.
    "bottom": ["P9P6P6P9", "P6B4B0P7", "P6B5B3P7", "P9P7P7Pa"],
}

# The hose. Rows 0..3 are under the shift's hem and in its shadow; the knee is rows 5..6 and it
# is muddy, because a knee is what a working man puts on the ground. Then the shoe.
HOSE_00 = {
    "front":  ["HdHaHcHf", "HcH8HaHe", "HaH5H8Hc", "H8H2H5Ha",
               "H6H0H3H8", "H7U0U2H9", "U4U1U3Ha",   # the knee, and the mud is a patch on it
               "H7H2H5H9", "H8H3H6Ha", "HaH5H8Hc",
               "L4L0L2L6", "L9L6L8Lb"],              # the shoe, then the sole edge
    "back":   ["HeHbHdHf", "HdH9HbHe", "HbH6H9Hd", "H9H3H6Hb",
               "H7H1H4H9", "H8H2H5Ha", "U5U2U4Hb",
               "H8H3H6Ha", "H9H4H7Hb", "HbH6H9Hd",
               "L5L1L3L7", "LaL7L9Lb"],
    "right":  ["HcHdHeHf", "HaHbHdHe", "H7H9HbHd", "H4H6H9Hb",
               "H1H3H6H9", "U1U2U4U6", "U2U3U5U7",
               "H5H6H8Ha", "H6H7H9Hb", "H8H9HbHd",
               "L2L3L5L7", "L7L8LaLb"],
    "left":   ["HfHeHfHf", "HeHdHeHf", "HcHbHdHe", "HaH9HbHd",
               "H9H8HaHc", "U6U5U6U7", "U7U6U7U7",
               "H9H8HaHc", "HaH9HbHd", "HcHbHdHe",
               "L6L5L7L8", "LbLaLbLb"],
    "top":    ["HfHeHeHf", "HeHdHdHe", "HeHdHdHe", "HfHeHeHf"],
    "bottom": ["LbLaLaLb", "LaL9L9La", "LaL9L9La", "LbLaLaLb"],   # the sole
}

# The shift's hem, on the leg's outer layer: four courses to mid-thigh, then nothing, so the hose
# and the shoe show below it. `S4S2S1S3` on the right leg mirrors to `S3S1S2S4` on the left, so
# the pair reads as one hanging volume rather than two tubes with a seam between them.
HEM_00 = {
    "front":  ["S8S4S2S6", "S9S5S3S7", "SaS6S4S8", "SbG2G1S9"] + [".." * 4] * 8,
    "back":   ["S9S5S3S7", "SaS6S4S8", "SbS7S5S9", "ScG3G2Sa"] + [".." * 4] * 8,
    "right":  ["S6S3S1S3", "S7S4S2S4", "S8S5S3S5", "G4G3G1G2"] + [".." * 4] * 8,
    "left":   ["ScS9S7S9", "SdSaS8Sa", "SeSbS9Sb", "G6G5G4G5"] + [".." * 4] * 8,
    "top":    ["S7S4S4S7", "S5S1S1S5", "S5S1S1S5", "S8S5S5S8"],
    "bottom": [".." * 4] * 4,                                     # open, so the hose shows
}


# ── person 01: the woman ─────────────────────────────────────────────

FACE_01 = [
    "FaF8F6F5F5F6F8Fb",   # 0
    "F7F3F1F0F0F1F4F8",   # 1  a higher, smoother forehead
    "F8F4F1F0F1F2F5F9",   # 2
    "FbQ0Q1F3F4Q1Q2Fc",   # 3  finer brows, set a course higher than his and one step lighter
    "FcO0I0F3F5I2O1Fd",   # 4  and her eyes are not a matched pair either
    "FcFaQ1F0F1FbFcFd",   # 5  a narrower nose, and its shadow falls on her OTHER side to his
    "FaB4F3M0M1F4B5Fb",   # 6
    "Q1FbF6F3F4F7FdQ2",   # 7  a lighter jaw: the whole lower face is less shadowed than his
]
HEAD_RIGHT_01 = [
    "FbF9F7F6F5F4F2F5",
    "FbF9F6F4F3F1F0F3",
    "FcFaF7F5F4F2F1F4",
    "FcFaF8F6F5F3F2F5",
    "FdFbF9Q0Q1F4F3F6",
    "FdFcFaQ2FcF5F4F7",
    "FeFdFbF9F7F6F5F8",
    "Q2FeFdFbF9F8F6F9",
]
HEAD_01 = {
    "front":  FACE_01,
    "top": [
        "FcFaF8F6F6F8FaFc",
        "FaF7F4F2F2F4F7Fa",
        "F8F4F1F0F0F1F4F8",
        "F7F3F1F0F0F1F3F7",
        "F7F3F1F0F0F1F3F7",
        "F8F5F2F1F1F2F5F8",
        "FaF8F6F4F4F6F8Fa",
        "FcFbF9F7F7F9FbFc",
    ],
    "back": [
        "FcFaF8F6F6F8FaFc",
        "FcF9F7F5F5F7F9Fc",
        "FdFaF8F6F6F8FaFd",
        "FdFbF9F7F7F9FbFd",
        "FeFcFaF8F8FaFcFe",
        "FfFdFbF9F9FbFdFf",
        "Q0FeFcFaFaFcFeQ0",
        "Q2Q0FeFcFcFeQ0Q2",
    ],
    "right":  HEAD_RIGHT_01,
    "left":   flipc(HEAD_RIGHT_01),
    "bottom": [
        "Q1Q1Q2Q3Q3Q2Q1Q1",
        "Q1Q2Q3Q4Q4Q3Q2Q1",
        "Q2Q3Q4Q5Q5Q4Q3Q2",
        "Q3Q4Q5Q6Q6Q5Q4Q3",
        "Q3Q4Q5Q6Q6Q5Q4Q3",
        "Q2Q3Q4Q5Q5Q4Q3Q2",
        "Q1Q2Q3Q4Q4Q3Q2Q1",
        "Q1Q1Q2Q3Q3Q2Q1Q1",
    ],
}

# Her shift: better linen, a small round neck, gathered at the throat with a drawstring. The
# gathers are what make it hers — a column of alternating steps across the chest, which is a
# thing a drawstring does and a straight ramp cannot say.
SHIFT_01 = {
    "front": [
        "ScS8S4P3P4S5S9Sd",   # 0  a round neck; the throat is covered skin
        "SaS6K0S2K5S3S7Sb",   # 1  the drawstring
        "S9S4S1S0S2S1S6Sa",   # 2  gathers: the steps ALTERNATE rather than ramp, which is what a
        "S9S3S2S0S1S2S5Sa",   # 3   drawstring does to cloth and a smooth ramp cannot say
        "SaS4S1S1S2S1S6Sb",   # 4
        "SaS5S2S0S2S3S7Sb",   # 5
        "SbS6S3S1S3S4S8Sc",   # 6
        "SbS7S3S2S3S5S8Sc",   # 7
        "ScS7S4S3S4S5S9Sd",   # 8
        "ScS8S5S4S5S6SaSd",   # 9
        "SdS9S6S5G1G0SbSe",   # 10  her grime is lower down and smaller than his
        "SeSbS8S7S8SaSeSf",   # 11
    ],
    "back": [
        "SdS9S5S4S5S7SaSe",
        "SbS7S4S3S4S6S9Sc",
        "T1T0S3S2S3S5S8Sb",   # her shoulder seam, and it is on the other side to his
        "SaS6S3S2S3S5S8Sb",
        "SaS6S4S3S4S5S9Sc",
        "SbS7S4S3S4S6S9Sc",
        "SbS7S5S4S5S7SaSd",
        "ScS8S5S4S5S7SaSd",
        "ScS9S6S5S6S8SbSe",
        "SdS9S6S5S6S8SbSe",
        "SdSaS7S6G2G1SbSf",
        "SeScS9S8S9SbSdSf",
    ],
    "right":  ["SaT0S5Sc", "S9T1S3Sb", "S9T2S1Sa", "SaT3S2Sb",
               "SaT4S3Sb", "SbT5S3Sc", "SbS8S4Sc", "ScS8S4Sd",
               "ScS9S5Sd", "SdS9S5Se", "SdSaS6Se", "SeSbS7Sf"],
    "left":   ["S9S5T0Sb", "S8S3T1Sa", "S7S2T2S9", "S8S3T3Sa",
               "S9S4T4Sb", "S9S4T5Sb", "SaS5S7Sc", "SaS5S7Sc",
               "SbS6S8Sd", "SbS6S8Sd", "ScS7S9Se", "SdS9SbSf"],
    "top":    ["ScS8S5S3S3S5S8Sc", "R0R2R4S0S0R5R3R1",
               "T1S3S1S0S0S1S3T1", "SaS6S4S2S3S5S8Sc"],
    "bottom": ["SeSdSdScScSdSdSe", "SdSdScSbSbScSdSd",
               "SdScSbSbSbSbScSd", "SeSdScSbSbScSdSe"],
}

# Her sleeve runs to the wrist — nine courses of cloth against his five — so the forearm does
# not show and the hand is all that does. That is the cut, and it is the thing that tells the
# sexes apart at a distance on this rig along with the hem.
SLEEVE_01 = {
    "front":  ["S5S1S6Sc", "S5S1S6Sc", "S4S0S5Sb", "S5S1S6Sc",
               "S5S1S6Sc", "S6S2S7Sd", "S6S2S7Sd", "S7S3S8Se",
               "G3G0G4G6",                            # the cuff
               "F4F1F5Fc", "F5F2F6Fd", "F7F4F8Ff"],
    "back":   ["S6S2S7Sd", "S5S1S6Sc", "S5S1S6Sc", "S4S0S5Sb",
               "S5S1S6Sc", "S6S2S7Sd", "S7S3S8Se", "S7S3S8Se",
               "G4G1G5G7",
               "F5F2F6Fd", "F6F3F7Fe", "F8F5F9Ff"],
    "right":  ["R1R0S2S4", "R3R2S0S2", "S6S3S1S3", "S7S4S1S3",
               "S7S4S1S3", "S8S5S2S4", "S8S5S2S4", "S9S6S3S5",
               "G3G2G1G3",
               "F6F3F1F3", "F7F4F2F4", "F9F6F4F6"],
    "left":   ["ScSaS9Sb", "ScSaS9Sb", "SdSbSaSc", "SdSbSaSc",
               "SeScSbSd", "SeScSbSd", "SfSdScSe", "SfSdScSe",
               "G6G5G5G7",
               "P4P3P2P4", "P7P6P5P7", "PbPaP9Pb"],   # the inside of the wrist, never tanned
    "top":    ["S8S4S4S8", "S5S1S1S5", "S5S1S1S5", "S9S5S5S9"],
    "bottom": ["P8P5P5P8", "P5B1B4P6", "P5B5B0P6", "P8P6P6P9"],
}

# Her stockings, gartered at the knee with a cord. The hem drags, so the mud is at the ANKLE
# rather than the knee — the opposite of his, and it is the difference between someone who
# kneels to work and someone whose skirt sweeps the yard.
HOSE_01 = {
    "front":  ["HdHaHcHf", "HcH9HbHe", "HbH7H9Hd", "HaH6H8Hc",
               "H9H5H7Hb", "K2K1K3K4",              # the garter
               "H8H4H6Ha", "H8H4H6Ha", "H9H5H7Hb",
               "U3U0U2U5",                          # the ankle, where the hem drags
               "L4L0L2L6", "L9L6L8Lb"],
    "back":   ["HeHbHdHf", "HdHaHcHe", "HcH8HaHd", "HbH7H9Hc",
               "HaH6H8Hb", "K3K2K4K4",
               "H9H5H7Ha", "H9H5H7Ha", "HaH6H8Hb",
               "U4U1U3U6",
               "L5L1L3L7", "LaL7L9Lb"],
    "right":  ["HbHcHdHf", "H9HaHcHe", "H7H8HaHc", "H5H6H8Ha",
               "H3H4H6H9", "K1K0K2K3",
               "H4H5H7H9", "H5H6H8Ha", "H7H8HaHc",
               "U1U2U3U5",
               "L2L3L5L7", "L7L8LaLb"],
    "left":   ["HfHeHfHf", "HeHdHeHf", "HcHbHdHe", "HaH9HbHd",
               "H9H8HaHc", "K4K3K5K5",
               "H8H7H9Hb", "H9H8HaHc", "HaH9HbHd",
               "U5U4U6U7",
               "L6L5L7L8", "LbLaLbLb"],
    "top":    ["HfHeHeHf", "HeHdHdHe", "HeHdHdHe", "HfHeHeHf"],
    "bottom": ["LbLaLaLb", "LaL9L9La", "LaL9L9La", "LbLaLaLb"],
}

# Her hem: ten courses to the ankle, so the shoe shows and nothing else. The last two courses are
# where a dragged hem goes — frayed to the bleached `R` on one course and dirty on the next.
HEM_01 = {
    "front":  ["S7S3S1S5", "S8S4S2S6", "S8S4S3S7", "S9S5S3S7",
               "S9S5S4S8", "SaS6S4S8", "SaS6S5S9", "SbS7S5S9",
               "R1R0R2R3", "ScG2G1Sa"] + [".." * 4] * 2,
    "back":   ["S8S4S2S6", "S9S5S3S7", "S9S5S4S8", "SaS6S4S8",
               "SaS6S5S9", "SbS7S5S9", "SbS7S6Sa", "ScS8S6Sa",
               "R2R1R3R4", "SdG3G2Sb"] + [".." * 4] * 2,
    # A gore seam down the outside of the skirt: what turns four straight panels into a garment.
    "right":  ["T0S3S1S3", "T1S4S2S4", "T2S4S2S4", "T3S5S3S5",
               "T4S5S3S5", "T5S6S4S6", "S8S6S4S6", "S9S7S5S7",
               "R0R1R4R5", "G4G3G1G2"] + [".." * 4] * 2,
    "left":   ["SbS8S6S8", "ScS9S7S9", "ScS9S7S9", "SdSaS8Sa",
               "SdSaS8Sa", "SeSbS9Sb", "SeSbS9Sb", "SfScSaSc",
               "R5R4R3R2", "G6G5G4G5"] + [".." * 4] * 2,
    "top":    ["S6S3S3S6", "S3S1S1S3", "S3S1S1S3", "S7S4S4S7"],
    "bottom": [".." * 4] * 4,
}


# Which blocks make each person.
ART = {
    "00": dict(head=HEAD_00, body=SHIFT_00, arm=SLEEVE_00, leg=HOSE_00,
               leg_outer=HEM_00, hem_rows=4, face=FACE_00),
    "01": dict(head=HEAD_01, body=SHIFT_01, arm=SLEEVE_01, leg=HOSE_01,
               leg_outer=HEM_01, hem_rows=10, face=FACE_01),
}


def blank() -> List[List[str | None]]:
    return [[None] * 64 for _ in range(64)]


def stamp(sym, box: str, art: Dict[str, List[str]], mirror: bool = False) -> None:
    """Write one box's six ASCII faces into the symbolic canvas.

    Mirroring swaps the two side faces and reverses each row, which is what puts the symmetry
    into the TEXTURE — `NpcModel` does not declare its left limbs `.mirror()`, they carry their
    own texOffs, and the male set the mod shipped was empty on the left for a whole revision
    because the old mesh mirrored its right limbs instead.
    """
    f = net(*BOXES[box])
    for face, (x0, y0, w, h) in f.items():
        rows = art[MIRROR_SWAP[face] if mirror else face]
        grid = [cells(r) for r in rows]
        if len(grid) != h or any(len(r) != w for r in grid):
            raise SystemExit(
                f"{box}.{face}: art is {len(grid)}x{len(grid[0]) if grid else 0} cells, "
                f"the net wants {h}x{w} — a cell is two characters")
        for cy, row in enumerate(grid):
            for cx in range(w):
                ch = row[w - 1 - cx] if mirror else row[cx]
                if ch != "..":
                    sym[y0 + cy][x0 + cx] = ch


def draw(slug: str) -> List[List[str | None]]:
    """One person, symbolically. The palette is applied afterwards."""
    a = ART[slug]
    sym = blank()
    stamp(sym, "head", a["head"])
    # `hat` deliberately untouched: hair, headwear and the beard are concentric cubes over it.
    stamp(sym, "body", a["body"])
    stamp(sym, "r_arm", a["arm"])
    stamp(sym, "l_arm", a["arm"], mirror=True)
    stamp(sym, "r_leg", a["leg"])
    stamp(sym, "l_leg", a["leg"], mirror=True)
    stamp(sym, "r_leg_outer", a["leg_outer"])
    stamp(sym, "l_leg_outer", a["leg_outer"], mirror=True)
    return sym


def materialise(sym, person: dict) -> Image.Image:
    table = palette(person)
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = im.load()
    for y in range(64):
        for x in range(64):
            ch = sym[y][x]
            if ch is None:
                continue
            if ch not in table:
                raise SystemExit(f"legend has no colour for '{ch}' at ({x},{y}) — "
                                 f"material '{ch[0]}' has {STEPS.get(ch[0], 0)} steps")
            px[x, y] = table[ch] + (255,)
    return im


# ── verification ─────────────────────────────────────────────────────

def garment_mask() -> set:
    """Which (box, face, cx, cy) cells every profession garment paints.

    Read off the shipped files rather than written down, because it is the coupling this whole
    design rests on: what the mask leaves open is what a drawn body has to get right, and the
    numbers in the header are this function's output.
    """
    p = OUT / GARMENT_FOR_SHEET
    im = Image.open(p).convert("RGBA")
    px = im.load()
    out = set()
    for box in BOXES:
        for face, (x, y, w, h) in net(*BOXES[box]).items():
            for cy in range(h):
                for cx in range(w):
                    if px[x + cx, y + cy][3] > 8:
                        out.add((box, face, cx, cy))
    return out


def region_counts(im: Image.Image) -> Dict[str, int]:
    px = im.load()
    return {name: sum(1 for y in range(y0, y1) for x in range(x0, x1) if px[x, y][3] > 8)
            for name, (x0, y0, x1, y1) in NEW_REGIONS.items()}


def distinct(im: Image.Image, boxes=None) -> int:
    """Distinct opaque colours inside the sampled regions. THE deliverable's number."""
    px = im.load()
    cols = set()
    for box in (boxes or BOXES):
        for face, (x, y, w, h) in net(*BOXES[box]).items():
            for cy in range(h):
                for cx in range(w):
                    if px[x + cx, y + cy][3] > 8:
                        cols.add(px[x + cx, y + cy][:3])
    return len(cols)


REGION_BOXES = {"head": ["head"], "body": ["body"], "arms": ["r_arm", "l_arm"],
                "legs": ["r_leg", "l_leg"], "leg_outer": ["r_leg_outer", "l_leg_outer"]}


def face_grid(im: Image.Image) -> List[List[Tuple[int, int, int]]]:
    px = im.load()
    hx, hy, _, _ = net(*BOXES["head"])["front"]
    return [[px[hx + cx, hy + cy][:3] for cx in range(8)] for cy in range(8)]


def nose_contrast(im: Image.Image) -> float:
    """The bridge (cols 3-4 of row 5) against its flanks (cols 2 and 5), in luminance.

    Positive means the bridge is the LIGHTER half, which is the majority reference form and the
    one that cannot merge with the mouth below it. Measured on the references: median 57.
    """
    g = face_grid(im)
    return ((lum(g[5][3]) + lum(g[5][4])) / 2) - ((lum(g[5][2]) + lum(g[5][5])) / 2)


def brow_contrast(im: Image.Image) -> float:
    """The strongest of the four brow cells against the cheek. References: median 61."""
    g = face_grid(im)
    return max(abs(lum(g[3][cx]) - lum(g[6][2])) for cx in (1, 2, 5, 6))


def span(person: dict) -> float:
    """The complexion's modelling span, between the first and last authored flesh anchor.

    Unchanged from the file this replaces on purpose. The 16 interpolated steps buy smoothness,
    not range, so the gate that caught a crushed palette still measures the same quantity.
    """
    a = person["anchors"]["F"]
    return abs(lum(a[0]) - lum(a[-1]))


def face_distances() -> Tuple[float, List[Tuple[str, str, int, float]]]:
    """How far apart the drawn faces are — in LUMINANCE, not in cells.

    The second version of this measurement. The first counted differing cells, scored two faces
    8 apart, and passed a set the contact sheet showed as one face repeated: the brows had been
    drawn one ramp step from the cheek, and a cell is free while contrast is what the eye is
    given. Measured on each person's OWN palette, since a drawn body no longer shares one.
    """
    pairs = []
    for i in range(len(PEOPLE)):
        for j in range(i + 1, len(PEOPLE)):
            pi, pj = PEOPLE[i], PEOPLE[j]
            ti, tj = palette(pi), palette(pj)
            gi = [cells(r) for r in ART[pi["slug"]]["face"]]
            gj = [cells(r) for r in ART[pj["slug"]]["face"]]
            cellcount = weighted = 0
            for cy in range(8):
                for cx in range(8):
                    a, b = gi[cy][cx], gj[cy][cx]
                    if ti[a] != tj[b]:
                        cellcount += 1
                        weighted += abs(lum(ti[a]) - lum(tj[b]))
            pairs.append((pi["slug"], pj["slug"], cellcount, weighted))
    if not pairs:
        return float("inf"), []
    return min(p[3] for p in pairs), sorted(pairs, key=lambda p: p[3])


def verify(im: Image.Image, person: dict, mask: set) -> List[str]:
    """Every claim this file makes about its own output, counted.

    Each of these has gone wrong once, here or in one of the two files this replaces:

      1. a base region empty or unfilled            — a hole in a person is not a style choice
      2. paint on `hat` or a garment's outer cube    — z-fights the shell drawn over it
      3. no hem on the leg outer                     — the cuts stop differing
      4. the eyes off row 4, or off cols 1,2,5,6     — 9 of 9 vanilla and the reference mode
      5. NO NOSE, or a nose whose bridge is the dark half — 28 of 31 references draw one, and 17
         of those draw it as a lit bridge, which is the form that cannot merge with the mouth
      6. a mouth that is not 2px at cols 3-4 on row 6 or 7
      7. a non-flesh tone anywhere on the face       — the villager monobrow, by name
      8. anything but flesh on the head's other five faces — the hair is a cube; paint under it
         is a seam at the cap's edge
      9. a feature with no contrast against its own cheek
     10. the two sides not mirrored                  — the shipped male set was empty on the left
     11. a pixel outside every sampled region        — paint nobody will ever see
     12. THE GARMENT COUPLING: something that is not the shift or skin in the cells the trade's
         tunic leaves open. That is what "one person in layers" means as a check
     13. too few distinct colours                    — the number that retired the last pipeline
    """
    bad: List[str] = []
    counts = region_counts(im)
    px = im.load()
    table = palette(person)
    slug = person["slug"]
    art = ART[slug]

    inv = {}
    for k, v in table.items():
        inv.setdefault(v, set()).add(k[0])

    def materials_at(x, y):
        return inv.get(px[x, y][:3], set())

    for part, area in BASE_PARTS.items():
        got = counts[part]
        if got == 0:
            bad.append(f"{part} EMPTY — the mesh samples it")
        elif got != area:
            bad.append(f"{part} {got}/{area} — {area - got} unpainted texels")

    for part in EMPTY_PARTS:
        if counts[part]:
            why = ("hair, headwear and the beard are concentric cubes over it"
                   if part == "hat" else "z-fights the garment layer")
            bad.append(f"{part} has {counts[part]}px — {why}")

    for part in CUT_PARTS:
        if counts[part] == 0:
            bad.append(f"{part} empty — no hem, so the cuts do not differ")

    # The cut, in courses of the leg's front face. A hem that creeps is a gown on a man.
    lx, ly, lw, lh = net(*BOXES["r_leg_outer"])["front"]
    painted = [cy for cy in range(lh)
               if any(px[lx + cx, ly + cy][3] > 8 for cx in range(lw))]
    want = art["hem_rows"]
    if painted != list(range(want)):
        bad.append(f"hem covers courses {painted}, this cut wants 0..{want - 1}")

    # The face, read off the SHIPPED pixels and not off the ASCII, so the palette cannot quietly
    # break a rule the art kept.
    hx, hy, _, _ = net(*BOXES["head"])["front"]

    def at(cx, cy):
        return px[hx + cx, hy + cy][:3]

    for cx in (1, 6):
        if "O" not in materials_at(hx + cx, hy + 4):
            bad.append(f"col {cx} of row 4 is not sclera — 9 of 9 vanilla put it there")
    for cx in (2, 5):
        if "I" not in materials_at(hx + cx, hy + 4):
            bad.append(f"col {cx} of row 4 is not an iris — 9 of 9 vanilla put it there")

    # THE NOSE, and the gate is inverted from the file this replaces. That file refused a nose on
    # vanilla's nine; 28 of 31 references draw one, so the corpus was wrong. It is required, and
    # it is required in the majority form — a LIT BRIDGE — because the old objection (a 2px nose
    # shadow over a 2px mouth is one dark blob) is only true of the minority form.
    row5 = [at(cx, 5) for cx in range(2, 6)]
    if len(set(row5)) == 1:
        bad.append("row 5 cols 2..5 is flat — no nose, and 28 of 31 references draw one")
    nose = nose_contrast(im)
    if nose < MIN_NOSE_CONTRAST:
        bad.append(f"the nose is only {nose:+.0f} luminance points off its flanks (floor "
                   f"{MIN_NOSE_CONTRAST:.0f}, references 57) — a nose drawn two ramp steps deep "
                   f"is a nose nobody sees, which is what the first pass here shipped")

    # A BROW IS CONTRAST TOO, and it has to come out of the occlusion ramp for the same reason.
    brow = brow_contrast(im)
    if brow < MIN_BROW_CONTRAST:
        bad.append(f"the brows are only {brow:.0f} luminance points off the cheek (floor "
                   f"{MIN_BROW_CONTRAST:.0f}, references 61) — eight cells of a tone seven points "
                   f"from the cheek is what read as one face six times")

    mouth = [(cx, cy) for cy in range(8) for cx in range(8)
             if "M" in materials_at(hx + cx, hy + cy)]
    if sorted(mouth) not in ([(3, 6), (4, 6)], [(3, 7), (4, 7)]):
        bad.append(f"mouth is {sorted(mouth)}, wants 2px at cols 3-4 on row 6 or 7")

    # THE MONOBROW GATE. Every texel of the face that is not an eye or the mouth has to be in the
    # flesh family. The relayed set put #332411 — a hair colour — across row 4.
    allowed = set(FLESH_MATERIALS) | {"M", "O", "I"}
    for cy in range(8):
        for cx in range(8):
            mats = materials_at(hx + cx, hy + cy)
            if mats and not (mats & allowed):
                bad.append(f"face ({cx},{cy}) is material {sorted(mats)} — a brow is a darker "
                           f"flesh, never a hair colour")
                break

    # The head cube is bald ON PURPOSE. Anything outside the flesh family on the other five faces
    # would be hair painted under a cube of hair, which shows as a seam at the cap's edge.
    for face in ("top", "back", "right", "left", "bottom"):
        fx, fy, fw, fh = net(*BOXES["head"])[face]
        for cy in range(fh):
            for cx in range(fw):
                mats = materials_at(fx + cx, fy + cy)
                if mats and not (mats & set(FLESH_MATERIALS)):
                    bad.append(f"head.{face} ({cx},{cy}) is {sorted(mats)} — the hair is a cube, "
                               f"this is the scalp")
                    break

    # The cheek reference is (2,6) — beside the mouth, and unambiguously cheek. The file this
    # replaces sampled (3,5), which is now the lit nose bridge, so every feature would have been
    # measured against a highlight.
    cheek = lum(at(2, 6))
    for what, key in (("iris", "I"), ("mouth", "M")):
        tones = [lum(v) for k, v in table.items() if k[0] == key]
        near = min(abs(t - cheek) for t in tones)
        if near < MIN_FEATURE_CONTRAST:
            bad.append(f"{what} only {near:.0f} from the cheek — invisible")

    # THE GARMENT COUPLING. What the trade's sleeveless V leaves open has to be the shift or the
    # skin under it, never the hose or the shoe. This is the check behind "linen at the neck, the
    # trade's tunic over the torso, their own cloth where the garment does not reach".
    open_torso = [(face, cx, cy)
                  for face, (x, y, w, h) in net(*BOXES["body"]).items()
                  for cy in range(h) for cx in range(w)
                  if ("body_outer", face, cx, cy) not in mask]
    ok = set(SHIFT_MATERIALS) | set(FLESH_MATERIALS)
    for face, cx, cy in open_torso:
        x, y, _, _ = net(*BOXES["body"])[face]
        mats = materials_at(x + cx, y + cy)
        if mats and not (mats & ok):
            bad.append(f"body.{face} ({cx},{cy}) shows {sorted(mats)} through the garment's V — "
                       f"it has to be the shift or the skin under it")
            break

    used = sampled_pixels()
    stray = sum(1 for y in range(64) for x in range(64)
                if px[x, y][3] > 8 and (x, y) not in used)
    if stray:
        bad.append(f"{stray}px outside every sampled region — invisible paint")

    for right, left in (("r_arm", "l_arm"), ("r_leg", "l_leg"),
                        ("r_leg_outer", "l_leg_outer")):
        rf, lf = net(*BOXES[right]), net(*BOXES[left])
        for face in rf:
            rx, ry, w, h = rf[face]
            sx, sy, _, _ = lf[MIRROR_SWAP[face]]
            a = im.crop((rx, ry, rx + w, ry + h)).transpose(Image.FLIP_LEFT_RIGHT)
            b = im.crop((sx, sy, sx + w, sy + h))
            if a.tobytes() != b.tobytes():
                bad.append(f"{left}.{MIRROR_SWAP[face]} is not the mirror of {right}.{face}")

    n = distinct(im)
    if n < MIN_DISTINCT:
        bad.append(f"{n} distinct colours, floor {MIN_DISTINCT} — the references measure 139 "
                   f"median and 17 is what the generated set managed. This is THE number")
    if n > MAX_DISTINCT:
        bad.append(f"{n} distinct colours, ceiling {MAX_DISTINCT} — past this it stops being "
                   f"pixel art")
    for region, floor in MIN_REGION_DISTINCT.items():
        got = distinct(im, REGION_BOXES[region])
        if got < floor:
            bad.append(f"{region} has {got} distinct colours, floor {floor} — density cannot all "
                       f"be bought on one region")
    return bad


# ── contact sheet ────────────────────────────────────────────────────

def retired() -> List[Tuple[str, Image.Image]]:
    """A sample of the 48 generated bodies, for the comparison the owner is judging."""
    out = []
    for p in sorted(OUT.glob(RETIRED_GLOB))[::9]:
        out.append((p.stem.replace("citizen_", ""), Image.open(p).convert("RGBA")))
    return out[:5]


def crowd(bodies: Dict[str, Image.Image]) -> List[Tuple[str, Image.Image]]:
    """A village: the drawn bodies against the generated ones, in a row.

    **This is the comparison, and it is deliberately not a fair one on variety.** The drawn pool
    is two people at the moment and the generated set is 24, so a row of ten cannot be about how
    many faces a town has. It is about the REGISTER: whether a drawn body reads as drawn beside a
    generated one at the same size, with the same garments over it.

    It also cannot show hair, beard or headwear. Those are cubes in `NpcHeadModels` and only the
    game draws them, so every figure here is bareheaded and this is the pessimistic view.
    """
    garments = sorted(p.name for p in OUT.glob("*_clothes.png")
                      if p.name != "builder_clothes.png")
    out = []
    for i, person in enumerate(PEOPLE):
        for g in [None, garments[i % len(garments)], garments[(i + 3) % len(garments)]]:
            worn = Image.open(OUT / g).convert("RGBA") if g else None
            label = f"DRAWN {person['slug']}\n{(g or 'no trade').replace('_clothes.png', '')}"
            out.append((label, elevation(bodies[person["slug"]], "front", worn)))
    for i, (name, tex) in enumerate(retired()):
        g = garments[i % len(garments)]
        out.append((f"generated\n{name}\n{g.replace('_clothes.png', '')}",
                    elevation(tex, "front", Image.open(OUT / g).convert("RGBA"))))
    return out


def contact_sheet(bodies: Dict[str, Image.Image]) -> Image.Image:
    garment = Image.open(OUT / GARMENT_FOR_SHEET).convert("RGBA")
    drawn = [(f"{p['slug']}\n{p['who']}", bodies[p["slug"]]) for p in PEOPLE]
    heads = drawn + [(n, t) for n, t in retired()[:3]]

    W = (64 * 5 + 6) * 8 + 6
    plan = [
        ("THE NET — 64x64 on the player UV, one hand-drawn file per person. The empty squares "
         "are meant to be empty: body and arm outer belong to the trade's garment, and `hat` is "
         "empty because hair, beard and headwear are concentric cubes over it.",
         [(n, checker(t)) for n, t in drawn], 5),
        ("THE HEAD at 26x — the drawn faces first, then three of the generated bodies for "
         "comparison. Eyes row 4, sclera cols 1 and 6, iris 2 and 5, mouth 2px on row 6. A NOSE, "
         "drawn as a lit bridge with its shadow at the flanks: 28 of 31 references draw one and "
         "17 of those light the bridge. No monobrow, no hair colour anywhere on the face.",
         head_only(heads), 26),
        ("FRONT, no trade — the shift and the hose, which is what a citizen with no profession "
         "wears. Man: shift to mid-thigh over hose. Woman: shift to the ankle.",
         [(n, elevation(t, "front")) for n, t in drawn], 9),
        (f"FRONT + {GARMENT_FOR_SHEET} — the trade's tunic over the top. What shows of the body "
         "is the V at the chest, both flanks above the waist, the shoulders, the whole sleeve, "
         "and everything below the hem. Measured off the mask, not assumed.",
         [(n, elevation(t, "front", garment)) for n, t in drawn], 9),
        ("BACK — the shoulder seam, and the cut from behind.",
         [(n, elevation(t, "back", garment)) for n, t in drawn], 9),
        ("RIGHT PROFILE — the hem line, and where the sleeve ends.",
         [(n, elevation(t, "right", garment)) for n, t in drawn], 9),
        ("A CROWD — the drawn bodies against the generated ones they replace. Judge the REGISTER "
         "here, not the variety: the drawn pool is two people so far. THE HAIR, BEARD AND "
         "HEADWEAR ARE MISSING from every figure — they are cubes in `NpcHeadModels` and only "
         "the game draws them, so this is the pessimistic view of a village.",
         crowd(bodies), 8),
    ]
    strips = [strip(title, items, scale, W) for title, items, scale in plan]
    width = max(s.size[0] for s in strips)
    im = Image.new("RGBA", (width, sum(s.size[1] for s in strips) + 26), (12, 12, 14, 255))
    d = ImageDraw.Draw(im)
    counts = ", ".join(f"{p['slug']}={distinct(bodies[p['slug']])}" for p in PEOPLE)
    d.text((6, 6), f"BURG — the drawn citizen bodies, people in their underclothes. "
                   f"{len(PEOPLE)} of a proposed roster of 14. Distinct colours: {counts} — the "
                   f"references measure 139 median, the 48 generated bodies managed 17.",
           fill=(255, 255, 255, 255))
    y = 24
    for s in strips:
        im.paste(s, (0, y))
        y += s.size[1]
    return im


# ── driver ───────────────────────────────────────────────────────────

def body_name(slug: str) -> str:
    return f"citizen_body_{slug}.png"


def snapshot() -> Dict[str, str]:
    """sha256 of every PNG in the texture directory that is not ours to write.

    The 48 generated bodies, the 12 relayed skins, `default_skin.png` and the nine garments all
    live here. Ours are excluded — a second run is meant to replace them — and everything else
    has to come out byte-identical, which is how this script proves it retired the old pipeline
    without deleting a single file of it.
    """
    return {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sorted(OUT.glob("*.png")) if not WRITEABLE.match(p.name)}


def report(bad: Dict[str, List[str]]) -> int:
    print()
    if bad:
        for f, faults in bad.items():
            for fault in faults:
                print(f"FAIL  {f}: {fault}")
        return 1
    print("OK — every base region filled, nothing on `hat` or a garment cube, the hem right for "
          "each cut, eyes and mouth on the corpus rows, A NOSE with a lit bridge, no non-flesh "
          "tone on the face, a bald scalp for the hair cube, the shift under the garment's V, no "
          "invisible paint, both sides mirrored, and every body inside the reference colour band.")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true", help="draw, measure and gate; write nothing")
    ap.add_argument("--check", action="store_true", help="measure what is already on disk")
    args = ap.parse_args()

    mask = garment_mask()
    print(f"  the garment mask, read off {GARMENT_FOR_SHEET}: {len(mask)} texels. "
          f"leg_outer cells in it: "
          f"{sum(1 for b, f, x, y in mask if b in ('r_leg_outer', 'l_leg_outer'))}")

    print("\n  the people, and the gates on them:")
    faults: Dict[str, List[str]] = {}
    for p in PEOPLE:
        s = span(p)
        flag = "" if MIN_SPAN <= s <= MAX_SPAN else "   <-- OUT OF RANGE"
        print(f"    {p['slug']}  {p['who']:34} flesh span {s:4.0f}{flag}")
        if not MIN_SPAN <= s <= MAX_SPAN:
            faults[p["slug"]] = [
                f"complexion span {s:.0f} outside {MIN_SPAN:.0f}..{MAX_SPAN:.0f} — a crushed "
                f"palette is what drawing them separately was for"]

    worst, pairs = face_distances()
    if pairs:
        print(f"    face separation, weakest first — luminance-weighted, NOT a cell count "
              f"(gate {MIN_FACE_SEPARATION:.0f}):")
        for a, b, c, w in pairs[:4]:
            print(f"      {a} / {b}  {c:3} cells  {w:6.0f} luminance")
        if worst < MIN_FACE_SEPARATION:
            a, b, c, w = pairs[0]
            faults["faces"] = [f"{a} and {b} are {c} cells but only {w:.0f} luminance points "
                               f"apart — they will read alike, which is what a cell count missed"]

    bodies: Dict[str, Image.Image] = {}
    print()
    for p in PEOPLE:
        name = body_name(p["slug"])
        if args.check:
            path = OUT / name
            if not path.exists():
                faults[name] = ["not written yet"]
                continue
            tex = Image.open(path).convert("RGBA")
        else:
            tex = materialise(draw(p["slug"]), p)
        bodies[p["slug"]] = tex
        n = distinct(tex)
        per = "  ".join(f"{r}={distinct(tex, REGION_BOXES[r])}" for r in REGION_BOXES)
        print(f"  {name}  {n:4} distinct colours   {per}")
        print(f"    nose bridge vs flanks {nose_contrast(tex):+5.0f} (refs 57, floor "
              f"{MIN_NOSE_CONTRAST:.0f})   brow vs cheek {brow_contrast(tex):5.0f} "
              f"(refs 61, floor {MIN_BROW_CONTRAST:.0f})")
        bad = verify(tex, p, mask)
        if bad:
            faults[name] = bad

    ret = sorted(OUT.glob(RETIRED_GLOB))
    if ret:
        sample = [distinct(Image.open(q).convert("RGBA")) for q in ret[::8]]
        print(f"\n  for comparison, the 48 generated bodies this retires: "
              f"{min(sample)}..{max(sample)} distinct colours "
              f"(sampled {len(sample)}). They stay on disk, unreferenced.")

    if args.check:
        return report(faults)
    if faults or args.dry_run:
        if args.dry_run and not faults:
            print("\ndry run: nothing written.")
        return report(faults)

    before = snapshot()
    OUT.mkdir(parents=True, exist_ok=True)
    SHEET.mkdir(parents=True, exist_ok=True)
    for slug, tex in bodies.items():
        name = body_name(slug)
        if not WRITEABLE.match(name):
            raise SystemExit(f"refusing to write {name}")
        tex.save(OUT / name)
    after = snapshot()
    changed = [n for n, h in before.items() if after.get(n) != h]
    if changed:
        raise SystemExit("DESTROYED EXISTING ART: " + ", ".join(changed))
    print(f"  wrote {len(bodies)} file(s); {len(before)} pre-existing PNG(s) byte-identical "
          f"after the write — including all 48 of the generated bodies this retires")

    contact_sheet(bodies).save(SHEET / "drawn_bodies.png")
    print(f"\nCONTACT SHEET -> {SHEET / 'drawn_bodies.png'}")
    print("LOOK AT IT. The crowd row is the register comparison and no count can make it for you.")
    return report(faults)


if __name__ == "__main__":
    sys.exit(main())
