"""RETIRED. Superseded by `draw_citizens.py`; kept so its 48 outputs are not orphaned silently.

WHAT RETIRED IT, AND WHY THIS FILE IS STILL HERE
------------------------------------------------
`draw_citizens.py` draws citizen bodies one at a time by hand instead of emitting a cross product,
and `CitizenLook` now indexes that pool. The 48 files this script wrote are still on disk and still
committed, unreferenced, exactly the way the 12 relayed skins before them were left — nothing is
deleted until somebody who owns the asset list says so.

Two measurements did it.

**The cross product bought 24 bodies, not 48.** 4 complexions x 6 faces x 2 cuts, and the two cuts
are the SEXES: a man is never mistakable for a woman, so the cut buys no variety inside either.
The larger figure that justified this pipeline multiplied in hair, beard and headwear, which are
cubes in `NpcHeadModels` and would be contributed to a hand-drawn body identically.

**And 24 generated bodies lost to the register the owner picked.** Measured against 31 reference
skins he supplied (measured, never copied):

    distinct colours per file   139 median (26..1076)   vs   17 (16..17) here
    draws a nose                28 of 31                vs    0 of 48
    brow vs cheek contrast      60.7 luminance          vs   21.3
    nose bridge vs its flanks   57.3                    vs    0 (a nose was GATED AGAINST)

The nose gate in `verify` below is the sharpest lesson in the pair of files. It refused a nose and
cited vanilla, where row 5 of the head front is flat in 8 of 9 player skins. That was true and it
was the wrong corpus: vanilla's nine are deliberately minimal, and 28 of 31 skins the owner
actually likes draw one — 17 of them as a LIT BRIDGE, which is the form the old objection ("a 2px
nose shadow over a 2px mouth is one dark blob") does not apply to.

What carried over unchanged, because it was measured and it was right: the complexion spans and
the `MIN_SPAN` gate on them, the luminance-weighted face-difference gate (a CELL COUNT passed six
faces that read as one), the garment mask coupling, `hat` left empty because the head shells are
concentric cubes, `texOffs(0,0)` on every model cube, and `npc_uv.py` as the single owner of the
mesh table.

    python make_citizen_skins.py             # write the 48 bodies, 2 materials, contact sheet
    python make_citizen_skins.py --dry-run   # draw and verify, write nothing
    python make_citizen_skins.py --check     # verify what is already on disk

ORIGINAL HEADER FOLLOWS
-----------------------
Every citizen body, both sexes, on one authored path.

WHAT THIS REPLACES, AND WHY IT IS NOT A REPAINT
-----------------------------------------------
The six male skins were produced by `remap_npc_uv.py`, which mechanically relayed the vanilla
VILLAGER texture onto the player UV. Measured on the shipped file, the head cube it produced is:

    face     dark px   content
    top          0     all flesh
    back         0     all flesh
    right/left   0     all flesh
    front       12     a 6-wide brow bar on row 4, green irises on row 5, a 4-wide mouth on row 7
    bottom      16     the neck shadow

**They are bald.** Not badly-drawn hair — zero hair texels anywhere on the head cube. And the
twelve shipped skins carry **three distinct alpha masks between them**: the six men share ONE,
the women have two (veiled and bare). An alpha mask is the only silhouette a texture on this rig
can carry — everything else is the model — so six men in six palettes were always going to read
as one man repainted, however the faces were fixed. The head front is 3.9% of the 1632 base
texels.

That measurement is the reason this file does not own hair. **Hair, beard and headwear are
geometry**, in `NpcHeadModels` on the Java side, rendered by `NpcHeadLayer` with a per-person
tint. This file draws the body, and it draws the head cube deliberately BALD — flesh on every
face but the front — because a cube of hair is now sitting over it.

WHY COMPLEXION IS A DRAWN FILE AND NOT A TINT
---------------------------------------------
`LivingEntityRenderer.render` ends its base pass with

    this.model.renderToBuffer(pose, vc, packedLight, i, flag1 ? 654311423 : -1);

read out of the 1.21.1 sources. The model colour is the literal `-1` — opaque white — and there
is no per-entity hook to change it. **The base texture cannot be tinted.** Every `RenderLayer`
by contrast takes an ARGB int (`renderColoredCutoutModel(..., int)`), which is how the garment
gets its four `NpcLook.TINTS` and how the hair gets its colour.

So a complexion has to be a separate DRAWING, and it has to be drawn rather than multiplied
down. Measured on the old pipeline, which multiplied one light drawing by a gain per variant:

    variant      flesh span   hair span      (lit -> deep, in luminance)
    as drawn         29           24
    dark             20           13

Multiplying a drawing down multiplies its contrast down, and 13 points is the same crushed hair
that `make_female_skins.HAIR_MODEL_FLOOR` exists to refuse. The four palettes below are each
drawn at their own full span and gated on it (`MIN_SPAN`).

If a tint knob on complexion is ever wanted on top of the four drawings, the mechanism is a
client-side composite rather than a renderer hook: `DynamicTexture(NativeImage)` plus
`TextureManager.register/release` both exist in 1.21.1, so the body could be composited per
person and registered under its own `ResourceLocation`. That costs a texture cache, a reload
listener and one base-pass draw batch per distinct person on screen. It is not needed for four
complexions and it is written down here so nobody has to rediscover the `-1` above.

WHY THE CROSS PRODUCT IS A BUILD-TIME ONE
-----------------------------------------
The authored art is 4 complexion palettes + 6 face blocks = **ten drawn things**. The 48 files
are their product with the two cuts, emitted here. The multiplicativity that matters is in the
AUTHORING; on disk it is generated output. This stops scaling at a third texture axis, which is
exactly why hair colour is a tint and hair style is geometry.

THE CUT IS THE SEX, AND IT IS THE SAME DOCTRINE AS THE WOMEN'S
--------------------------------------------------------------
`make_female_skins.py` established it and this file keeps it, importing her drawn blocks rather
than copying them. Reading down the middle of a working person: linen at the neck, the trade's
tunic over the torso, their OWN cloth where the garment does not reach. Measured off the garment
mask — all nine profession files are recolours of one drawing — the overtunic covers the torso
front rows 6..11 completely and rows 0..5 only at cols 0-1 and 6-7, with a 9-texel wedge on each
shoulder and nothing on the arm. So what shows of the base is a chest panel, the shoulders, the
sleeve, and everything below the hem.

    man    tunic to the KNEE (leg outer rows 0..3), sleeve to the elbow, bare forearm
    woman  gown to the ANKLE (leg outer rows 0..9), sleeve to the wrist

ONE BASE CLOTH PER SEX, DELIBERATELY
------------------------------------
The six gown colours of the previous female set cannot survive as a file axis: base cloth cannot
be tinted (the `-1` again), so a colour axis on it multiplies the file count. It shows only at
the sleeve, the hem, and on a citizen with no profession. The colour a player actually reads is
the garment's — 7 professions x 4 tints — so the base is one undyed cloth per sex, taken from
the researched range in `NpcLook.TINTS` via `make_female_skins.GOWNS`. If unemployed citizens
should vary, a 2-value cloth axis is one line here and 96 files instead of 48.
"""

from __future__ import annotations

import argparse
import hashlib
import random
import re
import sys
from pathlib import Path
from typing import Dict, List, Tuple

from PIL import Image, ImageDraw

# The mesh table, from its single owner.
from npc_uv import (MIRROR_SWAP, NEW_REGIONS, PLAYER_BOXES as BOXES, faces as net,
                    player_sampled as sampled_pixels)

# Her drawn work, shared rather than copied. The bodice's four-column chemise panel, the
# wrist-length sleeve, the ankle skirt and the shadowed under-leg were all engineered against the
# garment mask above and the reasoning holds unchanged; the linen and the cloth range are
# researched. Only the head, the veil and the hairstyles are retired, because the head cube is
# bald now and the veil has been promoted to geometry.
from make_female_skins import (BODICE, CHEMISE, GOWNS, SKIRT, SLEEVE, UNDER,
                               checker, elevation, flip, head_only, lum, strip)

HERE = Path(__file__).resolve().parent
OUT = HERE.parent / (
    "common/src/main/resources/assets/onceuponatown/textures/entity/npc")
SHEET = HERE / "structures/out/npc"

# The only filenames this script may write. Everything else in that directory is either the
# author's hand-drawn work or another tool's output, and one bad glob destroys the only copy.
WRITEABLE = re.compile(r"^(citizen_[mw]_c\d_f\d|npc_hair|npc_headwear)\.png$")

COMPLEXION_COUNT = 4
FACE_COUNT = 6
GARMENT_FOR_SHEET = "farmer_clothes.png"

# The six regions a body must fill or the person has a hole in them, with the net area of each:
# 6 faces of a w x h x d box come to 2wd + 2dh + 2wh.
BASE_PARTS = {"head": 384, "body": 352, "r_arm": 224, "l_arm": 224,
              "r_leg": 224, "l_leg": 224}

# The garment's. A skin pixel here z-fights the profession overlay drawn over it.
GARMENT_PARTS = ("body_outer", "r_arm_outer", "l_arm_outer")

# The cut's. Vanilla puts clothing on the legs' outer layer in 4 of its 9 player skins.
CUT_PARTS = ("r_leg_outer", "l_leg_outer")

# Empty in 9 of vanilla's 9, and empty here too: headwear is a model now, so nothing paints it.
# It stays declared in `NpcModel` — deleting a mesh cube is not this change's business.
EMPTY_PARTS = ("hat",) + GARMENT_PARTS

MIN_SPAN, MAX_SPAN = 26.0, 40.0     # the anti-crush gate on a complexion
MIN_FEATURE_CONTRAST = 30.0         # a mouth or an iris against its own cheek

# How far apart two faces have to be, summed over the head front as |luminance difference| per
# differing texel. NOT a count of differing cells — that was the first version of this gate and
# it passed six faces that came off the contact sheet looking like one. See `FACES`.
MIN_FACE_SEPARATION = 80.0


# ── palette ──────────────────────────────────────────────────────────
#
# Four complexions, each DRAWN. `warm` is the range measured off `default_skin.png`
# (#be886c x562, #b78272 x262, #b37b62 x198, #a36b4d) and is kept exactly, so the mod's own
# colouring survives as one of the four. The other three are authored to the same span rather
# than derived from it by a gain — see the header for the 29-vs-20 measurement that forced this.
#
#   L lit   F mid   S shadow   D deep (brows, under the jaw)   M mouth   o iris
COMPLEXIONS = [
    ("light", {"L": (0xe2, 0xb5, 0x96), "F": (0xd7, 0xa8, 0x87), "S": (0xc9, 0x97, 0x7a),
               "D": (0xc2, 0x93, 0x77), "M": (0x8f, 0x53, 0x46), "o": (0x4a, 0x32, 0x26)}),
    ("warm",  {"L": (0xbe, 0x88, 0x6c), "F": (0xb7, 0x82, 0x72), "S": (0xb3, 0x7b, 0x62),
               "D": (0xa3, 0x6b, 0x4d), "M": (0x77, 0x42, 0x2f), "o": (0x3d, 0x2d, 0x29)}),
    ("olive", {"L": (0xa8, 0x82, 0x5e), "F": (0x9e, 0x78, 0x57), "S": (0x92, 0x69, 0x4b),
               "D": (0x86, 0x60, 0x3f), "M": (0x5e, 0x3b, 0x2c), "o": (0x33, 0x25, 0x1d)}),
    ("dark",  {"L": (0x7a, 0x52, 0x40), "F": (0x6f, 0x4a, 0x39), "S": (0x61, 0x3f, 0x30),
               "D": (0x4e, 0x30, 0x25), "M": (0x3a, 0x21, 0x19), "o": (0x24, 0x18, 0x12)}),
]

# Fixed across every complexion. White sclera is what all nine vanilla player skins use.
FIXED = {
    "O": (0xff, 0xff, 0xff),   # sclera
    "Y": (0x4c, 0x3a, 0x30),   # shoe leather, lit
    "X": (0x3d, 0x2d, 0x29),   # shoe leather
    "x": (0x23, 0x18, 0x14),   # sole
}

# One undyed cloth per sex, out of the researched range. Grey-brown moorit for a working man,
# undyed cream for a woman — a cut apart at a glance without a dye either of them could not
# afford. See the header for why this is not a per-person axis.
CLOTH = {"m": GOWNS[2], "w": GOWNS[0]}


# ── the drawing ──────────────────────────────────────────────────────
#
#   .  transparent
#   L F S D  flesh lit/mid/shadow/deep      M  mouth    O  sclera    o  iris
#   1 2 3  linen shadow/mid/lit
#   -  cloth shadow    #  cloth mid    +  cloth lit    =  cloth trim
#   Y X  shoe leather lit/mid            x  sole
#
# Shading runs DOWN the cloth and is jittered, one course per section moving the highlight over:
# an unjittered ramp reads as a painted stripe, and scattered specks read as a flat field with
# dirt on it. Her bodice established this and the male tunic follows it.

# The head cube, everywhere but the front. FLESH — a scalp, because the hair is a cube now. The
# old set was bald by accident; this is bald on purpose, and `verify` gates it.
HEAD_SIDE = [
    "FFFFFFFL",     # col 7 is the frontmost edge of a right face, so it takes the light
    "FFFFFFFL",
    "FFFFFFFL",
    "SFFFFFFL",
    "SFFFFFFL",
    "SFFFFFFF",
    "SSFFFFFF",
    "DSSFFFFS",
]
HEAD_SHELL = {
    "top":    ["FFFFFFFF"] + ["FLLLLLLF"] * 6 + ["FFFFFFFF"],
    "back":   ["FFFFFFFF"] * 3 + ["SFFFFFFS"] * 3 + ["SSFFFFSS", "DSSFFSSD"],
    "right":  HEAD_SIDE,
    "left":   flip(HEAD_SIDE),
    "bottom": ["DDDDDDDD"] * 8,
}

# THE SIX FACES. Calibrated against vanilla's nine human faces on this exact 64x64 layout
# (`assets/minecraft/textures/entity/player/wide/*.png`), re-measured for this file rather than
# taken on trust:
#
#   * eyes on ROW 4, sclera at cols 1 and 6, iris at cols 2 and 5 — 9 of 9, no exceptions
#   * row 5 cols 2..5 is FLAT FLESH: luminance spread 0 in 8 of 9. Nobody draws a nose. A 2px
#     nose shadow over a 2px mouth merges into one dark blob
#   * mouth is 2px at cols 3 and 4, on row 6 in 6 of 9 and row 7 in 3 of 9
#   * a brow, where there is one at all, is a DARKER FLESH and never a hair colour. The relayed
#     male set inherited the villager's near-black `#332411` monobrow bar and it is most of why
#     those faces read as a villager. `verify` now refuses any non-flesh tone on the face
#   * a vanilla face spends a MEDIAN OF 16 ink texels of the 64 on the head front (range 4..46),
#     which is the whole budget a face has to be distinguishable within
#
# Six is what that budget supports; ten was the first proposal and 16 texels will not carry it.
#
# **A BROW IS DRAWN IN `D`, NEVER IN `S`, AND THAT WAS A MEASUREMENT NOT A PREFERENCE.** The
# first version of these six used `S` for the soft brows, passed a gate that counted DIFFERING
# CELLS, and came off the contact sheet as one face six times. The gate was measuring the wrong
# quantity. On the reference palette:
#
#     tone   luminance   distance from the cheek `F`
#     L        149.0        4.9
#     F        144.0        0.0
#     S        136.9        7.1      <- eight cells of this is invisible
#     D        120.3       23.7
#
# `plain`/`browed` scored 8 cells apart and **82 luminance-points** apart; `heavy`/`long` scored
# 18 cells and 496. So the gate is now the luminance-weighted sum (`MIN_FACE_SEPARATION`) and the
# six differ by SHAPE — where the shadow columns sit, how wide the jaw is, which row the mouth is
# on — rather than by the strength of one band.
FACES = [
    ("plain", [        # no brow at all — 8 of 9 vanilla have none
        "FLLLLLLF", "FLLLLLLF", "FLLLLLLF", "FLLLLLLF",
        "FOoFFoOF", "SFFFFFFS", "FFFMMFFF", "DSFFFFSD"]),
    ("browed", [       # brows in DEEP flesh, cols 1-2 and 5-6, with a gap: two brows, not a bar
        "FLLLLLLF", "FLLLLLLF", "FLLLLLLF", "FDDFFDDF",
        "FOoFFoOF", "SFFFFFFS", "FFFMMFFF", "DSFFFFSD"]),
    ("broad", [        # no brow, cheeks pulled in to cols 0-1/6-7, a heavy square jaw
        "FLLLLLLF", "FLLLLLLF", "FLLLLLLF", "FLLLLLLF",
        "FOoFFoOF", "SSFFFFSS", "SSFMMFSS", "DDDFFDDD"]),
    ("long", [         # narrow — shadow columns run the whole face — and the mouth on row 7
        "FLLLLLLF", "FLLLLLLF", "SLLLLLLS", "SLLLLLLS",
        "SOoFFoOS", "SFFFFFFS", "SFFFFFFS", "DFFMMFFD"]),
    ("lined", [        # brows, crow's feet in the outer COLUMNS of row 5, corners of the jaw
        "FLLLLLLF", "FSLLLLSF", "FSLLLLSF", "FDDFFDDF",
        "FOoFFoOF", "DSFFFFSD", "SFFMMFFS", "DDSFFSDD"]),
    ("young", [        # no shading anywhere: a full, lit, unlined face
        "LLLLLLLL", "LLLLLLLL", "LLLLLLLL", "LLLLLLLL",
        "LOoLLoOL", "LLLLLLLL", "LLLMMLLL", "FLLLLLLF"]),
]

# ── the man's cut ────────────────────────────────────────────────────
#
# A knee-length tunic over a linen shirt, belted. The linen shows at the neck facing (cols 2 and
# 5, which is inside the garment's V) with the throat bare above it; his own cloth carries the
# rest and the belt sits at row 9. Under a profession garment rows 6..11 vanish, so what a player
# reads is the collar, the chest, the sleeve and the hem.
# **The jitter is ONE course per panel, not every course.** The first draft moved the highlight
# on alternate rows, which on cloth whose mid and lit tones are 21 luminance points apart came off
# the contact sheet as a chequerboard — a plaid tunic, not a draped one. A fold is a COLUMN that
# holds; a single displaced course is the whole variation a panel needs.
TUNIC_M_SIDE = ["-##+", "-##+", "-##+", "-##+", "-##+", "-#+#",
                "-##+", "-##+", "-##+", "====", "-##+", "-##+"]
TUNIC_M = {
    "front": [
        "-#2FF2#-",   # the neck facing, throat bare at 3-4
        "-#2332#-",
        "-#+##+#-",
        "-#+##+#-",
        "-#+##+#-",
        "-##+#+#-",   # the one displaced course
        "-#+##+#-",
        "-#+##+#-",
        "-#+##+#-",
        "========",   # the belt
        "-#+##+#-",
        "-#+##+#-",
    ],
    "back": [
        "-#2332#-", "-#2332#-", "-#+##+#-", "-##+#+#-",
        "-#+##+#-", "-#+##+#-", "-#+##+#-", "-#+##+#-",
        "-#+##+#-", "========", "-#+##+#-", "-#+##+#-",
    ],
    "right":  TUNIC_M_SIDE,
    "left":   flip(TUNIC_M_SIDE),
    "top":    ["-#+##+#-", "-#+##+#-", "-#1331#-", "-#2332#-"],
    "bottom": ["--------"] * 4,
}

# Cloth to the elbow, then the bare forearm and a hand. Col 3 of the front is the side against
# the torso, hence the shadow column there.
SLEEVE_M = {
    "front":  ["#+#-", "#+#-", "#+#-", "##+-", "#+#-", "====",
               "LFFS", "LFFS", "LFFS", "LFFS", "FFFS", "SSSS"],
    "back":   ["#+#-", "#+#-", "##+-", "#+#-", "#+#-", "====",
               "LFFS", "LFFS", "LFFS", "LFFS", "FFFS", "SSSS"],
    "right":  ["-##+", "-##+", "-#+#", "-##+", "-##+", "====",
               "SFFL", "SFFL", "SFFL", "SFFL", "SFFF", "SSSS"],
    "left":   ["-##-", "-##-", "-##-", "-##-", "-##-", "====",
               "SFFS", "SFFS", "SFFS", "SFFS", "SFFS", "SSSS"],
    "top":    ["-##-", "#++#", "#++#", "-##-"],
    "bottom": ["FFFF", "FSSF", "FSSF", "FFFF"],           # the palm
}

# Under the hem: shadow for the four courses the tunic covers, then wool hose, then a shoe. Only
# the bottom eight courses are ever seen.
# Hose columns HOLD, for the same reason the tunic's do. Alternating `1221`/`2332` down the leg
# put a 47-point luminance step on every other course and read as a barcode on the sheet — the
# linen shadow and mid are far further apart than the wool's two tones are.
HOSE_M = {
    "front":  ["-##-"] * 4 + ["1221", "1221", "1231", "1221", "1221", "1221"]
              + ["YYYY", "XXXX"],
    "back":   ["-##-"] * 4 + ["1221", "1231", "1221", "1221", "1221", "1221"]
              + ["YYYY", "XXXX"],
    "right":  ["----"] * 4 + ["1122", "1122", "1132", "1122", "1122", "1122"]
              + ["YXXX", "XXXX"],
    "left":   ["----"] * 4 + ["2211", "2311", "2211", "2211", "2211", "2211"]
              + ["XXXY", "XXXX"],
    "top":    ["----"] * 4,
    "bottom": ["xxxx"] * 4,
}

# The tunic's hem, on the leg's outer layer: four courses and a trim band, then nothing, so the
# hose and the shoe show below the knee. `-#+#` on the right leg mirrors to `#+#-` on the left,
# so the pair reads as one draped volume rather than two tubes with a seam between them.
HEM_M = {
    "front":  ["-#+#", "-##+", "-#+#", "===="] + ["...."] * 8,
    "back":   ["-#+#", "-##+", "-#+#", "===="] + ["...."] * 8,
    "right":  ["-##+", "-##+", "-#+#", "===="] + ["...."] * 8,
    "left":   ["-##-", "-##-", "-##-", "===="] + ["...."] * 8,
    "top":    ["####"] * 4,
    "bottom": ["...."] * 4,                                # open, so the hose shows
}

# Which blocks make each sex. The woman's four are hers, imported.
CUT = {
    "m": dict(body=TUNIC_M, arm=SLEEVE_M, leg=HOSE_M, leg_outer=HEM_M, hem_rows=4),
    "w": dict(body=BODICE, arm=SLEEVE, leg=UNDER, leg_outer=SKIRT, hem_rows=10),
}


def blank() -> List[List[str | None]]:
    return [[None] * 64 for _ in range(64)]


def stamp(sym, box: str, art: Dict[str, List[str]], mirror: bool = False) -> None:
    """Write one box's six ASCII faces into the symbolic canvas."""
    f = net(*BOXES[box])
    for face, (x0, y0, w, h) in f.items():
        rows = art[MIRROR_SWAP[face] if mirror else face]
        if len(rows) != h or any(len(r) != w for r in rows):
            raise SystemExit(
                f"{box}.{face}: art is {len(rows)}x{len(rows[0]) if rows else 0}, "
                f"the net wants {h}x{w}")
        for cy, row in enumerate(rows):
            for cx in range(w):
                ch = row[w - 1 - cx] if mirror else row[cx]
                if ch != ".":
                    sym[y0 + cy][x0 + cx] = ch


def draw(sex: str, face: int) -> List[List[str | None]]:
    """One body, symbolically. The palette is applied afterwards."""
    cut = CUT[sex]
    sym = blank()
    stamp(sym, "head", dict(HEAD_SHELL, front=FACES[face][1]))
    # `hat` deliberately untouched: headwear is a model.
    stamp(sym, "body", cut["body"])
    stamp(sym, "r_arm", cut["arm"])
    stamp(sym, "l_arm", cut["arm"], mirror=True)
    stamp(sym, "r_leg", cut["leg"])
    stamp(sym, "l_leg", cut["leg"], mirror=True)
    stamp(sym, "r_leg_outer", cut["leg_outer"])
    stamp(sym, "l_leg_outer", cut["leg_outer"], mirror=True)
    return sym


def table_for(sex: str, complexion: int) -> Dict[str, Tuple[int, int, int]]:
    _, flesh = COMPLEXIONS[complexion]
    _, sh, mid, hi, trim = CLOTH[sex]
    t: Dict[str, Tuple[int, int, int]] = {}
    t.update(flesh)
    t.update(FIXED)
    t.update(CHEMISE)
    t.update({"-": sh, "#": mid, "+": hi, "=": trim})
    return t


def materialise(sym, sex: str, complexion: int) -> Image.Image:
    table = table_for(sex, complexion)
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = im.load()
    for y in range(64):
        for x in range(64):
            ch = sym[y][x]
            if ch is None:
                continue
            if ch not in table:
                raise SystemExit(f"legend has no colour for '{ch}' at ({x},{y})")
            px[x, y] = table[ch] + (255,)
    return im


# ── the two model materials ──────────────────────────────────────────
#
# Hair, beard and headwear are cubes in `NpcHeadModels`, and EVERY one of those cubes uses
# texOffs(0, 0). That is deliberate and it is what keeps this file and the Java geometry from
# becoming two copies of one table: if the texture is a uniform material, a cube can sample any
# region of it and needs no reserved rectangle here. Add a per-cube region and the box table has
# to exist in both languages, which is the failure that put a retired villager table inside
# `make_npc_textures --check` and 126 phantom faults on every file in the mod.
#
# Two consequences, both load-bearing:
#   * the material must be FULLY OPAQUE — a transparent texel would punch a hole in an arbitrary
#     cube face, and which one is not predictable
#   * it must be NEAR-WHITE, because the colour arrives as an ARGB multiply at render time. The
#     same reasoning as `NpcLook.TINTS`: "these multiply a drawn texture, so a strong shift stops
#     reading as cloth and starts reading as a team colour"
MATERIAL_W, MATERIAL_H = 64, 32


def hair_material() -> Image.Image:
    """Strands: vertical, because hair hangs. Jittered so it is not a comb of stripes."""
    im = Image.new("RGBA", (MATERIAL_W, MATERIAL_H), (0, 0, 0, 255))
    px = im.load()
    rng = random.Random(0x48414952)
    cols = [rng.choice((0xff, 0xf4, 0xe8, 0xf9, 0xee)) for _ in range(MATERIAL_W)]
    for x in range(MATERIAL_W):
        for y in range(MATERIAL_H):
            v = cols[x]
            # a slow darkening downward — the underside of a head of hair is in shadow
            v = int(v * (1.0 - 0.22 * y / MATERIAL_H))
            if rng.random() < 0.12:                 # a broken strand, so it is not ruled
                v = int(v * 0.9)
            px[x, y] = (v, v, v, 255)
    return im


def cloth_material() -> Image.Image:
    """A weave: a two-by-two twill, so cloth does not read as flat plastic."""
    im = Image.new("RGBA", (MATERIAL_W, MATERIAL_H), (0, 0, 0, 255))
    px = im.load()
    rng = random.Random(0x434c4f54)
    for y in range(MATERIAL_H):
        for x in range(MATERIAL_W):
            v = 0xff if ((x // 2) + (y // 2)) % 2 else 0xf0
            v = int(v * (1.0 - 0.18 * y / MATERIAL_H))
            if rng.random() < 0.08:
                v = int(v * 0.94)
            px[x, y] = (v, v, v, 255)
    return im


# ── verification ─────────────────────────────────────────────────────

def region_counts(im: Image.Image) -> Dict[str, int]:
    px = im.load()
    return {name: sum(1 for y in range(y0, y1) for x in range(x0, x1) if px[x, y][3] > 8)
            for name, (x0, y0, x1, y1) in NEW_REGIONS.items()}


def span(complexion: int) -> float:
    _, f = COMPLEXIONS[complexion]
    return abs(lum(f["L"]) - lum(f["D"]))


def face_distances(complexion: int = 1) -> Tuple[float, List[Tuple[str, str, int, float]]]:
    """How far apart the six faces are — in LUMINANCE, not in cells.

    The measurement that decides whether six faces is honest, and the second version of it. The
    first counted differing cells, scored `plain` and `browed` 8 apart, and passed a set that the
    contact sheet showed as one face six times: the brows were drawn in `S`, which is 7.1
    luminance points from the cheek. Cells are free; contrast is what the eye is given.

    Measured on the reference palette, since the shape is shared and only the palette moves.
    """
    ref = dict(COMPLEXIONS[complexion][1])
    ref.update(FIXED)
    pairs = []
    for i in range(FACE_COUNT):
        for j in range(i + 1, FACE_COUNT):
            cells = weighted = 0
            for cy in range(8):
                for cx in range(8):
                    a, b = FACES[i][1][cy][cx], FACES[j][1][cy][cx]
                    if a != b:
                        cells += 1
                        weighted += abs(lum(ref[a]) - lum(ref[b]))
            pairs.append((FACES[i][0], FACES[j][0], cells, weighted))
    return min(p[3] for p in pairs), sorted(pairs, key=lambda p: p[3])


def verify(name: str, im: Image.Image, sex: str, complexion: int) -> List[str]:
    """Every claim this file makes about its own output, counted.

    Each of these has actually gone wrong once, here or in the file this one replaces:

      1. a base region empty or unfilled  — a hole in a person is not a style choice
      2. paint on `hat` or a garment's outer layer — z-fights the layer drawn over it
      3. no cut on the leg outer — the sexes stop differing
      4. the eyes off row 4, or sclera/iris off cols 1,2,5,6 — 9 of 9 vanilla disagree
      5. a nose — 8 of 9 vanilla have a flat row 5, and 2px over 2px is one dark blob
      6. a mouth that is not 2px at cols 3-4 on row 6 or 7
      7. A NON-FLESH TONE ANYWHERE ON THE FACE — the villager monobrow, by name
      8. hair on the head cube — it is a cube of its own now, and paint under it is a seam
      9. a feature with no contrast against its own cheek
     10. the two sides not mirrored — the old set was empty on the left for a whole revision
     11. a pixel outside every sampled region — paint nobody will ever see
    """
    bad: List[str] = []
    counts = region_counts(im)
    px = im.load()
    flesh = COMPLEXIONS[complexion][1]

    for part, area in BASE_PARTS.items():
        got = counts[part]
        if got == 0:
            bad.append(f"{part} EMPTY — the mesh samples it")
        elif got != area:
            bad.append(f"{part} {got}/{area} — {area - got} unpainted texels")

    for part in EMPTY_PARTS:
        if counts[part]:
            why = ("headwear is a model" if part == "hat" else "z-fights the garment layer")
            bad.append(f"{part} has {counts[part]}px — {why}")

    for part in CUT_PARTS:
        if counts[part] == 0:
            bad.append(f"{part} empty — no cut, so the sexes do not differ")

    # The cut, in courses of the leg's front face. A hem that creeps is a gown on a man.
    lx, ly, lw, lh = net(*BOXES["r_leg_outer"])["front"]
    painted = [cy for cy in range(lh)
               if any(px[lx + cx, ly + cy][3] > 8 for cx in range(lw))]
    want = CUT[sex]["hem_rows"]
    if painted != list(range(want)):
        bad.append(f"hem covers courses {painted}, the {sex} cut wants 0..{want - 1}")

    # The face, against the corpus. Read off the SHIPPED pixels, not off the art, so the
    # palette cannot quietly break a rule the ASCII kept.
    hx, hy, _, _ = net(*BOXES["head"])["front"]

    def at(cx, cy):
        return px[hx + cx, hy + cy][:3]

    if at(1, 4) != FIXED["O"] or at(6, 4) != FIXED["O"]:
        bad.append("sclera not at cols 1 and 6 of row 4 — 9 of 9 vanilla put it there")
    if at(2, 4) != flesh["o"] or at(5, 4) != flesh["o"]:
        bad.append("iris not at cols 2 and 5 of row 4 — 9 of 9 vanilla put it there")
    if len({at(cx, 5) for cx in range(2, 6)}) != 1:
        bad.append("row 5 cols 2..5 is not flat — that is a nose, and 8 of 9 have none")

    mouth = [(cx, cy) for cy in range(8) for cx in range(8) if at(cx, cy) == flesh["M"]]
    if sorted(mouth) not in ([(3, 6), (4, 6)], [(3, 7), (4, 7)]):
        bad.append(f"mouth is {sorted(mouth)}, wants 2px at cols 3-4 on row 6 or 7")

    # THE MONOBROW GATE. Every texel of the face that is not an eye or the mouth has to be one
    # of the four flesh tones. The relayed set put `#332411` — a hair colour — across row 4.
    allowed = {flesh[k] for k in "LFSD"} | {FIXED["O"], flesh["o"], flesh["M"]}
    alien = {at(cx, cy) for cy in range(8) for cx in range(8)} - allowed
    if alien:
        bad.append("non-flesh tone on the face: "
                   + ", ".join("#%02x%02x%02x" % c for c in sorted(alien))
                   + " — a brow is a darker flesh, never a hair colour")

    # The head cube is bald ON PURPOSE now. Anything outside the flesh family on the other five
    # faces would be hair painted under a cube of hair, which shows as a seam at the cap's edge.
    shell = {"L", "F", "S", "D"}
    for face in ("top", "back", "right", "left", "bottom"):
        fx, fy, fw, fh = net(*BOXES["head"])[face]
        tones = {px[fx + cx, fy + cy][:3] for cy in range(fh) for cx in range(fw)}
        if tones - {flesh[k] for k in shell}:
            bad.append(f"head.{face} is not all flesh — the hair is a cube, this is the scalp")

    cheek = lum(at(3, 5))
    for what, tone in (("iris", flesh["o"]), ("mouth", flesh["M"])):
        if abs(lum(tone) - cheek) < MIN_FEATURE_CONTRAST:
            bad.append(f"{what} only {abs(lum(tone) - cheek):.0f} from the cheek — invisible")

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
    return bad


# ── contact sheet ────────────────────────────────────────────────────

def crowd(bodies: Dict[Tuple[str, int, int], Image.Image], n: int = 10):
    """A village, not a swatch book.

    The whole objection was that a town reads as one face repainted, and no single figure answers
    it — only a row of strangers standing together does. The roll here STANDS IN for the game's:
    in play it is `CitizenNames.variant(uuid, salt, n)` per axis off the entity's own UUID, which
    this file cannot reach. What is being judged is whether ten people look like ten people.

    **This row cannot show the hair, the beard or the headwear.** Those are cubes in
    `NpcHeadModels` and only the game can draw them, so ten bareheaded bodies is the PESSIMISTIC
    view — the silhouette variation that the geometry adds is missing from it by construction.
    """
    rng = random.Random(20260729)
    garments = sorted(p.name for p in OUT.glob("*_clothes.png"))
    out = []
    for i in range(n):
        sex = rng.choice("mw")
        c, f = rng.randrange(COMPLEXION_COUNT), rng.randrange(FACE_COUNT)
        g = rng.choice(garments + [None, None])          # some of a village has no trade
        tex = bodies[(sex, c, f)]
        worn = Image.open(OUT / g).convert("RGBA") if g else None
        label = f"{'man' if sex == 'm' else 'woman'}\n{COMPLEXIONS[c][0]} {FACES[f][0]}\n" \
                f"{(g or 'no trade').replace('_clothes.png', '')}"
        out.append((label, elevation(tex, "front", worn)))
    return out


def contact_sheet(bodies: Dict[Tuple[str, int, int], Image.Image],
                  hair: Image.Image, cloth: Image.Image) -> Image.Image:
    garment = Image.open(OUT / GARMENT_FOR_SHEET).convert("RGBA")

    def label(sex, c, f):
        return f"{sex}{c}{f}\n{COMPLEXIONS[c][0]}\n{FACES[f][0]}"

    # One face across the four complexions, and one complexion across the six faces: the two
    # axes, each held still while the other moves.
    by_complexion = [(label(s, c, 0), bodies[(s, c, 0)])
                     for s in "mw" for c in range(COMPLEXION_COUNT)]
    faces_warm = [(label("m", 1, f), bodies[("m", 1, f)]) for f in range(FACE_COUNT)]
    faces_dark = [(label("w", 3, f), bodies[("w", 3, f)]) for f in range(FACE_COUNT)]

    W = (64 * 5 + 6) * 8 + 6
    plan = [
        ("THE NET — 64x64 on the player UV, four complexions x two cuts. The empty squares are "
         "meant to be empty: body and arm outer belong to the profession garment, and `hat` is "
         "empty in 9 of vanilla's 9 because headwear is a MODEL now.",
         [(n, checker(t)) for n, t in by_complexion], 5),
        ("THE HEAD at 26x — the six faces on `warm`, then on `dark`. Eyes row 4, sclera 1 and 6, "
         "iris 2 and 5, flat row 5, 2px mouth: all measured off vanilla's nine. NO MONOBROW and "
         "NO GREEN IRIS, which is what the relayed set had. The head is bald on purpose — the "
         "hair is a cube.",
         head_only(faces_warm + faces_dark), 26),
        ("FRONT, no trade — linen at the neck, their own cloth below it. Man: tunic to the knee "
         "over hose. Woman: gown to the ankle.",
         [(n, elevation(t, "front")) for n, t in by_complexion], 9),
        (f"FRONT + {GARMENT_FOR_SHEET} — the trade's tunic over the top. What shows of the base "
         "is the collar, the shoulders, the sleeve and everything below the hem.",
         [(n, elevation(t, "front", garment)) for n, t in by_complexion], 9),
        ("BACK — the cut from behind.",
         [(n, elevation(t, "back", garment)) for n, t in by_complexion], 9),
        ("RIGHT PROFILE — the hem line, and where the sleeve ends.",
         [(n, elevation(t, "right", garment)) for n, t in by_complexion], 9),
        ("A CROWD — ten people rolled at random, with and without a trade. THE HAIR, BEARD AND "
         "HEADWEAR ARE MISSING FROM THIS ROW: they are cubes in `NpcHeadModels` and only the "
         "game draws them, so this is the pessimistic view of a village.",
         crowd(bodies), 8),
        ("THE TWO MODEL MATERIALS at 8x — hair strands and a cloth twill. Near-white and fully "
         "opaque on purpose: the colour arrives as an ARGB multiply at render time, and every "
         "cube in `NpcHeadModels` samples texOffs(0,0), so there is no reserved region to punch "
         "a hole in.",
         [("npc_hair", hair), ("npc_headwear", cloth)], 8),
    ]
    strips = [strip(title, items, scale, W) for title, items, scale in plan]
    width = max(s.size[0] for s in strips)
    im = Image.new("RGBA", (width, sum(s.size[1] for s in strips) + 26), (12, 12, 14, 255))
    d = ImageDraw.Draw(im)
    d.text((6, 6), "BURG — the citizen bodies. 4 drawn complexions x 6 measured faces x 2 cuts "
                   "= 48. Hair, beard and headwear are geometry and are not on this sheet.",
           fill=(255, 255, 255, 255))
    y = 24
    for s in strips:
        im.paste(s, (0, y))
        y += s.size[1]
    return im


# ── driver ───────────────────────────────────────────────────────────

def body_name(sex: str, c: int, f: int) -> str:
    return f"citizen_{sex}_c{c}_f{f}.png"


def snapshot() -> Dict[str, str]:
    """sha256 of every PNG in the texture directory that is not ours to write.

    `default_skin.png`, `builder_clothes.png`, the nine garments and the twelve retired skins all
    live here. Ours are excluded — a second run is meant to replace them, and the first version
    of this guard in `make_female_skins` reported its own output as destroyed art.
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
    print("OK — every base region filled, nothing on `hat` or a garment layer, the cut right for "
          "each sex, the face on vanilla's rows with no non-flesh tone anywhere on it, a bald "
          "scalp for the hair cube to sit on, no invisible paint, both sides mirrored.")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true", help="draw and verify, write nothing")
    ap.add_argument("--check", action="store_true", help="verify what is already on disk")
    args = ap.parse_args()

    worst, pairs = face_distances()
    print("  the two authored axes, and the gates on them:")
    for i, (cname, f) in enumerate(COMPLEXIONS):
        s = span(i)
        flag = "" if MIN_SPAN <= s <= MAX_SPAN else "   <-- OUT OF RANGE"
        print(f"    c{i} {cname:6} lit {lum(f['L']):3.0f}  deep {lum(f['D']):3.0f}  "
              f"span {s:4.0f}{flag}")
    print(f"    face separation, weakest first — luminance-weighted, NOT a cell count "
          f"(gate {MIN_FACE_SEPARATION:.0f}):")
    for a, b, cells, w in pairs[:4]:
        print(f"      {a:7} / {b:7} {cells:3} cells  {w:6.0f} luminance")
    print(f"    worst {worst:.0f}")
    print()

    faults: Dict[str, List[str]] = {}
    for i in range(COMPLEXION_COUNT):
        if not MIN_SPAN <= span(i) <= MAX_SPAN:
            faults[COMPLEXIONS[i][0]] = [
                f"complexion span {span(i):.0f} outside {MIN_SPAN:.0f}..{MAX_SPAN:.0f} — "
                f"a crushed palette is what drawing them separately was for"]
    if worst < MIN_FACE_SEPARATION:
        a, b, cells, w = pairs[0]
        faults["faces"] = [f"{a} and {b} are {cells} cells but only {w:.0f} luminance points "
                           f"apart — they will read alike, which is what a cell count missed"]

    bodies: Dict[Tuple[str, int, int], Image.Image] = {}
    for sex in "mw":
        for c in range(COMPLEXION_COUNT):
            for f in range(FACE_COUNT):
                name = body_name(sex, c, f)
                if args.check:
                    p = OUT / name
                    if not p.exists():
                        faults[name] = ["not written yet"]
                        continue
                    tex = Image.open(p).convert("RGBA")
                else:
                    tex = materialise(draw(sex, f), sex, c)
                bodies[(sex, c, f)] = tex
                bad = verify(name, tex, sex, c)
                if bad:
                    faults[name] = bad

    if args.check:
        for extra in ("npc_hair.png", "npc_headwear.png"):
            if not (OUT / extra).exists():
                faults[extra] = ["not written yet"]
        return report(faults)

    print(f"  {len(bodies)} bodies drawn and verified "
          f"({COMPLEXION_COUNT} complexions x {FACE_COUNT} faces x 2 cuts)")
    hair, cloth = hair_material(), cloth_material()
    for what, mat in (("npc_hair", hair), ("npc_headwear", cloth)):
        opaque = sum(1 for y in range(MATERIAL_H) for x in range(MATERIAL_W)
                     if mat.load()[x, y][3] > 8)
        total = MATERIAL_W * MATERIAL_H
        if opaque != total:
            faults[f"{what}.png"] = [
                f"{total - opaque} transparent texel(s) — every cube samples texOffs(0,0), so a "
                f"hole lands on an unpredictable face"]

    if faults or args.dry_run:
        if args.dry_run and not faults:
            print("\ndry run: nothing written.")
        return report(faults)

    before = snapshot()
    OUT.mkdir(parents=True, exist_ok=True)
    SHEET.mkdir(parents=True, exist_ok=True)
    written = []
    for (sex, c, f), tex in bodies.items():
        name = body_name(sex, c, f)
        if not WRITEABLE.match(name):
            raise SystemExit(f"refusing to write {name}")
        tex.save(OUT / name)
        written.append(name)
    for what, mat in (("npc_hair", hair), ("npc_headwear", cloth)):
        mat.save(OUT / f"{what}.png")
        written.append(f"{what}.png")

    after = snapshot()
    changed = [n for n, h in before.items() if after.get(n) != h]
    if changed:
        raise SystemExit("DESTROYED EXISTING ART: " + ", ".join(changed))
    print(f"  wrote {len(written)} file(s); {len(before)} pre-existing PNG(s) byte-identical "
          f"after the write")

    contact_sheet(bodies, hair, cloth).save(SHEET / "citizen_skins.png")
    print(f"\nCONTACT SHEET -> {SHEET / 'citizen_skins.png'}")
    print("LOOK AT IT — and note the crowd row has no hair on it. The silhouette is geometry "
          "and only the game can show it.")
    return report(faults)


if __name__ == "__main__":
    sys.exit(main())
