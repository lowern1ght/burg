"""The female half of the town, drawn on the player UV that `NpcModel` now uses.

Citizens are vanilla villagers on our own human rig. Their names have always carried a
sex — `CitizenNames.isFeminine` is the first coin flip of the name generator, so a Hedda
was always a Hedda — but `TownVillagerRenderer.getTextureLocation` only ever indexed one
set of six skins, so half the town were women that nothing on screen could tell you about.

**The difference is cut and covering, not colour.** `NpcLook.TINTS` is already researched:
undyed cream, yellower fleece, grey-brown moorit, madder. Green and blue are absent because
they cost money a household in a sunken-floor house did not have. So dressing a woman by
inventing a dye would be undoing that work. What actually separated the sexes at a glance
in the eleventh century is the same thing that separates them here:

  * a **head cloth** over the hair — the most legible marker there is, and free, because the
    player UV's `hat` box (32,0) is empty in every skin the mod ships AND in all nine of
    vanilla's own player skins;
  * a **gown to the ankle** instead of a tunic to the knee — which lives on the leg boxes'
    OUTER layer, (0,32) and (0,48). Vanilla puts clothing there itself in 4 of its 9;
  * long sleeves to the wrist rather than a short tunic sleeve.

No second mesh. A slim-arm rig would double the graphics work and was deliberately deferred,
so every difference here is texture.

**Not every head is covered, and that is the point.** A married woman covered her hair; an
unmarried girl went bareheaded or plaited. Six of six veiled gave every woman the same
silhouette and read as a convent rather than a village, so two of the six go bareheaded with
the hair that was always drawn on the head cube underneath — one plaited, one loose. Which
two is an authored choice (`COVERED`): demographics are not a contrast measurement.

**She is dressed in layers, and the layers have to relate.** Measured off the garment mask —
all nine profession garments share it, being recolours of one drawn file — the overtunic is
SLEEVELESS with a deep V: it covers the torso front rows 6..11 completely and rows 0..5 only
at cols 0-1 and 6-7, and it puts a 9-pixel wedge on each shoulder and nothing else on the arm.
So what shows of the base when a profession is set is exactly a chest panel, the shoulders, and
the whole sleeve below the wedge.

That is why a coloured bodice read as "a sack thrown over a different-coloured dress". The base
torso above the waist is therefore an undyed linen **chemise** — the layer that is under
everything and competes with none of the nine — and the gown's colour lives where the period
puts it: below the tunic's hem (the skirt) and at the ends of the sleeves. Reading down the
middle of a working woman: linen at the neck, the trade's tunic, her own gown at skirt and
sleeve. One person in layers.

**Nothing existing is overwritten.** Only `default_skin.png` and `builder_clothes.png` are in
git HEAD; the other thirteen are untracked generated output that git could not bring back. So
this writes `citizen_skin_f0..f5.png` and NOTHING else, and it proves it: every pre-existing
PNG in the directory is hashed before the write and re-hashed after.

    python make_female_skins.py              # write the six + the contact sheet
    python make_female_skins.py --dry-run    # draw and verify, write nothing
    python make_female_skins.py --check      # verify what is already on disk

The art is code. Each face of each box is an ASCII block with a one-character legend, so a
change is a diff and a mistake is visible in review — the alternative, hand-editing pixels,
is not reproducible and this repo does not ship unverified geometry of any kind.
"""

from __future__ import annotations

import argparse
import hashlib
import re
import sys
from pathlib import Path
from typing import Dict, List, Tuple

from PIL import Image, ImageDraw

# One copy of the player-UV rule, not a third: `remap_npc_uv` already owns the net unwrap and
# the region table, and it learned them the hard way (the old mesh mirrored its right limbs, so
# every skin in the mod had an empty left arm and left leg).
from remap_npc_uv import (NEW_REGIONS, PLAYER_BOXES as BOXES, faces as net,
                          player_sampled as sampled_pixels)

HERE = Path(__file__).resolve().parent
OUT = HERE.parent / (
    "common/src/main/resources/assets/onceuponatown/textures/entity/npc")
SHEET = HERE / "structures/out/npc"

# The only filenames this script is ever allowed to write. Anything else is a bug, and the
# bug it guards against destroys the only copy of somebody's hand-drawn work.
WRITEABLE = re.compile(r"^citizen_skin_f[0-9]\.png$")

MALE_SET = [f"citizen_skin_{i}.png" for i in range(6)]
GARMENT_FOR_SHEET = "farmer_clothes.png"


# The mesh — `remap_npc_uv.PLAYER_BOXES`, imported above, is the single owner of it:
#
#   head        texOffs(0, 0)   8x8x8      hat         texOffs(32, 0)  8x8x8  (+0.5)
#   body        texOffs(16,16)  8x12x4     body_outer  texOffs(16,32)  8x12x4 (+0.25)
#   right_arm   texOffs(40,16)  4x12x4     r_arm_outer texOffs(40,32)  4x12x4 (+0.25)
#   left_arm    texOffs(32,48)  4x12x4     l_arm_outer texOffs(48,48)  4x12x4 (+0.25)
#   right_leg   texOffs(0, 16)  4x12x4     r_leg_outer texOffs(0, 32)  4x12x4 (+0.25)
#   left_leg    texOffs(16,48)  4x12x4     l_leg_outer texOffs(0, 48)  4x12x4 (+0.25)
#
# The six regions a body has to fill or the person has a hole in them, with the net area of
# each: 6 faces of a w x h x d box come to 2wd + 2dh + 2wh.
BASE_PARTS = {"head": 384, "body": 352, "r_arm": 224, "l_arm": 224,
              "r_leg": 224, "l_leg": 224}

# The garment's. A skin pixel here z-fights the profession overlay drawn over it.
GARMENT_PARTS = ("body_outer", "r_arm_outer", "l_arm_outer")

# The gown's. Empty in every shipped skin, which is what makes an ankle-length skirt free.
GOWN_PARTS = ("r_leg_outer", "l_leg_outer")

# A mirror of a box about X: every face flips horizontally and the two side faces swap. The
# left arm and left leg are NOT declared `.mirror()` in NpcModel — they carry their own
# texOffs — so symmetry has to be put into the texture, and this is the transform that does it.
MIRROR_SWAP = {"right": "left", "left": "right", "front": "front",
               "back": "back", "top": "top", "bottom": "bottom"}


# ── palette ──────────────────────────────────────────────────────────
#
# Flesh and hair are MEASURED off `default_skin.png` rather than invented, so a woman's face is
# the same complexion range as the men beside her: flesh #be886c x562, #b78272 x262, #b37b62
# x198, #a36b4d x18; the dark family #4c3833, #3d2d29, #905e43; the greys #6f6d6a, #948f89,
# #7f7b77, #545353 that were drawn into the male skin as an undershirt.
FLESH = {
    "L": (0xbe, 0x88, 0x6c),   # lit
    "F": (0xb7, 0x82, 0x72),   # mid
    "S": (0xb3, 0x7b, 0x62),   # shadow
    "D": (0xa3, 0x6b, 0x4d),   # deep — brows, under the jaw
    "M": (0x77, 0x42, 0x35),   # mouth; flesh, so it follows the complexion
}
HAIR = {
    "A": (0x5c, 0x42, 0x3a),   # lit
    "H": (0x4c, 0x38, 0x33),
    "h": (0x3d, 0x2d, 0x29),   # shadow
    "o": (0x4c, 0x38, 0x33),   # iris; shifts with the hair, so pale hair gets pale eyes
}
FIXED = {
    "O": (0xff, 0xff, 0xff),   # sclera
    "Y": (0x4c, 0x3a, 0x30),   # shoe leather, lit
    "X": (0x3d, 0x2d, 0x29),   # shoe leather
    "x": (0x23, 0x18, 0x14),   # sole
}

# The chemise: undyed linen, ONE cloth for all six, because its whole job is to be the layer
# under any of the nine profession garments without competing with one. Unbleached rather than
# white — bleaching is labour, and this is the same household that cannot afford woad.
CHEMISE = {
    "1": (0x6e, 0x65, 0x52),   # shadow
    "2": (0x9f, 0x95, 0x7c),   # mid
    "3": (0xb8, 0xad, 0x92),   # lit — the panel that shows through the tunic's V
}

# Cloth. Undyed fleece and the one dye an ordinary household could afford, exactly the range
# `NpcLook.TINTS` documents — cream, the yellower fleece, grey-brown moorit, madder. No green
# (woad over weld, double-dyed, expensive) and no blue. The `trim` of each is the same wool
# darker, so a girdle and a hem read as the same cloth worn, not as a second dye.
#                       shadow              mid                 lit                 trim
GOWNS = [
    ("undyed cream",  (0x8e, 0x83, 0x68), (0xc3, 0xb8, 0x9a), (0xd8, 0xcf, 0xb4), (0x7d, 0x72, 0x59)),
    ("yellow fleece", (0x7d, 0x6f, 0x4e), (0xb3, 0xa2, 0x79), (0xd8, 0xc9, 0xa8), (0x6a, 0x5d, 0x3f)),
    ("grey-brown",    (0x6b, 0x62, 0x59), (0x9a, 0x90, 0x84), (0xb0, 0xa4, 0x98), (0x57, 0x50, 0x49)),
    ("grey fleece",   (0x54, 0x53, 0x4f), (0x7f, 0x7b, 0x77), (0x94, 0x8f, 0x89), (0x44, 0x42, 0x40)),
    ("madder",        (0x5e, 0x3a, 0x2c), (0xa0, 0x63, 0x49), (0xc0, 0x8a, 0x63), (0x4c, 0x2e, 0x23)),
    ("moorit brown",  (0x5c, 0x44, 0x32), (0x8a, 0x6a, 0x4a), (0x9c, 0x7a, 0x4e), (0x4a, 0x37, 0x28)),
]

# A head cloth was linen. Two of them, because a third — unbleached — is the CHEMISE's cloth,
# and a veil the same colour as the chemise it falls onto is a veil you cannot see at the
# shoulder. Both shadows are deep on purpose: the shadow tone is what frames the face.
#                     shadow              mid                 lit
VEILS = [
    ("bleached linen", (0x5c, 0x58, 0x49), (0xcf, 0xc9, 0xb4), (0xe2, 0xdd, 0xc9)),
    ("grey linen",     (0x40, 0x3e, 0x3b), (0x94, 0x8f, 0x89), (0xb3, 0xae, 0xaa)),
]

# The same six complexions the male set uses, straight out of `make_npc_textures.SKIN_VARIANTS`,
# so face index 3 is the same person's colouring whichever sex he or she turns out to be.
#   name, flesh gain, flesh hue push, hair gain
COMPLEXIONS = [
    ("plains",    1.00, (0, 0, 0),     1.00),
    ("weathered", 0.88, (6, -2, -6),   0.80),
    ("pale",      1.10, (2, 4, 8),     1.25),
    ("olive",     0.94, (-6, 2, -10),  0.70),
    ("ruddy",     1.02, (14, -6, -8),  0.90),
    ("dark",      0.68, (-4, -4, -6),  0.55),
]

# How many of the six go bareheaded. AUTHORED, because it is a demographic and not a
# measurement: four married women and two girls is a village, six of six is a convent, and one
# of six would not read as a pattern at all. A head cloth is still the primary marker of sex on
# this rig, so the bare ones stay the minority.
BARE_HEADS = 2

# WHICH two, though, is measured — see `cast`. A bare head is judged as hair, and hair whose lit
# and shadow tones are 14 points apart is a silhouette rather than a hairstyle. The complexion's
# hair gain is shared with the male set and cannot be tuned per skin, so the choice goes the
# other way: the two heads with the most modelling left in them are the ones uncovered. Picking
# the darkest complexion for it gave a head that read as a black void at 40x.
HAIR_MODEL_FLOOR = 20.0

# Contrast floor for the cell beside the cheek. Below this, cloth and face read as one light
# blob at distance — which is exactly what f0, f2 and f3 did with a fixed pale frame.
FRAME_CONTRAST = 35.0


def lum(rgb) -> float:
    return 0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]


# ── the drawing ──────────────────────────────────────────────────────
#
# Every face is an ASCII block over this legend. Cloth is symbolic — '#' is "the gown's mid
# tone", not a colour — so one drawing yields six women and a palette change is one line.
#
#   .  transparent          #  gown mid     +  gown lit    -  gown shadow   =  gown trim
#   1 2 3  chemise shadow / mid / lit
#   V  veil mid             W  veil lit     v  veil shadow   f  veil, framing the face
#   L F S D  flesh lit/mid/shadow/deep      M  mouth
#   A H h  hair lit/mid/shadow              o  iris          O  sclera
#   Y X  shoe leather lit/mid               x  sole
#
# **Shading runs down the cloth, not across it, and it is jittered.** The first draft put its
# highlights in scattered pairs and every gown came out one flat field with specks in it. Cloth
# hangs, so a fold is a COLUMN: the torso drapes `-#+##+#-` — shadow at the two shoulder seams
# where the sleeve meets it, a highlight either side of centre — and one course per section
# moves the highlight over, because an unjittered ramp reads as a painted stripe.


def flip(rows: List[str]) -> List[str]:
    """A face mirrored left-right. Used to derive the left side of the head and the torso from
    the right, so two hand-written blocks cannot drift out of symmetry."""
    return [r[::-1] for r in rows]


# CALIBRATED, not invented. Vanilla ships nine human faces on this exact 64x64 layout
# (`assets/minecraft/textures/entity/player/wide/*.png` — alex, ari, efe, kai, makena, noor,
# steve, sunny, zuri), which is a corpus, so the face was measured off it rather than guessed:
#
#   * eyes on ROW 4, sclera at cols 1 and 6, iris at cols 2 and 5 — 9 of 9, no exceptions
#   * mouth on ROW 6, two pixels, cols 3 and 4 — 9 of 9
#   * row 5 is PLAIN FLESH — not one of the nine draws a nose. My first draft put a two-pixel
#     nose shadow directly above the two-pixel mouth and the pair merged into a dark blob
#   * a brow, where there is one at all, is a DARKER FLESH and never a hair colour (noor is
#     the only one of the nine with brows; eight have none). The male set here inherited the
#     villager's near-black monobrow bar and it is most of why those faces read as a villager
#   * shading on the chin and cheek rows sits in the outer COLUMNS, not across the row
#
# Cols 0 and 7 of rows 3..7 are hair. Under a veil the frame column covers them; bare, they are
# hair falling past the jaw — so ONE face drawing serves both, and that hair is what keeps a
# bareheaded citizen reading as a woman without a head cloth to say so.
HEAD_RIGHT = [
    "HHHHHHHH",
    "HHHHAAAA",   # lit toward the front — col 7 is the frontmost edge of a right face
    "HHHHHHHH",
    "HHHHhSFF",   # hair over the ear, cheek in front of it
    "HHHHhSFF",
    "HHHHhSFF",
    "HHHhhSFF",
    "HHhhhDDD",
]
HEAD = {
    "front": [
        "hHHHHHHh",
        "hHAAAAHh",
        "HHHHHHHH",   # the hairline
        "hDDFFDDh",   # brows — a darker flesh, noor's device
        "hOoFFoOh",   # eyes
        "hSFFFFSh",   # cheeks, shaded at the sides. No nose: nine of nine agree.
        "hFFMMFFh",   # mouth
        "hDFFFFDh",   # jaw, shaded at the sides
    ],
    "top": ["HHHHHHHH", "HHHHHHHH", "HAAAAAAH", "HAAAAAAH",
            "HAAAAAAH", "HHHHHHHH", "HHHHHHHH", "HHHHHHHH"],
    "right": HEAD_RIGHT,
    "left": flip(HEAD_RIGHT),
    "bottom": ["DDDDDDDD"] * 8,
}

# The back of the head, and the only place a hairstyle can live on an 8x8x8 cube.
HAIR_STYLES = {
    # Gathered, tapering to a tail.
    "loose": ["HHHHHHHH", "HAAAAAAH", "HHHHHHHH", "HHHHHHHH",
              "hHHHHHHh", "hHHHHHHh", "hhHHHHhh", "hhhHHhhh"],
    # A plait: two dark columns either side of a light one that ZIGZAGS, which is what reads as
    # plaiting at this scale. A straight light column just reads as a stripe.
    "plait": ["HHHHHHHH", "HAAAAAAH", "HHHhhHHH", "HHhAHhHH",
              "HHhHAhHH", "HHhAHhHH", "HHhHAhHH", "HHhAHhHH"],
}

# The head cloth: a three-course band low on the forehead, one column framing each temple and
# jaw, and everything else — crown, sides, back — closed. Hair does not show under a veil.
#
# The face window is every '.' below, and it MUST stay open. Two of the first generated garment
# set had an opaque hat cube walling up the face — 80 of 80 pixels on its front, invisible in
# the net and unmissable on a figure — so `verify()` reads the window straight off this block
# and counts what landed in it.
#
# The frame column is 'f', not a fixed tone: which of the cloth's tones outlines the face is
# decided per complexion by `frame_tone`, because a pale linen shadow beside pale flesh is no
# outline at all, and the same shadow beside dark flesh is no better.
VEIL_RIGHT = ["vVVVVVVW"] * 6 + ["vvvvvvvW"] * 2      # col 7 is the frontmost edge
VEIL = {
    "front": [
        "VVVVVVVV",
        "WWWWWWWW",   # the browband, two lit courses so it reads at a distance
        "WWWWWWWW",
        "f......f",
        "f......f",
        "f......f",
        "f......f",
        "f......f",
    ],
    "top": ["VVVVVVVV"] * 5 + ["WWWWWWWW"] * 3,
    "back": ["VVVVVVVV"] * 5 + ["vvvvvvvv"] * 3,
    "right": VEIL_RIGHT,
    "left": flip(VEIL_RIGHT),
    "bottom": ["........"] * 8,                        # would only clip into the shoulders
}

# The torso: a laced kirtle with the linen chemise showing as a panel at the chest.
#
# The panel is FOUR columns wide, cols 2..5, and the gown takes cols 0-1 and 6-7 as straps —
# and that is not a shape I chose, it is the garment's own mask read back. All nine profession
# garments cover the torso front completely from row 6 down and, above it, exactly cols 0-1 and
# 6-7. So the gown's straps land under the tunic's straps and the tunic's V shows nothing but
# linen: layers, with a garment.
#
# Without one it still reads, which is why the panel is not the whole torso. A first attempt
# made rows 0..5 linen edge to edge, and with no profession set she came out a sleeveless linen
# bodice with coloured sleeves stuck on it. Four columns of shift inside a laced kirtle is a
# garment either way.
BODICE_RIGHT = ["-##+", "-##+", "-##+", "-#+#", "-##+", "-##+",
                "====", "-##+", "-##+", "-#+#", "-##+", "----"]
BODICE = {
    "front": [
        "-#2FF2#-",   # a small round neck, the throat showing
        "-#2332#-",
        "-#2332#-",
        "-#2323#-",
        "-#2332#-",
        "-#2332#-",
        "========",   # the girdle: the top of the gown, on the waist
        "-#+##+#-",
        "-#+##+#-",
        "-#+##+#-",
        "-##+#+#-",
        "--------",   # into the skirt
    ],
    "back": [
        "-#2332#-", "-#2332#-", "-#2323#-", "-#2332#-",
        "-#2332#-", "-#2332#-", "========", "-#+##+#-",
        "-#+##+#-", "-##+#+#-", "-#+##+#-", "--------",
    ],
    "right": BODICE_RIGHT,               # the kirtle wraps the sides; no linen shows there
    "left": flip(BODICE_RIGHT),
    "top":    ["-#+##+#-", "-#+##+#-", "-#1331#-", "-#2332#-"],
    "bottom": ["--------"] * 4,
}

# The gown's sleeve, to the wrist, and a hand. Rows 0..1 sit above the shoulder line, inside the
# body, and col 3 is the side against the torso — hence the shadow column there.
#
# The sleeve stays the GOWN's and not the chemise's: the overtunic puts a nine-pixel wedge on
# the shoulder and nothing at all on the rest of the arm, so on a working woman the sleeve is
# the largest piece of her own cloth a player ever sees. That is the "ends of the sleeves" the
# period gives us, and it is why her colour survives a profession.
SLEEVE = {
    "front":  ["#+#-", "#+#-", "#+#-", "#+#-", "#+#-", "##+-",
               "#+#-", "#+#-", "====", "LFFS", "FFFS", "SSSS"],
    "back":   ["#+#-", "#+#-", "#+#-", "#+#-", "#+#-", "##+-",
               "#+#-", "#+#-", "====", "LFFS", "FFFS", "SSSS"],
    "right":  ["-##+", "-##+", "-##+", "-##+", "-##+", "-#+#",
               "-##+", "-##+", "====", "SFFL", "SFFF", "SSSS"],
    "left":   ["-##-", "-##-", "-##-", "-##-", "-##-", "-##-",
               "-##-", "-##-", "====", "SFFS", "SFFS", "SSSS"],
    "top":    ["-##-", "#++#", "#++#", "-##-"],
    "bottom": ["FFFF", "FSSF", "FSSF", "FFFF"],           # the palm
}

# Under the skirt: in shadow, and a shoe. The base leg is what shows below the hem, so the
# bottom two courses are the only part of it anyone sees.
UNDER = {
    "front":  ["-##-"] * 9 + ["----", "YYYY", "XXXX"],
    "back":   ["-##-"] * 9 + ["----", "YYYY", "XXXX"],
    "right":  ["----"] * 10 + ["YXXX", "XXXX"],
    "left":   ["----"] * 10 + ["XXXY", "XXXX"],
    "top":    ["----"] * 4,
    "bottom": ["xxxx"] * 4,
}

# The skirt itself, on the leg's outer layer. Ankle length: it stops two courses short so the
# shoe shows, and the last course is a hem band.
#
# Two leg cubes means the skirt splits when she walks. That is what a skirt costs on the player
# mesh, and the alternative — a second mesh — was deliberately deferred. `-#+#` on the right
# leg mirrors to `#+#-` on the left, so assembled the pair reads shadow, mid, lit, mid | mid,
# lit, mid, shadow: one draped volume rather than two tubes with a black seam between them.
SKIRT = {
    "front":  ["-#+#", "-#+#", "-#+#", "-##+", "-#+#", "-#+#",
               "-#+#", "-#+#", "-##+", "====", "....", "...."],
    "back":   ["-#+#", "-#+#", "-#+#", "-##+", "-#+#", "-#+#",
               "-#+#", "-#+#", "-##+", "====", "....", "...."],
    "right":  ["-##+", "-##+", "-##+", "-#+#", "-##+", "-##+",
               "-##+", "-##+", "-#+#", "====", "....", "...."],
    "left":   ["-##-", "-##-", "-##-", "-##-", "-##-", "-##-",
               "-##-", "-##-", "-##-", "====", "....", "...."],
    "top":    ["####"] * 4,
    "bottom": ["...."] * 4,                                # open, so the shoe shows
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


def draw(covered: bool, hair: str) -> List[List[str | None]]:
    """One woman, symbolically. The palette is applied afterwards."""
    sym = blank()
    stamp(sym, "head", dict(HEAD, back=HAIR_STYLES[hair]))
    if covered:
        stamp(sym, "hat", VEIL)
    stamp(sym, "body", BODICE)
    stamp(sym, "r_arm", SLEEVE)
    stamp(sym, "l_arm", SLEEVE, mirror=True)
    stamp(sym, "r_leg", UNDER)
    stamp(sym, "l_leg", UNDER, mirror=True)
    stamp(sym, "r_leg_outer", SKIRT)
    stamp(sym, "l_leg_outer", SKIRT, mirror=True)
    return sym


def shift(rgb, gain: float, push=(0, 0, 0)):
    return tuple(max(0, min(255, int(c * gain + p))) for c, p in zip(rgb, push))


def pick_veil(gown_mid) -> Tuple[int, float]:
    """Which linen, chosen by contrast rather than by taking the cloths in turn.

    `i % len(VEILS)` gave the grey-brown gown a grey linen veil and the whole woman came out one
    grey mass with no head cloth in it. The veil has two neighbours that matter: the CHEMISE it
    falls onto at the shoulder, and the gown it is seen against in the same silhouette. So the
    score is the weaker of those two separations and the winner is the cloth that keeps both.

    The chemise tone used is its LIT one, because that is the tone the veil actually touches —
    the shoulder is `13333331`. Scoring against the mid instead put the chemise within 5 points
    of grey linen, so bleached linen won all four veils and half the palette was dead code.
    """
    best, score = 0, -1.0
    for i, (_, _, mid, _) in enumerate(VEILS):
        s = min(abs(lum(mid) - lum(CHEMISE["3"])), abs(lum(mid) - lum(gown_mid)))
        if s > score:
            best, score = i, s
    return best, score


def frame_tone(veil, flesh) -> Tuple[str, float]:
    """Which of the cloth's tones frames the face.

    The column of cloth beside the cheek is the only outline the face has, and one fixed tone
    cannot do that job for six complexions: the veil's shadow beside pale flesh was a five-point
    step and the whole head read as one light blob, while the same shadow beside DARK flesh
    would be no better. So take the shadow where it separates and the highlight where it does
    not — which is also the truthful reading, a white cloth beside a dark face.
    """
    _, sh, _, hi = veil
    if abs(lum(sh) - lum(flesh)) >= FRAME_CONTRAST:
        return "v", abs(lum(sh) - lum(flesh))
    return "W", abs(lum(hi) - lum(flesh))


def materialise(sym, complexion: int, gown: int, veil: int, frame: str) -> Image.Image:
    """Turn the symbolic drawing into one 64x64 skin."""
    _, fg, push, hg = COMPLEXIONS[complexion]
    _, g_sh, g_mid, g_hi, g_trim = GOWNS[gown]
    _, v_sh, v_mid, v_hi = VEILS[veil]
    table: Dict[str, Tuple[int, int, int]] = {}
    for k, v in FLESH.items():
        table[k] = shift(v, fg, push)
    for k, v in HAIR.items():
        table[k] = shift(v, hg)
    table.update(FIXED)
    table.update(CHEMISE)
    table.update({"-": g_sh, "#": g_mid, "+": g_hi, "=": g_trim,
                  "v": v_sh, "V": v_mid, "W": v_hi})
    table["f"] = table[frame]

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


# ── verification ─────────────────────────────────────────────────────

def region_counts(im: Image.Image) -> Dict[str, int]:
    px = im.load()
    out = {}
    for name, (x0, y0, x1, y1) in NEW_REGIONS.items():
        out[name] = sum(1 for y in range(y0, y1) for x in range(x0, x1)
                        if px[x, y][3] > 8)
    return out


def verify(name: str, im: Image.Image, covered: bool) -> List[str]:
    """Every claim this file makes about its own output, counted.

    Fails loudly and by name, in the habit of `remap_npc_uv`, which refuses when a garment
    lands on a base region. Each of these has actually gone wrong once:

      1. a base region empty       — the whole reason `remap_npc_uv.py` exists
      2. a base region unfilled    — a hole in a person is not a style choice
      3. a skin pixel on the garment's outer layer — z-fighting, a flickering seam
      4. an opaque face window     — a garment that walls the face up
      5. cloth on a bare head      — a floating scrap on an unused cube
      6. a face with no outline    — the pale complexions read as one light blob
      7. a pixel outside every sampled region — paint nobody will ever see
    """
    bad: List[str] = []
    counts = region_counts(im)
    px = im.load()

    for part, area in BASE_PARTS.items():
        got = counts[part]
        if got == 0:
            bad.append(f"{part} EMPTY — the mesh samples it")
        elif got != area:
            bad.append(f"{part} {got}/{area} — {area - got} unpainted texels")

    for part in GARMENT_PARTS:
        if counts[part]:
            bad.append(f"{part} has {counts[part]}px of SKIN — z-fights the garment layer")

    for part in GOWN_PARTS:
        if counts[part] == 0:
            bad.append(f"{part} empty — no gown, so no ankle-length skirt")

    # The face window, read straight off VEIL["front"] rather than as a second copy of the
    # numbers: every '.' there has to come out transparent, and there have to be enough of them
    # for a face to fit through. A bareheaded citizen must have NOTHING on the hat cube — a few
    # stray pixels there render as a floating scrap of cloth round her head.
    fx, fy, fw, fh = net(*BOXES["hat"])["front"]
    window = [(fx + cx, fy + cy) for cy, row in enumerate(VEIL["front"])
              for cx, ch in enumerate(row) if ch == "."]
    if covered:
        blocked = sum(1 for x, y in window if px[x, y][3] > 8)
        if blocked:
            bad.append(f"head cloth covers {blocked}/{len(window)} of the face window")
        if len(window) < 24:
            bad.append(f"face window is only {len(window)}px — no room for a face")
        if counts["hat"] == 0:
            bad.append("covered, but the hat cube is empty — no head cloth at all")
    elif counts["hat"] != 0:
        bad.append(f"bareheaded, but {counts['hat']}px on the hat cube — floating cloth")

    # A face needs an outline, and whichever cube provides it has to earn it. Measured where it
    # actually matters — the cell beside the cheek, against the cheek. This is the check that
    # the "whole head reads as one light blob" complaint turned into a number.
    hx, hy, _, _ = net(*BOXES["head"])["front"]
    cheek = lum(px[hx + 3, hy + 5][:3])
    worst = 255.0
    for cy in (3, 5, 7):
        for cx in (0, 7):
            side = px[fx + cx, fy + cy] if covered else px[hx + cx, hy + cy]
            worst = min(worst, abs(lum(side[:3]) - cheek))
    if worst < FRAME_CONTRAST:
        bad.append(f"face outline only {worst:.0f} from the cheek — reads as one blob")

    # Hair over the forehead on the head cube itself, veiled or not, so a seam between the two
    # cubes can never show scalp.
    if abs(lum(px[hx + 3, hy][:3]) - cheek) < 40:
        bad.append("no hair over the forehead — the head cube reads as bald")

    # A bare head is judged as HAIR, so the hair has to have modelling in it and not just
    # contrast with the face. Measured between the lit crown and the temple shadow.
    if not covered:
        span = abs(lum(px[hx + 3, hy + 1][:3]) - lum(px[hx, hy + 3][:3]))
        if span < HAIR_MODEL_FLOOR:
            bad.append(f"bare head with only {span:.0f} points of modelling in the hair — "
                       f"reads as a void rather than a hairstyle")

    used = sampled_pixels()
    stray = sum(1 for y in range(64) for x in range(64)
                if px[x, y][3] > 8 and (x, y) not in used)
    if stray:
        bad.append(f"{stray}px outside every sampled region — invisible paint")

    # The two sides must match, or the town walks about lopsided. The male set was empty on
    # the left for a whole revision because the old mesh mirrored its right limbs.
    for right, left in (("r_arm", "l_arm"), ("r_leg", "l_leg"),
                        ("r_leg_outer", "l_leg_outer")):
        rf, lf = net(*BOXES[right]), net(*BOXES[left])
        for face in rf:
            rx, ry, w, h = rf[face]
            lx, ly, _, _ = lf[MIRROR_SWAP[face]]
            a = im.crop((rx, ry, rx + w, ry + h)).transpose(Image.FLIP_LEFT_RIGHT)
            b = im.crop((lx, ly, lx + w, ly + h))
            if a.tobytes() != b.tobytes():
                bad.append(f"{left}.{MIRROR_SWAP[face]} is not the mirror of {right}.{face}")

    print(f"  {name}")
    print(f"    outline {worst:.0f}   " + "  ".join(f"{k}={v}" for k, v in counts.items()))
    return bad


# ── contact sheet ────────────────────────────────────────────────────

def elevation(skin: Image.Image, view: str,
              garment: Image.Image | None = None) -> Image.Image:
    """A figure at the mesh's own proportions, composited in the model's own order.

    The check that matters. A flat 64x64 net says nothing about whether a head cloth reads as
    a head cloth: the first generated garment set looked like plausible cloth as a net and like
    a coloured slab on a body. Base layer first, then the outer cubes, then the profession
    garment last, exactly as the game draws them — so anything that covers the face covers it
    here too.

    A proxy, not a substitute: the outer cubes are inflated in world and are drawn here at the
    same size, and the legs swing in game and do not here.
    """
    im = Image.new("RGBA", (16, 32), (30, 30, 34, 255))

    def blit(src, box, face, at):
        x, y, w, h = net(*BOXES[box])[face]
        im.alpha_composite(src.crop((x, y, x + w, y + h)), at)

    if view == "front":
        # The entity's right is the viewer's LEFT, the way a player skin's face reads.
        base = [("head", (4, 0)), ("body", (4, 8)), ("r_arm", (0, 8)),
                ("l_arm", (12, 8)), ("r_leg", (4, 20)), ("l_leg", (8, 20))]
        over = [("r_leg_outer", (4, 20)), ("l_leg_outer", (8, 20)), ("hat", (4, 0))]
        worn = [("body_outer", (4, 8)), ("r_arm_outer", (0, 8)), ("l_arm_outer", (12, 8))]
        face = "front"
    elif view == "back":
        # Seen from behind, the entity's right hand is on the viewer's right.
        base = [("head", (4, 0)), ("body", (4, 8)), ("l_arm", (0, 8)),
                ("r_arm", (12, 8)), ("l_leg", (4, 20)), ("r_leg", (8, 20))]
        over = [("l_leg_outer", (4, 20)), ("r_leg_outer", (8, 20)), ("hat", (4, 0))]
        worn = [("body_outer", (4, 8)), ("l_arm_outer", (0, 8)), ("r_arm_outer", (12, 8))]
        face = "back"
    else:                                        # from the entity's right side
        base = [("head", (4, 0)), ("body", (6, 8)), ("r_leg", (6, 20)),
                ("r_arm", (6, 8))]               # the arm is in front of the torso in profile
        over = [("r_leg_outer", (6, 20)), ("hat", (4, 0))]
        worn = [("body_outer", (6, 8)), ("r_arm_outer", (6, 8))]
        face = "right"

    for box, at in base:
        blit(skin, box, face, at)
    for box, at in over:
        blit(skin, box, face, at)
    if garment is not None:
        for box, at in worn:
            blit(garment, box, face, at)
    return im


def strip(label: str, items: List[Tuple[str, Image.Image]], scale: int,
          width: int) -> Image.Image:
    pad, lab, line = 6, 14, 11
    w, h = items[0][1].size
    lines = max(len(n.split("\n")) for n, _ in items)
    im = Image.new("RGBA", (max(width, (w * scale + pad) * len(items) + pad),
                            h * scale + pad * 2 + lab + lines * line),
                   (18, 18, 20, 255))
    d = ImageDraw.Draw(im)
    d.text((pad, 2), label, fill=(255, 210, 120, 255))
    for i, (name, tex) in enumerate(items):
        x = pad + i * (w * scale + pad)
        im.paste(tex.resize((w * scale, h * scale), Image.NEAREST), (x, pad + lab))
        d.text((x + 1, pad + lab + h * scale + 1), name, fill=(215, 215, 215, 255))
    return im


def checker(im: Image.Image) -> Image.Image:
    """Transparency on a checkerboard, so an empty region reads as empty and not as black."""
    out = Image.new("RGBA", im.size, (0, 0, 0, 255))
    p = out.load()
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            p[x, y] = (46, 46, 52, 255) if (x // 4 + y // 4) % 2 else (34, 34, 38, 255)
    out.alpha_composite(im)
    return out


def head_only(items: List[Tuple[str, Image.Image]]) -> List[Tuple[str, Image.Image]]:
    """Just the head, for the zoom. Covered or bare, this is where it is decided."""
    out = []
    for name, tex in items:
        h = Image.new("RGBA", (8, 8), (30, 30, 34, 255))
        for box in ("head", "hat"):
            x, y, w, hh = net(*BOXES[box])["front"]
            h.alpha_composite(tex.crop((x, y, x + w, y + hh)))
        out.append((name, h))
    return out


def contact_sheet(women: List[Tuple[str, Image.Image]]) -> Image.Image:
    garment = Image.open(OUT / GARMENT_FOR_SHEET).convert("RGBA")
    men = []
    for f in MALE_SET:
        p = OUT / f
        if p.exists():
            men.append((f.replace("citizen_skin_", "male ").replace(".png", ""),
                        Image.open(p).convert("RGBA")))

    W = (64 * 5 + 6) * 6 + 6            # the net strip is the widest thing on the sheet
    plan = [
        ("THE NET — flat-laid, 64x64 on the player UV. The empty squares are meant to be "
         "empty: body and arm outer belong to the profession garment, and a bareheaded citizen "
         "leaves the hat cube blank.",
         [(n, checker(t)) for n, t in women], 5),
        ("THE HEAD at 26x — four covered, two bareheaded. Judge the outline against the cheek "
         "here: this is where 'one light blob' was.",
         head_only(women), 26),
        ("FRONT, no profession — linen chemise above the waist, her own cloth below it and at "
         "the sleeves.",
         [(n, elevation(t, "front")) for n, t in women], 9),
        (f"FRONT + {GARMENT_FOR_SHEET} — linen at the neck, the trade's tunic, her gown at "
         "skirt and sleeve. One person in layers.",
         [(n, elevation(t, "front", garment)) for n, t in women], 9),
        ("BACK — the cloth falls to the shoulders; bare, the hair does.",
         [(n, elevation(t, "back")) for n, t in women], 9),
        ("RIGHT PROFILE — the covering's silhouette.",
         [(n, elevation(t, "right")) for n, t in women], 9),
    ]
    if men:
        plan.append((
            "THE MEN, for comparison — same six complexions, bare head, tunic to the knee. "
            "Their faces are the villager crop and are known to be rough.",
            [(n, elevation(t, "front", garment)) for n, t in men], 9))
    strips = [strip(title, items, scale, W) for title, items, scale in plan]

    width = max(s.size[0] for s in strips)
    height = sum(s.size[1] for s in strips) + 26
    im = Image.new("RGBA", (width, height), (12, 12, 14, 255))
    d = ImageDraw.Draw(im)
    d.text((6, 6), "BURG — the female citizen skins. Six women on the same six complexions as "
                   "the six men, differing by covering and cut and by no new dye.",
           fill=(255, 255, 255, 255))
    y = 24
    for s in strips:
        im.paste(s, (0, y))
        y += s.size[1]
    return im


# ── driver ───────────────────────────────────────────────────────────

def hair_range(hair_gain: float) -> float:
    """How much modelling a complexion's hair has left after its gain is applied.

    `A` and `h` are 26 points apart as drawn. The dark complexion multiplies the whole hair
    family by 0.55, which leaves 14 — enough to pass a contrast check against the face and not
    enough to read as hair rather than as a hole in the head.
    """
    return abs(lum(shift(HAIR["A"], hair_gain)) - lum(shift(HAIR["h"], hair_gain)))


def cast() -> List[dict]:
    """Who the six are.

    Authored: how many go bareheaded, and the gown each wears. Measured: which two those are,
    which linen a veil is cut from, and which of the cloth's tones frames the face. Everything
    measured prints its number, so a change of palette re-decides rather than drifting.
    """
    bare = sorted(range(len(COMPLEXIONS)),
                  key=lambda i: -hair_range(COMPLEXIONS[i][3]))[:BARE_HEADS]
    styles = ["plait", "loose"]
    out = []
    for i, (cname, fg, push, hg) in enumerate(COMPLEXIONS):
        flesh = shift(FLESH["F"], fg, push)
        veil, vscore = pick_veil(GOWNS[i][2])
        frame, fscore = frame_tone(VEILS[veil], flesh)
        out.append(dict(i=i, complexion=cname, gown=i, covered=i not in bare,
                        hair=styles[bare.index(i) % len(styles)] if i in bare else "loose",
                        veil=veil, frame=frame, vscore=vscore, fscore=fscore,
                        hrange=hair_range(hg)))
    return out


def snapshot() -> Dict[str, str]:
    """sha256 of every PNG in the texture directory that is not ours to write.

    Only `default_skin.png` and `builder_clothes.png` are in git HEAD; the other thirteen are
    untracked generated output, so overwriting one destroys the only copy there is. Hashing
    before and after is how this script proves it did not. Our own six are excluded — a second
    run is meant to replace them, and the first version of this guard reported its own output
    as destroyed art.
    """
    return {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sorted(OUT.glob("*.png")) if not WRITEABLE.match(p.name)}


def describe(p: dict) -> str:
    if not p["covered"]:
        return f"bare, {p['hair']}"
    return f"veiled, {VEILS[p['veil']][0]}"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true",
                    help="draw and verify, write nothing")
    ap.add_argument("--check", action="store_true",
                    help="verify the female skins already on disk")
    args = ap.parse_args()

    people = cast()

    if args.check:
        bad = {}
        for p in people:
            path = OUT / f"citizen_skin_f{p['i']}.png"
            if not path.exists():
                print(f"  {path.name}: not written yet")
                bad[path.name] = ["missing"]
                continue
            faults = verify(path.name, Image.open(path).convert("RGBA"), p["covered"])
            if faults:
                bad[path.name] = faults
        return report(bad)

    print(f"  the cast. {BARE_HEADS} of 6 bareheaded is authored; WHICH two, the linen and the "
          f"frame tone are measured:")
    print(f"    {'':5}{'complexion':11} {'gown':14} {'head':24} "
          f"{'hair range':>10} {'linen sep':>9} {'frame sep':>9}")
    for p in people:
        vsep = "%.0f" % p["vscore"] if p["covered"] else "-"
        fsep = "%.0f" % p["fscore"] if p["covered"] else "-"
        print(f"    f{p['i']}   {p['complexion']:11} {GOWNS[p['gown']][0]:14} "
              f"{describe(p):24} {p['hrange']:10.0f} {vsep:>9} {fsep:>9}")
    print()

    women, faults = [], {}
    for p in people:
        sym = draw(p["covered"], p["hair"])
        tex = materialise(sym, p["i"], p["gown"], p["veil"], p["frame"])
        label = f"f{p['i']}  {p['complexion']}\n{GOWNS[p['gown']][0]}\n{describe(p)}"
        f = verify(f"citizen_skin_f{p['i']}.png  ({p['complexion']}, "
                   f"{GOWNS[p['gown']][0]}, {describe(p)})", tex, p["covered"])
        if f:
            faults[f"citizen_skin_f{p['i']}.png"] = f
        women.append((label, tex))

    if faults or args.dry_run:
        if args.dry_run and not faults:
            print("\ndry run: 6 skins drawn and verified, nothing written.")
        return report(faults)

    before = snapshot()
    OUT.mkdir(parents=True, exist_ok=True)
    SHEET.mkdir(parents=True, exist_ok=True)
    for i, (_, tex) in enumerate(women):
        name = f"citizen_skin_f{i}.png"
        if not WRITEABLE.match(name):
            raise SystemExit(f"refusing to write {name}: not a female-skin filename")
        tex.save(OUT / name)
        print(f"  wrote {name}")

    after = snapshot()
    changed = [n for n, h in before.items() if after.get(n) != h]
    if changed:
        raise SystemExit("DESTROYED EXISTING ART: " + ", ".join(changed))
    print(f"  {len(before)} pre-existing PNG(s) byte-identical after the write")

    contact_sheet(women).save(SHEET / "female_skins.png")
    print(f"\n6 skins -> {OUT}")
    print(f"CONTACT SHEET -> {SHEET / 'female_skins.png'}")
    print("LOOK AT IT. No count can tell you whether a head cloth reads as a head cloth.")
    return 0


def report(bad: Dict[str, List[str]]) -> int:
    print()
    if bad:
        for f, faults in bad.items():
            for fault in faults:
                print(f"FAIL  {f}: {fault}")
        return 1
    print("OK — every base region filled, no skin on the garment layer, face window clear on "
          "the veiled and the hat cube empty on the bare, every face outlined against its own "
          "cheek, no invisible paint, both sides mirrored.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
