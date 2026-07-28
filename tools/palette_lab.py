"""Palette experiments as NBT you can open in a viewer.

Deciding a material scheme by description does not work — the difference between
"three materials, one of them 84% of the wall" and "six materials with roles" is
obvious in the world and invisible in prose. So this writes the same wall
geometry painted many different ways and leaves the choosing to the person who
has to look at it.

    python palette_lab.py

Writes into `tools/structures/out/palette/nbt/`, deliberately outside the mod's
resource tree so these samples never end up in the built jar. Open them in an
NBT viewer, or drop them into a world's `generated/minecraft/structures/` folder
and load them with a structure block.

**Rough stone only.** No bricks, no polished, no chiselled, no tiles. A village
fortification is built out of what its people dug up, and dressed masonry reads
as a palace — that was the verdict on the first batch, where `stone_bricks` was
the dominant block in every sample. Everything here is a stone you can mine and
place with no crafting step beyond a furnace, which also makes the progression
honest: the ladder is about digging deeper, not about learning stonecutting.

Two decisions are deliberately kept apart, because they are independent:

  `sampler_palettes`  one scheme, five different rough palettes — WHICH stones
  `sampler_schemes`   one palette, five different schemes — HOW to lay them out

Every sample repaints only masonry. Geometry, stairs, slabs, wall connections,
timber, ground and vegetation are untouched, so what you compare is exactly and
only the stone.
"""

from __future__ import annotations

import random
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable, Dict, List, Optional, Sequence, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))

from structures import render_png, wall                      # noqa: E402
from structures.corpus import modernize                      # noqa: E402
from structures.nbtio import Coord, Voxels, save, state      # noqa: E402

# Outside the mod resources on purpose. Anything under
# `common/src/main/resources/data/` is packaged into the jar, so a folder of
# throwaway palette samples would ship to every user of the mod.
OUT = Path("structures/out/palette/nbt")
SHEETS = Path("structures/out/palette")


# ── rough stone families ────────────────────────────────────────────
#
# A family is one stone plus whichever cut variants vanilla actually provides.
# Repainting has to be family-aware and it has to know what is missing: there is
# no `stone_wall` and no `deepslate_stairs`, so a naive string substitution
# produces ids that do not exist and a structure that will not place.

FULL = ("", "_stairs", "_slab", "_wall")
NO_WALL = ("", "_stairs", "_slab")
PLAIN = ("",)

FAMILY: Dict[str, Dict[str, str]] = {}


def _family(key: str, base: str, cut: str, shapes: Sequence[str]) -> None:
    FAMILY[key] = {s: (base if s == "" else f"{cut}{s}") for s in shapes}


_family("cobble", "cobblestone", "cobblestone", FULL)
_family("mossy", "mossy_cobblestone", "mossy_cobblestone", FULL)
_family("andesite", "andesite", "andesite", FULL)
_family("tuff", "tuff", "tuff", FULL)
_family("granite", "granite", "granite", FULL)
_family("diorite", "diorite", "diorite", FULL)
_family("cobbled_deep", "cobbled_deepslate", "cobbled_deepslate", FULL)
_family("blackstone", "blackstone", "blackstone", FULL)
_family("brick", "stone_bricks", "stone_brick", FULL)
_family("polished_andesite", "polished_andesite", "polished_andesite", NO_WALL)
_family("stone", "stone", "stone", NO_WALL)         # there is no stone_wall
_family("deepslate", "deepslate", "deepslate", PLAIN)   # no cut variants at all
_family("basalt", "basalt", "basalt", PLAIN)
_family("gravel", "gravel", "gravel", PLAIN)
_family("dripstone", "dripstone_block", "dripstone_block", PLAIN)

# id -> (family key, shape)
LOOKUP: Dict[str, Tuple[str, str]] = {}
for _k, _m in FAMILY.items():
    for _shape, _id in _m.items():
        LOOKUP.setdefault(_id, (_k, _shape))

# Blocks the old dressed palette produced, so an already-painted wall can be
# repainted back into rough stone rather than being left half brick.
for _dressed, _shape in (
        ("stone_bricks", ""), ("stone_brick_stairs", "_stairs"),
        ("stone_brick_slab", "_slab"), ("stone_brick_wall", "_wall"),
        ("mossy_stone_bricks", ""), ("mossy_stone_brick_slab", "_slab"),
        ("mossy_stone_brick_stairs", "_stairs"),
        ("cracked_stone_bricks", ""), ("chiseled_stone_bricks", ""),
        ("deepslate_bricks", ""), ("deepslate_brick_stairs", "_stairs"),
        ("deepslate_brick_slab", "_slab"), ("deepslate_brick_wall", "_wall"),
        ("polished_deepslate", ""), ("polished_andesite", ""),
        ("polished_andesite_slab", "_slab"), ("polished_tuff", ""),
        ("tuff_bricks", ""), ("deepslate_tiles", ""), ("smooth_stone", "")):
    LOOKUP.setdefault(_dressed, ("__dressed__", _shape))


# Blocks that obey gravity. They have no business in a wall face: the first
# time the chunk loads, any of them with air underneath drops out and leaves a
# hole. Six of them did exactly that in the first rough-stone batch, at the
# arrow loops, where the course below is deliberately empty.
FALLING = {"gravel", "sand", "red_sand", "suspicious_gravel", "anvil"}


def repaint_id(want: str, shape: str, ramp: "Ramp", fallback: str) -> str:
    """The id for `want` in `shape`, falling back along rough stone only.

    The fallback chain matters more than it looks. `stone` has no wall variant
    and `deepslate` has no cut variants at all, so a wanted tone often cannot
    supply the shape a cell needs. Returning the ORIGINAL block in that case
    quietly kept dressed masonry in the wall — the inner railing stayed
    `stone_brick_wall` in every sample that was supposed to have no brick in it.
    So it falls back to the palette's own main stone, and then to cobblestone,
    which has every cut there is.
    """
    for key in (want, "cobble"):
        fam = FAMILY.get(key)
        if fam is None or shape not in fam:
            continue
        if fam[shape] in FALLING:
            continue
        return fam[shape]
    return fallback


# ── roles ───────────────────────────────────────────────────────────
#
# Inferred from position. Deliberately crude — enough to SEE the schemes. Once
# one is chosen it gets reimplemented inside `wall.py`, where the real pier and
# bay indices are known instead of guessed from z.

def role_of(p: Coord) -> str:
    x, y, z = p
    if y >= wall.WALK:
        return "head"
    # A pier is a thickening of the wall, so its stone runs through — but only
    # the OUTER face and the projecting cell get the gradient tone. The inner
    # face of a pier reads as base. Giving the gradient stone the whole pier
    # through the full thickness put it at a third of the visible wall, which is
    # the gradient becoming the wall's identity instead of decorating it.
    if z % 4 == 0:
        if x <= wall.A_OUT:
            return "pier"
        if x == wall.A_MID:
            return "core"
        return "pier_in"
    if x == wall.A_MID:
        return "core"
    if x == wall.A_IN:
        return "inner"
    if x < wall.A_OUT:
        return "proud"
    return "field"


BANDS = (3,)          # the string course: one row, faces only


# ── ramps ───────────────────────────────────────────────────────────
#
# A gradient is an ORDERED chain of blocks whose textures blend into their
# neighbours, and at any one height only the TWO adjacent steps are mixed. The
# references state the chains outright as labelled strips (`61287e8a`,
# `e738d707`) and show them built (`c57b33be`, `9d7fa607`).
#
# This is what the earlier attempts got wrong. Picking a dominant per column out
# of the whole palette put mossy cobblestone next to andesite — two steps apart on
# the ramp — and that is exactly the harsh pairing a ramp exists to prevent. It
# read as grey mush.

@dataclass(frozen=True)
class Ramp:
    """An ordered chain of family keys, damp end first."""

    key: str
    note: str
    steps: Tuple[Tuple[str, ...], ...]
    structural: Tuple[str, ...] = ()    # strong stone: plinth and piers only

    def at(self, t: float, rng: random.Random) -> str:
        """Dither between the two steps straddling ramp position `t`.

        Two things stop this coming out too clean, which was the verdict on the
        first version where whole courses measured 100% one block:

        * a **step is a group of visually similar stones**, not one block, so even
          the middle of a band is mixed;
        * `t` is **jittered** per cell, so a band never resolves to a single stone
          however far it is from a transition.

        That noise is the point of a Minecraft gradient. A clean band reads as a
        stripe of paint.
        """
        top = len(self.steps) - 1
        t = max(0.0, min(float(top), t + rng.uniform(-0.42, 0.42)))
        i = int(t)
        group = self.steps[top] if i >= top else (
            self.steps[i + 1] if rng.random() < (t - i) else self.steps[i])
        return group[rng.randrange(len(group))]


# Three stones per level, plus moss as the damp end — moss is weathering and does
# not consume a stone slot. Water soaks up from the ground, so the damp end is the
# BOTTOM and the ramp climbs to the cleanest stone at the head.
# Each step is a GROUP of stones that look alike, so no course is ever one block.
RAMPS: Tuple[Ramp, ...] = (
    Ramp("r_lvl2", "surface stone: mossy -> cobble -> stone",
         steps=(("mossy",), ("cobble", "mossy"), ("stone", "cobble"))),
    Ramp("r_lvl3", "quarried: mossy -> cobble -> stone -> andesite",
         steps=(("mossy",), ("cobble", "mossy"), ("stone", "cobble"),
                ("andesite", "stone", "tuff"))),
    # Deepslate is the strong stone and it stays OUT of the field: heavy stone
    # belongs low, and cobblestone to deepslate is a two-step jump on the ramp.
    # It goes where strength is seen — the plinth and the piers.
    # The top level is where the villagers gain stone-WORKING. Dressed blocks
    # arrive here and nowhere earlier, and they arrive as a minority accent
    # diluting the rough field, never as the field itself.
    Ramp("r_lvl4", "worked stone: brick and polished andesite as the accent",
         steps=(("mossy",), ("cobble", "mossy"),
                ("stone", "cobble", "brick"),
                ("stone", "brick", "polished_andesite")),
         # Heavy stone stays in the plinth and the piers, diluted with stones
         # close to it in tone. NOT basalt or blackstone: Nether stone has no
         # business in a village wall.
         structural=("cobbled_deep", "deepslate", "cobbled_deep", "stone")),
)

PLINTH_TOP = 2        # courses of plinth that take the structural stone


def ramp_position(y: int, ramp: Ramp) -> float:
    """Height to ramp position. Six courses over the ramp gives 3-block bands.

    `9d7fa607` shows transition zones of three to five blocks: shorter reads as a
    seam, longer stops reading as two distinct stones.
    """
    span = max(1, wall.BODY_TOP - 1)
    return (y - 1) / span * (len(ramp.steps) - 1)


def paint_ramp(src: Voxels, ramp: Ramp, seed: int = 0) -> Voxels:
    """Repaint the masonry as a vertical ramp dither."""
    out = src.copy()
    for p, b in list(src.solid_items()):
        hit = LOOKUP.get(b.short)
        if hit is None:
            continue                    # timber, ground, torches: left alone
        _old, shape = hit
        role = role_of(p)
        x, y, z = p
        rng = random.Random(hash((p, ramp.key, seed)) & 0xFFFFFFFF)

        if ramp.structural and (y <= PLINTH_TOP or role in ("pier", "pier_in")):
            want = ramp.structural[rng.randrange(len(ramp.structural))]
        elif role == "core":
            want = "cobble"             # never seen; keep the palette on the faces
        else:
            want = ramp.at(ramp_position(y, ramp), rng)

        new_id = repaint_id(want, shape, ramp, b.short)
        if new_id != b.short:
            out.set(p, state(new_id, **b.prop_dict), src.block_nbt.get(p))
    out.name = ramp.key
    return out


def ragged_head(vox: Voxels, ramp: Ramp, seed: int = 0) -> int:
    """Break the merlon line so no two neighbours share a height.

    `33a7f4e5` and `9485c249` both read on this and not on their material mix: a
    ragged skyline carries a plain stone, and no amount of stone variety rescues a
    level one. Merlons run one to three courses.

    The single-course cells stay single: that is the embrasure, and a player's
    eyes sit above it. Raising everything to look busy is what makes a parapet you
    cannot see over.
    """
    rng = random.Random(seed + 4093)
    sx, _sy, sz = vox.size
    changed = 0
    for z in range(sz):
        for x in range(sx):
            if not vox.occupied((x, wall.WALK, z)):
                continue
            b = vox.get((x, wall.WALK, z))
            if b.short not in LOOKUP:
                continue                # timber rail, torch: not ours to raise
            if not vox.occupied((x, wall.WALK + 1, z)):
                continue                # an embrasure: leave it low
            # A merlon. Give it two or three courses, chosen per cell.
            # Two in three get a third course. At one in three the skyline
            # barely moved — one raised merlon per segment is not a ragged
            # silhouette, and the silhouette is the thing that carries.
            extra = rng.choice((0, 1, 1))
            for dy in range(2, 2 + extra):
                p = (x, wall.WALK + dy, z)
                if not in_box(vox, p) or vox.occupied(p):
                    continue
                below = vox.get((x, wall.WALK + dy - 1, z))
                fam, shape = LOOKUP.get(below.short, ("cobble", ""))
                vox.set(p, state(repaint_id(fam, shape, ramp, below.short)))
                changed += 1
    return changed


def in_box(vox: Voxels, p: Coord) -> bool:
    sx, sy, sz = vox.size
    return 0 <= p[0] < sx and 0 <= p[1] < sy and 0 <= p[2] < sz


def strip_connectors(vox: Voxels) -> None:
    """A dev sample has no business carrying jigsaw markers."""
    for p, b in list(vox.solid_items()):
        if b.short == "jigsaw":
            vox.set(p, None)


def sampler(pieces: Sequence[Voxels], name: str) -> Voxels:
    """Lay the variants end to end along the run, so you walk past all of them."""
    depth = sum(p.size[2] for p in pieces)
    width = max(p.size[0] for p in pieces)
    height = max(p.size[1] for p in pieces)
    out = Voxels((width, height, depth), {}, name)
    z0 = 0
    for piece in pieces:
        for (x, y, z), b in piece.solid_items():
            out.set((x, y, z0 + z), b, piece.block_nbt.get((x, y, z)))
        z0 += piece.size[2]
    return out


def by_course(v: Voxels) -> str:
    """The ramp, course by course, on the visible faces. This is the check."""
    lines = []
    for y in range(1, wall.BODY_TOP + 1):
        c: Dict[str, int] = {}
        for x in (wall.A_OUT, wall.A_IN):
            for z in range(v.size[2]):
                b = v.get((x, y, z))
                if b is not None and b.short in LOOKUP:
                    c[b.short] = c.get(b.short, 0) + 1
        tot = sum(c.values()) or 1
        top = sorted(c.items(), key=lambda kv: -kv[1])
        lines.append("      y%d  %s" % (y, "  ".join(
            "%s %d%%" % (k, round(100 * n / tot)) for k, n in top)))
    return "\n".join(lines)


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    SHEETS.mkdir(parents=True, exist_ok=True)

    variants: List[Voxels] = []
    for ramp in RAMPS:
        base = wall.compose("wall_segment", 3, seed=0)
        strip_connectors(base)
        v = paint_ramp(base, ramp)
        raised = ragged_head(v, ramp)
        modernize(v)
        variants.append(v)
        save(v, OUT / f"{ramp.key}.nbt")
        stones = sorted({b.short for _p, b in v.solid_items()
                         if b.short in LOOKUP})
        print(f"\n  {ramp.key}: {ramp.note}")
        print(f"      stones: {', '.join(stones)}")
        print(f"      merlons raised: {raised}")
        print(by_course(v))

    combo = sampler(variants, "sampler_ramps")
    modernize(combo)
    save(combo, OUT / "sampler_ramps.nbt")
    render_png.sheet([(v, f"{r.key}  —  {r.note}")
                      for v, r in zip(variants, RAMPS)],
                     tile=15).save(SHEETS / "ramps.png")
    print(f"\nNBT -> {OUT.resolve()}")
    print("sampler_ramps.nbt  three levels in a row, %d long" % combo.size[2])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
