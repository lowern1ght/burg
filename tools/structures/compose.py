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
Coord2 = Tuple[int, int]

# Blocks that are themselves an object: nothing gets stood on top of one.
NOT_A_PEDESTAL = ("chest", "barrel", "anvil", "stonecutter", "cauldron",
                  "furnace", "smoker", "blast_furnace", "crafting_table",
                  "composter", "lectern", "loom", "bed", "campfire", "beehive",
                  "bee_nest", "hay_block", "bell", "decorated_pot")

# Not a floor: anything you cannot stand on the top of, and anything attached.
NOT_A_FLOOR = ("_slab", "_stairs", "_wall", "_fence", "_fence_gate", "_gate",
               "_pane", "_bars", "_door", "_trapdoor", "_leaves", "_torch",
               "_sign", "_button", "_plate", "_pot", "_carpet", "_rail",
               "_banner", "_head", "_candle", "_sapling", "_bush", "_grass",
               "_flower", "_mushroom", "_crop", "_stem", "lantern", "ladder",
               "vine", "jigsaw", "chain", "_bed", "glass", "water", "lava",
               "snow", "campfire", "cauldron", "lever", "tripwire")

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
    # A harvested `*_wall`. Recorded, never used as a battlement.
    coping: Optional[BlockState] = None
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
               "crenel", "coping", "roof_fill", "ridge", "light", "torch_wall",
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
            # Harvested but NOT used as a battlement — see `v.crenel`. Kept only
            # so the id is recorded for anything that genuinely wants a low
            # garden coping.
            v.coping = v.coping or b
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
    # A floor has to be a FULL block. Taking the donor's lowest wall course and
    # only dropping slabs and stairs let everything else he stood at floor level
    # into the palette: the composed towers laid their storey floors out of
    # `oak_fence`, `cobblestone_wall` and `oak_leaves` at roughly one cell in
    # three. Standing on a fence is standing on nothing, so the ladder ran up
    # past four storeys with no landing beside it — `watchtower_lvl5` measured
    # 17 of 43 upper cells reachable and the cause read as a ladder bug for two
    # rounds of looking at the ladder.
    v.floor = [b for p, b in wall_cells if p[1] == ana.wall_lo
               and not b.short.endswith(NOT_A_FLOOR)]
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
    # A merlon is a FULL BLOCK. A `cobblestone_wall` is a garden coping and the
    # user has ruled it out of our builds; harvested walls go to `v.coping` and
    # are not used as battlements.
    v.crenel = v.crenel or (v.stone[0] if v.stone else state("cobblestone"))
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
    level: int = 0               # rung of the ladder: drives the yard stores
    beams: bool = True           # horizontal logs at storey breaks
    rail: bool = False           # fence railing round a flat lookout platform
    facade: bool = True          # apply pier/two-tone/arch/corbel articulation
    open_deck: bool = False      # roofed platform on posts, not a closed top
    # No `external_stair`: the one that existed walked `_ring` in raster order,
    # so it was 44 disconnected treads rather than a flight. The climb is the
    # internal ladder; see the note where it used to be built.



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
        # A doorstep. The floor inside sits one course above the apron, so the
        # threshold was a full block and entering the tower meant jumping. A stair
        # is the one thing you can climb a whole block onto, which is exactly what
        # a step is for.
        outward = {"north": (0, -1), "south": (0, 1),
                   "west": (-1, 0), "east": (1, 0)}[plan.front]
        sx_, sz_ = dx + outward[0], dz + outward[1]
        if 0 <= sx_ < span_x and 0 <= sz_ < span_z:
            # `facing` names the TALL half, so a step you climb inward has its
            # tall half toward the door — the opposite of `front`. Measured on
            # `house_lvl6`: the ridge stands at x=4, the west slope carries
            # facing=east and the east slope facing=west, both pointing at the
            # high side. Facing it outward put the full-block half where you put
            # your foot, which is the jump the step exists to remove.
            vox.set((sx_, 1, sz_), state(
                "cobblestone_stairs", facing=OPPOSITE_SIDE[plan.front],
                half="bottom", shape="straight", waterlogged="false"))

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

    # --- the climb is the internal ladder; there is no external flight ---
    #
    # There was one, and it was never a flight. `_ring` returns its cells in
    # RASTER order — every z for x0, then every z for x1 — so consecutive
    # "treads" were not adjacent: the run jumped clear across the tower between
    # steps, and one tread landed inside the wall at (4, 4, 5). Measured, it
    # placed 44 stairs whose tall half pointed the wrong way and reached nothing:
    # `check_usable` reported NO-STAIR on watchtower levels 1-6, and the ring of
    # loose stone it left round the base is the litter visible on the contact
    # sheet at the foot of every level.
    #
    # A wrapping flight was also the wrong device here. **Ladder inside, stepped
    # stone outside** — and outside, a step is what gets you over a threshold. A
    # masonry ramp wrapping the shaft to the roof would add ~130 blocks of stone,
    # make the ladder pointless, and read as a spiral bunker rather than a
    # village lookout. The medieval pattern the reference shows is a flight up to
    # a RAISED door; this tower's door is at ground level, so it needs a step and
    # not a ramp. `ensure_climbable` guarantees the ladder from that floor to the
    # deck.

    # --- a lean-to against one face: the strongest asymmetry available ---
    if plan.buttress and m >= 2:
        # Never against the front. The lean-to is placed after the door, so on
        # the front face it overwrote the doorstep with its stone course and the
        # doorway itself with its timber one: the tower's only entrance opened
        # into a wall, and every level measured as unenterable.
        side = rng.choice([s for s in SIDES if s != plan.front])
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
    _scatter_props(vox, x0, x1, z0, z1, rng, level=plan.level)
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

    # A ladder hangs on the block behind it. Rather than hope that wall is solid
    # for the whole run — it is not, once windows and a timber storey arrive, and
    # the run then stopped a couple of courses off the floor — the support column
    # is MADE solid. Guaranteeing it beats detecting it: every level of this tower
    # measured as having no way up while the ladder itself was present.
    behind = {"north": (0, 1), "south": (0, -1),
              "west": (1, 0), "east": (-1, 0)}[lad.get("facing", "north")]
    bx, bz = lx + behind[0], lz + behind[1]
    if not (0 <= bx < sx and 0 <= bz < sz):
        return

    # The highest cell inside the shaft that something could stand on.
    top_floor = 0
    for y in range(1, sy - 1):
        for x in range(x0 + 1, x1):
            for z in range(z0 + 1, z1):
                if (x, z) == (lx, lz):
                    continue
                if vox.occupied((x, y, z)) and not vox.occupied((x, y + 1, z))                         and not vox.occupied((x, y + 2, z)):
                    top_floor = max(top_floor, y)
    if top_floor <= 0:
        return

    filler = state("cobblestone")
    for y in range(1, top_floor + 2):
        if not vox.occupied((bx, y, bz)):
            vox.set((bx, y, bz), filler)
        vox.set((lx, y, lz), lad)

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
               palisade: bool = False, banners: bool = False,
               level: int = 0) -> Voxels:
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
    # Ground a prop may stand on. The old rule demanded a cell fully outside the
    # shell plus a one-cell margin, and on the taller barracks rungs there was no
    # such cell left — so the yard's barrel and chest existed at levels 1-3 and
    # were simply absent from 4 upward. Stores lean on the wall of the building
    # they belong to, so the ring against the wall counts too, minus the strip in
    # front of a door: nobody stacks barrels across their own threshold.
    doors = [q for q, b in out.solid_items() if b.short.endswith("_door")]

    def clear_of_doors(x: int, z: int) -> bool:
        return all(abs(x - q[0]) + abs(z - q[2]) >= 3 for q in doors)

    open_ground = [(x, z) for x in range(sx) for z in range(sz)
                   if not out.occupied((x, ana.ground_top + 1, z))
                   and out.occupied((x, ana.ground_top, z))
                   and not (x0 <= x <= x1 and z0 <= z <= z1)
                   and clear_of_doors(x, z)]
    # Sorted, then shuffled with a level-independent key, so the yard stays on the
    # same side of the building as it is upgraded instead of jumping about.
    open_ground.sort()
    random.Random(ana.shell[0] * 31 + ana.shell[2]).shuffle(open_ground)
    gy = ana.ground_top
    if open_ground:
        # A campfire sits on the ground. It used to be raised on a cobblestone
        # pedestal as a "brazier"; that is not how anyone places one.
        bx, bz = open_ground.pop()
        out.set((bx, gy + 1, bz), state("campfire", facing="north", lit="true",
                                       signal_fire="false", waterlogged="false"))
    # 5. Stores, not produce — and stores only.
    #
    # The anvil used to be in this list, scattered on open ground with a per-level
    # rng. So `barracks_lvl1` and `lvl3` had an anvil and `lvl4..6` did not: a tool
    # that appears, moves and then vanishes as the building is upgraded. The
    # author never does that — nothing that appears in one of his rungs is missing
    # from a higher one, and a workstation of his stands in a niche indoors, not
    # on the grass. Tools are the work ladder's job (`military_fittings`); this
    # pass puts out barrels and a chest and nothing that anybody works at.
    stores = [state("barrel", facing="up", open="false"),
              state("chest", facing="north", type="single")]
    # The count grows with the rung and never shrinks, for the same reason.
    for k in range(1 + level // 2):
        if not open_ground:
            break
        sxp, szp = open_ground.pop()
        out.set((sxp, gy + 1, szp), stores[k % len(stores)])

    # A palisade used to be added here: a line of upright logs at the plot
    # edge, placed with a 70% chance per cell and random height. It read as
    # scattered litter rather than a stockade — 30 lone stakes across the set —
    # so it is gone. A fence line is only worth adding as a deliberate,
    # continuous run with a gate, not as probabilistic scatter.
    return out


def _scatter_props(vox: Voxels, x0: int, x1: int, z0: int, z1: int,
                   rng: random.Random, level: int = 0) -> None:
    """A small yard of stores clustered on one side of the build.

    This is the most effective asymmetry available to a square tower: the shaft
    itself is symmetric by necessity, so the ground around it has to carry the
    irregularity. Every id here occurs in the author's corpus.
    """
    sx, sy, sz = vox.size
    # Garrison stores only. Hay bales and decorated pots were what made the
    # first pass read as a farmstead.
    # Stores, not tools. An anvil or a bench standing in the grass beside a tower
    # is a workstation nobody works at, and being scattered it moved and then
    # disappeared as the tower was upgraded.
    props = [state("barrel", facing="up", open="false"),
             state("barrel", facing="up", open="false"),
             state("chest", facing="north", type="single")]
    # Pick one quadrant and keep the clutter inside it — the SAME quadrant at
    # every rung, and one more crate as the rungs go up. Chosen per level, the
    # tower's yard jumped from corner to corner and its barrel count went
    # 5, 2, 3, 2, 3, 3, 5: stores appearing, moving and disappearing as the
    # garrison grew. Keyed off the footprint instead, which does not change.
    key = random.Random(x0 * 131 + z0 * 17 + x1)
    qx, qz = key.choice((0, 1)), key.choice((0, 1))
    xs = range(0, x0) if qx == 0 else range(x1 + 1, sx)
    zs = range(0, z0) if qz == 0 else range(z1 + 1, sz)
    spots = [(x, z) for x in xs for z in zs]
    spots.sort()
    key.shuffle(spots)
    for k, (x, z) in enumerate(spots[: 1 + level // 2]):
        if vox.occupied((x, 1, z)) or not vox.occupied((x, 0, z)):
            continue
        # Which crate, by position in the run rather than by dice. Rolling for it
        # kept the TOTAL monotonic while the barrel/chest split wobbled — the
        # tower's chest count went 0,0,3,2,3,2,3, so a chest still disappeared
        # between two rungs even though nothing had been removed.
        vox.set((x, 1, z), props[k % len(props)])
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

# ── the work ladder ─────────────────────────────────────────────────
#
# Read off the author's own 196 workstations in `plains/jobs/**`. Three rules he
# does not break:
#
#   1. **The tool that names the building is there at level 0 and never leaves.**
#      `kitchen` has its smoker at l0, `leather_workshop` its cauldron, `oven` and
#      `workshop` their bench. Nothing that ever appears is later removed.
#   2. **Counts only grow, and slowly.** beehive 1,1,1,3,5,5,5,5. kitchen smoker
#      1,1,1,2,2,2,3. wheat_farm composter 1,1,2,3,3,3. One or two more per rung.
#   3. **A new KIND unlocks at one specific rung**, and always in the same order:
#      heat in the middle, the specialist tool at the top. `workshop` gets its
#      furnace at l3 and its stonecutter at l4 of 4; `leather_workshop` its
#      furnace at l2 and its water cauldron at l5 of 6; `beekeeper` its furnace at
#      l2 and its bee nest at l3.
#
# So a rung is not "the same room with more props in it". It is one more thing the
# trade can now DO, and the previous rung's equipment standing exactly where it
# stood. That last part matters mechanically as well: `UpgradeAction` spawns the
# delta between levels, so a bench that moves between rungs is a bench the upgrade
# builds twice.
# What the trade can AFFORD, rung by rung. Two limits, and both are hard.
#
# **Vocabulary.** Measured over the author's 121 files: `anvil` appears in ZERO of
# them, `grindstone` in zero, `smithing_table` in zero, `stonecutter` in one.
# `crafting_table` is in 67, `furnace` in 41, `smoker` in 17, `cauldron` in 7. My
# first ladder opened with an anvil at level 0 — an id he never places, in the rung
# that has least earned it.
#
# **Cost.** An anvil is three iron blocks and four ingots: 31 iron. A cauldron is
# 7. A stonecutter is 1 iron and three stone. A bench, a furnace, a barrel and a
# chest are planks and cobble — free to a village with neither mine nor smith. So
# the order is not a matter of taste: a settlement that has just thrown up an earth
# bank cannot own the most expensive workstation in the game, and the rung a tool
# arrives at is the rung its iron becomes plausible.
#
#     free      crafting_table, furnace, smoker, barrel, chest
#     1 iron    stonecutter        (and it needs stone-working, so not before)
#     7 iron    cauldron
#     31 iron   anvil              — the top rung, and nothing earlier
#
# Counts are TOTALS, not additions: whatever the donor already carries counts
# toward them, so the ladder describes the finished building and the monotonicity
# sweep measures the same number the ladder promises.
ARMOURY_LADDER: Tuple[Dict[str, int], ...] = (
    {"crafting_table": 1},                                   # 0  a bench
    {"crafting_table": 1},                                   # 1  stone arrives
    {"crafting_table": 1},                                   # 2  heat arrives
    {"crafting_table": 1, "stonecutter": 1},                 # 3  first iron
    {"crafting_table": 1, "stonecutter": 1, "cauldron": 1},   # 4  quench trough
    {"crafting_table": 1, "stonecutter": 1, "cauldron": 1,
     "anvil": 1},                                            # 5  31 iron
)

# No spear rack. It was two `oak_fence` posts stacked in a cell and called a rack
# of polearms, and that is not what it reads as — it reads as a fence left standing
# in the middle of a room, and one of them ended up on top of a chest. Stacking
# blocks and naming the stack an object is not a device. The author does stack
# fences, 573 times over 121 files, but always as a railing or a window screen,
# where the stack IS the thing it looks like. The military reading comes from the
# forge, the stores and the walls.
SHIELDS_FROM = 4

WORK_ITEM = {
    "crafting_table": lambda side: state("crafting_table"),
    "anvil": lambda side: state("anvil", facing=OPPOSITE_SIDE[side]),
    "stonecutter": lambda side: state("stonecutter", facing=OPPOSITE_SIDE[side]),
    # EMPTY, not `water_cauldron`: a quench trough that is full of water is a
    # water source block, and it spreads.
    "cauldron": lambda side: state("cauldron"),
}


def work_spots(vox: Voxels, floor: int) -> List[Tuple[Coord2, str, int]]:
    """Floor cells a workstation may stand in, best first.

    The ranking is measured, not guessed, and it is the opposite of what I assumed
    twice. Over the author's 196 workstations:

        free orthogonal neighbours:  0 → 11%   1 → 54%   2 → 21%   3 → 10%
        distance to the nearest door: 0-1 → 4%, 2+ → 96%, 4+ → 60%
        touching another workstation: 19%

    So his workstation lives in a NICHE — walled on three sides, one way in — well
    away from the door, standing alone. My first instinct was the reverse ("only
    put furniture in open floor with three ways out"), which would have rejected
    two thirds of his own placements.

    A niche is also the safe choice functionally, which is why the two agree: a
    dead-end cell cannot be the link between two halves of a room. The dangerous
    cell is the one with exactly two free neighbours facing each other — a
    corridor — and that is tested for directly rather than by counting.
    """
    sx, sy, sz = vox.size
    doors = [p for p, b in vox.solid_items() if b.short.endswith("_door")]

    def free(x: int, z: int) -> bool:
        return (0 <= x < sx and 0 <= z < sz
                and not vox.occupied((x, floor + 1, z)))

    def full_support(x: int, z: int) -> bool:
        """A whole block underfoot, not a slab or a stair.

        A slab and a stair are how a route changes height, so anything standing
        on one is standing in a stairway. This is not a detail: the spear rack
        landed on the bottom slab of the armoury's own interior flight at
        (3, 2, 3) and (3, 3, 3), and `armory_lvl3` and `lvl4` lost their upper
        floor completely. To the 2D floor test that cell looked like the best
        niche in the building — walled on both sides, one way in — because the
        rest of the flight is at other heights and a plan view cannot see it.
        """
        b = vox.get((x, floor, z))
        if b is None or b.short.endswith(NOT_A_FLOOR):
            return False
        # Nor on top of something that is itself a thing. A fence post ended up
        # standing on a chest in `armory_lvl4`: legal Minecraft, nonsense to look
        # at, and it makes the chest unopenable from above.
        return not b.short.endswith(NOT_A_PEDESTAL)

    # The floor graph: cells you could stand in, at this storey.
    space = {(x, z) for x in range(sx) for z in range(sz)
             if free(x, z) and full_support(x, z)}

    def neighbours(c: Coord2) -> List[Coord2]:
        x, z = c
        return [n for n in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1))
                if n in space]

    def a_chokepoint(c: Coord2) -> bool:
        """Would filling this cell cut the floor in two?"""
        ns = neighbours(c)
        if len(ns) <= 1:
            return False                     # a dead end: safe by construction
        rest = space - {c}
        seen, stack = {ns[0]}, [ns[0]]
        while stack:
            x, z = stack.pop()
            for n in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
                if n in rest and n not in seen:
                    seen.add(n)
                    stack.append(n)
        return not all(n in seen for n in ns)

    def wall_side(x: int, z: int) -> Optional[str]:
        """A solid neighbour to back onto, if there is one."""
        for side, (dx, dz) in (("north", (0, -1)), ("south", (0, 1)),
                               ("west", (-1, 0)), ("east", (1, 0))):
            nb = vox.get((x + dx, floor + 1, z + dz))
            if nb is not None and not nb.short.endswith(
                    ("_slab", "_stairs", "_fence", "_pane", "_door", "_torch")):
                return side
        return None

    out: List[Tuple[Coord2, str, int]] = []
    for (x, z) in sorted(space):
        side = wall_side(x, z)
        door_d = min((abs(x - q[0]) + abs(z - q[2]) for q in doors), default=99)
        # Preferences are DEMOTED, never dropped. Rejecting outright left the
        # level 0 armoury with no legal cell at all and therefore no anvil — the
        # very thing this was written to prevent — and cost `armory_lvl4` its
        # cauldron once the stonecutter had taken the last good niche. A ranked
        # list that always has a tail means the caller can keep looking, and
        # `try_put` is what actually guarantees the building still works.
        #
        # The order of the ranks is the author's own distribution: one free side
        # (54% of his workstations), then walled in (11%), then two sides (21%),
        # then open floor. Ties break on distance from the door and then on
        # coordinate, so the SAME niche wins at every rung of a ladder and the
        # bench does not move when the building is upgraded.
        openness = len(neighbours((x, z)))
        rank = {1: 0, 0: 1, 2: 2}.get(openness, 3)
        if side is None:
            rank += 5                        # nothing to back onto
            side = "north"
        if door_d < 2:
            rank += 10                       # his 96%: not in the doorway
        if a_chokepoint((x, z)):
            rank += 20                       # a plan view says this is the way through
        if vox.occupied((x, floor + 2, z)):
            # Something directly overhead. A workstation is one block tall so this
            # is legal, and his own are in rooms with ceilings — but a cell with
            # air above it reads better, so prefer one. Rejecting these outright
            # left `armory_lvl5` with two candidate cells in the whole building
            # and cost it its cauldron and its bench.
            rank += 2
        out.append(((x, z), side, rank * 100 - min(door_d, 9)))
    out.sort(key=lambda t: (t[2], t[0]))
    return out


def _place_one(vox, spots, used, floor, name, free, try_put, claim,
               spaced: bool) -> bool:
    """Put one workstation in the best remaining niche. True if it landed."""
    for (xz, side, _k) in spots:
        if spaced and xz in used:
            continue
        p = (xz[0], floor + 1, xz[1])
        if not free(p):
            continue
        if try_put([(p, WORK_ITEM[name](side))]):
            claim(xz)
            return True
    return False


def military_fittings(vox: Voxels, seed: int = 0, ana: Optional[Anatomy] = None,
                      shields: bool = False,
                      armoury: bool = True, level: int = 0) -> int:
    """Fit out a building so it reads as military without any heraldry.

    The user rejected banners as the signal. These three carry the meaning
    through function instead:

      shields  trapdoors mounted flat on an inner wall at head height. The
               author uses horizontal trapdoor runs himself (pen railing in
               `pig_farm_lvl6..8`), so this stays in vocabulary
      armoury  an anvil, a stonecutter as a grindstone, and an EMPTY cauldron as
               a quench trough — one per niche, NOT clustered: 81% of his
               workstations touch no other workstation

    `level` drives `ARMOURY_LADDER`. Every id occurs in the
    author's corpus. Returns the number of blocks placed.
    """
    ana = ana or analyse(vox)
    sx, sy, sz = vox.size
    floor = ana.wall_lo
    placed = 0

    def free(p: Coord) -> bool:
        return (0 <= p[0] < sx and 0 <= p[1] < sy and 0 <= p[2] < sz
                and not vox.occupied(p))

    spots = work_spots(vox, floor)
    used: set = set()

    def claim(spot: Coord2) -> None:
        """A workstation stands alone: block its neighbours too."""
        x, z = spot
        used.update({(x, z), (x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)})

    # The 2D ranking in `work_spots` orders candidates well but cannot see a
    # staircase: its treads are at other heights, so a cell in the middle of a
    # flight looks like a walled-in niche. It picked one twice — first the bottom
    # slab of the armoury's own interior flight, then, once slabs were excluded,
    # the cell one step along it — and `armory_lvl3`/`lvl4` lost their upper floor
    # both times. So a placement is TRIED against the real walk graph, which
    # already models stairs and slabs, and a spot that costs the building its
    # connectivity is passed over for the next candidate.
    #
    # This is not the rollback that used to delete the anvil. Nothing is dropped:
    # the item keeps looking until it finds a niche it may legally stand in.
    from .assemble import _interior_reach
    reach = _interior_reach(vox)

    def try_put(group: List[Tuple[Coord, BlockState]]) -> bool:
        nonlocal reach, placed
        undo = [(q, vox.get(q)) for q, _b in group]
        for q, b in group:
            vox.set(q, b)
        after = _interior_reach(vox)
        if after < reach - 1e-9:
            for q, was in undo:
                vox.set(q, was)
            return False
        reach = after
        placed += len(group)
        return True

    # 1. The armourer's benches. Placed FIRST, because they are the building's
    #    reason to exist and must get the best niches; the rack can go anywhere.
    if armoury:
        kit = ARMOURY_LADDER[min(level, len(ARMOURY_LADDER) - 1)]
        # The ladder states TOTALS. A donor that already has a bench does not get
        # a second one bolted to the wall beside it — his own is the building's,
        # and two crafting tables in one room is the sort of detail that says
        # nobody looked.
        have = {}
        for _p, b in vox.solid_items():
            have[b.short] = have.get(b.short, 0) + 1
        for name in ("anvil", "stonecutter", "cauldron", "crafting_table"):
            for _n in range(max(0, kit.get(name, 0) - have.get(name, 0))):
                # Two passes. The first keeps a clear cell around each bench,
                # which is his 81%; the second allows them to touch, which is his
                # other 19%. Insisting on the gap outright meant the top rung of
                # the armoury ran out of niches and lost its cauldron and its
                # bench — the "spread out" preference silently outranking "the
                # building has the equipment it is supposed to have".
                for spaced in (True, False):
                    if _place_one(vox, spots, used, floor, name, free,
                                  try_put, claim, spaced):
                        break

    # 3. Shields: trapdoors flat on a wall at head height, facing off the wall.
    #    Decoration, so it arrives late — the author puts wool, carpet and banners
    #    on the top rung only.
    if shields and level >= SHIELDS_FROM:
        hung = 0
        for (xz, side, _k) in spots:
            if hung >= 3:
                break
            if xz in used:
                continue        # a shield does not hang over the anvil
            hx, hz = xz
            y = floor + 2
            # Nor over anything else. The loop ignored `used` and hung trapdoors
            # directly above the cauldron, the bench and the stonecutter — the
            # same "one functional block stacked on another" that the rack was.
            under = vox.get((hx, floor + 1, hz))
            if under is not None:
                continue
            off = {"north": (0, -1), "south": (0, 1),
                   "west": (-1, 0), "east": (1, 0)}[side]
            wall = (hx + off[0], y, hz + off[1])
            if not free((hx, y, hz)) or not vox.occupied(wall):
                continue
            # `facing` must point at the wall's opposite so the support is the
            # wall itself. Through `try_put` like everything else: a shield hung
            # at head height over the armoury's interior flight is exactly as
            # impassable as a bench standing on it, and that is how `armory_lvl4`
            # kept losing its upper floor after the benches had been fixed.
            if try_put([((hx, y, hz), state(
                    "oak_trapdoor", facing=OPPOSITE_SIDE[side], half="top",
                    open="false", powered="false", waterlogged="false"))]):
                hung += 1
    return placed


OPPOSITE_SIDE = {"north": "south", "south": "north",
                 "west": "east", "east": "west"}
