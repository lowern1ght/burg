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

  * a **head cloth** over the hair — the single most legible marker, and free, because the
    player UV's `hat` box (32,0) is empty in every skin the mod ships;
  * a **gown to the ankle** instead of a tunic to the knee — which lives on the leg boxes'
    OUTER layer, (0,32) and (0,48), also empty in every shipped skin;
  * long sleeves to the wrist rather than a short tunic sleeve.

No second mesh. A slim-arm rig would double the graphics work and was deliberately deferred,
so every difference here is texture.

**Where a pixel may and may not go.** `NpcClothesLayer` re-renders the SAME mesh with the
profession garment, so any skin pixel on `body_outer` (16,32), `r_arm_outer` (40,32) or
`l_arm_outer` (48,48) is drawn in exactly the same place as the garment over it: z-fighting,
which reads as a broken model rather than a shirt. Those three regions are the garment's and
this file leaves them empty — checked, not assumed, by `verify()`. The leg outer layer is
free because no garment in the mod paints it.

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
from remap_npc_uv import NEW_REGIONS, faces as net

HERE = Path(__file__).resolve().parent
OUT = HERE.parent / (
    "common/src/main/resources/assets/onceuponatown/textures/entity/npc")
SHEET = HERE / "structures/out/npc"

# The only filenames this script is ever allowed to write. Anything else is a bug, and the
# bug it guards against destroys the only copy of somebody's hand-drawn work.
WRITEABLE = re.compile(r"^citizen_skin_f[0-9]\.png$")

MALE_SET = [f"citizen_skin_{i}.png" for i in range(6)]
GARMENT_FOR_SHEET = "farmer_clothes.png"


# ── the mesh, read off NpcModel.createBodyLayer() ────────────────────
#
#   head        texOffs(0, 0)   8x8x8      hat         texOffs(32, 0)  8x8x8  (+0.5)
#   body        texOffs(16,16)  8x12x4     body_outer  texOffs(16,32)  8x12x4 (+0.25)
#   right_arm   texOffs(40,16)  4x12x4     r_arm_outer texOffs(40,32)  4x12x4 (+0.25)
#   left_arm    texOffs(32,48)  4x12x4     l_arm_outer texOffs(48,48)  4x12x4 (+0.25)
#   right_leg   texOffs(0, 16)  4x12x4     r_leg_outer texOffs(0, 32)  4x12x4 (+0.25)
#   left_leg    texOffs(16,48)  4x12x4     l_leg_outer texOffs(0, 48)  4x12x4 (+0.25)
BOXES: Dict[str, Tuple[int, int, int, int, int]] = {
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
    "D": (0xa3, 0x6b, 0x4d),   # deep — under the jaw
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

# A head cloth was linen and usually undyed: three of them, paired round the six gowns so no
# two of the six women are the same combination.
VEILS = [
    ("linen",            (0xa8, 0xa2, 0x90), (0xcf, 0xc9, 0xb4), (0xe2, 0xdd, 0xc9)),
    ("unbleached linen", (0x8e, 0x83, 0x68), (0xb8, 0xad, 0x92), (0xcf, 0xc4, 0xa6)),
    ("grey linen",       (0x6f, 0x6d, 0x6a), (0x94, 0x8f, 0x89), (0xb3, 0xae, 0xaa)),
]

# Which cloth goes on which head, chosen for CONTRAST rather than by `i % 3`. Taking them in
# turn gave the grey-brown gown a grey linen veil, and the whole woman came out one grey mass
# with no head cloth in it — the marker is only a marker if it separates from the gown.
VEIL_FOR = [2, 0, 0, 1, 0, 0]

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


# ── the drawing ──────────────────────────────────────────────────────
#
# Every face is an ASCII block over this legend. Cloth is symbolic — '#' is "the gown's mid
# tone", not a colour — so one drawing yields six women and a palette change is one line.
#
#   .  transparent          #  gown mid     +  gown lit    -  gown shadow   =  gown trim
#   V  veil mid             W  veil lit     v  veil shadow
#   L F S D  flesh lit/mid/shadow/deep      M  mouth
#   A H h  hair lit/mid/shadow              B  brow         o  iris         O  sclera
#   Y X  shoe leather lit/mid               x  sole
#
# **Shading runs down the cloth, not across it, and it is jittered.** The first draft put its
# highlights in scattered pairs and every gown came out one flat field with specks in it. Cloth
# hangs, so a fold is a COLUMN: the torso drapes `-#+##+#-` — shadow at the two shoulder seams
# where the sleeve meets it, a highlight either side of centre — and a few courses move the
# highlight one column over, because an unjittered ramp reads as a painted stripe.

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
FACE_FRONT = [
    "HHHHHHHH",   # hair. Rows 0..2 are under the cloth band; drawn anyway, so a seam
    "HHHHHHHH",   # between the two cubes can never show bald scalp.
    "HHHHHHHH",
    "hDDFFDDh",   # brows — a darker flesh, noor's device
    "hOoFFoOh",   # eyes
    "hSFFFFSh",   # cheeks, shaded at the sides. No nose: nine of nine agree.
    "hFFMMFFh",   # mouth
    "hDFFFFDh",   # jaw, shaded at the sides
]
HEAD = {
    "front": FACE_FRONT,
    "top": ["HHHHHHHH"] * 5 + ["AAAAAAAA"] * 3,          # crown, lit toward the front
    "back": ["HHHHHHHH"] * 6 + ["hhhhhhhh"] * 2,
    "right": ["HHHHHHHH"] * 3 + ["SFFFFFFS"] * 4 + ["SSSSSSSS"],
    "left": ["HHHHHHHH"] * 3 + ["SFFFFFFS"] * 4 + ["SSSSSSSS"],
    "bottom": ["DDDDDDDD"] * 8,                          # under the jaw
}

# The head cloth: a three-course band low on the forehead, one column framing each temple and
# jaw, and everything else — crown, sides, back — closed. Hair does not show. That is one
# decision taken once rather than a half-covering that has to be argued about per skin.
#
# The face window is every '.' below, and it MUST stay open. Two of the first generated garment
# set had an opaque hat cube walling up the face — 80 of 80 pixels on its front, invisible in
# the net and unmissable on a figure — so `verify()` reads the window straight off this block
# and counts what landed in it.
VEIL = {
    "front": [
        "VVVVVVVV",
        "WWWWWWWW",   # the browband, two lit courses so it reads at a distance
        "WWWWWWWW",
        "v......v",
        "v......v",
        "V......V",
        "V......V",
        "V......V",
    ],
    "top": ["VVVVVVVV"] * 5 + ["WWWWWWWW"] * 3,
    "back": ["VVVVVVVV"] * 5 + ["vvvvvvvv"] * 3,
    "right": ["vVVVVVVW"] * 6 + ["vvvvvvvW"] * 2,        # col 7 is the frontmost edge
    "left": ["WVVVVVVv"] * 6 + ["Wvvvvvvv"] * 2,         # col 0 is, on this side
    "bottom": ["........"] * 8,                          # would only clip into the shoulders
}

# The gown from the shoulders to the girdle. `-` at cols 0 and 7 is the seam the sleeve's own
# inner shadow meets, which is what stops the whole figure reading as one mass of cloth.
BODICE = {
    "front": [
        "-#+FF+#-",   # a small keyhole neck, the throat showing
        "-##++##-",
        "-#+##+#-",
        "-#+##+#-",
        "-#+##+#-",
        "-##+#+#-",   # one course where the fold moves over, so the drape is not a stripe
        "========",   # the girdle, at the waist
        "-#+##+#-",
        "-#+##+#-",
        "-#+##+#-",
        "-##+#+#-",
        "--------",   # into the skirt
    ],
    "back": [
        "-#+##+#-", "-#+##+#-", "-#+##+#-", "-##+#+#-",
        "-#+##+#-", "-#+##+#-", "========", "-#+##+#-",
        "-#+##+#-", "-##+#+#-", "-#+##+#-", "--------",
    ],
    "right": ["-##+", "-##+", "-##+", "-#+#", "-##+", "-##+",
              "====", "-##+", "-##+", "-#+#", "-##+", "----"],
    "left":  ["+##-", "+##-", "+##-", "#+#-", "+##-", "+##-",
              "====", "+##-", "+##-", "#+#-", "+##-", "----"],
    "top":    ["-#+##+#-", "-#+##+#-", "-++++++-", "-++++++-"],
    "bottom": ["--------"] * 4,
}

# A sleeve to the wrist and a hand. Rows 0..1 sit above the shoulder line, inside the body, and
# col 3 is the side against the torso — hence the shadow column there.
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
            for cx, ch in enumerate(row):
                if mirror:
                    ch = row[w - 1 - cx]
                if ch != ".":
                    sym[y0 + cy][x0 + cx] = ch


def draw() -> List[List[str | None]]:
    """One woman, symbolically. The palette is applied afterwards, six times."""
    sym = blank()
    stamp(sym, "head", HEAD)
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


def materialise(sym, complexion: int, gown: int, veil: int) -> Image.Image:
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
    table.update({"-": g_sh, "#": g_mid, "+": g_hi, "=": g_trim,
                  "v": v_sh, "V": v_mid, "W": v_hi})

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


def sampled_pixels() -> set:
    """Every texel any face of any box reads. Anything else is invisible."""
    used = set()
    for box, dims in BOXES.items():
        for _, (x, y, w, h) in net(*dims).items():
            for yy in range(y, y + h):
                for xx in range(x, x + w):
                    used.add((xx, yy))
    return used


def verify(name: str, im: Image.Image) -> List[str]:
    """Every claim this file makes about its own output, counted.

    Fails loudly and by name, in the habit of `remap_npc_uv`, which refuses when a garment
    lands on a base region. Five things, each of which has actually gone wrong once:

      1. a base region empty      — the whole reason `remap_npc_uv.py` exists
      2. a base region unfilled   — a hole in a person is not a style choice
      3. a skin pixel on the garment's outer layer — z-fighting, a flickering seam
      4. an opaque face window    — a garment that walls the face up
      5. a pixel outside every sampled region — paint nobody will ever see
    """
    bad: List[str] = []
    counts = region_counts(im)

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
    # for a face to fit through.
    fx, fy, fw, fh = net(*BOXES["hat"])["front"]
    px = im.load()
    window = [(fx + cx, fy + cy) for cy, row in enumerate(VEIL["front"])
              for cx, ch in enumerate(row) if ch == "."]
    blocked = sum(1 for x, y in window if px[x, y][3] > 8)
    if blocked:
        bad.append(f"head cloth covers {blocked}/{len(window)} of the face window")
    if len(window) < 24:
        bad.append(f"face window is only {len(window)}px — no room for a face")
    veil_front = sum(1 for y in range(fy, fy + fh) for x in range(fx, fx + fw)
                     if px[x, y][3] > 8)
    if veil_front == 0:
        bad.append("head cloth has no front — the marker is invisible from the front")

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
                bad.append(f"{left}.{MIRROR_SWAP[face]} is not the mirror of "
                           f"{right}.{face}")
    print(f"  {name}")
    print("    " + "  ".join(f"{k}={v}" for k, v in counts.items()))
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
        base = [("head", (4, 0)), ("body", (6, 8)), ("r_leg", (6, 20))]
        over = [("r_leg_outer", (6, 20)), ("hat", (4, 0))]
        worn = [("body_outer", (6, 8)), ("r_arm_outer", (6, 8))]
        base += [("r_arm", (6, 8))]              # the arm is in front of the torso in profile
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
         "empty: body and arm outer belong to the profession garment.",
         [(n, checker(t)) for n, t in women], 5),
        ("FRONT — head cloth, bodice, ankle-length gown. The view that decides whether she "
         "reads as a woman.",
         [(n, elevation(t, "front")) for n, t in women], 9),
        (f"FRONT + {GARMENT_FOR_SHEET} on the outer layer — the garment sits over the bodice, "
         "nothing z-fights, the gown is still hers.",
         [(n, elevation(t, "front", garment)) for n, t in women], 9),
        ("BACK — the cloth falls to the shoulders.",
         [(n, elevation(t, "back")) for n, t in women], 9),
        ("RIGHT PROFILE — the cloth's silhouette.",
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
    d.text((6, 6), "BURG — the female citizen skins. Six women, on the same six complexions "
                   "as the six men, differing by covering and cut and by no new dye.",
           fill=(255, 255, 255, 255))
    y = 24
    for s in strips:
        im.paste(s, (0, y))
        y += s.size[1]
    return im


# ── driver ───────────────────────────────────────────────────────────

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


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true",
                    help="draw and verify, write nothing")
    ap.add_argument("--check", action="store_true",
                    help="verify the female skins already on disk")
    args = ap.parse_args()

    if args.check:
        bad = {}
        for i in range(6):
            p = OUT / f"citizen_skin_f{i}.png"
            if not p.exists():
                print(f"  {p.name}: not written yet")
                bad[p.name] = ["missing"]
                continue
            faults = verify(p.name, Image.open(p).convert("RGBA"))
            if faults:
                bad[p.name] = faults
        return report(bad, 0)

    sym = draw()
    women, faults = [], {}
    for i in range(6):
        tex = materialise(sym, complexion=i, gown=i, veil=VEIL_FOR[i])
        label = (f"f{i}  {COMPLEXIONS[i][0]}\n{GOWNS[i][0]} / {VEILS[VEIL_FOR[i]][0]}")
        f = verify(f"citizen_skin_f{i}.png  ({COMPLEXIONS[i][0]}, {GOWNS[i][0]}, "
                   f"{VEILS[VEIL_FOR[i]][0]})", tex)
        if f:
            faults[f"citizen_skin_f{i}.png"] = f
        women.append((label, tex))

    if faults or args.dry_run:
        if args.dry_run and not faults:
            print("\ndry run: 6 skins drawn and verified, nothing written.")
        return report(faults, 0)

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


def report(bad: Dict[str, List[str]], ok: int) -> int:
    print()
    if bad:
        for f, faults in bad.items():
            for fault in faults:
                print(f"FAIL  {f}: {fault}")
        return 1
    print("OK — every base region filled, no skin on the garment layer, face window clear, "
          "no invisible paint, both sides mirrored.")
    return ok


if __name__ == "__main__":
    sys.exit(main())
