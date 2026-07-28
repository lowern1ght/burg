"""Clothing overlays for the town NPCs, on the model's own measured UV layout.

**The layout is measured, not assumed.** `NpcModel.createBodyLayer()` builds a
villager-shaped mesh — nose, hat, robe, crossed arms — on a HumanoidModel base, so
its `texOffs` are the VANILLA VILLAGER offsets and not the player skin's:

    hat        texOffs(32, 0)   8x10x8      head    texOffs(0, 0)  8x10x8
    nose       texOffs(24, 0)   2x4x2       body    texOffs(16,20) 8x12x6
    jacket     texOffs(0, 38)   8x20x6      arms    texOffs(44,22) 4x12x4
    legs       texOffs(0, 22)   4x12x4      crossed texOffs(40,38) 8x4x4

Reading the file against the PLAYER layout is what misled me: measured that way,
`builder_clothes.png` looks like it paints jacket/sleeve/trouser regions the model
never samples. It does not — `--check` reports 0 stray pixels, because those same
coordinates fall inside the villager `body`, `jacket`, `arm` and `leg` nets. The
file is simply unfinished: 521 opaque pixels of 4096, all of them legal, most of
them on one leg. Believe the checker, not the first read.

`NpcClothesLayer` calls `renderColoredCutoutModel(getParentModel(), ...)`, i.e. it
re-renders the SAME mesh with this texture, so anything opaque here is drawn over
the skin and anything transparent lets the skin through. A garment is therefore
just the body/arm/leg/jacket regions painted, with the head left clear.

    python make_npc_textures.py            # write the set + a labelled guide
    python make_npc_textures.py --check    # report which regions each file paints

Every variant is a RECOLOUR of the hand-drawn `builder_clothes.png`, not a synthesis:
its alpha mask is the garment's shape and its luminance is the folds, and only the
palette changes. The first version filled rectangles instead, and the half-finished
hand-drawn file beat all seven of its outputs — no neckline, no cuff, no hem, no hands,
and on two of them an opaque hat that walled up the face. Reuse the shape; do not
invent it.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Dict, List, Tuple

from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parent.parent / (
    "common/src/main/resources/assets/onceuponatown/textures/entity/npc")
SHEET = Path(__file__).resolve().parent / "structures/out/npc"

Box = Tuple[int, int, int, int]          # x0, y0, w, h


def net(u: int, v: int, w: int, h: int, d: int) -> Dict[str, Box]:
    """The six faces of a Minecraft box, as rectangles in the texture.

    The one piece of Minecraft geometry worth writing down: a box of w x h x d at
    `texOffs(u, v)` unwraps as top and bottom in a d-tall strip, then the four
    sides in an h-tall strip below it, ordered right, front, left, back.
    """
    return {
        "top":    (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right":  (u, v + d, d, h),
        "front":  (u + d, v + d, w, h),
        "left":   (u + d + w, v + d, d, h),
        "back":   (u + d + w + d, v + d, w, h),
    }


# The mesh, straight out of NpcModel.createBodyLayer().
PARTS: Dict[str, Dict[str, Box]] = {
    "head":    net(0, 0, 8, 10, 8),
    "hat":     net(32, 0, 8, 10, 8),
    "nose":    net(24, 0, 2, 4, 2),
    "body":    net(16, 20, 8, 12, 6),
    "jacket":  net(0, 38, 8, 20, 6),
    "arm":     net(44, 22, 4, 12, 4),
    "leg":     net(0, 22, 4, 12, 4),
    "crossed": net(40, 38, 8, 4, 4),
}
HAT_RIM: Box = (31, 47, 16, 16)          # texOffs(30,47) 16x16x1, the flat brim


SOURCE = "builder_clothes.png"      # the hand-drawn garment every variant is cut from


# Regions to flip when `--mirror` is passed. The hand-drawn garment is asymmetric —
# jacket.front differs from its own mirror in 24 of 121 opaque pixels — and which
# shoulder the strap belongs on is a drawing decision, not something the geometry
# settles. Flipping is offered rather than assumed because I cannot check it in game.
MIRRORABLE = ("jacket", "body", "crossed", "arm", "leg")


def mirror_garment(src: Image.Image) -> Image.Image:
    """Flip each part's faces left-right in place, so the garment swaps shoulders."""
    out = src.copy()
    for part in MIRRORABLE:
        for face, (x, y, w, h) in PARTS[part].items():
            piece = src.crop((x, y, x + w, y + h)).transpose(Image.FLIP_LEFT_RIGHT)
            out.paste(piece, (x, y))
    return out


def recolour(src: Image.Image, shadow, mid, trim, bulk_hi: int = 60) -> Image.Image:
    """Repaint the hand-drawn garment, keeping every drawn pixel where it was.

    This replaces a generator that filled whole rectangles with flat colour. Painting
    rectangles is what made the first set unusable: no neckline, no cuff, no hem, no
    hands — a coloured slab from shoulder to floor. Side by side, this half-finished
    hand-drawn file beat all seven of them, because it has SHAPE and they only had
    colour.

    So the shape is not synthesised, it is inherited. The alpha mask decides where cloth
    is and where skin shows through; the drawn luminance decides the folds. Only the
    palette moves. Measured over the source: 521 opaque pixels, luminance 25..207 with
    75% of them between 25 and 54, so the bulk is remapped across shadow..mid and the
    bright tail — the belt and its buckle — becomes the trim.

    A consequence worth stating: every profession then wears the same CUT in a different
    cloth. That is honest for a village and it is what this method can give. Genuinely
    different garments per trade need a hand at the pixels, not a better remap.
    """
    out = Image.new("RGBA", src.size, (0, 0, 0, 0))
    sp, op = src.load(), out.load()
    for y in range(src.size[1]):
        for x in range(src.size[0]):
            r, g, b, alpha = sp[x, y]
            if alpha == 0:
                continue
            lum = 0.299 * r + 0.587 * g + 0.114 * b
            if lum > bulk_hi:
                # The drawn highlight: belt, buckle, a lit edge. Keep it bright and give
                # it the trim colour so the garment reads as having a fitting.
                f = min(1.0, 0.55 + 0.45 * (lum - bulk_hi) / max(1.0, 207 - bulk_hi))
                op[x, y] = (int(trim[0] * f), int(trim[1] * f), int(trim[2] * f), alpha)
            else:
                t = max(0.0, min(1.0, (lum - 25) / max(1.0, bulk_hi - 25)))
                op[x, y] = (int(shadow[0] + (mid[0] - shadow[0]) * t),
                            int(shadow[1] + (mid[1] - shadow[1]) * t),
                            int(shadow[2] + (mid[2] - shadow[2]) * t), alpha)
    return out


# The palette stays the mod's own: the greys measured out of the source, plus the flax,
# oak and cobble range the buildings use. No saturated colour except the chief's — a
# village that has just learnt to cut stone has not learnt to dye cloth, which is the
# same ladder the buildings climb.
CHARCOAL = (0x2a, 0x28, 0x27)
ASH = (0x44, 0x42, 0x40)
SOOT = (0x1c, 0x1c, 0x1c)
FLAX = (0x8e, 0x83, 0x68)
UNDYED = (0xc3, 0xb8, 0x9a)
OAK = (0x9c, 0x7a, 0x4e)
RUST = (0x7a, 0x40, 0x28)
IRON = (0x8e, 0x93, 0x99)
MOSS = (0x4a, 0x57, 0x38)
BLOOD = (0x5e, 0x1e, 0x1c)

# shadow, mid, trim
SET = [
    ("chief_clothes", (CHARCOAL, BLOOD, OAK)),
    ("soldier_clothes", (SOOT, ASH, IRON)),
    ("soldier_veteran_clothes", (SOOT, CHARCOAL, RUST)),
    ("farmer_clothes", (OAK, FLAX, UNDYED)),
    ("mason_clothes", (CHARCOAL, ASH, IRON)),
    ("smith_clothes", (SOOT, RUST, IRON)),
    ("forester_clothes", (CHARCOAL, MOSS, OAK)),
]


# ── base skins ──────────────────────────────────────────────────────
#
# Vanilla gives a villager variety through its TYPE — seven biome skins, chosen per
# villager and rendered under the profession overlay. Ours had exactly one, so every
# citizen was the same person in different clothes.
#
# The same harvest-and-recolour discipline as the garments: the drawn skin is the shape,
# only the palette moves. Measured over `default_skin.png` — 1528 opaque pixels, 23
# distinct colours — the palette falls into three families that must be shifted
# separately, because shifting them together turns a suntan into a costume:
#
#   flesh   #be886c x500, #b78272 x228, #b37b62 x169, #b57b67 x21
#   dark    #905e43, #75472f, #4c3833, #3d2d29        hair, brow, mouth
#   grey    #6f6d6a x142, #636260 x128, #545353 x84   the undershirt, drawn into the skin
#
# Classified by measurement rather than by listing the hex codes, so a repainted source
# keeps working: near-neutral is the shirt, light is flesh, the rest is hair.
SKIN_VARIANTS = [
    # name,        flesh gain,   flesh hue push (r,g,b),  hair gain, shirt gain
    ("plains",     1.00, (0, 0, 0),        1.00, 1.00),
    ("weathered",  0.88, (6, -2, -6),      0.80, 0.94),
    ("pale",       1.10, (2, 4, 8),        1.25, 1.06),
    ("olive",      0.94, (-6, 2, -10),     0.70, 0.90),
    ("ruddy",      1.02, (14, -6, -8),     0.90, 0.98),
    ("dark",       0.68, (-4, -4, -6),     0.55, 0.86),
]


def _family(rgb) -> str:
    r, g, b = rgb
    if max(r, g, b) - min(r, g, b) < 14:
        return "grey"                       # the undershirt: neutral by construction
    return "flesh" if 0.299 * r + 0.587 * g + 0.114 * b > 118 else "hair"


def skin_variant(src: Image.Image, flesh_gain: float, push, hair_gain: float,
                 shirt_gain: float) -> Image.Image:
    """One base skin: same drawing, different colouring."""
    out = Image.new("RGBA", src.size, (0, 0, 0, 0))
    sp, op = src.load(), out.load()
    for y in range(src.size[1]):
        for x in range(src.size[0]):
            r, g, b, a = sp[x, y]
            if a == 0:
                continue
            fam = _family((r, g, b))
            if fam == "flesh":
                r, g, b = (r * flesh_gain + push[0], g * flesh_gain + push[1],
                           b * flesh_gain + push[2])
            elif fam == "hair":
                r, g, b = r * hair_gain, g * hair_gain, b * hair_gain
            else:
                r, g, b = r * shirt_gain, g * shirt_gain, b * shirt_gain
            op[x, y] = (max(0, min(255, int(r))), max(0, min(255, int(g))),
                        max(0, min(255, int(b))), a)
    return out


SKIN = "default_skin.png"           # the body underneath, for the front-view check


def src_skin() -> Image.Image:
    return Image.open(OUT / SKIN).convert("RGBA")


def front_view(items: List[Tuple[str, Image.Image]], skin: Image.Image) -> Image.Image:
    """The garment ON a figure, front elevation, at the mesh's own proportions.

    The check that matters, and the one I skipped. A flat view of the 64x64 net says
    nothing about whether a garment reads: the first set looked like plausible cloth as a
    net and like a coloured slab on a body, and two of them had an opaque hat cube walling
    up the face — 80 of 80 pixels on its front, invisible in the net, unmissable here.

    Composited in the model's own order: skin first, then robe, body, arms, legs, hat, so
    anything that covers the face covers it here too. A proxy for the game, not a
    substitute — the robe is inflated half a pixel in world and the arms swing — but it
    answers "is this a dressed villager or a painted box" without launching anything.
    """
    def figure(tex: Image.Image) -> Image.Image:
        im = Image.new("RGBA", (24, 34), (30, 30, 34, 255))

        def blit(src: Image.Image, box: Box, at: Tuple[int, int],
                 mirrored: bool = False) -> None:
            x, y, w, h = box
            piece = src.crop((x, y, x + w, y + h))
            if mirrored:
                piece = piece.transpose(Image.FLIP_LEFT_RIGHT)
            im.alpha_composite(piece, at)

        # `left_arm` and `left_leg` are declared `.mirror()` in NpcModel, so the same
        # texture region is sampled flipped on that side. The first version of this
        # preview ignored that and hung one unmirrored copy on both arms — and since
        # `arm.front` is asymmetric (mirror-diff 16 of 9 opaque pixels), one of the two
        # arms it drew was simply wrong. A preview that lies about which side a detail
        # falls on is worse than no preview.
        #
        # Viewer's left is the entity's RIGHT: a front face in the PNG reads as the
        # observer sees it, the same way a player skin's face does.
        for part, at, mir in (("leg", (8, 22), False), ("leg", (12, 22), True),
                              ("arm", (4, 10), False), ("arm", (16, 10), True),
                              ("body", (8, 10), False), ("head", (8, 0), False)):
            blit(skin, PARTS[part]["front"], at, mir)
        for part, at, mir in (("jacket", (8, 10), False), ("body", (8, 10), False),
                              ("arm", (4, 10), False), ("arm", (16, 10), True),
                              ("leg", (8, 22), False), ("leg", (12, 22), True),
                              ("hat", (8, 0), False)):
            blit(tex, PARTS[part]["front"], at, mir)
        return im

    scale, pad = 10, 6
    im = Image.new("RGBA", ((24 * scale + pad) * len(items) + pad,
                            34 * scale + pad * 2 + 14), (18, 18, 20, 255))
    d = ImageDraw.Draw(im)
    for i, (name, tex) in enumerate(items):
        x = pad + i * (24 * scale + pad)
        im.paste(figure(tex).resize((24 * scale, 34 * scale), Image.NEAREST), (x, pad))
        d.text((x + 2, 34 * scale + pad + 2), name.replace("_clothes", ""),
               fill=(220, 220, 220, 255))
    return im


def faces_clear(items: List[Tuple[str, Image.Image]]) -> int:
    """No garment may close over the face. Counted, not eyeballed."""
    x, y, w, h = PARTS["hat"]["front"]
    bad = 0
    for name, tex in items:
        px = tex.load()
        op = sum(1 for yy in range(y, y + h) for xx in range(x, x + w)
                 if px[xx, yy][3] > 0)
        if op > w * h * 0.8:
            print(f"  {name}: hat front {op}/{w * h} opaque — FACE WALLED UP")
            bad += 1
    return bad


def guide() -> Image.Image:
    """A labelled 8x blow-up: which square is which part, so a hand painter
    is not guessing. Not shipped — it goes to the contact-sheet folder."""
    scale = 8
    im = Image.new("RGBA", (64 * scale, 64 * scale), (18, 18, 20, 255))
    d = ImageDraw.Draw(im)
    colours = {
        "head": (90, 140, 200), "hat": (140, 110, 200), "nose": (200, 200, 90),
        "body": (200, 120, 80), "jacket": (90, 180, 120), "arm": (200, 90, 140),
        "leg": (110, 170, 200), "crossed": (170, 170, 170),
    }
    for part, faces in PARTS.items():
        for face, (x, y, w, h) in faces.items():
            d.rectangle([x * scale, y * scale,
                         (x + w) * scale - 1, (y + h) * scale - 1],
                        fill=colours[part] + (110,), outline=(20, 20, 20, 255))
            d.text((x * scale + 2, y * scale + 2), f"{part}\n{face}",
                   fill=(255, 255, 255, 230))
    x, y, w, h = HAT_RIM
    d.rectangle([x * scale, y * scale, (x + w) * scale - 1, (y + h) * scale - 1],
                fill=(140, 110, 200, 110), outline=(20, 20, 20, 255))
    d.text((x * scale + 2, y * scale + 2), "hat_rim", fill=(255, 255, 255, 230))
    for i in range(0, 65, 8):
        d.line([i * scale, 0, i * scale, 64 * scale], fill=(60, 60, 66, 255))
        d.line([0, i * scale, 64 * scale, i * scale], fill=(60, 60, 66, 255))
    return im


def sheet(items: List[Tuple[str, Image.Image]]) -> Image.Image:
    """All the overlays side by side at 6x, for looking at in one glance."""
    scale, pad = 6, 8
    w = (64 * scale + pad) * len(items) + pad
    im = Image.new("RGBA", (w, 64 * scale + pad * 2 + 14), (18, 18, 20, 255))
    d = ImageDraw.Draw(im)
    for i, (name, tex) in enumerate(items):
        x = pad + i * (64 * scale + pad)
        im.paste(tex.resize((64 * scale, 64 * scale), Image.NEAREST), (x, pad))
        d.text((x + 2, 64 * scale + pad + 2), name, fill=(220, 220, 220, 255))
    return im


def check() -> int:
    """Which regions does each shipped overlay actually paint?"""
    regions = {f"{p}.{f}": box for p, faces in PARTS.items()
               for f, box in faces.items()}
    regions["hat_rim"] = HAT_RIM
    used = set()
    for name, box in regions.items():
        x, y, w, h = box
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                used.add((xx, yy))
    for path in sorted(OUT.glob("*.png")):
        im = Image.open(path).convert("RGBA")
        px = im.load()
        opaque = {(x, y) for y in range(64) for x in range(64) if px[x, y][3] > 0}
        stray = opaque - used
        print(f"  {path.name:30s} painted={len(opaque):4d}  "
              f"outside any region={len(stray):4d}"
              f"{'   <-- invisible pixels' if stray else ''}")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true",
                    help="report which UV regions the shipped overlays paint")
    ap.add_argument("--mirror", action="store_true",
                    help="flip the garment: the strap swaps shoulders")
    args = ap.parse_args()
    if args.check:
        return check()

    OUT.mkdir(parents=True, exist_ok=True)
    SHEET.mkdir(parents=True, exist_ok=True)
    # Base skins first: they are what makes one citizen a different person from the next,
    # and the garments only say what that person does for a living.
    base = Image.open(OUT / SKIN).convert("RGBA")
    skins = []
    for i, (vname, fg, push, hg, sg) in enumerate(SKIN_VARIANTS):
        tex = skin_variant(base, fg, push, hg, sg)
        tex.save(OUT / f"citizen_skin_{i}.png")
        skins.append((vname, tex))
        print(f"  wrote citizen_skin_{i}.png  ({vname})")
    front_view(skins, base).save(SHEET / "skin_variants.png")

    src = Image.open(OUT / SOURCE).convert("RGBA")
    if args.mirror:
        src = mirror_garment(src)
        print("  garment mirrored: the strap swaps shoulders")
    made = []
    for name, (shadow, mid, trim) in SET:
        tex = recolour(src, shadow, mid, trim)
        tex.save(OUT / f"{name}.png")
        made.append((name, tex))
        # The mask must be the source's, exactly. If it ever is not, the variant has
        # grown or lost cloth somewhere and the shape is no longer the hand-drawn one.
        a = [src.load()[x, y][3] > 0 for y in range(64) for x in range(64)]
        b = [tex.load()[x, y][3] > 0 for y in range(64) for x in range(64)]
        print(f"  wrote {name}.png   mask identical to {SOURCE}: {a == b}")
    guide().save(SHEET / "uv_guide.png")
    sheet(made).save(SHEET / "clothes_sheet.png")
    front_view(made + [(SOURCE.replace(".png", ""), src)], src_skin()).save(
        SHEET / "front_view.png")
    bad = faces_clear(made)
    print()
    print(f"{len(made)} overlays -> {OUT}")
    print(f"guide, net sheet and FRONT VIEW -> {SHEET}")
    print("LOOK AT front_view.png. The net sheet cannot tell you whether a garment reads.")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
