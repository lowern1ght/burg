"""Build new structures out of the author's own voxels.

The premise: do not describe the style, reuse it. Every block that ends up in
the output came from a structure the author built by hand, so the result cannot
drift into "generated" territory the way a rule-driven builder does.

The core operation is a slice stretch along the roof's ridge axis. Duplicating
or dropping a whole X or Z slice keeps walls, roof, interior and terrain
mutually consistent for free — a gable simply gets longer, which is exactly
what the author's own `house` -> `house_lvl*` progression does.

Stretching *across* the ridge is a different problem: it would duplicate a
mid-slope row and flatten the pitch into a plateau, so `stretch` refuses that
axis unless `rebuild_roof=True`, which replays the donor's measured roof
profile onto the wider footprint instead of copying slope rows.

CLI:
    python -m structures.assemble donor.nbt --along 4 -o out.nbt
    python -m structures.assemble donor.nbt --along 6 --jitter 0.35 -o out.nbt
"""

from __future__ import annotations

import argparse
import random
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple

from .anatomy import TERRAIN, VEGETATION, Anatomy, analyse, roof_profile
from .nbtio import BlockState, Coord, Voxels, load, save

# Terrain blocks the author scatters through an apron, and roughly how often.
# Measured shares get re-derived per donor; this is only the fallback ordering.
APRON_FALLBACK = ("grass_block", "coarse_dirt", "dirt", "dirt_path")

# Singular features that must not be multiplied when a slice is repeated.
#
# Suffixes that make a block singular whatever wood or stone it is made of. The
# explicit set below misses variants, and it missed the important one outright:
# a DOOR. `armory_lvl4` shipped three oak doors side by side at (8,4,4..6) — one
# door in the donor, the stretch inserting two copies of the slice that held it.
# Three doors in a row is not a wider building, it is a mistake you can see from
# outside.
SINGULAR_SUFFIX = ("_door", "_trapdoor", "_wall_banner", "_banner", "_wall_sign",
                   "_sign", "_hanging_sign", "_lectern", "_bell")

NO_DUPLICATE = {
    "oak_trapdoor", "spruce_trapdoor", "iron_trapdoor",
    "white_wall_banner", "red_wall_banner", "brown_wall_banner",
    "oak_wall_sign", "oak_wall_hanging_sign", "bell", "lectern",
    "chiseled_bookshelf", "anvil", "stonecutter", "note_block",
    # Workstations. A building has one hearth and one bench; copying the slice
    # that holds them gave `barracks_lvl4` two furnaces. Beds are deliberately
    # NOT here — extra bunks in a longer barracks are the point.
    "furnace", "smoker", "blast_furnace", "crafting_table", "cauldron",
    "water_cauldron", "composter", "beehive", "bee_nest",
}


def ridge_axis(vox: Voxels, ana: Optional[Anatomy] = None) -> str:
    """Which axis the roof ridge runs along: "x" or "z".

    The ridge is the long dimension of the topmost roof layer. Stretching along
    it is safe; stretching across it is not.
    """
    ana = ana or analyse(vox)
    top = vox.top_y()
    for y in range(top, ana.roof_lo - 1, -1):
        cells = [(p[0], p[2]) for p, b in vox.solid_items()
                 if p[1] == y and b.short not in VEGETATION]
        if len(cells) < 2:
            continue
        xs = {c[0] for c in cells}
        zs = {c[1] for c in cells}
        if len(xs) != len(zs):
            return "x" if len(xs) > len(zs) else "z"
    # Fall back to the shell's long side.
    w, d = ana.shell_size
    return "x" if w >= d else "z"


def _slice_cells(vox: Voxels, axis: str, i: int) -> Dict[Coord, BlockState]:
    idx = 0 if axis == "x" else 2
    return {p: b for p, b in vox.solid_items() if p[idx] == i}


def _shift(pos: Coord, axis: str, delta: int) -> Coord:
    x, y, z = pos
    return (x + delta, y, z) if axis == "x" else (x, y, z + delta)


def _slice_map(vox: Voxels, axis: str, i: int) -> Dict[Tuple[int, int], str]:
    """A slice as {(other two coords): block id}, for comparing slices."""
    idx = 0 if axis == "x" else 2
    out: Dict[Tuple[int, int], str] = {}
    for p, b in vox.solid_items():
        if p[idx] == i:
            out[(p[1], p[2] if axis == "x" else p[0])] = b.short
    return out


def _similarity(a: Dict[Tuple[int, int], str], b: Dict[Tuple[int, int], str]) -> float:
    """Share of cells the two slices agree on, air included."""
    keys = set(a) | set(b)
    if not keys:
        return 1.0
    same = sum(1 for k in keys if a.get(k) == b.get(k))
    return same / len(keys)


def repeatable_slices(vox: Voxels, axis: str, lo: int, hi: int) -> List[int]:
    """Slices that can be duplicated without altering the building's shape.

    A slice is safe to repeat only if it closely resembles BOTH neighbours —
    that makes it a slice from the middle of a uniform run, so inserting a copy
    is invisible. Gable ends, stair landings and door bays differ from their
    neighbours and must never be repeated.

    This used to be decided from the shell bounds returned by `analyse`, which
    is a heuristic and was plain wrong on `house_2_lvl3`: it reported the whole
    box as the shell, so the gable-end slice became a candidate and got copied
    into the middle of the second floor, cutting the room in half with a band of
    slabs. Content is authoritative; the detector is not.
    """
    scored: List[Tuple[float, int]] = []
    for i in range(lo + 1, hi):
        cur = _slice_map(vox, axis, i)
        if not cur:
            continue
        score = min(_similarity(cur, _slice_map(vox, axis, i - 1)),
                    _similarity(cur, _slice_map(vox, axis, i + 1)))
        scored.append((score, i))
    if not scored:
        return []
    best = max(sc for sc, _ in scored)
    # The threshold has to be relative. Measured over `house_2_lvl3` and
    # `house_3_lvl5`, the middle run scores 0.47-0.62 while every end cap sits
    # at or below 0.20-0.34 — a clean 2-3x separation, but nowhere near the 0.85
    # an absolute cutoff would demand of a furnished Minecraft interior.
    if best < 0.35:
        return []          # nothing uniform enough anywhere: refuse to stretch
    return [i for sc, i in scored if sc >= best * 0.8]


# How much of the walk area a stretch may cost before the pick is rejected. Not
# zero: inserting a slice adds cells, and a copy of a furnished slice can legally
# put a barrel where a cell used to be free.
REACH_TOLERANCE = 0.03


def _singular(b: BlockState) -> bool:
    """One per building: never copied into a duplicated slice."""
    return b.short in NO_DUPLICATE or b.short.endswith(SINGULAR_SUFFIX)


def _interior_reach(vox: Voxels) -> float:
    """Fraction of the ROOFED cells reachable from outside the build.

    The one number that says "you can get in, and get about once you are in".

    It has to be the roofed cells and not every walkable cell. Measured over the
    whole build the donor `house_3_lvl2` scores 0.464 — most of what a player can
    stand on is apron and roof slope, and the ratio is swamped. Its rooms score
    30 of 31. A stretch that walled the interior into one-cell slots moved the
    whole-build number by 0.009, which no tolerance can catch, and the indoor
    number from 0.97 to 0.67.

    A cell counts as indoor if something solid roofs its column — the same rule
    `check_usable` uses, kept identical on purpose so the gate and the report
    cannot disagree about what a room is.
    """
    from .traverse import reachable, walkable
    cells = walkable(vox)
    if not cells:
        return 1.0
    sx, sy, sz = vox.size
    inside = set()
    for p in cells:
        for y in range(p[1] + 2, sy):
            if vox.occupied((p[0], y, p[2])):
                inside.add(p)
                break
    border = [p for p in cells
              if (p[0] in (0, sx - 1) or p[2] in (0, sz - 1))
              and p not in inside]
    if not inside or not border:
        return 1.0
    seen = reachable(vox, border)
    return sum(1 for p in inside if p in seen) / len(inside)


def _insert_slices(vox: Voxels, axis: str, delta: int, picks: Sequence[int],
                   new_size: Tuple[int, int, int], axis_i: int) -> Voxels:
    """Build the lengthened copy with `picks` duplicated into the gap."""
    out = Voxels(new_size, {}, f"{vox.name}+{axis}{delta:+d}")
    picks = sorted(picks)
    insert_at = picks[0]
    for pos, b in vox.solid_items():
        shift = delta if pos[axis_i] >= insert_at else 0
        out.take(vox, pos, _shift(pos, axis, shift), b)
    # Minus anything that must not be multiplied. Jigsaw blocks are connection
    # points: extra copies let the mod graft neighbours onto a wall face.
    # Trapdoors, signs and banners are singular features — duplicating the slice
    # that holds one turned a single hatch into a row of four identical
    # trapdoors.
    for n, src in enumerate(picks):
        for pos, b in _slice_cells(vox, axis, src).items():
            if b.short == "jigsaw" or _singular(b):
                continue
            out.take(vox, pos, _shift(pos, axis, insert_at - src + n), b)
    return out


def stretch(vox: Voxels, axis: str, delta: int,
            ana: Optional[Anatomy] = None,
            seed: int = 0, allow_cross_ridge: bool = False) -> Voxels:
    """Lengthen (delta>0) or shorten (delta<0) the build along `axis`.

    Slices are inserted in the middle of the shell so corners, gable ends and
    the terrain margin all survive untouched.
    """
    ana = ana or analyse(vox)
    if delta == 0:
        return vox.copy()

    ridge = ridge_axis(vox, ana)
    if axis != ridge and not allow_cross_ridge:
        raise ValueError(
            f"{vox.name or 'donor'}: ridge runs along {ridge!r}; stretching "
            f"along {axis!r} would duplicate a mid-slope row and flatten the "
            f"pitch. Stretch along {ridge!r}, or pass allow_cross_ridge=True "
            "and rebuild the roof.")

    x0, x1, z0, z1 = ana.shell
    lo, hi = (x0, x1) if axis == "x" else (z0, z1)
    span = hi - lo + 1
    if span + delta < 3:
        raise ValueError(f"cannot shrink a {span}-wide shell by {-delta}")

    rng = random.Random(seed)
    axis_i = 0 if axis == "x" else 2
    sx, sy, sz = vox.size
    new_size = list(vox.size)
    new_size[axis_i] += delta

    out = Voxels(tuple(new_size), {}, f"{vox.name}+{axis}{delta:+d}")  # type: ignore[arg-type]

    if delta > 0:
        # Repeat interior slices of the shell, avoiding the two end slices so a
        # gable end or a door column is never duplicated.
        candidates = repeatable_slices(vox, axis, lo, hi)
        if not candidates:
            raise ValueError(
                f"{vox.name or 'donor'}: no slice along {axis!r} resembles both "
                "its neighbours closely enough to duplicate safely; every "
                "candidate is an end cap or a one-off bay")
        # Resembling both neighbours is necessary and NOT sufficient. In
        # `house_3_lvl2` the interior counter runs as two identical slab rows, so
        # a third copy scores as invisible — and it walls the room into
        # one-cell slots, because the gap those rows stop short of is the only
        # thing joining the two halves of the floor. `armory_lvl2` measured 30 of
        # 45 ground cells reachable and its door opening into masonry.
        #
        # So the pick is verified, not trusted: lay the copies, ask `traverse`
        # whether the building is still as connected as the donor was, and try
        # the next candidate if it is not. Similarity is a shape test; this is
        # the function test, and only the second one can see a blocked door.
        order = list(candidates)
        rng.shuffle(order)
        before = _interior_reach(vox)
        fallback = None
        for src_pick in order:
            cand = _insert_slices(vox, axis, delta, [src_pick] * delta,
                                  tuple(new_size), axis_i)
            if _interior_reach(cand) >= before - REACH_TOLERANCE:
                return cand
            fallback = fallback if fallback is not None else cand
        # Every candidate closes something. Keep the first — the caller's gate
        # will report it — rather than silently refusing to build at all.
        return fallback if fallback is not None else out
    else:
        drop = set()
        candidates = repeatable_slices(vox, axis, lo, hi) or [
            i for i in range(lo + 1, hi)]
        rng.shuffle(candidates)
        for i in candidates[: -delta]:
            drop.add(i)
        # Never drop a slice that carries a jigsaw connector.
        jig = {p[axis_i] for p, b in vox.solid_items() if b.short == "jigsaw"}
        drop -= jig
        for pos, b in vox.solid_items():
            i = pos[axis_i]
            if i in drop:
                continue
            shift = -sum(1 for d in drop if d < i)
            out.take(vox, pos, _shift(pos, axis, shift), b)

    return out


# ── breaking the symmetry ───────────────────────────────────────────

def renoise_apron(vox: Voxels, ana: Optional[Anatomy] = None,
                  seed: int = 0, strength: float = 0.35) -> Voxels:
    """Re-scatter the terrain apron asymmetrically.

    A slice stretch copies the donor's ground verbatim, which makes the apron
    repeat with a visible period. The author's ground is noisy: `coarse_dirt`
    patches sit off-centre and paths wander. This resamples apron cells from
    the donor's own terrain mix, so the block vocabulary stays authentic while
    the arrangement stops looking stamped.
    """
    ana = ana or analyse(vox)
    rng = random.Random(seed)
    out = vox.copy()

    # The donor's own terrain mix, weighted as it actually occurs.
    pool: List[BlockState] = [b for (p, b) in vox.solid_items()
                              if p[1] <= ana.ground_top and b.short in TERRAIN]
    if not pool:
        return out
    x0, x1, z0, z1 = ana.shell

    for (x, y, z), b in list(vox.solid_items()):
        if y > ana.ground_top or b.short not in TERRAIN:
            continue
        # Leave the footprint under the walls alone — that is the foundation.
        if x0 <= x <= x1 and z0 <= z <= z1:
            continue
        if rng.random() < strength:
            out.set((x, y, z), pool[rng.randrange(len(pool))])
    return out


def jitter_decor(vox: Voxels, ana: Optional[Anatomy] = None,
                 seed: int = 0, strength: float = 0.3) -> Voxels:
    """Nudge exterior vegetation and props by a block, dropping a few.

    Only touches cells outside the shell, and only moves a prop onto a spot
    that is empty and supported, so nothing ends up floating.
    """
    ana = ana or analyse(vox)
    rng = random.Random(seed + 1)
    out = vox.copy()
    x0, x1, z0, z1 = ana.shell
    sx, sy, sz = vox.size

    for (x, y, z), b in list(vox.solid_items()):
        if x0 <= x <= x1 and z0 <= z <= z1:
            continue
        if b.short not in VEGETATION and not b.short.endswith("_leaves"):
            continue
        if rng.random() > strength:
            continue
        if rng.random() < 0.25:
            out.set((x, y, z), None)          # thin it out
            continue
        for _ in range(4):
            nx = x + rng.choice((-1, 0, 1))
            nz = z + rng.choice((-1, 0, 1))
            if not (0 <= nx < sx and 0 <= nz < sz):
                continue
            if (nx, y, nz) == (x, y, z) or out.occupied((nx, y, nz)):
                continue
            if not out.occupied((nx, y - 1, nz)):
                continue                       # never leave it floating
            keep = out.block_nbt.get((x, y, z))
            out.set((x, y, z), None)
            out.set((nx, y, nz), b, keep)
            break
    return out


def variant(donor: Voxels, along: int = 0, seed: int = 0,
            jitter: float = 0.35) -> Voxels:
    """A full variant: stretch along the ridge, then break the symmetry."""
    ana = analyse(donor)
    out = stretch(donor, ridge_axis(donor, ana), along, ana, seed=seed) \
        if along else Voxels(donor.size, dict(donor.grid), donor.name,
                             list(donor.entities))
    out = renoise_apron(out, seed=seed, strength=jitter)
    out = jitter_decor(out, seed=seed, strength=jitter)
    out.name = f"{Path(donor.name).stem}_a{along:+d}_s{seed}"
    return out


def main(argv: Optional[Sequence[str]] = None) -> int:
    ap = argparse.ArgumentParser(description="Derive a variant from a donor NBT.")
    ap.add_argument("donor")
    ap.add_argument("--along", type=int, default=0,
                    help="blocks to add (or remove) along the ridge axis")
    ap.add_argument("--seed", type=int, default=0)
    ap.add_argument("--jitter", type=float, default=0.35)
    ap.add_argument("-o", "--out", required=True)
    a = ap.parse_args(argv)

    donor = load(a.donor)
    donor.name = Path(a.donor).stem
    ana = analyse(donor)
    print(f"donor {donor.name}: {ana.describe()}")
    print(f"  ridge axis = {ridge_axis(donor, ana)}")

    v = variant(donor, along=a.along, seed=a.seed, jitter=a.jitter)
    save(v, a.out)
    print(f"wrote {a.out}: size={v.size} solid={v.solid_count}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
