"""Compose new structures from block states harvested out of author builds.

`assemble.stretch` covers the case where a donor of the right *type* exists.
The military set has no donor at all — the corpus is plains houses, jobs,
gardens, streets and starters, with nothing tower-shaped or wall-shaped. So a
watchtower has to be composed rather than stretched.

The compromise is explicit: the *arrangement* here is ours, but every block
state — including stair facings, slab types, log axes and wall connection
flags — is lifted from a structure the author built. Harvesting per side and
replaying per side is what keeps roof stairs correctly oriented; guessing
`facing` from scratch is what produced upside-down roofs before.

The construction grammar follows `house_3_lvl5`, measured layer by layer:

    ground apron -> stone lower storey -> slab string course
                 -> timber upper storey -> stair-pitched roof or battlements

Everything is restricted to block ids that occur in the corpus. A military
build that reaches for `iron_bars` would be inventing vocabulary the author
never used, so arrow slits use `oak_fence` and `glass_pane` instead.
"""

from __future__ import annotations

import random
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Sequence, Tuple

import nbtlib
from nbtlib import Compound

from .anatomy import TERRAIN, VEGETATION, Anatomy, analyse
from .facade import FacadeStyle, articulate, capped_merlons, string_course
from .nbtio import BlockState, Coord, Voxels, state

SIDES = ("north", "south", "west", "east")

# Orientation for a jigsaw sitting on the given outward-facing edge, matching
# the author's own convention (west_up at x=0, north_up at z=0, and so on).
JIGSAW_ORIENTATION = {"north": "north_up", "south": "south_up",
                      "west": "west_up", "east": "east_up"}


def jigsaw(target: str, pool: str = "minecraft:empty",
           final_state: str = "minecraft:dirt_path") -> Compound:
    """Block-entity data for a jigsaw connector, in the author's format.

    `BuildSchematic.replaceJigsawBlocks` reads `final_state` to decide what the
    marker becomes after placement; without this compound the marker is left
    standing in the world.
    """
    return Compound({
        "joint": nbtlib.String("rollable"),
        "name": nbtlib.String(target),
        "pool": nbtlib.String(pool),
        "final_state": nbtlib.String(final_state),
        "id": nbtlib.String("minecraft:jigsaw"),
        "target": nbtlib.String(target),
    })


STREET_JIGSAW = ("onceuponatown:streets", "onceuponatown:plains/streets")


# ── vocabulary ──────────────────────────────────────────────────────

@dataclass
class Vocabulary:
    """Block states harvested from one donor, grouped by construction role."""

    donor: str = ""
    apron: List[BlockState] = field(default_factory=list)
    floor: List[BlockState] = field(default_factory=list)
    stone: List[BlockState] = field(default_factory=list)
    timber: List[BlockState] = field(default_factory=list)
    post: Optional[BlockState] = None
    slab_top: Optional[BlockState] = None
    slab_bottom: Optional[BlockState] = None
    stone_slab_top: Optional[BlockState] = None
    window: List[BlockState] = field(default_factory=list)
    fence: Optional[BlockState] = None
    crenel: Optional[BlockState] = None
    roof_stairs: Dict[str, BlockState] = field(default_factory=dict)
    roof_fill: Optional[BlockState] = None
    ridge: Optional[BlockState] = None
    light: Optional[BlockState] = None
    torch_wall: Optional[BlockState] = None
    door_lower: Optional[BlockState] = None
    door_upper: Optional[BlockState] = None
    ladder: Dict[str, BlockState] = field(default_factory=dict)

    def describe(self) -> str:
        return (f"vocab from {self.donor}: stone={len(self.stone)} "
                f"timber={len(self.timber)} window={len(self.window)} "
                f"roof_stairs={sorted(self.roof_stairs)} "
                f"post={self.post} crenel={self.crenel}")


def merge(primary: Vocabulary, *others: Vocabulary) -> Vocabulary:
    """Fill roles missing from `primary` using later vocabularies.

    Needed because no single donor covers everything. `house_3_lvl5` has the
    stone-base / timber-upper material grammar the military set wants but roofs
    in plain slabs, so it harvests no roof stairs at all; `house_lvl6` supplies
    those for all four sides. Combining author parts across donors is the whole
    premise, so long as no block state is invented.
    """
    out = Vocabulary(donor=" + ".join([primary.donor] + [o.donor for o in others]))
    lists = ("apron", "floor", "stone", "timber", "window")
    singles = ("post", "slab_top", "slab_bottom", "stone_slab_top", "fence",
               "crenel", "roof_fill", "ridge", "light", "torch_wall",
               "door_lower", "door_upper")
    for f in lists:
        merged: List[BlockState] = list(getattr(primary, f))
        for o in others:
            if not merged:
                merged = list(getattr(o, f))
        setattr(out, f, merged)
    for f in singles:
        val = getattr(primary, f)
        for o in others:
            if val is None:
                val = getattr(o, f)
        setattr(out, f, val)
    out.roof_stairs = dict(primary.roof_stairs)
    out.ladder = dict(primary.ladder)
    for o in others:
        for k, v in o.roof_stairs.items():
            out.roof_stairs.setdefault(k, v)
        for k, v in o.ladder.items():
            out.ladder.setdefault(k, v)
    return out


def _most_common(states: Sequence[BlockState]) -> Optional[BlockState]:
    if not states:
        return None
    counts: Dict[BlockState, int] = {}
    for s in states:
        counts[s] = counts.get(s, 0) + 1
    return max(counts, key=lambda k: counts[k])


def harvest(donor: Voxels, ana: Optional[Anatomy] = None,
            wall_hi: Optional[int] = None) -> Vocabulary:
    """Pull one block state per construction role out of a donor.

    Pass `wall_hi` when the zone detector is wrong about where the roof starts;
    `house_3_lvl5` for instance has a cobblestone-slab string course at y=3
    that reads as roofing material but is really the floor of the timber storey.
    """
    ana = ana or analyse(donor, wall_hi=wall_hi)
    v = Vocabulary(donor=donor.name)

    for (x, y, z), b in donor.solid_items():
        n = b.short
        if y <= ana.ground_top and n in TERRAIN:
            v.apron.append(b)
        if n.endswith("_wall"):
            v.crenel = v.crenel or b
        if n.endswith("_door"):
            if b.get("half") == "lower":
                v.door_lower = v.door_lower or b
            else:
                v.door_upper = v.door_upper or b
        if n == "ladder":
            v.ladder[b.get("facing", "north")] = b
        if n in ("lantern",):
            v.light = v.light or b
        if n == "wall_torch":
            v.torch_wall = v.torch_wall or b

    def zone(lo: int, hi: int) -> List[Tuple[Coord, BlockState]]:
        return [(p, b) for p, b in donor.solid_items() if lo <= p[1] <= hi]

    wall_cells = zone(ana.wall_lo, ana.wall_hi)
    v.stone = [b for _, b in wall_cells
               if ("cobblestone" in b.short or b.short in ("stone", "smooth_stone"))
               and not b.short.endswith(("_slab", "_stairs", "_wall"))]
    v.timber = [b for _, b in wall_cells if b.short.endswith("_planks")]
    v.floor = [b for p, b in wall_cells if p[1] == ana.wall_lo
               and not b.short.endswith(("_slab", "_stairs"))]
    v.window = [b for _, b in wall_cells
                if b.short.endswith(("_pane", "_bars")) or b.short == "glass"]
    v.post = _most_common([b for _, b in wall_cells
                           if b.short.endswith("_log") and b.get("axis") == "y"])
    v.fence = _most_common([b for _, b in wall_cells if b.short.endswith("_fence")])

    slabs = [b for _, b in donor.solid_items() if b.short.endswith("_slab")]
    v.slab_top = _most_common([b for b in slabs if b.get("type") == "top"
                               and not b.short.startswith("cobble")])
    v.slab_bottom = _most_common([b for b in slabs if b.get("type") == "bottom"
                                  and not b.short.startswith("cobble")])
    v.stone_slab_top = _most_common([b for b in slabs if b.get("type") == "top"
                                     and ("cobble" in b.short or "stone" in b.short)])

    # Roof stairs, harvested per side. A stair on the -X edge of a working
    # author roof already carries the facing that slopes the right way, so
    # replaying it on the -X edge of a new roof cannot come out inverted.
    x0, x1, z0, z1 = ana.shell
    for y in range(ana.roof_lo, ana.roof_hi + 1):
        for (x, yy, z), b in donor.solid_items():
            if yy != y or not b.short.endswith("_stairs"):
                continue
            if x <= x0 + 1:
                v.roof_stairs.setdefault("west", b)
            if x >= x1 - 1:
                v.roof_stairs.setdefault("east", b)
            if z <= z0 + 1:
                v.roof_stairs.setdefault("north", b)
            if z >= z1 - 1:
                v.roof_stairs.setdefault("south", b)
    roof_cells = zone(ana.roof_lo, ana.roof_hi)
    v.roof_fill = _most_common([b for _, b in roof_cells
                               if b.short.endswith("_planks")])
    v.ridge = _most_common([b for p, b in roof_cells
                            if p[1] == ana.roof_hi and b.short.endswith("_slab")])

    # Fallbacks, all ids that occur in the corpus.
    v.apron = v.apron or [state("grass_block", snowy="false"), state("coarse_dirt"),
                          state("dirt")]
    v.stone = v.stone or [state("cobblestone"), state("mossy_cobblestone")]
    v.timber = v.timber or [state("oak_planks")]
    v.floor = v.floor or [state("oak_planks")]
    v.window = v.window or [state("glass_pane", east="false", north="true",
                                  south="true", west="false", waterlogged="false")]
    v.post = v.post or state("oak_log", axis="y")
    v.fence = v.fence or state("oak_fence", east="false", north="true",
                               south="true", west="false", waterlogged="false")
    v.crenel = v.crenel or state("cobblestone_wall", up="true", north="false",
                                 south="false", west="false", east="false",
                                 waterlogged="false")
    v.slab_top = v.slab_top or state("oak_slab", type="top", waterlogged="false")
    v.slab_bottom = v.slab_bottom or state("oak_slab", type="bottom",
                                           waterlogged="false")
    v.stone_slab_top = v.stone_slab_top or state("cobblestone_slab", type="top",
                                                 waterlogged="false")
    v.roof_fill = v.roof_fill or state("oak_planks")
    v.ridge = v.ridge or v.slab_bottom
    v.light = v.light or state("lantern", hanging="false", waterlogged="false")
    v.door_lower = v.door_lower or state(
        "oak_door", half="lower", hinge="left", facing="south", open="false",
        powered="false")
    v.door_upper = v.door_upper or v.door_lower.with_props(half="upper")
    return v


# ── plans ───────────────────────────────────────────────────────────

@dataclass
class TowerPlan:
    """A watchtower: square shaft, optional stone base, battlements or roof."""

    shell: int = 3               # inner shaft size across X
    shell_z: int = 0             # across Z; 0 means "same as shell"
    storeys: int = 2             # timber/stone courses of 3 blocks each
    stone_courses: int = 0       # how many of them are stone
    battlements: bool = False    # crenellated top instead of a roof
    pitched_roof: bool = False   # stair-pitched cap
    margin: int = 2              # terrain apron around the shaft
    lantern: bool = True
    banner: bool = False
    front: str = "south"
    buttress: bool = True        # lean-to against one face, breaks symmetry
    beams: bool = True           # horizontal logs at storey breaks
    rail: bool = False           # fence railing round a flat lookout platform
    facade: bool = True          # apply pier/two-tone/arch/corbel articulation
    open_deck: bool = False      # roofed platform on posts, not a closed top
    external_stair: bool = False # stone stair wrapping the outside



def _ring(x0: int, x1: int, z0: int, z1: int) -> List[Tuple[int, int]]:
    out = []
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                out.append((x, z))
    return out


def _side_of(x: int, z: int, x0: int, x1: int, z0: int, z1: int) -> str:
    if z == z0:
        return "north"
    if z == z1:
        return "south"
    if x == x0:
        return "west"
    return "east"


def compose_tower(v: Vocabulary, plan: TowerPlan, seed: int = 0) -> Voxels:
    """Build a watchtower from harvested block states."""
    rng = random.Random(seed)
    inner_x = plan.shell
    inner_z = plan.shell_z or plan.shell
    m = plan.margin
    span_x = inner_x + 2 * m
    span_z = inner_z + 2 * m
    wall_h = plan.storeys * 3
    roof_h = 3 if plan.pitched_roof else (6 if plan.open_deck else
                                         2 if plan.battlements else 1)
    height = 1 + 1 + wall_h + roof_h + 1

    vox = Voxels((span_x, height, span_z), {}, "tower")
    x0, z0 = m, m
    x1, z1 = m + inner_x - 1, m + inner_z - 1
    span = max(span_x, span_z)   # kept for the fitting bounds checks below

    # --- ground apron, deliberately uneven ---
    for x in range(span_x):
        for z in range(span_z):
            pick = v.apron[rng.randrange(len(v.apron))]
            vox.set((x, 0, z), pick)
    # A short path stub off the front edge only, never a full-length runway.
    path = state("dirt_path")
    fx = (x0 + x1) // 2 + rng.choice((-1, 0, 0, 1))
    for z in range(z1 + 1, span_z):
        if rng.random() < 0.8:
            vox.set((fx, 0, z), path)

    # --- foundation footprint ---
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            vox.set((x, 1, z), v.stone[rng.randrange(len(v.stone))])

    # --- shaft ---
    ring = _ring(x0, x1, z0, z1)
    corners = {(x0, z0), (x0, z1), (x1, z0), (x1, z1)}
    stone_top = 1 + plan.stone_courses * 3
    for i in range(wall_h):
        y = 2 + i
        stone_course = y <= stone_top
        for (x, z) in ring:
            if (x, z) in corners:
                # Quoins follow the course they sit in. Running an oak post up
                # through the stone storeys left four timber columns in every
                # course and pulled the whole shaft brown even where nine of
                # fifteen courses were cobble.
                vox.set((x, y, z),
                        v.stone[rng.randrange(len(v.stone))] if stone_course
                        else v.post)
                continue
            if stone_course:
                blk = v.stone[rng.randrange(len(v.stone))]
            else:
                blk = v.timber[rng.randrange(len(v.timber))]
            vox.set((x, y, z), blk)
        # An arrow slit per storey, on one randomly chosen face — asymmetric on
        # purpose, since exact symmetry is the loudest generated-build tell.
        if i % 3 == 1:
            for side in rng.sample(SIDES, k=rng.choice((1, 2))):
                cells = [(x, z) for (x, z) in ring
                         if (x, z) not in corners
                         and _side_of(x, z, x0, x1, z0, z1) == side]
                if not cells:
                    continue
                cx, cz = cells[len(cells) // 2]
                slit = (v.window[rng.randrange(len(v.window))]
                        if v.window and rng.random() < 0.5 else v.fence)
                vox.set((cx, y, cz), slit)
        # A storey break: a horizontal log beam ring across the wall face.
        #
        # This used to replace the whole wall course with `slab type=top`,
        # which occupies only the upper half of its cell — leaving a
        # see-through gap running right around the building, so the tower read
        # as cut in half. The author never does that: in `house.nbt` the slab
        # string course sits on the ring OUTSIDE the wall (x=1 and x=7 with the
        # wall at x=2..6) and is flush under the roof above it, while the wall
        # itself stays solid. Beams are laid in the wall plane; the projecting
        # slab band is added separately below.
        if i and i % 3 == 2 and i != wall_h - 1:
            for (x, z) in ring:
                if (x, z) in corners:
                    continue
                if stone_course:
                    # Masonry gets a slab band, not a log ring — a full course
                    # of beams inside the stone storeys is what made a "stone
                    # keep" read as a timber one.
                    continue
                side = _side_of(x, z, x0, x1, z0, z1)
                axis = "x" if side in ("north", "south") else "z"
                vox.set((x, y, z), state(v.post.short, axis=axis))
            # No projecting band here. The facade pass owns the string course
            # and places exactly one, at the stone/timber transition. Emitting
            # a band at every third course as well stacked four ledges up the
            # shaft and turned the tower into a wedding cake.

    # --- door on the front face, at ground level ---
    front_cells = [(x, z) for (x, z) in ring
                   if (x, z) not in corners
                   and _side_of(x, z, x0, x1, z0, z1) == plan.front]
    if front_cells:
        dx, dz = front_cells[len(front_cells) // 2]
        vox.set((dx, 2, dz), v.door_lower.with_props(facing=plan.front))
        vox.set((dx, 3, dz), v.door_upper.with_props(facing=plan.front))

    # --- interior: floor plates and a ladder up the back wall ---
    lad_side = {"south": "north", "north": "south",
                "west": "east", "east": "west"}[plan.front]
    lx, lz = (x0 + x1) // 2, (z0 + z1) // 2
    if min(inner_x, inner_z) >= 3:
        if lad_side == "north":
            lz = z0 + 1
        elif lad_side == "south":
            lz = z1 - 1
        elif lad_side == "west":
            lx = x0 + 1
        else:
            lx = x1 - 1
    # A ladder attaches to the block BEHIND it, so its `facing` is the opposite
    # of the wall it is fixed to. Setting facing to the wall side put every rung
    # in mid-air: the support was looked for on the open interior side.
    OPPOSITE = {"north": "south", "south": "north",
                "west": "east", "east": "west"}
    climb = OPPOSITE[lad_side]
    lad = (v.ladder.get(climb) or state("ladder", facing=climb,
                                        waterlogged="false"))
    lad = lad.with_props(facing=climb)
    # From the floor, not from the first course. Starting at y=2 left the bottom
    # rung out of reach: you cannot board a ladder whose lowest rung is above
    # your head, so every level of the tower measured as having no way up.
    for y in range(1, 2 + wall_h):
        vox.set((lx, y, lz), lad)
    # A trapdoor-height platform at each storey break, leaving the ladder clear.
    for s in range(1, plan.storeys):
        y = 1 + s * 3
        for x in range(x0 + 1, x1):
            for z in range(z0 + 1, z1):
                if (x, z) == (lx, lz):
                    continue
                vox.set((x, y, z), v.floor[rng.randrange(len(v.floor))])

    top_y = 2 + wall_h

    # --- crown ---
    if plan.pitched_roof:
        # Stairs stepping inward one block per layer, capped with a slab ridge —
        # the pattern measured in house_lvl6 y=5..8. 82% of the author's builds
        # this tall roof this way.
        for k in range(roof_h):
            y = top_y + k
            rx0, rx1 = x0 - 1 + k, x1 + 1 - k
            rz0, rz1 = z0 - 1 + k, z1 + 1 - k
            if rx0 > rx1 or rz0 > rz1:
                break
            for (x, z) in _ring(rx0, rx1, rz0, rz1):
                side = _side_of(x, z, rx0, rx1, rz0, rz1)
                blk = v.roof_stairs.get(side)
                vox.set((x, y, z), blk if blk else v.slab_top)
            for x in range(rx0 + 1, rx1):
                for z in range(rz0 + 1, rz1):
                    vox.set((x, y, z), v.roof_fill)
        vox.set(((x0 + x1) // 2, top_y + roof_h, (z0 + z1) // 2), v.ridge)
    elif plan.open_deck:
        # An observation deck: floor, corner posts, a rail and a roof carried on
        # the posts. Open on all sides, which is the point of a lookout and what
        # the reference tower does instead of a closed top storey.
        over = 1
        walk = v.slab_bottom.with_props(type="bottom")
        for x in range(x0 - over, x1 + over + 1):
            for z in range(z0 - over, z1 + over + 1):
                if 0 <= x < span_x and 0 <= z < span_z:
                    vox.set((x, top_y, z), walk)
        deck = _ring(x0 - over, x1 + over, z0 - over, z1 + over)
        deck_corners = {(x0 - over, z0 - over), (x0 - over, z1 + over),
                        (x1 + over, z0 - over), (x1 + over, z1 + over)}
        for (x, z) in deck:
            if not (0 <= x < span_x and 0 <= z < span_z):
                continue
            if (x, z) in deck_corners:
                for dy in (1, 2, 3):
                    vox.set((x, top_y + dy, z), v.post)
            elif rng.random() < 0.8:
                vox.set((x, top_y + 1, z), v.fence)
        for k in (0, 1):
            ry = top_y + 4 + k
            rx0, rx1 = x0 - over + k, x1 + over - k
            rz0, rz1 = z0 - over + k, z1 + over - k
            if rx0 > rx1 or rz0 > rz1:
                break
            for (x, z) in _ring(rx0, rx1, rz0, rz1):
                if not (0 <= x < span_x and 0 <= z < span_z):
                    continue
                sd = _side_of(x, z, rx0, rx1, rz0, rz1)
                blk = v.roof_stairs.get(sd)
                vox.set((x, ry, z), blk if blk else v.slab_top)
            for x in range(rx0 + 1, rx1):
                for z in range(rz0 + 1, rz1):
                    if 0 <= x < span_x and 0 <= z < span_z:
                        vox.set((x, ry, z), v.roof_fill)
    else:
        # Battlement platform: a solid walkway, then merlons with gaps.
        # Overhang the shaft only when it is wide enough to carry it — a
        # 1-block machicolation on a 3-wide shaft reads as a mushroom cap.
        over = 1 if min(inner_x, inner_z) >= 5 else 0
        walk = (v.stone_slab_top or v.slab_bottom) if plan.stone_courses else v.slab_bottom
        walk = walk.with_props(type="bottom")
        for x in range(x0 - over, x1 + over + 1):
            for z in range(z0 - over, z1 + over + 1):
                vox.set((x, top_y, z), walk)
        if plan.battlements:
            # Merlons on roughly every other cell, but chosen by rng rather than
            # a strict i%2 alternation: exact alternation is mirror-symmetric on
            # every axis, and symmetry is what makes a build read as generated.
            # Corners always carry one so the outline still reads as battlements.
            crown = _ring(x0 - over, x1 + over, z0 - over, z1 + over)
            crown_corners = {(x0 - over, z0 - over), (x0 - over, z1 + over),
                             (x1 + over, z0 - over), (x1 + over, z1 + over)}
            for (x, z) in crown:
                if (x, z) in crown_corners or rng.random() < 0.55:
                    vox.set((x, top_y + 1, z), v.crenel)
        elif plan.rail:
            # A wooden lookout gets a fence rail instead of stone merlons; the
            # author uses fence for openings and slab-top for edging, so a rail
            # of fence posts round a platform stays inside his vocabulary.
            for (x, z) in _ring(x0 - over, x1 + over, z0 - over, z1 + over):
                if rng.random() < 0.85:
                    vox.set((x, top_y + 1, z), v.fence)

    # --- external stair, wrapping the shaft ---
    # The reference garrison tower is reached from outside: the flight climbs
    # around the base instead of a ladder threading the interior. It also breaks
    # the silhouette, which a plain shaft badly needs.
    if plan.external_stair:
        ring_out = _ring(x0 - 1, x1 + 1, z0 - 1, z1 + 1)
        start = 0
        for i, (px, pz) in enumerate(ring_out):
            if _side_of(px, pz, x0 - 1, x1 + 1, z0 - 1, z1 + 1) == plan.front:
                start = i
                break
        # From the ground up, one step per cell, and it must not skip: a flight
        # with a hole in it is a flight nobody climbs. The first step sits at
        # y=1 so it can be stepped onto from the apron — starting at y=2 put the
        # bottom of the run a full block above the ground, which is a jump.
        step_y = 1
        for k in range(2 * len(ring_out)):
            px, pz = ring_out[(start + k) % len(ring_out)]
            if step_y > top_y:
                break
            if not (0 <= px < span_x and 0 <= pz < span_z):
                continue
            facing = _side_of(px, pz, x0 - 1, x1 + 1, z0 - 1, z1 + 1)
            vox.set((px, step_y, pz), state(
                "cobblestone_stairs", facing=facing, half="bottom",
                shape="straight", waterlogged="false"))
            for fill_y in range(1, step_y):
                if not vox.occupied((px, fill_y, pz)):
                    vox.set((px, fill_y, pz),
                            v.stone[rng.randrange(len(v.stone))])
            # Two cells of headroom over each tread, or the flight is walled in
            # by whatever the shaft above it happens to be.
            for clear_y in (step_y + 1, step_y + 2):
                q = (px, clear_y, pz)
                cur = vox.get(q)
                if cur is not None and not cur.short.endswith("_stairs") \
                        and clear_y > step_y:
                    vox.set(q, None)
            step_y += 1

    # --- a lean-to against one face: the strongest asymmetry available ---
    if plan.buttress and m >= 2:
        side = rng.choice(SIDES)
        if side == "north":
            cells = [(x, z0 - 1) for x in range(x0, x1 + 1)]
        elif side == "south":
            cells = [(x, z1 + 1) for x in range(x0, x1 + 1)]
        elif side == "west":
            cells = [(x0 - 1, z) for z in range(z0, z1 + 1)]
        else:
            cells = [(x1 + 1, z) for z in range(z0, z1 + 1)]
        for (x, z) in cells:
            if not (0 <= x < span_x and 0 <= z < span_z):
                continue
            vox.set((x, 1, z), v.stone[rng.randrange(len(v.stone))])
            vox.set((x, 2, z), v.timber[rng.randrange(len(v.timber))])
            vox.set((x, 3, z), v.slab_top)
        # A barrel or two of stores under the lean-to.
        if cells:
            bx, bz = cells[rng.randrange(len(cells))]
            vox.set((bx, 2, bz), state("barrel", facing="up", open="false"))

    # --- fittings ---
    if plan.lantern:
        # Hung from the underside of the crown, not floating mid-shaft.
        vox.set(((x0 + x1) // 2, top_y - 1, (z0 + z1) // 2),
                v.light.with_props(hanging="true"))
    # A wall torch on one outer face, placed off-centre.
    if v.torch_wall is not None:
        side = rng.choice(SIDES)
        cells = [(x, z) for (x, z) in ring
                 if (x, z) not in corners
                 and _side_of(x, z, x0, x1, z0, z1) == side]
        if cells:
            tx, tz = cells[rng.randrange(len(cells))]
            off = {"north": (0, -1), "south": (0, 1),
                   "west": (-1, 0), "east": (1, 0)}[side]
            px, pz = tx + off[0], tz + off[1]
            if 0 <= px < span_x and 0 <= pz < span_z and not vox.occupied((px, 4, pz)):
                vox.set((px, 4, pz), v.torch_wall.with_props(facing=side))
    if plan.banner:
        # Hung on the OUTSIDE face, not in place of a wall block. Writing it
        # into the ring cell replaced the masonry and left the banner with
        # nothing behind it to hang on.
        outward = {"north": (0, -1), "south": (0, 1),
                   "west": (-1, 0), "east": (1, 0)}
        for side in rng.sample(SIDES, k=1):
            cells = [(x, z) for (x, z) in ring
                     if (x, z) not in corners
                     and _side_of(x, z, x0, x1, z0, z1) == side]
            dx, dz = outward[side]
            for (bx, bz) in cells:
                px, pz = bx + dx, bz + dz
                y = top_y - 2
                if not (0 <= px < span_x and 0 <= pz < span_z):
                    continue
                if vox.occupied((px, y, pz)) or not vox.occupied((bx, y, bz)):
                    continue
                vox.set((px, y, pz), state("red_wall_banner", facing=side))
                break

    # Articulation, applied per material band so the stone storeys get stone
    # piers and the timber storeys oak posts. This is what turns a flat shaft
    # into a wall with rhythm; without it no choice of block reads as masonry.
    if plan.facade:
        shell = (x0, x1, z0, z1)
        wall_top = 2 + wall_h - 1
        stone_hi = min(stone_top, wall_top)
        if plan.stone_courses and stone_hi >= 2:
            articulate(vox, shell, 2, stone_hi, FacadeStyle.tier1_stone(),
                       seed=seed, corbels=False)
            string_course(vox, shell, stone_hi + 1, FacadeStyle.tier1_stone())
        if wall_top > stone_hi:
            articulate(vox, shell, max(2, stone_hi + 1), wall_top,
                       FacadeStyle.tier1_timber(), seed=seed + 3,
                       arches=False, corbels=False)

    _add_connectors(vox, x0, x1, z0, z1, plan.front, "onceuponatown:military")
    _scatter_props(vox, x0, x1, z0, z1, rng)
    _scatter_vegetation(vox, x0, x1, z0, z1, rng)
    ensure_climbable(vox, lx, lz, lad, x0, x1, z0, z1)
    return vox


def ensure_climbable(vox: Voxels, lx: int, lz: int, lad: BlockState,
                     x0: int, x1: int, z0: int, z1: int) -> None:
    """Run the ladder from the floor to the top floor and keep its shaft open.

    A tower has to be climbable from the bottom to the top, and deriving the
    right ladder height from `storeys`, `roof_h` and whichever crown was chosen
    does not survive contact: the deck of an open-deck tower sits two courses
    above where `top_y` says the shaft ends, so the ladder stopped short and
    then got capped by the deck floor laid over it. Every level of the watchtower
    measured as having no way up.

    So this works from what was actually built rather than from the plan. It
    finds the highest floor inside the shaft, runs the ladder to it, clears
    anything else out of that column, and makes sure there is somewhere to step
    off at the top.
    """
    sx, sy, sz = vox.size
    if not (0 <= lx < sx and 0 <= lz < sz):
        return

    # A ladder hangs on the block behind it, so it can only run as high as the
    # wall it is fixed to. Extending it past that put rungs in the roof void
    # with nothing behind them — four unsupported blocks per tower, which the
    # style gate caught immediately and was right to.
    behind = {"north": (0, 1), "south": (0, -1),
              "west": (1, 0), "east": (-1, 0)}[lad.get("facing", "north")]
    bx, bz = lx + behind[0], lz + behind[1]

    def supported(y: int) -> bool:
        return vox.occupied((bx, y, bz))

    # The highest cell inside the shaft that something could stand on, capped by
    # how far the ladder's own wall reaches.
    top_floor = 0
    for y in range(1, sy - 1):
        if not supported(y):
            continue
        for x in range(x0 + 1, x1):
            for z in range(z0 + 1, z1):
                if (x, z) == (lx, lz):
                    continue
                if vox.occupied((x, y, z)) and not vox.occupied((x, y + 1, z)) \
                        and not vox.occupied((x, y + 2, z)):
                    top_floor = max(top_floor, y)
    if top_floor <= 0:
        return

    # Ladder from the floor to one cell above the top floor, so you arrive level
    # with it rather than below it, and nothing stray left in the column.
    for y in range(1, top_floor + 2):
        if supported(y):
            vox.set((lx, y, lz), lad)
    for y in range(1, sy):
        cur = vox.get((lx, y, lz))
        if cur is not None and cur.short == "ladder" and not supported(y):
            vox.set((lx, y, lz), None)

    # Somewhere to step off. The cell beside the ladder at the top floor must be
    # standable: solid under it, two cells clear above.
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        ax, az = lx + dx, lz + dz
        if not (x0 < ax < x1 and z0 < az < z1):
            continue
        if not vox.occupied((ax, top_floor, az)):
            continue
        for y in (top_floor + 1, top_floor + 2):
            cur = vox.get((ax, y, az))
            if cur is not None and cur.short != "ladder":
                vox.set((ax, y, az), None)
        break


@dataclass
class YardPlan:
    """A drill ground: a walled compound, not a fenced patch of dirt.

    Reference `40fe` labels the parts explicitly — a crenellated curtain wall
    round a bare drill floor, lean-to canopies along the inside of the walls as
    covered shooting positions, target butts against one wall, and a gate. The
    first version was a fence round some posts and read as a vegetable plot.
    """

    width: int = 11
    depth: int = 9
    pells: int = 2            # upright posts to strike
    canopy_sides: int = 1     # how many inner walls get a lean-to
    butts: bool = True        # straw target butts against one wall
    wall_h: int = 3
    battlements: bool = True
    margin: int = 1
    front: str = "south"


def compose_yard(v: Vocabulary, plan: YardPlan, seed: int = 0) -> Voxels:
    """Build a walled training compound."""
    rng = random.Random(seed)
    m = plan.margin
    sx = plan.width + 2 * m
    sz = plan.depth + 2 * m
    height = 1 + plan.wall_h + (2 if plan.battlements else 1) + 3
    vox = Voxels((sx, height, sz), {}, "yard")
    x0, z0 = m, m
    x1, z1 = m + plan.width - 1, m + plan.depth - 1

    # Bare, trodden drill floor inside; grass only outside the wall. A clearly
    # bounded floor material is what makes the enclosure read as a yard.
    for x in range(sx):
        for z in range(sz):
            inside = x0 <= x <= x1 and z0 <= z <= z1
            vox.set((x, 0, z), state(TRODDEN[rng.randrange(len(TRODDEN))])
                    if inside else v.apron[rng.randrange(len(v.apron))])

    # Curtain wall with a gate gap on the front.
    gate_x = (x0 + x1) // 2 + rng.choice((-1, 1))
    corners = {(x0, z0), (x0, z1), (x1, z0), (x1, z1)}
    for (x, z) in _ring(x0, x1, z0, z1):
        if z == z1 and abs(x - gate_x) <= 1 and plan.front == "south":
            continue
        for c in range(plan.wall_h):
            y = 1 + c
            if (x, z) in corners:
                vox.set((x, y, z), v.stone[rng.randrange(len(v.stone))])
            else:
                vox.set((x, y, z), v.stone[rng.randrange(len(v.stone))])

    # Gate jambs and a timber lintel over the opening.
    for gx in (gate_x - 2, gate_x + 2):
        if x0 < gx < x1:
            for c in range(plan.wall_h):
                vox.set((gx, 1 + c, z1), v.post)
    for gx in range(gate_x - 1, gate_x + 2):
        if x0 <= gx <= x1:
            vox.set((gx, 1 + plan.wall_h, z1), state(v.post.short, axis="x"))

    # Lean-to canopies along the inside of the walls: a plank roof on posts,
    # sloping in. These are the covered shooting positions of the reference.
    canopy_choices = [s for s in ("north", "west", "east")][: max(0, plan.canopy_sides)]
    roof_y = 1 + plan.wall_h
    for side in canopy_choices:
        if side == "north":
            cells = [(x, z0 + 1) for x in range(x0 + 1, x1)]
            inner = [(x, z0 + 2) for x in range(x0 + 1, x1)]
        elif side == "west":
            cells = [(x0 + 1, z) for z in range(z0 + 1, z1)]
            inner = [(x0 + 2, z) for z in range(z0 + 1, z1)]
        else:
            cells = [(x1 - 1, z) for z in range(z0 + 1, z1)]
            inner = [(x1 - 2, z) for z in range(z0 + 1, z1)]
        # Both rows sit at one level. An earlier version dropped the outer row
        # a block to fake a slope, but with posts only every third bay most of
        # that row had nothing under it and nothing beside it — six floating
        # blocks against an author maximum of two.
        for (cx, cz) in cells:
            vox.set((cx, roof_y, cz), v.roof_fill)
        for i, (ix, iz) in enumerate(inner):
            blk = v.roof_stairs.get(side)
            vox.set((ix, roof_y, iz), blk if blk else v.slab_top)
            if i % 3 == 1:
                for py in range(1, roof_y):
                    if not vox.occupied((ix, py, iz)):
                        vox.set((ix, py, iz), v.post)

    # Target butts: straw bales stacked against the far wall, under the canopy.
    if plan.butts:
        bz = z0 + 2
        for bx in range(x0 + 2, min(x0 + 6, x1)):
            vox.set((bx, 1, bz), state("hay_block", axis="x"))

    # Pells to strike, off any centre line.
    spots = [(x, z) for x in range(x0 + 3, x1 - 1) for z in range(z0 + 3, z1 - 1)]
    rng.shuffle(spots)
    for (px, pz) in spots[: plan.pells]:
        vox.set((px, 1, pz), state("oak_log", axis="y"))
        vox.set((px, 2, pz), state("stripped_oak_log", axis="y"))

    # A brazier for night drill.
    if len(spots) > plan.pells:
        bx, bz2 = spots[plan.pells]
        vox.set((bx, 1, bz2), state("campfire", facing="north", lit="true",
                                    signal_fire="false", waterlogged="false"))

    shell = (x0, x1, z0, z1)
    if plan.battlements:
        capped_merlons(vox, shell, 1 + plan.wall_h, FacadeStyle.tier1_stone(),
                       seed=seed)
    articulate(vox, shell, 1, plan.wall_h, FacadeStyle.tier1_stone(),
               seed=seed, arches=False, corbels=False)

    _add_connectors(vox, x0, x1, z0, z1, plan.front, "onceuponatown:military")
    return vox


@dataclass
class WallPlan:
    """A wall segment: a straight run, optionally with a gate opening."""

    length: int = 6
    height: int = 3           # courses above the foundation
    stone: bool = True
    battlements: bool = True
    gate: bool = False
    towers: bool = False      # thicken both ends into posts
    axis: str = "z"
    margin: int = 1
    facade: bool = True


def compose_wall(v: Vocabulary, plan: WallPlan, seed: int = 0) -> Voxels:
    """Build a wall or gatehouse segment from harvested block states."""
    rng = random.Random(seed)
    m = plan.margin
    length = plan.length
    thick = 3 if plan.towers else 1
    height = 1 + 1 + plan.height + (2 if plan.battlements else 1)
    # Asymmetric margin: more ground on the outer (defended) face than on the
    # walkway side. A single-thickness wall centred in its box maps onto itself
    # under an X mirror, so no amount of per-face weathering can break the
    # symmetry — the plot has to be off-centre.
    m_out, m_in = m + 1, m
    across = thick + m_out + m_in

    if plan.axis == "z":
        size = (across, height, length + 2 * m)
    else:
        size = (length + 2 * m, height, across)
    vox = Voxels(size, {}, "wall")
    sx, sy, sz = size

    def cell(i: int, across_i: int) -> Tuple[int, int]:
        """(x, z) for position `i` along the run and `across_i` across it."""
        return ((m_out + across_i, m + i) if plan.axis == "z"
                else (m + i, m_out + across_i))

    body = v.stone if plan.stone else v.timber

    for x in range(sx):
        for z in range(sz):
            vox.set((x, 0, z), v.apron[rng.randrange(len(v.apron))])

    # An opening dead on centre makes the whole run mirror-symmetric, so offset
    # it. Kept at least one cell in from either end so the jambs still read.
    gate_at = -99
    if plan.gate:
        gate_at = length // 2 + rng.choice((-1, 1)) if length >= 6 else length // 2
        gate_at = max(2, min(length - 3, gate_at))
    for i in range(length):
        for a in range(thick):
            x, z = cell(i, a)
            vox.set((x, 1, z), body[rng.randrange(len(body))])
            for c in range(plan.height):
                y = 2 + c
                is_gate = plan.gate and abs(i - gate_at) <= 1 and c < 2
                if is_gate:
                    continue
                end_post = plan.towers and i in (0, length - 1)
                if end_post and (a in (0, thick - 1)):
                    vox.set((x, y, z), v.post)
                else:
                    vox.set((x, y, z), body[rng.randrange(len(body))])
            # Cap course, then merlons at irregular spacing. Strict alternation
            # would make the run perfectly mirror-symmetric.
            cap_y = 2 + plan.height
            cap = (v.stone_slab_top if plan.stone else v.slab_top)
            vox.set((x, cap_y, z), cap.with_props(type="bottom"))
            if plan.battlements and (i in (0, length - 1) or rng.random() < 0.55):
                vox.set((x, cap_y + 1, z), v.crenel)

    # A torch on one end post and a tuft of grass at the foot, so a run of
    # segments does not read as extruded geometry.
    if v.torch_wall is not None and plan.towers:
        ex, ez = cell(rng.choice((0, length - 1)), thick // 2)
        side = "north" if plan.axis == "z" else "west"
        if not vox.occupied((ex, 2 + plan.height + 2, ez)):
            vox.set((ex, 2 + plan.height, ez), v.light)
    for _ in range(max(2, length // 3)):
        gx2, gz2 = rng.randrange(sx), rng.randrange(sz)
        if not vox.occupied((gx2, 1, gz2)) and vox.occupied((gx2, 0, gz2)):
            vox.set((gx2, 1, gz2), state("short_grass"))

    if plan.gate:
        gx, gz = cell(gate_at, thick // 2)
        vox.set((gx, 2, gz), v.door_lower.with_props(facing="south"))
        vox.set((gx, 3, gz), v.door_upper.with_props(facing="south"))
        # Lintel over the opening.
        for i in (gate_at - 1, gate_at, gate_at + 1):
            if 0 <= i < length:
                lx, lz = cell(i, thick // 2)
                vox.set((lx, 4, lz), v.post)

    # Articulate the two long faces. A wall is where the devices pay off most:
    # piers give it rhythm, the mossy two-tone gives depth, and the corbelled
    # head is the machicolation that makes a parapet read as defensive rather
    # than as a fence made of stone.
    if plan.facade:
        if plan.axis == "z":
            shell = (m_out, m_out + thick - 1, m, m + length - 1)
            faces = ("west", "east")
        else:
            shell = (m, m + length - 1, m_out, m_out + thick - 1)
            faces = ("north", "south")
        style = (FacadeStyle.tier1_stone() if plan.stone
                 else FacadeStyle.tier1_timber())
        style.pier_spacing = 3
        articulate(vox, shell, 1, 1 + plan.height, style, seed=seed,
                   arches=True, corbels=plan.battlements, sides=faces)

    # Connectors on both ends so segments chain, plus a street tie-in.
    if plan.axis == "z":
        ends = (("north", (m_out + thick // 2, 0, 0)),
                ("south", (m_out + thick // 2, 0, sz - 1)))
    else:
        ends = (("west", (0, 0, m_out + thick // 2)),
                ("east", (sx - 1, 0, m_out + thick // 2)))
    for side, pos in ends:
        vox.set(pos, state("jigsaw", orientation=JIGSAW_ORIENTATION[side]),
                jigsaw("onceuponatown:military"))
    return vox


# ── shared finishing ────────────────────────────────────────────────

def _add_connectors(vox: Voxels, x0: int, x1: int, z0: int, z1: int,
                    front: str, entry_target: str) -> None:
    """One entry connector on the front edge, street connectors on the others.

    Mirrors the author's layout: the entry jigsaw uses `minecraft:empty` as its
    pool and the building's entry_pool as its target; street jigsaws point at
    `onceuponatown:plains/streets`.
    """
    sx, sy, sz = vox.size
    edge = {"north": (( x0 + x1) // 2, 0, 0),
            "south": (( x0 + x1) // 2, 0, sz - 1),
            "west": (0, 0, (z0 + z1) // 2),
            "east": (sx - 1, 0, (z0 + z1) // 2)}
    vox.set(edge[front], state("jigsaw", orientation=JIGSAW_ORIENTATION[front]),
            jigsaw(entry_target))
    for side in SIDES:
        if side == front:
            continue
        vox.set(edge[side], state("jigsaw", orientation=JIGSAW_ORIENTATION[side]),
                jigsaw(STREET_JIGSAW[0], STREET_JIGSAW[1]))


# ── military dressing ───────────────────────────────────────────────

# Village dressing. A garrison is not a smallholding: flowers, pots, crops,
# beehives and hay bales are what made the first pass read as farm buildings.
VILLAGE_DRESSING = {
    "flower_pot", "decorated_pot", "hay_block", "beehive", "bee_nest",
    "honey_block", "honeycomb_block", "oak_sapling", "lily_pad", "wheat",
    "carrots", "potatoes", "composter", "moss_carpet", "dandelion", "poppy",
    "allium", "azure_bluet", "cornflower", "oxeye_daisy", "red_tulip",
    "pink_tulip", "white_tulip", "orange_tulip", "seagrass", "farmland",
    "white_carpet", "red_carpet", "yellow_carpet",
}
# Ponds belong to a farmstead, not a garrison. `house_3` carries a 17-block
# pool at ground level that the armory inherited; it is filled in rather than
# left as open water.
WET = {"water", "water_cauldron", "lily_pad", "seagrass"}
# Ground that reads as trodden rather than tended, weighted for scattering.
# `podzol` was rejected by the user. `packed_mud` was sanctioned, and is the
# first block used here that does NOT occur in the author's corpus.
TRODDEN = ("coarse_dirt", "coarse_dirt", "dirt_path", "dirt", "packed_mud")
BANNERS = ("red_wall_banner", "white_wall_banner", "brown_wall_banner")


def militarize(vox: Voxels, seed: int = 0, ana: Optional[Anatomy] = None,
               ground: float = 0.8, thin_green: float = 0.75,
               palisade: bool = False, banners: bool = False) -> Voxels:
    """Turn a village-looking build into a garrison one.

    Applied to every military structure, composed or stretched. It deliberately
    does not touch the walls or roof of a stretched donor — those are the
    author's and are what make the build look hand-made. What changes is the
    ground and the dressing, which is what actually carries the reading:

      * lawn becomes trodden earth
      * flowers, pots, crops, hives and hay bales are stripped out
      * banners, braziers, stores and a lit perimeter go in

    Every id used is one that occurs in the author's corpus.
    """
    rng = random.Random(seed + 977)
    ana = ana or analyse(vox)
    out = vox.copy()
    sx, sy, sz = vox.size
    x0, x1, z0, z1 = ana.shell

    # 1. Trodden ground, but only where boots actually fall — a worn apron
    #    hugging the walls, fading to grass at the plot edge. Converting the
    #    whole plot made everything one flat brown, the same value as the oak,
    #    and the building stopped separating from the ground.
    for (x, y, z), b in list(vox.solid_items()):
        if y > ana.ground_top or b.short != "grass_block":
            continue
        dist = max(0, x0 - x, x - x1, z0 - z, z - z1)
        chance = ground if dist <= 1 else ground * 0.45 if dist == 2 else 0.08
        if rng.random() < chance:
            out.set((x, y, z), state(TRODDEN[rng.randrange(len(TRODDEN))]))

    # 2. Fill in standing water, strip village dressing, thin the greenery.
    for (x, y, z), b in list(vox.solid_items()):
        n = b.short
        if n in WET:
            # Fill to grade with trodden earth so the pond reads as filled in,
            # not as a hole.
            out.set((x, y, z), state(TRODDEN[rng.randrange(len(TRODDEN))]))
        elif n.startswith("potted_") or n in VILLAGE_DRESSING:
            out.set((x, y, z), None)
        elif n in ("short_grass", "grass", "oak_leaves", "spruce_leaves",
                   "moss_block"):
            if rng.random() < thin_green:
                out.set((x, y, z), None)

    # 3. Banners on an outward wall face, at head height.
    faces: List[Tuple[Coord, str]] = []
    for (x, y, z), b in out.solid_items():
        if not (ana.wall_lo + 1 <= y <= ana.wall_hi):
            continue
        if b.short.endswith(("_planks",)) or "cobblestone" in b.short:
            for side, (dx, dz) in (("north", (0, -1)), ("south", (0, 1)),
                                   ("west", (-1, 0)), ("east", (1, 0))):
                nx, nz = x + dx, z + dz
                if 0 <= nx < sx and 0 <= nz < sz and not out.occupied((nx, y, nz)):
                    faces.append(((nx, y, nz), side))
    # Banners are a prestige fitting: level 4 and up only. A poor early building
    # should not be flying colours.
    if banners:
        rng.shuffle(faces)
        for (pos, side) in faces[: rng.choice((2, 3))]:
            out.set(pos, state(BANNERS[rng.randrange(len(BANNERS))], facing=side))

    # 4. A brazier: campfire on a cobble pedestal, out in the yard.
    open_ground = [(x, z) for x in range(sx) for z in range(sz)
                   if not out.occupied((x, ana.ground_top + 1, z))
                   and out.occupied((x, ana.ground_top, z))
                   and not (x0 - 1 <= x <= x1 + 1 and z0 - 1 <= z <= z1 + 1)]
    rng.shuffle(open_ground)
    gy = ana.ground_top
    if open_ground:
        # A campfire sits on the ground. It used to be raised on a cobblestone
        # pedestal as a "brazier"; that is not how anyone places one.
        bx, bz = open_ground.pop()
        out.set((bx, gy + 1, bz), state("campfire", facing="north", lit="true",
                                       signal_fire="false", waterlogged="false"))
    # 5. Stores, not produce.
    stores = [state("barrel", facing="up", open="false"),
              state("chest", facing="north", type="single"),
              state("anvil", facing="north")]
    for _ in range(rng.choice((1, 2))):
        if not open_ground:
            break
        sxp, szp = open_ground.pop()
        out.set((sxp, gy + 1, szp), stores[rng.randrange(len(stores))])

    # A palisade used to be added here: a line of upright logs at the plot
    # edge, placed with a 70% chance per cell and random height. It read as
    # scattered litter rather than a stockade — 30 lone stakes across the set —
    # so it is gone. A fence line is only worth adding as a deliberate,
    # continuous run with a gate, not as probabilistic scatter.
    return out


def _scatter_props(vox: Voxels, x0: int, x1: int, z0: int, z1: int,
                   rng: random.Random) -> None:
    """A small yard of stores clustered on one side of the build.

    This is the most effective asymmetry available to a square tower: the shaft
    itself is symmetric by necessity, so the ground around it has to carry the
    irregularity. Every id here occurs in the author's corpus.
    """
    sx, sy, sz = vox.size
    # Garrison stores only. Hay bales and decorated pots were what made the
    # first pass read as a farmstead.
    props = [state("barrel", facing="up", open="false"),
             state("barrel", facing="up", open="false"),
             state("chest", facing="north", type="single"),
             state("anvil", facing="north"),
             state("crafting_table")]
    # Pick one quadrant and keep the clutter inside it.
    qx = rng.choice((0, 1))
    qz = rng.choice((0, 1))
    xs = range(0, x0) if qx == 0 else range(x1 + 1, sx)
    zs = range(0, z0) if qz == 0 else range(z1 + 1, sz)
    spots = [(x, z) for x in xs for z in zs]
    rng.shuffle(spots)
    for (x, z) in spots[: rng.choice((1, 2, 2, 3))]:
        if vox.occupied((x, 1, z)) or not vox.occupied((x, 0, z)):
            continue
        vox.set((x, 1, z), props[rng.randrange(len(props))])
    # A patch of trodden ground on the same side.
    for (x, z) in spots[:4]:
        if rng.random() < 0.6 and vox.occupied((x, 0, z)):
            vox.set((x, 0, z), state("coarse_dirt"))


def _scatter_vegetation(vox: Voxels, x0: int, x1: int, z0: int, z1: int,
                        rng: random.Random) -> None:
    """A couple of bushes outside the footprint, plus bare tufts.

    Leaves are placed as small clumps, never as single blocks. A lone leaf
    cube floating over grass reads as a mistake — foliage has to sit on
    something and clump with its own kind. Measured before this change: 63 of
    78 leaf blocks in the set had no leaf neighbour.
    """
    sx, sy, sz = vox.size

    def free_ground(x: int, z: int) -> bool:
        return (0 <= x < sx and 0 <= z < sz
                and vox.occupied((x, 0, z)) and not vox.occupied((x, 1, z))
                and not (x0 - 1 <= x <= x1 + 1 and z0 - 1 <= z <= z1 + 1))

    leaf = state("oak_leaves", persistent="true", distance="1",
                 waterlogged="false")

    # One or two bushes: a centre cell plus two to three neighbours, so every
    # leaf has at least one leaf beside it.
    for _ in range(rng.choice((1, 2))):
        for _try in range(12):
            cx, cz = rng.randrange(sx), rng.randrange(sz)
            if not free_ground(cx, cz):
                continue
            vox.set((cx, 1, cz), leaf)
            around = [(cx + 1, cz), (cx - 1, cz), (cx, cz + 1), (cx, cz - 1)]
            rng.shuffle(around)
            grown = 0
            for (nx, nz) in around:
                if grown >= rng.choice((2, 3)):
                    break
                if free_ground(nx, nz):
                    vox.set((nx, 1, nz), leaf)
                    grown += 1
            # A taller crown on one cell so the clump is not a flat pancake.
            if rng.random() < 0.5 and not vox.occupied((cx, 2, cz)):
                vox.set((cx, 2, cz), leaf)
            break

    # Bare tufts, which do not need clumping.
    for _ in range(max(1, (sx * sz) // 26)):
        gx, gz = rng.randrange(sx), rng.randrange(sz)
        if free_ground(gx, gz):
            vox.set((gx, 1, gz), state("short_grass"))


def tidy_leaves(vox: Voxels) -> int:
    """Remove floating and lone leaves; the last word on foliage.

    Leaves reach a structure from several places — bush scatter, the donor's own
    planting, and `jitter_decor` nudging things about — so guaranteeing the rule
    needs one pass at the end rather than care at each site. A leaf survives
    only if something holds it up and another leaf sits beside it.

    Returns the number removed.
    """
    removed = 0
    for _pass in range(3):          # removing one leaf can orphan its neighbour
        doomed = []
        for p, b in vox.solid_items():
            if not b.short.endswith("_leaves"):
                continue
            x, y, z = p
            nbrs = ((x + 1, y, z), (x - 1, y, z), (x, y, z + 1),
                    (x, y, z - 1), (x, y + 1, z), (x, y - 1, z))
            supported = any(vox.occupied(q) for q in nbrs)
            clumped = any(vox.get(q) is not None
                          and vox.get(q).short.endswith("_leaves")
                          for q in nbrs)
            if not supported or not clumped:
                doomed.append(p)
        if not doomed:
            break
        for p in doomed:
            vox.set(p, None)
            removed += 1
    return removed

def cap_pillars(vox: Voxels, stairs: Optional[BlockState] = None) -> int:
    """Finish each upright log column the way `house_2_lvl6` does.

    The author terminates a post with `oak_stairs[half=bottom]` whose `facing`
    points toward the building centre — since `facing` names a stair's low side,
    the tall half sits outward and reads as a bracket flaring at the post head.
    His poorer levels instead leave `slab[type=bottom]`, which fills only the
    lower half of its cell and leaves a visible notch.

    That notch is deliberate. The user confirmed the empty spots in low-level
    houses are meant to look unfinished, which is why this is applied only to
    the richer levels — filling every column made all tiers look equally
    finished and flattened the progression. An earlier version filled the voids
    with full logs, which was wrong on both counts: wrong block, wrong tiers.

    Returns the number of columns capped.
    """
    from collections import defaultdict

    sx, sy, sz = vox.size
    cols = defaultdict(list)
    for (x, y, z), b in vox.solid_items():
        if b.short.endswith("_log") and b.get("axis") == "y":
            cols[(x, z)].append(y)
    if not cols:
        return 0

    solid = [p for p, _ in vox.solid_items()]
    cx = sum(p[0] for p in solid) / len(solid)
    cz = sum(p[2] for p in solid) / len(solid)

    base = stairs or state("oak_stairs", facing="north", half="bottom",
                           shape="straight", waterlogged="false")
    capped = 0
    for (x, z), ys in cols.items():
        y = max(ys) + 1
        if y >= sy:
            continue
        cur = vox.get((x, y, z))
        # Only replace the author's rough half-slab cap, never a real structure.
        if cur is not None and not (cur.short.endswith("_slab")
                                    and cur.get("type") in ("bottom", "top")):
            continue
        # Face the centre, so the tall half of the stair points outward.
        if abs(x - cx) >= abs(z - cz):
            facing = "east" if x < cx else "west"
        else:
            facing = "south" if z < cz else "north"
        vox.set((x, y, z), base.with_props(facing=facing, half="bottom"))
        capped += 1
    return capped

def military_fittings(vox: Voxels, seed: int = 0, ana: Optional[Anatomy] = None,
                      spears: bool = True, shields: bool = False,
                      armoury: bool = True) -> int:
    """Fit out a building so it reads as military without any heraldry.

    The user rejected banners as the signal. These three carry the meaning
    through function instead:

      spears   a run of fence posts standing against an inner wall — racked
               polearms
      shields  trapdoors mounted flat on an inner wall at head height. The
               author uses horizontal trapdoor runs himself (pen railing in
               `pig_farm_lvl6..8`), so this stays in vocabulary
      armoury  an anvil, a stonecutter as a grindstone, and an EMPTY cauldron as
               a quench trough, clustered by the forge

    Every id occurs in the author's corpus. Returns the number of blocks placed.
    """
    rng = random.Random(seed + 4231)
    ana = ana or analyse(vox)
    sx, sy, sz = vox.size
    floor = ana.wall_lo
    placed = 0

    def free(p: Coord) -> bool:
        return (0 <= p[0] < sx and 0 <= p[1] < sy and 0 <= p[2] < sz
                and not vox.occupied(p))

    def wall_side(x: int, y: int, z: int) -> Optional[str]:
        """The direction of an adjacent solid wall, if there is exactly one."""
        hits = []
        for side, (dx, dz) in (("north", (0, -1)), ("south", (0, 1)),
                               ("west", (-1, 0)), ("east", (1, 0))):
            nb = vox.get((x + dx, y, z + dz))
            if nb is not None and not nb.short.endswith(
                    ("_slab", "_stairs", "_fence", "_pane", "_door")):
                hits.append(side)
        return hits[0] if len(hits) == 1 else None

    # Interior floor cells that stand against a wall.
    spots = []
    for x in range(sx):
        for z in range(sz):
            p = (x, floor + 1, z)
            if not free(p) or not vox.occupied((x, floor, z)):
                continue
            side = wall_side(x, floor + 1, z)
            if side:
                spots.append(((x, z), side))
    rng.shuffle(spots)

    # 1. Spear rack: two or three fence posts in a row along one wall.
    if spears and spots:
        (ax, az), side = spots[0]
        run = (0, 1) if side in ("north", "south") else (1, 0)
        for k in range(rng.choice((2, 3))):
            px, pz = ax + run[0] * k, az + run[1] * k
            if not free((px, floor + 1, pz)) or not vox.occupied((px, floor, pz)):
                break
            for dy in (1, 2):
                if free((px, floor + dy, pz)):
                    vox.set((px, floor + dy, pz),
                            state("oak_fence", north="false", south="false",
                                  west="false", east="false",
                                  waterlogged="false"))
                    placed += 1

    # 2. Shields: trapdoors flat on a wall at head height, facing off the wall.
    if shields:
        hung = 0
        for (hx, hz), side in spots[1:]:
            if hung >= rng.choice((2, 3)):
                break
            y = floor + 2
            off = {"north": (0, -1), "south": (0, 1),
                   "west": (-1, 0), "east": (1, 0)}[side]
            wall = (hx + off[0], y, hz + off[1])
            if not free((hx, y, hz)) or not vox.occupied(wall):
                continue
            # `facing` must point at the wall's opposite so the support is the
            # wall itself.
            vox.set((hx, y, hz), state(
                "oak_trapdoor", facing=OPPOSITE_SIDE[side], half="top",
                open="false", powered="false", waterlogged="false"))
            placed += 1
            hung += 1

    # 3. Armourer's corner, next to the forge.
    if armoury:
        forge = [p for p, b in vox.solid_items()
                 if b.short in ("furnace", "smoker", "blast_furnace")]
        if forge:
            fx, fy, fz = forge[0]
            kit = [state("anvil", facing="north"),
                   state("stonecutter", facing="north"),
                   state("cauldron")]
            near = [(fx + dx, fy, fz + dz)
                    for dx in (-2, -1, 0, 1, 2) for dz in (-2, -1, 0, 1, 2)
                    if (dx or dz)]
            rng.shuffle(near)
            for item in kit:
                for p in near:
                    if free(p) and vox.occupied((p[0], p[1] - 1, p[2])):
                        vox.set(p, item)
                        placed += 1
                        near.remove(p)
                        break
    return placed


OPPOSITE_SIDE = {"north": "south", "south": "north",
                 "west": "east", "east": "west"}
