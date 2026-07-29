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
import colorsys
import hashlib
import itertools
import math
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
WRITEABLE = re.compile(r"^(citizen_body_\d\d|citizen_trim"
                       r"|citizen_(?:hair|beard|headwear)_\d\d)\.png$")

# What the previous pipeline left behind, for the comparison strip on the sheet. Read only.
RETIRED_GLOB = "citizen_[mw]_c*_f*.png"
GARMENT_FOR_SHEET = "farmer_clothes.png"

# THE WEALTH LADDER LIVES IN JAVA AND IS READ FROM THERE, never copied. `NpcLook` is the only
# owner of those sixteen numbers; this file parses them out of the source so the contact sheet
# cannot show a stratification the game does not render. It is the same rule as `npc_uv.py` for
# the mesh and `solids.py` for the shape model, and it exists because for one afternoon two
# copies of the villager UV table disagreed and every garment in the mod reported phantom faults.
JAVA_NPC_LOOK = HERE.parent / (
    "common/src/main/java/org/dawnoftime/onceuponatown/client/NpcLook.java")

# The braid. ONE file for all eight garments, which is only possible because they share one alpha
# mask — measured, 252 texels, identical across all eight.
TRIM_NAME = "citizen_trim.png"

# The garments a CITIZEN can actually be given. `Citizen.CLOTHES` maps six professions onto these
# four; `chief`, `builder`, `soldier` and `soldier_veteran` are role garments nobody rolls into,
# so gating the wealth ladder against them would be gating against cloth no citizen wears.
ROLLABLE_GARMENTS = ("farmer_clothes.png", "mason_clothes.png",
                     "smith_clothes.png", "forester_clothes.png")

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

# AND THE SAME QUANTITY PER CELL, which is the gate a roster of fourteen needs and a roster of two
# did not. The sum above is total luminance over all differing cells, so two faces that differ
# everywhere by a hair clear 80 easily: 64 cells at 1.25 points each. Per cell the number this
# repo has already measured is 7 — the threshold below which a luminance step is invisible, proved
# twice, once by `make_citizen_skins` and once by the first drawn pass here spending four. Two
# people whose flesh ramps are offset by ten points score about 640 in total and 10 per cell, so
# the two gates only disagree on a roster this size, and it is the per-cell one that bites.
MIN_FACE_SEPARATION_PER_CELL = 7.0

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

# HAND-DRAWN IS A CLAIM, SO IT IS MEASURED. Two bodies that share their ASCII and differ only in
# palette are the failure the whole file exists to end — 48 generated bodies were exactly that —
# and a colour count cannot see it, because a repaint of a dense drawing is still dense. So every
# pair of people is compared SYMBOLICALLY, palette ignored: the share of sampled cells whose
# material-and-step differs. A repaint measures 0%. The two bodies drawn first measure 72.1%, and
# the floor is set at half of that, because a later person may legitimately share a cut with an
# earlier one — the hose diverge least of any region at 47% even between a man and a woman.
MIN_SYMBOLIC_DIVERGENCE = 0.35


# ── the wealth ladder's own gates ────────────────────────────────────
#
# All three floors come from numbers this repo already has, and none is invented here.
#
# ADJACENT RUNGS. The four tints `NpcLook` shipped were shipped as "so two farmers are not twins",
# so the CLOSEST pair among them is a separation the owner has already accepted as visible. It is
# `0xB0A498` against `0xC08A63` on `forester_clothes`, mean 15.5 over the garment's own pixels in
# RGB distance. Every step of the wealth ladder must clear it — and the ladder's own tightest step
# turns out to BE that pair, one rung apart, so the floor and the narrowest rung are one
# measurement rather than two.
MIN_RUNG_APART = 15.5

# ANY TWO TINTS AT ALL. Seven luminance points is this repo's measured invisibility threshold —
# `make_citizen_skins` measured it and the first drawn pass here re-proved it by spending four. On
# the grey diagonal a 7-point luminance step is an RGB distance of 7*sqrt(3) = 12.1, so this is
# that same threshold expressed in the metric that also sees hue.
MIN_TINT_APART = 12.0

# A TINTED GARMENT MAY NOT GO DARKER THAN THE DARKEST ONE THE MOD SHIPS. `soldier_veteran_clothes`
# has median luminance 38.2; less the 7-point invisibility threshold, 31.2 is the point below which
# a garment is darker than anything already accepted. A multiply can only darken, so this is the
# gate that stops "faded" and "richly dyed" both arriving as a black smear — which this repo has
# shipped once already, and it was read as a bug in the garment code rather than a palette.
MIN_TINTED_LUMINANCE = 31.2

# The braid is TRIM, not a repaint: it may edge the garment and may not become it. Measured on the
# shared mask, the garment is mostly edge — the chest is two narrow straps — so the share is high
# by construction and the ceiling is what stops a second garment being drawn by accident.
MIN_TRIM_SHARE, MAX_TRIM_SHARE = 0.15, 0.45

# The braid is drawn in near-white greys for the same reason `npc_hair.png` is: a multiply cannot
# lighten, so a braid drawn dark can never be gold. Floor on its darkest tone.
MIN_BRAID_TONE = 0x70

# HOW FAR THE BRAID HAS TO BE FROM WHAT IT LIES ON, in RGB distance between the two mean colours.
# Set from the contact sheet, between what it rejected and what it accepted: cream measured 9.4
# from the drawn shift and silver 32.1 — both read as a WHITE BIB rather than a braid — pewter
# measured 44.6 and read as a grey band, and gold reads as braid against every body on the roster,
# closest at 52.6 against 04, whose linen is a mid unbleached and whose skin is olive.
#
# THE NUMBER CANNOT SEE THE REAL DIFFERENCE, so the rule is recorded as well as the floor: every
# candidate that failed was a NEUTRAL GREY beside neutral linen, and every one that passed was a
# warm metal or a saturated silk. A braid has to differ in hue and not only in weight, which is the
# same finding as the wealth rungs' — the tightest pair among the tints the mod already ships is
# 4.2 luminance points apart and 0.21 saturation apart.
MIN_BRAID_APART = 48.0


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
# THE ROSTER — ALL FOURTEEN DRAWN.
#
# Fourteen is inside the 12..20 band a drawn pool needs, and it is 7 per sex so the roll is uniform
# whichever sex the name generator's first coin flip produced. The axes, in the order of how much
# each changes a body — which is also the order the authoring effort went in:
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
#   00 warm,  prime, weathered — grimy shift, muddy hose       01 light, slight — bleached linen
#   02 dark,  prime, well kept — better linen, no dirt         03 warm,  prime — coarse, dirty hem
#   04 olive, older — patched shift, a lined face              05 olive, older — patched skirt
#   06 light, young — a shift too big for him, no shoes        07 dark,  prime — a woven girdle
#   08 warm,  heavy — two fold columns, a leather belt         09 warm,  young — cap sleeves
#   10 olive, young — a hard tan line at the sleeve            11 light, older — heavier, greyer
#   12 dark,  older — both cuffs mended in another linen       13 dark,  young — clean, barefoot
#
# THE HEM IS A DIFFERENT LENGTH ON EVERY ONE OF THEM — men 3,4,5,6,7 courses and women 8,9,10,11 —
# because the hem and the sleeve are the only two things on this rig that read at twenty blocks.
#
# COMPLEXION IS A LADDER, NOT FOURTEEN CHOICES. The per-cell face-separation gate is this repo's
# 7-luminance-point invisibility threshold and there are 109 points of plausible flesh luminance to
# fit fourteen people into, so they are laid out evenly up it — 98, 107, 116, 125, 134, 142, 149,
# 157, 165, 173, 182, 191, 199, 206 at the first anchor — rather than picked one at a time. Three
# separate pairs failed that gate when they were picked independently, and one of them failed even
# at sixteen points apart because its ART was the other's shifted two steps in the opposite
# direction: a palette offset and an art offset cancel, and the result is the same face twice.
#
# The 48 generated files stay on disk, unreferenced, and can now be PROPOSED for deletion — not
# done, and not without asking.
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
    dict(
        slug="02", sex="m", who="a kept man, dark complexion",
        note="the dark complexion DRAWN and not multiplied down — 36 luminance points of flesh "
             "modelling against the 20 the old multiply kept. Better linen, a stitched neck "
             "facing, no dirt on him anywhere, and a tunic a course longer than 00's",
        cut="short",
        anchors={
            "F": [(0x8d, 0x61, 0x45), (0x7f, 0x56, 0x3d), (0x6f, 0x49, 0x34), (0x61, 0x3f, 0x2d)],
            "P": [(0x9d, 0x71, 0x53), (0x8b, 0x61, 0x47), (0x71, 0x4b, 0x36), (0x53, 0x34, 0x25)],
            "B": [(0x95, 0x5f, 0x47), (0x77, 0x45, 0x33)],
            "Q": [(0x51, 0x32, 0x23), (0x32, 0x1d, 0x14)],
            "M": [(0x55, 0x2b, 0x23), (0x39, 0x19, 0x15)],
            "O": [(0xff, 0xff, 0xff), (0xd0, 0xc4, 0xb8)],
            "I": [(0x49, 0x32, 0x23), (0x21, 0x15, 0x0f)],
            # bleached linen, and it is a fact about him rather than about linen: he is the one
            # who can afford to have it whitened, and there is no `G` on him at all
            "S": [(0xe4, 0xdd, 0xcc), (0xd2, 0xcb, 0xb8), (0xa8, 0xa1, 0x8e), (0x62, 0x5c, 0x4e)],
            "G": [(0xb4, 0xa8, 0x92), (0x8e, 0x84, 0x70), (0x5a, 0x53, 0x45)],
            "R": [(0xf0, 0xec, 0xdd), (0xdc, 0xd6, 0xc6)],
            # hose in the grey fleece of GOWNS[3] — a better wool than 00's moorit
            "H": [(0x94, 0x8f, 0x89), (0x7f, 0x7b, 0x77), (0x5c, 0x59, 0x55), (0x33, 0x31, 0x2f)],
            "U": [(0x74, 0x66, 0x50), (0x56, 0x4a, 0x38), (0x36, 0x2e, 0x23)],
            "L": [(0x58, 0x42, 0x32), (0x46, 0x33, 0x28), (0x34, 0x25, 0x1e), (0x1c, 0x13, 0x0f)],
            "K": [(0x86, 0x6e, 0x50), (0x5c, 0x4a, 0x32)],
            "T": [(0x8a, 0x84, 0x72), (0x66, 0x60, 0x50)],
        },
    ),
    dict(
        slug="03", sex="w", who="a working woman, warm complexion",
        note="coarse unbleached linen, a shift she works in: the hem filthy where it drags "
             "through a yard, one sleeve pushed up, mud to the shin",
        cut="long",
        anchors={
            # WARM, like 00, and deliberately lifted clear of him: the two warm bodies were 334
            # luminance points apart on the face-separation measure against 2000+ for every other
            # pair, because a family name is not a palette. Her whole ramp sits 15 points above
            # his, so the two overlap only at their ends.
            "F": [(0xd5, 0xa1, 0x81), (0xc7, 0x93, 0x75), (0xb9, 0x85, 0x67), (0xad, 0x7b, 0x5d)],
            "P": [(0xe5, 0xb9, 0x9b), (0xd5, 0xa7, 0x8b), (0xb9, 0x87, 0x6b), (0x97, 0x67, 0x4f)],
            "B": [(0xd9, 0x99, 0x7f), (0xbd, 0x73, 0x5b)],
            "Q": [(0x9f, 0x67, 0x4b), (0x6f, 0x45, 0x2f)],
            "M": [(0x9b, 0x53, 0x47), (0x6d, 0x33, 0x2b)],
            "O": [(0xff, 0xff, 0xff), (0xdc, 0xd2, 0xc6)],
            "I": [(0x59, 0x3f, 0x2b), (0x27, 0x1b, 0x13)],
            # coarse linen: the deep end is deeper than 00's and the light end is not bleached
            "S": [(0xc0, 0xb2, 0x94), (0xae, 0xa0, 0x84), (0x88, 0x7c, 0x64), (0x48, 0x41, 0x34)],
            "G": [(0x86, 0x76, 0x5c), (0x62, 0x56, 0x44), (0x38, 0x31, 0x26)],
            "R": [(0xd6, 0xcc, 0xb6), (0xbe, 0xb4, 0x9e)],
            "H": [(0x9c, 0x82, 0x64), (0x86, 0x6c, 0x52), (0x64, 0x4e, 0x3a), (0x3a, 0x2c, 0x20)],
            "U": [(0x70, 0x5e, 0x46), (0x52, 0x44, 0x32), (0x32, 0x2a, 0x1e)],
            "L": [(0x5e, 0x48, 0x36), (0x4a, 0x38, 0x2a), (0x38, 0x28, 0x1e), (0x20, 0x16, 0x10)],
            "K": [(0x92, 0x76, 0x54), (0x64, 0x50, 0x36)],
            "T": [(0x82, 0x78, 0x62), (0x5e, 0x56, 0x44)],
        },
    ),
    dict(
        slug="04", sex="m", who="an old man, olive complexion",
        note="a shift PATCHED in another linen, seamed round the patch, a rope at the waist and "
             "a short worn tunic. His face carries more of the occlusion ramp than anyone's",
        cut="short",
        anchors={
            "F": [(0x92, 0x7a, 0x56), (0x88, 0x6e, 0x4c), (0x7a, 0x60, 0x40), (0x6e, 0x56, 0x38)],
            "P": [(0xa6, 0x90, 0x6c), (0x96, 0x7c, 0x5a), (0x7c, 0x60, 0x44), (0x5e, 0x46, 0x2e)],
            "B": [(0x98, 0x74, 0x54), (0x7c, 0x54, 0x3a)],
            # AGE IS THE OCCLUSION RAMP. His runs deeper and wider than anyone's, because a lined
            # face is shadow in more places, and lines are the one thing a 16-step ramp can draw.
            "Q": [(0x64, 0x4c, 0x32), (0x34, 0x22, 0x12)],
            "M": [(0x68, 0x44, 0x34), (0x40, 0x24, 0x1a)],
            "O": [(0xf4, 0xf0, 0xe4), (0xc6, 0xbe, 0xac)],   # an old man's eye is not white
            "I": [(0x54, 0x4c, 0x34), (0x20, 0x1c, 0x10)],
            "S": [(0xb8, 0xae, 0x94), (0xa6, 0x9c, 0x82), (0x80, 0x77, 0x60), (0x44, 0x3e, 0x32)],
            "G": [(0x84, 0x78, 0x60), (0x60, 0x56, 0x46), (0x36, 0x30, 0x26)],
            # the PATCH is another linen entirely, and it is lighter than his own — a scrap off a
            # newer garment is the only cloth a household has spare. Toned down from a first
            # version at 0xE0D8C2, which read off the sheet as a white label stitched to his belly
            # rather than as cloth: a patch is a different linen, not a different material.
            "R": [(0xcc, 0xc4, 0xae), (0xb0, 0xa8, 0x90)],
            "H": [(0x8e, 0x86, 0x72), (0x78, 0x70, 0x60), (0x58, 0x52, 0x46), (0x30, 0x2c, 0x25)],
            "U": [(0x6c, 0x60, 0x4a), (0x50, 0x46, 0x34), (0x30, 0x2a, 0x20)],
            "L": [(0x50, 0x40, 0x30), (0x40, 0x31, 0x26), (0x30, 0x24, 0x1c), (0x1a, 0x13, 0x0e)],
            "K": [(0x9c, 0x84, 0x5c), (0x6c, 0x5a, 0x3c)],   # a rope, not a woven cord
            "T": [(0x74, 0x6c, 0x58), (0x50, 0x4a, 0x3c)],
        },
    ),
    dict(
        slug="05", sex="w", who="an old woman, olive complexion",
        note="a skirt patched at the knee where it wore through, a cloth wrapped twice at the "
             "waist, sleeves to the wrist. Lighter than 04 on purpose — a family is not a palette",
        cut="long",
        anchors={
            "F": [(0xb4, 0x9a, 0x74), (0xa8, 0x8c, 0x68), (0x9c, 0x7e, 0x5c), (0x92, 0x76, 0x52)],
            "P": [(0xc6, 0xb0, 0x8c), (0xb6, 0x9c, 0x78), (0x9a, 0x7e, 0x5e), (0x7c, 0x60, 0x46)],
            "B": [(0xbc, 0x96, 0x74), (0xa0, 0x74, 0x56)],
            "Q": [(0x82, 0x68, 0x4a), (0x4e, 0x3a, 0x26)],
            "M": [(0x88, 0x62, 0x52), (0x5a, 0x3a, 0x2e)],
            "O": [(0xf6, 0xf2, 0xe8), (0xcc, 0xc4, 0xb4)],
            "I": [(0x62, 0x58, 0x40), (0x2a, 0x24, 0x18)],
            "S": [(0xcc, 0xc2, 0xa8), (0xba, 0xb0, 0x96), (0x92, 0x89, 0x72), (0x50, 0x4a, 0x3c)],
            "G": [(0x92, 0x86, 0x6c), (0x6c, 0x62, 0x50), (0x40, 0x3a, 0x2e)],
            "R": [(0xe8, 0xe2, 0xd0), (0xce, 0xc6, 0xb2)],
            "H": [(0xa4, 0x9c, 0x8a), (0x8c, 0x84, 0x76), (0x66, 0x60, 0x54), (0x38, 0x34, 0x2c)],
            "U": [(0x76, 0x6a, 0x54), (0x58, 0x4e, 0x3c), (0x36, 0x30, 0x24)],
            "L": [(0x56, 0x44, 0x36), (0x45, 0x35, 0x2a), (0x34, 0x27, 0x1f), (0x1e, 0x16, 0x11)],
            "K": [(0xa8, 0x98, 0x76), (0x76, 0x6a, 0x52)],   # a cloth band, not a rope
            "T": [(0x7c, 0x74, 0x60), (0x56, 0x50, 0x42)],
        },
    ),
    dict(
        slug="06", sex="m", who="a young man, light complexion",
        note="a handed-down shift two sizes too big: the sleeve swallows his hands, the neck "
             "gapes, the hem hangs to his knee. And no shoes at all — he is the youngest here",
        cut="short",
        anchors={
            "F": [(0xee, 0xc6, 0xa8), (0xe4, 0xba, 0x9c), (0xd8, 0xae, 0x92), (0xd0, 0xa4, 0x88)],
            "P": [(0xf6, 0xd8, 0xc0), (0xec, 0xca, 0xb0), (0xd8, 0xb0, 0x96), (0xba, 0x90, 0x76)],
            "B": [(0xf0, 0xb4, 0x9c), (0xd8, 0x90, 0x78)],
            # A YOUNG FACE IS THE SAME RAMPS WITH LESS OF THE DEEP END USED, not a paler palette.
            # His `Q` exists and is drawn — a socket and a nose flank are shadow at any age — but
            # it never leaves the eye and nose, where 04's runs across the forehead and the jaw.
            "Q": [(0xb8, 0x86, 0x68), (0x86, 0x5c, 0x44)],
            "M": [(0xc0, 0x74, 0x66), (0x8c, 0x48, 0x40)],
            "O": [(0xff, 0xff, 0xff), (0xe0, 0xd8, 0xd0)],
            # hazel-green, and the third iris colour on the roster. One iris colour for a whole
            # town is a repaint, which is the argument that put slate in 01.
            "I": [(0x6e, 0x82, 0x6a), (0x30, 0x3c, 0x2e)],
            "S": [(0xd0, 0xc8, 0xb0), (0xbe, 0xb6, 0x9e), (0x96, 0x8e, 0x78), (0x52, 0x4c, 0x3e)],
            "G": [(0x8c, 0x80, 0x66), (0x68, 0x5e, 0x4a), (0x3c, 0x36, 0x2a)],
            "R": [(0xe6, 0xe0, 0xd0), (0xcc, 0xc6, 0xb4)],
            "H": [(0xa0, 0x96, 0x84), (0x88, 0x80, 0x70), (0x62, 0x5c, 0x50), (0x36, 0x32, 0x2c)],
            "U": [(0x72, 0x64, 0x4c), (0x54, 0x48, 0x36), (0x34, 0x2c, 0x22)],
            # AND HIS LEATHER RAMP IS NEVER USED, because he has no shoes. It stays declared
            # because `palette` builds every material's ramp and an absent one is a crash rather
            # than a bare foot.
            "L": [(0x54, 0x40, 0x32), (0x44, 0x33, 0x28), (0x33, 0x26, 0x1e), (0x1c, 0x14, 0x10)],
            "K": [(0x8e, 0x76, 0x54), (0x62, 0x4e, 0x36)],
            "T": [(0x86, 0x7e, 0x68), (0x60, 0x5a, 0x48)],
        },
    ),
    dict(
        slug="07", sex="w", who="a woman in her prime, dark complexion",
        note="A WOVEN GIRDLE — a tablet-woven band in madder, two courses of it, alternating. The "
             "one dyed thing anyone on this roster owns, and it is a band and not a garment",
        cut="long",
        anchors={
            "F": [(0x98, 0x6a, 0x4c), (0x8c, 0x5e, 0x42), (0x7c, 0x52, 0x38), (0x6c, 0x46, 0x30)],
            "P": [(0xaa, 0x7e, 0x5e), (0x98, 0x6c, 0x4e), (0x7e, 0x54, 0x3c), (0x5e, 0x3a, 0x28)],
            "B": [(0xa0, 0x66, 0x4c), (0x82, 0x4a, 0x34)],
            "Q": [(0x58, 0x36, 0x24), (0x34, 0x1c, 0x10)],
            "M": [(0x5c, 0x2c, 0x26), (0x3a, 0x16, 0x12)],
            "O": [(0xff, 0xff, 0xff), (0xd8, 0xcc, 0xc0)],
            "I": [(0x44, 0x2c, 0x1c), (0x1a, 0x0e, 0x08)],
            "S": [(0xd4, 0xc8, 0xa8), (0xc2, 0xb6, 0x98), (0x9a, 0x90, 0x76), (0x54, 0x4e, 0x3e)],
            "G": [(0x8a, 0x7c, 0x62), (0x66, 0x5a, 0x48), (0x3a, 0x34, 0x28)],
            "R": [(0xe8, 0xe2, 0xd2), (0xd0, 0xca, 0xb8)],
            "H": [(0xa8, 0x92, 0x74), (0x90, 0x7a, 0x60), (0x6a, 0x58, 0x44), (0x3c, 0x30, 0x24)],
            "U": [(0x74, 0x62, 0x4a), (0x56, 0x48, 0x36), (0x34, 0x2c, 0x20)],
            "L": [(0x60, 0x4a, 0x38), (0x4c, 0x3a, 0x2c), (0x3a, 0x2a, 0x20), (0x22, 0x18, 0x12)],
            # THE GIRDLE, and it is the only DYED thread on the roster: madder, which is the one
            # dye NpcLook's researched range says an ordinary household could afford. A band is
            # what that buys — a whole garment in madder is a rung of the wealth ladder above her.
            "K": [(0xb4, 0x62, 0x4a), (0x6e, 0x34, 0x28)],
            "T": [(0x88, 0x7e, 0x66), (0x62, 0x5a, 0x48)],
        },
    ),
    dict(
        slug="08", sex="m", who="a heavy man, warm complexion",
        note="more cloth, so TWO fold columns instead of one, and a LEATHER BELT rather than a "
             "cord — the only leather waist on the roster, and it says he can pay for leather. "
             "The ruddiest complexion here, because the luminance metric cannot see hue and four "
             "warm bodies have to differ by something",
        cut="short",
        anchors={
            "F": [(0xd6, 0x96, 0x74), (0xca, 0x8a, 0x68), (0xbc, 0x7e, 0x5c), (0xb2, 0x74, 0x52)],
            "P": [(0xea, 0xb6, 0x94), (0xd6, 0xa0, 0x7e), (0xba, 0x80, 0x60), (0x98, 0x60, 0x46)],
            "B": [(0xde, 0x94, 0x76), (0xc0, 0x6c, 0x52)],
            "Q": [(0x98, 0x5e, 0x42), (0x66, 0x3a, 0x26)],
            "M": [(0x9a, 0x52, 0x44), (0x6a, 0x2e, 0x26)],
            "O": [(0xff, 0xff, 0xff), (0xd6, 0xc8, 0xbc)],
            "I": [(0x56, 0x38, 0x24), (0x22, 0x14, 0x0c)],
            "S": [(0xcc, 0xc0, 0xa2), (0xba, 0xae, 0x90), (0x92, 0x88, 0x6e), (0x4e, 0x48, 0x3a)],
            "G": [(0x8a, 0x7c, 0x62), (0x66, 0x5a, 0x48), (0x3a, 0x34, 0x28)],
            "R": [(0xe2, 0xdc, 0xca), (0xca, 0xc4, 0xb0)],
            "H": [(0x9e, 0x92, 0x80), (0x86, 0x7c, 0x6c), (0x60, 0x58, 0x4c), (0x34, 0x30, 0x2a)],
            "U": [(0x76, 0x66, 0x4e), (0x56, 0x4a, 0x38), (0x34, 0x2c, 0x22)],
            # THE BELT IS THIS RAMP, and it is why his `L` runs lighter at the top than anyone's:
            # a belt is dressed leather with a sheen, a shoe is not.
            "L": [(0x7c, 0x5a, 0x3a), (0x60, 0x44, 0x2c), (0x44, 0x2f, 0x20), (0x24, 0x18, 0x11)],
            "K": [(0x8e, 0x74, 0x52), (0x60, 0x4c, 0x34)],
            "T": [(0x84, 0x7a, 0x62), (0x5e, 0x56, 0x44)],
        },
    ),
    dict(
        slug="09", sex="w", who="a young woman, warm complexion",
        note="CAP SLEEVES — three courses and then bare arm to the wrist, the shortest sleeve on "
             "the roster and the thing that tells her from 01, 03, 05 and 07 at any distance. "
             "Golden-warm rather than pink, so she and 03 differ in hue as well as in weight",
        cut="long",
        anchors={
            "F": [(0xeb, 0xbf, 0x8f), (0xe1, 0xb3, 0x83), (0xd3, 0xa5, 0x77), (0xc7, 0x99, 0x6b)],
            "P": [(0xf9, 0xd7, 0xaf), (0xed, 0xc7, 0x9f), (0xd7, 0xad, 0x83), (0xb7, 0x8d, 0x67)],
            "B": [(0xf3, 0xb7, 0x95), (0xd7, 0x8f, 0x6f)],
            "Q": [(0xb3, 0x87, 0x5b), (0x7f, 0x5d, 0x3f)],
            "M": [(0xbf, 0x77, 0x63), (0x8d, 0x4f, 0x41)],
            "O": [(0xff, 0xff, 0xff), (0xde, 0xd6, 0xc8)],
            "I": [(0x57, 0x75, 0x6d), (0x2b, 0x3b, 0x37)],   # a fourth iris: green-grey
            "S": [(0xdc, 0xd4, 0xbc), (0xca, 0xc2, 0xaa), (0xa0, 0x99, 0x84), (0x58, 0x53, 0x44)],
            "G": [(0x94, 0x86, 0x6a), (0x6e, 0x63, 0x4e), (0x40, 0x39, 0x2c)],
            "R": [(0xee, 0xe8, 0xd6), (0xd4, 0xce, 0xbc)],
            "H": [(0xc8, 0xbe, 0xa4), (0xb0, 0xa6, 0x8c), (0x84, 0x7c, 0x66), (0x4a, 0x45, 0x39)],
            "U": [(0x7e, 0x70, 0x56), (0x5c, 0x50, 0x3c), (0x38, 0x30, 0x24)],
            "L": [(0x62, 0x4c, 0x3a), (0x4e, 0x3b, 0x2c), (0x3a, 0x2b, 0x20), (0x22, 0x18, 0x12)],
            "K": [(0x9a, 0x82, 0x5e), (0x68, 0x56, 0x3c)],
            "T": [(0x8c, 0x84, 0x6c), (0x66, 0x5f, 0x4c)],
        },
    ),
    dict(
        slug="10", sex="m", who="a young man, olive complexion",
        note="A HARD TAN LINE at the sleeve: the forearm is the tanned `F` ramp and the upper arm "
             "the pale `P` one, meeting in one course with no blend. It is the only place on the "
             "roster the two flesh ramps are put next to each other on purpose",
        cut="short",
        anchors={
            "F": [(0xa3, 0x8b, 0x65), (0x99, 0x7f, 0x5b), (0x8d, 0x73, 0x4f), (0x83, 0x69, 0x47)],
            # AND HIS PALE RAMP RUNS HIGH, because that is what a tan line IS — the arm above the
            # cuff has not seen the sun and is lighter than his face by more than a ramp step.
            "P": [(0xd1, 0xbb, 0x95), (0xc1, 0xa9, 0x83), (0xa5, 0x8b, 0x67), (0x83, 0x6b, 0x4d)],
            "B": [(0xb3, 0x8d, 0x67), (0x95, 0x6b, 0x4b)],
            "Q": [(0x6b, 0x55, 0x37), (0x41, 0x31, 0x1f)],
            "M": [(0x73, 0x4b, 0x3d), (0x4b, 0x2b, 0x23)],
            "O": [(0xff, 0xff, 0xff), (0xdc, 0xd4, 0xc4)],
            "I": [(0x4d, 0x45, 0x2f), (0x21, 0x1d, 0x13)],
            "S": [(0xc4, 0xba, 0x9c), (0xb2, 0xa8, 0x8c), (0x8a, 0x82, 0x6a), (0x4a, 0x44, 0x38)],
            "G": [(0x82, 0x76, 0x5c), (0x60, 0x56, 0x44), (0x36, 0x30, 0x26)],
            "R": [(0xdc, 0xd6, 0xc2), (0xc2, 0xbc, 0xa8)],
            "H": [(0x96, 0x8c, 0x76), (0x7e, 0x76, 0x62), (0x5a, 0x54, 0x46), (0x30, 0x2c, 0x25)],
            "U": [(0x6e, 0x62, 0x4a), (0x50, 0x46, 0x34), (0x30, 0x2a, 0x20)],
            "L": [(0x56, 0x42, 0x32), (0x44, 0x33, 0x27), (0x32, 0x25, 0x1c), (0x1a, 0x13, 0x0e)],
            "K": [(0x8a, 0x72, 0x50), (0x5c, 0x4a, 0x32)],
            "T": [(0x7e, 0x76, 0x60), (0x5a, 0x54, 0x42)],
        },
    ),
    dict(
        slug="11", sex="w", who="an older woman, light complexion",
        note="heavier through the body and greyer in the cloth: her linen has been washed until "
             "the colour left it, which is a lighter `S` with a NARROWER range than anyone's, and "
             "the modelling has to come from the fold count instead",
        cut="long",
        anchors={
            "F": [(0xd3, 0xad, 0x95), (0xc9, 0xa1, 0x89), (0xbd, 0x93, 0x7b), (0xb1, 0x87, 0x6f)],
            "P": [(0xe5, 0xc5, 0xaf), (0xd7, 0xb5, 0x9d), (0xbb, 0x95, 0x7d), (0x99, 0x73, 0x5d)],
            "B": [(0xdb, 0xa7, 0x91), (0xbf, 0x81, 0x6b)],
            "Q": [(0x9b, 0x73, 0x5b), (0x69, 0x49, 0x35)],
            "M": [(0x9f, 0x67, 0x5b), (0x6f, 0x3f, 0x35)],
            "O": [(0xf8, 0xf4, 0xea), (0xd0, 0xc8, 0xb8)],
            "I": [(0x73, 0x7f, 0x83), (0x31, 0x39, 0x3b)],
            "S": [(0xdc, 0xd8, 0xcc), (0xcc, 0xc8, 0xbc), (0xac, 0xa8, 0x9c), (0x6a, 0x66, 0x5c)],
            "G": [(0x9c, 0x94, 0x84), (0x74, 0x6e, 0x62), (0x46, 0x42, 0x3a)],
            "R": [(0xf0, 0xee, 0xe6), (0xd8, 0xd6, 0xcc)],
            "H": [(0xb0, 0xac, 0xa4), (0x98, 0x94, 0x8c), (0x70, 0x6d, 0x66), (0x40, 0x3e, 0x3a)],
            "U": [(0x7a, 0x70, 0x60), (0x5a, 0x52, 0x46), (0x36, 0x32, 0x2a)],
            "L": [(0x58, 0x4a, 0x3e), (0x46, 0x3a, 0x30), (0x36, 0x2c, 0x24), (0x20, 0x1a, 0x15)],
            "K": [(0xa4, 0x9c, 0x88), (0x72, 0x6c, 0x5e)],
            "T": [(0x88, 0x84, 0x78), (0x60, 0x5c, 0x52)],
        },
    ),
    dict(
        slug="12", sex="m", who="an older man, dark complexion",
        note="ONE SLEEVE MENDED IN ANOTHER LINEN — and it is on both arms, because the mesh "
             "mirrors them and `verify` compares them byte for byte, so the honest reading is a "
             "man who mended both cuffs out of the same scrap. The darkest body on the roster",
        cut="short",
        anchors={
            "F": [(0x82, 0x58, 0x3e), (0x76, 0x4e, 0x36), (0x68, 0x44, 0x2e), (0x5c, 0x3a, 0x26)],
            "P": [(0x94, 0x68, 0x4c), (0x82, 0x58, 0x40), (0x68, 0x44, 0x30), (0x4c, 0x2e, 0x20)],
            "B": [(0x8a, 0x56, 0x40), (0x6c, 0x3c, 0x2c)],
            "Q": [(0x46, 0x2c, 0x1c), (0x2a, 0x18, 0x0e)],
            "M": [(0x48, 0x24, 0x1e), (0x30, 0x14, 0x10)],
            "O": [(0xf2, 0xee, 0xe2), (0xc4, 0xbc, 0xaa)],
            "I": [(0x3c, 0x2a, 0x1e), (0x1c, 0x12, 0x0c)],
            "S": [(0xc0, 0xb4, 0x98), (0xae, 0xa2, 0x86), (0x86, 0x7c, 0x64), (0x46, 0x40, 0x34)],
            "G": [(0x80, 0x74, 0x5a), (0x5e, 0x54, 0x42), (0x34, 0x2e, 0x24)],
            # the mending linen, and it is GREYER than his own rather than lighter: a scrap off a
            # different garment, not a newer one. 04's patch is brighter and that is his own story.
            # Darkened from 0xC8C6BA after the sheet: a mended cuff runs right round the arm, so
            # it lands at the same height on BOTH arms, and at that contrast the pair read as one
            # light band straight across him. Greyer than his own linen and no lighter.
            "R": [(0xb4, 0xb2, 0xa8), (0x96, 0x94, 0x8c)],
            "H": [(0x8a, 0x82, 0x70), (0x74, 0x6c, 0x5e), (0x54, 0x4e, 0x44), (0x2e, 0x2a, 0x24)],
            "U": [(0x68, 0x5c, 0x46), (0x4c, 0x42, 0x32), (0x2e, 0x28, 0x1e)],
            "L": [(0x50, 0x3e, 0x2e), (0x40, 0x30, 0x24), (0x30, 0x23, 0x1a), (0x1a, 0x12, 0x0d)],
            "K": [(0x94, 0x7e, 0x58), (0x66, 0x54, 0x3a)],
            "T": [(0x72, 0x6c, 0x5c), (0x4e, 0x4a, 0x3e)],
        },
    ),
    dict(
        slug="13", sex="w", who="a young woman, dark complexion",
        note="clean and BAREFOOT — the only other bare feet on the roster are 06's, and hers are "
             "a choice rather than poverty: nothing else on her is worn at all. No grime, no mud, "
             "no patch, and a shift washed white",
        cut="long",
        anchors={
            "F": [(0xab, 0x7b, 0x5b), (0xa1, 0x71, 0x51), (0x93, 0x65, 0x47), (0x87, 0x5b, 0x3d)],
            "P": [(0xbf, 0x8f, 0x6f), (0xad, 0x7d, 0x5d), (0x91, 0x63, 0x47), (0x6f, 0x47, 0x31)],
            "B": [(0xb5, 0x77, 0x59), (0x97, 0x59, 0x41)],
            "Q": [(0x67, 0x43, 0x2d), (0x3f, 0x25, 0x17)],
            "M": [(0x6d, 0x39, 0x31), (0x47, 0x1f, 0x1b)],
            "O": [(0xff, 0xff, 0xff), (0xdc, 0xd0, 0xc4)],
            "I": [(0x3f, 0x29, 0x1b), (0x19, 0x0f, 0x09)],
            "S": [(0xe6, 0xdf, 0xce), (0xd4, 0xcd, 0xba), (0xaa, 0xa3, 0x90), (0x62, 0x5c, 0x4e)],
            "G": [(0xa4, 0x98, 0x80), (0x7c, 0x72, 0x5e), (0x4a, 0x44, 0x36)],
            "R": [(0xf2, 0xee, 0xe0), (0xda, 0xd6, 0xc6)],
            "H": [(0xd0, 0xc8, 0xb0), (0xba, 0xb2, 0x9a), (0x8e, 0x87, 0x72), (0x50, 0x4b, 0x3f)],
            "U": [(0x84, 0x76, 0x5c), (0x60, 0x54, 0x40), (0x3a, 0x33, 0x26)],
            "L": [(0x5c, 0x48, 0x36), (0x4a, 0x38, 0x2a), (0x38, 0x28, 0x1e), (0x20, 0x16, 0x10)],
            "K": [(0x9e, 0x88, 0x62), (0x6c, 0x5a, 0x3e)],
            "T": [(0x90, 0x8a, 0x74), (0x68, 0x62, 0x50)],
        },
    ),
]


# By slug, because the relight needs a person's own ramps to convert a luminance correction into
# that material's steps, and `draw` only ever had the slug.
PERSON = {p["slug"]: p for p in PEOPLE}


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


# ── person 02: the kept man, dark complexion ─────────────────────────
#
# THE ONE THE WHOLE APPROACH RESTS ON. The retired pipeline reached its dark complexion by
# multiplying the plains one down by 0.68, which kept 20 luminance points of flesh modelling out
# of 29. Drawn, his own ramp spans 36 — more than the light bodies, not less — because a dark face
# is modelled with its own anchors rather than with someone else's dimmed.

FACE_02 = [
    "F8F6F5F4F4F5F7F9",   # 0  the scalp under the hair cube
    "F5F2F0F0F0F1F3F6",   # 1  and his light falls RIGHT of centre, the mirror of 00's
    "F6F3F1F0F1F2F4F7",   # 2
    "F9Q1Q0F3F3Q2FaFb",   # 3  two brow cells one side, one the other. He is not symmetric
    "FaO0I1F3F4I1O0Fb",   # 4
    "FaFbQ0F0F1FcFdFe",   # 5  the nose, its shadow on his RIGHT — 00's falls the other way, and
    #                            the two flanks are dark by different means: occlusion, deep flesh
    "F9B5F3M0M1F4B5Fa",   # 6  a shallower blush than 00's: he is not weathered
    "Q1FaF7F4F5F8FbQ2",   # 7
]
HEAD_RIGHT_02 = [
    "F9F7F5F4F3F2F1F4",
    "FaF8F6F4F3F1F0F3",
    "FaF8F6F5F4F2F1F4",
    "FbF9F7F6F5F3F2F5",
    "FbFaF8F7F5F4F3F6",
    "FcFbF9Q0Q1F5F4F7",   # the ear sits a course LOWER on him than on 00 or 01
    "FdFcFaQ2FcF7F6F9",
    "Q0FeFcFaF9F8F7Fa",
]
HEAD_02 = {
    "front":  FACE_02,
    "top": [
        "FcFaF8F7F6F8FaFc",
        "FaF7F5F3F2F4F7Fa",
        "F8F5F2F1F0F2F5F8",
        "F7F3F1F0F0F1F4F7",
        "F7F3F0F0F0F1F3F7",
        "F8F5F2F1F1F2F5F8",
        "FaF8F5F4F4F5F8Fa",
        "FcFaF8F7F7F8FaFc",
    ],
    "back": [
        "FbF9F7F6F6F7F9Fb",
        "FbF9F7F5F5F7F9Fb",
        "FcFaF8F6F6F8FaFc",
        "FdFbF9F7F7F9FbFd",
        "FdFcFaF8F8FaFcFd",
        "FeFdFbFaFaFbFdFe",
        "FfFeFcFbFbFcFeFf",
        "Q1FfFeFdFdFeFfQ1",   # his nape is less occluded than 00's — a cropped head, not a hooded one
    ],
    "right":  HEAD_RIGHT_02,
    "left":   flipc(HEAD_RIGHT_02),
    "bottom": [
        "Q1Q2Q2Q3Q3Q2Q2Q1",
        "Q2Q2Q3Q4Q4Q3Q2Q2",
        "Q2Q3Q4Q5Q5Q4Q3Q2",
        "Q3Q4Q5Q6Q6Q5Q4Q3",
        "Q3Q4Q5Q6Q6Q5Q4Q3",
        "Q2Q3Q4Q5Q5Q4Q3Q2",
        "Q2Q2Q3Q4Q4Q3Q2Q2",
        "Q1Q2Q2Q3Q3Q2Q2Q1",
    ],
}

# A square neck finished with a stitched facing right across it, which is what better linen buys
# and what 00's raw laced slit does not have. The fold column sits at col 3 and jitters to 2 —
# 00's sits at 3 and jitters to 4, so the two hang differently.
SHIFT_02 = {
    "front": [
        "ScS8S4P3P4S5S9Sd",   # 0  a square neck, wider than 00's laced slit
        "SaS6S2P1P2S3S7Sb",   # 1
        "S9S5T1T0T0T1S6Sa",   # 2  THE FACING, stitched right across. Nobody poor has this
        "S9S4S1S0S1S2S5Sa",   # 3
        "SaS5S2S1S2S3S6Sb",   # 4
        "SaS6S2S1S2S4S7Sb",   # 5
        "SbS6S3S2S3S4S8Sc",   # 6
        "K2K0K1K2K3K3K4K5",   # 7  his girdle rides a course higher than 00's
        "SbS7S4S3S4S5S9Sd",   # 8
        "ScS8S5S4S5S6SaSd",   # 9
        "ScS9S6S5S6S7SaSe",   # 10  and there is NO grime anywhere on him
        "SdSaS7S6S7S9SbSf",   # 11
    ],
    "back": [
        "SdS9S6S4S5S7SaSe",
        "SbS7S4S2S3S5S8Sc",
        "SaS6S3S1S2S4S7Sb",
        "S9S5S2S1S2S3S6Sa",
        "SaS5T0T1S2S4S7Sb",   # a yoke seam across the shoulder blades, not down them
        "SaS6S3S2S3S5S8Sc",
        "SbS7S4S3S4S6S9Sd",
        "K3K1K2K3K4K4K5K5",
        "ScS8S5S4S5S7SaSd",
        "ScS9S6S5S6S8SbSe",
        "SdSaS7S6S7S9SbSe",
        "SeSbS8S7S8SaScSf",
    ],
    "right":  ["ScS7S5Sb", "SaS5S3S9", "S9S3T0S8", "S8S2S1S7",
               "S9S3S2S8", "SaS4S3S9", "SaS5S4Sa", "K3K1K2K4",
               "SbS6S5Sb", "ScS7S6Sc", "ScS8S7Sd", "SdS9S8Se"],
    "left":   ["SbS5S7Sc", "S9S3S5Sa", "S8T0S3S9", "S7S1S2S8",
               "S8S2S3S9", "S9S3S4Sa", "SaS4S5Sa", "K4K2K1K3",
               "SbS5S6Sb", "ScS6S7Sc", "SdS7S8Sc", "SeS8S9Sd"],
    "top":    ["ScS8S5S3S3S5S8Sc", "S9S5S2S0S0S2S5S9",
               "R1R3S1S0S0S1R4R2", "SaS6S3S1S2S4S7Sb"],
    "bottom": ["SeSdSdScScSdSdSe", "SdSdScSbSbScSdSd",
               "SdScSbSaSaSbScSd", "SeSdScSbSbScSdSe"],
}

# Sleeve to the forearm, one course longer than 00's, and its cuff is STITCHED rather than dirty.
SLEEVE_02 = {
    "front":  ["S5S1S6Sc", "S4S0S5Sb", "S5S1S6Sc", "S5S1S6Sc",
               "S6S2S7Sd", "S6S2S7Sd", "T2T0T3T5",
               "F4F1F5Fc", "F5F2F6Fd", "F5F2F6Fd", "F7F4F8Fe", "F9F6FaFf"],
    "back":   ["S6S2S7Sd", "S5S1S6Sc", "S4S0S5Sb", "S5S1S6Sc",
               "S6S2S7Sd", "S7S3S8Se", "T3T1T4T5",
               "F5F2F6Fd", "F5F2F6Fd", "F6F3F7Fe", "F8F5F9Ff", "FaF7FbFf"],
    "right":  ["R1R0S2S4", "R3R2S0S2", "S6S3S1S3", "S7S4S1S3",
               "S8S5S2S4", "S8S5S2S4", "T2T1T0T2",
               "F6F3F1F3", "F6F3F1F3", "F7F4F2F4", "F9F6F4F6", "FbF8F6F8"],
    "left":   ["ScSaS9Sb", "ScSaS9Sb", "SdSbSaSc", "SdSbSaSc",
               "SeScSbSd", "SeScSbSd", "T5T4T3T5",
               "P4P3P2P4", "P6P5P4P6", "P8P7P6P8", "P9P8P7P9", "PbPaP9Pb"],
    "top":    ["S8S4S4S8", "S5S1S1S5", "S5S1S1S5", "S9S5S5S9"],
    "bottom": ["P9P6P6P9", "P6B3B0P7", "P6B4B2P7", "P9P7P7Pa"],
}

# Good hose and a shoe with a heel counter. The one mark on him is at the shin, not the knee: he
# does not kneel to work, which is the whole difference between him and 00.
HOSE_02 = {
    "front":  ["HcH9HbHe", "HbH7H9Hd", "H9H4H7Hb", "H7H1H4H9",
               "H5H0H2H7", "H6H1H3H8", "H7H2H4H9",
               "H8H3H5Ha", "U2U0U1U4", "HaH5H7Hc",
               "L3L0L1L5", "L8L5L7La"],
    "back":   ["HdHaHcHe", "HcH8HaHd", "HaH5H8Hc", "H8H2H5Ha",
               "H6H1H3H8", "H7H2H4H9", "H8H3H5Ha",
               "H9H4H6Hb", "U3U1U2U5", "HbH6H8Hd",
               "L4L1L2L6", "L9L6L8Lb"],
    "right":  ["HbHcHdHe", "H9HaHcHd", "H6H8HaHc", "H3H5H8Ha",
               "H0H2H5H8", "H1H3H6H9", "H2H4H7Ha",
               "H4H6H8Hb", "U0U1U3U5", "H7H9HbHd",
               "L1L2L4L6", "L6L7L9Lb"],
    "left":   ["HeHdHeHf", "HdHcHdHe", "HbHaHcHd", "H9H8HaHc",
               "H8H7H9Hb", "H9H8HaHc", "HaH9HbHd",
               "HbHaHcHe", "U5U4U6U7", "HcHbHdHe",
               "L5L4L6L7", "LaL9LbLb"],
    "top":    ["HeHdHdHe", "HdHcHcHd", "HdHcHcHd", "HeHdHdHe"],
    "bottom": ["LaL9L9La", "L9L8L8L9", "L9L8L8L9", "LaL9L9La"],
}

# Five courses of hem, one more than 00. A longer tunic IS the tell of a man who can spare cloth,
# and it is a thing the silhouette says at any distance.
HEM_02 = {
    "front":  ["S7S3S1S5", "S8S4S2S6", "S9S5S3S7", "SaS6S4S8", "SbS7S5S9"] + [".." * 4] * 7,
    "back":   ["S8S4S2S6", "S9S5S3S7", "SaS6S4S8", "SbS7S5S9", "ScS8S6Sa"] + [".." * 4] * 7,
    "right":  ["T0S3S1S3", "T1S4S2S4", "T2S5S3S5", "T3S6S4S6", "T4S7S5S7"] + [".." * 4] * 7,
    "left":   ["SbS9S7S9", "ScSaS8Sa", "SdSbS9Sb", "SeScSaSc", "SfSdSbSd"] + [".." * 4] * 7,
    "top":    ["S6S3S3S6", "S3S1S1S3", "S3S1S1S3", "S7S4S4S7"],
    "bottom": [".." * 4] * 4,
}


# ── person 03: the working woman, warm complexion ────────────────────

FACE_03 = [
    "FcFaF8F7F7F8FaFd",   # 0
    "F9F6F3F2F2F4F7Fa",   # 1  a broader, flatter forehead than 01's
    "FaF7F4F3F3F5F8Fb",   # 2
    "FdQ2Q1Q0F5F5Q1Fe",   # 3  brows that meet nearer the centre on ONE side — she has a squint
    "FdO0I1F5F6I2O1Fe",   # 4
    "FdFbFcF1F2Q1FeFf",   # 5  a broad nose, bridge lit, one flank in occlusion
    "FbB4F5M1M2F6B5Fc",   # 6
    "Q2FcF9F6F7FaFdQ3",   # 7  a heavier jaw than 01's, and shaded across it rather than at the ends
]
HEAD_RIGHT_03 = [
    "FdFbF9F8F7F6F5F8",
    "FdFbF9F7F6F4F3F6",
    "FeFcFaF8F7F5F4F7",
    "FeFcFaF9F8F6F5F8",
    "FfFdFbQ1Q2F7F6F9",
    "FfFeFcQ3FdF8F7Fa",
    "Q0FfFdFbFaF9F8Fb",
    "Q3Q0FfFdFcFbFaFc",
]
HEAD_03 = {
    "front":  FACE_03,
    "top": [
        "FeFcFaF9F9FaFcFe",
        "FcF9F7F5F5F7F9Fc",
        "FaF7F4F3F3F4F7Fa",
        "F9F5F3F2F2F3F6F9",
        "F9F5F2F2F2F3F5F9",
        "FaF7F4F3F3F4F7Fa",
        "FcFaF7F6F6F7FaFc",
        "FeFdFbFaFaFbFdFe",
    ],
    "back": [
        "FdFbF9F8F8F9FbFd",
        "FeFbF9F7F7F9FbFe",
        "FfFcFaF8F8FaFcFf",
        "FfFdFbF9F9FbFdFf",
        "Q0FeFcFaFaFcFeQ0",
        "Q1FfFdFbFbFdFfQ1",
        "Q2Q0FeFcFcFeQ0Q2",
        "Q4Q2Q0FeFeQ0Q2Q4",
    ],
    "right":  HEAD_RIGHT_03,
    "left":   flipc(HEAD_RIGHT_03),
    "bottom": [
        "Q2Q3Q3Q4Q4Q3Q3Q2",
        "Q3Q4Q4Q5Q5Q4Q4Q3",
        "Q4Q4Q5Q6Q6Q5Q4Q4",
        "Q4Q5Q6Q7Q7Q6Q5Q4",
        "Q4Q5Q6Q7Q7Q6Q5Q4",
        "Q4Q4Q5Q6Q6Q5Q4Q4",
        "Q3Q4Q4Q5Q5Q4Q4Q3",
        "Q2Q3Q3Q4Q4Q3Q3Q2",
    ],
}

# Coarse linen, a keyhole neck closed with one cord, and a wide gathered apron-line of grime
# across the belly where she leans on things. The fold column is at col 4 — 00's is at 3 and
# 02's at 3-2 — so the three drape differently and it shows in profile.
SHIFT_03 = {
    "front": [
        "SdSaS6P4P5S7SbSe",   # 0  a keyhole neck, the throat covered skin
        "SbS8K3P2P3S5S9Sc",   # 1  ONE cord, off centre, and it is her whole fastening
        "SaS6S3S1S0S3S7Sb",   # 2  the lit fold runs down col 4, not col 3
        "SaS5S2S1S0S2S6Sb",   # 3
        "SbS6S3S2S1S3S7Sc",   # 4
        "SbS7S4S2S1S4S8Sc",   # 5
        "ScS8S4S3S2S5S9Sd",   # 6
        "ScS8S5S4S3S5S9Sd",   # 7
        "SdS9S5S4S4S6SaSe",   # 8
        "G1G0S6S5S5G2SbSe",   # 9  she leans on things: the grime is a BAND at the belly, broken
        "SeSbS7S6S6S8ScSf",   # 10   in the middle so it is not a painted stripe
        "SfScS9S8S8SaSdSf",   # 11
    ],
    "back": [
        "SeSbS7S5S6S8ScSf",
        "ScS9S5S3S4S6SaSd",
        "SbS7S4S2S1S4S8Sc",
        "SbS6S3S2S1S3S7Sc",
        "ScS7S4S3S2S4S8Sd",
        "ScS8S5S4S3S5S9Sd",
        "SdS9S5S4S4S6SaSe",
        "SdS9S6S5S5S7SaSe",
        "SeSaS7S6S6S8SbSf",
        "SeSbS7S6G2G1ScSf",
        "SfScS8S7S8SaSdSf",
        "SfSdSaS9SaScSeSf",
    ],
    "right":  ["ScS8S6Sd", "SaS6S4Sb", "S9S4S2Sa", "S9S4S1S9",
               "SaS5S2Sa", "SbS6S3Sb", "SbS7S4Sc", "ScS8S5Sd",
               "ScS8S5Sd", "G3G2G1G4", "SeSaS8Se", "SfSbS9Sf"],
    "left":   ["SdS6S8Sc", "SbS4S6Sa", "SaS2S4S9", "S9S1S4S9",
               "SaS2S5Sa", "SbS3S6Sb", "ScS4S7Sb", "SdS5S8Sc",
               "SdS5S8Sc", "G4G1G2G3", "SeS8SaSe", "SfS9SbSf"],
    "top":    ["SeSaS7S5S5S7SaSe", "R1R3S2S1S1S2R4R2",
               "SbS7S4S2S2S4S7Sb", "ScS9S6S4S4S6S9Sd"],
    "bottom": ["SfSeSeSdSdSeSeSf", "SeSeSdScScSdSeSe",
               "SeSdScScScScSdSe", "SfSeSdScScSdSeSf"],
}

# ONE SLEEVE PUSHED UP AND THE OTHER DOWN is not available — the mesh mirrors the arms, and
# `verify` compares them byte for byte. So hers ends at the elbow like a man's, which is what a
# woman working a yard does with a sleeve, and the forearm below it is TANNED where 01's is not.
SLEEVE_03 = {
    "front":  ["S6S2S7Sd", "S5S1S6Sc", "S5S1S6Sc", "S6S2S7Sd",
               "G4G1G5G7",
               "F5F2F6Fd", "F5F2F6Fd", "F6F3F7Fd", "F6F3F7Fe",
               "F8F4F8Fe", "F9F6FaFf", "FbF8FcFf"],
    "back":   ["S7S3S8Se", "S6S2S7Sd", "S5S1S6Sc", "S6S2S7Sd",
               "G5G2G6G7",
               "F6F3F7Fe", "F5F2F6Fd", "F6F3F7Fe", "F7F4F8Fe",
               "F9F5F9Ff", "FaF7FbFf", "FcF9FdFf"],
    "right":  ["S7S4S2S4", "S6S3S1S3", "S6S3S1S3", "S7S4S2S4",
               "G4G3G2G4",
               "F7F4F2F4", "F7F4F2F4", "F8F5F3F5", "F8F5F3F5",
               "FaF7F5F7", "FbF8F6F8", "FdFaF8Fa"],
    "left":   ["SdSbSaSc", "SdSbSaSc", "SeScSbSd", "SeScSbSd",
               "G7G6G6G7",
               "P4P3P2P4", "P5P4P3P5", "P7P6P5P7", "P8P7P6P8",
               "P9P8P7P9", "PaP9P8Pa", "PbPaP9Pb"],
    "top":    ["SaS6S6Sa", "S7S2S2S7", "S7S2S2S7", "SbS7S7Sb"],
    "bottom": ["PaP7P7Pa", "P7B2B5P8", "P7B4B1P8", "PaP8P8Pb"],
}

# Stockings, no garter — hers are tied at the top and it does not show below the skirt. The mud
# is at the SHIN and the ankle both, and it runs up the outside where a skirt swings against it.
HOSE_03 = {
    "front":  ["HcH9HbHe", "HbH8HaHd", "HaH6H8Hc", "H9H5H7Hb",
               "H8H4H6Ha", "H8H4H6Ha", "H9H5H7Hb",
               "U2U0U1U4", "U3U1U2U5", "U4U2U3U6",
               "L4L0L2L6", "L9L6L8Lb"],
    "back":   ["HdHaHcHe", "HcH9HbHd", "HbH7H9Hc", "HaH6H8Hb",
               "H9H5H7Hb", "H9H5H7Hb", "HaH6H8Hc",
               "U3U1U2U5", "U4U2U3U6", "U5U3U4U7",
               "L5L1L3L7", "LaL7L9Lb"],
    "right":  ["U0U1U2U4", "U0U1U2U4", "U1U2U3U5", "H5H7H9Hb",
               "H4H6H8Ha", "H4H6H8Ha", "H5H7H9Hb",
               "U1U2U3U5", "U2U3U4U6", "U3U4U5U7",
               "L2L3L5L7", "L7L8LaLb"],
    "left":   ["U4U2U1U0", "U4U2U1U0", "U5U3U2U1", "HbH9H7H5",
               "HaH8H6H4", "HaH8H6H4", "HbH9H7H5",
               "U5U3U2U1", "U6U4U3U2", "U7U5U4U3",
               "L7L5L3L2", "LbLaL8L7"],
    "top":    ["U5U4U4U5", "U4U3U3U4", "U4U3U3U4", "U5U4U4U5"],
    "bottom": ["LbLaLaLb", "LaL9L9La", "LaL9L9La", "LbLaLaLb"],
}

# Nine courses, so the ankle and the whole shoe show — she has hitched it to work in, and that is
# a course shorter than 01's and two shorter than a woman who does not. The last two courses are
# where a dragged hem goes: frayed, then filthy.
HEM_03 = {
    "front":  ["S8S4S2S6", "S9S5S3S7", "S9S5S4S8", "SaS6S4S8",
               "SaS6S5S9", "SbS7S5S9", "SbS7S6Sa",
               "R1R0R2R3", "G2G0G1G4"] + [".." * 4] * 3,
    "back":   ["S9S5S3S7", "SaS6S4S8", "SaS6S5S9", "SbS7S5S9",
               "SbS7S6Sa", "ScS8S6Sa", "ScS8S7Sb",
               "R2R1R3R4", "G3G1G2G5"] + [".." * 4] * 3,
    "right":  ["S6S3S1S3", "S7S4S2S4", "S7S4S2S4", "S8S5S3S5",
               "S8S5S3S5", "S9S6S4S6", "S9S6S4S6",
               "R0R1R4R5", "G3G2G1G3"] + [".." * 4] * 3,
    "left":   ["S3S1S3S6", "S4S2S4S7", "S4S2S4S7", "S5S3S5S8",
               "S5S3S5S8", "S6S4S6S9", "S6S4S6S9",
               "R5R4R1R0", "G3G1G2G3"] + [".." * 4] * 3,
    "top":    ["S7S4S4S7", "S4S1S1S4", "S4S1S1S4", "S8S5S5S8"],
    "bottom": [".." * 4] * 4,
}


# ── person 04: the old man, olive complexion ─────────────────────────
#
# AGE IS DRAWN WITH THE OCCLUSION RAMP AND NOTHING ELSE. Not a grey palette — hair is a tinted
# cube and greys itself. What an old face has that a young one has not is shadow in more places:
# a line across the forehead, a hollow at the temple, a fold from nose to mouth corner, a slack
# jaw. All of that is `Q`, and his `Q` runs deeper and wider than anyone's.

FACE_04 = [
    "FbF9F8F7F7F8FaFc",   # 0
    "F9F5Q3Q2Q2Q3F6Fa",   # 1  A LINED FOREHEAD, and the line is CONTINUOUS across cols 2..5. The
    "F8F5F2F1F1F2F5F9",   # 2   first version alternated Q and F along this row and came off the
    #                            sheet as a field of specks — the same failure this repo already
    #                            recorded for scattered highlights on cloth. A crease is a run.
    "FdQ2Q1Q0F4Q1Q3Fe",   # 3  a heavy brow, three cells one side
    "FeO1I1F3F5I2O0Ff",   # 4  and his eyes are sunk — the socket carries the deep end of Q
    "FeFcQ0F0F1FdFeFf",   # 5  the nose, its bridge still the lit half, its flank in occlusion
    "FcQ4F5M1M2F6Q5Fd",   # 6  THE FOLD from nose to mouth corner, which is the oldest thing here
    "Q6FeFaF7F8FbFeQ7",   # 7  a slack jaw, and the deepest cells on the whole body are these
]
HEAD_RIGHT_04 = [
    "FdFbF9F8F7F6F5F8",
    "FdFbF9F7F6F4F3F6",
    "FeFcQ0Q1F6F4F3F6",   # the temple hollow, and it is a fact about his age
    "FeFcFaF8F7F5F4F7",
    "FfFdFbQ2Q3F6F5F8",
    "FfFeFcQ4FeF7F6F9",
    "Q0FfFdFbF9F8F7Fa",
    "Q4Q1FfFdFbFaF9Fc",
]
HEAD_04 = {
    "front":  FACE_04,
    "top": [
        "FfFdFbFaFaFbFdFf",
        "FdFaF8F6F6F8FaFd",
        "FbF8F5F4F4F5F8Fb",
        "FaF6F3F2F2F4F7Fa",
        "F9F5F3F2F2F3F6F9",
        "FaF7F4F3F3F5F8Fb",
        "FdFaF7F6F6F7FaFd",
        "FfFeFcFbFbFcFeFf",
    ],
    "back": [
        "FeFcFaF9F9FaFcFe",
        "FeFcFaF8F8FaFcFe",
        "FfFdFbF9F9FbFdFf",
        "Q0FeFcFaFaFcFeQ0",
        "Q1FfFdFbFbFdFfQ1",
        "Q2Q0FeFcFcFeQ0Q2",
        "Q3Q1FfFdFdFfQ1Q3",
        "Q5Q3Q1FfFfQ1Q3Q5",
    ],
    "right":  HEAD_RIGHT_04,
    "left":   flipc(HEAD_RIGHT_04),
    "bottom": [
        "Q3Q4Q4Q5Q5Q4Q4Q3",
        "Q4Q5Q5Q6Q6Q5Q5Q4",
        "Q4Q5Q6Q7Q7Q6Q5Q4",
        "Q5Q6Q7Q7Q7Q7Q6Q5",
        "Q5Q6Q7Q7Q7Q7Q6Q5",
        "Q4Q5Q6Q7Q7Q6Q5Q4",
        "Q4Q5Q5Q6Q6Q5Q5Q4",
        "Q3Q4Q4Q5Q5Q4Q4Q3",
    ],
}

# A SHIFT WITH A PATCH IN IT, and the patch is what makes it his. Another linen entirely — lighter
# than his own, because a scrap off a newer garment is the only spare cloth a household has — laid
# as a BLOCK with a stitched seam round it. Not scattered specks: a patch is a rectangle, cut and
# sewn, and it is the one thing on this body that is not a fold.
SHIFT_04 = {
    "front": [
        "SdSaS6P5P6S7SbSe",   # 0  a plain slit neck, no lace and no facing — he has neither
        "SbS8S4P3P4S5S9Sc",   # 1
        "SaS6S3S2S3S4S8Sb",   # 2
        "SaS5S2S1S2S3S7Sb",   # 3
        "SbS6S3S2S3S4S8Sc",   # 4
        "SbS7S3S2S3S5S8Sc",   # 5
        "T1R0R1R2T0S5S9Sd",   # 6  THE PATCH: four courses of another linen, seamed left and top
        "T2R1R0R1T1S6SaSd",   # 7
        "T3R2R1R2T2S7SaSe",   # 8
        "T4T3T4T3T3S8SbSe",   # 9  and its bottom seam, which is what says sewn and not spilt
        "K1K0K1K2K2K3K4K5",   # 10 a ROPE at the waist, and it rides low on him
        "SeSbS8S7S8SaSdSf",   # 11
    ],
    "back": [
        "SeSbS7S6S7S9ScSf",
        "ScS9S5S4S5S7SaSd",
        "SbS7S4S3S4S6S9Sc",
        "SbS6S3S2S3S5S8Sc",
        "SbS7S4S3S4S6S9Sc",
        "ScS8S5S4S5S7SaSd",
        "ScS8S5S4S5S7SaSd",
        "SdS9S6S5S6S8SbSe",
        "SdSaS7S6S7S9SbSe",
        "G2G1G0G2G3G4SbSf",   # his back is where the dirt is — he leans on walls, not on tables
        "K2K1K2K3K3K4K5K5",
        "SfScS9S8S9SbSeSf",
    ],
    "right":  ["ScS8S6Sd", "SaS6S4Sb", "S9S5S3Sa", "S9S4S2S9",
               "SaS5S3Sa", "SbS6S4Sb", "T0R0R1T1", "T1R1R0T2",
               "T2R2R1T3", "T3T2T3T4", "K2K1K2K4", "SfSbS9Sf"],
    "left":   ["SdS6S8Sc", "SbS4S6Sa", "SaS3S5S9", "S9S2S4S9",
               "SaS3S5Sa", "SbS4S6Sb", "T1R1R0T0", "T2R0R1T1",
               "T3R1R2T2", "T4T3T2T3", "K4K2K1K2", "SfS9SbSf"],
    "top":    ["SeSaS7S5S5S7SaSe", "SbS7S4S2S2S4S7Sb",
               "R1R2S1S0S0S1R3R2", "ScS8S5S3S4S6S9Sd"],
    "bottom": ["SfSeSeSdSdSeSeSf", "SeSeSdScScSdSeSe",
               "SeSdScSbSbScSdSe", "SfSeSdScScSdSeSf"],
}

# Three courses of sleeve — the shortest on the roster — a mended cuff, and a forearm that has
# been in the sun for sixty years. His hand carries the warm accent at every knuckle.
SLEEVE_04 = {
    "front":  ["S5S1S6Sc", "S5S1S6Sc", "T2T0T3T5",
               "F5F2F6Fd", "F5F2F6Fd", "F6F3F7Fd", "F6F3F7Fe",
               "F7F4F8Fe", "F8F5F9Ff", "F9F6FaFf", "FbF8FcFf", "FdFaFeFf"],
    "back":   ["S6S2S7Sd", "S5S1S6Sc", "T3T1T4T5",
               "F6F3F7Fe", "F5F2F6Fd", "F6F3F7Fe", "F7F4F8Fe",
               "F8F5F9Ff", "F9F6FaFf", "FaF7FbFf", "FcF9FdFf", "FeFbFfFf"],
    "right":  ["S6S3S1S3", "S6S3S1S3", "T2T1T0T2",
               "F6F3F1F3", "F7F4F2F4", "F7F4F2F4", "F8F5F3F5",
               "F9F6F4F6", "FaF7F5F7", "FbF8F6F8", "FdFaF8Fa", "FfFcFaFc"],
    "left":   ["SdSbSaSc", "SdSbSaSc", "T5T4T3T5",
               "P4P3P2P4", "P5P4P3P5", "P6P5P4P6", "P7P6P5P7",
               "P8P7P6P8", "P9P8P7P9", "PaP9P8Pa", "PbPaP9Pb", "PbPbPaPb"],
    "top":    ["SbS7S7Sb", "S8S3S3S8", "S8S3S3S8", "ScS8S8Sc"],
    "bottom": ["PaP7P7Pa", "P7B5B2P8", "P7B4B5P8", "PaP8P8Pb"],
}

# Hose gone slack at the knee — the courses alternate rather than ramp, which is what sagging wool
# does and a smooth ramp cannot say. His shoes have worn through at the toe.
HOSE_04 = {
    # THE SAG IS JITTERED, NOT ALTERNATED. The first version flipped whole courses between two
    # depths and his legs came off the sheet reading as a check — which is the painted-stripe
    # failure this repo already records for stone courses, arriving on wool. Slack cloth pools
    # unevenly: each course shifts WHERE it is deep rather than whether it is.
    "front":  ["HcH9HbHe", "HaH6H9Hc", "H8H3H6Ha",
               "H6H1H4H8", "H8H4H5H9", "H7H2H6H9", "H9H5H7Ha",
               "H7H3H5H9", "H9H4H8Hb", "HbH6H9Hd",
               "L5L1L3L7", "LaL7L9Lb"],
    "back":   ["HdHaHcHe", "HbH7HaHd", "H9H4H7Hb",
               "H7H2H5H9", "H9H5H6Ha", "H8H3H7Ha", "HaH6H8Hb",
               "H8H4H6Ha", "HaH5H9Hc", "HcH7HaHe",
               "L6L2L4L8", "LbL8LaLb"],
    "right":  ["HbHcHdHe", "H9HaHcHd", "H6H8HaHc",
               "H3H5H8Ha", "H5H8HaHc", "H4H6H9Hb", "H6H9HbHd",
               "H5H7H9Hb", "H7H9HcHe", "H9HbHdHe",
               "U1U2U4U6", "L7L8LaLb"],                          # mud on the shoe, not the knee
    "left":   ["HeHdHcHb", "HdHcHaH9", "HcHaH8H6",
               "HaH8H5H3", "HcHaH8H5", "HbH9H6H4", "HdHbH9H6",
               "HbH9H7H5", "HeHcH9H7", "HeHdHbH9",
               "U6U4U2U1", "LbLaL8L7"],
    "top":    ["HeHdHdHe", "HdHcHcHd", "HdHcHcHd", "HeHdHdHe"],
    "bottom": ["LbLaLaLb", "LaL9L8L9", "L9L8L8L9", "LbLaLaLb"],
    }

# Three courses only. The shortest tunic on the roster, and it is not a choice — it is a garment
# that has been cut down and cut down again.
HEM_04 = {
    "front":  ["S8S4S2S6", "S9S5S3S7", "G3G1G2G5"] + [".." * 4] * 9,
    "back":   ["S9S5S3S7", "SaS6S4S8", "G4G2G3G6"] + [".." * 4] * 9,
    "right":  ["S7S4S2S4", "S8S5S3S5", "G4G3G1G3"] + [".." * 4] * 9,
    "left":   ["S4S2S4S7", "S5S3S5S8", "G3G1G3G4"] + [".." * 4] * 9,
    "top":    ["S7S4S4S7", "S4S1S1S4", "S4S1S1S4", "S8S5S5S8"],
    "bottom": [".." * 4] * 4,
}


# ── person 05: the old woman, olive complexion ───────────────────────

FACE_05 = [
    "FdFbFaF9F9FaFcFe",   # 0
    "Q0F7F4F3F3F5F8Q1",   # 1  HER lines are the temple, not the brow: a run down the outer column
    "Q1F6F3F2F2F4F7Q2",   # 2   of rows 1..3, which is crow's feet and reads as a fold rather than
    "Q2Q1Q0F4F4Q0Q1Q3",   # 3   as the specks two isolated cells on row 1 produced. Thin high brow

    "FdO1I1F4F5I1O0Fe",   # 4
    "FdFbFdF1F2Q0FeFf",   # 5  a narrow nose, its shadow on her left
    "FbQ3F6M1M2F7Q4Fc",   # 6  the fold from nose to mouth, shallower than 04's
    "Q5FdFaF8F9FbFdQ6",   # 7
]
HEAD_RIGHT_05 = [
    "FeFcFaF9F8F7F6F9",
    "FeFcFaF8F7F5F4F7",
    "FfFdQ1Q2F7F5F4F7",
    "FfFdFbF9F8F6F5F8",
    "Q0FeFcQ3Q4F7F6F9",
    "Q0FfFdQ5FfF8F7Fa",
    "Q1Q0FeFcFaF9F8Fb",
    "Q5Q2Q0FeFcFbFaFd",
]
HEAD_05 = {
    "front":  FACE_05,
    "top": [
        "Q0FeFcFbFbFcFeQ0",
        "FeFbF9F7F7F9FbFe",
        "FcF9F6F5F5F6F9Fc",
        "FbF7F4F3F3F5F8Fb",
        "FaF6F4F3F3F4F7Fa",
        "FbF8F5F4F4F6F9Fc",
        "FeFbF8F7F7F8FbFe",
        "Q0FfFdFcFcFdFfQ0",
    ],
    "back": [
        "FfFdFbFaFaFbFdFf",
        "FfFdFbF9F9FbFdFf",
        "Q0FeFcFaFaFcFeQ0",
        "Q1FfFdFbFbFdFfQ1",
        "Q2Q0FeFcFcFeQ0Q2",
        "Q3Q1FfFdFdFfQ1Q3",
        "Q4Q2Q0FeFeQ0Q2Q4",
        "Q6Q4Q2Q0Q0Q2Q4Q6",
    ],
    "right":  HEAD_RIGHT_05,
    "left":   flipc(HEAD_RIGHT_05),
    "bottom": [
        "Q4Q4Q5Q6Q6Q5Q4Q4",
        "Q4Q5Q6Q6Q6Q6Q5Q4",
        "Q5Q6Q6Q7Q7Q6Q6Q5",
        "Q5Q6Q7Q7Q7Q7Q6Q5",
        "Q5Q6Q7Q7Q7Q7Q6Q5",
        "Q5Q6Q6Q7Q7Q6Q6Q5",
        "Q4Q5Q6Q6Q6Q6Q5Q4",
        "Q4Q4Q5Q6Q6Q5Q4Q4",
    ],
}

# A cloth wrapped TWICE at the waist rather than a cord tied once — two courses of `K`, which is
# how a woman with no belt keeps a shift in. Her patch is on the shoulder where a yoke wears out,
# not on the belly.
SHIFT_05 = {
    "front": [
        "SeSbS7P5P6S8ScSf",   # 0  a wide plain neck, and the throat is covered skin
        "ScS9S5P3P4S6SaSd",   # 1
        "SbS7T0T1T1T0S8Sc",   # 2  the neck edge turned over and stitched once, roughly
        "SaS6S3S2S3S4S7Sb",   # 3
        "SbS7S4S3S4S5S8Sc",   # 4
        "SbS7S4S3S4S5S8Sc",   # 5
        "ScS8S5S4S5S6S9Sd",   # 6
        "ScS8S5S4S5S6S9Sd",   # 7
        "K1K0K1K2K2K3K4K4",   # 8  wrapped once
        "K3K2K3K3K4K4K5K5",   # 9  and again — two courses, which is the whole device
        "SeSbS8S7S8SaSdSf",   # 10
        "SfScSaS9SaScSeSf",   # 11
    ],
    "back": [
        "SfScS8S7S8SaSdSf",
        "SdSaS6S5S6S8SbSe",
        "ScS8S5S4S5S7SaSd",
        "SbS7S4S3S4S6S9Sc",
        "ScS8S5S4S5S7SaSd",
        "ScS8S5S4S5S7SaSd",
        "SdS9S6S5S6S8SbSe",
        "SdS9S6S5S6S8SbSe",
        "K2K1K2K3K3K4K5K5",
        "K4K3K4K4K5K5K5K5",
        "SfScS9S8S9SbSeSf",
        "SfSdSbSaSbSdSfSf",
    ],
    "right":  ["SeS9S7Se", "ScS7S5Sc", "SbT0S4Sb", "SaS5S3Sa",
               "SbS6S4Sb", "SbS6S4Sb", "ScS7S5Sc", "ScS7S5Sc",
               "K2K1K2K4", "K4K3K4K5", "SfSbS9Sf", "SfSdSbSf"],
    "left":   ["SeS7S9Se", "ScS5S7Sc", "SbS4T0Sb", "SaS3S5Sa",
               "SbS4S6Sb", "SbS4S6Sb", "ScS5S7Sc", "ScS5S7Sc",
               "K4K2K1K2", "K5K4K3K4", "SfS9SbSf", "SfSbSdSf"],
    # HER PATCH IS HERE, on the shoulder, because that is the seam a carrying-strap wears through
    # and it is the one part of the torso a garment never covers.
    "top":    ["SfSbS8S6S6S8SbSf", "R0R1R2S1S1R2R1R0",
               "T0T1S2S1S1S2T1T0", "ScS8S5S3S4S6S9Sd"],
    "bottom": ["SfSfSeSeSeSeSfSf", "SfSeSdScScSdSeSf",
               "SeSdScSbSbScSdSe", "SfSeSdScScSdSeSf"],
}

# Sleeves to the wrist, and the elbow has gone through.
#
# THE PATCH IS TWO COLUMNS WIDE AND NOT FOUR, which is a correction the sheet forced. `NpcModel`
# does not declare the left limbs `.mirror()` and `verify` compares them byte for byte, so ANY arm
# feature appears twice at the same height — and a patch spanning the whole arm width put a light
# course on both arms at once with linen between them, which the eye joined into a single band
# straight across the figure. That is this repo's painted-stripe failure again, arriving on a
# person. Narrowed to the OUTER two columns it reads as what it is: both elbows worn through.
SLEEVE_05 = {
    "front":  ["S6S2S7Sd", "S5S1S6Sc", "S5S1S6Sc", "T2T0S6Sc",
               "R1R0S6Sc", "R2R1S7Sd", "T3T1S7Sd",
               "S7S3S8Se", "S8S4S9Se",
               "F5F2F6Fd", "F6F3F7Fe", "F8F5F9Ff"],
    "back":   ["S7S3S8Se", "S6S2S7Sd", "S5S1S6Sc", "S7S3T1T3",
               "S7S3R0R1", "S8S4R1R2", "S8S4T2T4",
               "S8S4S9Se", "S9S5SaSe",
               "F6F3F7Fe", "F7F4F8Ff", "F9F6FaFf"],
    "right":  ["S7S4S2S4", "S6S3S1S3", "S6S3S1S3", "S7T1T0S3",
               "S7R1R0S3", "S8R2R1S4", "S8T2T1S4",
               "S8S5S3S5", "S9S6S4S6",
               "F6F3F1F3", "F8F5F3F5", "FaF7F5F7"],
    "left":   ["SdSbSaSc", "SdSbSaSc", "SeScSbSd", "SeT3T4Sd",
               "SeR3R4Sd", "SfR4R5Se", "SfT4T5Se",
               "SeScSbSd", "SfSdScSe",
               "P5P4P3P5", "P8P7P6P8", "PbPaP9Pb"],
    "top":    ["SbS7S7Sb", "S8S3S3S8", "S8S3S3S8", "ScS8S8Sc"],
    "bottom": ["PaP7P7Pa", "P7B4B1P8", "P7B2B5P8", "PaP8P8Pb"],
}

# Stockings, gartered high and out of sight under an ankle-length skirt, so what shows of her legs
# is almost nothing. That IS her silhouette, and it is what tells her from 01 and 03 at distance.
HOSE_05 = {
    "front":  ["HdHaHcHf", "HcH9HbHe", "HbH8HaHd", "HaH7H9Hc",
               "HaH6H8Hc", "HaH6H8Hc", "HbH7H9Hd",
               "HbH7H9Hd", "HcH8HaHe", "HdH9HbHe",
               "L5L1L3L7", "LaL7L9Lb"],
    "back":   ["HeHbHdHf", "HdHaHcHe", "HcH9HbHe", "HbH8HaHd",
               "HbH7H9Hd", "HbH7H9Hd", "HcH8HaHe",
               "HcH8HaHe", "HdH9HbHe", "HeHaHcHf",
               "L6L2L4L8", "LbL8LaLb"],
    "right":  ["HbHdHeHf", "HaHcHdHe", "H8HaHcHe", "H6H8HaHc",
               "H5H7H9Hc", "H5H7H9Hc", "H6H8HaHd",
               "H7H9HbHe", "H9HbHdHe", "HbHdHeHf",
               "L2L3L5L7", "L7L8LaLb"],
    "left":   ["HfHeHdHb", "HeHdHcHa", "HeHcHaH8", "HcHaH8H6",
               "HcH9H7H5", "HcH9H7H5", "HdHaH8H6",
               "HeHbH9H7", "HeHdHbH9", "HfHeHdHb",
               "L7L5L3L2", "LbLaL8L7"],
    "top":    ["HfHeHeHf", "HeHdHdHe", "HeHdHdHe", "HfHeHeHf"],
    "bottom": ["LbLaLaLb", "LaL9L9La", "LaL9L9La", "LbLaLaLb"],
}

# Eleven courses — the longest on the roster, brushing the ground, and PATCHED at the knee where
# a skirt wears through against a stool. Two columns wide, on the OUTSIDE of each knee, for the
# reason her sleeve patch is: a patch across the whole width reads as a band across the figure.
HEM_05 = {
    "front":  ["S8S4S2S6", "S9S5S3S7", "S9S5S4S8", "T1T0S4S8",
               "R1R0S5S9", "R2R1S5S9", "T2T1S6Sa",
               "SbS7S6Sa", "ScS8S6Sa", "ScS8S7Sb", "G3G1G2G5"] + [".." * 4] * 1,
    "back":   ["S9S5S3S7", "SaS6S4S8", "SaS6S5S9", "S9S5T1T2",
               "SaS6R0R1", "SaS6R1R2", "SbS7T2T3",
               "ScS8S7Sb", "SdS9S7Sb", "SdS9S8Sc", "G4G2G3G6"] + [".." * 4] * 1,
    "right":  ["S6S3S1S3", "S7S4S2S4", "S7S4S2S4", "T1T0S3S5",
               "R1R0S3S5", "R2R1S4S6", "T2T1S4S6",
               "S9S6S4S6", "S9S6S4S6", "SaS7S5S7", "G4G3G1G3"] + [".." * 4] * 1,
    "left":   ["S3S1S3S6", "S4S2S4S7", "S4S2S4S7", "S5S3T0T1",
               "S5S3R0R1", "S6S4R1R2", "S6S4T1T2",
               "S6S4S6S9", "S6S4S6S9", "S7S5S7Sa", "G3G1G3G4"] + [".." * 4] * 1,
    "top":    ["S8S5S5S8", "S5S2S2S5", "S5S2S2S5", "S9S6S6S9"],
    "bottom": [".." * 4] * 4,
}


# ── person 06: the young man, light complexion ───────────────────────
#
# EVERYTHING ABOUT HIM IS THAT THE GARMENT IS NOT HIS SIZE, which is the cheapest true thing to
# say about a young man in a household that hands clothes down. The sleeve runs ten courses over an
# arm that is twelve, so his hands barely show; the neck gapes two cells wider than anyone's; the
# hem hangs to the knee on a body whose tunic should stop at mid-thigh. And he is barefoot.

FACE_06 = [
    "F7F5F3F2F2F3F5F8",   # 0
    "F4F2F0F0F0F0F2F5",   # 1  a smooth forehead and nothing on it — that IS youth here
    "F5F2F1F0F0F1F3F6",   # 2
    "F8Q1Q0F1F1Q0Q2F9",   # 3  brows at the LIGHT end of the OCCLUSION ramp — present, not heavy.
    #                            The first version drew them two steps down the FLESH ramp and the
    #                            gate scored them 5 luminance points off the cheek against a floor
    #                            of 25: exactly the mistake the commit before this one records, made
    #                            again on the first young face. A feature is contrast, not a step.
    "F9O0I0F2F3I2O1Fa",   # 4  hazel-green, the roster's third iris — and a SHADED corner in one
    #                            sclera and a deeper step in one pupil, because he owns neither
    #                            shoes nor a girdle and those two absences forfeit 18 of the 128
    #                            palette steps. Density has to be earned where he does have detail.
    "F9FaFbF0F1Q0FdFe",   # 5  a short nose. Its lit flank is deep FLESH and its shaded one is
    #                            OCCLUSION, because his flesh ramp is narrow and two of its steps
    #                            are 25 points — under the floor. The same lesson, on the nose.
    "F8B3F3M0M1F3B2F9",   # 6  a full mouth and a high colour in the cheek — the youngest face here
    "FdF9F5F2F3F6FaFe",   # 7  and almost no jaw shadow at all
]
HEAD_RIGHT_06 = [
    "F8F6F4F3F2F1F0F3",
    "F9F7F5F3F2F0F0F2",
    "F9F7F5F4F3F1F0F3",
    "FaF8F6F5F4F2F1F4",
    "FaF9F7Q0Q1F3F2F5",
    "FbFaF8Q2FbF4F3F6",
    "FcFbF9F7F5F4F3F6",
    "FeFcFaF8F6F5F4F7",
]
HEAD_06 = {
    "front":  FACE_06,
    "top": [
        "FaF8F6F4F4F6F8Fa",
        "F8F5F3F1F1F3F5F8",
        "F6F3F1F0F0F1F3F6",
        "F5F2F0F0F0F0F2F5",
        "F5F2F0F0F0F0F2F5",
        "F6F3F1F0F0F1F3F6",
        "F8F6F3F2F2F3F6F8",
        "FaF9F7F5F5F7F9Fa",
    ],
    "back": [
        "FaF8F6F4F4F6F8Fa",
        "FaF7F5F3F3F5F7Fa",
        "FbF8F6F4F4F6F8Fb",
        "FbF9F7F5F5F7F9Fb",
        "FcFaF8F6F6F8FaFc",
        "FdFbF9F7F7F9FbFd",
        "FdFcFaF8F8FaFcFd",
        "FfFdFbF9F9FbFdFf",
    ],
    "right":  HEAD_RIGHT_06,
    "left":   flipc(HEAD_RIGHT_06),
    "bottom": [
        "Q0Q0Q1Q2Q2Q1Q0Q0",
        "Q0Q1Q2Q3Q3Q2Q1Q0",
        "Q1Q2Q3Q4Q4Q3Q2Q1",
        "Q2Q3Q5Q6Q6Q5Q3Q2",
        "Q2Q3Q5Q7Q7Q5Q3Q2",   # under the chin, and the deepest cells anywhere on him
        "Q1Q2Q3Q4Q4Q3Q2Q1",
        "Q0Q1Q2Q3Q3Q2Q1Q0",
        "Q0Q0Q1Q2Q2Q1Q0Q0",
    ],
}

# A NECK THAT GAPES. Every other body puts covered skin at cols 3-4 for a course or two; his runs
# cols 2-5 for three, because the garment is too wide at the shoulder and the whole opening has
# slipped. The folds are deeper and there are more of them, which is what too much cloth does.
SHIFT_06 = {
    "front": [
        "SaS6P4P2P3P5S7Sb",   # 0  the opening runs cols 2..5 — the widest neck on the roster
        "S8S4P2P0P1P3S5S9",   # 1
        "S7S3P3P1P2P4S4S8",   # 2  three courses of it, and it is a garment that does not fit
        "S8S2S0S2S1S3S6Sa",   # 3  and below it the folds are DEEP and close: too much cloth
        "S9S1S3S0S2S1S7Sb",   # 4
        "S9S2S0S3S1S2S6Sa",   # 5
        "SaS3S1S4S2S3S7Sb",   # 6
        "SaS4S2S5S3S4S8Sc",   # 7
        "SbS5S3S6S4S5S9Sc",   # 8
        "SbS6S4S7S5S6S9Sd",   # 9
        "ScS7S5S8S6S7SaSd",   # 10 no girdle at all — nothing holds it in, which is the point
        "SdS9S7SaS8S9SbSe",   # 11
    ],
    "back": [
        "SbS7S5S3S4S6S8Sc",
        "S9S5S3S1S2S4S6Sa",
        "S8S4S1S0S1S3S5S9",
        "S9S3S1S2S0S2S6Sa",
        "SaS2S4S1S3S1S7Sb",
        "SaS3S1S4S2S3S6Sa",
        "SbS4S2S5S3S4S7Sb",
        "SbS5S3S6S4S5S8Sc",
        "ScS6S4S7S5S6S9Sc",
        "ScS7S5S8S6S7S9Sd",
        "SdS8S6S9S7S8SaSd",
        "SeSaS8SbS9SaScSe",
    ],
    # THE SIDE SEAM IS A `T` RUN and the shoulder is abraded to `R`, and both are here because the
    # gate counted: at 87 distinct colours he was under the floor of 100 and his torso under its
    # own floor of 22. A garment made of one material in one ramp is a tube, and it measures like
    # one — the seam and the wear are what make it a made thing, and they cost two materials.
    "right":  ["SaT0P3Sb", "S8T1P1S9", "S7T2P2S8", "S8T3S3S9",
               "S9T4S1Sa", "S9T5S2Sa", "SaS4S3Sb", "SaS5S4Sb",
               "SbS6S5Sc", "SbS7S6Sd", "ScS8S7Se", "SdS9S8Sf"],
    "left":   ["SbP3T0Sa", "S9P1T1S8", "S8P2T2S7", "S9S3T3S8",
               "SaS1T4S9", "SaS2T5S9", "SbS3S4Sa", "SbS4S5Sa",
               "ScS5S6Sb", "SdS6S7Sb", "SeS7S8Sc", "SfS8S9Sd"],
    "top":    ["SbS7S4S2S2S4S7Sb", "R0R2R4S0S0R5R3R1",
               "T1S5S2S0S1S3S6T0", "SbS7S4S2S3S5S8Sc"],
    "bottom": ["SfSeSeSdSdSeSeSf", "SeScSbSaSaSbScSe",
               "ScSbSaS9S9SaSbSc", "SdScSbSaSaSbScSd"],
}

# TEN COURSES OF SLEEVE over an arm of twelve, so all that shows of his hand is the fingers. That
# is one drawn fact doing the whole job of "it is not his".
SLEEVE_06 = {
    "front":  ["S6S2S7Sd", "S5S1S6Sc", "S6S2S7Sd", "S5S1S6Sc",
               "S6S2S7Sd", "S6S2S7Sd", "S7S3S8Se", "S7S3S8Se",
               "S8S4S9Se", "S9S5SaSf",
               "F5F2F7Fd", "F9F6FaFf"],
    "back":   ["S7S3S8Se", "S6S2S7Sd", "S5S1S6Sc", "S6S2S7Sd",
               "S7S3S8Se", "S7S3S8Se", "S8S4S9Se", "S8S4S9Se",
               "S9S5SaSf", "SaS6SbSf",
               "F6F3F7Fe", "F9F6FaFf"],
    # THE ELBOW IS WORN THROUGH. A shift two sizes too big bunches at the elbow and the cloth goes
    # first there, so the outside of the sleeve carries the abraded `R` for three courses — and it
    # is the one thing on him that uses that ramp below the shoulder. Kept off cols 0 and 3, which
    # are the box's own wrap columns: a lighter thread there is a line down his outline.
    "right":  ["S7S4S2S4", "S6S3S1S3", "S7S4S2S4", "S6S3S1S3",
               "S7R2R0S4", "S7R3R1S4", "S8R4R2S5", "S8S5S3S5",
               "S9S6S4S6", "SaS7S5S7",
               "F7F4F1F4", "FbF8F5F8"],
    "left":   ["SdSbSaSc", "ScSaS9Sb", "SdSbSaSc", "ScSaS9Sb",
               "SdSbSaSc", "SdSbSaSc", "SeScSbSd", "SeScSbSd",
               "SfSdScSe", "SfSdScSe",
               "P4P3P2P4", "PbPaP9Pb"],
    "top":    ["S9S5S5S9", "S6S1S1S6", "S6S1S1S6", "SaS6S6Sa"],
    "bottom": ["P8P5P5P8", "P5B1B3P6", "P5B2B0P6", "P8P6P6P9"],
}

# BARE FEET. The last two courses are the top of the foot and the sole in the pale covered-skin
# ramp, which is where a foot's colour lives — a foot is not tanned. There is no leather on him.
HOSE_06 = {
    "front":  ["HdH9HbHf", "HbH8HaHd", "H9H6H8Hb", "H8H4H6Ha",
               "H6H0H3H9", "H8H4H6Ha", "U3U1U2U5",
               "H8H4H6Ha", "H9H5H7Hb", "HbH7H9Hd",
               "P6P3P4P8", "PaP7P8Pb"],
    "back":   ["HdHaHcHe", "HcH9HbHe", "HaH7H9Hc", "H9H5H7Hb",
               "H8H4H6Ha", "H9H5H7Hb", "U4U2U3U6",
               "H9H5H7Hb", "HaH6H8Hc", "HcH8HaHe",
               "P7P4P5P9", "PbP8P9Pb"],
    "right":  ["HaHcHdHe", "H8HaHcHd", "H5H7H9Hc", "H2H4H7Ha",
               "H1H3H6H9", "H2H4H7Ha", "U1U2U4U6",
               "H4H6H8Hb", "H6H8HaHd", "H8HaHcHe",
               "P4P5P7P9", "P9PaPbPb"],
    "left":   ["HeHdHcHa", "HdHcHaH8", "HcH9H7H5", "HaH7H4H2",
               "H9H6H3H1", "HaH7H4H2", "U6U4U2U1",
               "HbH8H6H4", "HdHaH8H6", "HeHcHaH8",
               "P9P7P5P4", "PbPbPaP9"],
    "top":    ["HeHdHdHe", "HdHcHcHd", "HdHcHcHd", "HeHdHdHe"],
    # THE SOLE OF A BARE FOOT, and it takes the warm accent where a foot bears weight: the heel and
    # the pads, exactly as a palm does. It is the only place on the roster `B` is used below a face.
    "bottom": ["PbP9P9Pb", "P9B5B4Pa", "P9B4B5Pa", "PbPaPaPb"],
}

# Seven courses, which is the longest tunic any man here wears and it is not a choice: it is a
# garment cut for someone else.
HEM_06 = {
    "front":  ["S7S3S1S5", "S8S4S2S6", "S8S4S3S7", "S9S5S3S7",
               "S9S5S4S8", "SaS6S4S8", "G3G0G2G7"] + [".." * 4] * 5,
    "back":   ["S8S4S2S6", "S9S5S3S7", "S9S5S4S8", "SaS6S4S8",
               "SaS6S5S9", "SbS7S5S9", "G4G1G3G6"] + [".." * 4] * 5,
    # The shift's side seam runs on down the hem, because it is the same seam and a garment does not
    # stop being made below the waist.
    "right":  ["T0S3S1S3", "T1S4S2S4", "T2S4S2S4", "T3S5S3S5",
               "T4S5S3S5", "T5S6S4S6", "G4G3G1G3"] + [".." * 4] * 5,
    "left":   ["S3S1S3T0", "S4S2S4T1", "S4S2S4T2", "S5S3S5T3",
               "S5S3S5T4", "S6S4S6T5", "G3G1G3G4"] + [".." * 4] * 5,
    "top":    ["S6S3S3S6", "S3S0S0S3", "S3S0S0S3", "S7S4S4S7"],
    "bottom": [".." * 4] * 4,
}


# ── person 07: the woman in her prime, dark complexion ───────────────

FACE_07 = [
    "FaF8F6F5F5F6F8Fb",   # 0
    "F7F4F2F1F1F2F4F8",   # 1
    "F8F5F3F2F2F3F5F9",   # 2
    "FbQ0Q1F4F4Q1Q0Fc",   # 3  an even, level brow: hers is the most symmetric face on the roster,
    "FcO0I1F3F5I2O2Fd",   # 4   which is its own kind of difference when nobody else's is
    "FcFdQ0F0F1FeFfFf",   # 5  a straight narrow nose, its shadow on her right
    "FaB4F4M0M1F5B4Fb",   # 6
    "Q1FbF7F4F5F8FbQ2",   # 7
]
HEAD_RIGHT_07 = [
    "FbF9F7F6F5F4F3F6",
    "FbF9F7F5F4F2F1F4",
    "FcFaF8F6F5F3F2F5",
    "FcFaF8F7F6F4F3F6",
    "FdFbF9Q0Q1F5F4F7",
    "FdFcFaQ2FdF6F5F8",
    "FeFdFbF9F7F6F5F8",
    "Q1FeFdFbF9F8F7Fa",
]
HEAD_07 = {
    "front":  FACE_07,
    "top": [
        "FdFbF9F7F7F9FbFd",
        "FbF8F6F4F4F6F8Fb",
        "F9F6F3F2F2F3F6F9",
        "F8F4F2F1F1F2F5F8",
        "F8F4F1F1F1F2F4F8",
        "F9F6F3F2F2F3F6F9",
        "FbF9F6F5F5F6F9Fb",
        "FdFcFaF8F8FaFcFd",
    ],
    "back": [
        "FcFaF8F6F6F8FaFc",
        "FcFaF8F6F6F8FaFc",
        "FdFbF9F7F7F9FbFd",
        "FdFbF9F8F8F9FbFd",
        "FeFcFaF9F9FaFcFe",
        "FfFdFbFaFaFbFdFf",
        "Q0FeFcFbFbFcFeQ0",
        "Q2Q0FeFdFdFeQ0Q2",
    ],
    "right":  HEAD_RIGHT_07,
    "left":   flipc(HEAD_RIGHT_07),
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

# THE WOVEN GIRDLE, and it is the whole point of her. Two courses of madder-dyed band whose steps
# ALTERNATE along the row rather than ramp across it, which is what reads as tablet weaving at this
# scale — the same device `make_female_skins` uses for a plait, and for the same reason: a straight
# light run is a stripe and a zigzag is a weave.
SHIFT_07 = {
    "front": [
        "SdSaS6P4P5S7SbSe",   # 0
        "SbS8S5P2P3S5S9Sc",   # 1
        "SaS6S3S1S2S4S8Sb",   # 2
        "SaS5S2S0S1S3S7Sb",   # 3
        "SbS6S3S1S2S4S8Sc",   # 4
        "SbS7S3S2S3S5S8Sc",   # 5
        "K0K3K1K4K1K3K0K2",   # 6  THE BAND: alternating, not ramped, and it sits at the waist
        "K3K0K4K1K4K0K3K1",   # 7   with the two courses out of phase, which is the weave
        "ScS8S5S3S4S6SaSd",   # 8
        "ScS9S6S4S5S7SaSd",   # 9
        "SdS9S6S5S6S8SbSe",   # 10
        "SeSbS8S7S8SaSdSf",   # 11
    ],
    "back": [
        "SeSbS7S5S6S8SbSf",
        "ScS9S6S4S5S7SaSd",
        "SbS7S4S2S3S5S9Sc",
        "SaS6S3S2S3S4S8Sb",
        "T1T0S4S3S4S5S9Sc",   # a shoulder seam across, not down — hers is a two-panel shift
        "SbS7S4S3S4S6S9Sc",
        "K1K4K2K0K2K4K1K3",
        "K4K1K0K2K0K1K4K2",
        "ScS9S6S4S5S7SaSd",
        "SdS9S6S5S6S8SbSe",
        "SdSaS7S6S7S9SbSe",
        "SfScS9S8S9SbSeSf",
    ],
    "right":  ["ScT0S6Sd", "SaT1S4Sb", "S9T2S2Sa", "S9T3S1S9",
               "SaT4S2Sa", "SbT5S3Sb", "K1K3K0K2", "K4K0K3K1",
               "ScS8S5Sc", "ScS8S5Sd", "SdS9S6Se", "SfSbS8Sf"],
    "left":   ["SdS6T0Sc", "SbS4T1Sa", "SaS2T2S9", "S9S1T3S9",
               "SaS2T4Sa", "SbS3T5Sb", "K2K0K3K1", "K1K3K0K4",
               "ScS5S8Sc", "SdS5S8Sc", "SeS6S9Sd", "SfS8SbSf"],
    "top":    ["SdSaS7S5S5S7SaSd", "R0R2R4S1S1R5R3R1",
               "SbS7S4S2S2S4S7Sb", "ScS8S5S3S4S6S9Sd"],
    "bottom": ["SfSeSeSdSdSeSeSf", "SeSeSdScScSdSeSe",
               "SeSdScSbSbScSdSe", "SfSeSdScScSdSeSf"],
}

# Sleeves to the wrist, gathered at the cuff with a narrow run of the same woven band — one course,
# because a band at the cuff is a scrap of the girdle's warp and nobody weaves two.
SLEEVE_07 = {
    "front":  ["S6S2S7Sd", "S5S1S6Sc", "S5S1S6Sc", "S6S2S7Sd",
               "S6S2S7Sd", "S7S3S8Se", "S7S3S8Se", "S8S4S9Se",
               "K1K3K0K2",
               "F5F2F6Fd", "F6F3F7Fe", "F8F5F9Ff"],
    "back":   ["S7S3S8Se", "S6S2S7Sd", "S5S1S6Sc", "S5S1S6Sc",
               "S6S2S7Sd", "S7S3S8Se", "S8S4S9Se", "S8S4S9Se",
               "K3K0K2K4",
               "F6F3F7Fe", "F7F4F8Ff", "F9F6FaFf"],
    "right":  ["S7S4S2S4", "S6S3S1S3", "S6S3S1S3", "S7S4S2S4",
               "S7S4S2S4", "S8S5S3S5", "S8S5S3S5", "S9S6S4S6",
               "K0K2K1K3",
               "F6F3F1F3", "F8F5F3F5", "FaF7F5F7"],
    "left":   ["SdSbSaSc", "SdSbSaSc", "SeScSbSd", "SeScSbSd",
               "SeScSbSd", "SfSdScSe", "SfSdScSe", "SfSdScSe",
               "K3K1K2K4",
               "P5P4P3P5", "P8P7P6P8", "PbPaP9Pb"],
    "top":    ["SaS6S6Sa", "S7S2S2S7", "S7S2S2S7", "SbS7S7Sb"],
    "bottom": ["P9P6P6P9", "P1B3B0P7", "P0B5B1P7", "P9P7P7Pa"],
}

# Stockings in a moorit wool, and a shoe. Nothing on them at all — the girdle is where her cloth
# money went and a woman who owns a woven band does not walk through a midden.
HOSE_07 = {
    "front":  ["HdHaHcHf", "HcH8HaHe", "HaH5H8Hc", "H8H3H6Hb",
               "H6H1H4H9", "H6H1H4H9", "H8H2H5Ha",
               "HaH4H7Hc", "HcH6H9Hd", "HdH8HbHe",
               "L4L0L2L6", "L9L6L8Lb"],
    "back":   ["HeHbHdHf", "HdH9HbHe", "HbH6H9Hd", "H9H4H7Hc",
               "H7H2H5Ha", "H7H2H5Ha", "H9H3H6Hb",
               "HbH5H8Hd", "HdH7HaHe", "HeH9HcHf",
               "L5L1L3L7", "LaL7L9Lb"],
    "right":  ["HbHdHeHf", "H8HaHcHe", "H5H7H9Hc", "H2H4H7Ha",
               "H0H2H5H8", "H0H2H5H8", "H1H3H6H9",
               "H4H6H8Hb", "H7H9HbHe", "HaHcHeHf",
               "L2L3L5L8", "L7L8LaLb"],
    "left":   ["HfHeHdHb", "HeHcHaH8", "HcH9H7H5", "HaH7H4H2",
               "H8H5H2H0", "H8H5H2H0", "H9H6H3H1",
               "HbH8H6H4", "HeHbH9H7", "HfHeHcHa",
               "L8L5L3L2", "LbLaL8L7"],
    "top":    ["HfHeHeHf", "HeHdHdHe", "HeHdHdHe", "HfHeHeHf"],
    "bottom": ["LbLaLaLb", "LaL9L9La", "LaL8L8La", "LbLaLaLb"],
}

# Eight courses, the shortest skirt on the roster: hitched and tucked into the girdle, which is
# what a band at the waist is FOR. The tuck is why she is the only woman whose shin shows.
HEM_07 = {
    "front":  ["S8S4S2S6", "S9S5S3S7", "S9S5S4S8", "SaS6S4S8",
               "SaS6S5S9", "SbS7S5S9", "K1K3K0K2", "ScS8S6Sa"] + [".." * 4] * 4,
    "back":   ["S9S5S3S7", "SaS6S4S8", "SaS6S5S9", "SbS7S5S9",
               "SbS7S6Sa", "ScS8S6Sa", "K3K0K2K4", "SdS9S7Sb"] + [".." * 4] * 4,
    "right":  ["S6S3S1S3", "S7S4S2S4", "S7S4S2S4", "S8S5S3S5",
               "S8S5S3S5", "S9S6S4S6", "K0K2K1K3", "SaS7S5S7"] + [".." * 4] * 4,
    # The inner face of the skirt, and it is the deep end of her ramp: a skirt shadows itself
    # between the legs, which is where the last steps of `S` are actually earned.
    "left":   ["SaS8SaSd", "SbS9SbSe", "SbS9SbSe", "ScSaScSf",
               "ScSaScSf", "SdSbSdSf", "K3K1K2K0", "SeScSeSf"] + [".." * 4] * 4,
    "top":    ["S7S4S4S7", "S4S1S1S4", "S4S1S1S4", "S8S5S5S8"],
    "bottom": [".." * 4] * 4,
}


# ── person 08: the heavy man ─────────────────────────────────────────
#
# MORE CLOTH IS TWO FOLD COLUMNS, NOT A DARKER RAMP. Every other torso here drapes with one lit
# column and a jitter; his has two, at cols 2 and 5, with the shadow between them — which is what a
# shift does when there is enough of it to gather twice. And the waist is LEATHER: the only belt on
# the roster, against six cords and two cloth wraps.

# HIS FACE HAD TO BE REDRAWN, and the reason is worth recording. The first version was 00's block
# with every step two deeper, and his palette sits sixteen luminance points above 00's — so the two
# cancelled and the pair measured 5.6 points per differing cell against a floor of 7. A palette
# offset and an art offset in opposite directions produce the same face. So this one is authored
# against him rather than against 00: a low forehead, the DEEP end of the occlusion ramp in the
# brow where 00 uses its light end, and four blush cells running down into a second chin.
FACE_08 = [
    "FeFcFaF9F9FaFcFf",   # 0
    "FbF8F6F5F5F6F9Fc",   # 1  the lowest forehead on the roster — two courses, not three
    "FcF9F7F6F6F7FaFd",   # 2
    "FaQ3Q2F3F3Q2Q3Fb",   # 3  a heavy low brow out of the DEEP end of Q, where 00 takes the light
    "FbO0I1F2F4I2O0Fc",   # 4
    "FcFaFbF0F1Q1FcFd",   # 5  a short broad nose
    "F9B5B4M1M2B4B5Fa",   # 6  FOUR blush cells — the fullest cheek here, and nobody else has four
    "Q0FaB5F5F6B5FaQ1",   # 7  and it runs down into row 7: a second chin, not a jawline
]
HEAD_RIGHT_08 = [
    "FeFcFaF9F8F7F6F9",
    "FeFcFaF8F7F5F4F7",
    "FfFdFbF9F8F6F5F8",
    "FfFdFbFaF9F7F6F9",
    "Q0FeFcQ1Q2F8F7Fa",
    "Q0FfFdQ3FfF9F8Fb",
    "Q1Q0FeFcFaF9F8Fb",
    "Q2Q1FfFdFbFaF9Fc",
]
HEAD_08 = {
    "front":  FACE_08,
    "top": ["FfFdFbFaFaFbFdFf", "FdFaF8F6F6F8FaFd", "FbF8F5F4F4F5F8Fb",
            "FaF6F3F2F2F4F7Fa", "FaF6F3F2F2F3F6Fa", "FbF8F5F4F4F5F8Fb",
            "FdFbF8F7F7F8FbFd", "FfFeFcFbFbFcFeFf"],
    "back": ["FeFcFaF9F9FaFcFe", "FeFcFaF8F8FaFcFe", "FfFdFbFaFaFbFdFf",
             "Q0FeFcFbFbFcFeQ0", "Q0FfFdFcFcFdFfQ0", "Q1Q0FeFdFdFeQ0Q1",
             "Q2Q1FfFeFeFfQ1Q2", "Q3Q2Q0FfFfQ0Q2Q3"],
    "right":  HEAD_RIGHT_08,
    "left":   flipc(HEAD_RIGHT_08),
    "bottom": ["Q3Q4Q4Q5Q5Q4Q4Q3", "Q4Q5Q5Q6Q6Q5Q5Q4", "Q4Q5Q6Q7Q7Q6Q5Q4",
               "Q5Q6Q7Q7Q7Q7Q6Q5", "Q5Q6Q7Q7Q7Q7Q6Q5", "Q4Q5Q6Q7Q7Q6Q5Q4",
               "Q4Q5Q5Q6Q6Q5Q5Q4", "Q3Q4Q4Q5Q5Q4Q4Q3"],
}
SHIFT_08 = {
    "front": [
        "SeSbS7P5P6S8ScSf",   # 0
        "ScS9S5P3P4S6SaSd",   # 1
        "SbS7S2S5S5S2S8Sc",   # 2  TWO fold columns — 2 and 5 lit, the middle in shadow between
        "SbS6S1S4S4S1S7Sb",   # 3
        "ScS7S2S5S5S2S8Sc",   # 4
        "ScS8S2S5S5S3S9Sd",   # 5
        "SdS9S3S6S6S3SaSd",   # 6
        "L2L0L1L2L3L3L4L5",   # 7  A LEATHER BELT, two courses, and the buckle side is lit
        "L4L2L3L4L5L5L6L7",   # 8
        "SeSbS5S8S8G1ScSf",   # 9
        "SfScS7S9S9S7SdSf",   # 10
        "SfSdS9SbSbS9SeSf",   # 11
    ],
    "back": [
        "SfScS8S6S7S9SdSf",
        "SdSaS6S4S5S7SbSe",
        "ScS8S3S6S6S3S9Sd",
        "T1T0S2S5S5S2S8Sc",
        "ScS8S3S6S6S3S9Sd",
        "SdS9S3S6S6S4SaSe",
        "SdSaS4S7S7S4SbSe",
        "L3L1L2L3L4L4L5L6",
        "L5L3L4L5L6L6L7L8",
        "SfScS6S9S9G2SdSf",
        "SfSdS8SaSaS8SeSf",
        "SfSeSaScScSaSfSf",
    ],
    "right":  ["SeSaS7Sf", "ScS8S5Sd", "SaS3S6Sb", "S9S1S5Sa",
               "SaS3S6Sb", "SbS3S6Sc", "ScS4S7Sd", "L2L0L1L3",
               "L4L2L3L5", "SeS6S9Sf", "SfS8SbSf", "SfSaSdSf"],
    "left":   ["SfS7SaSe", "SdS5S8Sc", "SbS6S3Sa", "SaS5S1S9",
               "SbS6S3Sa", "ScS6S3Sb", "SdS7S4Sc", "L3L1L0L2",
               "L5L3L2L4", "SfS9S6Se", "SfSbS8Sf", "SfSdSaSf"],
    "top":    ["SfSbS7S5S5S7SbSf", "R1R3R5S2S2R5R3R1",
               "T1S6S2S5S5S2S6T0", "ScS8S4S6S6S4S9Sd"],
    "bottom": ["SfSfSeSeSeSeSfSf", "SfSeSdScScSdSeSf",
               "SeSdScSbSbScSdSe", "SfSeSdScScSdSeSf"],
}
SLEEVE_08 = {
    "front":  ["S5S1S6Sc", "S5S1S6Sc", "S4S0S5Sb", "S5S1S6Sc", "S6S2S7Sd",
               "T2T0T3T5",
               "F4F1F5Fc", "F4F1F5Fc", "F5F2F6Fd", "F6F3F7Fe", "F8F5F9Ff", "FaF7FbFf"],
    "back":   ["S6S2S7Sd", "S5S1S6Sc", "S5S1S6Sc", "S4S0S5Sb", "S6S2S7Sd",
               "T3T1T4T5",
               "F5F2F6Fd", "F4F1F5Fc", "F5F2F6Fd", "F7F4F8Fe", "F9F6FaFf", "FbF8FcFf"],
    "right":  ["R1R0S2S4", "R3R2S0S2", "S5S2S1S3", "S6S3S1S3", "S7S4S2S4",
               "T2T1T0T2",
               "F5F2F1F3", "F6F3F1F3", "F7F4F2F4", "F8F5F3F5", "FaF7F5F7", "FcF9F7F9"],
    "left":   ["ScSaS9Sb", "ScSaS9Sb", "SdSbSaSc", "SdSbSaSc", "SeScSbSd",
               "T5T4T3T5",
               "P4P3P2P4", "P6P5P4P6", "P8P7P6P8", "P9P8P7P9", "PaP9P8Pa", "PbPaP9Pb"],
    "top":    ["SbS7S7Sb", "S7S2S2S7", "S7S2S2S7", "ScS8S8Sc"],
    "bottom": ["PaP7P7Pa", "P7B5B1P8", "P7B4B5P8", "PaP8P8Pb"],
}
HOSE_08 = {
    "front":  ["HdHaHcHf", "HcH8HaHe", "HaH5H8Hc", "H8H2H5Ha", "H6H1H3H8",
               "H7U0U2H9", "U4U1U3Ha",
               "H8H3H6Ha", "H9H4H7Hb", "HbH6H9Hd", "L5L1L3L7", "LaL7L9Lb"],
    "back":   ["HeHbHdHf", "HdH9HbHe", "HbH6H9Hd", "H9H3H6Hb", "H7H1H4H9",
               "H8H2H5Ha", "U5U2U4Hb",
               "H9H4H7Hb", "HaH5H8Hc", "HcH7HaHe", "L6L2L4L8", "LbL8LaLb"],
    "right":  ["HcHdHeHf", "HaHbHdHe", "H7H9HbHd", "H4H6H9Hb", "H1H3H6H9",
               "U1U2U4U6", "U2U3U5U7",
               "H5H6H8Ha", "H6H7H9Hb", "H8H9HbHd", "L2L3L5L7", "L7L8LaLb"],
    "left":   ["HfHeHdHc", "HeHdHbHa", "HdHbH9H7", "HbH9H6H4", "H9H6H3H1",
               "U6U4U2U1", "U7U5U3U2",
               "HaH8H6H5", "HbH9H7H6", "HdHbH9H8", "L7L5L3L2", "LbLaL8L7"],
    "top":    ["HfHeHeHf", "HeHdHdHe", "HeHdHdHe", "HfHeHeHf"],
    "bottom": ["LbLaLaLb", "LaL9L9La", "LaL9L9La", "LbLaLaLb"],
}
HEM_08 = {
    "front":  ["S8S2S6Sa", "S9S3S7Sb", "SaS4S8Sc", "SbS5S9Sd", "ScS6SaSe",
               "G3G0G2G5"] + [".." * 4] * 6,
    "back":   ["S9S3S7Sb", "SaS4S8Sc", "SbS5S9Sd", "ScS6SaSe", "SdS7SbSf",
               "G4G1G3G6"] + [".." * 4] * 6,
    "right":  ["T0S3S1S5", "T1S4S2S6", "T2S5S3S7", "T3S6S4S8", "T4S7S5S9",
               "G4G3G1G3"] + [".." * 4] * 6,
    "left":   ["S5S1S3T0", "S6S2S4T1", "S7S3S5T2", "S8S4S6T3", "S9S5S7T4",
               "G3G1G3G4"] + [".." * 4] * 6,
    "top":    ["S8S4S4S8", "S5S1S1S5", "S5S1S1S5", "S9S6S6S9"],
    "bottom": [".." * 4] * 4,
}


# ── person 09: the young woman ───────────────────────────────────────
#
# CAP SLEEVES: three courses and then bare arm to the wrist. Of the seven women, 01, 05, 07 and 11
# run to the wrist, 03 to the elbow, and hers stop at the shoulder — which is the one axis on this
# rig that reads at any distance, since the hem tells sex and the sleeve tells the person.

FACE_09 = [
    "FbF9F7F6F6F7F9Fc",   # 0
    "F8F5F2F1F1F3F6F9",   # 1
    "F9F6F3F2F2F4F7Fa",   # 2
    "FcQ0Q1F3F3Q1Q0Fd",   # 3  a fine even brow, the lightest occlusion on the roster
    "FdO0I0F3F4I1O0Fe",   # 4  a fourth iris colour: green-grey
    "FdFbFcF0F1Q0FdFe",   # 5
    "FbB2F3M0M1F4B3Fc",   # 6  the LIGHT end of the blush, which nobody else uses — she is young
    "Q0FbF7F4F5F8FbQ1",   # 7
]
HEAD_RIGHT_09 = [
    "FcFaF8F7F6F5F4F7",
    "FcFaF8F6F5F3F2F5",
    "FdFbF9F7F6F4F3F6",
    "FdFbF9F8F7F5F4F7",
    "FeFcFaQ0Q1F6F5F8",
    "FeFdFbQ2FeF7F6F9",
    "FfFeFcFaF8F7F6F9",
    "Q1FfFdFbF9F8F7Fa",
]
HEAD_09 = {
    "front":  FACE_09,
    "top": ["FdFbF9F7F7F9FbFd", "FbF8F5F3F3F5F8Fb", "F9F5F2F1F1F2F5F9",
            "F8F4F1F0F0F1F4F8", "F8F4F1F0F0F1F4F8", "F9F6F3F1F1F3F6F9",
            "FbF9F6F4F4F6F9Fb", "FdFcFaF8F8FaFcFd"],
    "back": ["FcFaF8F6F6F8FaFc", "FcF9F7F5F5F7F9Fc", "FdFaF8F6F6F8FaFd",
             "FdFbF9F7F7F9FbFd", "FeFcFaF8F8FaFcFe", "FfFdFbF9F9FbFdFf",
             "FfFeFcFbFbFcFeFf", "Q1FfFeFdFdFeFfQ1"],
    "right":  HEAD_RIGHT_09,
    "left":   flipc(HEAD_RIGHT_09),
    "bottom": ["Q0Q1Q1Q2Q2Q1Q1Q0", "Q1Q2Q2Q3Q3Q2Q2Q1", "Q2Q2Q3Q4Q4Q3Q2Q2",
               "Q2Q3Q4Q5Q5Q4Q3Q2", "Q2Q3Q4Q5Q5Q4Q3Q2", "Q2Q2Q3Q4Q4Q3Q2Q2",
               "Q1Q2Q2Q3Q3Q2Q2Q1", "Q0Q1Q1Q2Q2Q1Q1Q0"],
}
SHIFT_09 = {
    "front": [
        "ScS8S4P3P4S5S9Sd",   # 0
        "SaK1K0P1P2K0K1Sb",   # 1  a drawstring that runs right across, not two lace holes
        "S9S5S2S0S1S2S6Sa",   # 2
        "S9S4S1S0S1S3S6Sa",   # 3
        "SaS5S2S1S2S3S7Sb",   # 4
        "SaS6S2S1S2S4S7Sb",   # 5
        "SbS6S3S2S3S4S8Sc",   # 6
        "SbS7S3S2S3S5S8Sc",   # 7
        "K2K1K2K3K3K4K4K5",   # 8  a woven cord, tied once
        "ScS8S5S4S5S6SaSd",   # 9
        "ScS9S6S5S6S7SaSe",   # 10 nothing dirty on her anywhere above the hem
        "SdSaS7S6S7S9ScSe",   # 11
    ],
    "back": [
        "SdS9S5S4S5S7SaSe",
        "SbS7S4S3S4S6S9Sc",
        "SaS6S3S2S3S5S8Sb",
        "T1T0S2S1S2S4S7Sb",
        "SaS6S3S2S3S5S8Sc",
        "SbS7S4S3S4S6S9Sc",
        "SbS7S4S3S4S6S9Sd",
        "ScS8S5S4S5S7SaSd",
        "K3K2K3K4K4K5K5K5",
        "ScS9S6S5S6S8SbSe",
        "SdSaS7S6S7S9SbSe",
        "SeSbS8S7S8SaSdSf",
    ],
    "right":  ["SbS7S5Sc", "SaK0K1Sb", "S9S4S2Sa", "S9S3S1S9",
               "SaS4S2Sa", "SaS5S3Sb", "SbS5S3Sb", "ScS6S4Sc",
               "K2K1K2K4", "ScS7S5Sd", "SdS8S6Se", "SeS9S7Sf"],
    "left":   ["ScS5S7Sb", "SbK1K0Sa", "SaS2S4S9", "S9S1S3S9",
               "SaS2S4Sa", "SbS3S5Sa", "SbS3S5Sb", "ScS4S6Sc",
               "K4K2K1K2", "SdS5S7Sc", "SeS6S8Sd", "SfS7S9Se"],
    "top":    ["ScS8S5S3S3S5S8Sc", "R0R2R4S0S0R5R3R1",
               "T1S4S1S0S0S1S4T0", "SaS6S3S1S2S4S7Sb"],
    "bottom": ["SeSdSdScScSdSdSe", "SdSdScSbSbScSdSd",
               "SdScSbSaSaSbScSd", "SeSdScSbSbScSdSe"],
}
SLEEVE_09 = {
    # THREE COURSES OF CLOTH and a cord at the edge of them, then nine of bare arm. The cord is
    # where a cap sleeve is gathered and it is the only thing holding it on.
    "front":  ["S5S1S6Sc", "S5S1S6Sc", "K1K0K2K4",
               "P3P1P4Pa", "P4P2P5Pa", "F4F1F5Fc", "F4F1F5Fc", "F5F2F6Fd",
               "F5F2F6Fd", "F7F4F8Fe", "F8F5F9Ff", "FaF7FbFf"],
    "back":   ["S6S2S7Sd", "S5S1S6Sc", "K2K1K3K4",
               "P4P2P5Pa", "P5P3P6Pb", "F5F2F6Fd", "F4F1F5Fc", "F5F2F6Fd",
               "F6F3F7Fe", "F8F5F9Ff", "F9F6FaFf", "FbF8FcFf"],
    "right":  ["R1R0S2S4", "R3R2S0S2", "K1K0K1K3",
               "P4P2P1P3", "P5P3P2P4", "F5F2F1F3", "F6F3F1F3", "F6F3F2F4",
               "F7F4F2F4", "F9F6F4F6", "FaF7F5F7", "FcF9F7F9"],
    "left":   ["ScSaS9Sb", "ScSaS9Sb", "K4K3K2K4",
               "P7P6P5P7", "P8P7P6P8", "P8P7P6P8", "P9P8P7P9", "P9P8P7P9",
               "PaP9P8Pa", "PaP9P8Pa", "PbPaP9Pb", "PbPbPaPb"],
    "top":    ["S9S5S5S9", "S6S1S1S6", "S6S1S1S6", "SaS6S6Sa"],
    "bottom": ["P9P6P6P9", "P6B1B4P7", "P6B3B0P7", "P9P7P7Pa"],
}
HOSE_09 = {
    "front":  ["HdHaHcHf", "HcH9HbHe", "HbH7H9Hd", "HaH6H8Hc", "H9H5H7Hb",
               "K1K0K2K3", "H8H4H6Ha", "H9H5H7Hb", "HaH6H8Hc", "HbH7H9Hd",
               "L4L0L2L6", "L9L6L8Lb"],
    "back":   ["HeHbHdHf", "HdHaHcHe", "HcH8HaHd", "HbH7H9Hc", "HaH6H8Hb",
               "K2K1K3K4", "H9H5H7Hb", "HaH6H8Hc", "HbH7H9Hd", "HcH8HaHe",
               "L5L1L3L7", "LaL7L9Lb"],
    "right":  ["HbHdHeHf", "H9HbHdHe", "H7H9HbHd", "H5H7H9Hc", "H3H5H7Hb",
               "K0K1K2K3", "H4H6H8Hb", "H6H8HaHd", "H8HaHcHe", "HaHcHeHf",
               "L2L3L5L7", "L7L8LaLb"],
    "left":   ["HfHeHdHb", "HeHdHbH9", "HdHbH9H7", "HcH9H7H5", "HbH7H5H3",
               "K3K2K1K0", "HbH8H6H4", "HdHaH8H6", "HeHcHaH8", "HfHeHcHa",
               "L7L5L3L2", "LbLaL8L7"],
    "top":    ["HfHeHeHf", "HeHdHdHe", "HeHdHdHe", "HfHeHeHf"],
    "bottom": ["LbLaLaLb", "LaL9L9La", "LaL9L9La", "LbLaLaLb"],
}
HEM_09 = {
    "front":  ["S7S3S1S5", "S8S4S2S6", "S8S4S3S7", "S9S5S3S7", "S9S5S4S8",
               "SaS6S4S8", "SaS6S5S9", "SbS7S5S9", "SbS7S6Sa",
               "G2G0G1G4"] + [".." * 4] * 2,
    "back":   ["S8S4S2S6", "S9S5S3S7", "S9S5S4S8", "SaS6S4S8", "SaS6S5S9",
               "SbS7S5S9", "SbS7S6Sa", "ScS8S6Sa", "ScS8S7Sb",
               "G3G1G2G5"] + [".." * 4] * 2,
    "right":  ["T0S3S1S3", "T1S4S2S4", "T2S4S2S4", "T3S5S3S5", "T4S5S3S5",
               "T5S6S4S6", "S8S6S4S6", "S9S7S5S7", "S9S7S5S7",
               "G3G2G1G3"] + [".." * 4] * 2,
    "left":   ["S3S1S3T0", "S4S2S4T1", "S4S2S4T2", "S5S3S5T3", "S5S3S5T4",
               "S6S4S6T5", "S6S4S6S8", "S7S5S7S9", "S7S5S7S9",
               "G3G1G2G3"] + [".." * 4] * 2,
    "top":    ["S6S3S3S6", "S3S1S1S3", "S3S1S1S3", "S7S4S4S7"],
    "bottom": [".." * 4] * 4,
}


# ── person 10: the young man with a tan line ─────────────────────────
#
# THE ONE PLACE THE TWO FLESH RAMPS ARE PUT SIDE BY SIDE ON PURPOSE. `F` is skin the sun has had and
# `P` is skin it has not; every other body uses `P` for the inside of a limb, where the transition is
# hidden. His cuff sits at course 5 and the change happens in ONE course with no blend, because that
# is what a tan line is — and his `P` runs higher than anyone's so the step is worth drawing.

FACE_10 = [
    "F9F7F5F4F4F5F7Fa",   # 0
    "F6F3F1F0F0F1F4F7",   # 1
    "F7F4F2F1F1F2F5F8",   # 2
    "FaQ1Q0F2F2Q0Q1Fb",   # 3
    "FbO0I1F2F4I1O0Fc",   # 4
    "FbFcQ0F0F1FdFeFf",   # 5  OCCLUSION on one flank, not deep flesh: his ramp is narrow and two
    #                            of its steps measured 29 against a floor of 30. His nose's shadow is on the LEFT flank — the mirror of 08's
    "FaB3F3M0M1F4B4Fb",   # 6
    "FfFbF6F3F4F7FbQ0",   # 7  and his jaw is shaded on ONE side only
]
HEAD_RIGHT_10 = [
    "FaF8F6F5F4F3F2F5",
    "FaF8F6F4F3F1F0F3",
    "FbF9F7F5F4F2F1F4",
    "FbF9F7F6F5F3F2F5",
    "FcFaF8Q0Q1F4F3F6",
    "FcFbF9Q2FcF5F4F7",
    "FdFcFaF8F6F5F4F7",
    "FeFdFbF9F7F6F5F8",
]
HEAD_10 = {
    "front":  FACE_10,
    "top": ["FcFaF8F6F6F8FaFc", "FaF7F4F2F2F4F7Fa", "F8F4F1F0F0F1F4F8",
            "F7F3F0F0F0F0F3F7", "F7F3F0F0F0F0F3F7", "F8F5F2F0F0F2F5F8",
            "FaF8F5F3F3F5F8Fa", "FcFbF9F7F7F9FbFc"],
    "back": ["FbF9F7F5F5F7F9Fb", "FbF8F6F4F4F6F8Fb", "FcF9F7F5F5F7F9Fc",
             "FcFaF8F6F6F8FaFc", "FdFbF9F7F7F9FbFd", "FeFcFaF8F8FaFcFe",
             "FeFdFbF9F9FbFdFe", "Q0FeFcFaFaFcFeQ0"],
    "right":  HEAD_RIGHT_10,
    "left":   flipc(HEAD_RIGHT_10),
    "bottom": ["Q1Q1Q2Q3Q3Q2Q1Q1", "Q1Q2Q3Q4Q4Q3Q2Q1", "Q2Q3Q4Q5Q5Q4Q3Q2",
               "Q2Q3Q4Q5Q5Q4Q3Q2", "Q2Q3Q4Q5Q5Q4Q3Q2", "Q2Q3Q4Q5Q5Q4Q3Q2",
               "Q1Q2Q3Q4Q4Q3Q2Q1", "Q1Q1Q2Q3Q3Q2Q1Q1"],
}
SHIFT_10 = {
    "front": [
        "SbS7S3P2P3S4S8Sc",   # 0
        "S9S5S1P0P1S2S6Sa",   # 1  a plain wide slit, no lace and no facing
        "S8S4S1S0S1S2S5S9",   # 2
        "S9S4S1S0S1S3S6Sa",   # 3
        "S9S5S2S1S2S3S6Sa",   # 4
        "SaS5S2S1S2S4S7Sb",   # 5
        "SaS6S3S2S3S4S7Sb",   # 6
        "SbS6S3S2S3S5S8Sc",   # 7
        "K1K0K1K2K2K3K4K4",   # 8
        "SbS7S4S3S4S5S9Sc",   # 9
        "ScS8S5S4S5S6S9Sd",   # 10
        "SdS9S6S5S6S8SbSe",   # 11
    ],
    "back": [
        "ScS8S4S3S4S6S9Sd",
        "SaS6S2S1S2S4S7Sb",
        "S9S5S2S1S2S3S6Sa",
        "S9S4S1S0S1S3S6Sa",
        "SaS5S2S1S2S4S7Sb",
        "SaS6S3S2S3S4S7Sb",
        "SbS6S3S2S3S5S8Sc",
        "SbS7S4S3S4S5S8Sc",
        "K2K1K2K3K3K4K4K5",
        "ScS8S5S4S5S6SaSd",
        "ScS9S6S5S6S7SaSd",
        "SeSaS7S6S7S9ScSf",
    ],
    "right":  ["SbS6S4Sc", "S9S4S2Sa", "S8S3S1S9", "S9S3S1S9",
               "S9S4S2Sa", "SaS5S3Sb", "SaS5S3Sb", "SbS6S4Sc",
               "K1K0K1K3", "SbS7S5Sc", "ScS8S6Sd", "SdS9S7Se"],
    "left":   ["ScS4S6Sb", "SaS2S4S9", "S9S1S3S8", "S9S1S3S9",
               "SaS2S4S9", "SbS3S5Sa", "SbS3S5Sa", "ScS4S6Sb",
               "K3K1K0K1", "ScS5S7Sb", "SdS6S8Sc", "SeS7S9Sd"],
    "top":    ["SbS7S4S2S2S4S7Sb", "R0R2R4S1S1R5R3R1",
               "T1S5S2S0S1S3S6T0", "SaS6S3S1S2S4S7Sb"],
    "bottom": ["SdScScSbSbScScSd", "ScScSbSaSaSbScSc",
               "ScSbSaS9S9SaSbSc", "SdScSbSaSaSbScSd"],
}
SLEEVE_10 = {
    # Rows 0..4 sleeve, row 5 the cuff, and then the TAN LINE: rows 6..7 in the pale ramp because
    # the cuff has covered them, and rows 8..11 in the tanned one. No blend between the two.
    "front":  ["S5S1S6Sc", "S5S1S6Sc", "S4S0S5Sb", "S5S1S6Sc", "S6S2S7Sd",
               "T2T0T3T5",
               "P3P1P4P9", "P4P2P5Pa",
               "F5F2F6Fd", "F6F3F7Fe", "F8F5F9Ff", "FaF7FbFf"],
    "back":   ["S6S2S7Sd", "S5S1S6Sc", "S5S1S6Sc", "S4S0S5Sb", "S6S2S7Sd",
               "T3T1T4T5",
               "P4P2P5Pa", "P5P3P6Pa",
               "F6F3F7Fe", "F7F4F8Ff", "F9F6FaFf", "FbF8FcFf"],
    "right":  ["R1R0S2S4", "R3R2S0S2", "S6S3S1S3", "S7S4S1S3", "S8S5S2S4",
               "T2T1T0T2",
               "P4P2P0P2", "P5P3P1P3",
               "F6F3F1F3", "F7F4F2F4", "F9F6F4F6", "FbF8F6F8"],
    "left":   ["ScSaS9Sb", "ScSaS9Sb", "SdSbSaSc", "SdSbSaSc", "SeScSbSd",
               "T5T4T3T5",
               "P8P7P6P8", "P9P8P7P9",
               "PaP9P8Pa", "PaP9P8Pa", "PbPaP9Pb", "PbPbPaPb"],
    "top":    ["S9S5S5S9", "S6S1S1S6", "S6S1S1S6", "SaS6S6Sa"],
    "bottom": ["P9P6P6P9", "P6B2B0P7", "P6B4B3P7", "P9P7P7Pa"],
}
HOSE_10 = {
    "front":  ["HcH9HbHe", "HbH7HaHd", "H9H4H7Hb", "H7H2H5H9", "H6H1H4H8",
               "H7H2H5H9", "H8H3H6Ha", "H9H4H7Hb", "U3U1U2U5", "HbH6H9Hd",
               "L4L0L2L6", "L9L6L8Lb"],
    "back":   ["HdHaHcHe", "HcH8HbHd", "HaH5H8Hc", "H8H3H6Ha", "H7H2H5H9",
               "H8H3H6Ha", "H9H4H7Hb", "HaH5H8Hc", "U4U2U3U6", "HcH7HaHe",
               "L5L1L3L7", "LaL7L9Lb"],
    "right":  ["HbHcHeHf", "H9HaHcHe", "H6H8HaHd", "H3H5H8Hb", "H1H3H6H9",
               "H3H5H8Hb", "H5H7H9Hc", "H7H9HbHe", "U1U2U4U6", "HaHcHeHf",
               "L2L3L5L7", "L7L8LaLb"],
    "left":   ["HfHeHcHb", "HeHcHaH9", "HdHaH8H6", "HbH8H5H3", "H9H6H3H1",
               "HbH8H5H3", "HcH9H7H5", "HeHbH9H7", "U6U4U2U1", "HfHeHcHa",
               "L7L5L3L2", "LbLaL8L7"],
    "top":    ["HeHdHdHe", "HdHcHcHd", "HdHcHcHd", "HeHdHdHe"],
    "bottom": ["LbLaLaLb", "LaL9L9La", "LaL9L9La", "LbLaLaLb"],
}
HEM_10 = {
    "front":  ["S8S4S2S6", "S9S5S3S7", "SaS6S4S8", "G3G1G2G5"] + [".." * 4] * 8,
    "back":   ["S9S5S3S7", "SaS6S4S8", "SbS7S5S9", "G4G2G3G6"] + [".." * 4] * 8,
    "right":  ["T0S3S1S3", "T1S4S2S4", "T2S5S3S5", "G4G3G1G3"] + [".." * 4] * 8,
    "left":   ["S3S1S3T0", "S4S2S4T1", "S5S3S5T2", "G3G1G3G4"] + [".." * 4] * 8,
    "top":    ["S7S4S4S7", "S4S1S1S4", "S4S1S1S4", "S8S5S5S8"],
    "bottom": [".." * 4] * 4,
}


# ── person 11: the older woman, light complexion ─────────────────────
#
# HER LINEN HAS BEEN WASHED UNTIL THE COLOUR LEFT IT. Her `S` ramp is the NARROWEST on the roster —
# 0xDCD8CC to 0x6A665C, greyer and flatter than anyone's — so the modelling cannot come from the
# ramp and has to come from the number of folds instead: four columns across the torso against
# everyone else's one or two. That is the trade this file exists to make explicit.

FACE_11 = [
    "FcFaF8F7F7F8FaFd",   # 0
    "F9F6F4F1F0F4F7Fa",   # 1
    "FaF7F4F3F3F5F8Fb",   # 2
    "FdQ1Q0F4F4Q0Q2Fe",   # 3
    "FeO1I1F4F5I2O0Ff",   # 4
    "FeFcFdF1F2Q0FeFf",   # 5
    "FcQ3F5M1M2F6Q4Fd",   # 6  the nose-to-mouth fold, and it is her only age line on the front
    "Q5FdFaF7F8FbFdQ6",   # 7  a soft heavy jaw
]
HEAD_RIGHT_11 = [
    "FdFbF9F8F7F6F5F8",
    "FdFbF9F7F6F4F3F6",
    "FeFcFaF8F7F5F4F7",
    "FeFcFaF9F8F6F5F8",
    "FfFdFbQ1Q2F7F6F9",
    "FfFeFcQ3FfF8F7Fa",
    "Q0FfFdFbF9F8F7Fa",
    "Q3Q0FfFdFbFaF9Fc",
]
HEAD_11 = {
    "front":  FACE_11,
    "top": ["FfFdFbFaFaFbFdFf", "FdFaF8F6F6F8FaFd", "FbF8F5F4F4F5F8Fb",
            "FaF7F4F3F3F4F7Fa", "FaF6F4F3F3F4F6Fa", "FbF8F5F4F4F5F8Fb",
            "FdFbF8F7F7F8FbFd", "FfFeFcFbFbFcFeFf"],
    "back": ["FeFcFaF9F9FaFcFe", "FeFcFaF8F8FaFcFe", "FfFdFbF9F9FbFdFf",
             "FfFdFbFaFaFbFdFf", "Q0FeFcFbFbFcFeQ0", "Q1Q0FeFdFdFeQ0Q1",
             "Q2Q1FfFeFeFfQ1Q2", "Q4Q2Q0FfFfQ0Q2Q4"],
    "right":  HEAD_RIGHT_11,
    "left":   flipc(HEAD_RIGHT_11),
    "bottom": ["Q3Q4Q4Q5Q5Q4Q4Q3", "Q4Q4Q5Q6Q6Q5Q4Q4", "Q4Q5Q6Q7Q7Q6Q5Q4",
               "Q5Q6Q7Q7Q7Q7Q6Q5", "Q5Q6Q7Q7Q7Q7Q6Q5", "Q4Q5Q6Q7Q7Q6Q5Q4",
               "Q4Q4Q5Q6Q6Q5Q4Q4", "Q3Q4Q4Q5Q5Q4Q4Q3"],
}
SHIFT_11 = {
    "front": [
        "SeSbS7P5P6S8ScSf",   # 0
        "ScS9S5P3P4S6SaSd",   # 1
        "SaS5S1S6S3S7S9Sb",   # 2  TWO fold columns and a jitter, which is the arrangement 08 uses.
        "SaS4S1S6S2S7S9Sb",   # 3   FOUR of them was the first attempt: held in the same columns it
        "SbS6S2S1S6S3SaSc",   # 4   came off the sheet as vertical striping, and stepped one column
        "SbS5S2S1S6S3SaSc",   # 5   over every two courses it came off as a CHECKER, which is worse.
        "ScS6S3S7S2S8SaSd",   # 6   Her narrow ramp buys less modelling and that is the honest price
        "ScS5S3S7S2S8SaSd",   # 7   of linen washed until the colour left it.
        "K1K0K1K2K2K3K4K4",   # 8
        "SdS8S4S9S4S9SbSe",   # 9
        "SdS7S4S9S3S9SbSe",   # 10
        "SeSaS7SbS6SbSdSf",   # 11
    ],
    "back": [
        "SfScS8S6S7S9SdSf",
        "SdSaS6S4S5S7SbSe",
        "SbS6S2S7S4S8SaSc",
        "T1T0S2S7S3S8SaSc",
        "ScS7S3S2S7S4SbSd",
        "ScS6S3S2S7S4SbSd",
        "SdS7S4S8S3S9SbSe",
        "SdS6S4S8S3S9SbSe",
        "K2K1K2K3K3K4K5K5",
        "SeS9S5SaS5SaScSf",
        "SeS8S5SaS4SaScSf",
        "SfSbS8ScS7ScSeSf",
    ],
    "right":  ["SeSaS7Sf", "ScS8S5Sd", "SaS5S2Sb", "S9S4S1Sa",
               "SaS5S2Sb", "SbS6S3Sc", "SbS6S3Sc", "ScS7S4Sd",
               "K2K1K2K4", "SdS8S5Se", "SdS7S4Se", "SfSaS7Sf"],
    "left":   ["SfS7SaSe", "SdS5S8Sc", "SbS2S5Sa", "SaS1S4S9",
               "SbS2S5Sa", "ScS3S6Sb", "ScS3S6Sb", "SdS4S7Sc",
               "K4K2K1K2", "SeS5S8Sd", "SeS4S7Sd", "SfS7SaSf"],
    "top":    ["SfSbS7S5S5S7SbSf", "R0R2R4S0S0R5R3R1",
               "T1S6S2S8S8S2S6T0", "ScS8S4S9S9S4S9Sd"],
    "bottom": ["SfSfSeSeSeSeSfSf", "SfSeSdScScSdSeSf",
               "SeSdScSbSbScSdSe", "SfSeSdScScSdSeSf"],
}
SLEEVE_11 = {
    "front":  ["S6S2S7Sd", "S5S1S6Sc", "S6S2S7Sd", "S5S1S6Sc", "S6S2S7Sd",
               "S6S2S7Sd", "S7S3S8Se", "S7S3S8Se", "T2T0T3T5",
               "F5F2F6Fd", "F6F3F7Fe", "F8F5F9Ff"],
    "back":   ["S7S3S8Se", "S6S2S7Sd", "S5S1S6Sc", "S6S2S7Sd", "S7S3S8Se",
               "S7S3S8Se", "S8S4S9Se", "S8S4S9Se", "T3T1T4T5",
               "F6F3F7Fe", "F7F4F8Ff", "F9F6FaFf"],
    "right":  ["S7S4S2S4", "S6S3S1S3", "S7S4S2S4", "S6S3S1S3", "S7S4S2S4",
               "S7S4S2S4", "S8S5S3S5", "S8S5S3S5", "T2T1T0T2",
               "F6F3F1F3", "F8F5F3F5", "FaF7F5F7"],
    "left":   ["SdSbSaSc", "ScSaS9Sb", "SdSbSaSc", "ScSaS9Sb", "SdSbSaSc",
               "SdSbSaSc", "SeScSbSd", "SeScSbSd", "T5T4T3T5",
               "P5P4P3P5", "P8P7P6P8", "PbPaP9Pb"],
    "top":    ["SbS7S7Sb", "S8S3S3S8", "S8S3S3S8", "ScS8S8Sc"],
    "bottom": ["P2P0P1P2", "P1B4B1P3", "P0B3B5P2", "P2P1P1P3"],
}
HOSE_11 = {
    "front":  ["HdHaHcHf", "HcH9HbHe", "HbH6HaHd", "HaH4H9Hc", "HaH2H8Hc",
               "K1K0K2K3", "HbH1H9Hd", "HbH3H9Hd", "HcH8HaHe", "HdH9HbHe",
               "L5L1L3L7", "LaL7L9Lb"],
    "back":   ["HeHbHdHf", "HdHaHcHe", "HcH7HbHe", "HbH5HaHd", "HbH0H9Hd",
               "K2K1K3K4", "HcH2HaHe", "HcH4HaHe", "HdH9HbHe", "HeHaHcHf",
               "L6L0L4L8", "LbL8LaLb"],
    "right":  ["HbHdHeHf", "HaHcHdHe", "H8HaHcHe", "H6H8HaHc", "H5H7H9Hc",
               "K0K1K2K3", "H6H8HaHd", "H7H9HbHe", "H9HbHdHe", "HbHdHeHf",
               "L2L3L5L7", "L7L8LaLb"],
    "left":   ["HfHeHdHb", "HeHdHcHa", "HeHcHaH8", "HcHaH8H6", "HcH9H7H5",
               "K3K2K1K0", "HdHaH8H6", "HeHbH9H7", "HeHdHbH9", "HfHeHdHb",
               "L7L5L3L2", "LbLaL8L7"],
    "top":    ["HfHeHeHf", "HeHdHdHe", "HeHdHdHe", "HfHeHeHf"],
    "bottom": ["LbLaLaLb", "LaL9L9La", "LaL9L9La", "LbLaLaLb"],
}
HEM_11 = {
    "front":  ["S8S4S2S6", "S9S5S3S7", "S9S5S4S8", "SaS6S4S8", "SaS6S5S9",
               "SbS7S5S9", "SbS7S6Sa", "ScS8S6Sa", "ScS8S7Sb", "SdS9S7Sb",
               "G3G1G2G5"] + [".." * 4] * 1,
    "back":   ["S9S5S3S7", "SaS6S4S8", "SaS6S5S9", "SbS7S5S9", "SbS7S6Sa",
               "ScS8S6Sa", "ScS8S7Sb", "SdS9S7Sb", "SdS9S8Sc", "SeSaS8Sc",
               "G4G2G3G6"] + [".." * 4] * 1,
    "right":  ["T0S3S1S3", "T1S4S2S4", "T2S4S2S4", "T3S5S3S5", "T4S5S3S5",
               "T5S6S4S6", "S9S6S4S6", "S9S7S5S7", "SaS7S5S7", "SaS8S6S8",
               "G4G3G1G3"] + [".." * 4] * 1,
    "left":   ["S3S1S3T0", "S4S2S4T1", "S4S2S4T2", "S5S3S5T3", "S5S3S5T4",
               "S6S4S6T5", "S6S4S6S9", "S7S5S7S9", "S7S5S7Sa", "S8S6S8Sa",
               "G3G1G3G4"] + [".." * 4] * 1,
    "top":    ["S8S5S5S8", "S5S2S2S5", "S5S2S2S5", "S9S6S6S9"],
    "bottom": [".." * 4] * 4,
}


# ── person 12: the older man, dark complexion ────────────────────────
#
# BOTH CUFFS MENDED OUT OF THE SAME SCRAP. The roster line said "one sleeve mended"; the mesh does
# not allow it — `NpcModel` gives the left limbs their own texOffs and `verify` compares the two byte
# for byte, so an asymmetric sleeve is not drawable on this rig. The honest reading of the same fact
# is a man who mended both, and the mending linen is GREYER than his own rather than lighter, which
# is what tells his patch from 04's.

FACE_12 = [
    "F7F5F4F3F3F4F6F8",   # 0
    "F5F2FdFcFcFdF3F6",   # 1  a forehead line in the DEEP END OF FLESH, not in occlusion: his skin is
    "F6F3F1F0F0F1F3F7",   # 2   darker and the same Q step would have been a hole
    "FaQ1Q0Q0F3Q0Q2Fb",   # 3
    "FbO1I1F2F4I2O0Fc",   # 4
    "FbF9Q0F0F1FcFdFe",   # 5
    "FaQ4F4M0M1F5Q5Fb",   # 6
    "Q6FcF8F5F6F9FcQ7",   # 7
]
HEAD_RIGHT_12 = [
    "FbF9F7F6F5F4F3F6",
    "FbF9F7F5F4F2F1F4",
    "FcQ0Q1F6F5F3F2F5",   # the temple hollow, like 04's and one course higher
    "FcFaF8F7F6F4F3F6",
    "FdFbF9Q2Q3F5F4F7",
    "FdFcFaQ4FdF6F5F8",
    "FeFdFbF9F7F6F5F8",
    "Q3Q0FeFcFaF9F8Fb",
]
HEAD_12 = {
    "front":  FACE_12,
    "top": ["FdFbF9F8F8F9FbFd", "FbF8F6F4F4F6F8Fb", "F9F6F3F2F2F3F6F9",
            "F8F4F1F0F0F2F5F8", "F8F4F1F0F0F1F4F8", "F9F6F3F1F1F3F6F9",
            "FbF9F6F5F5F6F9Fb", "FdFcFaF9F9FaFcFd"],
    "back": ["FcFaF8F6F6F8FaFc", "FcFaF8F6F6F8FaFc", "FdFbF9F7F7F9FbFd",
             "FeFcFaF8F8FaFcFe", "FeFdFbF9F9FbFdFe", "FfFeFcFbFbFcFeFf",
             "Q0FfFdFcFcFdFfQ0", "Q3Q1FfFeFeFfQ1Q3"],
    "right":  HEAD_RIGHT_12,
    "left":   flipc(HEAD_RIGHT_12),
    "bottom": ["Q3Q4Q4Q5Q5Q4Q4Q3", "Q4Q5Q5Q6Q6Q5Q5Q4", "Q4Q5Q6Q7Q7Q6Q5Q4",
               "Q5Q6Q7Q7Q7Q7Q6Q5", "Q5Q6Q7Q7Q7Q7Q6Q5", "Q4Q5Q6Q7Q7Q6Q5Q4",
               "Q4Q5Q5Q6Q6Q5Q5Q4", "Q3Q4Q4Q5Q5Q4Q4Q3"],
}
SHIFT_12 = {
    "front": [
        "SdSaS6P5P6S8ScSe",   # 0
        "SbS8S4P3P4S6S9Sc",   # 1
        "SaS6S3S1S2S4S8Sb",   # 2
        "SaS5S2S0S1S3S7Sb",   # 3
        "SbS6S3S1S2S4S8Sc",   # 4
        "SbS7S3S2S3S5S8Sc",   # 5
        "ScS8S4S3S4S6S9Sd",   # 6
        "K1K0K1K2K2K3K4K4",   # 7  a cord, and it rides high on a thin old man
        "ScS8S5S4S5S6SaSd",   # 8
        "SdS9S6S5S6S8SbSe",   # 9
        "G2G1G0G2G3G4SbSe",   # 10 the dirt is low and to one side
        "SeSbS8S7S8SaSdSf",   # 11
    ],
    "back": [
        "SeSbS7S6S7S9ScSf",
        "ScS9S5S4S5S7SaSd",
        "SbS7S4S3S4S6S9Sc",
        "SbS6S3S2S3S5S8Sc",
        "T1T0S4S3S4S6S9Sc",
        "ScS8S5S4S5S7SaSd",
        "ScS8S5S4S5S7SaSd",
        "K2K1K2K3K3K4K5K5",
        "SdS9S6S5S6S8SbSe",
        "SdSaS7S6S7S9SbSe",
        "SeSbS8S7S8SaSdSf",
        "SfScS9S8S9SbSeSf",
    ],
    "right":  ["SdS9S7Se", "SbS7S5Sc", "SaS5S3Sb", "S9S4S2Sa",
               "SaS5S3Sb", "SbS6S4Sc", "ScS7S5Sd", "K2K1K2K4",
               "ScS8S6Sd", "SdS9S7Se", "G3G2G1G4", "SfSbS9Sf"],
    "left":   ["SeS7S9Sd", "ScS5S7Sb", "SbS3S5Sa", "SaS2S4S9",
               "SbS3S5Sa", "ScS4S6Sb", "SdS5S7Sc", "K4K2K1K2",
               "SdS6S8Sc", "SeS7S9Sd", "G4G1G2G3", "SfS9SbSf"],
    "top":    ["SdSaS7S5S5S7SaSd", "R1R0S3S2S2S3R0R1",
               "T1S6S3S1S1S3S6T0", "SbS7S4S2S3S5S8Sc"],
    "bottom": ["SfSeSeSdSdSeSeSf", "SeSeSdScScSdSeSe",
               "SeSdScSbSbScSdSe", "SfSeSdScScSdSeSf"],
}
SLEEVE_12 = {
    # THE MENDED CUFF: two courses of the mending linen with a seam above them, on the last courses
    # of the sleeve. `R` runs grey rather than bright here, so it reads as a different garment's
    # cloth rather than a newer one's.
    "front":  ["S5S1S6Sc", "S5S1S6Sc", "S4S0S5Sb", "S5S1S6Sc",
               "T2T0T3T5", "R1R0R2R4", "R2R1R3R5",
               "F5F2F6Fd", "F5F2F6Fd", "F6F3F7Fe", "F8F5F9Ff", "FaF7FbFf"],
    "back":   ["S6S2S7Sd", "S5S1S6Sc", "S5S1S6Sc", "S4S0S5Sb",
               "T3T1T4T5", "R2R1R3R5", "R3R2R4R5",
               "F6F3F7Fe", "F5F2F6Fd", "F7F4F8Fe", "F9F6FaFf", "FbF8FcFf"],
    "right":  ["R1R0S2S4", "R3R2S0S2", "S6S3S1S3", "S7S4S1S3",
               "T2T1T0T2", "R1R0R1R3", "R2R1R2R4",
               "F6F3F1F3", "F6F3F1F3", "F7F4F2F4", "F9F6F4F6", "FbF8F6F8"],
    "left":   ["ScSaS9Sb", "ScSaS9Sb", "SdSbSaSc", "SdSbSaSc",
               "T5T4T3T5", "R4R3R2R4", "R5R4R3R5",
               "P4P3P2P4", "P6P5P4P6", "P8P7P6P8", "PaP9P8Pa", "PbPaP9Pb"],
    "top":    ["S9S5S5S9", "S6S1S1S6", "S6S1S1S6", "SaS6S6Sa"],
    "bottom": ["P9P6P6P9", "P6B5B2P7", "P6B4B5P7", "P9P7P7Pa"],
}
HOSE_12 = {
    "front":  ["HdHaHcHf", "HbH8HaHd", "H9H5H8Hb", "H7H3H6H9", "H8H4H7Ha",
               "H7H3H6H9", "H9H5H8Hb", "H8H4H7Ha", "HaH6H9Hc", "HcH8HbHe",
               "L5L1L3L7", "LaL7L9Lb"],
    "back":   ["HeHbHdHf", "HcH9HbHe", "HaH6H9Hc", "H8H4H7Ha", "H9H5H8Hb",
               "H8H4H7Ha", "HaH6H9Hc", "H9H5H8Hb", "HbH7HaHd", "HdH9HcHf",
               "L6L2L4L8", "LbL8LaLb"],
    "right":  ["HbHcHeHf", "H9HaHcHe", "H6H8HaHd", "H4H6H8Hb", "H5H7H9Hc",
               "H4H6H8Hb", "H6H8HaHd", "H5H7H9Hc", "H7H9HbHe", "H9HbHdHf",
               "U1U2U4U6", "L7L8LaLb"],
    "left":   ["HfHeHcHb", "HeHcHaH9", "HdHaH8H6", "HbH8H6H4", "HcH9H7H5",
               "HbH8H6H4", "HdHaH8H6", "HcH9H7H5", "HeHbH9H7", "HfHdHbH9",
               "U6U4U2U1", "LbLaL8L7"],
    "top":    ["HfHeHeHf", "HeHdHdHe", "HeHdHdHe", "HfHeHeHf"],
    "bottom": ["LbLaLaLb", "LaL9L8L9", "L9L8L8L9", "LbLaLaLb"],
}
HEM_12 = {
    "front":  ["S7S3S1S5", "S8S4S2S6", "S9S5S3S7", "SaS6S4S8", "G3G1G2G5"]
              + [".." * 4] * 7,
    "back":   ["S8S4S2S6", "S9S5S3S7", "SaS6S4S8", "SbS7S5S9", "G4G2G3G6"]
              + [".." * 4] * 7,
    "right":  ["T0S3S1S3", "T1S4S2S4", "T2S5S3S5", "T3S6S4S6", "G4G3G1G3"]
              + [".." * 4] * 7,
    "left":   ["S3S1S3T0", "S4S2S4T1", "S5S3S5T2", "S6S4S6T3", "G3G1G3G4"]
              + [".." * 4] * 7,
    "top":    ["S6S3S3S6", "S3S1S1S3", "S3S1S1S3", "S7S4S4S7"],
    "bottom": [".." * 4] * 4,
}


# ── person 13: the young woman, dark complexion ──────────────────────
#
# NOTHING ON HER IS WORN. No `G` above the hem, no `U` at all, no patch, no seam let out — and
# BAREFOOT, which on 06 is poverty and on her is a choice, because everything else about her is
# kept. Two bodies can share a device and mean opposite things by it; that is what makes fourteen
# people worth drawing rather than fourteen palettes.

FACE_13 = [
    "F9F7F5F4F4F5F7Fa",   # 0
    "F6F3F1F0F0F1F4F7",   # 1
    "F7F4F2F1F1F2F5F8",   # 2
    "FaQ0Q1F3F3Q1Q0Fb",   # 3
    "FbO1I0F3F4I1O2Fc",   # 4
    "FbF9FaF0F1Q0FcFd",   # 5
    "FaB2F3M0M1F4B3Fb",   # 6
    "Q0FaF6F3F4F7FaQ1",   # 7  the shallowest jaw shadow of the four dark bodies
]
HEAD_RIGHT_13 = [
    "FaF8F6F5F4F3F2F5",
    "FaF8F6F4F3F1F0F3",
    "FbF9F7F5F4F2F1F4",
    "FbF9F7F6F5F3F2F5",
    "FcFaF8Q0Q1F4F3F6",
    "FcFbF9Q2FcF5F4F7",
    "FdFcFaF8F6F5F4F7",
    "Q0FdFbF9F7F6F5F8",
]
HEAD_13 = {
    "front":  FACE_13,
    "top": ["FcFaF8F6F6F8FaFc", "FaF7F4F2F2F4F7Fa", "F8F5F2F0F0F2F5F8",
            "F7F3F1F0F0F1F3F7", "F7F3F0F0F0F1F3F7", "F8F5F2F0F0F2F5F8",
            "FaF8F5F3F3F5F8Fa", "FcFbF9F7F7F9FbFc"],
    "back": ["FbF9F7F5F5F7F9Fb", "FbF9F7F5F5F7F9Fb", "FcFaF8F6F6F8FaFc",
             "FdFbF9F7F7F9FbFd", "FdFcFaF8F8FaFcFd", "FeFdFbFaFaFbFdFe",
             "FfFeFcFbFbFcFeFf", "Q1FfFeFdFdFeFfQ1"],
    "right":  HEAD_RIGHT_13,
    "left":   flipc(HEAD_RIGHT_13),
    "bottom": ["Q0Q1Q1Q2Q2Q1Q1Q0", "Q1Q1Q2Q3Q3Q2Q1Q1", "Q1Q2Q3Q4Q4Q3Q2Q1",
               "Q2Q3Q5Q6Q6Q5Q3Q2", "Q2Q3Q5Q7Q7Q5Q3Q2", "Q1Q2Q3Q4Q4Q3Q2Q1",
               "Q1Q1Q2Q3Q3Q2Q1Q1", "Q0Q1Q1Q2Q2Q1Q1Q0"],
}
SHIFT_13 = {
    "front": [
        "ScS9S5P3P4S6SaSd",   # 0
        "SaS7S3P1P2S4S8Sb",   # 1
        "S9S5T1T0T0T1S6Sa",   # 2  a stitched facing, like 02's — the mark of cloth kept properly
        "S9S4S1S0S1S3S6Sa",   # 3
        "SaS5S2S1S2S4S7Sb",   # 4
        "SaS6S2S1S2S4S7Sb",   # 5
        "SbS6S3S2S3S5S8Sc",   # 6
        "SbS7S3S2S3S5S8Sc",   # 7
        "K1K0K1K2K2K3K4K4",   # 8
        "ScS8S5S4S5S6S9Sd",   # 9
        "ScS9S6S5S6S7SaSd",   # 10 and nothing dirty anywhere on the torso
        "SdSaS7S6S7S9SbSe",   # 11
    ],
    "back": [
        "SdSaS6S5S6S8SbSe",
        "SbS8S4S3S4S6S9Sc",
        "SaS6S3S2S3S5S8Sb",
        "SaS5S2S1S2S4S7Sb",
        "T1T0S3S2S3S5S8Sc",
        "SbS7S4S3S4S6S9Sc",
        "SbS7S4S3S4S6S9Sd",
        "ScS8S5S4S5S7SaSd",
        "K2K1K2K3K3K4K5K5",
        "ScS9S6S5S6S8SbSe",
        "SdSaS7S6S7S9SbSe",
        "SeSbS8S7S8SaSdSf",
    ],
    "right":  ["ScS8S6Sd", "SaS6S4Sb", "S9T0S2Sa", "S9S4S1S9",
               "SaS5S2Sa", "SaS6S3Sb", "SbS6S3Sb", "ScS7S4Sc",
               "K2K1K2K4", "ScS8S5Sd", "SdS9S6Se", "SeSaS7Sf"],
    "left":   ["SdS6S8Sc", "SbS4S6Sa", "SaS2T0S9", "S9S1S4S9",
               "SaS2S5Sa", "SbS3S6Sa", "SbS3S6Sb", "ScS4S7Sc",
               "K4K2K1K2", "SdS5S8Sc", "SeS6S9Sd", "SfS7SaSe"],
    "top":    ["ScS9S6S4S4S6S9Sc", "R0R2R4S1S1R5R3R1",
               "T1S5S2S0S0S2S5T0", "SaS6S3S1S2S4S7Sb"],
    "bottom": ["SeSdSdScScSdSdSe", "SdSdScSbSbScSdSd",
               "SdScSbSaSaSbScSd", "SeSdScSbSbScSdSe"],
}
SLEEVE_13 = {
    "front":  ["S6S2S7Sd", "S5S1S6Sc", "S5S1S6Sc", "S6S2S7Sd", "S6S2S7Sd",
               "S7S3S8Se", "S7S3S8Se", "T2T0T3T5",
               "F5F2F6Fd", "F6F3F7Fe", "F7F4F8Fe", "F9F6FaFf"],
    "back":   ["S7S3S8Se", "S6S2S7Sd", "S5S1S6Sc", "S6S2S7Sd", "S7S3S8Se",
               "S8S4S9Se", "S8S4S9Se", "T3T1T4T5",
               "F6F3F7Fe", "F7F4F8Ff", "F8F5F9Ff", "FaF7FbFf"],
    "right":  ["S7S4S2S4", "S6S3S1S3", "S6S3S1S3", "S7S4S2S4", "S7S4S2S4",
               "S8S5S3S5", "S8S5S3S5", "T2T1T0T2",
               "F6F3F1F3", "F7F4F2F4", "F9F6F4F6", "FbF8F6F8"],
    "left":   ["SdSbSaSc", "SdSbSaSc", "SeScSbSd", "SeScSbSd", "SeScSbSd",
               "SfSdScSe", "SfSdScSe", "T5T4T3T5",
               "P5P4P3P5", "P7P6P5P7", "P9P8P7P9", "PbPaP9Pb"],
    "top":    ["SaS6S6Sa", "S7S2S2S7", "S7S2S2S7", "SbS7S7Sb"],
    "bottom": ["P9P6P6P9", "P6B3B0P7", "P6B5B2P7", "P9P7P7Pa"],
}
HOSE_13 = {
    # BAREFOOT, and clean with it: no `U` on the whole leg, where 06's barefoot legs carry mud at
    # the ankle. The last two courses are the foot in the pale ramp, because a foot is not tanned.
    "front":  ["HdHaHcHf", "HcH9HbHe", "HbH6HaHd", "HaH4H9Hc", "HaH2H8Hc",
               "K1K0K2K3", "HbH1H9Hd", "HbH3H9Hd", "HcH0HaHe",
               "P4P1P2P6", "P7P4P5P9", "PaP7P8Pb"],
    "back":   ["HeHbHdHf", "HdHaHcHe", "HcH9HbHe", "HbH8HaHd", "HbH7H9Hd",
               "K2K1K3K4", "HcH8HaHe", "HcH8HaHe", "HdH9HbHe",
               "P5P2P3P7", "P8P5P6Pa", "PbP8P9Pb"],
    "right":  ["HbHdHeHf", "HaHcHdHe", "H8HaHcHe", "H6H8HaHc", "H5H7H9Hc",
               "K0K1K2K3", "H6H8HaHd", "H7H9HbHe", "H9HbHdHe",
               "P2P3P5P7", "P5P6P8Pa", "P9PaPbPb"],
    "left":   ["HfHeHdHb", "HeHdHcHa", "HeHcHaH8", "HcHaH8H6", "HcH9H7H5",
               "K3K2K1K0", "HdHaH8H6", "HeHbH9H7", "HeHdHbH9",
               "P7P5P3P2", "PaP8P6P5", "PbPbPaP9"],
    "top":    ["HfHeHeHf", "HeHdHdHe", "HeHdHdHe", "HfHeHeHf"],
    "bottom": ["PbP9P9Pb", "P9B5B4Pa", "P9B4B5Pa", "PbPaPaPb"],
}
HEM_13 = {
    # WASHED WHITE, and that is the `R` ramp doing its other job. `R` is "abraded linen — bleached
    # and thinned where a tunic rubs"; on her, who has no grime, no mud and no patch, it is the
    # BLEACHED reading — a skirt beaten on a stone comes back brightest across the panel that gets
    # the beating. Three courses front and two behind, and never on cols 0 or 3, which are the box's
    # wrap columns and would put a pale line down her silhouette.
    # And TWO courses of soil at the very hem, not one. She is barefoot in a floor-length skirt, so
    # the bottom of it is the one part of her that is dirty — which is her whole story, not an
    # exception to it — and two courses is what puts the `G` ramp's eight steps on her at all.
    "front":  ["S8S4S2S6", "S9S5S3S7", "S9S5S4S8", "SaR3R1S8", "SaR4R2S9",
               "SbR5R3S9", "SbS7S6Sa", "ScG5G4Sa", "G1G0G2G3"] + [".." * 4] * 3,
    "back":   ["S9S5S3S7", "SaS6S4S8", "SaS6S5S9", "SbS7S5S9", "SbR4R2Sa",
               "ScR5R3Sa", "ScS8S7Sb", "SdG6G5Sb", "G2G1G3G4"] + [".." * 4] * 3,
    "right":  ["T0S3S1S3", "T1S4S2S4", "T2S4S2S4", "T3S5S3S5", "T4S5S3S5",
               "T5S6S4S6", "S9S6S4S6", "S9G7G6S7", "SaG7G6S7"] + [".." * 4] * 3,
    "left":   ["S3S1S3T0", "S4S2S4T1", "S4S2S4T2", "S5S3S5T3", "S5S3S5T4",
               "S6S4S6T5", "S6S4S6S9", "S7G6G7S9", "S7G6G7Sa"] + [".." * 4] * 3,
    "top":    ["S7S4S4S7", "S4S1S1S4", "S4S1S1S4", "S8S5S5S8"],
    "bottom": [".." * 4] * 4,
}


# Which blocks make each person.
ART = {
    "00": dict(head=HEAD_00, body=SHIFT_00, arm=SLEEVE_00, leg=HOSE_00,
               leg_outer=HEM_00, hem_rows=4, face=FACE_00),
    "01": dict(head=HEAD_01, body=SHIFT_01, arm=SLEEVE_01, leg=HOSE_01,
               leg_outer=HEM_01, hem_rows=10, face=FACE_01),
    "02": dict(head=HEAD_02, body=SHIFT_02, arm=SLEEVE_02, leg=HOSE_02,
               leg_outer=HEM_02, hem_rows=5, face=FACE_02),
    "03": dict(head=HEAD_03, body=SHIFT_03, arm=SLEEVE_03, leg=HOSE_03,
               leg_outer=HEM_03, hem_rows=9, face=FACE_03),
    "04": dict(head=HEAD_04, body=SHIFT_04, arm=SLEEVE_04, leg=HOSE_04,
               leg_outer=HEM_04, hem_rows=3, face=FACE_04),
    "05": dict(head=HEAD_05, body=SHIFT_05, arm=SLEEVE_05, leg=HOSE_05,
               leg_outer=HEM_05, hem_rows=11, face=FACE_05),
    "06": dict(head=HEAD_06, body=SHIFT_06, arm=SLEEVE_06, leg=HOSE_06,
               leg_outer=HEM_06, hem_rows=7, face=FACE_06),
    "07": dict(head=HEAD_07, body=SHIFT_07, arm=SLEEVE_07, leg=HOSE_07,
               leg_outer=HEM_07, hem_rows=8, face=FACE_07),
    "08": dict(head=HEAD_08, body=SHIFT_08, arm=SLEEVE_08, leg=HOSE_08,
               leg_outer=HEM_08, hem_rows=6, face=FACE_08),
    "09": dict(head=HEAD_09, body=SHIFT_09, arm=SLEEVE_09, leg=HOSE_09,
               leg_outer=HEM_09, hem_rows=10, face=FACE_09),
    "10": dict(head=HEAD_10, body=SHIFT_10, arm=SLEEVE_10, leg=HOSE_10,
               leg_outer=HEM_10, hem_rows=4, face=FACE_10),
    "11": dict(head=HEAD_11, body=SHIFT_11, arm=SLEEVE_11, leg=HOSE_11,
               leg_outer=HEM_11, hem_rows=11, face=FACE_11),
    "12": dict(head=HEAD_12, body=SHIFT_12, arm=SLEEVE_12, leg=HOSE_12,
               leg_outer=HEM_12, hem_rows=5, face=FACE_12),
    "13": dict(head=HEAD_13, body=SHIFT_13, arm=SLEEVE_13, leg=HOSE_13,
               leg_outer=HEM_13, hem_rows=9, face=FACE_13),
}


# ── the light: ONE source round the box, never a border per face ─────
#
# THE FAULT THIS PASS EXISTS TO END, in the owner's words: *"какие-то рамки у рук, ног выбивает из
# ощущения"* — visible frames round the arms and the legs. Every gate in this file passed the set
# that had them, because no colour count and no contrast floor can see a lighting topology.
#
# Measured cause. Every face was drawn with a SYMMETRIC BORDER — its first and last column darker
# than its middle — on all four sides. Four borders round one box put two dark columns AGAINST each
# other at every seam, so the darkness doubles exactly where the eye reads an edge:
#
#     citizen_body_00  r_arm  front  edge 133.4  mid 153.9   delta 20.5
#                             back   edge 131.1  mid 152.0   delta 21.0
#     citizen_body_05  body   front  edge 106.9  mid 149.4   delta 42.5
#                             right  edge 109.9  mid 153.2   delta 43.4
#
#     r_arm, walked round in net order
#       front last 115.1 -> left first 108.2    two dark columns running together = THE FRAME
#       left  last 114.2 -> back  first 149.9   a step of 36
#       back  last 112.2 -> right first 152.1   a step of 40
#
# A CUBE IS LIT DIRECTIONALLY. One source: the lit face brightest, one side lighter than the other,
# the back darkest, and the tone CONTINUOUS round the box — one minimum in the cycle, not four.
#
# CALIBRATED, and on the right corpus. The mod's own hand-drawn `default_skin.png` scores ZERO wrap
# faults with a worst seam of 10.8, which is where `check_wrap.MAX_SEAM_STEP` of 12 comes from; its
# torso swings 30 luminance points round the whole cycle and its limbs 6 to 8. The 31 downloaded
# references are noisy on the same metric (median 4 step-hits over 12 boxes) and they are the WRONG
# corpus for it: a reference's seam step is usually a design boundary — a jacket edge, a stripe, a
# logo — where ours was pure shading. So the number to beat is the author's zero, not their median.
#
# HOW, and why not by hand. Fourteen people times nineteen faces is 266 blocks of ASCII, and an
# edit that size by hand is an edit nobody can check. This pass instead moves each column of each
# side face BODILY, by one offset in the material's own ramp, so that the column MEANS follow the
# light while every cell keeps its own deviation from its column — the folds, the grime patches, the
# seams, the belt, the cuff and the mended courses all survive untouched, because they are row
# content and material content, not column lighting.
#
# THE HEAD FRONT IS FROZEN. The owner's verdict is that the faces are finished (row spreads
# 53/206/48/69/58 on body 00), so cols 1..6 of `head.front` are never touched. Cols 0 and 7 are —
# they are the seam itself, and the fix there is what the brief calls "a wrap fix at the head's own
# corners". The flesh ramp is only 28.5 luminance points wide over its 16 steps, 1.8 a step, so the
# whole correction at those two columns is a handful of points.
#
# THE TWO LIMBS STAY MIRRORS. `verify` compares `l_arm` to the mirror of `r_arm` byte for byte and
# the mod shipped an empty left arm for a whole revision, so the light on the left limb is the
# mirror of the light on the right. That is the author's own convention too — `default_skin.png`'s
# two arms are exact mirrors — and it costs nothing here, because each limb is continuous in itself.

# Shade round the box, 0 lightest and 1 darkest, at the CENTRE of each face. `right` spans 0..90
# degrees, `front` 90..180, `left` 180..270, `back` 270..360 — the net's own order, which is the
# box rolled, which is why the last column of each face is against the first column of the next.
#
# The lit face is the front, the figure's left is the brighter side and the back is darkest: one
# maximum at 135 and one minimum at 315, and everything between them monotone. Interpolated
# linearly in angle, so the value at a seam is the same from both sides BY CONSTRUCTION and no
# arithmetic can put a step there.
LIGHT_SHADE = ((45.0, 0.72), (135.0, 0.00), (225.0, 0.42), (315.0, 1.00))

# How many luminance points the whole cycle may swing, per box. The author's own skin swings 30 on
# the torso and 6..8 on a limb; the drawn set swings 52..61, which is what made the borders so loud.
# These caps sit between the two: enough that the light reads at 26x, little enough that no face is
# a frame. The head's is the tightest because its flesh ramp is only 28.5 points wide in total and a
# larger swing would flatten the back of the head onto the ramp's last step.
# A BOX CAN ONLY SWING AS FAR AS ITS NARROWEST RAMP, and that is measured rather than guessed. The
# arm carries the linen sleeve (`S`, 118 luminance points wide) and the bare forearm (`F`, 28.5) on
# the same columns, and a correction is spent in luminance, so a 34-point swing left the flesh
# clamped at `Ff` on three columns of every body and the column mean short of its target by the seam
# floor itself. A flat cap of 26 fixed the arm and then cost the LEG range it could well afford —
# a barefoot woman's hose is one 125-point ramp and nothing else, and she came off the gate at 94
# distinct colours against a floor of 100.
#
# So the ceiling below is an authored maximum and the real swing is the smaller of it, the range the
# drawing already spends, and 85% of the narrowest ramp the box actually uses. `main` prints it.
SWING_CAP = {"head": 24.0, "body": 40.0, "arm": 34.0, "leg": 34.0, "leg_outer": 34.0}

# Which cells of which face may not move. `head.front` cols 1..6 carry the eyes, the nose bridge
# and the mouth, and the owner has ruled the faces finished.
FROZEN: Dict[str, Dict[str, Tuple[int, ...]]] = {
    "head": {"front": (1, 2, 3, 4, 5, 6)},
}

CYCLE = ("right", "front", "left", "back")

# ORDERED DITHER, and it is the repo's own gradient rule arriving on a texture. `docs/STYLE.md`:
# "map height to a position on the chain, and dither ONLY between two adjacent steps". A correction
# is spent in whole ramp steps, so a column that wants 1.4 steps of shadow either rounds to 1 and
# stays too light or to 2 and goes too dark — and the rounding error was worth up to half a step,
# which on the linen is 4.3 luminance points and on the hem was the seam floor itself.
#
# Dithering the fraction spends it exactly, and it is where the density lost to the correction comes
# back: 14 bodies came off the relight at 94..113 distinct colours against 101..122 before it, and
# the floor is 100. Two adjacent steps of one material are 1.8 points apart on the flesh and 8.6 on
# the linen, both at or under this repo's measured invisibility threshold of 7, so what a player
# sees is a blend and not a check. A 4x4 Bayer matrix, so no full course and no full column ever
# takes the same side of the fraction — a course of it is the "painted stripe" `docs/STYLE.md` warns
# about and a column of it would put the step back at the seam.
BAYER4 = ((0, 8, 2, 10), (12, 4, 14, 6), (3, 11, 1, 9), (15, 7, 13, 5))


def shade_at(theta: float) -> float:
    """`LIGHT_SHADE` interpolated cyclically. Continuous, so a seam cannot carry a step."""
    pts = sorted(LIGHT_SHADE)
    theta %= 360.0
    for i in range(len(pts)):
        a_ang, a_val = pts[i]
        b_ang, b_val = pts[(i + 1) % len(pts)]
        span = (b_ang - a_ang) % 360.0
        off = (theta - a_ang) % 360.0
        if off <= span:
            return a_val + (b_val - a_val) * (off / span if span else 0.0)
    return pts[0][1]


def lum_per_step(table: Dict[str, Tuple[int, int, int]], mat: str) -> float:
    """How much luminance one step of this material's ramp is worth.

    Wildly different per material, which is why the correction is computed in LUMINANCE and only
    then converted: the flesh ramp spends 1.8 points a step and the linen 7.4.
    """
    n = STEPS[mat]
    hi = lum(table[mat + "0"])
    lo = lum(table[mat + "0123456789abcdef"[n - 1]])
    return max(0.35, (hi - lo) / (n - 1))


def _col_angles(face: str, n: int) -> List[float]:
    k = CYCLE.index(face)
    return [90.0 * k + 90.0 * (j + 0.5) / n for j in range(n)]


# HOW FAR THE LIGHT MAY PUSH ONE MATERIAL, as a share of its own ramp. Measured: without this, a
# 14-point graze on the arm ate the abraded `R` ramp whole — 24.3 luminance points over six steps —
# so an elbow authored across R0..R4 arrived on disk as two tones, and the two barefoot bodies stayed
# under the distinct-colour floor however much real content was drawn into them.
#
# A third is the number because a ramp is authored to model something; spend more than a third of it
# on the light and the light IS the modelling. The wide ramps then carry the correction the narrow
# ones could not, which is exactly what `relight`'s loop is for.
LIGHT_BITE = 1.0 / 3.0


def _shift(table: Dict[str, Tuple[int, int, int]], ch: str, off_lum: float, t: float) -> str:
    """One cell moved `off_lum` luminance points, in its own ramp's steps, dither included."""
    mat, steps, was = ch[0], STEPS[ch[0]], int(ch[1], 16)
    room = LIGHT_BITE * (steps - 1)
    off = max(-room, min(room, off_lum / lum_per_step(table, mat)))
    want = math.floor(was + off + t)
    return mat + "0123456789abcdef"[max(0, min(steps - 1, want))], want, steps


def uncorner_seams(art: Dict[str, List[str]], dims: Tuple[int, int, int, int, int],
                   moved: List[str] | None = None) -> Dict[str, List[str]]:
    """Move a stitched seam off a WRAP COLUMN, one column in.

    A wrap column is the figure's own silhouette edge — the last column of one face against the
    first of the next — and `T` is deliberately the darkest thing in the wardrobe, "cooler and
    darker than the cloth either side of it", on a six-step ramp. A vertical `T` run drawn there is
    therefore a dark line down the outline that no lighting correction can lift: `T0` is already the
    ramp's lightest step, so the relight clamps and the column stays 25 to 36 luminance points off
    its neighbour. Measured: those seams were **16 of the 16** wrap faults left after the relight,
    all of them on the hem, on nine of the fourteen bodies.

    It is also the better drawing. On a four-wide leg box, `right` col 3 is the front corner and col
    0 the back one; a skirt's gore seam runs down the OUTSIDE of the leg, which is cols 1..2. The
    seam was at the corner because the corner is where a hand-written net's first character is, not
    because a garment is made that way.

    Only `T`, and only a run of two courses or more. A `K` girdle crosses every column of a row by
    design and a single `T` is a stitch rather than a seam.
    """
    _, _, w, _, d = dims
    widths = {"right": d, "front": w, "left": d, "back": w}
    out = {f: list(rows) for f, rows in art.items()}
    for face in CYCLE:
        n = widths[face]
        grid = [cells(r) for r in out[face]]
        for edge, inner in ((0, 1), (n - 1, n - 2)):
            run = [i for i, row in enumerate(grid)
                   if row[edge] != ".." and row[edge][0] == "T"]
            if len(run) < 2:
                continue
            for i in run:
                if grid[i][inner] == "..":
                    continue
                grid[i][edge], grid[i][inner] = grid[i][inner], grid[i][edge]
            if moved is not None:
                moved.append(f"{face} col {edge} -> {inner}, {len(run)} courses")
        out[face] = ["".join(row) for row in grid]
    return out


# THE OTHER HALF OF THE LIGHT. This file has always said "light comes from above AND from the
# figure's front-left", and only the second half was ever drawn: the four side faces had no vertical
# trend at all, so a shoulder and a hem were lit the same. From above means a side face is grazed —
# lighter at the top course, darker at the bottom, where the ground occludes it.
#
# It is also where the density lost to the relight comes back. Removing four symmetric borders took
# 14 bodies from 101..122 distinct colours to 94..113 and put four of them under the floor of 100,
# because the borders WERE the horizontal range. A vertical trend is range that means something:
# 12 luminance points over the height of a box, dithered, which is under two steps of linen and
# never a band.
#
# Applied ONCE, before `relight`, and deliberately not inside its loop: the loop corrects column
# MEANS, and a trend with zero mean down the column survives it untouched while anything the loop
# has to undo would be added again every round.
# Per box, and the SIGN is measured off the author's own skin rather than assumed. On his
# `default_skin.png` the torso and the arms run 21..49 points lighter at the top, and the LEGS
# run 37..48 points lighter at the BOTTOM — a thigh sits in the torso's own shadow and the shoe
# below it catches the light. Our hose was drawn that way already; a uniform "top lighter" would
# have fought it.
GRAZE = {"head": 8.0, "body": 12.0, "arm": 14.0, "leg": -12.0, "leg_outer": 8.0}


def graze(art: Dict[str, List[str]], dims: Tuple[int, int, int, int, int], person: dict,
          box: str) -> Dict[str, List[str]]:
    """Light from above, on the four side faces. Top course lighter, bottom course darker."""
    table = palette(person)
    _, _, w, h, d = dims
    widths = {"right": d, "front": w, "left": d, "back": w}
    frozen = FROZEN.get(box, {})
    out = {f: list(rows) for f, rows in art.items()}
    for face in CYCLE:
        rows = out[face]
        n = widths[face]
        got_rows = []
        for cy, r in enumerate(rows):
            row = cells(r)
            lift = GRAZE.get(box, 12.0) * (
                0.5 - (cy / (len(rows) - 1) if len(rows) > 1 else 0.5))
            got = []
            for j, ch in enumerate(row):
                if ch == ".." or j in frozen.get(face, ()) or j >= n:
                    got.append(ch)
                    continue
                t = (BAYER4[cy % 4][j % 4] + 0.5) / 16.0
                got.append(_shift(table, ch, -lift, t)[0])
            got_rows.append("".join(got))
        out[face] = got_rows
    return out


RAMP_SPAN_SHARE = 0.15


def ramp_span(art: Dict[str, List[str]], table: Dict[str, Tuple[int, int, int]]) -> float:
    """The narrowest ramp this box MAINLY uses, end to end, in luminance points.

    What limits how hard a box can be lit, and it is measured twice over. Not the whole legend's
    narrowest: a barefoot person's hose never touches the twelve-step leather ramp, and charging her
    for it cost her the range her wool could easily carry. And not the narrowest ramp present
    either — the same woman's bare foot is a handful of 28.5-point flesh cells on a leg box that is
    otherwise 125 points of wool, and letting those few cells set the limit held her hose to 12 of
    its 16 steps and the whole body to 98 distinct colours against a floor of 100.

    So: the narrowest ramp covering at least 15% of the box. A material below that share clamps at
    its own ends instead, which `relight`'s loop then absorbs into the columns around it.
    """
    seen: Dict[str, int] = {}
    total = 0
    for rows in art.values():
        for r in rows:
            for ch in cells(r):
                if ch != "..":
                    seen[ch[0]] = seen.get(ch[0], 0) + 1
                    total += 1
    if not total:
        return 999.0
    big = [m for m, n in seen.items() if n >= RAMP_SPAN_SHARE * total] or list(seen)
    return min(lum_per_step(table, m) * (STEPS[m] - 1) for m in big)


def _column_lum(art: Dict[str, List[str]], face: str, n: int,
                table: Dict[str, Tuple[int, int, int]]) -> List[float | None]:
    grid = [cells(r) for r in art[face]]
    out: List[float | None] = []
    for j in range(n):
        vals = [lum(table[row[j]]) for row in grid if row[j] != ".."]
        out.append(sum(vals) / len(vals) if vals else None)
    return out


def relight(art: Dict[str, List[str]], box: str, dims: Tuple[int, int, int, int, int],
            person: dict, rounds: int = 4,
            clamped: List[str] | None = None) -> Dict[str, List[str]]:
    """One box's ASCII, relit by a single source instead of four borders.

    Returns a new art dict; the input is left alone, because `ART` is module state and a person
    drawn twice would otherwise be relit twice.

    `top` and `bottom` take the FRONT face's own column corrections. Their column axis is the same
    x as the front's — a box's net unwraps them above it — so anything else would put a step along
    the shoulder or the sole.

    RUN MORE THAN ONCE, and that is not a fudge. A correction is spent in whole ramp steps, and a
    step is worth 1.8 luminance points on the flesh and 8.6 on the linen, so one pass leaves up to
    half a step of the target unspent — which on the hem came to 12 points, the seam floor itself.
    Re-measuring and re-correcting converges in three; the fourth is there to prove it has.

    What it CANNOT correct is a column the ramp cannot reach: a stitched `T` seam is six steps of a
    dark thread and no offset makes it linen. Those are collected in `clamped` and reported, because
    the answer to one is to move the seam off the box's corner in the ART — a seam drawn exactly at
    a wrap column is a dark line at the figure's edge whatever the lighting does.
    """
    table = palette(person)
    _, _, w, _, d = dims
    widths = {"right": d, "front": w, "left": d, "back": w}
    frozen = FROZEN.get(box, {})
    cur = {f: list(rows) for f, rows in art.items()}

    live = [v for face in CYCLE for v in _column_lum(cur, face, widths[face], table)
            if v is not None]
    if not live:
        return cur
    mean_now = sum(live) / len(live)
    swing = min(max(live) - min(live), SWING_CAP.get(box, 34.0),
                0.85 * ramp_span(cur, table))

    # where the light says each column should sit. Fixed once, off the drawing as authored, so the
    # rounds converge on one target rather than chasing their own output.
    target: Dict[str, List[float | None]] = {}
    for face in CYCLE:
        n = widths[face]
        target[face] = [None if j in frozen.get(face, ())
                        else mean_now + swing * (0.5 - shade_at(theta))
                        for j, theta in enumerate(_col_angles(face, n))]

    for _ in range(rounds):
        delta: Dict[str, List[float]] = {}
        moved = False
        for face in CYCLE:
            n = widths[face]
            now = _column_lum(cur, face, n, table)
            delta[face] = [0.0 if (now[j] is None or target[face][j] is None)
                           else target[face][j] - now[j] for j in range(n)]

        nxt: Dict[str, List[str]] = {}
        for face, rows in cur.items():
            d_for = delta["front"] if face in ("top", "bottom") else delta.get(face)
            if d_for is None:
                nxt[face] = list(rows)
                continue
            out_rows = []
            for cy, r in enumerate(rows):
                row = cells(r)
                got = []
                for j, ch in enumerate(row):
                    if ch == ".." or j >= len(d_for):
                        got.append(ch)
                        continue
                    # a positive delta wants the cell LIGHTER, which is a LOWER step. The
                    # fraction of a step is DITHERED rather than rounded away — see `BAYER4`.
                    t = (BAYER4[cy % 4][j % 4] + 0.5) / 16.0
                    now, want, steps = _shift(table, ch, -d_for[j], t)
                    got.append(now)
                    if now != ch:
                        moved = True
                    if clamped is not None and not 0 <= want < steps:
                        clamped.append(f"{box}.{face} col {j} {ch}: {ch[0]} ramp cannot go "
                                       f"{'lighter' if want < 0 else 'darker'}")
                out_rows.append("".join(got))
            nxt[face] = out_rows
        cur = nxt
        if not moved:
            break
    return cur


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
    """One person, symbolically. The palette is applied afterwards.

    Every box is relit before it is stamped, and the RELIT art is what both limbs are stamped from,
    so the left stays the exact mirror of the right and `verify`'s byte-for-byte comparison holds.
    """
    a = ART[slug]
    who = PERSON[slug]
    sym = blank()

    def lit(key: str, box: str, name: str) -> Dict[str, List[str]]:
        dims = BOXES[box]
        art = graze(uncorner_seams(a[key], dims), dims, who, name)
        return relight(art, name, dims, who)

    stamp(sym, "head", lit("head", "head", "head"))
    # `hat` deliberately untouched: it is hair, headwear and the beard, painted on their own layer.
    body = lit("body", "body", "body")
    arm = lit("arm", "r_arm", "arm")
    leg = lit("leg", "r_leg", "leg")
    hem = lit("leg_outer", "r_leg_outer", "leg_outer")
    stamp(sym, "body", body)
    stamp(sym, "r_arm", arm)
    stamp(sym, "l_arm", arm, mirror=True)
    stamp(sym, "r_leg", leg)
    stamp(sym, "l_leg", leg, mirror=True)
    stamp(sym, "r_leg_outer", hem)
    stamp(sym, "l_leg_outer", hem, mirror=True)
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


def symbolic_divergence() -> Tuple[List[Tuple[str, str, float]], Tuple[str, str, float] | None]:
    """How much of each pair's ASCII actually differs, WITH THE PALETTE IGNORED.

    The gate that makes "hand-drawn, one person per file" a measurement instead of a claim. The 48
    files this pipeline replaced were one drawing under 24 palettes and every one of them was
    dense in exactly the same places; a distinct-colour count cannot tell that apart from twelve
    drawings, because a repaint of a dense drawing is still dense. This can: it compares the
    symbolic cells — material letter and ramp step — so two people who share a shape score 0
    however differently they are coloured.

    Measured on the two bodies drawn first: 72.1% of sampled cells differ (head 86, torso 76,
    arms 65, legs 47, leg outer 94). The legs are the closest even between a man and a woman,
    which is why the floor is half of the whole-body figure rather than near it.
    """
    used = sampled_pixels()
    syms = {p["slug"]: draw(p["slug"]) for p in PEOPLE}
    pairs = []
    for a, b in itertools.combinations([p["slug"] for p in PEOPLE], 2):
        sa, sb = syms[a], syms[b]
        diff = tot = 0
        for y in range(64):
            for x in range(64):
                if (x, y) not in used:
                    continue
                p, q = sa[y][x], sb[y][x]
                if p is None and q is None:
                    continue
                tot += 1
                if p != q:
                    diff += 1
        pairs.append((a, b, diff / tot if tot else 0.0))
    pairs.sort(key=lambda t: t[2])
    return pairs, pairs[0] if pairs else None


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


# ── hair, beards and headwear: PAINT on the `hat` cube ───────────────
#
# WHAT THIS REPLACES. A pass before this one built 42 baked cubes for them, on the argument that a
# texture cannot carry a silhouette on this rig. The number that refutes it was already measured
# and already in the brief that commissioned the geometry: of the 31 references, **31 use the head's
# second layer**. That is how every hat, hood and long hair in Minecraft is drawn — an inflated
# shell whose ALPHA carves the outline — and vanilla ships no hair models at all. The cubes also
# cost a black screen. `NpcHeadModels` and `NpcHeadLayer` are retired on disk, unreferenced.
#
# MEASURED ON THE SAME 31, so the paintings are the right size:
#
#     head second layer painted                        31 of 31
#     coverage of the cube's 384 net texels            median 73  (min 10, max 316)
#     reaches the CHIN course (row 7 of a side face)   23 of 31
#
# HAIR PAST THE JAW — asked, measured, and the answer is DO NOT. The worry was that shoulder-length
# hair needs the torso's second layer, which `NpcClothesLayer` owns for the trade tunic. Matching
# each reference's shoulder paint against its own crown colour: of the 24 that paint both, **2
# continue the hair colour onto the torso and 15 put a garment there**. So the corpus stops at the
# head too, and no ordering rule between the two layers is needed. The cube's bottom edge is the
# chin, 23 of 31 references paint down to that course, and jaw-length is both the rig's ceiling and
# the corpus's habit.
#
# THE ONE THING THE GEOMETRY COULD DO AND THIS CANNOT: a BRIM. A shell 0.5 outside the head cannot
# project past it, so a wide-brimmed straw hat is not available and what is drawn instead is a
# brimless straw cap with a band. That is the whole cost of the correction and it is worth stating.

# The paintings are near-white greys and the tint carries the colour, exactly as the retired
# `npc_hair.png` was (measured: 195..228 grey). A multiply can only darken, so a painting drawn
# dark can never be fair hair or bleached linen.
#
# EIGHT STEPS WAS THE LIMITING FACTOR, and it measured like one: the thirteen paintings shipped at
# 5..8 distinct tones over 60..295 texels, which is 0.03..0.05 tones a texel against the bodies'
# 0.08 and the references' hat median of 0.07. A silhouette in eight tones has no interior at all,
# and the owner's verdict was that the outlines are settled and the insides are not.
#
# A LEGEND CANNOT CARRY THE DENSITY, AND THAT IS WHY THE ART NO LONGER DECIDES THE TONE. The pass
# before this one answered the flatness by widening the legend — sixteen `W` steps plus eight `V`,
# twenty-four in all — and wrote the constants without ever writing the pass that would use them.
# It could not have worked: a cell is a letter and ONE hex digit, the art only ever uses `W0..W7`,
# and a painting whose every tone is authored by hand can hold no more tones than there are
# characters. Twenty-four is also arithmetically short of the target. THE BODIES' OWN ARCHITECTURE
# is the answer and it was already in this file: author the FORM in a handful of steps, then compute
# the modelling in LUMINANCE and quantise onto a ramp fine enough that the rounding is invisible.
# `graze` and `relight` do exactly that for a body, over 15 materials and 128 steps; `model_head`
# below does it for a covering, over one grey ramp of 169.
#
# SO THE AUTHORED EIGHT ARE NOW THE FORM AND NOTHING ELSE — where on the head we are, crown lit and
# nape shadowed. Not a painting had to be rewritten to gain the range, and the silhouettes, which
# the owner has ruled settled, are untouched: the alpha is exactly the alpha that was authored.
#
# HOW FAR DOWN, and it is measured off the tints rather than guessed. `CitizenLook`'s hair colours
# run from `0xFF231F1C` (black, dominant channel 0x23) to `0xFFA8834E` (fair, 0xA8). A multiply by
# black turns the whole 0x54..0xFC range below into 11..34 — twenty-three luminance points, which is
# three times this repo's invisibility threshold and no more. By fair it becomes 55..166. So the
# floor is 0x54 rather than the braid's 0x70 because a parting has to be visible in fair hair, and
# the arithmetic also says plainly what the ceiling on this work is: BLACK HAIR CANNOT SHOW MORE
# THAN ABOUT THREE DISTINGUISHABLE TONES whatever is drawn, and the density below is spent for the
# nine lighter tints and for the anti-banding.
HEAD_HI, HEAD_LO = 0xfc, 0x54

# THE FINE SCALE, one luminance point a step. 169 steps from the lit crest to the floor, which is
# every integer grey in between and the most a monotone grey ramp can carry without `ramp` refusing
# it for a collapsed step. One point is a seventh of this repo's measured invisibility threshold, so
# no single step of it can be seen and the whole of it is there for the same reason the bodies keep
# 128: a correction spent in whole steps is a correction rounded away, and the rounding is what a
# fine ramp buys back. It is also where the distinct-colour count comes from — 8 authored tones
# cannot be 100 tones however they are arranged.
HEAD_FINE = ramp([(HEAD_HI,) * 3, (HEAD_LO,) * 3], HEAD_HI - HEAD_LO + 1)

# THE AUTHORED ALPHABET: eight steps of FORM, 9.4 luminance points apart. Above this repo's
# invisibility threshold of 7, so every authored step reads; and 66 points in total, which leaves
# the rest of the ramp for the interior. `W` is the only letter the art uses and the only one it
# may use — `materialise_head` no longer looks a cell up in a legend at all, `model_head` converts
# it, and an unknown letter raises there.
#
# KEPT AS FLOATS, and that is not fussiness. Rounded to whole luminance points the eight form steps
# land exactly on eight steps of the one-point ramp, so every other field's contribution rounds into
# the same buckets and the tones collide: rounding these cost 8 distinct colours a file, measured, on
# a target the paintings were already short of. A step of 11 leaves ten different fractions in play
# and they beat against the graze's 1.14 and the light's own curve instead of agreeing with them.
#
# ELEVEN AND NOT MORE, and the ceiling is arithmetic. 7 steps of form + 28.5 of lock + 34.6 of tip +
# 24 of the light's swing + 4 of the graze + 3 of noise has to land above `MIN_HEAD_TONE`; at 11 the
# darkest texel of the thirteen paintings is 0x64, sixteen points clear of the floor, and at 12.5 it
# is 0x5a with two points to spare and five more tones a file, which is not a trade worth making. The
# shipped set spanned 244..116 in eight flat steps, so this is a NARROWER form than what it replaces,
# spending the difference on the interior.
HEAD_FORM_STEP = 11.0
HEAD_FORM_LUM = [HEAD_HI - i * HEAD_FORM_STEP for i in range(8)]

# The alphabet, as one statement. `_form_index` reads its keys rather than repeating them, because
# two copies of a legend is the failure this repo has already paid for in `npc_uv.py` and `solids.py`.
HEAD_LEGEND = {"W" + "01234567"[i]: tuple([int(round(l))] * 3)
               for i, l in enumerate(HEAD_FORM_LUM)}


def _form_index(ch: str) -> int:
    """The authored cell as a form step 0..7. 0 is the lit crest."""
    if ch not in HEAD_LEGEND:
        raise SystemExit(f"a covering is authored in {'/'.join(sorted(HEAD_LEGEND))} and this cell "
                         f"is '{ch}' — the form is eight steps and the interior is computed by "
                         f"`model_head`, so there is no wider legend to reach for")
    return int(ch[1])


def _fine(l: float, t: float) -> int:
    """A luminance as an index into `HEAD_FINE`, the fractional step DITHERED rather than rounded.

    The ramp is one point a step, so the fraction is worth less than a point and no dither could
    make it visible — but it is spent the same way `_shift` spends a body's, because a bias of half
    a step applied to every texel of a face IS a tone shift, and because the dither pattern is
    itself one of the things that keeps a large flat panel off a single tone.
    """
    return max(0, min(len(HEAD_FINE) - 1, int(math.floor((HEAD_HI - l) + t))))

# THE FACE WINDOW, and it is a gate rather than a hope. Two of an earlier generated garment set had
# an opaque `hat` cube walling up the face — 80 of 80 pixels on its front, invisible in the net and
# unmissable on a figure. The eyes are on head-front row 4 at cols 1,2,5,6 and the nose on row 5,
# so rows 3..5 cols 1..6 may never be painted by anything; the mouth is row 6 cols 3-4 and may not
# be either, which is what stops a moustache becoming a gag at this scale. Row 7 is the chin and is
# a beard's to take, and cols 0 and 7 are outside the window at every row — that is where hair
# falls past the temple.
FACE_WINDOW = ({(cx, cy) for cy in (3, 4, 5) for cx in range(1, 7)}
               | {(3, 6), (4, 6)})

# Coverage bounds. The floor is under the references' minimum of 10 because a stubble is legitimately
# less than a crop.
#
# THE CEILING IS GEOMETRIC NOW AND NOT THE CORPUS'S 316, and the reason is the bug this pass fixed.
# 316 is the references' maximum, and it was a fair ceiling right up until the `left` face was found
# blank on all thirteen paintings: every coverage number the set had ever reported was a side short,
# so the ceiling had been calibrated against a systematically broken measurement. A wimple that
# covers the whole head but the face is 384 texels less the 20 the face window forbids, and that IS
# the drawing — the references' 316 is the widest HAIR in a corpus of skins, not the widest covering
# a head can wear. So the ceiling is what the cube physically leaves: everything but the window.
MIN_HEAD_COVER = 8
MAX_HEAD_COVER = 6 * 64 - 20

# TONES PER TEXEL, not a flat count, because a stubble is 72 texels and a wimple is 359 and one
# floor cannot mean the same thing to both. It was DECLARED BY THE PREVIOUS PASS AND NEVER READ —
# `verify_head` gated on the flat count alone, which is why a 60-texel beard and a 295-texel wimple
# were held to the same ten.
#
# 0.22 AND NOT THE 0.25 THAT WAS WRITTEN DOWN, and lowering a floor needs the better measurement.
# 0.25 came off "the four hand-drawn dense references at 0.26..0.61" — and those four paint 15, 37,
# 54 and 66 texels. A small painting has a high ratio for free: three cells cannot help being three
# tones. Measured on the eleven references that paint a COMPARABLE area, 100 hat texels or more, the
# median is **0.073** and the range 0.019..0.735. So 0.22 is three times the median of the corpus at
# this size and under a third of its maximum, and the thirteen paintings clear it by 40% or better.
HEAD_TONES_PER_TEXEL = 0.22
MIN_HEAD_TONES = 10        # and never fewer than this, whatever the coverage

# AND THE BAND THE BODIES SET, REPORTED AND NOT GATED. The brief asks for the coverings to reach what
# the bodies reach — 101..122 distinct colours, against the references' 139 median — and four of the
# thirteen do. The other nine cannot, and the reason is arithmetic rather than effort: a painting
# holds no more colours than it has texels, and these paint 72..359 where a body paints 1632. The
# 72-texel stubble's ceiling is 72. `main` prints each file's count, its ceiling and its ratio, which
# is the only form in which the comparison is honest.
HEAD_COLOUR_BAND = (101, 122)

# The darkest a covering may be drawn. The braid's floor is 0x70 because a braid has to be able to
# become gold; a PARTING has to be able to be seen in fair hair, which is a multiply by 0xA8, so
# 0x54 lands it at 55 against the crest's 166.
#
# THIS CONSTANT WAS DEAD. `verify_head` tested `MIN_BRAID_TONE` — the braid's 0x70 — on a covering,
# so the one number with an argument written above it was never the one enforced. Wired up here; it
# is not what lets anything pass, because the paintings bottom out around 0x76 either way.
MIN_HEAD_TONE = 0x54

# A LOCK OF HAIR, across four columns: the lit crest, the turn, the deep between two locks, the turn
# back. Two locks to an eight-wide face, which is the coarsest a lock can be and still be a lock at
# this scale. Spent in LUMINANCE rather than in ramp steps, for the same reason `graze` and `relight`
# are: 28 points is four times this repo's invisibility threshold whatever ramp carries it, and a
# lock has to be CONTRAST and not a ramp step — law 3 of the skin skill arriving on a head.
LOCK_DEPTH = 28.5

# WHERE THE CREST SITS, and it is the whole reason this is a cosine of a folded coordinate rather
# than a four-entry table indexed by column. A lock is a field on the box, not on a face: index it
# per face and the four columns of the pattern restart at every corner, which puts a 14-point step
# at each of the four seams — `check_wrap` exists to catch exactly that and would have. So the field
# is evaluated on the distance round the box from the FRONT MIDLINE, folded, which makes it
#
#   * mirror-symmetric by construction, so `left` really is the mirror of `right`, and
#   * seam-continuous, because a crest lands on the front midline, the back midline AND all four
#     corners at once.
#
# Measured on the 31 references' own hat cubes before being believed: their seam steps there run
# median 4.0, p90 15.3, and 12 of the 31 carry one over the 12-point floor. So a corner step is not
# unheard of in the corpus — but the median says a covering normally wraps continuously, and that is
# the standard held to here.
LOCK_PERIOD = 4.0

# THE PHASE WALKS ONE COLUMN AT TWO COURSES, and not per course: `docs/STYLE.md`, "hold the columns,
# displace one course per panel", because per-course jitter on cloth 21 points apart is what read as
# tartan. It also costs almost nothing at the seams and the arithmetic says why: an EVEN phase keeps
# the crest on the corner exactly, an odd one does not, so only the 3 courses of 8 that carry phase 1
# are discontinuous there — 3/8 of 0.71 of the depth, which is 7.6 points and under the floor.
LOCK_PHASE_AT = (3, 6)

# How hard each kind is modelled. Hair falls in locks and takes the full amplitude; a cloth cap
# folds instead, which is softer, and a beard is short so its locks are shallower.
LOCK_GAIN = {"hair": 1.0, "beard": 0.75, "headwear": 0.6}

# A HAIR TIP IS DARKER AND THINNER THAN ITS ROOT. Two courses of it at the bottom edge of every
# column, which is where the silhouette ends — so it is drawn off the ALPHA and cannot be authored
# wrong, and a ragged hem therefore gets a ragged tip for free. Cloth gets it too: the bottom edge of
# a veil is in its own shadow. In lock steps, so it scales with `LOCK_DEPTH`.
TIP = (4, 2)

# AND THE TIP LEANS ON THE LOCK: deeper between two locks than on a crest. A lock is a rope of hair
# and its end is a tuft, not a straight cut, so the two fields are not independent — and this is also
# the repo's silhouette rule turned inward, no two neighbouring strand ends the same depth. 0.7 to
# 1.3 of the nominal tip.
TIP_LOCK_LEAN = (0.7, 0.6)

# LIGHT FROM ABOVE, on the four upright faces: top course lighter, bottom course darker. Eight
# points, which is what `GRAZE` gives the head box on a body, and it is also the pass that fills the
# fine ramp in — a trend of 8 points over 7 courses is 1.14 a course, so every course lands on its
# own step of a one-point ramp while the whole trend stays inside the invisibility threshold as a
# LOCAL difference and reads only as form.
HEAD_GRAZE = 8.0

# The same, front to back, on `top` and `bottom`. Four points, and `top` row 7 is the front edge.
# `bottom`'s row order is taken to match `top`'s rather than measured, which is honest about the one
# thing here that is a guess: four points is half the invisibility threshold, so getting the sign
# wrong on the underside of a jaw costs nothing anyone can see, and it is there for the fine fill.
HEAD_GRAZE_TOP = 4.0

# AND THE OTHER HALF OF THE LIGHT: one source, round the box, `LIGHT_SHADE` and `shade_at` — the same
# model the bodies are relit by, applied the same way, by correcting each column's MEAN rather than by
# adding a field. Front brightest at 135 degrees, back darkest at 315, the figure's left a little
# lighter than its right, and CONTINUOUS in angle, so the value at a seam is the same from both sides
# by construction and no arithmetic can put a step there. The two lids take the FRONT's corrections,
# exactly as `relight` gives them the front's, because their column axis is the front's x.
#
# A CORRECTION AND NOT A FIELD, because the paintings have a VIGNETTE BAKED INTO THEM. Every course of
# every authored face runs `W3W2W1W1W1W1W2W3` — darker at both its own edges, on all four faces. That
# is a photograph's border, not a light, and it is precisely what `check_wrap` was built to catch: it
# scored the shipped coverings at 25 faults over the 13 files, mostly "TWO DARK COLUMNS together" at
# the corners. A field added on top cannot remove it; a column-mean correction can, and it keeps every
# cell's deviation from its own column, so the locks, the tips and the ragged edges all survive.
#
# THIS IS THE ONE THING THAT IS NOT MIRRORED, and it is measured rather than assumed. Of the 25
# references that paint a side face of the head shell, **2 are the exact pixel mirror of the other
# side and 17 differ by more than this repo's 7-point invisibility threshold** — a hand-drawn
# covering has symmetric HAIR and asymmetric LIGHT. `check_wrap` says the same thing from the other
# end: "one side lighter than the other... one minimum in the cycle, not four". So the strand pattern
# folds and the illumination does not, and `verify_head` gates the ALPHA as an exact mirror and the
# tone as within this swing.
#
# 24 POINTS IS `SWING_CAP["head"]`, the same number the bodies' head box is allowed, and for the same
# reason: a wider swing flattens the back of the head onto the end of the ramp.
HEAD_SWING = 24.0

# THE CROWN, on the two lids only. The top of a head is its highest point and takes the most light;
# the lid rolls away from it in every direction. Ten points, radial, and radial is deliberate — a lid
# shaded front-to-back alone was 64 texels of eight tones, which is a quarter of the cube and where a
# covering's flatness was most visible on the contact sheet.
HEAD_CROWN = 10.0

# STRAND NOISE, and it is called noise because that is what it is — ANTI-BANDING, not modelling.
# Every field above is a smooth function of two numbers (where a column sits round the box, and which
# course it is on), and smooth fields collide when they are quantised: four of the paintings landed
# 60-odd tones into a range with room for 130 because two different cells kept rounding to the same
# step. Six points peak to peak is UNDER this repo's measured invisibility threshold of 7, so it can
# only break the ties and can never be seen as speckle — and hair is genuinely not a smooth surface,
# which is why an artist dithers it.
#
# KEYED ON THE FOLDED RING INDEX, never on the column. That is what keeps the painting an exact
# mirror of itself: `int(m - 0.5)` is the same number for `right` col 7 and `left` col 0, so the noise
# lands identically on the two sides and `verify_head` can compare them cell for cell.
STRAND_NOISE = 3.0


def blank_head() -> List[List[int | None]]:
    return [[None] * 64 for _ in range(64)]


E = ".." * 8       # an empty course of the 8-wide cube


def mirror_left(art: Dict[str, List[str]]) -> Dict[str, List[str]]:
    """`left` is the horizontal MIRROR of `right`. THE BUG, and the second time this shape of it.

    Every one of the thirteen paintings authored `right` and left `left` at exactly zero texels, so
    in game a citizen's hair was missing down one side of the head. It is a repeat: `left_arm` and
    `left_leg` were empty in every texture the mod shipped for a whole revision, because the retired
    villager mesh mirrored its right limbs and the player mesh does not — `remap_npc_uv.py` exists
    because of that, and `npc_uv.MIRROR_SWAP` is the transform it left behind.

    MIRRORED AND NOT COPIED, and the net says why. The four upright faces unwrap right, front, left,
    back, and that strip is the box ROLLED — so `right` col 7 is against `front` col 0 while `left`
    col 0 is against `front` col 7. Copying `right` across would put the back edge of the head where
    its front edge belongs and the painting would break at both seams; flipping it puts the front
    edge at col 0, where the front edge is.

    Measured on the corpus before being believed: of the 31 references, **0 paint one side face and
    leave the other blank**, and of the 25 that paint both the two sides differ in coverage by a
    median of 4%. Side to side, a head covering is symmetric — unlike a body, where wear differs.

    An authored `left` is left alone, so a future asymmetric covering is possible; `verify_head`
    gates on the result rather than on this.
    """
    if art.get("left"):
        return art
    if not art.get("right"):
        return art
    return dict(art, left=["".join(reversed(cells(r))) for r in art["right"]])


def _ring_m(face: str, cx: int, n: int) -> float:
    """How far round the box a column sits, from the FRONT MIDLINE, folded so both ways agree.

    Half-integers, because a column is a cell and its centre is the thing being measured. `front`
    spans -3.5..+3.5, `left` +4.5..+11.5, `right` -4.5..-11.5 and `back` +12.5..+19.5, which wraps
    to -12.5..-19.5 from the other side and folds onto the same values. `top` and `bottom` share the
    front's x axis — a box's net unwraps them above it — so they are asked for the front's number.

    That fold is what makes the lock both mirror-symmetric and seam-continuous. See `LOCK_PERIOD`.
    """
    half = n / 2.0
    if face == "front":
        d = (cx + 0.5) - half
    elif face == "left":
        d = half + (cx + 0.5)
    elif face == "right":
        d = -(half + (n - cx - 0.5))
    else:                                    # back, and the two lids via the front
        d = half + n + (cx + 0.5)
    total = 4.0 * n
    a = abs(d) % total
    return min(a, total - a)


def _lock_at(m: float, phase: int) -> float:
    """0 on a lock's lit crest, 1 in the deep between two locks."""
    return (1.0 - math.cos(2.0 * math.pi * (m + phase) / LOCK_PERIOD)) / 2.0


def _phase_at(cy: int) -> int:
    return sum(1 for c in LOCK_PHASE_AT if cy >= c)


# The six faces in a fixed order, so the noise below keys on a number and not on a dict's iteration.
HAT_FACES = ("top", "bottom", "right", "front", "left", "back")


def _strand(face: str, k: int, cy: int) -> float:
    """Deterministic anti-banding, ±`STRAND_NOISE`, one value per cell and MIRROR-SYMMETRIC.

    `k` is the folded ring index, so the two side faces are handed the same number at mirrored
    columns and the painting stays its own mirror. `left` and `right` are deliberately given the SAME
    face key for that reason; anything keyed on the column would put a six-point difference between
    the two sides, which is under the invisibility threshold but not zero, and an invariant that can
    be checked exactly is worth more than one that cannot.
    """
    key = "right" if face == "left" else face
    n = (HAT_FACES.index(key) * 977 + k * 131 + cy * 37) * 2654435761 % 4294967291
    return STRAND_NOISE * (2.0 * (n % 2048) / 2047.0 - 1.0)


def _lock_column(face: str, cx: int, w: int, h: int, gain: float) -> float:
    """The lock's own contribution to one column's mean, in luminance and ALPHA-INDEPENDENT.

    Averaged over the whole height of the face rather than over the cells that happen to be painted,
    and that is the fix for a measured fault. Taken over the painted cells instead, a column with one
    cell in it — the corner of a beard's jaw — reported the full depth of a single tip texel as its
    column's intent, and the relight then sat that column 27 points below the light: a 30-point step
    at the seam, on `citizen_beard_03`, where its neighbour had five cells and reported a third of it.
    Nominal is also mirror-symmetric by construction, which the painted subset is not.
    """
    return -gain * LOCK_DEPTH * sum(
        _lock_at(_ring_m(face, cx, w), _phase_at(cy)) for cy in range(h)) / h


def _head_fields(kind: str, art: Dict[str, List[str]]) -> Dict[str, List[List[float | None]]]:
    """One painting as LUMINANCE, before the light. Five fields, all of them mirror-symmetric.

      * the FORM, authored, eight steps of 11 points: crown lit, nape shadowed.
      * the LOCKS, a 4-column field on the BOX rather than on a face, 28.5 points deep, its phase
        walking one column at two courses.
      * the TIPS, taken off the ALPHA so they follow a ragged hem, leaning on the lock.
      * the GRAZE and the CROWN — light from above on the four upright faces; a lid rolls away from
        its highest point in every direction.
      * STRAND NOISE, six points peak to peak, which is anti-banding and says so.

    Every one keys on the FOLDED ring position, so at this stage the painting is exactly its own
    mirror and no corner can carry a step. `_relight_head` then puts the one asymmetric thing on it.

    THE LOCK IS THE ONE FIELD THE LIGHT MUST NOT EAT. A lock is a per-COLUMN pattern, and so is the
    vignette the paintings were authored with, so a column-mean correction cannot tell them apart: the
    first version cancelled both and the crown came off the banded read-out with its parting INVERTED.
    `_lock_column` therefore states the lock's own column offset, alpha-independent, and
    `_relight_head` hands it back — see the note there for why the tip is deliberately NOT handed back
    with it.
    """
    gain = LOCK_GAIN[kind]
    fs = net(*BOXES["hat"])
    out: Dict[str, List[List[float | None]]] = {}
    for face, (_, _, w, h) in fs.items():
        rows = art.get(face, [E] * h)
        grid = [cells(r) for r in rows]
        if len(grid) != h or any(len(r) != w for r in grid):
            wrong = [(i, len(r)) for i, r in enumerate(grid) if len(r) != w]
            raise SystemExit(f"hat.{face}: {len(grid)} courses, the net wants {h}x{w} cells; "
                             f"wrong courses (index, cells) {wrong[:6]}")

        # where each column's paint ENDS, off the alpha. A hem that steps course by course gets a
        # tip that steps with it, which is the whole reason this is not authored.
        ends: Dict[int, Tuple[int, int | None]] = {}
        for cx in range(w):
            on = [cy for cy in range(h) if grid[cy][cx] != ".."]
            if on:
                ends[cx] = (on[-1], on[-2] if len(on) > 1 else None)

        upright = face in CYCLE
        got: List[List[float | None]] = []
        for cy in range(h):
            row: List[float | None] = []
            for cx in range(w):
                ch = grid[cy][cx]
                if ch == "..":
                    row.append(None)
                    continue
                l = HEAD_FORM_LUM[_form_index(ch)]

                m = _ring_m(face if upright else "front", cx, w)
                a = _lock_at(m, _phase_at(cy))
                l -= gain * LOCK_DEPTH * a

                if not upright:
                    rx, ry = cx - (w - 1) / 2.0, cy - (h - 1) / 2.0
                    l -= HEAD_CROWN * (math.hypot(rx, ry)
                                       / math.hypot((w - 1) / 2.0, (h - 1) / 2.0))

                if upright and cx in ends:
                    last, prev = ends[cx]
                    t = TIP[0] if cy == last else (TIP[1] if prev is not None and cy == prev else 0)
                    if t:
                        lean = TIP_LOCK_LEAN[0] + TIP_LOCK_LEAN[1] * a
                        l -= gain * t * (LOCK_DEPTH / 4.0) * lean

                span = (cy / (h - 1)) - 0.5 if h > 1 else 0.0
                l -= (HEAD_GRAZE if upright else HEAD_GRAZE_TOP) * span

                l += _strand(face, int(m - 0.5), cy)
                row.append(l)
            got.append(row)
        out[face] = got
    return out


def _relight_head(vals: Dict[str, List[List[float | None]]], gain: float,
                  moved: List[str] | None = None) -> Dict[str, List[List[float | None]]]:
    """One light source round the box, spent as a shift of each COLUMN's mean.

    `relight` for a covering, and simpler than the body's for one reason: there is a single ramp and
    the arithmetic is in luminance, so a correction is exact and one pass converges. The body's has to
    run four times because it spends in whole ramp steps of fifteen different materials.

    WHAT IT REMOVES is the vignette the paintings were authored with — `W3W2W1W1W1W1W2W3` on every
    course of every face, darker at both its own edges. Four vignettes round one box put two dark
    columns against each other at every corner, which is the fault `check_wrap` exists to catch and
    which it scored at 25 over the thirteen shipped files.

    WHAT IT MUST NOT REMOVE is the lock, and the first version did. A lock is a per-column pattern
    exactly like a vignette, so a correction that only knows the column mean cancels both — the crown
    came back off the banded read-out with its parting inverted. So each column's target carries
    `_lock_column`, the lock's own offset, and the correction only relights the remainder.

    THE TIP IS DELIBERATELY NOT HANDED BACK WITH IT. A tip is a bottom-of-column feature, so its
    contrast is against the cells above it in the same column and survives as deviation either way;
    its column MEAN, on the other hand, depends entirely on how many cells that column has, and
    handing that back put a 30-point step at `citizen_beard_03`'s jaw where a one-cell column reported
    a whole tip as its intent. Everything else — the strands within a column, the crown, the noise —
    is deviation from its own column and never moves.

    `top` and `bottom` take the FRONT face's whole correction, exactly as the body's `relight` does,
    because a box's net unwraps them above the front and their column axis is the front's x. Giving the
    lids their own correction instead was tried and measured worse on both counts: the crown's radial
    falloff is itself a per-column pattern, so an independent correction flattens it into the light —
    the lid came back darkest at its own two edges, which is the vignette again — and the thirteen
    files lost 8 to 19 distinct colours each. Borrowing the front's leaves the crown's midline
    columns darker than the two either side, which reads as a CENTRE PARTING rather than as the lock's
    crest; that is the one place the lid and the four upright faces disagree, and a parting is a
    hair feature, so it is left standing and recorded here rather than corrected away.
    """
    fs = net(*BOXES["hat"])
    widths = {f: fs[f][2] for f in fs}
    heights = {f: fs[f][3] for f in fs}

    def col_mean(grid: List[List[float | None]], cx: int) -> float | None:
        col = [r[cx] for r in grid if r[cx] is not None]
        return sum(col) / len(col) if col else None

    live = [v for f in CYCLE for r in vals[f] for v in r if v is not None]
    if not live:
        return vals
    mean_now = sum(live) / len(live)
    swing = min(max(live) - min(live), HEAD_SWING)

    keep = {f: [_lock_column(f, cx, widths[f], heights[f], gain) for cx in range(widths[f])]
            for f in CYCLE}
    flat = [v for f in CYCLE for v in keep[f]]
    keep_bar = sum(flat) / len(flat)

    delta: Dict[str, List[float]] = {}
    for face in CYCLE:
        out = []
        for cx, theta in enumerate(_col_angles(face, widths[face])):
            now = col_mean(vals[face], cx)
            want = mean_now + swing * (0.5 - shade_at(theta)) + keep[face][cx] - keep_bar
            out.append(0.0 if now is None else want - now)
        delta[face] = out
        if moved is not None:
            moved.append(f"{face} " + " ".join(f"{d:+.0f}" for d in out))

    got: Dict[str, List[List[float | None]]] = {}
    for face, rows in vals.items():
        d_for = delta["front"] if face in ("top", "bottom") else delta[face]
        got[face] = [[None if v is None else v + d_for[cx] for cx, v in enumerate(r)]
                     for r in rows]
    return got


def model_head(kind: str, art: Dict[str, List[str]]) -> Dict[str, List[List[int | None]]]:
    """One painting's authored FORM plus its interior, as indices into `HEAD_FINE`.

    THE SILHOUETTE IS NOT TOUCHED. Every cell the art left `..` stays transparent and every cell it
    painted stays painted; what changes is only the tone inside the outline, which is the division
    the owner drew — the outlines are settled, the insides are not.

    Fields, then the light, then the fraction DITHERED onto the one-point ramp rather than rounded
    away, exactly as `_shift` does for a body.
    """
    vals = _relight_head(_head_fields(kind, art), LOCK_GAIN[kind])
    out: Dict[str, List[List[int | None]]] = {}
    for face, (_, _, w, h) in net(*BOXES["hat"]).items():
        rows = vals[face]
        got: List[List[int | None]] = []
        for cy in range(h):
            # the dither's column is the FOLDED ring index, not the raw one: keying the Bayer
            # pattern on the column would put its own phase on the two sides differently.
            got.append([None if rows[cy][cx] is None else
                        _fine(rows[cy][cx],
                              (BAYER4[cy % 4][int(_ring_m(face if face in CYCLE else "front",
                                                          cx, w) - 0.5) % 4] + 0.5) / 16.0)
                        for cx in range(w)])
        out[face] = got
    return out


def stamp_head(sym, grids: Dict[str, List[List[int | None]]]) -> None:
    """Write one painting's six faces into the `hat` net and NOWHERE ELSE.

    The layer re-renders the whole model with this texture, so a texel outside the `hat` net would
    appear on the torso or the leg. `verify_head` counts that as a fault rather than trusting this.
    """
    for face, (x0, y0, w, h) in net(*BOXES["hat"]).items():
        rows = grids[face]
        for cy in range(h):
            for cx in range(w):
                v = rows[cy][cx]
                if v is not None:
                    sym[y0 + cy][x0 + cx] = v


def materialise_head(sym) -> Image.Image:
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = im.load()
    for y in range(64):
        for x in range(64):
            v = sym[y][x]
            if v is None:
                continue
            if not 0 <= v < len(HEAD_FINE):
                raise SystemExit(f"head tone {v} at ({x},{y}) is off the {len(HEAD_FINE)}-step ramp")
            px[x, y] = HEAD_FINE[v] + (255,)
    return im


# ── the hair ─────────────────────────────────────────────────────────
#
# Light from above and from the figure's front-left, held to as the bodies hold to it: the crown is
# W0..W2, the nape W5..W7. On a `right` face col 7 is the frontmost edge and so the lit one; on
# `top`, row 7 is the front edge. The ragged edges are the point — a hair painting's silhouette is
# its whole identity, and a straight bottom edge reads as a swim cap.

HAIR = {
    # 00 A CROP. The shortest, and it is index 0 because index 0 is never absent — a citizen who
    # rolls nothing else still has to have hair.
    "00": {
        "top":    ["W2W1W0W0W0W0W1W2", "W1W0W0W0W0W0W0W1", "W1W0W0W0W0W0W0W1",
                   "W1W0W0W0W0W0W0W1", "W2W1W0W0W0W0W1W2", "W2W1W1W0W0W1W1W2",
                   "W3W2W1W1W1W1W2W3", "W3W2W2W1W1W2W2W3"],
        "back":   ["W3W2W1W1W1W1W2W3", "W4W3W2W2W2W2W3W4", "W5W4W3W3W3W3W4W5",
                   "W6W5W4W4W4W4W5W6", "..W6W5W5W5W5W6..", E, E, E],
        "right":  ["W4W3W2W2W1W1W1W2", "W5W4W3W2W2W2W2W3", "W5W4W4W3W3W3W3W4",
                   "W6W5W4W4W4......", E, E, E, E],
        "front":  ["W3W2W1W1W1W1W2W3", "W4W3W2W2W2W2W3W4", "W5W4........W4W5", E, E, E, E, E],
    },
    # 01 SHAGGY. Over the ears, and every edge of it is ragged — no two neighbouring cells of the
    # bottom edge end on the same course, which is this repo's silhouette rule arriving on a head.
    "01": {
        "top":    ["W3W2W1W1W1W1W2W3", "W2W1W0W0W0W0W1W2", "W2W1W0W0W0W0W0W2",
                   "W1W0W0W0W0W0W0W1", "W1W0W0W0W0W0W0W1", "W2W1W0W0W0W1W1W2",
                   "W3W2W1W1W1W1W2W3", "W4W3W2W2W2W2W3W4"],
        "back":   ["W3W2W2W1W1W2W2W3", "W4W3W2W2W2W2W3W4", "W5W4W3W3W3W3W4W5",
                   "W6W5W4W4W4W4W5W6", "W7W6W5W5W5W5W6W7", "..W7W6..W6W7....",
                   "....W7......W7..", E],
        "right":  ["W4W3W2W2W1W1W1W2", "W5W4W3W3W2W2W2W3", "W6W5W4W4W3W3W3W4",
                   "W6W5W5W4W4W4W4W5", "W7W6W6W5W5......", "..W7W6W6........",
                   "....W7..........", E],
        "front":  ["W3W2W2W1W1W2W2W3", "W4W3W3W2W2W3W3W4", "W5W4......W4W5W5",
                   "W6............W6", E, E, E, E],
    },
    # 02 TO THE JAW. It reaches the cube's bottom course, which 23 of the 31 references do and which
    # is as long as a head shell can be. Anything longer needs the torso's second layer, and the
    # measurement says the corpus does not go there either — 2 of 24.
    "02": {
        "top":    ["W3W2W2W1W1W2W2W3", "W2W1W1W0W0W1W1W2", "W2W1W0W0W0W0W1W2",
                   "W1W0W0W0W0W0W0W1", "W1W0W0W0W0W0W0W1", "W2W1W0W0W0W1W1W2",
                   "W2W1W1W1W1W1W2W3", "W3W2W2W1W1W2W2W3"],
        "back":   ["W3W2W1W1W1W1W2W3", "W3W2W2W1W1W2W2W3", "W4W3W2W2W2W2W3W4",
                   "W5W4W3W3W3W3W4W5", "W5W4W4W3W3W4W4W5", "W6W5W4W4W4W4W5W6",
                   "W6W5W5W4W4W5W5W6", "W7W6W5W5W5W5W6W7"],
        "right":  ["W4W3W2W2W1W1W1W2", "W5W4W3W3W2W2W2W2", "W5W4W4W3W3W2W2W3",
                   "W6W5W4W4W4W3W3W3", "W6W5W5W4W4W4W3W4", "W7W6W5W5W5W4W4W4",
                   "W7W6W6W5W5W5W4W5", "..W7W6W6W6W5W5W5"],
        "front":  ["W3W2W2W1W1W2W2W3", "W4W3W3W2W2W3W3W4", "W5W4......W4W4W5",
                   "W5............W5", "W6............W6", "W6............W6",
                   "W7W7........W7W7", "..W7........W7.."],
    },
    # 03 RECEDING. A high hairline and thin over the crown: the top's FRONT courses are bare, which
    # is a silhouette nothing else on the roster has, and it is the one style that is about absence.
    "03": {
        "top":    ["W3W2W1W1W1W1W2W3", "W3W2W1W1W1W1W2W3", "W4W3W2W2W2W2W3W4",
                   "W5W4W3W3W3W3W4W5", "..W5W4W4W4W4W5..", "....W5W5W5W5....",
                   E, E],
        "back":   ["W3W2W2W1W1W2W2W3", "W4W3W2W2W2W2W3W4", "W5W4W3W3W3W3W4W5",
                   "W6W5W4W4W4W4W5W6", "..W6W5W5W5W5W6..", E, E, E],
        "right":  ["W4W3W2W2W2......", "W5W4W3W3W3......", "W6W5W4W4........",
                   "W7W6W5..........", E, E, E, E],
        "front":  ["W4W3........W3W4", E, E, E, E, E, E, E],
    },
    # 04 GATHERED AND PULLED BACK. Tight at the temple, full at the nape: hair off the ears, which
    # is the difference you can see in profile and the only style whose SIDE faces are nearly bare.
    "04": {
        "top":    ["W3W2W2W1W1W2W2W3", "W2W1W1W0W0W1W1W2", "W2W1W1W0W0W1W1W2",
                   "W1W1W0W0W0W0W1W1", "W1W0W0W0W0W0W1W1", "W1W0W0W0W0W0W0W1",
                   "W2W1W1W0W0W1W1W2", "W2W2W1W1W1W1W2W2"],
        "back":   ["W3W2W1W1W1W1W2W3", "W4W3W2W1W1W2W3W4", "W5W4W3W2W2W3W4W5",
                   "W6W5W4W3W3W4W5W6", "..W6W5W4W4W5W6..", "..W7W6W5W5W6W7..",
                   "....W7W6W6W7....", "......W7W7......"],
        "right":  ["W4W3W2W2W1W1W1W2", "W5W4W3W3W2W2W2W3", "W6W5W4W4W3W3....",
                   E, E, E, E, E],
        "front":  ["W3W2W2W1W1W2W2W3", "W4W3........W3W4", E, E, E, E, E, E],
    },
}

# ── the beards ───────────────────────────────────────────────────────
#
# Index 0 is none and is not drawn. Each of these shares the HAIR's tint, which is the one rule that
# survived the geometry unchanged: the base pass is handed a hardcoded -1, so a beard painted into
# the body could never follow a hair colour and a grey-haired man would have had a brown beard.

BEARD = {
    # 01 STUBBLE. Drawn at the LIGHT end of the ramp, because it is tinted with the hair colour and
    # what a few days' growth looks like is the hair colour thinned, not a smaller beard.
    "01": {
        "front":  [E, E, E, E, E, "W1............W1", "W2W2W1....W1W2W2",
                   "W4W3W1W0W0W1W3W4"],
        "right":  [E, E, E, E, E, "..........W1W1..", "......W2W2W1W1..",
                   "..W3W2W2W1W1W0.."],
        "bottom": ["W3W2W2W1W1W2W2W3", "W3W2W1W1W1W1W2W3", "W2W1W1W0W0W1W1W2",
                   "W2W1W1W0W0W1W1W2", E, E, E, E],
    },
    # 02 A SHORT FULL BEARD. Round the mouth and never over it — the mouth cells are gated shut.
    "02": {
        "front":  [E, E, E, E, E, "W2............W2", "W3W2W2....W2W2W3",
                   "W4W3W2W1W1W2W3W4"],
        "right":  [E, E, E, E, "..........W2W1..", "......W3W2W1W1..",
                   "..W4W3W2W2W1W1..", "W5W4W3W3W2W2W1W1"],
        "bottom": ["W4W3W3W2W2W3W3W4", "W3W2W2W1W1W2W2W3", "W2W1W1W0W0W1W1W2",
                   "W2W1W1W0W0W1W1W2", "W3W2W2W1W1W2W2W3", "W4W3W3W2W2W3W3W4",
                   E, E],
    },
    # 03 A LONG BEARD, and its length is CAPPED BY THE CUBE — the shell's bottom edge is the chin and
    # nothing can hang below it. So "long" is expressed as reach up the jaw and a fully covered
    # underside, not as length, and that limit is the honest cost of paint over geometry.
    "03": {
        "front":  [E, E, E, "W2............W2", "W3............W3",
                   "W4............W4", "W5W4W4....W4W4W5", "W6W5W4W3W3W4W5W6"],
        "right":  [E, E, E, "..........W2W2..", "......W3W2W2W1..",
                   "....W4W3W2W2W1..", "..W5W4W3W3W2W2..",
                   "W6W5W4W4W3W3W2W2"],
        "bottom": ["W5W4W4W3W3W4W4W5", "W4W3W3W2W2W3W3W4", "W3W2W2W1W1W2W2W3",
                   "W2W1W1W0W0W1W1W2", "W2W1W1W0W0W1W1W2", "W3W2W2W1W1W2W2W3",
                   "W4W3W3W2W2W3W3W4", "W5W4W4W3W3W4W4W5"],
    },
}

# ── the headwear ─────────────────────────────────────────────────────
#
# Index 0 is bare and is not drawn. These are CLOTH and straw, so each kind takes its own tint
# rather than a per-person roll: linen is bleached or it is not, straw is straw.

HEADWEAR = {
    # 01 A COIF. A close linen cap tied under the ear, and the tie is what tells it from a hood: the
    # side faces run two courses lower than the front does.
    "01": {
        "top":    ["W2W1W1W0W0W1W1W2"] * 2 + ["W1W0W0W0W0W0W0W1"] * 4
                  + ["W2W1W1W0W0W1W1W2", "W3W2W2W1W1W2W2W3"],
        "back":   ["W2W1W1W0W0W1W1W2", "W3W2W1W1W1W1W2W3", "W4W3W2W2W2W2W3W4",
                   "W5W4W3W3W3W3W4W5", "W5W4W4W3W3W4W4W5", "W6W5W4W4W4W4W5W6",
                   "..W6W5W5W5W5W6..", E],
        "right":  ["W3W2W2W1W1W1W1W2", "W4W3W3W2W2W2W2W2", "W5W4W4W3W3W3W3W3",
                   "W5W5W4W4W4W3W3W4", "W6W5W5W4W4W4....", "..W6W5W5W5......",
                   "....W6W6........", E],
        "front":  ["W2W1W1W0W0W1W1W2", "W3W2W2W1W1W2W2W3", "W4W3......W3W3W4",
                   "W5............W5", E, E, E, E],
    },
    # 02 A STRAW CAP WITH A BAND, and NOT a brimmed hat. A shell inflated half a block outside the
    # head cannot project past it, so a brim is the one thing the retired geometry could do that
    # paint cannot. The band at the fourth course is what makes it read as a hat rather than a coif.
    "02": {
        "top":    ["W3W2W1W1W1W1W2W3", "W2W1W0W0W0W0W1W2", "W1W0W0W0W0W0W0W1",
                   "W1W0W0W0W0W0W0W1", "W1W0W0W0W0W0W0W1", "W2W1W0W0W0W0W1W2",
                   "W2W1W1W1W1W1W1W2", "W3W2W2W1W1W2W2W3"],
        "back":   ["W3W2W1W1W1W1W2W3", "W4W3W2W2W2W2W3W4", "W4W3W3W2W2W3W3W4",
                   "W7W6W6W5W5W6W6W7", "..W7W7W6W6W7W7..", E, E, E],
        "right":  ["W4W3W2W2W1W1W1W2", "W5W4W3W3W2W2W2W3", "W5W4W4W3W3W3W3W3",
                   "W7W6W6W5W5W5W5W6", "..W7W7W6W6......", E, E, E],
        "front":  ["W3W2W1W1W1W1W2W3", "W4W3W2W2W2W2W3W4", "W7W6W6W5W5W6W6W7",
                   "W7............W7", E, E, E, E],
    },
    # 03 A HOOD. Wool, and it is weather rather than dress, which is why it is the one covering both
    # sexes may roll. It reaches the chin course and gathers under the jaw.
    "03": {
        "top":    ["W3W2W2W1W1W2W2W3"] + ["W2W1W1W0W0W1W1W2"] * 2
                  + ["W1W0W0W0W0W0W0W1"] * 3 + ["W2W1W1W0W0W1W1W2", "W3W2W2W1W1W2W2W3"],
        "back":   ["W3W2W2W1W1W2W2W3", "W3W2W2W1W1W2W2W3", "W4W3W3W2W2W3W3W4",
                   "W5W4W4W3W3W4W4W5", "W5W5W4W4W4W4W5W5", "W6W5W5W4W4W5W5W6",
                   "W6W6W5W5W5W5W6W6", "W7W6W6W5W5W6W6W7"],
        "right":  ["W3W2W2W1W1W1W1W2", "W4W3W3W2W2W2W2W2", "W5W4W4W3W3W3W2W3",
                   "W5W5W4W4W4W3W3W3", "W6W5W5W4W4W4W4W4", "W6W6W5W5W5W5W4W5",
                   "W7W6W6W6W5W5W5W5", "W7W7W6W6W6W6W5W6"],
        "front":  ["W3W2W2W1W1W2W2W3", "W4W3W3W2W2W3W3W4", "W5W4W4W3W3W4W4W5",
                   "W5............W5", "W6............W6", "W6............W6",
                   "W7W7........W7W7", "W7W7W6W5W5W6W7W7"],
        "bottom": [E, E, "..W6W5W4W4W5W6..", "..W5W4W3W3W4W5..",
                   "..W5W4W3W3W4W5..", "..W6W5W4W4W5W6..", E, E],
    },
    # 04 A VEIL. Bleached linen over the crown, falling to the jaw at the sides and back, and it
    # leaves TWO courses of forehead showing where the hood covers three — the whole difference
    # between a covering worn against weather and one worn as dress.
    "04": {
        "top":    ["W2W1W1W0W0W1W1W2"] * 3 + ["W1W0W0W0W0W0W0W1"] * 3
                  + ["W1W1W0W0W0W0W1W1", "W2W1W1W1W1W1W1W2"],
        "back":   ["W2W1W1W0W0W1W1W2", "W3W2W2W1W1W2W2W3", "W3W3W2W2W2W2W3W3",
                   "W4W3W3W2W2W3W3W4", "W4W4W3W3W3W3W4W4", "W5W4W4W3W3W4W4W5",
                   "W5W5W4W4W4W4W5W5", "W6W5W5W4W4W5W5W6"],
        "right":  ["W3W2W2W1W1W1W1W1", "W4W3W3W2W2W2W2W2", "W4W4W3W3W3W2W2W2",
                   "W5W4W4W3W3W3W3W3", "W5W5W4W4W4W3W3W3", "W6W5W5W4W4W4W4W4",
                   "W6W6W5W5W5W5W4W5", "..W6W6W6W5W5W5W5"],
        "front":  ["W2W1W1W0W0W1W1W2", "W3W2W2W1W1W2W2W3", "W4W3......W3W3W4",
                   "W4............W4", "W5............W5", "W5............W5",
                   "W6W6........W6W6", "..W6........W6.."],
    },
    # 05 A WIMPLE: the veil, plus a band that passes under the chin. The band is the whole BOTTOM
    # face and the last course of the front, which is the only way a 8x8x8 shell can say "under".
    "05": {
        "top":    ["W2W1W1W0W0W1W1W2"] * 3 + ["W1W0W0W0W0W0W0W1"] * 3
                  + ["W1W1W0W0W0W0W1W1", "W2W1W1W1W1W1W1W2"],
        "back":   ["W2W1W1W0W0W1W1W2", "W3W2W2W1W1W2W2W3", "W3W3W2W2W2W2W3W3",
                   "W4W3W3W2W2W3W3W4", "W4W4W3W3W3W3W4W4", "W5W4W4W3W3W4W4W5",
                   "W5W5W4W4W4W4W5W5", "W6W5W5W4W4W5W5W6"],
        "right":  ["W3W2W2W1W1W1W1W1", "W4W3W3W2W2W2W2W2", "W4W4W3W3W3W2W2W2",
                   "W5W4W4W3W3W3W3W3", "W5W5W4W4W4W3W3W3", "W6W5W5W4W4W4W4W4",
                   "W6W6W5W5W5W5W4W4", "W7W6W6W6W5W5W5W4"],
        "front":  ["W2W1W1W0W0W1W1W2", "W3W2W2W1W1W2W2W3", "W4W3......W3W3W4",
                   "W4............W4", "W5............W5", "W5............W5",
                   "W6W6........W6W6", "W6W5W4W3W3W4W5W6"],
        "bottom": ["W6W5W5W4W4W5W5W6", "W5W4W4W3W3W4W4W5", "W4W3W3W2W2W3W3W4",
                   "W3W2W2W1W1W2W2W3", "W3W2W2W1W1W2W2W3", "W4W3W3W2W2W3W3W4",
                   "W5W4W4W3W3W4W4W5", "W6W5W5W4W4W5W5W6"],
    },
}

HEAD_SETS = (("hair", HAIR), ("beard", BEARD), ("headwear", HEADWEAR))


def head_name(kind: str, slug: str) -> str:
    return f"citizen_{kind}_{slug}.png"


def head_tones_floor(cover: int) -> int:
    """How many tones a covering of this size owes, and why it is not one number.

    A stubble is 72 texels and a wimple 359; ten tones is a real floor for the first and no floor at
    all for the second, which is what the flat `MIN_HEAD_TONES` gate could not say. Never more than
    the texel count, because a painting cannot hold more colours than it has pixels.
    """
    return min(cover, max(MIN_HEAD_TONES, int(round(HEAD_TONES_PER_TEXEL * cover))))


def verify_head(kind: str, slug: str, im: Image.Image) -> List[str]:
    """Every claim a head painting makes.

    The first is the one that matters: the layer re-renders the WHOLE model with this texture, so a
    texel outside the `hat` net lands on the torso or the leg. The second is the face window, which
    an earlier generated set got wrong on two of nine files. The third is `check_sides`' rule brought
    inside the writer, because a gate that only runs afterwards is a gate that ships once.
    """
    bad: List[str] = []
    px = im.load()
    fs = net(*BOXES["hat"])
    hat = {(x, y) for _, (x0, y0, w, h) in fs.items()
           for y in range(y0, y0 + h) for x in range(x0, x0 + w)}
    stray = [(x, y) for y in range(64) for x in range(64)
             if px[x, y][3] > 8 and (x, y) not in hat]
    if stray:
        bad.append(f"{len(stray)}px outside the `hat` net, e.g. {stray[0]} — this texture is drawn "
                   f"over the WHOLE model, so that lands on a torso or a leg")

    fx, fy, _, _ = fs["front"]
    shut = [(cx, cy) for cx, cy in sorted(FACE_WINDOW) if px[fx + cx, fy + cy][3] > 8]
    if shut:
        bad.append(f"the face window is painted at {shut} — rows 3..5 cols 1..6 are the eyes and "
                   f"the nose and row 6 cols 3-4 the mouth, and a covering that walls them up is "
                   f"invisible in the net and unmissable on a figure")

    # BOTH SIDES, AND THE SILHOUETTE EXACTLY MIRRORED. Thirteen of thirteen paintings shipped with
    # `left` blank; see `mirror_left` and `check_sides.py`. Measured over the 31 references: 0 paint
    # one side face and leave the other bare. The TONE is deliberately not gated as an exact mirror —
    # 17 of the 25 that paint a side differ across it by more than the invisibility threshold, and
    # `HEAD_TURN` is why ours do too — but the swing is bounded, so a bug there is still catchable.
    rx, ry, rw, rh = fs["right"]
    lx, ly, _, _ = fs["left"]
    r = l = 0
    shape = 0
    worst = 0.0
    for cy in range(rh):
        for cx in range(rw):
            a = px[rx + cx, ry + cy]
            b = px[lx + (rw - 1 - cx), ly + cy]
            r += a[3] > 8
            l += b[3] > 8
            if (a[3] > 8) != (b[3] > 8):
                shape += 1
            elif a[3] > 8:
                worst = max(worst, abs(lum(a[:3]) - lum(b[:3])))
    if not r and not l:
        pass                                     # a covering with no side face at all is fine
    elif shape:
        bad.append(f"`right` paints {r} texels and `left` {l}, and {shape} cells disagree — the "
                   f"silhouette of a covering is the same on both sides of a head. All thirteen "
                   f"paintings shipped with `left` at exactly zero; 0 of 31 references do that, and "
                   f"the mod already shipped an empty left arm and left leg for one whole revision")
    elif worst > HEAD_SWING + 1.0:
        bad.append(f"the two sides differ by up to {worst:.0f} luminance points where the light's "
                   f"whole swing round the box is {HEAD_SWING:.0f} — that is more than illumination")

    cover = sum(1 for x, y in hat if px[x, y][3] > 8)
    if not MIN_HEAD_COVER <= cover <= MAX_HEAD_COVER:
        bad.append(f"covers {cover} of the cube's 384 texels, wanted "
                   f"{MIN_HEAD_COVER}..{MAX_HEAD_COVER} (the cube less the face window)")
    tones = len({px[x, y][:3] for x, y in hat if px[x, y][3] > 8})
    floor = head_tones_floor(cover)
    if tones < floor:
        bad.append(f"{tones} tones over {cover} texels, floor {floor} — the bodies run "
                   f"{HEAD_COLOUR_BAND[0]}..{HEAD_COLOUR_BAND[1]} and the references 139 median, "
                   f"and a silhouette in eight tones has no interior at all")
    darkest = min((px[x, y][0] for x, y in hat if px[x, y][3] > 8), default=255)
    if darkest < MIN_HEAD_TONE:
        bad.append(f"darkest tone {darkest:#02x}, floor {MIN_HEAD_TONE:#02x} — the tint is a "
                   f"multiply and cannot lighten, so this could never be fair hair")

    # NO FULL COURSES. `docs/STYLE.md`'s painted-stripe rule, and the skin skill records it catching
    # three times on cloth: a highlight or a shadow running the whole width of a face reads as plaid.
    # Only courses of four cells or more — two painted cells of one tone is a corner, not a stripe.
    for face, (x0, y0, w, h) in fs.items():
        for cy in range(h):
            on = [px[x0 + cx, y0 + cy][:3] for cx in range(w) if px[x0 + cx, y0 + cy][3] > 8]
            if len(on) >= 4 and len(set(on)) == 1:
                bad.append(f"{face} course {cy} is {len(on)} cells of one tone — a full course of a "
                           f"highlight or a shadow reads as plaid; hold the columns and displace")
    return bad


def head_textures() -> Dict[Tuple[str, str], Image.Image]:
    out = {}
    for kind, table in HEAD_SETS:
        for slug, art in table.items():
            sym = blank_head()
            stamp_head(sym, model_head(kind, mirror_left(art)))
            out[(kind, slug)] = materialise_head(sym)
    return out


def wear_head(body: Image.Image, heads: Dict[Tuple[str, str], Image.Image],
              hair: str | None, colour: int,
              beard: str | None, covering: str | None, cloth: int) -> Image.Image:
    """A body with its head dressed, composited in the layer's own order.

    Hair, then beard in the SAME colour, then the covering in its own — which is exactly the three
    passes `NpcHairLayer.render` makes over one cube, and the order is what lets a hood cover hair
    without being further out.
    """
    out = body.copy()
    for key, tint in ((("hair", hair), colour), (("beard", beard), colour),
                      (("headwear", covering), cloth)):
        if key[1] is None:
            continue
        out.alpha_composite(multiply(heads[key], tint))
    return out


# ── wealth: a quality tier on the garment, not a second wardrobe ─────
#
# WHY THIS AXIS IS THE GARMENT'S AND NOT THE BODY'S. `LivingEntityRenderer.render` hands its base
# pass a hardcoded `-1` for the model colour, so a drawn body cannot be tinted at all; every
# RenderLayer takes an ARGB int. The garment is a layer, so it is the only part of a citizen whose
# colour can change while he is alive — and wealth is the one thing about a person here that does.
#
# WHAT SEPARATED RICH FROM POOR WAS NOT A DIFFERENT GARMENT. It was dye saturation, weave and
# trim: a poor man wore the colour the sheep grew, madder and woad cost money, braid cost more. So
# wealth is FOUR GRADES OF ONE WARDROBE and never 7 trades x 4 tiers of drawn file.
#
# THE MEASUREMENT THAT SET THE COUNT, AND IT DISAGREED WITH THE OBVIOUS PLAN. Four bands of cloth
# tint do not fit. Over the four garments a citizen can wear, three sit at median luminance 61..73
# and carry 10 or 11 distinct tones each, so the volume a multiply can reach is small: four bands
# fit inside it only by collapsing the variety WITHIN a band to 4.6..10 against the 15.5 the
# shipped four already achieve. Three bands keep it. The fourth rung is bought with the BRAID
# instead — new pixels, own layer, own tint — which competes for none of that volume. So each of
# the three steps is carried by a different mechanism:
#
#     faded -> undyed    cleanliness      value, not hue
#     undyed -> dyed     dye saturation
#     dyed -> costly     hue family + braid   cheap dyes are warm, woad and double-dyes cool
#
# A TINT ALONE CANNOT CARRY IT, and here is the number: a multiply can reach any hue and any
# saturation, but it can only ever DARKEN, so "rich" cannot be brighter than the drawn cloth and
# the braid cannot be a bright edge unless it is its own texture. Drawn near-white, a braid texel
# retains 92% of its tint; the garment's own median texel retains 24..51%. That gap IS the braid,
# and no tint on the cloth can produce it.


def wealth_ladder() -> Tuple[List[str], List[List[int]], List[int]]:
    """The rung names, the cloth tints per rung and the braid tints — READ OUT OF `NpcLook.java`.

    Parsed rather than copied, because two copies of a table is the failure this repo has already
    paid for twice (`npc_uv.py`, `solids.py`). If the Java moves a number, the sheet moves with it
    or this raises; what it must never do is quietly show a stratification the game does not draw.
    """
    src = JAVA_NPC_LOOK.read_text(encoding="utf-8")

    m = re.search(r"public static final int ((?:\w+ = \d+, )*\w+ = \d+);", src)
    if not m:
        raise SystemExit("cannot find the wealth rung names in NpcLook.java")
    names = [p.split(" = ")[0].lower() for p in m.group(1).split(", ")]

    def block(field: str) -> str:
        i = src.index(field)
        j = src.index("{", i)
        depth, k = 0, j
        while True:
            if src[k] == "{":
                depth += 1
            elif src[k] == "}":
                depth -= 1
                if depth == 0:
                    return src[j:k + 1]
            k += 1

    rows = [[int(h, 16) for h in re.findall(r"0x([0-9A-Fa-f]{8})", g)]
            for g in re.findall(r"\{([^{}]*)\}", block("TINTS_BY_WEALTH"))]
    braid = [int(h, 16) for h in re.findall(r"0x([0-9A-Fa-f]{8})", block("TRIM_TINTS"))]
    if len(rows) != len(names):
        raise SystemExit(f"NpcLook names {len(names)} rungs and tabulates {len(rows)}")
    if not all(rows) or not braid:
        raise SystemExit("a wealth rung parsed empty out of NpcLook.java")
    return names, rows, braid


def multiply(im: Image.Image, argb: int) -> Image.Image:
    """The garment layer's own arithmetic: an ARGB int multiplied into a drawn texture."""
    r, g, b = (argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF
    out = im.copy()
    px = out.load()
    for y in range(out.size[1]):
        for x in range(out.size[0]):
            p = px[x, y]
            if p[3] > 8:
                px[x, y] = (p[0] * r // 255, p[1] * g // 255, p[2] * b // 255, p[3])
    return out


# The braid's own tones. Near-white, so the tint has the whole range to work in — the same
# arrangement `npc_hair.png` uses, measured at 195..228 grey. Four of them, and they alternate
# along the run because a braid is WOVEN: an unbroken line of one tone reads as an outline someone
# drew round the garment, which is the painted-stripe failure this repo warns about in stone.
BRAID_TONES = {
    "lit":  (0xF0, 0xF0, 0xF0),
    "mid":  (0xC8, 0xC8, 0xC8),
    "low":  (0xA0, 0xA0, 0xA0),   # where the band turns a corner
    "deep": (0x78, 0x78, 0x78),   # the hem's under-edge, so the band has a bottom
}


def braid_cells(mask: set) -> Dict[Tuple[str, str, int, int], str]:
    """Which cells the braid runs through, and which tone each takes.

    COMPUTED OFF THE GARMENT'S OWN MASK, not written down — the same reason `garment_mask` reads
    the mask off disk rather than carrying the numbers. Trim by definition follows the edge of the
    thing it trims, and this repo's own lesson is to measure the walls rather than the box.

    Two runs, and they are the two a garment actually carries:

      * the OPENING — a mask cell with an off-mask neighbour INSIDE its own face. That is the neck
        hole's border and the armhole's, and nothing else: a cell at the edge of its face is where
        the front meets the flank, which is a seam and not an opening.
      * the HEM — the last covered course of each column on the four upright faces of the torso.
    """
    tone: Dict[Tuple[str, str, int, int], str] = {}
    for box in ("body_outer", "r_arm_outer", "l_arm_outer"):
        for face, (_, _, w, h) in net(*BOXES[box]).items():
            on = {(cx, cy) for cy in range(h) for cx in range(w)
                  if (box, face, cx, cy) in mask}
            if not on:
                continue
            for cx, cy in sorted(on):
                free = sum(1 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))
                           if 0 <= cx + dx < w and 0 <= cy + dy < h
                           and (cx + dx, cy + dy) not in on)
                if free:
                    tone[(box, face, cx, cy)] = (
                        "low" if free >= 2 else ("lit" if (cx + cy) % 2 else "mid"))
            if box == "body_outer" and face in ("front", "back", "right", "left"):
                for cx in range(w):
                    col = [cy for cy in range(h) if (cx, cy) in on]
                    if col:
                        tone.setdefault((box, face, cx, max(col)),
                                        "deep" if cx % 2 else "low")
    return tone


def draw_braid(mask: set) -> Image.Image:
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = im.load()
    for (box, face, cx, cy), key in braid_cells(mask).items():
        x, y, _, _ = net(*BOXES[box])[face]
        px[x + cx, y + cy] = BRAID_TONES[key] + (255,)
    return im


def _mask_pixels(name: str) -> List[Tuple[int, int, int]]:
    im = Image.open(OUT / name).convert("RGBA")
    px = im.load()
    out = []
    for box in BOXES:
        for face, (x, y, w, h) in net(*BOXES[box]).items():
            for cy in range(h):
                for cx in range(w):
                    p = px[x + cx, y + cy]
                    if p[3] > 8:
                        out.append(p[:3])
    return out


def _region_pixels(im: Image.Image, boxes: Sequence[str]) -> List[Tuple[int, int, int]]:
    px = im.load()
    return [px[x + cx, y + cy][:3]
            for box in boxes
            for face, (x, y, w, h) in net(*BOXES[box]).items()
            for cy in range(h) for cx in range(w)
            if px[x + cx, y + cy][3] > 8]


def _mul(c: Tuple[int, int, int], argb: int) -> Tuple[int, int, int]:
    return (c[0] * ((argb >> 16) & 0xFF) // 255,
            c[1] * ((argb >> 8) & 0xFF) // 255,
            c[2] * (argb & 0xFF) // 255)


def _mean(colours) -> Tuple[float, float, float]:
    cs = list(colours)
    return tuple(sum(c[i] for c in cs) / len(cs) for i in range(3))


def _dist(a, b) -> float:
    return sum((a[i] - b[i]) ** 2 for i in range(3)) ** 0.5


def tint_distance(pixels: Sequence[Tuple[int, int, int]], a: int, b: int) -> float:
    """How far apart two tints land ON THIS CLOTH — mean RGB distance over its own pixels.

    Not a distance between the two tints. A tint is a multiply, so what it does depends entirely on
    what it multiplies: the same pair of tints separates by 28 on the farmer's bright wool and by
    15 on the forester's dark one, and it is the dark one that decides whether the ladder reads.
    """
    tot = 0.0
    for c in pixels:
        x, y = _mul(c, a), _mul(c, b)
        tot += (sum((x[i] - y[i]) ** 2 for i in range(3))) ** 0.5
    return tot / len(pixels)


def verify_wealth(mask: set, braid: Image.Image,
                  bodies: Dict[str, Image.Image]) -> Tuple[List[str], List[str]]:
    """Every claim the wealth ladder makes, counted on the cloth a citizen can actually wear."""
    bad: List[str] = []
    log: List[str] = []
    names, rows, braid_tints = wealth_ladder()
    cloth = {n: _mask_pixels(n) for n in ROLLABLE_GARMENTS}

    log.append(f"  the wealth ladder, read out of NpcLook.java: {len(rows)} rungs "
               f"({', '.join(f'{names[i]}x{len(rows[i])}' for i in range(len(rows)))}) = "
               f"{sum(len(r) for r in rows)} cloth tints, {len(braid_tints)} braid tints")

    for a in range(len(rows) - 1):
        line = []
        for n in ROLLABLE_GARMENTS:
            mn = min(tint_distance(cloth[n], x, y) for x in rows[a] for y in rows[a + 1])
            line.append(f"{n.split('_')[0][:8]} {mn:5.1f}")
            if mn < MIN_RUNG_APART:
                bad.append(f"rung {names[a]} -> {names[a+1]} is only {mn:.1f} apart on "
                           f"{n} (floor {MIN_RUNG_APART}, which IS the closest pair among the "
                           f"four tints the mod already ships) — that step will not read")
        log.append(f"    {names[a]:6} -> {names[a+1]:6}  " + "   ".join(line))

    flat = [(t, x) for t, row in enumerate(rows) for x in row]
    for n in ROLLABLE_GARMENTS:
        worst = min(((tint_distance(cloth[n], x, y), ta, tb)
                     for (ta, x), (tb, y) in itertools.combinations(flat, 2)))
        if worst[0] < MIN_TINT_APART:
            bad.append(f"two tints on {n} are {worst[0]:.1f} apart — rungs "
                       f"{names[worst[1]]} and {names[worst[2]]} (floor {MIN_TINT_APART}, this "
                       f"repo's 7-luminance-point invisibility threshold in a metric that sees hue)")
    log.append("    closest of ALL %d tints, per garment:  " % len(flat) + "   ".join(
        f"{n.split('_')[0][:8]} "
        f"{min(tint_distance(cloth[n], x, y) for (_, x), (_, y) in itertools.combinations(flat, 2)):5.1f}"
        for n in ROLLABLE_GARMENTS))

    for t, row in enumerate(rows):
        line = []
        for n in ROLLABLE_GARMENTS:
            lums = sorted(lum(_mul(c, x)) for x in row for c in cloth[n])
            sats = sorted(colorsys.rgb_to_hsv(*[v / 255 for v in _mul(c, x)])[1]
                          for x in row for c in cloth[n])
            med = lums[len(lums) // 2]
            line.append(f"{n.split('_')[0][:6]} l{med:5.1f} s{sats[len(sats)//2]:.2f}")
            if med < MIN_TINTED_LUMINANCE:
                bad.append(f"rung {names[t]} lands {n} at median luminance {med:.1f} — floor "
                           f"{MIN_TINTED_LUMINANCE} is the darkest garment the mod ships less the "
                           f"invisibility threshold, and below it a garment is a black smear")
        log.append(f"    {names[t]:6} lands the cloth at  " + "  ".join(line))

    # THE BRAID. Trim, and only trim: inside the garment's mask, narrow, and bright enough that a
    # multiply still has somewhere to go.
    px = braid.load()
    cells = {(box, face, cx, cy)
             for box in BOXES
             for face, (x, y, w, h) in net(*BOXES[box]).items()
             for cy in range(h) for cx in range(w)
             if px[x + cx, y + cy][3] > 8}
    outside = cells - mask
    if outside:
        bad.append(f"{len(outside)} braid cells fall outside the garment mask, e.g. "
                   f"{sorted(outside)[0]} — a braid on bare cloth is paint on the shift")
    share = len(cells) / len(mask)
    log.append(f"    the braid: {len(cells)} of {len(mask)} garment cells = {share:.0%}, "
               f"{len({px[x, y][:3] for y in range(64) for x in range(64) if px[x, y][3] > 8})} tones")
    if not MIN_TRIM_SHARE <= share <= MAX_TRIM_SHARE:
        bad.append(f"the braid covers {share:.0%} of the garment, wanted "
                   f"{MIN_TRIM_SHARE:.0%}..{MAX_TRIM_SHARE:.0%} — trim edges a garment, it is not "
                   f"a second one")
    darkest = min((px[x, y][0] for y in range(64) for x in range(64) if px[x, y][3] > 8),
                  default=255)
    if darkest < MIN_BRAID_TONE:
        bad.append(f"the braid's darkest tone is {darkest:#02x}, floor {MIN_BRAID_TONE:#02x} — a "
                   f"multiply cannot lighten, so a braid drawn dark can never be gold")

    # THE BRAID HAS TO BE SEEN, AND AGAINST TWO THINGS, NOT ONE. In RGB distance and not in
    # luminance, for the same reason the rungs are: the tightest pair among the tints the mod
    # already ships is 4.2 luminance points apart and 0.21 saturation apart, so luminance is the
    # wrong currency for an axis that moves in hue. A copper braid on green cloth measured -0.3
    # luminance and reads perfectly on the sheet.
    top = len(rows) - 1
    braid_px = [px[x, y][:3] for y in range(64) for x in range(64) if px[x, y][3] > 8]
    braid_mean = {i: _mean(_mul(c, braid_tints[i % len(braid_tints)]) for c in braid_px)
                  for i in range(len(rows[top]))}
    line = []
    for n in ROLLABLE_GARMENTS:
        worst = min(_dist(braid_mean[i], _mean(_mul(c, cl) for c in cloth[n]))
                    for i, cl in enumerate(rows[top]))
        line.append(f"{n.split('_')[0][:8]} {worst:5.1f}")
        if worst < MIN_BRAID_APART:
            bad.append(f"the braid is only {worst:.1f} from {n} at the top rung (floor "
                       f"{MIN_BRAID_APART}) — an edge nobody sees is not trim")
    log.append("    the braid against the cloth it edges:  " + "   ".join(line))

    # AND AGAINST THE LINEN IT LIES BESIDE, which is the gate the first braid failed. The neckline
    # braid runs down the inner edge of the two straps, and what is on the other side of that edge
    # is the drawn shift showing through the garment's V. Cream and silver cleared the cloth gate
    # comfortably and came off the contact sheet as a WHITE BIB — measured afterwards at 9.4 and
    # 32.1 from the shift. Pewter measured 44.6 and read as a grey band rather than a braid; gold
    # and copper measure 62 and up and read as braid. So the floor sits between what the sheet
    # rejected and what it accepted, which is how this file's nose and brow floors were set too.
    # The arm is in the comparison as well, because the shoulder cape's edge lies on bare skin.
    line = []
    for slug, tex in sorted(bodies.items()):
        near = _mean(_region_pixels(tex, REGION_BOXES["body"])
                     + _region_pixels(tex, REGION_BOXES["arms"]))
        worst = min(_dist(braid_mean[i], near) for i in braid_mean)
        line.append(f"{slug} {worst:5.1f}")
        if worst < MIN_BRAID_APART:
            bad.append(f"the braid is only {worst:.1f} from body {slug}'s own linen and skin "
                       f"(floor {MIN_BRAID_APART}) — a braid the weight of the shift beside it "
                       f"reads as a white bib, which is what cream at 9.4 and silver at 32.1 did")
    log.append("    the braid against the shift and skin it lies beside:  " + "   ".join(line))
    return bad, log


# ── contact sheet ────────────────────────────────────────────────────

def retired() -> List[Tuple[str, Image.Image]]:
    """A sample of the 48 generated bodies, for the comparison the owner is judging."""
    out = []
    for p in sorted(OUT.glob(RETIRED_GLOB))[::9]:
        out.append((p.stem.replace("citizen_", ""), Image.open(p).convert("RGBA")))
    return out[:5]


def crowd(bodies: Dict[str, Image.Image]) -> List[Tuple[str, Image.Image]]:
    """A village: the drawn bodies against the generated ones, in a row.

    Fourteen drawn against 24 generated, so this IS now a fair comparison on variety as well as
    on register — which it was not when the pool was two.

    Bareheaded on purpose: hair, beard and headwear have their own strip, so this row isolates
    what the body texture alone contributes and is the pessimistic view of a village.
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


def wealth_row(body: Image.Image, garment_name: str,
               braid: Image.Image) -> List[Tuple[str, Image.Image]]:
    """One person up the whole wealth ladder: every rung, every tint inside it.

    Composited in the game's own order — the drawn body, then the garment multiplied by the cloth
    tint, then the braid multiplied by its own — which is exactly the two passes
    `NpcClothesLayer.render` makes. So if the stratification does not read here it will not read in
    game, and if it reads here the only thing left to check is the light.
    """
    names, rows, braid_tints = wealth_ladder()
    garment = Image.open(OUT / garment_name).convert("RGBA")
    out = []
    for t, row in enumerate(rows):
        for i, cloth in enumerate(row):
            worn = multiply(garment, cloth)
            if t == len(rows) - 1:
                worn.alpha_composite(multiply(braid, braid_tints[i % len(braid_tints)]))
            out.append((f"{names[t]}\n#{i}  {cloth & 0xFFFFFF:06x}",
                        elevation(body, "front", worn)))
    return out


def dressed_row(bodies: Dict[str, Image.Image],
                heads: Dict[Tuple[str, str], Image.Image]) -> List[Tuple[str, Image.Image]]:
    """EVERY PAINTED HEAD, on a body, with a trade over it — the view nothing else on this sheet
    gives and the first one that shows a citizen the way a player will see one.

    The hair colours are `CitizenLook.HAIR_BY_COMPLEXION`'s own values and the cloth colours are
    `NpcHairLayer`'s, so this row is the game's arithmetic and not an impression of it.
    """
    hair_colours = [0xFF231F1C, 0xFF3D2D24, 0xFF5C4033, 0xFFA8834E, 0xFF8F8A83]
    cloth = {"01": 0xFFE8E2D2, "02": 0xFFD8BE7E, "03": 0xFF9A9084,
             "04": 0xFFEFEADA, "05": 0xFFE2DDC9}
    garment = Image.open(OUT / GARMENT_FOR_SHEET).convert("RGBA")
    slugs = [p["slug"] for p in PEOPLE]
    out = []
    for i, style in enumerate(sorted(HAIR)):
        body = bodies[slugs[i % len(slugs)]]
        col = hair_colours[i % len(hair_colours)]
        out.append((f"hair {style}\n{col & 0xFFFFFF:06x}",
                    elevation(wear_head(body, heads, style, col, None, None, 0), "front", garment)))
    for i, b in enumerate(sorted(BEARD)):
        body = bodies[slugs[(i + 2) % len(slugs)]]
        col = hair_colours[(i + 4) % len(hair_colours)]
        out.append((f"beard {b}\n+ hair 01",
                    elevation(wear_head(body, heads, "01", col, b, None, 0), "front", garment)))
    for i, hw in enumerate(sorted(HEADWEAR)):
        body = bodies[slugs[(i + 1) % len(slugs)]]
        out.append((f"headwear {hw}\n{cloth[hw] & 0xFFFFFF:06x}",
                    elevation(wear_head(body, heads, "00", hair_colours[1], None, hw, cloth[hw]),
                              "front", garment)))
    return out


def contact_sheet(bodies: Dict[str, Image.Image], braid: Image.Image,
                  head_tex: Dict[Tuple[str, str], Image.Image]) -> Image.Image:
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
         "here AND the variety — fourteen drawn against the 24 generated. Bareheaded on purpose: "
         "the painted hair has its own strip below, so this row isolates what a body texture "
         "alone contributes, which is the pessimistic view of a village.",
         crowd(bodies), 8),
        ("HAIR, BEARD AND HEADWEAR — PAINTED on the `hat` cube the rig already carries, which is "
         "what 31 of 31 reference skins do and what retired 42 baked cubes. Silhouette is "
         "transparency; colour is the layer's ARGB tint, which is why hair colour is a free axis "
         "and complexion is not. NOTE THE ONE LOSS: a shell cannot project past the head, so the "
         "straw hat has a band and no brim.",
         dressed_row(bodies, head_tex), 9),
        ("WEALTH — one body up the whole ladder on farmer_clothes: faded, undyed, dyed, costly. "
         "Four rungs but only THREE bands of cloth tint, because a fourth does not fit in what a "
         "multiply can reach; the top rung is bought with the BRAID, which is its own texture with "
         "its own tint. Read out of NpcLook.java, composited in the game's order. The undyed rung "
         "is the default and IS the range the mod already shipped.",
         wealth_row(bodies[PEOPLE[0]["slug"]], "farmer_clothes.png", braid), 8),
        ("WEALTH again on mason_clothes, which is the mod's darkest rollable garment and the one "
         "that decides whether the ladder reads at all — a multiply can only darken, so the dark "
         "cloth is where the rungs run out of room.",
         wealth_row(bodies[PEOPLE[1 % len(PEOPLE)]["slug"]], "mason_clothes.png", braid), 8),
    ]
    strips = [strip(title, items, scale, W) for title, items, scale in plan]
    width = max(s.size[0] for s in strips)
    im = Image.new("RGBA", (width, sum(s.size[1] for s in strips) + 26), (12, 12, 14, 255))
    d = ImageDraw.Draw(im)
    counts = ", ".join(f"{p['slug']}={distinct(bodies[p['slug']])}" for p in PEOPLE)
    d.text((6, 6), f"BURG — {len(PEOPLE)} drawn citizen bodies, people in their underclothes, "
                   f"with hair/beard/headwear PAINTED on the hat cube and wealth as a quality "
                   f"tier on the garment. Distinct colours: {counts} — the references measure "
                   f"139 median, the 48 generated bodies managed 17.",
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
              f"(gate {MIN_FACE_SEPARATION:.0f} total, {MIN_FACE_SEPARATION_PER_CELL:.0f} per "
              f"differing cell):")
        for a, b, c, w in sorted(pairs, key=lambda p: p[3] / max(p[2], 1))[:4]:
            print(f"      {a} / {b}  {c:3} cells  {w:6.0f} luminance  {w / max(c, 1):5.1f}/cell")
        bad = []
        if worst < MIN_FACE_SEPARATION:
            a, b, c, w = pairs[0]
            bad.append(f"{a} and {b} are {c} cells but only {w:.0f} luminance points apart — they "
                       f"will read alike, which is what a cell count missed")
        for a, b, c, w in pairs:
            if c and w / c < MIN_FACE_SEPARATION_PER_CELL:
                bad.append(f"{a} and {b} differ in {c} cells by {w / c:.1f} luminance points each "
                           f"(floor {MIN_FACE_SEPARATION_PER_CELL:.0f}, this repo's measured "
                           f"invisibility threshold) — a total of {w:.0f} is only large because "
                           f"the whole face differs by an amount nobody can see")
        if bad:
            faults["faces"] = bad

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

    div, worst_pair = symbolic_divergence()
    if worst_pair:
        a, b, frac = worst_pair
        print(f"\n  HAND-DRAWN, MEASURED — symbolic divergence, palette ignored. A repaint of one "
              f"drawing is 0%; floor {MIN_SYMBOLIC_DIVERGENCE:.0%}.")
        for pa, pb, f in div[:4]:
            print(f"      {pa} / {pb}  {f:5.1%}")
        if frac < MIN_SYMBOLIC_DIVERGENCE:
            faults["drawing"] = [
                f"{a} and {b} share {1 - frac:.0%} of their ASCII — {frac:.1%} of cells differ, "
                f"floor {MIN_SYMBOLIC_DIVERGENCE:.0%}. That is one drawing in two palettes, which "
                f"is the failure the whole file exists to end and no colour count can see it"]

    heads = head_textures()
    print("\n  HAIR, BEARDS AND HEADWEAR — paint on the `hat` cube, not geometry. 31 of 31 "
          "references use the head's second layer.")
    print(f"    {'file':26} {'cover':>5} {'left':>5} {'tones':>6} {'floor':>5} {'ceil':>5} "
          f"{'t/px':>6} {'dark':>5}  {'mirror':>6}")
    covers, tones_got, ratios = [], [], []
    fs = net(*BOXES["hat"])
    for (kind, slug), tex in sorted(heads.items()):
        bad = verify_head(kind, slug, tex)
        px = tex.load()
        hat = [(x, y) for _, (x0, y0, w, h) in fs.items()
               for y in range(y0, y0 + h) for x in range(x0, x0 + w)]
        cover = sum(1 for x, y in hat if px[x, y][3] > 8)
        tones = len({px[x, y][:3] for x, y in hat if px[x, y][3] > 8})
        dark = min((px[x, y][0] for x, y in hat if px[x, y][3] > 8), default=255)
        lx, ly, lw, lh = fs["left"]
        rx, ry, _, _ = fs["right"]
        left = sum(1 for cy in range(lh) for cx in range(lw)
                   if px[lx + cx, ly + cy][3] > 8)
        worst = max((abs(lum(px[rx + cx, ry + cy][:3]) - lum(px[lx + (lw - 1 - cx), ly + cy][:3]))
                     for cy in range(lh) for cx in range(lw)
                     if px[rx + cx, ry + cy][3] > 8 and px[lx + (lw - 1 - cx), ly + cy][3] > 8),
                    default=0.0)
        covers.append(cover)
        tones_got.append(tones)
        ratios.append(tones / max(cover, 1))
        print(f"    {head_name(kind, slug):26} {cover:5} {left:5} {tones:6} "
              f"{head_tones_floor(cover):5} {cover:5} {tones / max(cover, 1):6.2f} "
              f"{dark:#5x}  {worst:6.1f}")
        if bad:
            faults[head_name(kind, slug)] = bad
    covers.sort()
    tones_got.sort()
    ratios.sort()
    print(f"    coverage median {covers[len(covers) // 2]} against the references' 73 "
          f"(their range 10..316, ours {covers[0]}..{covers[-1]}); `left` is the mirror of `right` "
          f"and the two differ only by the light, whose whole swing is {HEAD_SWING:.0f}")
    inband = sum(1 for t in tones_got if t >= HEAD_COLOUR_BAND[0])
    print(f"    tones {tones_got[0]}..{tones_got[-1]} against the bodies' "
          f"{HEAD_COLOUR_BAND[0]}..{HEAD_COLOUR_BAND[1]} and the references' 139 median — "
          f"{inband} of {len(tones_got)} reach the band. `ceil` IS the ceiling and it is why the "
          f"rest cannot: a painting holds no more colours than it has texels, and a body paints "
          f"1632 where these paint {covers[0]}..{covers[-1]}. See `HEAD_COLOUR_BAND`.")
    print(f"    tones per texel {ratios[0]:.2f}..{ratios[-1]:.2f}, floor "
          f"{HEAD_TONES_PER_TEXEL}; the eleven references painting 100 hat texels or more manage a "
          f"median of 0.073 and a maximum of 0.735")

    braid = draw_braid(mask) if not args.check else (
        Image.open(OUT / TRIM_NAME).convert("RGBA") if (OUT / TRIM_NAME).exists() else None)
    print("\n  WEALTH — a quality tier on the garment layer:")
    if braid is None:
        faults[TRIM_NAME] = ["not written yet"]
    else:
        bad, log = verify_wealth(mask, braid, bodies)
        for line in log:
            print(line)
        if bad:
            faults[TRIM_NAME] = bad

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
    written = {body_name(slug): tex for slug, tex in bodies.items()}
    written[TRIM_NAME] = braid
    written.update({head_name(k, s): t for (k, s), t in heads.items()})
    for name, tex in written.items():
        if not WRITEABLE.match(name):
            raise SystemExit(f"refusing to write {name}")
        tex.save(OUT / name)
    after = snapshot()
    changed = [n for n, h in before.items() if after.get(n) != h]
    if changed:
        raise SystemExit("DESTROYED EXISTING ART: " + ", ".join(changed))
    print(f"  wrote {len(written)} file(s); {len(before)} pre-existing PNG(s) byte-identical "
          f"after the write — including all 48 of the generated bodies this retires")

    contact_sheet(bodies, braid, heads).save(SHEET / "drawn_bodies.png")
    print(f"\nCONTACT SHEET -> {SHEET / 'drawn_bodies.png'}")
    print("LOOK AT IT. The crowd row is the register comparison and the wealth rows are the "
          "stratification; no count can make either judgement for you.")
    return report(faults)


if __name__ == "__main__":
    sys.exit(main())
