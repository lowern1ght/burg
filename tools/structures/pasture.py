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
        """A `*_wall` block: 1.5 tall, see over but not cross — a dry-stone fold."""
        return state(f"{self.cut}_wall", up="true", north="none", south="none",
                     west="none", east="none", waterlogged="false")


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
          drop_terrain: bool = True) -> None:
    """Stamp a donor building into the plot at `at`, block-entity data included.

    Skips the donor's own ground layer — the plot lays its own, and two terrain
    layers fight over the same cells — and skips its jigsaw markers and
    entities, which belong to the donor's life as a standalone building.
    """
    ox, oz = at
    for (x, y, z), b in donor.solid_items():
        if drop_terrain and y == 0:
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
    family: str = "house"       # which of the author's house ladders to graft
    yard: Coord2                # the flank arm of the yard, w x d
    strip: int                  # depth of the working arm behind the house;
                                # 0 for a compact yard with no arm at all
    clip: int                   # how far the outer corners are cut back
    byre: int                   # depth along the house wall of the lean-to
    ground: Sequence[str] = GRAZED
    graze: bool = True          # tufts of grass left standing inside
    holding_pen: bool = False   # a small sorting pen for shearing
    wallow: bool = False        # a mud hollow with water in it
    dry_stone: bool = False     # wall-block infill in the piers from lvl4
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
    yard=(11, 11), strip=5,
    clip=2, byre=5,
    ground=GRAZED, graze=True, milking=True,
    note="the widest yard, kept in grass, big trough and a milking corner")
PIG = Breed(
    key="pig_sty", entity="minecraft:pig", family="house",
    yard=(8, 10), strip=0, clip=1,
    byre=4,
    ground=MIRE, graze=False, wallow=True,
    note="compact and churned to mud, a wallow, fed at the house door")
SHEEP = Breed(
    key="sheep_fold", entity="minecraft:sheep", family="house_2",
    yard=(9, 10), strip=5,
    clip=2, byre=4,
    ground=FOLD, graze=True, dry_stone=True, wool=True, holding_pen=True,
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
    lantern: bool = False       # prestige fitting: lvl4 and up only
    rich: bool = False          # posts finished with a stair cap
    herd: int = 2               # animals shipped in the file


LADDER: Tuple[Tier, ...] = (
    Tier("base", "the author's plainest house, a crooked yard fence, a puddle", crooked=True, herd=2),
    Tier("lvl1", "house_lvl1, fence put straight, open byre off the house wall", shelter=1, herd=2),
    Tier("lvl2", "house_lvl2, stone-pier fence, kerbed trough, byre with a rack", piers=True, shelter=2, kerb=True, herd=3),
    Tier("lvl3", "house_lvl3, byre gabled and shuttered, muck heap, worn paths", piers=True, shelter=3, kerb=True, herd=3),
    # Beds arrive in the donor at lvl4, which is where the JSON grants residents.
    Tier("lvl4", "house_lvl4 (beds), byre on a stone plinth, a lantern", piers=True, shelter=4, kerb=True, lantern=True,
         rich=True, herd=4),
    Tier("lvl5", "house_lvl6, the byre run out to full length, deep eave", piers=True, shelter=5, kerb=True, annex=True,
         lantern=True, rich=True, herd=4),
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
               wear: Sequence[Coord2], rng: random.Random) -> None:
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
        p_worn = {0: 0.9, 1: 0.5, 2: 0.2}.get(near, 0.05)
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

def fence_ring(vox: Voxels, tier: Tier, breed: Breed, ring: Sequence[Coord2],
               skip: Set[Coord2], pal: Palette, rng: random.Random) -> None:
    """The enclosure itself, at the tier's material.

    Three grammars, one per stage of the ladder:

    * **crooked** (base) — oak fence with the odd post out of true: a log stub
      leaning in, a rail missing its cap. Poor, and it has to read as poor.
    * **straight** (lvl1) — oak fence with log posts at intervals, which is what
      the author does along the north edge of `sheep_field`.
    * **piers** (lvl2+) — the stone-pier fence of reference `05d4`: a cobblestone
      post capped with a slab every third cell, oak fence spanning between them.
      For the fold the infill becomes a dry-stone wall course from lvl4.
    """
    for i, (x, z) in enumerate(ring):
        if (x, z) in skip:
            continue
        pier = tier.piers and i % 3 == 0
        if pier:
            # Three tones, so the run has depth rather than one flat grey:
            # cobblestone carries the field, mossy climbs the foot of some posts,
            # andesite gives the odd post its own colour.
            roll = rng.random()
            tone = (pal.stone.weathered if roll < 0.3
                    else pal.stone.second if roll < 0.42 else pal.stone.main)
            vox.set((x, 1, z), state(tone))
            vox.set((x, 2, z), pal.stone.slab("bottom"))
        elif tier.piers and breed.dry_stone:
            # A fold is dry stone: the infill is a wall course, not a rail.
            vox.set((x, 1, z), pal.stone.rail())
        elif tier.crooked:
            if rng.random() < 0.12:
                # Out of true: a log stub where a post rotted and was replaced.
                _capped_post(vox, (x, z), pal)
            else:
                vox.set((x, 1, z), pal.wood.fence())
        else:
            if i % 4 == 0:
                _capped_post(vox, (x, z), pal)
            else:
                vox.set((x, 1, z), pal.wood.fence())


def _capped_post(vox: Voxels, cell: Coord2, pal: Palette) -> None:
    """A log post in the boundary, with a rail on top of it.

    **The cap is not decoration.** A bare full block in a fence line is a
    mounting block: an animal jumps a full block, so it hops onto the post
    (rise 1.0) and walks out over the fence beside it. This was the actual
    escape route the jump-aware check found in every pen — the boundary had no
    hole in it anywhere, and the animals still got out over its own posts.
    """
    x, z = cell
    vox.set((x, 1, z), state("oak_log", axis="y"))
    # A **slab** cap, not a rail. A lone fence on top of a post connects to
    # nothing on any side and renders as a stub sticking out of the post — the
    # fabric check counted 15 of them in one yard, and the author does not do it
    # anywhere in 115 files. A slab reads as a capped post, matches how the
    # stone piers are finished, and still defeats the jump: its top is at +1.5,
    # so it cannot be reached from the ground either.
    vox.set((x, 2, z), pal.wood.slab("bottom"))


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
                inside = [c for c in ((x + dx, z), (x, z + dz))
                          if c in mask and not vox.occupied((c[0], 1, c[1]))]
                outside = [c for c in ((x + dx, z), (x, z + dz))
                           if c not in mask and not vox.occupied((c[0], 1, c[1]))
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
            vox.set((jx, 2, z), pal.stone.slab("bottom") if tier.piers
                    else pal.wood.slab("bottom"))


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


def _flat_roof(vox: Voxels, shed: Shed, pal: Palette, y: int) -> None:
    """A lean-to lid: slabs, with the eave course oversailing by one.

    Only the poorest rung uses this. Over anything longer than three cells a
    single flat course of slabs reads as a tarp stretched over the yard, which
    is what lvl2 and lvl3 looked like before `_mono_roof` replaced it.
    """
    for (x, z) in shed.cells:
        vox.set((x, y, z), pal.wood.slab("bottom"))
    for x in range(shed.x0, shed.x1 + 1):
        vox.set((x, y, shed.z1 + 1), pal.wood.slab("top"))


def _mono_roof(vox: Voxels, shed: Shed, pal: Palette, eave: int) -> int:
    """A single-pitch roof falling toward the yard — the shape a lean-to has.

    One course higher at the back than at the front, so the plane actually
    slopes: stairs carry the fall and the course under the back stairs is closed
    with plank, or the slope is a shelf with a gap into the shed behind it.
    """
    z0, z1 = shed.z0, shed.z1
    for x in range(shed.x0, shed.x1 + 1):
        vox.set((x, eave, z0), pal.wood.planks)
        vox.set((x, eave + 1, z0), pal.wood.stairs("south", "bottom"))
        for z in range(z0 + 1, z1):
            vox.set((x, eave, z), pal.wood.stairs("south", "bottom"))
        vox.set((x, eave, z1), pal.wood.slab("bottom"))
        # Eave over the yard face, so the fall has somewhere to land.
        vox.set((x, eave, z1 + 1), pal.wood.slab("top"))
    return eave + 1


def _pitched_roof(vox: Voxels, shed: Shed, pal: Palette, eave: int,
                  oversail: bool) -> int:
    """The author's own roof: stairs stepping inward one course per layer.

    Measured over the corpus rather than assumed — 82% of his builds ten or more
    tall pitch the roof this way, and the flat-slab lid this replaces is only
    right on a five-tall cottage. The ridge closes in slabs.

    Returns the ridge height.
    """
    depth = shed.z1 - shed.z0 + 1
    steps = (depth + 1) // 2
    y = eave
    z_lo, z_hi = shed.z0, shed.z1
    if oversail:
        # Deep eave on beam ends, over the yard face only (`078d`). The back row
        # of the shed *is* the fence line, so oversailing there would hang the
        # barn roof outside the pen over open ground.
        for x in range(shed.x0, shed.x1 + 1):
            vox.set((x, y, z_hi + 1), pal.wood.stairs("north", "bottom"))
        for x in (shed.x0, shed.x1):
            vox.set((x, y - 1, z_hi + 1), pal.wood.beam("z"))
    for step in range(steps):
        for x in range(shed.x0, shed.x1 + 1):
            if z_lo < z_hi:
                vox.set((x, y, z_lo), pal.wood.stairs("south", "bottom"))
                vox.set((x, y, z_hi), pal.wood.stairs("north", "bottom"))
            else:
                vox.set((x, y, z_lo), pal.wood.slab("bottom"))
        if z_lo >= z_hi:
            break
        # Close the void behind each course, or the roof is a pair of ramps
        # with the loft open to the sky between them.
        for x in range(shed.x0, shed.x1 + 1):
            for z in range(z_lo + 1, z_hi):
                if not vox.occupied((x, y, z)) and step > 0:
                    vox.set((x, y, z), pal.wood.planks)
        z_lo, z_hi = z_lo + 1, z_hi - 1
        y += 1
    return y


def shelter(vox: Voxels, shed: Shed, breed: Breed, tier: Tier, pal: Palette,
            rng: random.Random) -> int:
    """Build the shelter for this tier. Returns its highest block.

    **Long, low, and only as tall as it has to be.** Measured off the author's
    own animal building, `pig_farm`: 12x10 on the ground with the eave three
    courses up, cobblestone below, timber frame above, and one broad roof plane
    over the lot. Two versions of this module got that backwards — a 4x3
    footprint with the posts at y=4 and the ridge at y=6 — and rendered as a
    plank tower parked in a field. The user's word for it was accurate.

    So: the body is two courses (interior clearance y=1..2, which is what a cow
    and a farmer both need), the eave sits at y=3, and only the barn takes a
    third course. Extra rungs buy *length and massing*, never height.
    """
    if tier.shelter == 1:
        # Two posts and a plank: the poorest shelter that is still a shelter.
        for (x, z) in ((shed.x0, shed.z1), (shed.x1, shed.z1)):
            for y in (1, 2):
                vox.set((x, y, z), pal.wood.post)
        _flat_roof(vox, shed, pal, 3)
        return 3
    if tier.shelter == 2:
        _frame(vox, shed, pal, 3)
        # A back wall even on the open shed. Two reasons, and the second is not
        # cosmetic: a byre open on all four sides is a table on legs, and the
        # back row *is* the boundary — left as a bare rail, the hay rack in
        # front of it becomes the step an animal uses to get over it.
        for x in range(shed.x0 + 1, shed.x1):
            vox.set((x, 1, shed.z0), pal.wood.fence())
            vox.set((x, 2, shed.z0), pal.wood.planks)
        return _mono_roof(vox, shed, pal, 3)
    if tier.shelter == 3:
        _frame(vox, shed, pal, 3)
        _walls(vox, shed, pal, 3, rng)
        return _mono_roof(vox, shed, pal, 3)
    if tier.shelter == 4:
        # Same squat body, but the lid becomes a pitch: a stair course each side
        # and a slab ridge, which over three cells of depth is the author's own
        # roof at the smallest size it exists in.
        _frame(vox, shed, pal, 3)
        _walls(vox, shed, pal, 3, rng)
        return _pitched_roof(vox, shed, pal, 3, oversail=False)
    # lvl5, the barn: one course taller, a cell deeper, and — the device that
    # actually makes a farm building read — a lower roof plane beside the main
    # one instead of a single bigger box (`0c6c`, `4c7e`: cluster massing).
    top = 4
    _frame(vox, shed, pal, top)
    _walls(vox, shed, pal, top, rng)
    ridge = _pitched_roof(vox, shed, pal, top, oversail=True)
    if tier.annex:
        _annex(vox, shed, pal, rng)
    return ridge


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


def _side_door(vox: Voxels, shed: Shed, pal: Palette,
               rng: random.Random) -> None:
    """A door in the gable wall, for the farmer.

    Not in the yard face. That face is the animals' way in and doors across it
    would dam the shelter — the barn version of the gatehouse whose own flanking
    piers blocked the passage. The stock walks in under the open front; the
    farmer comes in from the side.
    """
    x = rng.choice((shed.x0, shed.x1))
    facing = "east" if x == shed.x0 else "west"
    z = shed.z0 + 1
    if z >= shed.z1:
        return
    vox.set((x, 1, z), pal.wood.door(facing, "lower", "left"))
    vox.set((x, 2, z), pal.wood.door(facing, "upper", "left"))
    vox.set((x, 3, z), pal.wood.beam("z"))


# ── fittings and props ──────────────────────────────────────────────

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
    chosen: List[Coord2] = []
    for c in spots:
        if all(abs(c[0] - o[0]) + abs(c[1] - o[1]) >= 3 for o in chosen):
            chosen.append(c)
        if len(chosen) == tier.herd:
            break
    for c in chosen:
        vox.entities.append(animal(breed.entity, c, 1, rng))


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
    """A byre built **against the farmhouse wall**, roofed off it.

    The longhouse arrangement, and the answer to a free-standing shed being
    rejected twice: the animals' shelter shares a wall with the house instead of
    standing on its own in the middle of the plot. The house carries the back
    side, so the roof needs no rear gable and the two volumes read as one
    farmstead. The pitch falls away from the house, which is why the course
    against the wall is the high one.
    """
    x0, x1 = shed.x0, shed.x1
    z0, z1 = shed.z0, shed.z1
    body = 3 if tier.shelter >= 4 else 2

    # The outer post line takes the loads the house does not: ends always, then
    # every third bay.
    for z in range(z0, z1 + 1):
        if z in (z0, z1) or (z - z0) % 3 == 0:
            for y in range(1, body + 1):
                vox.set((x1, y, z), pal.wood.post)
        elif tier.shelter >= 4:
            # Stone plinth between the posts: the author's stone-base grammar.
            vox.set((x1, 1, z), state(pal.stone.main if rng.random() < 0.75
                                      else pal.stone.weathered))

    # Gable ends, from the tier that walls the byre in: rail below, panel above.
    if tier.shelter >= 3:
        for z in (z0, z1):
            for x in range(x0, x1):
                vox.set((x, 1, z), pal.wood.fence())
                for y in range(2, body + 1):
                    vox.set((x, y, z), pal.wood.planks)
        if tier.shelter >= 3:
            shutter_z = rng.choice((z0, z1))
            vox.set((x0 + 1 if x0 + 1 < x1 else x0, 2, shutter_z),
                    pal.wood.trapdoor("south", "bottom"))

    # Beam course on the open face, then the fall: one course down per cell out
    # from the wall, stairs carrying the slope and a slab at the eave.
    high = body + 2
    for z in range(z0, z1 + 1):
        vox.set((x1, body + 1, z), pal.wood.beam("z"))
        for i, x in enumerate(range(x0, x1 + 1)):
            y = max(body + 1, high - i)
            vox.set((x, y, z), pal.wood.stairs("east", "bottom") if x < x1
                    else pal.wood.slab("bottom"))
        # Eave one cell past the posts: an edge for the fall and a shadow line
        # on the yard.
        vox.set((x1 + 1, body + 1, z), pal.wood.slab("top"))
        # And close the join against the house: the top course stops one cell
        # short of the wall column, which left a one-cell slot open to the sky
        # between the two roofs at every top tier. Only filled where the donor
        # has not already built something there.
        if not vox.occupied((x0 - 1, high, z)):
            vox.set((x0 - 1, high, z), pal.wood.slab("bottom"))
    return high


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


def clear_mounts(vox: Voxels, mask: Set[Coord2],
                 keep: Set[Coord2]) -> List[Coord2]:
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
    rails = {(p[0], p[2]) for p, b in vox.solid_items()
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


def absorb_pockets(vox: Voxels, mask: Set[Coord2]) -> Set[Coord2]:
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
    open_cell = {(x, z) for x in range(sx) for z in range(sz)
                 if _mob_passable(vox.get((x, 1, z))) and (x, z) not in mask}
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


def compose_farmstead(breed: Breed, tier: Tier, seed: int = 0) -> Pen:
    """One farmstead: the author's house, with this animal's yard beside it."""
    rng = random.Random(seed * 7919 + 13)
    pal = Palette(stone=Stone(), wood=Timber())
    house = donor_house(breed_donors(breed)[LADDER.index(tier)])
    hw, hd = house_box(breed)
    yw, yd = breed.yard

    # The plot is the same size at every level, which the author does in all 98
    # of his buildings and which `UpgradeAction` relies on: it replaces the NBT
    # at the same origin, so a level that needs a wider box may not fit where
    # the last one stood. `house.nbt` is a cell narrower than `house_lvl2` and
    # up, so the donor is shifted east until its **east wall** lands on the same
    # column at every rung, and the yard beyond it never moves.
    wall_x = MARGIN + hw - 1          # where the house's east wall always sits
    strip = breed.strip
    # The house stands at the front, on the street, with its door on the south
    # edge as the donor built it, and the yard wraps behind and beside it —
    # **flush against its real walls**, measured rather than taken from the box.
    bx0, bx1, bz0, bz1 = house_bounds(house)
    # Clamped: a donor whose walls start further in than the strip is deep would
    # otherwise be grafted above the top of the plot and lose its northern rows.
    house_at = (wall_x - bx1, max(MARGIN, MARGIN + strip - bz0))
    wall_north = house_at[1] + bz0     # the row the yard's strip butts onto
    sx = MARGIN + hw + yw + MARGIN
    # Deep enough for **both** the house box and the yard the breed asks for. It
    # was sized from the house alone, so the flank ran past the south edge of the
    # plot: the row that should have been the boundary had yard on both sides of
    # it, was never fenced, and the animals walked straight out.
    sz = plot_depth(breed)
    height = max(box_height(tier), house.top_y() + 2)
    vox = Voxels((sx, height, sz), {}, f"{breed.key}_{tier.key}")

    mask = yard_region(wall_x, wall_north, yw, yd, house_at[0] + bx0,
                       breed.clip, sz)
    yx0, yx1 = wall_x + 1, wall_x + yw
    yz0 = MARGIN
    # The flank's own front row, over the whole arm rather than over its outer
    # column: the corner clip shortens that column, so measuring there put the
    # front two rows too far north and left no run to hang a gate in at all.
    yz1 = max(c[1] for c in mask if c[0] > wall_x)
    rim = set(boundary(mask))

    # The house goes in before anything reads the plot: `absorb_pockets` and
    # `open_ring` both need to know where its walls actually are.
    graft(vox, house, house_at)
    mask = absorb_pockets(vox, mask)
    rim = set(boundary(mask))

    byre: Optional[Shed] = None
    if tier.shelter >= 1:
        depth = 2 if tier.shelter < 4 else 3
        blen = max(2, min(breed.byre + (1 if tier.shelter >= 4 else 0),
                          yz1 - yz0 - 1))
        # Against the house's east wall, starting level with the working arm, so
        # the byre and the muck heap share the trodden end of the yard and the
        # far end stays open ground.
        bz0 = wall_north + 1
        byre = Shed(yx0, yx0 + depth - 1, bz0, bz0 + blen - 1)
    byre_cells = set(byre.cells) if byre else set()

    inner = sorted(mask - rim)
    open_cells = [c for c in inner if c not in byre_cells]
    if not open_cells:
        raise ValueError("yard has no open ground left")

    # The gate hangs in the yard's own south boundary — a cell whose south
    # neighbour is neither yard nor house. Taking `max(z)` of the outer column
    # instead put the gate inside the yard on the L-shaped plan, so the fence had
    # no opening at all and nothing could walk in; `check_pen` caught it.
    def in_house(c: Coord2) -> bool:
        return (house_at[0] + bx0 <= c[0] <= house_at[0] + bx1
                and house_at[1] + bz0 <= c[1] <= house_at[1] + bz1)

    # Only the flank's street-facing run. The strip behind the house also has a
    # south boundary — wherever the donor's north wall has a gap — but a gate
    # there opens into the dead pocket between fence and wall, and `check_pen`
    # said so plainly: 84 standable yard cells, none of them reachable.
    front = sorted(c for c in mask
                   if c[1] == yz1 and c[0] > wall_x
                   and (c[0], c[1] + 1) not in mask
                   and not in_house((c[0], c[1] + 1)))
    if len(front) < 3:
        raise ValueError("yard has no street-facing run to hang a gate in")
    # Nearest the house end of the run, so the walk from the street door to the
    # yard is short — but never dead centre.
    off = rng.choice((0, 1, 2))
    gate = front[min(len(front) - 2, 1 + off)]

    trough_anchor = min(open_cells,
                        key=lambda c: (abs(c[0] - yx1) + abs(c[1] - yz1)))
    hollow = [trough_anchor]
    for dx, dz in ((0, 1), (1, 0), (0, 2)):
        q = (trough_anchor[0] + dx, trough_anchor[1] + dz)
        if q in open_cells and len(hollow) < (3 if tier.kerb else 2):
            hollow.append(q)

    dx, dz = house_door(house)
    door = (house_at[0] + dx, house_at[1] + dz)
    wear = [gate, door] + hollow
    if byre:
        wear += [(byre.x1 + 1, z) for z in range(byre.z0, byre.z1 + 1)]
    lay_ground(vox, breed, mask, rim, wear, rng)

    # The donor plants bushes round its plot. A leaf block is standable at
    # +1.0, so one growing against the yard fence is a mounting block and one
    # growing against the house is a stair onto its roof — `sheep_fold` base
    # escaped exactly that way. They come out inside the yard and its lane.
    yard_zone = mask | {(c[0] + dx, c[1] + dz) for c in mask
                        for dx, dz in NEIGH4}
    for (x, y, z), b in list(vox.solid_items()):
        if y >= 1 and (x, z) in yard_zone and b.short in ("oak_leaves",
                                                          "oak_sapling"):
            vox.set((x, y, z), None)
        # The donor fences its own garden. Inside the farmstead's yard — or one
        # cell from it — that line runs parallel to the yard's own boundary with
        # a dead cell between the two, which is the fence looking like it was
        # built twice. His street-side garden fence is further out and stays.
        if y >= 1 and (x, z) in yard_zone and b.short.endswith(("_fence",
                                                                "_fence_gate")):
            vox.set((x, y, z), None)

    ring = open_ring(vox, mask)
    walk = ring_walk(set(ring))
    fence_ring(vox, tier, breed, walk, skip={gate}, pal=pal, rng=rng)
    hang_gate(vox, gate, pal, tier)

    if byre:
        lean_to(vox, byre, tier, pal, rng)

    water_hollow(vox, hollow, tier.kerb, pal.stone, rng)
    if breed.wallow:
        # A sty is a mire: mud round the wallow, not a lawn with a pond in it.
        for (x, z) in open_cells:
            if min(abs(x - h[0]) + abs(z - h[1]) for h in hollow) <= 2 \
                    and rng.random() < 0.7:
                vox.set((x, 0, z), state(MIRE[rng.randrange(len(MIRE))]))

    free = [c for c in open_cells if c not in hollow
            and not vox.occupied((c[0], 1, c[1]))]
    # One cell of clearance inside every fenced run: nothing a full block high
    # goes here, or it is a step over the boundary.
    lane = {(c[0] + dx, c[1] + dz) for c in ring for dx, dz in NEIGH4}
    prop_cells = [c for c in free if c not in lane]

    if byre and tier.shelter >= 2:
        # Hay is a full block, so the rack obeys the clear lane exactly like the
        # props do. It did not, and that was the leak: the animal stood on its
        # own feed and stepped onto the capped post beside it.
        rack = [(byre.x0, z) for z in range(byre.z0 + 1, byre.z1)
                if not vox.occupied((byre.x0, 1, z))
                and (byre.x0, z) not in lane][:2]
        feeder(vox, rack, pal, rng)
    elif prop_cells:
        c = prop_cells[rng.randrange(len(prop_cells))]
        vox.set((c[0], 1, c[1]), state("hay_block", axis="z"))

    if breed.holding_pen and tier.shelter >= 3:
        used = holding_pen(vox, [c for c in prop_cells if c[0] > yx0 + 2],
                           pal, rng)
        prop_cells = [c for c in prop_cells if c not in used]
    if tier.shelter >= 3 and prop_cells:
        near_byre = [c for c in prop_cells if byre and abs(c[0] - byre.x1) <= 2]
        dung_heap(vox, near_byre[:1] or prop_cells[:1], rng)
        dashed_path(vox, gate, "north", 3, rng)
        dashed_path(vox, door, "south", 2, rng)

    yard_props(vox, breed, tier, byre, list(prop_cells), pal, rng, lane)
    lighting(vox, tier, walk, byre, pal, rng)
    planting(vox, breed, mask, free, rng)

    # One terminator, in front of the farmhouse door — the author's own
    # convention for a house, and where a street should meet this plot.
    vox.set((door[0], 0, sz - 1),
            state("jigsaw", orientation=JIGSAW_ORIENTATION["south"]),
            jigsaw(JOBS_TARGET))

    # Last, so that rails added by the byre, the holding pen and the props are
      # all part of the run being checked.
    close_diagonals(vox, mask, pal)
    clear_mounts(vox, mask, byre_cells)
    reconnect(vox)
    stock(vox, breed, tier, [c for c in free
                             if not vox.occupied((c[0], 1, c[1]))], rng)
    trim(vox)
    vox.name = f"{breed.key}{'' if tier.key == 'base' else '_' + tier.key}"
    return Pen(vox=vox, breed=breed, tier=tier, yard=inner, mask=mask,
               gate=gate, shed=byre, seed=seed, house_at=house_at)


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
