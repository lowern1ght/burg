"""Wall-articulation devices, applied to finished geometry.

The composed military buildings failed for one mechanical reason: their walls
were flat, uniform fields of a single block. The reference builds the user
supplied are the opposite — every wall carries rhythm and tonal depth. None of
that depends on expensive materials, which is why it all lands inside the
author's own vocabulary of oak, cobblestone and mossy cobblestone.

The devices, in the order they matter:

  piers        vertical pilasters projecting one block from the wall face at a
               regular interval. Turns a plane into a rhythm.
  two-tone     piers in the light stone, bay infill in the dark one. Depth read
               from value, not only from geometry.
  speckle      the mossy variant scattered through the field so no wall is a
               uniform colour.
  arches       the top of each bay closed with stairs springing off the piers.
  corbels      the wall head projecting outward on upside-down stairs, which is
               what gives a parapet its weight.
  string       a projecting slab course marking a floor level.
  merlons      crenellations capped with a slab rather than left as bare cubes.

Everything operates on an explicit rectangular shell and y-range, so the same
pass works on a composed tower, a wall segment, or a stretched author donor.
Piers project OUTWARD; the wall itself is never recessed, which would punch
holes through a single-thickness shell.
"""

from __future__ import annotations

import random
from dataclasses import dataclass
from typing import Dict, List, Optional, Sequence, Tuple

from .nbtio import BlockState, Coord, Voxels, state

Shell = Tuple[int, int, int, int]          # x0, x1, z0, z1

OUTWARD = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
OUTWARD_KEYS = ("north", "south", "west", "east")
Coord2 = Tuple[int, int]
OPPOSITE = {"north": "south", "south": "north", "west": "east", "east": "west"}

# Only plain masonry and timber get repainted. A door, window, arrow slit,
# ladder or banner is a decision already made by the builder, and overwriting
# it with pier stone would erase the openings the wall exists to have.
PLAIN_SUFFIX = ("_planks", "_log", "_bricks")
PLAIN_EXACT = {"cobblestone", "mossy_cobblestone", "stone", "smooth_stone",
               "stripped_oak_log", "stripped_spruce_log"}


def _is_plain(b: BlockState) -> bool:
    n = b.short
    if n in PLAIN_EXACT:
        return True
    return n.endswith(PLAIN_SUFFIX) and not n.endswith(("_slab", "_stairs"))


@dataclass
class FacadeStyle:
    """Which blocks play which role. All tier-1 by default."""

    pier: BlockState
    infill: BlockState
    speckle: BlockState
    stairs: BlockState
    slab_bottom: BlockState
    slab_top: BlockState
    crenel: BlockState
    pier_spacing: int = 3
    speckle_rate: float = 0.22
    streak_spread: float = 0.35   # how much the mossy ratio varies bay to bay

    @staticmethod
    def tier1_stone() -> "FacadeStyle":
        """Cobblestone piers, mossy field — the author's own two tones."""
        return FacadeStyle(
            pier=state("cobblestone"),
            infill=state("mossy_cobblestone"),
            speckle=state("cobblestone"),
            stairs=state("cobblestone_stairs", facing="north", half="bottom",
                         shape="straight", waterlogged="false"),
            slab_bottom=state("cobblestone_slab", type="bottom",
                              waterlogged="false"),
            slab_top=state("cobblestone_slab", type="top", waterlogged="false"),
            crenel=state("cobblestone_wall", up="true", north="none",
                         south="none", west="none", east="none",
                         waterlogged="false"),
        )

    @staticmethod
    def tier1_timber() -> "FacadeStyle":
        """Oak log piers over a plank field, for the poorest builds."""
        return FacadeStyle(
            pier=state("oak_log", axis="y"),
            infill=state("oak_planks"),
            speckle=state("stripped_oak_log", axis="y"),
            stairs=state("oak_stairs", facing="north", half="bottom",
                         shape="straight", waterlogged="false"),
            slab_bottom=state("oak_slab", type="bottom", waterlogged="false"),
            slab_top=state("oak_slab", type="top", waterlogged="false"),
            crenel=state("oak_fence", north="false", south="false",
                         west="false", east="false", waterlogged="false"),
        )


def _side_cells(shell: Shell, side: str) -> List[Coord2]:
    x0, x1, z0, z1 = shell
    if side == "north":
        return [(x, z0) for x in range(x0, x1 + 1)]
    if side == "south":
        return [(x, z1) for x in range(x0, x1 + 1)]
    if side == "west":
        return [(x0, z) for z in range(z0, z1 + 1)]
    return [(x1, z) for z in range(z0, z1 + 1)]


def pier_positions(shell: Shell, side: str, spacing: int) -> List[Coord2]:
    """Pier cells along one side: both ends, then every `spacing` between."""
    cells = _side_cells(shell, side)
    if len(cells) <= 2:
        return list(cells)
    out = [cells[0], cells[-1]]
    for i in range(spacing, len(cells) - 1, spacing):
        out.append(cells[i])
    return out


def articulate(vox: Voxels, shell: Shell, y_lo: int, y_hi: int,
               style: FacadeStyle, seed: int = 0,
               arches: bool = True, corbels: bool = True,
               two_tone: bool = True, ragged_base: bool = True,
               sides: Sequence[str] = OUTWARD_KEYS) -> None:
    """Articulate the wall band y_lo..y_hi of `shell`, in place.

    Only rewrites cells that already hold something, and only projects piers
    into cells that are empty — so this never carves into a roof or an interior.
    """
    sx, sy, sz = vox.size
    x0, x1, z0, z1 = shell

    def inside(p: Coord) -> bool:
        return 0 <= p[0] < sx and 0 <= p[1] < sy and 0 <= p[2] < sz

    for si, side in enumerate(sides):
        # A separate stream per face. Sharing one made the two faces of a
        # single-thickness wall identical, which is mirror-symmetric by
        # construction; independent weathering is also just truer.
        rng = random.Random(seed * 31 + si * 7919 + 5501)
        cells = _side_cells(shell, side)
        piers = set(pier_positions(shell, side, style.pier_spacing))
        dx, dz = OUTWARD[side]

        ends = {cells[0], cells[-1]}
        for (cx, cz) in cells:
            is_pier = (cx, cz) in piers
            # A corner is a quoin, not a buttress: it takes the pier material
            # but stays flush. Projecting at the ends too thickened every
            # corner on both of its sides and turned a narrow shaft into a
            # lumpy bundle of columns.
            projects = is_pier and (cx, cz) not in ends
            for y in range(y_lo, y_hi + 1):
                cur = vox.get((cx, y, cz))
                if cur is None or not _is_plain(cur):
                    continue
                if is_pier:
                    vox.set((cx, y, cz), style.pier)
                    if projects:
                        p = (cx + dx, y, cz + dz)
                        if inside(p) and not vox.occupied(p):
                            vox.set(p, style.pier)
                elif two_tone:
                    # Streaks, not static. Each bay column gets its own mossy
                    # ratio, so the tone varies vertically band by band the way
                    # weathered masonry does. A flat per-cell coin flip reads as
                    # uniform noise from any distance.
                    key = (cx * 31 + cz * 17) & 0xFFFF
                    ratio = _column_ratio(key, style, seed)
                    blk = (style.speckle if rng.random() < ratio
                           else style.infill)
                    vox.set((cx, y, cz), blk)

        if arches:
            _arch_bays(vox, shell, side, piers, cells, y_hi, style)
        if corbels:
            _corbel(vox, shell, side, cells, y_hi + 1, style, inside)
        if ragged_base:
            _ragged_base(vox, side, piers, cells, y_lo, style, rng, inside)


def _column_ratio(key: int, style: FacadeStyle, seed: int) -> float:
    """A stable per-column mossy ratio, spread around the base rate."""
    r = random.Random(key * 2654435761 + seed)
    lo = max(0.0, style.speckle_rate - style.streak_spread)
    hi = min(1.0, style.speckle_rate + style.streak_spread)
    return lo + (hi - lo) * r.random()


def _ragged_base(vox: Voxels, side: str, piers: set, cells: Sequence[Coord2],
                 y_lo: int, style: FacadeStyle, rng: random.Random,
                 inside) -> None:
    """Let the wall foot break into the ground instead of ending level.

    A wall meeting the terrain on one flat line is the clearest sign of a
    generated build. In the references the piers run down to different depths
    and loose stone gathers at the foot.
    """
    dx, dz = OUTWARD[side]
    for (cx, cz) in cells:
        depth = rng.choice((0, 1, 1, 2)) if (cx, cz) in piers else rng.choice((0, 0, 1))
        for d in range(1, depth + 1):
            p = (cx, y_lo - d, cz)
            if inside(p) and vox.occupied(p):
                vox.set(p, style.pier if (cx, cz) in piers else style.infill)
        # A little spill of loose stone at the foot, on one side only.
        if rng.random() < 0.22:
            q = (cx + dx, y_lo - 1, cz + dz)
            if inside(q) and vox.occupied(q):
                vox.set(q, style.infill)


def _bays(cells: Sequence[Coord2], piers: set) -> List[List[Coord2]]:
    """Runs of non-pier cells between consecutive piers."""
    out: List[List[Coord2]] = []
    run: List[Coord2] = []
    for c in cells:
        if c in piers:
            if run:
                out.append(run)
                run = []
        else:
            run.append(c)
    if run:
        out.append(run)
    return out


def _arch_bays(vox: Voxels, shell: Shell, side: str, piers: set,
               cells: Sequence[Coord2], y_top: int, style: FacadeStyle) -> None:
    """Close the head of each bay with stairs springing off its piers.

    The arch is highest in the middle of the bay, so the cells next to a pier
    carry the stone and step down toward the centre.
    """
    horizontal = "x" if side in ("north", "south") else "z"
    for bay in _bays(cells, piers):
        if len(bay) < 2:
            continue
        first, last = bay[0], bay[-1]
        for (cx, cz), toward in ((first, +1), (last, -1)):
            cur = vox.get((cx, y_top, cz))
            if cur is None or not _is_plain(cur):
                continue
            # `facing` names the low side of a stair, so it points into the bay.
            if horizontal == "x":
                facing = "east" if toward > 0 else "west"
            else:
                facing = "south" if toward > 0 else "north"
            vox.set((cx, y_top, cz),
                    style.stairs.with_props(facing=facing, half="top"))


def _corbel(vox: Voxels, shell: Shell, side: str, cells: Sequence[Coord2],
            y: int, style: FacadeStyle, inside) -> None:
    """Project the wall head outward on upside-down stairs.

    This is the machicolation of the reference wall: the parapet oversails the
    face, and the transition is carried on inverted stairs rather than just
    stopping. `half=top` puts the solid part up against the projecting course.
    """
    dx, dz = OUTWARD[side]
    facing = OPPOSITE[side]
    for (cx, cz) in cells:
        if not vox.occupied((cx, y - 1, cz)):
            continue
        p = (cx + dx, y, cz + dz)
        if inside(p) and not vox.occupied(p):
            vox.set(p, style.stairs.with_props(facing=facing, half="top"))


def string_course(vox: Voxels, shell: Shell, y: int, style: FacadeStyle,
                  project: bool = True) -> None:
    """A slab band at floor level, one block proud of the face.

    Placed OUTSIDE the wall line rather than replacing a wall course: a full
    course of top-slabs in the wall plane leaves a see-through gap all the way
    round, which is what made an earlier tower read as cut in half.
    """
    sx, sy, sz = vox.size
    x0, x1, z0, z1 = shell
    lo_x, hi_x = (x0 - 1, x1 + 1) if project else (x0, x1)
    lo_z, hi_z = (z0 - 1, z1 + 1) if project else (z0, z1)
    for x in range(lo_x, hi_x + 1):
        for z in range(lo_z, hi_z + 1):
            if not (x in (lo_x, hi_x) or z in (lo_z, hi_z)):
                continue
            if not (0 <= x < sx and 0 <= z < sz and 0 <= y < sy):
                continue
            if vox.occupied((x, y, z)):
                continue
            vox.set((x, y, z), style.slab_top)


def capped_merlons(vox: Voxels, shell: Shell, y: int, style: FacadeStyle,
                   seed: int = 0, rate: float = 0.55) -> None:
    """Crenellations with a slab cap, at irregular spacing."""
    rng = random.Random(seed + 88)
    sx, sy, sz = vox.size
    x0, x1, z0, z1 = shell
    corners = {(x0, z0), (x0, z1), (x1, z0), (x1, z1)}
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if not (x in (x0, x1) or z in (z0, z1)):
                continue
            if not (0 <= x < sx and 0 <= z < sz and y + 1 < sy):
                continue
            if not vox.occupied((x, y - 1, z)):
                continue
            if (x, z) not in corners and rng.random() > rate:
                continue
            vox.set((x, y, z), style.crenel)
            vox.set((x, y + 1, z), style.slab_bottom)
