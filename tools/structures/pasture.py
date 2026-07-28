"""The livestock set: a farmstead per animal — cow, pig, sheep.

Not a pen with a shed in it. A **farmstead**: the author's own house, with this
animal's yard wrapped round it, and the byre built against the house wall. That
shape came out of two rejections — a composed shed read as a plank tower, and a
yard parked alongside the house read as "a house with a fenced strip next to it".

The building is the author's, not ours. He built seven levels of `house` —
footprint 9x11 at every one, palette 27 rising to 57 — so the tier ladder here
simply installs `house`, `house_lvl1`, `house_lvl2`, `house_lvl3`, `house_lvl4`
and `house_lvl6` in turn, and the whole material progression of the farmstead is
his work. Our part is the yard, the byre against his east wall, and the ground.

**Yard area is not the same for the three, because it is not the same in life.**
Cattle need by far the most ground per head and are kept at pasture; sheep graze
wide but are folded tight and want a small holding pen to be sorted and shorn in;
a sty is deliberately compact and its floor is churned to mud, because pigs root
and wallow rather than graze. So the same house carries an 11x11, a 9x10 and a
7x8 flank yard, each with a working strip behind the house.

The yard is an **L** wrapping the back and one flank. That puts the house inside
its own farmstead: two of its walls are boundary, the front stays clear for the
street and the door, and the two arms do different jobs — the narrow one behind
the house is the trodden working yard with the byre, the rack and the muck heap;
the wide one is grazing.

**Function first**, as in `wall.py`, and here it is what shaped the design:

* An animal **jumps a full block**, so the boundary is not "a solid ring at
  animal height". Anything a block high beside a fence is a mounting block.
  `escape_routes` models the jump, the free fall, the 1.5-tall perchable rail and
  the closed gate an animal cannot open. It found the leak immediately — the log
  posts in the pens' own fence line — and every full block in a run is capped
  because of it, with a one-cell clear lane inside every fence for props.
* `check_pen` also requires a route in through the gate, into the byre, and into
  the **farmhouse**: grafting a donor can go wrong in ways the style gate cannot
  see, and an offset by one puts a wall through the doorway.
* `tools/check_pens.py` re-checks the shipped files from the animals' own
  recorded positions, so the generator cannot mark its own homework.

Things the donor forces you to measure rather than assume, each of which cost a
cycle:

* `house.nbt` is **eight** cells wide inside its nine-wide box; `house_lvl2` and
  up are nine. Assuming the wall sat at the box edge left a one-cell corridor
  between house and yard, open to the plot edge, and the animals walked down it.
  `house_east_wall` measures it, and the donor is shifted east so the wall lands
  on the same column at every rung — the plot has to be identical at every level
  because `UpgradeAction` replaces the NBT at the same origin.
* The donor plants tufts of grass along its walls, so "is the neighbour cell
  occupied" is the wrong test for "is this run closed". A run is closed only by
  two solid courses.
* Donor levels ship a raw `minecraft:villager`, an item frame, up to three street
  connectors and pre-1.20.3 `minecraft:grass`. All dropped or renamed on load.

Materials stay inside the ruling for this fork — oak, cobblestone, mossy
cobblestone, `mud` for the pigs, `packed_mud` as the one sanctioned addition, and
no `podzol` even though all three of the author's own animal fields use it. The
yard's own progression:

    0  crooked oak fence, a drinking puddle dug into the terrain, one bale
    1  fence put straight on capped posts, open byre off the house wall
    2  stone-pier fence (`05d4`), kerbed trough and a filled cauldron, hay rack
    3  byre gabled and shuttered, muck heap, holding pen for a flock, worn paths
    4  byre on a stone plinth, posts capped, a lantern under cover (lvl4+ only)
    5  the byre run out to full length under a deep eave

`stone-pier fence` was item 4 on the still-open list in `docs/BUILD_LANGUAGE.md`:
masonry posts with fence infill between them, from reference `05d4`. A line of
loose logs reads as litter; this is what a palisade should have been.
"""

from __future__ import annotations

import random
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Sequence, Set, Tuple

import nbtlib
from nbtlib import Compound
from nbtlib import List as NbtList

from .compose import JIGSAW_ORIENTATION, jigsaw
from .corpus import modernize
from .fabric import Canvas, Fault
from pathlib import Path

from .nbtio import BlockState, Coord, Voxels, load, state
from .traverse import check_route, is_passable, surface

Coord2 = Tuple[int, int]


# ── the farm's own palette ──────────────────────────────────────────
#
# Deliberately NOT imported from `wall.py`. That module carries the
# fortification material ladder — tuff, deepslate, brick weathering — which is a
# different vocabulary with a different job, and coupling a cattle shed to it
# means every change to the wall tiers reaches into the byre. A farm is oak,
# cobblestone and mossy cobblestone, and that is the whole list.

@dataclass(frozen=True)
class Stone:
    """Cobblestone in three tones: the field, the odd post, the weathered foot."""

    main: str = "cobblestone"
    second: str = "andesite"
    weathered: str = "mossy_cobblestone"
    cut: str = "cobblestone"

    def slab(self, type_: str = "bottom") -> BlockState:
        return state(f"{self.cut}_slab", type=type_, waterlogged="false")

    def stairs(self, facing: str = "north", half: str = "bottom") -> BlockState:
        return state(f"{self.cut}_stairs", facing=facing, half=half,
                     shape="straight", waterlogged="false")

    def rail(self) -> BlockState:
        """A rail is OAK, even on a masonry course.

        This used to return a `*_wall` block as a "dry-stone fold". Two rulings
        kill it: a farm boundary never turns to stone, and stone `*_wall` blocks
        are out of our vocabulary altogether. It also measured badly on its own
        terms — a fence does not connect to a `*_wall` (0 of 8 in the author's
        files), so every joint between the two read as a broken run.
        """
        return state("oak_fence", north="false", south="false", west="false",
                     east="false", waterlogged="false")


@dataclass(frozen=True)
class Timber:
    """Oak used as oak: posts stand, beams lie, planks fill panels."""

    post: BlockState = field(default_factory=lambda: state("oak_log", axis="y"))
    planks: BlockState = field(default_factory=lambda: state("oak_planks"))

    def beam(self, axis: str) -> BlockState:
        return state("stripped_oak_log", axis=axis)

    def stairs(self, facing: str = "north", half: str = "bottom") -> BlockState:
        return state("oak_stairs", facing=facing, half=half, shape="straight",
                     waterlogged="false")

    def slab(self, type_: str = "bottom") -> BlockState:
        return state("oak_slab", type=type_, waterlogged="false")

    def fence(self, axis: Optional[str] = None) -> BlockState:
        props = dict(north="false", south="false", west="false", east="false",
                     waterlogged="false")
        if axis == "z":
            props.update(north="true", south="true")
        elif axis == "x":
            props.update(east="true", west="true")
        return state("oak_fence", **props)

    def door(self, facing: str, half: str, hinge: str) -> BlockState:
        return state("oak_door", facing=facing, half=half, hinge=hinge,
                     open="false", powered="false")

    def trapdoor(self, facing: str, half: str = "top") -> BlockState:
        return state("oak_trapdoor", facing=facing, half=half, open="false",
                     powered="false", waterlogged="false")


@dataclass(frozen=True)
class Palette:
    stone: Stone = field(default_factory=Stone)
    wood: Timber = field(default_factory=Timber)


def trim(vox: Voxels, pad: int = 1) -> Voxels:
    """Shrink the declared box to what got built, plus `pad` of headroom.

    The author leaves headroom above the highest block in 70 of his 98 buildings,
    so a little is in keeping; the five layers the hand-computed heights produced
    are not.
    """
    want = min(vox.size[1], vox.top_y() + 1 + pad)
    if want != vox.size[1]:
        vox.size = (vox.size[0], want, vox.size[2])
    return vox

MARGIN = 1                      # apron cells outside the fence, all round
JOBS_TARGET = "onceuponatown:jobs"

NEIGH4: Tuple[Coord2, ...] = ((1, 0), (-1, 0), (0, 1), (0, -1))
AXIS_OF = {"north": "z", "south": "z", "west": "x", "east": "x"}
OPPOSITE = {"north": "south", "south": "north", "west": "east", "east": "west"}
VEC = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}

# Full cubes in this palette. A fence, a wall and a pane all connect to a full
# block and to each other, and to nothing else — a slab or a stair neighbour
# leaves the rail unconnected, which is why the props have to be derived from
# the finished grid rather than guessed while placing.
# Which neighbours a rail connects to. **Measured off the author's 121 files**
# rather than guessed from the vanilla source: for every fence, pane and wall in
# the corpus, which neighbour ids had the connection flag set.
#
#   connects:  oak_planks 716/12   oak_log 192/8   cobblestone 148/5
#              mossy_cobblestone 43/0   hay_block 41/0   stripped_oak_log 38/0
#              stone 33/6   white_terracotta 56/0   crafting_table 5/0
#   does not:  oak_slab 1/115   oak_stairs 17/52   oak_trapdoor 2/38
#              stone_slab 5/38   white_bed 0/13   leaves 0/516   plants, pots,
#              lanterns, chains, torches, pressure plates
#
# The finding that mattered: **a fence does not connect to a `*_wall` block**
# (0 of 8 in his files) and a wall does not connect to a fence (0 of 8, the same
# pairs seen from the other side). The first version of this module had them
# connecting, which is a wrong property on every rail beside the sheep fold's
# dry-stone infill.
STURDY = {
    "oak_planks", "oak_log", "stripped_oak_log", "cobblestone",
    "mossy_cobblestone", "andesite", "stone", "smooth_stone", "hay_block",
    "white_wool", "brown_wool", "crafting_table", "white_terracotta", "barrel",
    "packed_mud", "mud", "dirt", "coarse_dirt", "grass_block", "rooted_dirt",
}
# Kept as a separate name because other modules import it: everything a *fence*
# may be asked about. `dirt_path` is deliberately absent — its side face is
# 15/16 tall, so nothing connects to it.
FULL_BLOCKS = STURDY

RAILS = {"oak_fence", "cobblestone_wall", "glass_pane", "iron_bars"}
# Decoration a rail may be built straight through.
SOFT_DECOR = {"short_grass", "grass", "oak_leaves", "oak_sapling",
              "flower_pot", "dandelion", "poppy"}


# ── ground mixes ────────────────────────────────────────────────────
#
# Weighted tuples, sampled per cell. `podzol` is deliberately absent: all three
# author fields use it and the user ruled it out for this fork. `packed_mud` is
# the one sanctioned block that does not occur in the author's corpus.

TRODDEN = ("coarse_dirt", "coarse_dirt", "dirt_path", "dirt", "packed_mud")
GRAZED = ("grass_block", "grass_block", "grass_block", "coarse_dirt", "dirt")
MIRE = ("mud", "mud", "mud", "packed_mud", "coarse_dirt")
FOLD = ("grass_block", "grass_block", "coarse_dirt", "coarse_dirt", "dirt")


# ── the farmhouse ───────────────────────────────────────────────────
#
# The building is not composed, it is **the author's own house**, grafted in. He
# built seven levels of it — `house` through `house_lvl6`, footprint 9x11 at
# every one, palette 27 rising to 57 — which is exactly the ladder this set
# needs, so the material progression of the farmstead is his work rather than a
# rule of ours. A composed shed could not come close, and the earlier attempts
# proved it twice.
#
# Facts about the donor that have to be handled, all measured:
#   * the door is at (3, 1..2, 8) facing south, so the front is +Z
#   * the terminator connector sits at (4, 0, 10), in front of that door
#   * higher levels carry up to three extra street connectors — dropped, since a
#     farmstead is a leaf and offers nothing to build onto
#   * `house`, `house_lvl2` ship a raw `minecraft:villager` and `house_lvl6` an
#     item frame — dropped, the mod spawns its own citizens
#   * beds appear at `house_lvl4`, which is where the JSON grants residents

HOUSES = Path(__file__).resolve().parents[2] / (
    "common/src/main/resources/data/onceuponatown/structure/plains/houses")

# **A family per breed, not one house for all three.** Measured: with the same
# donor everywhere, the three farmsteads came out 0.93 cosine-similar by block
# content and the six levels of one breed 0.97 — against 0.78 across the author's
# own house ladder and 0.81 across his three animal fields. They were the same
# build three times over, which is exactly what the user said.
#
# The author has three house families and they are genuinely different buildings:
#
#   house      9x11, one storey, plainest — the compact sty
#   house_2   12x12, **two storeys**, dormer stairs, 11 stair states at lvl4
#   house_3   14x12, stone-heavy base with a timber upper — the biggest farmstead
#
# `house_3_lvl6` is one of the four permanently corrupt files, so that ladder
# stops at lvl5; `house_2_lvl2` is skipped to keep six rungs that step visibly.
HOUSE_LADDERS = {
    "house": ("house", "house_lvl1", "house_lvl2", "house_lvl3", "house_lvl4",
              "house_lvl6"),
    "house_2": ("house_2", "house_2_lvl1", "house_2_lvl3", "house_2_lvl4",
                "house_2_lvl5", "house_2_lvl6"),
    "house_3": ("house_3", "house_3_lvl1", "house_3_lvl2", "house_3_lvl3",
                "house_3_lvl4", "house_3_lvl5"),
}

_house_cache: Dict[str, Voxels] = {}


def donor_house(name: str) -> Voxels:
    """Load a donor house once, with its pre-1.20.3 block ids renamed.

    28 of the author's structures contain `minecraft:grass`, removed in 1.20.3.
    Renaming at load rather than after composition matters because the geometry
    code *reads* the grafted blocks to decide where walls are.
    """
    if name not in _house_cache:
        vox = load(HOUSES / f"{name}.nbt")
        modernize(vox)
        _house_cache[name] = vox
    return _house_cache[name]


def house_door(donor: Voxels) -> Coord2:
    """Where the donor's front door is, measured.

    Hard-coding (3, 8) worked only for the `house` family; `house_2` and
    `house_3` put theirs elsewhere, and the plot's street connector goes in front
    of it.
    """
    for (x, y, z), b in sorted(donor.solid_items()):
        if b.short.endswith("_door") and b.get("half") == "lower":
            return (x, z)
    return (donor.size[0] // 2, donor.size[2] - 1)


def house_bounds(donor: Voxels) -> Tuple[int, int, int, int]:
    """The donor's actual **wall** footprint at y=1..2, as (x0, x1, z0, z1).

    Measured, because the building does not fill its box and the box is what
    every earlier version of this module assumed. `house.nbt` is eight cells wide
    in a nine-wide box and every level starts one cell in from the north edge, so
    aligning the yard to the box left a **one-cell dead corridor** between the
    yard fence and the real wall — which reads as the fence being built twice,
    and is exactly what it was.

    Terrain, vegetation and the donor's own garden fence are excluded: a fence is
    not a wall, and the yard has to butt onto something solid.
    """
    cells = [(p[0], p[2]) for p, b in donor.solid_items()
             if p[1] in (1, 2) and b.short not in TERRAIN_LIKE
             and not b.short.endswith(("_fence", "_fence_gate"))]
    if not cells:
        return (0, donor.size[0] - 1, 0, donor.size[2] - 1)
    xs = [c[0] for c in cells]
    zs = [c[1] for c in cells]
    return (min(xs), max(xs), min(zs), max(zs))


TERRAIN_LIKE = {"grass_block", "dirt", "coarse_dirt", "dirt_path", "podzol",
                "rooted_dirt", "mud", "packed_mud", "water", "short_grass",
                "grass", "oak_sapling", "oak_leaves", "farmland", "wheat"}


def graft(dst: Voxels, donor: Voxels, at: Coord2,
          keep_ground_in: Optional[Tuple[int, int, int, int]] = None) -> None:
    """Stamp a donor building into the plot at `at`, block-entity data included.

    Skips the donor's own ground layer — the plot lays its own, and two terrain
    layers fight over the same cells — and skips its jigsaw markers and
    entities, which belong to the donor's life as a standalone building.
    """
    ox, oz = at
    x0, x1, z0, z1 = keep_ground_in or (0, -1, 0, -1)
    for (x, y, z), b in donor.solid_items():
        # His ground layer is dropped **outside** his walls, because the plot lays
        # its own — but kept inside them, because that is where his water, his
        # paths and the floor under his walls live. Dropping it everywhere and then
        # laying ground over the top drowned his ponds and left the lily pads
        # sitting on dirt.
        # His ground is dropped outside his walls — the plot lays its own — but
        # **water is kept wherever he dug it**: his ponds sit outside the wall
        # bbox, so dropping them there stranded the lily pads he floated on top.
        if y == 0 and b.short != "water" and not (x0 <= x <= x1 and z0 <= z <= z1):
            continue
        if b.short == "jigsaw":
            continue
        q = (x + ox, y, z + oz)
        if not (0 <= q[0] < dst.size[0] and 0 <= q[2] < dst.size[2]
                and q[1] < dst.size[1]):
            continue
        dst.set(q, b, donor.block_nbt.get((x, y, z)))


# ── breeds ──────────────────────────────────────────────────────────

@dataclass(frozen=True)
class Breed:
    """One animal, and what its yard has to provide for it.

    The three differ in more than the entity id, or all three files would be the
    same pen with a different mob standing in it: a pasture is grazed and open, a
    sty is churned to mud and cramped, a fold is dry stone and wool.
    """

    key: str                    # output name, also the folder name
    entity: str                 # entity id shipped in the NBT
    family: str                 # which of the author's house ladders to graft
    yard: Coord2                # the flank arm of the yard, w x d
    strip: int                  # depth of the working arm behind the house;
                                # 0 for a compact yard with no arm at all
    clip: int                   # how far the outer corners are cut back
    byre: int                   # depth along the house wall of the lean-to
    ground: Sequence[str] = GRAZED
    graze: bool = True          # tufts of grass left standing inside
    holding_pen: bool = False   # a small sorting pen for shearing
    wallow: bool = False        # a mud hollow with water in it
    boundary: str = "postrail"  # postrail | hurdle | boarded
    water: str = "pond"         # pond | dip | wallow
    byre_form: str = "lean"     # lean | gable | low
    milking: bool = False       # cauldron and barrel by the shed
    wool: bool = False          # wool bales in the loft, shearing bench
    note: str = ""


# Yard area is **not** the same for the three, because it is not the same in
# life. Cattle need by far the most ground per head and are kept at pasture;
# sheep are flock animals that graze wide but are folded tight at night, with a
# small holding pen to sort and shear them in; a pig sty is deliberately compact
# and its floor is churned to mud, because pigs root and wallow rather than
# graze. So: the same house, three different yards — 11x11, 9x10 and 7x8 for a
# top-tier herd of four.
COW = Breed(
    key="cow_pasture", entity="minecraft:cow", family="house_3",
    yard=(11, 11), strip=5, clip=2, byre=5,
    boundary="postrail", byre_form="lean", water="pond",
    ground=GRAZED, graze=True, milking=True,
    note="the widest yard, kept in grass, big trough and a milking corner")
PIG = Breed(
    key="pig_sty", entity="minecraft:pig", family="house",
    yard=(8, 10), strip=0, clip=1, byre=4,
    boundary="boarded", byre_form="low", water="wallow",
    ground=MIRE, graze=False, wallow=True,
    note="compact and churned to mud, a wallow, fed at the house door")
SHEEP = Breed(
    key="sheep_fold", entity="minecraft:sheep", family="house_2",
    yard=(9, 10), strip=5, clip=2, byre=4,
    boundary="hurdle", byre_form="gable", water="dip",
    ground=FOLD, graze=True, wool=True, holding_pen=True,
    note="dry-stone fold with a holding pen for shearing, wool store")

BREEDS: Tuple[Breed, ...] = (COW, PIG, SHEEP)


# ── the level ladder ────────────────────────────────────────────────

@dataclass(frozen=True)
class Tier:
    """One rung, shared by all three breeds so the set reads as one family."""

    key: str
    note: str
    crooked: bool = False       # the boundary is out of true: the poorest look
    piers: bool = False         # stone-pier fence instead of plain oak
    shelter: int = 0            # 0 none, 1 lean-to, 2 open shed, 3 walled,
                                # 4 pitched, 5 barn
    kerb: bool = False          # trough kerbed in stone rather than a puddle
    annex: bool = False         # a lower lean-to store against one gable
    weathered: bool = False     # mossy skirt and rubble spill at the pier feet
    planters: bool = False      # greenery on the pier caps, boxes on the byre
    stores: bool = False        # a hay stack, and the fold's wool store
    lantern: bool = False       # prestige fitting: lvl4 and up only
    rich: bool = False          # posts finished with a stair cap
    herd: int = 2               # animals shipped in the file


LADDER: Tuple[Tier, ...] = (
    Tier("base", "the family's plainest house, a crooked yard fence, a puddle", crooked=True, herd=2),
    Tier("lvl1", "next house rung, fence straightened, open byre off its wall", shelter=1, herd=2),
    Tier("lvl2", "fence framed on posts, kerbed trough and cauldron, hay rack", piers=True, shelter=2, kerb=True, herd=3),
    Tier("lvl3", "byre walled, muck heap, holding pen, worn paths",
         piers=True, shelter=3, kerb=True, weathered=True, herd=3),
    # Beds arrive in the donor at lvl4, which is where the JSON grants residents.
    Tier("lvl4", "the rung with beds (residents), byre plinth, lantern, planters",
         piers=True, shelter=4, kerb=True, weathered=True, planters=True,
         lantern=True, rich=True, herd=4),
    Tier("lvl5", "top house rung, byre at full length, deep eave, winter stores",
         piers=True, shelter=5, kerb=True, annex=True, weathered=True,
         planters=True, stores=True, lantern=True, rich=True, herd=4),
)


def box_height(tier: Tier) -> int:
    """Generous; `trim` cuts the declared box back to what got built."""
    return {0: 4, 1: 5, 2: 5, 3: 5, 4: 6, 5: 8}[tier.shelter]


# ── the octagonal enclosure ─────────────────────────────────────────

def octagon(x0: int, x1: int, z0: int, z1: int, clip: int) -> Set[Coord2]:
    """Cells of a rectangle with its four corners cut back by `clip`."""
    out: Set[Coord2] = set()
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if min(x - x0, x1 - x) + min(z - z0, z1 - z) >= clip:
                out.add((x, z))
    return out


def boundary(mask: Set[Coord2]) -> Set[Coord2]:
    """The ring: mask cells touching the outside, thickened at the corners.

    A cut corner produces cells that meet only diagonally, and two fence posts
    meeting at a corner do not close anything — a fence occupies its post and
    its bars, not its cell. So every diagonal step gets the one mask cell that
    is orthogonally adjacent to both of its ends, which is exactly the two-cell
    staircase the author fences his own corners with.
    """
    ring = {c for c in mask
            if any((c[0] + dx, c[1] + dz) not in mask for dx, dz in NEIGH4)}
    for (x, z) in sorted(ring):
        for dx, dz in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
            diag = (x + dx, z + dz)
            if diag not in ring:
                continue
            if (x + dx, z) in ring or (x, z + dz) in ring:
                continue        # already 4-connected through a shared cell
            for closer in ((x + dx, z), (x, z + dz)):
                if closer in mask:
                    ring.add(closer)
                    break
    return ring


def ring_walk(ring: Set[Coord2]) -> List[Coord2]:
    """The ring in the order you would walk it, not in row-major order.

    This matters for one reason: pier spacing. `every third cell` over a
    row-major sort scatters the masonry posts arbitrarily round the enclosure —
    two touching here, a gap of nine there — and the stone-pier fence rendered
    as rubble tipped along the boundary rather than as posts with rails between
    them. Spacing is only meaningful along the path.

    Traced with a direction preference, so the walk goes straight on where it
    can and only turns at a corner; the thickened corner cells otherwise offer
    two equally good next steps and the trace zig-zags.
    """
    if not ring:
        return []
    start = min(ring, key=lambda c: (c[1], c[0]))
    order = [start]
    seen = {start}
    heading = (1, 0)
    while True:
        x, z = order[-1]
        turns = [heading, (-heading[1], heading[0]), (heading[1], -heading[0]),
                 (-heading[0], -heading[1])]
        nxt = None
        for dx, dz in turns:
            q = (x + dx, z + dz)
            if q in ring and q not in seen:
                nxt, heading = q, (dx, dz)
                break
        if nxt is None:
            # Corners can leave a diagonal hop as the only way on.
            for dx, dz in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
                q = (x + dx, z + dz)
                if q in ring and q not in seen:
                    nxt, heading = q, (dx, dz)
                    break
        if nxt is None:
            break
        order.append(nxt)
        seen.add(nxt)
    # Anything the trace could not reach still has to be fenced.
    order.extend(sorted(ring - seen))
    return order


# ── the escape model ────────────────────────────────────────────────
#
# A farm animal **jumps a full block**. That one fact invalidates the obvious
# way to check a pen — "is the boundary ring solid at animal height" — because a
# cow does not need a hole in the fence. It needs a step. Anything one block
# high standing beside the fence is a mounting block: the animal hops onto it
# (rise 1.0, allowed), and from there the top of the fence is only half a block
# further up, so it perches on the rail and drops outside.
#
# Heights that matter, measured from the yard floor at y=1:
#   full block             top at +1.0   reachable by jumping
#   bottom slab / stair    top at +0.5   reachable by walking
#   fence / wall / gate    top at +1.5   NOT reachable from flat ground,
#                                        reachable from any full block beside it
#
# Cows are 1.4 tall and sheep 1.3, so two clear cells is the honest requirement;
# a pig at 0.9 could squeeze through one, which only makes this conservative.
MOB_JUMP = 1.0
RAIL_TOP = 1.5


def _mob_passable(b: Optional[BlockState]) -> bool:
    """Can an animal's body occupy this cell?

    `traverse.is_passable` answers that for a *player*, and for a gate and a
    wooden door the answer there is yes — they can be opened, so they are
    doorways rather than walls. An animal cannot open anything, and getting this
    wrong made the first run of the escape model report every pen as leaking
    through its own gate.
    """
    if b is None:
        return True
    n = b.short
    if n.endswith(("_fence_gate", "_door")):
        return False
    if n.endswith("_trapdoor"):
        return False
    return is_passable(b)


def _mob_surface(vox: Voxels, p: Coord) -> Optional[float]:
    """Height an animal's feet reach standing on the block at `p`.

    Differs from `traverse.surface` in exactly one place, and it is the place
    that matters: a railing is *perchable*. `traverse` returns None for fences
    because a player cannot step onto one without jumping, and the wall set is
    graded on no-jump routes — but an animal jumps, so for this model the top of
    a fence is a real surface at +1.5.
    """
    b = vox.get(p)
    if b is None:
        return None
    if b.short.endswith(("_fence", "_wall", "_fence_gate")):
        return p[1] + RAIL_TOP
    return surface(vox, p)


def escape_routes(vox: Voxels, yard: Sequence[Coord2],
                  mask: Set[Coord2]) -> List[Coord]:
    """Cells outside the pen an animal can reach from inside it.

    Empty means the pen holds. This replaced a flood fill that only looked at
    the boundary ring at y=1 and declared every pen sound: it could not see a
    hay bale parked against the fence, which is the single most common way a
    Minecraft pen leaks.
    """
    sx, sy, sz = vox.size

    def standable(p: Coord) -> Optional[float]:
        if not _mob_passable(vox.get(p)):
            return None
        if not _mob_passable(vox.get((p[0], p[1] + 1, p[2]))):
            return None            # a cow is 1.4 tall: one cell is not enough
        return _mob_surface(vox, (p[0], p[1] - 1, p[2]))

    cells: Dict[Coord, float] = {}
    for x in range(sx):
        for z in range(sz):
            for y in range(1, sy):
                h = standable((x, y, z))
                if h is not None:
                    cells[(x, y, z)] = h

    start = [(x, 1, z) for (x, z) in yard if (x, 1, z) in cells]
    seen = set(start)
    stack = list(start)
    out: List[Coord] = []
    while stack:
        p = stack.pop()
        for dx, dz in NEIGH4:
            for dy in (1, 0, -1, -2, -3, -4):
                q = (p[0] + dx, p[1] + dy, p[2] + dz)
                if q not in cells or q in seen:
                    continue
                if cells[q] - cells[p] > MOB_JUMP + 1e-6:
                    continue       # too high to jump: this is the fence working
                seen.add(q)
                if (q[0], q[2]) not in mask:
                    out.append(q)  # outside the enclosure — it got out
                stack.append(q)
                break
    return sorted(out)


def enclosed(vox: Voxels, inner: Sequence[Coord2], y: int = 1) -> List[Coord2]:
    """Cells from which an animal standing at `y` can walk out of the box.

    The pen's own test, and the reason to have one: a pen that looks right and
    leaks is worse than one that looks wrong, because the failure only shows up
    in a world, hours later, as cows in the town square.
    """
    sx, _sy, sz = vox.size
    # Whatever an animal cannot walk through, decided by the movement model
    # rather than by a list of ids. A hand list is how an `andesite` pier came to
    # be treated as thin air: the pier was a perfectly good barrier in the game
    # and the check walked the herd straight through it.
    barrier = {(px, pz) for (px, py, pz), b in vox.solid_items()
               if py == y and not _mob_passable(b)}
    seen = set(c for c in inner if c not in barrier)
    stack = list(seen)
    leaks: List[Coord2] = []
    while stack:
        x, z = stack.pop()
        if x <= 0 or z <= 0 or x >= sx - 1 or z >= sz - 1:
            leaks.append((x, z))
            continue
        for dx, dz in NEIGH4:
            q = (x + dx, z + dz)
            if q in seen or q in barrier:
                continue
            seen.add(q)
            stack.append(q)
    return sorted(set(leaks))


# ── derived block properties ────────────────────────────────────────

def reconnect(vox: Voxels) -> int:
    """Recompute fence, wall, pane and gate connection props from the grid.

    Every one of these is *derived* rather than chosen, so nothing here invents
    a block state: a fence with `north=false` beside another fence reads as a
    line of loose stumps, and the mod's builder places blocks one at a time, so
    it cannot be relied on to fix the shapes afterwards.
    """
    snapshot = dict(vox.grid)
    changed = 0

    def at(p: Coord) -> Optional[BlockState]:
        return snapshot.get(p)

    def links(p: Coord, direction: str, family: str) -> bool:
        """Would this rail connect that way in the game?

        `family` is "rail" for fences, panes and bars, or "wall" for `*_wall`
        blocks — they do **not** connect to each other, which the corpus is
        unambiguous about.
        """
        dx, dz = VEC[direction]
        nb = at((p[0] + dx, p[1], p[2] + dz))
        if nb is None:
            return False
        n = nb.short
        if n in STURDY:
            return True
        if n.endswith("_fence_gate"):
            # A gate joins the run it stands in: its facing is across the run.
            return AXIS_OF[nb.get("facing", "north")] != AXIS_OF[direction]
        if family == "rail":
            return n.endswith(("_fence", "_pane", "_bars"))
        return n.endswith("_wall")

    for pos, b in list(vox.solid_items()):
        n = b.short
        if n.endswith(("_fence", "_pane", "_bars")):
            props = {d: ("true" if links(pos, d, "rail") else "false")
                     for d in ("north", "south", "east", "west")}
            new = b.with_props(**props)
        elif n.endswith("_wall"):
            sides = {d: links(pos, d, "wall")
                     for d in ("north", "south", "east", "west")}
            above = vox.occupied((pos[0], pos[1] + 1, pos[2]))
            straight = (sides["north"] and sides["south"]
                        and not sides["east"] and not sides["west"]) or \
                       (sides["east"] and sides["west"]
                        and not sides["north"] and not sides["south"])
            new = b.with_props(
                up="true" if (above or not straight) else "false",
                **{d: ("low" if v else "none") for d, v in sides.items()})
        elif n.endswith("_fence_gate"):
            facing = b.get("facing", "north")
            flank = ("west", "east") if AXIS_OF[facing] == "z" else ("north", "south")
            in_wall = any(
                (at((pos[0] + VEC[d][0], pos[1], pos[2] + VEC[d][1])) or
                 state("air")).short.endswith("_wall") for d in flank)
            new = b.with_props(in_wall="true" if in_wall else "false")
        else:
            continue
        if new != b:
            vox.set(pos, new, vox.block_nbt.get(pos))
            changed += 1
    return changed


# ── ground ──────────────────────────────────────────────────────────

def _ground_state(name: str) -> BlockState:
    return state(name, snowy="false") if name == "grass_block" else state(name)


def _patch_field(cells: Sequence[Coord2], mix: Sequence[str],
                 rng: random.Random, size: int = 2,
                 coherence: float = 0.75) -> Dict[Coord2, str]:
    """Sample `mix` over `cells` in patches rather than per cell.

    A per-cell coin flip over a five-block mix produces a chequerboard, which is
    what the first pass rendered as: no cell agreed with its neighbour anywhere
    on the plot. Ground in the corpus reads as *patches* — a few cells of the
    same material together, with the odd stray. So the material is chosen per
    `size`-cell block and only re-rolled for a minority of cells inside it.
    """
    patch: Dict[Coord2, str] = {}
    out: Dict[Coord2, str] = {}
    for (x, z) in cells:
        key = (x // size, z // size)
        if key not in patch:
            patch[key] = mix[rng.randrange(len(mix))]
        out[(x, z)] = (patch[key] if rng.random() < coherence
                       else mix[rng.randrange(len(mix))])
    return out


def lay_ground(vox: Voxels, breed: Breed, mask: Set[Coord2], ring: Set[Coord2],
               wear: Sequence[Coord2], rng: random.Random,
               herd: int = 2, keep: Optional[Set[Coord2]] = None) -> None:
    """Grass over the plot, the yard mix inside the fence, wear where they stand.

    `wear` is where the animals and the farmer actually are — the gate, the
    trough, the front of the shelter. Wear spreads out from those and fades,
    which is why it has to be passed in rather than scattered: worn ground in a
    ring round the fence and nowhere else is a texture swap, not a farmyard.
    """
    sx, _sy, sz = vox.size
    plot = [(x, z) for x in range(sx) for z in range(sz)]
    # The apron is not a lawn. A uniform green band one cell wide round the pen
    # reads as a tray the build is sitting on — measured against the author's
    # fields, whose ground patches run right out to the box edge.
    outside = _patch_field([c for c in plot if c not in mask],
                           ("grass_block",) * 4 + ("coarse_dirt", "dirt"), rng,
                           coherence=0.7)
    inside = _patch_field(sorted(mask), breed.ground, rng)
    worn = _patch_field(sorted(mask), TRODDEN, rng)

    for (x, z) in plot:
        if keep and (x, z) in keep:
            continue          # the donor's own ground, inside his walls
        here = vox.get((x, 0, z))
        if here is not None and here.short == "water":
            continue          # and his water, wherever he dug it
        if (x, z) not in mask:
            vox.set((x, 0, z), _ground_state(outside[(x, z)]))
            continue
        # Distance to the nearest place something stands, in cells.
        near = min((abs(x - w[0]) + abs(z - w[1]) for w in wear), default=99)
        # Tight radius on purpose. A first pass used 1.0/0.85/0.5/0.25 with a
        # 0.7 floor along the fence, and between the gate, the trough and the
        # whole shed frontage that covered the yard — every pen rendered as a
        # mud pit with a green border. The author's `cow_field` keeps roughly
        # half its interior in grass.
        # More stock, more wear: the yard of a four-head farm is not the yard
        # of a two-head one, and this is the cheapest way for the ground to say
        # which rung it is on.
        heavy = 1.0 + 0.12 * (herd - 2)
        p_worn = min(1.0, {0: 0.9, 1: 0.5, 2: 0.2}.get(near, 0.05) * heavy)
        if (x, z) in ring:
            p_worn = max(p_worn, 0.4)       # trodden along the fence line
        name = worn[(x, z)] if rng.random() < p_worn else inside[(x, z)]
        vox.set((x, 0, z), _ground_state(name))

    # Spill outside the gate, and heavier on one flank than the other: the
    # author's ground is asymmetric and evenly spread wear reads as machine-made.
    heavy = rng.choice(("west", "east"))
    for (x, z) in sorted(ring):
        for dx, dz in NEIGH4:
            q = (x + dx, z + dz)
            if q in mask or not (0 <= q[0] < sx and 0 <= q[1] < sz):
                continue
            bias = 0.45 if (heavy == "west") == (q[0] < sx // 2) else 0.1
            if rng.random() < bias:
                vox.set((q[0], 0, q[1]),
                        _ground_state(TRODDEN[rng.randrange(len(TRODDEN))]))


def water_hollow(vox: Voxels, cells: Sequence[Coord2], kerb: bool,
                 stone: Stone, rng: random.Random) -> None:
    """A drinking trough dug into the terrain layer.

    At y=0, never y=1. The author does this in `cow_field` and `pig_field`, and
    the reason is mechanical rather than stylistic: water at pen level has
    nothing holding it and floods the yard the moment the structure is placed.
    `kerb` sets the rim in stone slabs, flush with the ground — a built trough
    rather than a puddle.
    """
    for (x, z) in cells:
        vox.set((x, 0, z), state("water", level="0"))
    if not kerb:
        return
    rim: Set[Coord2] = set()
    for (x, z) in cells:
        for dx, dz in NEIGH4:
            q = (x + dx, z + dz)
            if q not in cells:
                rim.add(q)
    for (x, z) in sorted(rim):
        if vox.get((x, 0, z)) is None:
            continue
        # Not every rim cell: a complete kerb is a perfect rectangle, which is
        # the one thing the corpus profile says the author never builds.
        if rng.random() < 0.75:
            vox.set((x, 0, z), stone.slab("top"))


def dashed_path(vox: Voxels, start: Coord2, direction: str, length: int,
                rng: random.Random) -> None:
    """Wear from the gate inward — patches with gaps, never a ribbon (`389f`)."""
    dx, dz = VEC[direction]
    x, z = start
    for i in range(length):
        x, z = x + dx, z + dz
        if vox.get((x, 0, z)) is None:
            return
        if rng.random() < 0.75:
            vox.set((x, 0, z), state("dirt_path"))
        if rng.random() < 0.35:
            side = rng.choice(NEIGH4)
            q = (x + side[0], z + side[1])
            if vox.get((q[0], 0, q[1])) is not None:
                vox.set((q[0], 0, q[1]), state("coarse_dirt"))


# ── the boundary ────────────────────────────────────────────────────

# How the boundary is built at each rung, per grammar: how often a masonry pier
# stands, and what fills the bays between them. This is the **wholesale** part of
# the ladder — swapping sixty blocks of boundary from rung to rung is what makes
# one level read as a different farm from the last, where a handful of extra props
# does not. Measured: level-to-level similarity of built content sat at 0.90 with
# a fixed boundary, against 0.79 across the author's own house ladder.
#
#   timber    the pasture — oak rails, masonry arriving late and sparsely
#   drystone  the fold — stone from the moment it can be afforded, ending as a
#             continuous dry-stone wall between close-set piers
#   rail_low  the sty — mostly boarded rails, barely any stone at all
BOUNDARY_LADDER = {
    # (post spacing, infill) per rung. **No stone anywhere in the run.** The
    # user's ruling, and it is the realistic one: a farmer with a better year buys
    # straighter timber, more posts, boards and a proper gate — he does not rebuild
    # his pasture fence in masonry. Stone stays where stone belongs on a farm: the
    # plinth under the byre, the kerb of a trough, the basin of a sheep dip.
    #
    #   rail    `oak_fence` — see-over post and rail
    #   double  two courses of it, a hurdle you cannot lean over
    #   panel   `oak_planks` boarded with a rail on top: a solid pen wall
    #
    # post-and-rail  cattle: airy, heavy posts, never boarded — they need to see
    # hurdle         sheep: light and close-set, doubled as the fold gets kept
    # boarded        pigs: they push and root, so boards early
    "postrail": ((0, "rail"), (5, "rail"), (4, "rail"), (4, "rail"),
                 (3, "double"), (3, "double")),
    "hurdle":   ((0, "rail"), (4, "rail"), (3, "double"), (3, "double"),
                 (2, "double"), (2, "double")),
    "boarded":  ((0, "rail"), (5, "rail"), (5, "rail"), (4, "panel"),
                 (3, "panel"), (3, "panel")),
}


def fence_ring(vox: Voxels, tier: Tier, breed: Breed, ring: Sequence[Coord2],
               skip: Set[Coord2], pal: Palette, rng: random.Random) -> None:
    """The enclosure, in **this breed's** grammar.

    Three of them, because three yards built the same way are one yard three
    times — measured at 0.88 cosine similarity between the breeds against 0.67
    across the author's own three animal fields.

    * **timber** (the pasture) — oak rails on log posts, each post capped with a
      slab; masonry piers only once the tier has stone, and then sparsely.
    * **drystone** (the fold) — a low `cobblestone_wall` course between capped
      cobblestone piers from the very first tier that can afford stone. A fold is
      dry stone in life and it is the one legitimate use of a `*_wall` block:
      a railing you can see over but not cross.
    * **rail_low** (the sty) — oak rails with an `oak_trapdoor` course laid along
      the inside, which is the author's own pen railing in `pig_farm_lvl6..8`,
      and hardly any stone.
    """
    grammar = breed.boundary
    rung = LADDER.index(tier)
    post_every, infill = BOUNDARY_LADDER[grammar][rung]
    for i, (x, z) in enumerate(ring):
        if (x, z) in skip:
            continue
        if post_every and i % post_every == 0:
            _capped_post(vox, (x, z), pal)
            continue
        if tier.crooked and rng.random() < 0.12:
            _capped_post(vox, (x, z), pal)   # a post that rotted and was replaced
            continue
        if infill == "panel":
            # Boarded: a plank between the posts with a rail over it. The rail on
            # top is not decoration — a bare full block in a boundary is a step,
            # and the animals leave over it.
            vox.set((x, 1, z), pal.wood.planks)
            vox.set((x, 2, z), pal.wood.fence())
            continue
        vox.set((x, 1, z), pal.wood.fence())
        if infill == "double":
            vox.set((x, 2, z), pal.wood.fence())


def _capped_post(vox: Voxels, cell: Coord2, pal: Palette) -> None:
    """A log post in the boundary, with a **slab** cap on top of it.

    The cap is not decoration. A bare full block in a fence line is a mounting
    block: an animal jumps a full block, hops onto the post and walks out over the
    rail beside it — the escape route the jump-aware check found in every pen. A
    lone fence on top of a post connects on no side and renders as a stub, which
    the author does nowhere in 115 files, so it is a slab: a capped post, and its
    top at +1.5 cannot be reached from the ground either.
    """
    x, z = cell
    vox.set((x, 1, z), state("oak_log", axis="y"))
    # No cap where the donor already has something two cells up: the slab would sit
    # directly under his rail, and a rail over a bottom slab is something he does
    # **nowhere** in 121 files. It is also unnecessary — an animal cannot stand on
    # the post if the cell above its head is blocked, which is the whole point of
    # the cap.
    if vox.occupied((x, 3, z)):
        # And take out the cap this post inherited from the rung below, where the
        # donor had nothing overhead yet. Growing on top of the previous rung means
        # its decisions arrive with it, valid or not.
        below = vox.get((x, 2, z))
        if below is not None and below.short.endswith("_slab"):
            vox.set((x, 2, z), None)
        return
    vox.set((x, 2, z), pal.wood.slab("bottom"))


def rubble(vox: Voxels, ring: Sequence[Coord2], mask: Set[Coord2],
           pal: Palette, rng: random.Random) -> int:
    """Loose stone at the foot of a masonry run (`2b41`).

    Ground-level only, so it is never a step: the escape model would flag a full
    block at pen level beside the rail, and rightly.
    """
    spilled = 0
    for (x, z) in ring:
        b = vox.get((x, 1, z))
        if b is None or b.short not in ("cobblestone", "mossy_cobblestone",
                                        "andesite"):
            continue
        for dx, dz in NEIGH4:
            q = (x + dx, z + dz)
            if q in mask and rng.random() < 0.3:
                vox.set((q[0], 0, q[1]), state("cobblestone" if rng.random() < 0.6
                                               else "mossy_cobblestone"))
                spilled += 1
    return spilled


def rail_course(vox: Voxels, ring: Sequence[Coord2], mask: Set[Coord2],
                pal: Palette, rng: random.Random,
                skip: Optional[Set[Coord2]] = None) -> int:
    """Trapdoors laid along the inside of the rail — the sty's own railing.

    `pig_farm_lvl6..8` runs oak trapdoors horizontally as pen railing, up to five
    in a line, which is why the critic tolerates a run of them. Placed on the
    yard side of a rail so it reads as a boarded pen rather than open fence.
    """
    laid = 0
    for (x, z) in ring:
        if not vox.occupied((x, 1, z)):
            continue
        for dx, dz in NEIGH4:
            q = (x + dx, z + dz)
            if q not in mask or vox.occupied((q[0], 1, q[1])):
                continue
            if skip and q in skip:
                continue          # never board over the shelter's own floor
            if rng.random() < 0.45:
                facing = {(1, 0): "west", (-1, 0): "east",
                          (0, 1): "north", (0, -1): "south"}[(dx, dz)]
                vox.set((q[0], 1, q[1]), pal.wood.trapdoor(facing, "top"))
                laid += 1
            break
    return laid


def close_diagonals(vox: Voxels, mask: Set[Coord2], pal: Palette) -> int:
    """Fill the corner cell where two rails meet only diagonally.

    A fence connects to nothing diagonally, so two rails touching at a corner
    read as a gap in the run — and the escape model never complains, because
    nothing can walk through a corner either. That is why this needs its own
    check: measured over the author, his worst build has 4 such steps and the
    median is 0, while the clipped corners here produced 12.

    The filling cell is always chosen **inside** the yard, so closing the line
    never eats into the apron.
    """
    fixed = 0
    for _ in range(3):                  # closing one step can create another
        rails = {(p[0], p[2]) for p, b in vox.solid_items()
                 if p[1] == 1 and b.short.endswith(("_fence", "_fence_gate",
                                                    "_wall"))}
        barrier = {(p[0], p[2]) for p, b in vox.solid_items()
                   if p[1] == 1 and (b.short in FULL_BLOCKS
                                     or b.short.endswith(("_fence", "_wall",
                                                          "_fence_gate",
                                                          "_door")))}
        gaps = []
        for (x, z) in sorted(rails):
            for dx, dz in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
                q = (x + dx, z + dz)
                if q not in rails:
                    continue
                if (x + dx, z) in barrier or (x, z + dz) in barrier:
                    continue
                # Prefer a cell inside the yard; fall back to the apron. At a
                # clipped corner the closing cell is the corner that was cut
                # away, so an inside-only rule left those steps open — which is
                # where the remaining gaps in the run were.
                # A tuft of grass or a leaf does not close a run, and it must
                # not block the closing either: the pier-foot planting landed in
                # exactly these cells and left fourteen corners open again.
                def free(c: Coord2) -> bool:
                    b = vox.get((c[0], 1, c[1]))
                    return b is None or b.short in SOFT_DECOR

                inside = [c for c in ((x + dx, z), (x, z + dz))
                          if c in mask and free(c)]
                outside = [c for c in ((x + dx, z), (x, z + dz))
                           if c not in mask and free(c)
                           and vox.occupied((c[0], 0, c[1]))]
                if inside or outside:
                    gaps.append((inside or outside)[0])
                    break
        if not gaps:
            break
        for cell in gaps:
            vox.set((cell[0], 1, cell[1]), pal.wood.fence())
            fixed += 1
    return fixed


def gate_run(ring: Sequence[Coord2], z_front: int) -> List[Coord2]:
    """Ring cells along the front edge, in x order — candidates for the gate."""
    return sorted(c for c in ring if c[1] == z_front)


def hang_gate(vox: Voxels, cell: Coord2, pal: Palette, tier: Tier) -> None:
    """A gate in the front run, with log jambs so it reads as an opening."""
    x, z = cell
    vox.set((x, 1, z), state("oak_fence_gate", facing="north", in_wall="false",
                             open="false", powered="false"))
    for jx in (x - 1, x + 1):
        if vox.occupied((jx, 1, z)):
            vox.set((jx, 1, z), state("oak_log", axis="y"))
            # Capped for the same reason every other post in the run is: an
            # uncapped jamb is a step out of the pen, right beside the gate.
            # Timber, like the rest of the run: a stone-capped gatepost is the
            # first step toward a masonry fence, and a farm never takes it.
            vox.set((jx, 2, z), pal.wood.slab("bottom"))


# ── the shelter ─────────────────────────────────────────────────────

@dataclass
class Shed:
    """Where the shelter sits, so the props know where the walls are."""

    x0: int
    x1: int
    z0: int
    z1: int
    open_side: str = "south"    # the face left open to the yard

    @property
    def cells(self) -> List[Coord2]:
        return [(x, z) for x in range(self.x0, self.x1 + 1)
                for z in range(self.z0, self.z1 + 1)]

    def corners(self) -> List[Coord2]:
        return [(self.x0, self.z0), (self.x1, self.z0),
                (self.x0, self.z1), (self.x1, self.z1)]


def place_shed(breed: Breed, tier: Tier, mask: Set[Coord2], ring: Set[Coord2],
               z_back: int, rng: random.Random) -> Optional[Shed]:
    """Back the shelter onto the rear run, offset to one end.

    Offset deliberately: a shelter centred on the back wall makes the whole pen
    mirror-symmetric about its own axis, and that single decision is enough to
    push a build past the corpus ceiling on its own.
    """
    if tier.shelter == 0:
        return None
    # `breed.shed` is the barn at lvl5. Everything below it is shallower, so the
    # silhouette changes from tier to tier instead of the same box growing a
    # different hat.
    row_len = len(sorted(x for (x, z) in mask if z == z_back))
    if tier.shelter == 1:
        w, d = 3, 2
    elif tier.shelter == 5:
        w, d = row_len - 1, 4
    else:
        # As long as the back run allows. The author's own farm building is a
        # long low volume under one broad roof plane — a byre four cells wide
        # and three deep under a two-course pitch is a hut, and that is what the
        # first version of this set rendered as at every tier.
        w, d = row_len - 1, 3
    row = sorted(x for (x, z) in mask if z == z_back)
    if len(row) < 3:
        return None
    # Clamp rather than give up. The back run is shortened by the clipped
    # corners, and returning None here meant the tier that asks for a barn got a
    # pen with no shelter at all — and then failed its own loft check.
    w = min(w, len(row))
    lo, hi = row[0], row[-1]
    # Two candidate anchors, both off centre; pick one and stay with it.
    left = lo + 1
    right = hi - w
    x0 = rng.choice((left, right))
    x0 = max(lo, min(right if right > lo else lo, x0))
    return Shed(x0, x0 + w - 1, z_back, z_back + d - 1, open_side="south")


def _frame(vox: Voxels, shed: Shed, pal: Palette, top: int) -> None:
    """Corner posts and the beam course they carry (`440b`, `078d`).

    Oak used as oak: `oak_log` stands, `stripped_oak_log` lies. The beam course
    is what a jetty or an eave springs from, and it is also what stops the roof
    from reading as a lid dropped on four sticks.
    """
    for (x, z) in shed.corners():
        for y in range(1, top + 1):
            vox.set((x, y, z), pal.wood.post)
    for x in range(shed.x0, shed.x1 + 1):
        for z in (shed.z0, shed.z1):
            if (x, z) not in shed.corners():
                vox.set((x, top, z), pal.wood.beam("x"))
    for z in range(shed.z0 + 1, shed.z1):
        for x in (shed.x0, shed.x1):
            vox.set((x, top, z), pal.wood.beam("z"))


def _walls(vox: Voxels, shed: Shed, pal: Palette, top: int,
           rng: random.Random) -> None:
    """A byre wall: rail below, panel above, posts framing the bays (`440b`).

    Not a plank box. The lower course is an `oak_fence` rail — you can see the
    stock over it and it still holds them — and only the course above is
    plank infill between posts. Filling every closed cell with plank from the
    ground up is what made the first pass read as a garden shed, which is
    exactly the note the wall set was pulled up on: planks are panel infill,
    never a building material.

    The yard face stays open: it is the animals' way in.
    """
    closed = [(x, z) for (x, z) in shed.cells
              if (x in (shed.x0, shed.x1) or z in (shed.z0, shed.z1))
              and z != shed.z1]
    corners = set(shed.corners())
    shutter = rng.choice([c for c in closed if c[1] == shed.z0]) if closed else None
    for (x, z) in closed:
        if (x, z) in corners:
            continue
        # An intermediate post every other bay, so the frame reads.
        if (x + z) % 3 == 0:
            for y in range(1, top):
                vox.set((x, y, z), pal.wood.post)
            continue
        # Gable walls get a **stone plinth**, the back wall a rail. That is the
        # author's own stone-base / timber-upper grammar out of `house_3_lvl5`,
        # and it is also what stops the shelter reading as one brown mass: an
        # all-oak shed at this size renders as a solid block whatever the
        # framing does.
        if x in (shed.x0, shed.x1):
            vox.set((x, 1, z), state(pal.stone.main if rng.random() < 0.75
                                     else pal.stone.weathered))
        else:
            vox.set((x, 1, z), pal.wood.fence())
        for y in range(2, top):
            if (x, z) == shutter and y == 2:
                vox.set((x, y, z), pal.wood.trapdoor("south", "bottom"))
            else:
                vox.set((x, y, z), pal.wood.planks)


def _annex(vox: Voxels, shed: Shed, pal: Palette, rng: random.Random) -> int:
    """A store lean-to against one gable, under a lower roof.

    This is where the barn's door goes. Not in the yard face — that face is how
    the stock gets in, and doors across it would dam the shelter the way the
    first gatehouse dammed its own passage.
    """
    east = rng.random() < 0.5
    x0 = shed.x1 + 1 if east else shed.x0 - 2
    x1 = x0 + 1
    z0, z1 = shed.z0 + 1, shed.z1
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if not vox.occupied((x, 0, z)):
                return 0                     # off the plot: skip the annex
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            if edge and not (x == (x0 if east else x1) and z == z1):
                for y in (1, 2):
                    vox.set((x, y, z), pal.wood.planks if y == 2
                            else state(pal.stone.main))
        # A lower roof plane: one course under the barn eave, so the two planes
        # read as two volumes.
        for z in range(z0, z1 + 1):
            vox.set((x, 3, z), pal.wood.slab("bottom"))
    # Corner posts, and the doorway into the yard end of the store.
    for (cx, cz) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        vox.set((cx, 1, cz), pal.wood.post)
        vox.set((cx, 2, cz), pal.wood.post)
    dx = x0 if east else x1
    vox.set((dx, 1, z1), pal.wood.door("north", "lower", "left"))
    vox.set((dx, 2, z1), pal.wood.door("north", "upper", "left"))
    return 3


def feeder(vox: Voxels, cells: Sequence[Coord2], pal: Palette,
           rng: random.Random) -> None:
    """A hay rack: bales with a trapdoor front, the author's own pen railing.

    `pig_farm_lvl6..8` runs oak trapdoors horizontally as pen railing — the one
    place the critic tolerates a run of identical attached decorations, because
    it is the author's habit, not a stretch artefact.
    """
    for i, (x, z) in enumerate(cells):
        vox.set((x, 1, z), state("hay_block", axis="x" if i % 2 else "z"))
        front = (x, 1, z + 1)
        if not vox.occupied(front) and rng.random() < 0.7:
            vox.set(front, pal.wood.trapdoor("north", "bottom"))


def yard_props(vox: Voxels, breed: Breed, tier: Tier, shed: Optional[Shed],
               free: List[Coord2], pal: Palette, rng: random.Random,
               lane: Optional[Set[Coord2]] = None) -> None:
    """Working kit, gated by tier. A pen with nothing in it reads as a plot."""
    rng.shuffle(free)
    take = iter(free)

    def spot() -> Optional[Coord2]:
        for c in take:
            if not vox.occupied((c[0], 1, c[1])):
                return c
        return None

    if tier.kerb:
        # A filled cauldron by the shelter: the one water container that reads as
        # a trough from eye level. The dug hollow is the pond they drink from,
        # this is the one the farmer fills, and both occur in the corpus.
        c = spot()
        if c:
            vox.set((c[0], 1, c[1]), state("water_cauldron", level="3"))
    if breed.milking and tier.shelter >= 2:
        c = spot()
        if c:
            vox.set((c[0], 1, c[1]), state("cauldron"))
        c = spot()
        if c:
            vox.set((c[0], 1, c[1]), state("barrel", facing="north",
                                           open="false"))
    if breed.wallow and tier.shelter >= 2:
        c = spot()
        if c:
            vox.set((c[0], 1, c[1]), state("composter", level="0"))
    if tier.stores:
        # A stack of fodder for the winter, and the fold's wool store: the top
        # rung is when a farm has a surplus worth storing.
        for i in range(3):
            c = spot()
            if c:
                vox.set((c[0], 1, c[1]), state("hay_block",
                                               axis=("y", "x", "z")[i % 3]))
        if breed.wool:
            for _ in range(2):
                c = spot()
                if c:
                    vox.set((c[0], 1, c[1]), state("white_wool"))
    if tier.planters:
        # A pot by the yard gate and a box of greenery: the fittings a farm gets
        # once it is no longer scraping by (`078d`, `35962`).
        c = spot()
        if c:
            vox.set((c[0], 1, c[1]), state("flower_pot"))
    if breed.wool and tier.shelter >= 2:
        c = spot()
        if c:
            vox.set((c[0], 1, c[1]), state("crafting_table"))
        for _ in range(2 if tier.shelter >= 4 else 1):
            c = spot()
            if c:
                vox.set((c[0], 1, c[1]), state("white_wool"))
    if tier.shelter >= 3 and shed is not None:
        # Firewood stacked against the byre gable (`35962`): logs lying down —
        # and clear of the fence, because a log stack is a full block and the
        # escape model found animals standing on it to leave.
        z = shed.z1 + 1
        for i, x in enumerate(range(shed.x0, min(shed.x0 + 2, shed.x1))):
            if lane is not None and (x, z) in lane:
                continue
            if not vox.occupied((x, 1, z)):
                vox.set((x, 1, z), state("stripped_oak_log", axis="x"))
                if i == 0 and not vox.occupied((x, 2, z)):
                    vox.set((x, 2, z), state("oak_log", axis="x"))


def lighting(vox: Voxels, tier: Tier, ring: Sequence[Coord2],
             shed: Optional[Shed], pal: Palette, rng: random.Random) -> None:
    """A torch on a fence post early, a lantern under the roof from lvl4.

    The lantern is the prestige fitting and is gated exactly where banners are
    in the military set: lvl4 and up. It hangs **inside**, off the loft floor or
    a roof beam, because light belongs where the work happens — a lantern on a
    chain out in the open yard is the stage prop the wall set was pulled up on.
    """
    if tier.lantern and shed is not None:
        # Under cover: hang it from the first solid cell of the roof or loft
        # over the open half of the shed.
        for z in range(shed.z1, shed.z0 - 1, -1):
            for x in (shed.x0 + 1, shed.x1 - 1):
                for y in range(2, 6):
                    if vox.occupied((x, y, z)) or vox.occupied((x, y - 1, z)):
                        continue
                    if vox.occupied((x, y + 1, z)):
                        vox.set((x, y, z), state("lantern", hanging="true",
                                                 waterlogged="false"))
                        return
    posts = [c for c in ring if vox.occupied((c[0], 1, c[1]))
             and not vox.occupied((c[0], 2, c[1]))]
    if posts:
        x, z = posts[rng.randrange(len(posts))]
        vox.set((x, 2, z), state("torch"))


def planting(vox: Voxels, breed: Breed, mask: Set[Coord2],
             free: Sequence[Coord2], rng: random.Random) -> None:
    """Tufts inside, leaves clumped against one flank only.

    Asymmetric on purpose: the author's base vegetation is heaviest on one side
    of a build, and evenly distributed greenery is a generated-build tell.
    """
    if breed.graze:
        for (x, z) in free:
            if rng.random() < 0.18 and not vox.occupied((x, 1, z)):
                below = vox.get((x, 0, z))
                if below is not None and below.short == "grass_block":
                    vox.set((x, 1, z), state("short_grass"))
    sx, _sy, sz = vox.size
    flank = rng.choice(("west", "east"))
    beyond = [(x, z) for x in range(sx) for z in range(sz)
              if (x, z) not in mask]
    clumped = [c for c in beyond if (c[0] < sx // 2) == (flank == "west")]
    rng.shuffle(clumped)
    for (x, z) in clumped[: rng.choice((2, 3))]:
        if not vox.occupied((x, 1, z)):
            vox.set((x, 1, z), state("oak_leaves", distance="1",
                                     persistent="true", waterlogged="false"))
    # Tufts and the odd sapling outside the fence, the author's own habit — one
    # sapling apiece in `pig_field` and `sheep_field`.
    for (x, z) in beyond:
        below = vox.get((x, 0, z))
        if (not vox.occupied((x, 1, z)) and below is not None
                and below.short == "grass_block" and rng.random() < 0.22):
            vox.set((x, 1, z), state("short_grass"))
    spare = [c for c in beyond if not vox.occupied((c[0], 1, c[1]))
             and (vox.get((c[0], 0, c[1])) or state("air")).short == "grass_block"]
    if spare and rng.random() < 0.6:
        x, z = spare[rng.randrange(len(spare))]
        vox.set((x, 1, z), state("oak_sapling", stage="0"))


# ── animals ─────────────────────────────────────────────────────────

def animal(entity: str, cell: Coord2, y: int, rng: random.Random) -> Compound:
    """One entity entry, in the format `SchematicReader.readEntities` expects.

    It requires `pos` and `nbt`, and `nbt` must carry `id`. Deliberately minimal
    otherwise: `NewBuildAction` calls `entity.load(nbt)` then `moveTo`, so a
    stale `Pos` is harmless but a `UUID` is not — the author's fields each carry
    fixed entity UUIDs, so two copies of the same field try to add two entities
    with the same UUID.
    """
    x = cell[0] + 0.3 + rng.random() * 0.4
    z = cell[1] + 0.3 + rng.random() * 0.4
    nbt = Compound({
        "id": nbtlib.String(entity),
        "Pos": NbtList[nbtlib.Double]([nbtlib.Double(x), nbtlib.Double(float(y)),
                                      nbtlib.Double(z)]),
        "Rotation": NbtList[nbtlib.Float](
            [nbtlib.Float(rng.randrange(0, 360)), nbtlib.Float(0.0)]),
    })
    if entity.endswith("sheep"):
        nbt["Color"] = nbtlib.Byte(0)
        nbt["Sheared"] = nbtlib.Byte(0)
    return Compound({
        "nbt": nbt,
        "blockPos": NbtList[nbtlib.Int](
            [nbtlib.Int(cell[0]), nbtlib.Int(y), nbtlib.Int(cell[1])]),
        "pos": NbtList[nbtlib.Double]([nbtlib.Double(x), nbtlib.Double(float(y)),
                                      nbtlib.Double(z)]),
    })


def stock(vox: Voxels, breed: Breed, tier: Tier, free: Sequence[Coord2],
          rng: random.Random) -> None:
    """Put the herd in the pen, spread out and standing on solid ground."""
    spots = [c for c in free
             if not vox.occupied((c[0], 1, c[1]))
             and vox.occupied((c[0], 0, c[1]))
             and (vox.get((c[0], 0, c[1])) or state("air")).short != "water"]
    rng.shuffle(spots)
    # Spread them out if the yard allows, crowd them if it does not: a compact sty
    # at the top rung has a pond, a muck heap and its winter stores, and insisting
    # on three cells between animals left the fourth pig unplaced.
    chosen: List[Coord2] = []
    for gap in (3, 2, 1):
        chosen = []
        for c in spots:
            if all(abs(c[0] - o[0]) + abs(c[1] - o[1]) >= gap for o in chosen):
                chosen.append(c)
            if len(chosen) == tier.herd:
                break
        if len(chosen) == tier.herd:
            break
    for c in chosen:
        vox.entities.append(animal(breed.entity, c, 1, rng))


# ── water ───────────────────────────────────────────────────────────
#
# Two grammars, both measured off his files rather than invented:
#
#   **natural** — `lake.nbt`, `cow_field`: water at **y=0**, dug into the terrain
#   layer, rim of `coarse_dirt` / `rooted_dirt` / `grass_block`, and `lily_pad`
#   **at y=1 directly over water** (53 of his 54 pads sit exactly like that).
#   **built** — `fountain_place`: a masonry floor at y=0 and water at **y=1**
#   held in by a cobblestone rim in the same course. That is how a raised basin
#   is done here, and it is what a sheep dip is.
#
# He has no clay, gravel, sand or sugar cane anywhere in 121 files, so a bank is
# earth and a basin is cobblestone. Water is kept two cells clear of the boundary:
# a swimming animal floats a block higher than a standing one, and a pond against
# the fence would be a step nobody modelled.

POND_RIM = ("coarse_dirt", "rooted_dirt", "coarse_dirt", "dirt")


def pond(vox: Voxels, cells: Sequence[Coord2], size: int,
         rng: random.Random) -> List[Coord2]:
    """Kept for the standalone case; the ladder uses grow_blob + pond_cells."""
    """A dug pond: an irregular blob of water at ground level, banked in earth."""
    if not cells:
        return []
    anchor = cells[rng.randrange(len(cells))]
    blob = {anchor}
    frontier = [anchor]
    while len(blob) < size and frontier:
        x, z = frontier.pop(rng.randrange(len(frontier)))
        for dx, dz in NEIGH4:
            q = (x + dx, z + dz)
            if q in cells and q not in blob and rng.random() < 0.7:
                blob.add(q)
                frontier.append(q)
    for (x, z) in blob:
        vox.set((x, 0, z), state("water", level="0"))
        vox.set((x, 1, z), None)
    rim = {(x + dx, z + dz) for (x, z) in blob for dx, dz in NEIGH4} - blob
    for (x, z) in sorted(rim):
        if vox.get((x, 0, z)) is None:
            continue
        vox.set((x, 0, z), state(POND_RIM[rng.randrange(len(POND_RIM))]))
        if rng.random() < 0.3 and not vox.occupied((x, 1, z)):
            vox.set((x, 1, z), state("short_grass"))       # reeds at the bank
    for (x, z) in sorted(blob):
        if rng.random() < 0.3:
            vox.set((x, 1, z), state("lily_pad"))
    return sorted(blob)


def dip_pool(vox: Voxels, cells: Sequence[Coord2], pal: Palette,
             rng: random.Random) -> List[Coord2]:
    """A sheep dip: a stone basin you drive the flock through.

    Real husbandry — a fleece is washed before shearing — and built in his
    fountain grammar: masonry floor, water held at pen level by a cobblestone
    rim, and a stair at one end so the sheep walk down into it.
    """
    # Along either axis: restricting the basin to a north-south run left the
    # fold's crowded rungs with nowhere to put it at all.
    run = None
    pool = set(cells)
    for (x, z) in cells:
        for line in ([(x, z), (x, z + 1), (x, z + 2)],
                     [(x, z), (x + 1, z), (x + 2, z)]):
            if all(c in pool for c in line):
                run = line
                break
        if run:
            break
    if run is None:
        return []
    for (x, z) in run:
        vox.set((x, 0, z), state(pal.stone.main))
        vox.set((x, 1, z), state("water", level="0"))
    rim = {(x + dx, z + dz) for (x, z) in run for dx, dz in NEIGH4} - set(run)
    for (x, z) in sorted(rim):
        if vox.get((x, 0, z)) is None or vox.occupied((x, 1, z)):
            continue
        vox.set((x, 1, z), state(pal.stone.main if rng.random() < 0.7
                                 else pal.stone.weathered))
    # The way in: a stair down at the near end, facing along the run.
    x, z = run[-1]
    along_z = run[0][0] == run[-1][0]
    step = (x, z + 1) if along_z else (x + 1, z)
    if not vox.occupied((step[0], 1, step[1])):
        # Facing **away** from the basin, because facing is the tall side: the
        # flock walks down the low half into the water. Facing it the other way
        # builds a step up out of the pool.
        vox.set((step[0], 1, step[1]),
                pal.stone.stairs("south" if along_z else "east", "bottom"))
    return sorted(set(run) | rim)


def runnel(vox: Voxels, mask: Set[Coord2], source: Sequence[Coord2],
           target: Coord2, avoid: Set[Coord2], rng: random.Random) -> List[Coord2]:
    """A shallow watercourse **inside** the pasture, from the pond to the trough.

    Water belongs in the enclosure, not beside it: an earlier version ran a ditch
    down the plot margin, outside the fence, which is water the stock cannot reach
    and reads as a border drawn round the plot. This one crosses the yard the
    animals stand in, banked in earth like his `lake.nbt`, and is planked over
    where the worn path crosses it.

    It wanders: at each step it takes the axis that closes the most distance, but
    a third of the time it takes the other one instead.
    """
    if not source:
        return []
    x, z = min(source, key=lambda c: abs(c[0] - target[0]) + abs(c[1] - target[1]))
    cut: List[Coord2] = []
    for _ in range(14):
        if (x, z) == target:
            break
        dx = (1 if target[0] > x else -1) if target[0] != x else 0
        dz = (1 if target[1] > z else -1) if target[1] != z else 0
        if dx and dz:
            if rng.random() < 0.33:
                dx = 0
            else:
                dz = 0
        elif not dx and not dz:
            break
        nxt = (x + dx, z + dz)
        if nxt not in mask or nxt in avoid:
            break
        x, z = nxt
        if vox.get((x, 0, z)) is None:
            break
        vox.set((x, 0, z), state("water", level="0"))
        vox.set((x, 1, z), None)
        cut.append((x, z))
    for (cx, cz) in cut:
        for ddx, ddz in NEIGH4:
            q = (cx + ddx, cz + ddz)
            if q in cut or q not in mask or vox.get((q[0], 0, q[1])) is None:
                continue
            vox.set((q[0], 0, q[1]),
                    state(POND_RIM[rng.randrange(len(POND_RIM))]))
            if rng.random() < 0.2 and not vox.occupied((q[0], 1, q[1])):
                vox.set((q[0], 1, q[1]), state("short_grass"))
    # A plank sill where the stock and the farmer cross it.
    if len(cut) >= 3:
        cross = cut[len(cut) // 2]
        vox.set((cross[0], 1, cross[1]), state("oak_slab", type="top",
                                               waterlogged="false"))
    return cut


# ── the composer ────────────────────────────────────────────────────

@dataclass
class Pen:
    """A finished piece plus the geometry its checks need.

    `enclosed()` needs the yard cells and `check_route` needs the gate, and
    neither can be recovered from the voxels afterwards without re-deriving the
    octagon — so the composer hands them back rather than being reverse
    engineered.
    """

    vox: Voxels
    breed: Breed
    tier: Tier
    yard: List[Coord2]
    mask: Set[Coord2]
    gate: Coord2
    shed: Optional[Shed]
    seed: int = 0
    house_at: Coord2 = (1, 1)
    # What the checked writer complained about while this rung was built. Empty is
    # the only acceptable value in a shipped file; the driver gates on it.
    faults: List[Fault] = field(default_factory=list)

    @property
    def name(self) -> str:
        return self.vox.name


def yard_region(wall_x: int, wall_north: int, yw: int, yd: int,
                hx0: int, clip: int, plot_z: int) -> Set[Coord2]:
    """The yard, wrapping the house on two sides — behind it and beside it.

    Both arms are derived from **where the house's walls actually are**:
    `wall_x` is its east wall column and `wall_north` its north wall row, both
    measured off the donor rather than taken from its box. Deriving the strip
    from the box origin instead put the mask at negative z — outside the plot
    entirely — and left the donor's own garden fence running parallel to the
    yard's, one cell apart, which is what "the fence is duplicated" was.

    The two **outer** corners of the flank are cut back. The corners that meet
    the house stay square, or the fence never meets the wall.
    """
    out: Set[Coord2] = set()
    flank_z0, flank_z1 = MARGIN, min(wall_north + yd - 1, plot_z - MARGIN - 1)
    for x in range(wall_x + 1, wall_x + yw + 1):
        for z in range(flank_z0, flank_z1 + 1):
            if (wall_x + yw - x) + min(z - flank_z0, flank_z1 - z) >= clip:
                out.add((x, z))
    for x in range(hx0, wall_x + 1):          # the working strip behind the house
        for z in range(MARGIN, wall_north):
            out.add((x, z))
    # A strip one usable cell wide is not a yard, it is a corridor between two
    # fences — which is exactly how a doubled fence reads. `Breed.strip` is sized
    # so that never happens, and the compact sty asks for no strip at all.
    return out


def open_ring(vox: Voxels, mask: Set[Coord2]) -> List[Coord2]:
    """Boundary cells that still need fencing.

    A cell whose only way out is through the farmhouse wall is already closed;
    fencing it would build a rail inside the building. Everything else gets the
    run.
    """
    def walled(q: Coord2) -> bool:
        """Two solid courses — a wall an animal can neither pass nor stand on.

        `occupied` is the wrong test and cost a debugging cycle: the donor
        plants tufts of grass along its own walls, so the cell beside the yard
        read as occupied, the run was left unfenced, and the animals walked out
        through the grass. One solid course is not enough either — an animal
        jumps a full block, so a single course with air above it is a step, not
        a wall.
        """
        return all(_mob_passable(vox.get((q[0], y, q[1]))) is False
                   for y in (1, 2))

    keep: List[Coord2] = []
    for c in sorted(boundary(mask)):
        outside = [(c[0] + dx, c[1] + dz) for dx, dz in NEIGH4
                   if (c[0] + dx, c[1] + dz) not in mask]
        if any(not walled(q) for q in outside):
            keep.append(c)
    return keep


def lean_to(vox: Voxels, shed: Shed, tier: Tier, pal: Palette,
            rng: random.Random) -> int:
    """A byre against the farmhouse wall, roofed off it — the longhouse form.

    The house carries the back side, so the roof needs no rear gable and the two
    volumes read as one farmstead. The pitch falls away from the house, which is
    why the course against the wall is the high one.
    """
    x0, x1 = shed.x0, shed.x1
    z0, z1 = shed.z0, shed.z1
    body = 3 if tier.shelter >= 4 else 2

    for z in range(z0, z1 + 1):
        if z in (z0, z1) or (z - z0) % 3 == 0:
            for y in range(1, body + 1):
                vox.set((x1, y, z), pal.wood.post)
        elif tier.shelter >= 4:
            vox.set((x1, 1, z), state(pal.stone.main if rng.random() < 0.75
                                      else pal.stone.weathered))
    if tier.shelter >= 3:
        for z in (z0, z1):
            for x in range(x0, x1):
                vox.set((x, 1, z), pal.wood.fence())
                for y in range(2, body + 1):
                    vox.set((x, y, z), pal.wood.planks)
        vox.set((x0 + 1 if x0 + 1 < x1 else x0, 2, rng.choice((z0, z1))),
                pal.wood.trapdoor("south", "bottom"))

    # **One continuous course**, all at the same height, falling away from the
    # house through the stairs' own shape. The earlier version stepped down a
    # whole block over a single cell, which left the cell under the step empty —
    # a notch you could see through into the byre — and finished with an eave slab
    # hanging in the air one cell past the posts. Both were what the user was
    # pointing at, and both come from doing roof arithmetic per column instead of
    # laying a plane.
    # **`facing` is the tall side.** Measured, not recalled: 648 of the author's
    # stair runs ascend toward their facing and none against it, and on the west
    # slope of his roofs the stairs face east — tall half inward, toward the ridge.
    # A lean-to's high side is the house wall on the **west**, so its stairs face
    # west. They faced east, which pitched every byre roof backwards: the tall half
    # sat at the eave and the roof climbed away from the house.
    roof_y = body + 1
    for z in range(z0, z1 + 1):
        for x in range(x0, x1 + 1):
            vox.set((x, roof_y, z), pal.wood.stairs("west", "bottom"))
        # The eave projects one cell past the post line and lands on a **beam
        # end** — `oak_log` lying proud of the frame, his own device (`1b95`,
        # `725f`). Measured: his roofs are never cantilevered more than two cells
        # from a supported column, and mostly one.
        if (z - z0) % 2 == 0:
            vox.set((x1 + 1, body, z), pal.wood.beam("x"))
        vox.set((x1 + 1, roof_y, z), pal.wood.slab("bottom"))
        # And close the join against the house, where the donor has not already.
        if not vox.occupied((x0 - 1, roof_y, z)):
            vox.set((x0 - 1, roof_y, z), pal.wood.slab("bottom"))
    return roof_y


def gable_byre(vox: Voxels, shed: Shed, tier: Tier, pal: Palette,
               rng: random.Random) -> int:
    """A free-standing shelter with a two-sided pitch — the fold's own building.

    Stone plinth, timber above, a stair course each side and a slab ridge. Two
    stair facings instead of one, which is half of what makes the author's roofs
    read as roofs rather than as ramps.
    """
    x0, x1, z0, z1 = shed.x0, shed.x1, shed.z0, shed.z1
    body = 2 if tier.shelter < 4 else 3
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            corner = x in (x0, x1)
            for y in range(1, body + 1):
                if corner:
                    vox.set((x, y, z), pal.wood.post)
                elif y == 1:
                    vox.set((x, y, z), state(pal.stone.main if rng.random() < 0.7
                                             else pal.stone.weathered))
                elif z == z0 or tier.shelter >= 3:
                    vox.set((x, y, z), pal.wood.planks)
    # The pitch: stairs facing in from both long sides, slab ridge between — and
    # the **gable triangle filled** at each end column. Left open, the two end
    # walls are a hole into the attic, which is the same defect as a stepped
    # lean-to leaving a notch: a roof plane is not a roof until its ends close.
    y = body + 1
    lo, hi = z0, z1
    while lo < hi:
        for x in range(x0, x1 + 1):
            vox.set((x, y, lo), pal.wood.stairs("south", "bottom"))
            vox.set((x, y, hi), pal.wood.stairs("north", "bottom"))
        for x in (x0, x1):
            for z in range(lo + 1, hi):
                if not vox.occupied((x, y, z)):
                    vox.set((x, y, z), pal.wood.planks)
        lo, hi = lo + 1, hi - 1
        y += 1
    if lo == hi:
        for x in range(x0, x1 + 1):
            vox.set((x, y, lo), pal.wood.slab("bottom"))
    return y


def low_sty(vox: Voxels, shed: Shed, tier: Tier, pal: Palette,
            rng: random.Random) -> int:
    """A sty: squat, boarded, flat-lidded — and with a floor left to stand on.

    Posts at the four corners only, stone plinth along the back, rails down the
    sides and boarded across the front, slab lid at y=3. The first version walled
    every perimeter cell of a two-wide box, which left no interior at all.

    It stays the lowest of the three forms on purpose: the byre's eave is at 4 and
    the fold's ridge at 5 or 6.
    """
    x0, x1, z0, z1 = shed.x0, shed.x1, shed.z0, shed.z1
    corners = {(x0, z0), (x1, z0), (x0, z1), (x1, z1)}
    for (cx, cz) in corners:
        for y in (1, 2):
            vox.set((cx, y, cz), pal.wood.post)
    for x in range(x0 + 1, x1):
        vox.set((x, 1, z0), state(pal.stone.main if rng.random() < 0.7
                                  else pal.stone.weathered))
        if tier.shelter >= 3:
            vox.set((x, 2, z0), pal.wood.planks)
        # Boarded front: the author's own pen railing used as a low wall.
        vox.set((x, 1, z1), pal.wood.trapdoor("north", "top"))
    for z in range(z0 + 1, z1):
        for x in (x0, x1):
            vox.set((x, 1, z), pal.wood.fence())
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            vox.set((x, 3, z), pal.wood.slab("bottom"))
    return 3


def shelter_for(breed: Breed, shed: Shed, tier: Tier, vox: Voxels,
                pal: Palette, rng: random.Random) -> int:
    """Dispatch on the breed's own shelter form, and add the top rung's annex.

    The annex was reachable only from a `shelter()` that nothing called any more,
    so the rung that promises cluster massing was quietly building without it —
    dead code claiming a device in its own note.
    """
    if breed.byre_form == "gable":
        top = gable_byre(vox, shed, tier, pal, rng)
    elif breed.byre_form == "low":
        top = low_sty(vox, shed, tier, pal, rng)
    else:
        top = lean_to(vox, shed, tier, pal, rng)
    if tier.annex:
        _annex(vox, shed, pal, rng)
    return top


def holding_pen(vox: Voxels, cells: Sequence[Coord2], pal: Palette,
                rng: random.Random) -> List[Coord2]:
    """A small pen inside the yard for sorting and shearing a flock.

    Husbandry rather than decoration: nobody shears a fold loose in the yard,
    you pen a few at a time. Its rail is 1.5 tall like the boundary and it keeps
    a cell clear of it, so it can never become a step out.
    """
    if len(cells) < 8:
        return []
    for anchor in cells:
        box = [(anchor[0] + dx, anchor[1] + dz)
               for dx in range(3) for dz in range(2)]
        if all(c in cells for c in box):
            break
    else:
        return []
    x0, z0 = anchor
    gate = (x0 + 1, z0 + 1)
    for (x, z) in box:
        if (x, z) == gate:
            continue
        vox.set((x, 1, z), pal.wood.fence())
    vox.set((gate[0], 1, gate[1]),
            state("oak_fence_gate", facing="north", in_wall="false",
                  open="true", powered="false"))
    return box


def dung_heap(vox: Voxels, cells: Sequence[Coord2],
              rng: random.Random) -> None:
    """The muck heap by the byre. Every working farmyard has one."""
    if not cells:
        return
    x, z = cells[0]
    vox.set((x, 0, z), state("packed_mud"))
    vox.set((x, 1, z), state("packed_mud"))
    for dx, dz in ((1, 0), (0, 1), (-1, 0)):
        q = (x + dx, z + dz)
        if q in cells and rng.random() < 0.6:
            vox.set((q[0], 0, q[1]), state("coarse_dirt"))


def clear_mounts(vox: Voxels, mask: Set[Coord2], keep: Set[Coord2],
                 ring: Optional[Set[Coord2]] = None) -> List[Coord2]:
    """Remove any full block left standing beside a rail. The last word on it.

    The clear lane is applied when props are placed, but `close_diagonals` runs
    after them and adds rails — so a muck heap that was two cells from the
    boundary can end up against a brand new one, and the animals were leaving
    over exactly that. Filtering at placement time cannot see rails that do not
    exist yet; this invariant can.

    Structure is safe by construction: a post, a plinth or a wall carries
    something in the cell above it, and only blocks with air above are a step.
    `keep` excludes the byre, whose hay rack is meant to sit against its own
    gable.
    """
    # **The whole boundary line**, not just its rails. A masonry pier is part of
    # the boundary and its slab cap sits at +1.5, so a full block beside a pier is
    # the same step as one beside a fence — the sheep dip's rim landed next to a
    # pier and the flock left over it, while a rails-only rule saw nothing.
    rails = set(ring) if ring else set()
    rails |= {(p[0], p[2]) for p, b in vox.solid_items()
              if p[1] == 1 and b.short.endswith(("_fence", "_fence_gate", "_wall"))}
    gone: List[Coord2] = []
    for (x, z) in sorted(mask):
        if (x, z) in keep:
            continue
        b = vox.get((x, 1, z))
        if b is None or b.short not in STURDY:
            continue
        if vox.occupied((x, 2, z)):
            continue
        if not any((x + dx, z + dz) in rails for dx, dz in NEIGH4):
            continue
        vox.set((x, 1, z), None)
        vox.set((x, 0, z), state("coarse_dirt"))   # a scuff where it stood
        gone.append((x, z))
    return gone


def breed_donors(breed: "Breed") -> Tuple[str, ...]:
    return HOUSE_LADDERS[breed.family]


def house_box(breed: "Breed") -> Coord2:
    """Wall footprint of the breed's family, as the widest and deepest rung.

    `house_2` is 13 wide at its base and 12 at every level above it, so the box
    has to be the maximum: the plot must be identical at every rung because
    `UpgradeAction` replaces the NBT at the same origin.
    """
    w = d = 0
    for name in breed_donors(breed):
        bx0, bx1, bz0, bz1 = house_bounds(donor_house(name))
        w = max(w, bx1 - bx0 + 1)
        d = max(d, donor_house(name).size[2])
    return (w, d)


def plot_depth(breed: "Breed") -> int:
    """Plot depth for a breed, taken over the **whole ladder**.

    Each donor level puts its walls in a slightly different row, so sizing the
    box from one level made the footprint change between levels — and
    `UpgradeAction` replaces the NBT at the same origin, so the footprint has to
    be identical at every rung. The author holds to that in all 98 of his
    buildings; here it takes a max over the ladder.
    """
    hd = house_box(breed)[1]
    worst = 0
    for name in breed_donors(breed):
        donor = donor_house(name)
        _bx0, _bx1, bz0, _bz1 = house_bounds(donor)
        house_z = max(MARGIN, MARGIN + breed.strip - bz0)
        wall_north = house_z + bz0
        worst = max(worst, max(house_z + hd, wall_north + breed.yard[1]) + MARGIN)
    return worst


def absorb_pockets(vox: Voxels, mask: Set[Coord2],
                   house: Tuple[int, int, int, int]) -> Set[Coord2]:
    """Add cells that are walled off from the apron into the yard.

    The donor's walls are not straight lines — its north face is a cell short in
    places — so a yard sized to the wall's *bounding* row leaves a pocket between
    the fence and the wall. Fencing that pocket produces two parallel runs a cell
    apart with a dead cell between them, which is what "the fence is duplicated"
    looked like on the shallow pig strip: six of them in one file.

    Nothing needs fencing if it cannot be reached from outside. So: flood the
    plot from its border over open ground, and whatever open cell the flood never
    reaches is inside the farmstead already. The yard takes it, the boundary stays
    a single line, and the yard follows the building's real silhouette.
    """
    sx, _sy, sz = vox.size
    hx0, hx1, hz0, hz1 = house
    # **His rooms are not pockets.** The inside of the grafted house is air at
    # pen level and unreachable from the apron, so a plain flood absorbed it into
    # the yard — and the fence run, the piers and the props were then built
    # through his living room: 14 cells of it in `cow_pasture_lvl2` alone.
    open_cell = {(x, z) for x in range(sx) for z in range(sz)
                 if _mob_passable(vox.get((x, 1, z))) and (x, z) not in mask
                 and not (hx0 <= x <= hx1 and hz0 <= z <= hz1)}
    border = [(x, z) for (x, z) in open_cell
              if x in (0, sx - 1) or z in (0, sz - 1)]
    seen = set(border)
    stack = list(border)
    while stack:
        x, z = stack.pop()
        for dx, dz in NEIGH4:
            q = (x + dx, z + dz)
            if q in open_cell and q not in seen:
                seen.add(q)
                stack.append(q)
    return mask | (open_cell - seen)


@dataclass
class Plan:
    """Every positional decision for a family, taken **once**.

    The old composer decided the gate, the trough, the byre box, the pond and every
    prop slot per rung, from a per-rung seed. The result was a set that rebuilt
    itself each level: measured against the author's ladders, 75% of one of his
    levels survives verbatim into the next, and only 48% of ours did — the devices
    all shifted by a cell or two. Planning once and letting the rungs *add* to a
    copy of the previous rung is what makes a ladder grow instead.

    Everything here is sized for the **top** rung, so a lower rung is always a
    prefix of it: the byre reaches its full length by lvl5 and never moves, the pond
    is dug from the same blob outward, props come off an ordered list.
    """

    breed: Breed
    seed: int
    sx: int
    sz: int
    wall_x: int
    house_at: Dict[int, Coord2]          # rung -> graft offset
    bounds: Dict[int, Tuple[int, int, int, int]]
    mask: Set[Coord2]
    inner: List[Coord2]
    ring: List[Coord2]
    walk: List[Coord2]
    gate: Coord2
    door: Coord2
    byre_box: Tuple[int, int, int, int]  # the lvl5 extent; lower rungs are shorter
    trough: List[Coord2]
    pond: List[Coord2]                   # ordered: rung k digs a prefix
    props: List[Coord2]                  # ordered slots, never reshuffled
    wear: List[Coord2]                   # ordered: wear spreads, never retreats
    house_ground: Set[Coord2]


def plan_farmstead(breed: Breed, seed: int = 0) -> Plan:
    """Lay out the farmstead once, for the whole ladder."""
    rng = random.Random(seed * 7919 + 13)
    hw, hd = house_box(breed)
    yw, yd = breed.yard
    donors = breed_donors(breed)
    wall_x = MARGIN + hw - 1
    strip = breed.strip

    bounds, house_at = {}, {}
    for rung, name in enumerate(donors):
        bx0, bx1, bz0, bz1 = house_bounds(donor_house(name))
        bounds[rung] = (bx0, bx1, bz0, bz1)
        house_at[rung] = (wall_x - bx1, max(MARGIN, MARGIN + strip - bz0))
    sx = MARGIN + hw + yw + MARGIN
    sz = plot_depth(breed)

    # The yard is derived from the **top** rung's house, so it is the same yard at
    # every rung: a mask that shifted with the donor was half the churn.
    top = len(donors) - 1
    tbx0, tbx1, tbz0, tbz1 = bounds[top]
    wall_north = max(house_at[r][1] + bounds[r][2] for r in range(len(donors)))
    mask = yard_region(wall_x, wall_north, yw, yd, house_at[top][0] + tbx0,
                       breed.clip, sz)
    # The house's own ground is the **union** over the ladder: every cell any rung's
    # house covers. Two wrong versions cost a cycle each — the top rung's footprint
    # alone left cells that were neither yard nor wall on the poorer rungs, and the
    # intersection put yard cells *inside* the bigger houses, so the herd walked
    # through his rooms and out the far side. The union is the only choice that
    # never overlaps a house at any rung and is still the same yard at every rung.
    per_rung = []
    for r in range(len(donors)):
        rbx0, rbx1, rbz0, rbz1 = bounds[r]
        per_rung.append({(house_at[r][0] + x, house_at[r][1] + z)
                         for x in range(rbx0, rbx1 + 1)
                         for z in range(rbz0, rbz1 + 1)})
    house_ground = set.union(*per_rung)
    mask -= house_ground
    rim = set(boundary(mask))
    inner = sorted(mask - rim)
    ring = sorted(rim)
    walk = ring_walk(rim)

    def in_house(c: Coord2) -> bool:
        return c in house_ground

    yz1 = max(c[1] for c in mask if c[0] > wall_x)
    front = sorted(c for c in mask
                   if c[1] == yz1 and c[0] > wall_x
                   and (c[0], c[1] + 1) not in mask
                   and not in_house((c[0], c[1] + 1)))
    if len(front) < 3:
        raise ValueError("yard has no street-facing run to hang a gate in")
    gate = front[min(len(front) - 2, 1 + rng.choice((0, 1, 2)))]
    ddx, ddz = house_door(donor_house(donors[top]))
    door = (house_at[top][0] + ddx, house_at[top][1] + ddz)

    # The byre at full length; a rung shorter is the same box with fewer rows.
    depth = 3 if breed.byre_form == "low" else 3
    blen = max(2, min(breed.byre + 1, yz1 - MARGIN - 1))
    bz0 = wall_north + 1
    bx0 = wall_x + 1 + (2 if breed.byre_form == "gable" else 0)
    byre_box = (bx0, bx0 + depth - 1, bz0, bz0 + blen - 1)

    byre_cells = {(x, z) for x in range(byre_box[0], byre_box[1] + 1)
                  for z in range(byre_box[2], byre_box[3] + 1)}
    open_cells = [c for c in inner if c not in byre_cells]
    if not open_cells:
        raise ValueError("yard has no open ground left")

    anchor = min(open_cells, key=lambda c: (abs(c[0] - (wall_x + yw)) + abs(c[1] - yz1)))
    trough = [anchor]
    for dx, dz in ((0, 1), (1, 0), (0, 2)):
        q = (anchor[0] + dx, anchor[1] + dz)
        if q in open_cells and len(trough) < 3:
            trough.append(q)

    lane = {(c[0] + dx, c[1] + dz) for c in ring for dx, dz in NEIGH4}
    setback = 2 if breed.water == "dip" else 1
    span = range(-setback, setback + 1)
    near_ring = {(c[0] + dx, c[1] + dz) for c in ring for dx in span for dz in span}
    deep = [c for c in open_cells if c not in near_ring and c not in trough]
    pond = grow_blob(deep, 13, rng) if deep else []

    slots = [c for c in open_cells
             if c not in lane and c not in trough and c not in pond]
    rng.shuffle(slots)

    wear = [gate, door] + trough + [(byre_box[1] + 1, z)
                                    for z in range(byre_box[2], byre_box[3] + 1)]
    spread = [c for c in inner if c not in wear]
    rng.shuffle(spread)
    wear += spread

    return Plan(breed=breed, seed=seed, sx=sx, sz=sz, wall_x=wall_x,
                house_at=house_at, bounds=bounds, mask=mask, inner=inner,
                ring=ring, walk=walk, gate=gate, door=door, byre_box=byre_box,
                trough=trough, pond=pond, props=slots, wear=wear,
                house_ground=house_ground)


def grow_blob(cells: Sequence[Coord2], size: int,
              rng: random.Random) -> List[Coord2]:
    """An irregular blob, in the order it grew — so a prefix is a smaller blob."""
    if not cells:
        return []
    pool = set(cells)
    anchor = cells[rng.randrange(len(cells))]
    order = [anchor]
    blob = {anchor}
    frontier = [anchor]
    while len(blob) < size and frontier:
        x, z = frontier.pop(rng.randrange(len(frontier)))
        for dx, dz in NEIGH4:
            q = (x + dx, z + dz)
            if q in pool and q not in blob and rng.random() < 0.7:
                blob.add(q)
                order.append(q)
                frontier.append(q)
    return order


def byre_for(plan: Plan, tier: Tier) -> Optional[Shed]:
    """This rung's byre: the planned box, shorter at the early rungs.

    It only ever grows, and it grows from the same corner, so the byre of rung N is
    a sub-box of rung N+1's — that is what keeps it out of the "changed" column.

    Each form has a floor size below which it has no interior at all: a sty walls
    its own perimeter, so under 3x3 there is nowhere to stand, and a gable needs
    three cells of depth to carry a ridge. `check_pen` refuses anything smaller,
    and rightly — a shelter you cannot enter is furniture.
    """
    if tier.shelter == 0:
        return None
    x0, x1, z0, z1 = plan.byre_box
    grow = {1: 3, 2: 4, 3: 5, 4: 6, 5: 99}[tier.shelter]
    z_end = min(z1, z0 + max(2, grow) - 1)
    if plan.breed.byre_form == "lean":
        width = 2 if tier.shelter < 4 else min(3, x1 - x0 + 1)
    else:
        width = 3 if tier.shelter < 5 else min(4, x1 - x0 + 1)
    z_end = max(z_end, z0 + (1 if plan.breed.byre_form == "lean"
                             and tier.shelter == 1 else 2))
    return Shed(x0, min(x1, x0 + width - 1), z0, min(z1, z_end))


def build_rung(plan: Plan, tier: Tier, prev: Optional[Voxels]) -> Pen:
    """One rung, built **on top of** the previous one.

    Only three things are allowed to change what is already there: the house is
    replaced by its next level, the boundary is upgraded in place, and the byre
    extends along its planned box. Everything else is addition.
    """
    breed = plan.breed
    rung = LADDER.index(tier)
    rng = random.Random(plan.seed * 7919 + 101 + rung)
    pal = Palette(stone=Stone(), wood=Timber())
    house = donor_house(breed_donors(breed)[rung])
    bx0, bx1, bz0, bz1 = plan.bounds[rung]
    at = plan.house_at[rung]
    height = max(box_height(tier), house.top_y() + 2)

    # Every write below goes through the fabric canvas: same `set`/`get`, but it
    # refuses the three things that are wrong no matter what the rest of the build
    # looks like — a cube on a bottom slab, a rail on one, a roof block bearing on
    # nothing — and tags each pass, so a complaint names the pass instead of a
    # coordinate to go hunting from.
    if prev is None:
        vox = Canvas(Voxels((plan.sx, height, plan.sz), {},
                            f"{breed.key}_{tier.key}"))
        with vox.device("ground"):
            lay_ground(vox, breed, plan.mask, set(plan.ring),
                       plan.wear[:6], rng, tier.herd, plan.house_ground)
    else:
        vox = Canvas(prev.copy(f"{breed.key}_{tier.key}"))
        vox.size = (plan.sx, max(height, prev.size[1]), plan.sz)
        vox.entities = []
        # The previous rung's house comes out whole; his next level replaces it.
        pbx0, pbx1, pbz0, pbz1 = plan.bounds[rung - 1]
        pat = plan.house_at[rung - 1]
        for x in range(pat[0] + pbx0 - 1, pat[0] + pbx1 + 2):
            for z in range(pat[1] + pbz0 - 1, pat[1] + pbz1 + 2):
                for y in range(1, vox.size[1]):
                    if (x, z) not in plan.mask:
                        vox.set((x, y, z), None)

    # Unchecked: this is his building, finished, and his chimney stands on his own
    # furnace. Still tagged, so if one of our devices later trips over one of his
    # cells the report says whose cell it was.
    with vox.device("graft " + house.name, checked=False):
        graft(vox, house, at, keep_ground_in=(bx0, bx1, bz0, bz1))
    for (x, y, z), b in list(vox.solid_items()):
        if y != 1 or (x, z) not in plan.mask:
            continue
        if b.short in ("oak_leaves", "oak_sapling") or \
                b.short.endswith(("_fence", "_fence_gate")):
            if (x, z) in plan.house_ground:
                vox.set((x, y, z), None)

    # Wear spreads with the herd; it never retreats, so this only ever adds.
    with vox.device("wear"):
        for c in plan.wear[:6 + 3 * rung]:
            if c in plan.mask and not vox.occupied((c[0], 1, c[1])):
                vox.set((c[0], 0, c[1]),
                        state(TRODDEN[rng.randrange(len(TRODDEN))]))

    # The plan's ring was measured against the **top** rung's house. A lower rung's
    # donor is smaller and leaves runs open that the plan does not know about, so the
    # boundary for this rung is the union: the planned run plus whatever this house
    # fails to close. It only ever adds, so the run stays stable up the ladder.
    absorbed = absorb_pockets(vox, plan.mask,
                              (at[0] + bx0, at[0] + bx1, at[1] + bz0, at[1] + bz1))
    extra = [c for c in open_ring(vox, absorbed) if c not in set(plan.ring)]
    walk = plan.walk + [c for c in ring_walk(set(extra)) if c not in set(plan.walk)]
    ring = sorted(set(plan.ring) | set(extra))
    with vox.device("boundary"):
        fence_ring(vox, tier, breed, walk, skip={plan.gate}, pal=pal, rng=rng)
        hang_gate(vox, plan.gate, pal, tier)

    byre = byre_for(plan, tier)
    byre_cells = set(byre.cells) if byre else set()
    if byre:
        # Clear the shelter's own volume first. This is the one place where growing
        # on top of the previous rung bites: last rung's roof is still sitting at
        # head height inside the box this rung wants to build in, and `check_pen`
        # rightly refused a byre nobody could stand up in. The author removes 8% of
        # a level for the same reason — you do take the old lean-to down to build
        # the byre.
        for (cx, cz) in byre_cells | {(byre.x1 + 1, z)
                                      for z in range(byre.z0, byre.z1 + 1)}:
            for y in range(1, min(vox.size[1], 8)):
                vox.set((cx, y, cz), None)
        # The byre stands **on** the yard's west boundary, so that clear took the
        # fence with it and the herd walked out through its own shelter. Whatever
        # any pass removes, the boundary is re-asserted below.
        with vox.device(breed.byre_form + " byre"):
            shelter_for(breed, byre, tier, vox, pal, rng)
        if breed.boundary == "boarded":
            with vox.device("rail course"):
                rail_course(vox, ring, plan.mask, pal, rng, byre_cells)

    with vox.device("trough"):
        water_hollow(vox, plan.trough[:3 if tier.kerb else 2], tier.kerb,
                     pal.stone, rng)

    # The pond is dug from the same blob outward: a prefix at every rung.
    wet: List[Coord2] = []
    if tier.kerb and plan.pond:
        want = {2: 6, 3: 9, 4: 11, 5: 13}.get(tier.shelter, 0)
        wet = plan.pond[:want]
        with vox.device(breed.water + " water"):
            if breed.water == "dip":
                wet = dip_pool(vox,
                               [c for c in plan.pond if c not in byre_cells],
                               pal, rng) or wet[:4]
                if wet and not any(vox.get((c[0], 1, c[1])) for c in wet):
                    pond_cells(vox, wet, rng)
            else:
                pond_cells(vox, wet, rng)
                if breed.water == "wallow":
                    for (x, z) in {(c[0] + dx, c[1] + dz) for c in wet
                                   for dx, dz in NEIGH4} - set(wet):
                        if vox.get((x, 0, z)) is not None \
                                and rng.random() < 0.8:
                            vox.set((x, 0, z),
                                    state(MIRE[rng.randrange(len(MIRE))]))
                if breed.water == "pond" and wet:
                    runnel(vox, plan.mask, wet, plan.trough[0],
                           byre_cells, rng)

    free = [c for c in plan.props if c not in wet
            and not vox.occupied((c[0], 1, c[1]))]
    if byre and tier.shelter >= 2:
        # Never in a boundary cell: the byre's west column *is* the yard's run
        # here, and a hay bale there is a step onto the fence.
        rack = [(byre.x0, z) for z in range(byre.z0 + 1, byre.z1)
                if not vox.occupied((byre.x0, 1, z))
                and (byre.x0, z) not in set(ring)][:2]
        with vox.device("feed rack"):
            feeder(vox, rack, pal, rng)
    elif free:
        with vox.device("feed rack"):
            vox.set((free[0][0], 1, free[0][1]), state("hay_block", axis="z"))

    if breed.holding_pen and tier.shelter >= 3:
        with vox.device("holding pen"):
            holding_pen(vox, [c for c in free if c[0] > plan.wall_x + 3], pal, rng)
    if tier.shelter >= 3:
        with vox.device("muck heap"):
            dung_heap(vox, free[1:2] or free[:1], rng)
        with vox.device("paths"):
            dashed_path(vox, plan.gate, "north", 3, rng)
            dashed_path(vox, plan.door, "south", 2, rng)

    # Keep standing room for the herd: a compact sty at the top rung has a pond, a
    # muck heap and its stores, and the fourth pig had nowhere left to be.
    reserve = tier.herd + 2
    with vox.device("yard fittings"):
        yard_props(vox, breed, tier, byre,
                   list(free[:max(0, len(free) - reserve)]), pal, rng,
                   {(c[0] + dx, c[1] + dz) for c in ring for dx, dz in NEIGH4})
    if tier.weathered:
        with vox.device("rubble"):
            rubble(vox, ring, plan.mask, pal, rng)
    with vox.device("lighting"):
        lighting(vox, tier, walk, byre, pal, rng)
    with vox.device("planting"):
        planting(vox, breed, plan.mask, free, rng)

    with vox.device("jigsaw"):
        vox.set((plan.door[0], 0, plan.sz - 1),
                state("jigsaw", orientation=JIGSAW_ORIENTATION["south"]),
                jigsaw(JOBS_TARGET))

    # **The boundary invariant.** Every run cell holds something an animal cannot
    # pass, whatever earlier passes did to it. Cheaper and safer than auditing each
    # pass: the byre clear, the pocket absorption and the donor swap have each
    # opened a hole here at some point in this file's history.
    with vox.device("boundary invariant"):
        for c in ring:
            if c == plan.gate:
                continue
            if not vox.occupied((c[0], 1, c[1])):
                vox.set((c[0], 1, c[1]), pal.wood.fence())

    with vox.device("diagonal closure"):
        close_diagonals(vox, plan.mask, pal)
    # `keep` protects the byre's own fittings — but not where the byre stands on
    # the boundary, or it protects the defect instead.
    with vox.device("mount clearance"):
        clear_mounts(vox, plan.mask, byre_cells - set(ring), set(ring))
    with vox.device("lily tidy"):
        for (x, y, z), b in list(vox.solid_items()):
            if b.short == "lily_pad":
                below = vox.get((x, y - 1, z))
                if below is None or below.short != "water":
                    vox.set((x, y, z), None)
    with vox.device("reconnect"):
        reconnect(vox)
    # The byre counts as standing room: by the top rung a compact sty has a pond, a
    # muck heap and its props in the yard, and the fourth pig had nowhere to go.
    with vox.device("herd"):
        stock(vox, breed, tier,
              [c for c in list(plan.inner) + sorted(byre_cells)
               if not vox.occupied((c[0], 1, c[1]))], rng)
    trim(vox)
    vox.name = f"{breed.key}{'' if tier.key == 'base' else '_' + tier.key}"
    return Pen(vox=vox.vox, breed=breed, tier=tier, yard=plan.inner,
               mask=plan.mask, gate=plan.gate, shed=byre, seed=plan.seed,
               house_at=at, faults=list(vox.faults))


def pond_cells(vox: Voxels, cells: Sequence[Coord2],
               rng: random.Random) -> None:
    """Dig the given cells as water, bank them, and float a pad or two."""
    for (x, z) in cells:
        vox.set((x, 0, z), state("water", level="0"))
        vox.set((x, 1, z), None)
    rim = {(x + dx, z + dz) for (x, z) in cells for dx, dz in NEIGH4} - set(cells)
    for (x, z) in sorted(rim):
        if vox.get((x, 0, z)) is None or vox.get((x, 0, z)).short == "water":
            continue
        vox.set((x, 0, z), state(POND_RIM[rng.randrange(len(POND_RIM))]))
        if rng.random() < 0.3 and not vox.occupied((x, 1, z)):
            vox.set((x, 1, z), state("short_grass"))
    for (x, z) in cells:
        if rng.random() < 0.3 and not vox.occupied((x, 1, z)):
            vox.set((x, 1, z), state("lily_pad"))


def compose_ladder(breed: Breed, seed: int = 0) -> List[Pen]:
    """The whole family, each rung grown out of the one before it."""
    plan = plan_farmstead(breed, seed)
    pens: List[Pen] = []
    prev: Optional[Voxels] = None
    for tier in LADDER:
        pen = build_rung(plan, tier, prev)
        pens.append(pen)
        prev = pen.vox
    return pens


def compose_farmstead(breed: Breed, tier: Tier, seed: int = 0) -> Pen:
    """One rung. Builds the ladder up to it, because a rung is not independent."""
    wanted = LADDER.index(tier)
    plan = plan_farmstead(breed, seed)
    prev: Optional[Voxels] = None
    pen = None
    for t in LADDER[:wanted + 1]:
        pen = build_rung(plan, t, prev)
        prev = pen.vox
    assert pen is not None
    return pen


compose_pen = compose_farmstead          # what the driver calls


def recipes() -> List[Tuple[Breed, Tier]]:
    """Every file in the set: three breeds by six tiers."""
    return [(b, t) for b in BREEDS for t in LADDER]


# ── the functional gate ─────────────────────────────────────────────

def check_pen(pen: Pen) -> List[str]:
    """Everything a pen has to *do*, checked. Empty list means it works.

    `critic.py` measures how a build looks and cannot see any of this. For the
    wall set the equivalent question was whether a player can walk the circuit;
    for a pen it is four questions, and the first one is the one that only ever
    shows up in a world, as animals in the town square.
    """
    vox = pen.vox
    sx, _sy, sz = vox.size
    yard = set(pen.yard)
    out: List[str] = []

    leaks = enclosed(vox, pen.yard)
    if leaks:
        out.append(f"boundary leaks: an animal walks out at {leaks[:4]}")
    hops = escape_routes(vox, pen.yard, pen.mask)
    if hops:
        out.append(f"{len(hops)} escape route(s): an animal jumps out at "
                   f"{hops[:4]} — something a block high is standing next to "
                   f"the fence")

    yard_cells = [(x, 1, z) for (x, z) in pen.yard]
    border = [(x, 1, z) for x in range(sx) for z in range(sz)
              if (x in (0, sx - 1) or z in (0, sz - 1)) and (x, z) not in yard]
    entry = check_route(vox, border, yard_cells, "gate")
    if not entry:
        out.append(entry.reason)

    if pen.shed is not None:
        inside = [(x, 1, z) for (x, z) in pen.shed.cells]
        shelter_route = check_route(vox, yard_cells, inside, "shelter")
        if not shelter_route:
            out.append(shelter_route.reason)

    # The farmhouse has to be a house: you get in through its door and stand up
    # inside it. Grafting a donor can go wrong in ways the style gate cannot
    # see — an offset by one puts the wall through the doorway.
    hx, hz = pen.house_at
    rooms = [(hx + dx, 1, hz + dz) for dx in range(1, 8) for dz in range(2, 10)]
    entry = check_route(vox, border, rooms, "farmhouse")
    if not entry:
        out.append(entry.reason)

    if len(vox.entities) < pen.tier.herd:
        out.append(f"{len(vox.entities)} animals placed, tier wants "
                   f"{pen.tier.herd} — the yard ran out of free ground")
    return out
