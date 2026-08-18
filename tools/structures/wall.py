"""The fortification set: curtain wall, corner, gate and wall tower.

A wall is not a building with the roof taken off. A building is a shell with an
inside; a wall is two faces and a walk between them, and almost everything that
makes it read comes from the difference between those two faces.

**Function first.** The first version of this module looked right and was
useless: the corner was a solid block, the gate was dammed by its own flanking
piers, the tower had no way up, and the top tier had one block of headroom under
its gallery so nothing could stand on it at all. Ten of twelve pieces failed
`traverse.check_route`. Everything below is arranged so that a player can walk
the whole circuit, climb every tower and pass through every gate without a
single jump, and `traverse.py` is what says whether that is still true.

The rules that follow from it:

* **One walk elevation for the entire set.** `BODY_TOP` is the same at every
  level of every piece, so the walk is at the same height whatever mix of tiers
  and kinds a ring is built from. Vary the height by tier and a mixed ring
  becomes a staircase of one-block steps, every one of which needs a jump.
* **Two clear cells above the walk, everywhere.** A player is two blocks tall.
* **Crenellation is functional.** The outer parapet is one block at the
  embrasures and two at the merlons: chest height, so you can see and shoot
  over the low part, with cover beside it. That is what a battlement is for, and
  it is also why the wall is worth walking on.
* **Lighting is interior.** Torches on the inside face, pointing in. Nobody
  lights the outside of their own wall for the benefit of whoever is attacking
  it, and a lantern on a chain hanging off the battlements is a stage prop.

Materials run as a progression, in the vocabulary the mod is heading for rather
than only what the corpus already contains:

    0  earth bank behind an oak stockade
    1  cobblestone plinth, oak frame — posts, beams, panel infill
    2  cobblestone and stone, andesite as the gradient
    3  cobblestone and stone, tuff as the gradient
    4  cobblestone and stone, stone brick as worked stone, under an oak gallery

The base is cobblestone and stone at every level and only the gradient stone
changes. Rough stone throughout: no bricks, no polished, no chiselled. Dressed
masonry reads as a palace, and a village wall is built from what it dug.

Timber is used as timber. `oak_log` stands as a post, `stripped_oak_log` lies as
a beam, `oak_planks` only ever fills a panel between them, `oak_stairs` and
`oak_slab` build the roof, `oak_fence` is a rail. Planks used as a general
building material is what made the first pass read as a shed.

Devices, each traced to the reference image it came from:

  vertical streaking     `2b41`, `0fee`, `81de` — the field is columns, each
                         keeping its own material, not per-cell noise
  mossy skirt            `0fee`, `81de` — weathering climbs the foot to a
                         different height in every column
  rubble spill           `2b41` — loose stone on the ground at the foot
  arrow loops            `1b95`, `81de` — one-deep recesses in pairs
  projecting parapet     `9432` — the head oversails the face for its length
  brackets, shadow slot  `9432`, `1b95` — the oversail steps down onto a
                         bracket at each pier; the gap between is the device
  capped merlons         `9432`, `00ed` — two wide, in the pier material
  dressed wall walk      `00ed` — plank floor, interior torches
  timber hoarding        `0fee`, `81de` — a covered fighting gallery projecting
                         beyond the stone, which you stand IN
  beam ends              `1b95` — beam stubs projecting past the posts
  external stair         `b42d`, `40fe` — steps with a rail, on the tower
  stepped top profile    `55a3`, `0fee` — the parapet never runs level

Two constraints are structural, and both were measured:

* **The footprint is identical at every level; only the height changes.** The
  author does this in all 98 of his buildings — `house` is 9x11 from level 0 to
  6, `house_3` 14x12, `pig_farm` 26x19 across nine levels.
* **The connector sits on the middle cell of the wall's three-cell section.**
  `attemptPlacement` positions a piece by its connector, not by its box, so
  that one rule is what makes any piece meet any other.
"""

from __future__ import annotations

import random
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Sequence, Tuple

from .compose import JIGSAW_ORIENTATION, jigsaw
from .nbtio import BlockState, Coord, Voxels, state

Coord2 = Tuple[int, int]

OUT_VEC = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
OPPOSITE = {"north": "south", "south": "north", "west": "east", "east": "west"}

MILITARY_POOL = "burg:military"
STREET_TARGET = "burg:streets"
STREET_POOL = "burg:plains/streets"

# ── the invariants ──────────────────────────────────────────────────
#
# The wall is three cells thick: outer face, walk, inner face. Two is too thin
# to stand on and four wastes the plot at village scale.
THICK = 3
A_MID = 3                  # across-coordinate of the walk, and of every connector
A_OUT = A_MID - 1          # outer face
A_IN = A_MID + 1           # inner face
OUTER_MARGIN = 2           # x = 0, 1: the hoarding projects into these
#
# The single most important number in the file. Every piece at every level puts
# its highest body block here, so the walk surface is at BODY_TOP + 1 and the
# player stands in cell WALK — always, everywhere. A mixed-tier ring is walkable
# because of this line.
BODY_TOP = 6
WALK = BODY_TOP + 1        # the cell a player's feet occupy: 7
HEAD_CLEAR = 2             # cells above the walk that must stay empty


# Measured, not assumed: on `house_lvl6` the ridge sits at x=4, the west slope
# (x=3) is `facing=east` and the east slope (x=5) is `facing=west`. Both point at
# the ridge. So **`facing` names the direction of the TALL half of a stair.**
# Every comment in this file used to say the opposite and every stair was
# therefore reversed.


def shift(cell: Coord2, direction: str, n: int = 1) -> Coord2:
    vx, vz = OUT_VEC[direction]
    return (cell[0] + vx * n, cell[1] + vz * n)


def _st(name: str, **props: str) -> BlockState:
    return state(name, **props)


# ── masonry vocabularies ────────────────────────────────────────────

@dataclass(frozen=True)
class Masonry:
    """One stone vocabulary, over a base that never changes between tiers.

    **Cobblestone and stone are the base at every level**; only `grad` varies.
    They are what a village digs, they carry the bulk of the wall, and keeping
    them fixed is what makes the gradient stone read as a gradient rather than as
    the wall's identity. Letting each tier choose its own dominant made
    `stone_bricks` 75% of a wall, which reads as a palace someone else built.

    **Rough stone only** — no bricks, no polished, no chiselled. Everything here
    can be mined and placed with no crafting step, which also makes the ladder
    honest: it is about digging deeper, not about learning stonecutting.
    """

    grad: str                    # the gradient stone: what makes this tier itself
    cut: str                     # stairs/slab/wall family prefix
    main: str = "cobblestone"    # base, half one
    base2: str = "stone"         # base, half two
    weathered: str = "mossy_cobblestone"

    def block(self, which: str = "main") -> BlockState:
        return _st(getattr(self, which))

    def stairs(self, facing: str = "north", half: str = "bottom") -> BlockState:
        return _st(f"{self.cut}_stairs", facing=facing, half=half,
                   shape="straight", waterlogged="false")

    def slab(self, type_: str = "bottom") -> BlockState:
        return _st(f"{self.cut}_slab", type=type_, waterlogged="false")

    def rail(self) -> BlockState:
        """A wall block: 1.5 tall, so a railing you can see over but not cross.

        This is the one job a `*_wall` block is actually right for. Using them
        as merlons — which the first version did all the way round every piece —
        is neither how a battlement is built nor how anyone builds in Minecraft.
        """
        return _st(f"{self.cut}_wall", up="true", north="none", south="none",
                   west="none", east="none", waterlogged="false")


# The ladder is the gradient stone getting deeper and rarer while the base holds.
COBBLE = Masonry(grad="andesite", cut="cobblestone")
TUFF = Masonry(grad="tuff", cut="cobblestone")
# The top level is where the villagers gain stone-WORKING, so its gradient stone
# is worked: stone brick, with a little polished andesite as the finest detail.
# No deepslate and nothing black — dark stone made the wall read as a different,
# colder building, and no Nether stone belongs in a village at all.
WORKED = Masonry(grad="stone_bricks", cut="stone_brick")


@dataclass
class Timber:
    """Oak used as oak: posts stand, beams lie, planks fill panels."""

    post: BlockState = field(default_factory=lambda: _st("oak_log", axis="y"))
    planks: BlockState = field(default_factory=lambda: _st("oak_planks"))
    stripped: BlockState = field(default_factory=lambda: _st(
        "stripped_oak_log", axis="y"))

    def beam(self, axis: str) -> BlockState:
        """A horizontal beam. Stripped, so the frame reads against the panels."""
        return _st("stripped_oak_log", axis=axis)

    def stairs(self, facing: str = "north", half: str = "bottom") -> BlockState:
        return _st("oak_stairs", facing=facing, half=half, shape="straight",
                   waterlogged="false")

    def slab(self, type_: str = "bottom") -> BlockState:
        return _st("oak_slab", type=type_, waterlogged="false")

    def fence(self, axis: Optional[str] = None) -> BlockState:
        """A rail. Connected along its run, or the run reads as loose stumps."""
        props = dict(north="false", south="false", west="false", east="false",
                     waterlogged="false")
        if axis == "z":
            props.update(north="true", south="true")
        elif axis == "x":
            props.update(east="true", west="true")
        return _st("oak_fence", **props)

    def door(self, facing: str, half: str, hinge: str) -> BlockState:
        return _st("oak_door", facing=facing, half=half, hinge=hinge,
                   open="false", powered="false")

    def trapdoor(self, facing: str, half: str = "top") -> BlockState:
        return _st("oak_trapdoor", facing=facing, half=half, open="false",
                   powered="false", waterlogged="false")


GROUND = ("coarse_dirt", "coarse_dirt", "dirt_path", "dirt", "packed_mud")


@dataclass
class Palette:
    """Everything a tier builds from."""

    stone: Masonry = COBBLE
    wood: Timber = field(default_factory=Timber)

    def grass(self) -> BlockState:
        return _st("grass_block", snowy="false")

    def trodden(self, rng: random.Random) -> BlockState:
        return _st(GROUND[rng.randrange(len(GROUND))])

    def tuft(self) -> BlockState:
        return _st("short_grass")

    def leaves(self) -> BlockState:
        return _st("oak_leaves", persistent="true", distance="1",
                   waterlogged="false")

    def torch(self, facing: str) -> BlockState:
        """A wall torch. `facing` points away from its support.

        Torches, not lanterns, and only ever on the inner face. The reference
        wall `9432` hangs lanterns outside, but that is a showcase render, not a
        fortification: light on the outer face illuminates the ground an
        attacker is standing on.
        """
        return _st("wall_torch", facing=facing)


# ── gradient ramps ──────────────────────────────────────────────────
#
# A gradient is an ORDERED chain of stones whose textures blend into their
# neighbours, and at any one height only the TWO adjacent steps are mixed. The
# references state the chains outright (`61287e8a`, `e738d707`) and show them
# built (`c57b33be`, `9d7fa607`).
#
# Two details stop it coming out too clean, which was the verdict on the first
# attempt where whole courses measured 100% one block:
#   * a step is a GROUP of stones that look alike, not one block;
#   * the ramp position is JITTERED per cell, so a band never resolves to a
#     single stone however far it is from a transition.
#
# Water soaks up from the ground, so the damp end is the BOTTOM and the chain
# climbs to the cleanest stone at the head. Three stones per level; moss is
# weathering and does not consume a slot.
RAMPS: Dict[str, Tuple[Tuple[str, ...], ...]] = {
    "cobble": (("mossy_cobblestone",),
               ("cobblestone", "mossy_cobblestone"),
               ("cobblestone", "stone"),
               ("stone", "andesite", "cobblestone")),
    "tuff": (("mossy_cobblestone",),
             ("cobblestone", "mossy_cobblestone"),
             ("cobblestone", "stone"),
             ("stone", "tuff", "cobblestone")),
    # The top level is where the villagers gain stone-WORKING, so its clean end
    # is worked stone — a minority accent over the rough field, never the field.
    "hoarding": (("mossy_cobblestone",),
                 ("cobblestone", "mossy_cobblestone"),
                 ("cobblestone", "stone", "stone_bricks"),
                 ("stone", "stone_bricks", "cobblestone")),
}
RAMP_JITTER = 0.42


def ramp_block(tier: Tier, y: int, rng: random.Random,
               bias: float = 0.0) -> Optional[BlockState]:
    """Dither between the two ramp steps straddling this height.

    `bias` nudges the position toward the clean end — used by piers, so they read
    stronger than the field without stepping off the chain.
    """
    steps = RAMPS.get(tier.key)
    if not steps:
        return None
    top = len(steps) - 1
    t = (y - 1) / max(1, tier.body_top - 1) * top + bias
    t = max(0.0, min(float(top), t + rng.uniform(-RAMP_JITTER, RAMP_JITTER)))
    i = int(t)
    group = steps[top] if i >= top else (
        steps[i + 1] if rng.random() < (t - i) else steps[i])
    return _st(group[rng.randrange(len(group))])


# ── tiers ───────────────────────────────────────────────────────────

@dataclass
class Tier:
    """One rung of the fortification ladder."""

    key: str
    note: str
    stone: Masonry = COBBLE
    rampart: bool = False      # earth bank behind a log stockade
    frame: bool = False        # timber frame above a masonry plinth
    plinth: int = 0
    piers: bool = False
    loops: bool = False        # arrow loops
    merlons: bool = False
    oversail: bool = False     # projecting parapet on brackets
    dressed: bool = False      # plank walk and interior torches
    hoarding: bool = False     # covered timber fighting gallery
    # Body height. Every tier shares BODY_TOP so a mixed-tier ring has no step —
    # except the earth rampart, which is half height on purpose: it is a stockade
    # you stand behind, not a curtain you patrol, and at full height it cost 62
    # logs and a hill of earth for one segment. Its walk sits lower, so the joint
    # between a raised segment and an unraised one has a step. That is an upgrade
    # boundary rather than a finished wall, and it buys half the material.
    body_top: int = BODY_TOP

    @property
    def walk(self) -> int:
        """The cell this tier's walk surface is stood in.

        Ask the tier, never the module constant. Halving the rampart and leaving
        the gate and the tower on `WALK` is exactly how level 0 came out with its
        curtain walking at 4 and its gatehouse at 7 — the two pieces could not be
        joined, and the gate's own stair climbed to a floor that was not there.
        """
        return self.body_top + 1


TIERS: Tuple[Tier, ...] = (
    Tier("rampart", "low earth bank behind an oak stockade", rampart=True,
         body_top=BODY_TOP // 2),
    Tier("frame", "oak frame on a cobblestone plinth", frame=True, plinth=2),
    Tier("cobble", "cobble and stone, andesite gradient, merlons",
         stone=COBBLE, piers=True, loops=True, merlons=True),
    Tier("tuff", "cobble and stone, tuff gradient, projecting parapet",
         stone=TUFF, piers=True, loops=True, merlons=True, oversail=True,
         dressed=True),
    Tier("hoarding", "worked stone: brick and stone, under an oak gallery",
         stone=WORKED, piers=True, loops=True, oversail=True, dressed=True,
         hoarding=True),
)


def box_height(tier: Tier, extra: int = 0) -> int:
    """A generous box; `trim` cuts it back to what actually got built."""
    h = WALK + HEAD_CLEAR + 2
    if tier.hoarding:
        h += 4
    return h + extra


def trim(vox: Voxels, pad: int = 1) -> Voxels:
    """Shrink the declared box to the built content plus `pad`.

    The author leaves headroom in 70 of his 98 buildings, so a little is in
    keeping. Five layers, which the hand-computed heights produced, is not.
    """
    want = min(vox.size[1], vox.top_y() + 1 + pad)
    if want != vox.size[1]:
        vox.size = (vox.size[0], want, vox.size[2])
    return vox


# ── stations ────────────────────────────────────────────────────────

@dataclass(frozen=True)
class Station:
    """One cell-wide slice through the wall, with its outward direction."""

    outer: Coord2
    mid: Coord2
    inner: Coord2
    out: str
    i: int

    @property
    def run_axis(self) -> str:
        """The axis the run travels along — perpendicular to `out`."""
        return "z" if self.out in ("west", "east") else "x"

    def face(self, n: int) -> Coord2:
        """`n` cells outward from the outer face; 0 is the face itself."""
        return shift(self.outer, self.out, n)

    def back(self, n: int) -> Coord2:
        return shift(self.inner, OPPOSITE[self.out], n)


def straight_run(out: str, run_axis: str, lo: int, hi: int,
                 start: int = 0) -> List[Station]:
    """Stations for a straight run, keyed off `A_MID`."""
    inward = OPPOSITE[out]
    idx = 0 if run_axis == "z" else 1
    out_a = A_MID + OUT_VEC[out][idx]
    in_a = A_MID + OUT_VEC[inward][idx]
    stations = []
    for k, p in enumerate(range(lo, hi + 1)):
        if run_axis == "z":
            cells = ((out_a, p), (A_MID, p), (in_a, p))
        else:
            cells = ((p, out_a), (p, A_MID), (p, in_a))
        stations.append(Station(cells[0], cells[1], cells[2], out, start + k))
    return stations


# ── ground ──────────────────────────────────────────────────────────

def lay_ground(vox: Voxels, pal: Palette, rng: random.Random,
               under: Sequence[Coord2]) -> None:
    """Grass over the plot, worn earth under and beside the wall.

    Varied cell by cell deliberately: a uniform apron is a large block of
    identical cells and it dominates the mirror-symmetry score, which is the
    loudest generated-build tell in the corpus profile.
    """
    sx, _sy, sz = vox.size
    worn = set(under)
    for c in list(under):
        for d in OUT_VEC:
            worn.add(shift(c, d))
    for x in range(sx):
        for z in range(sz):
            if (x, z) in worn or rng.random() < 0.12:
                vox.set((x, 0, z), pal.trodden(rng))
            else:
                vox.set((x, 0, z), pal.grass())


# ── the wall body ───────────────────────────────────────────────────

@dataclass
class ColumnStyle:
    """Per-station choices, fixed for the whole column.

    Streaking is a column property in every reference, never a per-cell coin
    flip: `2b41` and `0fee` both read as a row of distinct vertical strips.
    """

    dominant: str            # "main" or "second"
    skirt_to: int            # weathering climbs the face to this y
    is_pier: bool


def column_styles(stations: Sequence[Station], tier: Tier,
                  rng: random.Random) -> Dict[int, ColumnStyle]:
    styles: Dict[int, ColumnStyle] = {}
    n = len(stations)
    for k, st in enumerate(stations):
        # `9432` reads as a one-wide pier against a four-wide bay.
        is_pier = tier.piers and (k == 0 or k == n - 1 or k % 4 == 0)
        # Roughly two in five non-pier columns take the contrasting tone. At the
        # earlier one-in-three it measured out at two andesite blocks in a whole
        # segment — the device was in the code and not in the wall.
        dom = "main" if is_pier or rng.random() < 0.58 else "grad"
        styles[st.i] = ColumnStyle(dom, rng.choice((1, 1, 2, 2, 3)), is_pier)
    return styles


def build_body(vox: Voxels, pal: Palette, tier: Tier,
               stations: Sequence[Station], styles: Dict[int, ColumnStyle],
               rng: random.Random) -> None:
    """Raise the three-cell section to `BODY_TOP` for every station."""
    if tier.rampart:
        _build_rampart(vox, pal, tier, stations, rng)
        return

    m = tier.stone
    for st in stations:
        cs = styles[st.i]
        for y in range(1, tier.body_top + 1):
            vox.set((st.outer[0], y, st.outer[1]),
                    _face_block(m, cs, y, rng, tier))
            # The core is never seen; spending the contrast tone here would
            # only dilute the two-tone on the faces.
            vox.set((st.mid[0], y, st.mid[1]), m.block("main"))
            vox.set((st.inner[0], y, st.inner[1]),
                    ramp_block(tier, y, rng) or _base(m, rng))

    if tier.frame:
        _timber_frame(vox, pal, tier, stations, rng)


def _base(m: Masonry, rng: random.Random, stone_share: float = 0.30) -> BlockState:
    """The field: cobblestone with stone mixed through it.

    The base is two stones, not one. Both are what a village digs, and letting
    them share the field is what keeps the gradient stone as a gradient.
    """
    return m.block("base2") if rng.random() < stone_share else m.block("main")


def _face_block(m: Masonry, cs: ColumnStyle, y: int,
                rng: random.Random, tier: Optional[Tier] = None) -> BlockState:
    """The outer face, painted off the tier's gradient ramp.

    Replaces choosing a dominant per column out of the whole palette. That put
    mossy cobblestone next to andesite — two steps apart on the ramp — which is
    exactly the harsh pairing a ramp exists to prevent, and it read as grey mush.
    """
    if tier is not None:
        # A pier follows the ramp too, shifted a little toward the clean end so
        # it reads as the stronger column without leaving the chain. Giving it
        # the gradient stone at every height put andesite — and worked stone at
        # the top level — down in the damp foot, next to moss, which is the
        # two-steps-apart pairing the ramp exists to prevent.
        blk = ramp_block(tier, y, rng, bias=0.7 if cs.is_pier else 0.0)
        if blk is not None:
            return blk
    if cs.is_pier:
        return m.block("grad")
    if y <= cs.skirt_to:
        return m.block("weathered")
    return _base(m, rng)


def _timber_frame(vox: Voxels, pal: Palette, tier: Tier,
                  stations: Sequence[Station], rng: random.Random) -> None:
    """Level 1: masonry plinth, then a real oak frame above it.

    Posts stand at bay intervals, a beam course lies on the plinth and again
    under the head, and planks only ever fill the panel between them. The first
    version filled the whole storey with planks, which is what made it read as
    a fence rather than as framing.
    """
    w = pal.wood
    axis = stations[0].run_axis if stations else "z"
    for st in stations:
        post = (st.i % 3 == 0)
        for y in range(tier.plinth + 1, tier.body_top + 1):
            beam_course = y in (tier.plinth + 1, tier.body_top)
            for cell in (st.outer, st.mid, st.inner):
                if post and cell is st.outer:
                    blk = w.post
                elif beam_course:
                    blk = w.beam(axis)
                else:
                    blk = w.planks
                vox.set((cell[0], y, cell[1]), blk)


def _build_rampart(vox: Voxels, pal: Palette, tier: Tier,
                   stations: Sequence[Station], rng: random.Random) -> None:
    """Level 0: an oak stockade with an earth bank behind it.

    The bank is solid to the walk elevation, which is what lets the earliest
    tier be walked exactly like the stone ones — no reference covers a palisade,
    so this is built from what it is rather than copied.
    """
    w = pal.wood
    top = tier.body_top
    for st in stations:
        for y in range(1, top + 1):
            vox.set((st.outer[0], y, st.outer[1]), w.post)
            vox.set((st.mid[0], y, st.mid[1]), pal.trodden(rng))
            vox.set((st.inner[0], y, st.inner[1]), pal.trodden(rng))
        # A grass crust on the bank, as an earth rampart would grow.
        vox.set((st.mid[0], top, st.mid[1]),
                pal.grass() if rng.random() < 0.6 else pal.trodden(rng))
        vox.set((st.inner[0], top, st.inner[1]), pal.grass())


# ── devices ─────────────────────────────────────────────────────────

def arrow_loops(vox: Voxels, tier: Tier, stations: Sequence[Station],
                styles: Dict[int, ColumnStyle], rng: random.Random) -> None:
    """One-deep recesses in the outer course, in pairs (`1b95`, `81de`).

    With three cells of thickness, clearing the outer cell leaves a dark slot
    with wall behind it. Placed at eye height for someone standing on the walk,
    which is the only height at which a loophole means anything.
    """
    if not tier.loops:
        return
    y = tier.body_top - 2
    if y < 1:
        return                      # too short to have an eye height
    k = 0
    while k < len(stations) - 1:
        st = stations[k]
        if styles[st.i].is_pier or rng.random() < 0.35:
            k += 1
            continue
        pair = 2 if (k + 1 < len(stations)
                     and not styles[stations[k + 1].i].is_pier) else 1
        for j in range(pair):
            t = stations[k + j]
            vox.set((t.outer[0], y, t.outer[1]), None)
        k += pair + rng.choice((2, 3, 3, 4))


def rubble(vox: Voxels, pal: Palette, tier: Tier,
           stations: Sequence[Station], rng: random.Random) -> None:
    """Loose stone lying on the ground at the foot (`2b41`)."""
    m = tier.stone
    for st in stations:
        if rng.random() > 0.26:
            continue
        cell = st.face(1)
        p = (cell[0], 1, cell[1])
        if in_box(vox, p) and not vox.occupied(p):
            vox.set(p, m.slab("bottom") if rng.random() < 0.55
                    else m.block("weathered"))


def base_planting(vox: Voxels, pal: Palette, stations: Sequence[Station],
                  rng: random.Random) -> None:
    """Tufts against the foot, on some bays and not others (`9432`)."""
    for st in stations:
        cell = st.face(1)
        p = (cell[0], 1, cell[1])
        if in_box(vox, p) and not vox.occupied(p) and rng.random() < 0.20:
            vox.set(p, pal.tuft())


def climbing_leaves(vox: Voxels, pal: Palette, stations: Sequence[Station],
                    rng: random.Random) -> None:
    """Leaf masses clinging to the outer face, in clumps of two or three.

    Only ever clumped and only ever against something solid; a lone leaf cube
    reads as a mistake. `compose.tidy_leaves` is the final backstop.
    """
    k = 1
    while k < len(stations) - 1:
        # Sparser than it was. Leaves hide the stonework the gradient exists
        # to show, and too many of them read as noise rather than as planting.
        if rng.random() < 0.82:
            k += 1
            continue
        cell = stations[k].face(1)
        base = rng.choice((1, 2))
        for dy in range(rng.choice((2, 3))):
            p = (cell[0], base + dy, cell[1])
            if in_box(vox, p) and not vox.occupied(p):
                vox.set(p, pal.leaves())
        nxt = stations[k + 1].face(1)
        for dy in range(rng.choice((1, 2))):
            p = (nxt[0], base + dy, nxt[1])
            if in_box(vox, p) and not vox.occupied(p):
                vox.set(p, pal.leaves())
        k += rng.choice((3, 4, 5))


def in_box(vox: Voxels, p: Coord) -> bool:
    sx, sy, sz = vox.size
    return 0 <= p[0] < sx and 0 <= p[1] < sy and 0 <= p[2] < sz


def clear_walk(vox: Voxels, stations: Sequence[Station],
               walk: int = WALK) -> None:
    """Guarantee the invariant: the walk lane is passable for two cells up.

    Called last on every piece, so devices may be careless about the walk lane —
    a promise enforced in one place is one that can be tested.

    *Passable*, not *empty*: it asks `traverse` the same question the test asks.
    An earlier version emptied the lane outright and silently deleted every
    torch it had just hung there, which is how the wall ended up with interior
    lighting in the code and none in the output.
    """
    from .traverse import is_passable
    for st in stations:
        for y in range(walk, walk + HEAD_CLEAR):
            p = (st.mid[0], y, st.mid[1])
            if not is_passable(vox.get(p)):
                vox.set(p, None)


def step_point(stations: Sequence[Station], rng: random.Random) -> int:
    """Where along the run the parapet steps up a course (`55a3`, `0fee`).

    A curtain that runs dead level for its whole length is the piece most at
    risk of reading as extruded geometry, and it is also the one the corpus gate
    rejects: eight identical stations put mirror_z above the author's ceiling.
    `0fee` never runs level. The device and the fix are the same thing.

    Only the parapet steps. The walk itself must not, or the ring needs a jump.
    """
    n = len(stations)
    if n < 4:
        return n + 1
    return rng.randrange(n // 3, max(n // 3 + 1, (2 * n) // 3))


def wall_head(vox: Voxels, pal: Palette, tier: Tier,
              stations: Sequence[Station], styles: Dict[int, ColumnStyle],
              rng: random.Random, step_at: int = 10 ** 6) -> None:
    """Walk surface, parapets, merlons and interior lighting.

    The crenellation is sized to be used, not just seen. The outer parapet is
    one block at the embrasures — chest height to someone standing on the walk,
    so they can see and shoot over it — and two blocks at the merlons, which is
    the cover between the embrasures. That is the whole point of a battlement,
    and it is also what makes walking the wall worth doing.
    """
    if tier.rampart:
        _rampart_head(vox, pal, tier, stations, rng)
        return
    if tier.hoarding:
        hoarding(vox, pal, tier, stations, styles, rng, step_at)
        return

    m = tier.stone
    w = pal.wood
    for st in stations:
        cs = styles[st.i]
        # The walk surface. Dressed levels pave it in the contrasting stone —
        # tuff on stone brick — so the surface you walk on is visibly a
        # different stone from the wall you walk between. `00ed` boards its
        # walk in timber and that reads well there, but on a masonry curtain a
        # plank floor is oak doing a job stone should do.
        vox.set((st.mid[0], tier.body_top, st.mid[1]),
                m.block("base2") if tier.dressed else m.block("main"))
        # The outer face's own top course stays masonry: floor material there is
        # visible from outside and reads as the floor sticking out of the wall.
        vox.set((st.outer[0], tier.body_top, st.outer[1]), m.block("main"))

        if tier.oversail:
            out1 = st.face(1)
            # The parapet oversails the face for the whole length. Thickening it
            # to two cells did hide the exposed deck edge, but by making the head
            # fat — a symptom fix. The edge is dealt with where it comes from:
            # the top course of the outer row is masonry, not floor material.
            vox.set((out1[0], tier.walk, out1[1]), m.block("main"))
            if cs.is_pier:
                # A bracket steps the oversail down onto the pier.
                vox.set((out1[0], tier.body_top, out1[1]), m.block("main"))
                vox.set((out1[0], tier.body_top - 1, out1[1]),
                        m.stairs(facing=st.out, half="top"))
            # else: nothing at (out1, body_top) — the shadow slot.
        else:
            vox.set((st.outer[0], tier.walk, st.outer[1]), m.block("main"))

    if tier.merlons:
        _merlons(vox, pal, tier, stations, rng)
    else:
        # The timber-framed tier gets a timber head. Stone battlements on an oak
        # wall is the kind of mismatch that makes a progression look accidental.
        _timber_crest(vox, pal, tier, stations)
    _inner_rail(vox, pal, tier, stations, rng)
    # From the first tier that has a real walk. A wall someone stands watch on
    # is a wall that needs light to stand watch by; only the earth rampart, which
    # predates the garrison, goes without.
    _interior_lighting(vox, pal, tier, stations, rng)


def _merlons(vox: Voxels, pal: Palette, tier: Tier,
             stations: Sequence[Station], rng: random.Random) -> None:
    """Merlons: full blocks, exactly two courses, at irregular spacing (`9432`).

    **Two courses and no more.** `wall_head` has already laid the parapet base
    at WALK, so a merlon is that plus one — top at WALK + 2. A player standing on
    the walk has their eyes at about WALK + 1.6, which is above the embrasure
    (one course, top at WALK + 1) and below the merlon. That is the entire point
    of a battlement: see and shoot over the low part, take cover behind the high
    part.

    The earlier version stacked a step riser, the merlon and a slab cap on top of
    that base and reached four courses — top at WALK + 4, more than two blocks
    above eye level. Nothing could be seen over it and each one stood up like a
    horn. The stepped top profile went with it: on an eight-cell segment it does
    not read as the terracing it does across a long run in `0fee`, it reads as one
    tooth being taller than its neighbours.

    Full blocks, not `*_wall` blocks, and no slab cap either.
    """
    m = tier.stone
    line = 1 if tier.oversail else 0
    k = 0
    while k < len(stations):
        run = rng.choice((2, 2, 2, 3))
        span = min(run, len(stations) - k)
        for j in range(span):
            cell = stations[k + j].face(line)
            vox.set((cell[0], tier.walk + 1, cell[1]), m.block("main"))
        k += run + rng.choice((1, 2, 2))


def _timber_crest(vox: Voxels, pal: Palette, tier: Tier,
                  stations: Sequence[Station]) -> None:
    """Level 1's head: oak posts with a fence rail between them."""
    w = pal.wood
    axis = stations[0].run_axis if stations else "z"
    for st in stations:
        if st.i % 3 == 0:
            vox.set((st.outer[0], tier.walk, st.outer[1]), w.post)
            vox.set((st.outer[0], tier.walk + 1, st.outer[1]), w.post)
        else:
            vox.set((st.outer[0], tier.walk, st.outer[1]), w.beam(axis))
            vox.set((st.outer[0], tier.walk + 1, st.outer[1]), w.fence(axis))


def _inner_rail(vox: Voxels, pal: Palette, tier: Tier,
                stations: Sequence[Station], rng: random.Random) -> None:
    """A railing on the inside, so the walk can be used without falling off.

    Deliberately unlike the outer parapet: a wall whose two faces match is
    mirror-symmetric by construction.

    **Oak fence, not a `*_wall` block.** A stone-wall block is the technically
    correct see-over railing and it still looks odd in quantity, which is the
    verdict that matters. A timber rail on a stone parapet is also the truer
    thing: it is the cheap part, the part that gets replaced, and it ties the
    curtain to the hoarding gallery that arrives at the top tier.
    """
    m = tier.stone
    w = pal.wood
    for st in stations:
        post = st.i % 4 == 0
        # A post every few bays so the rail reads as carpentry, not as a line —
        # and the post runs DOWN to the floor rather than sitting on top of the
        # masonry as a single cube. A log that starts and stops in mid-air reads
        # as a block someone forgot to remove; a post is planted.
        if post:
            vox.set((st.inner[0], tier.body_top, st.inner[1]), w.post)
            vox.set((st.inner[0], tier.walk, st.inner[1]), w.post)
        else:
            vox.set((st.inner[0], tier.body_top, st.inner[1]),
                    m.block("main") if tier.merlons else w.beam(st.run_axis))
            vox.set((st.inner[0], tier.walk, st.inner[1]),
                    w.fence(st.run_axis))


def _interior_lighting(vox: Voxels, pal: Palette, tier: Tier,
                       stations: Sequence[Station],
                       rng: random.Random) -> None:
    """Torches on the inner face, pointing into the walk.

    A wall torch hangs on the block behind it, so the torch sits in the walk
    lane with its back against the inner railing and `facing` pointing inward.
    """
    for st in stations:
        if rng.random() > 0.22:
            continue
        # `facing` names the direction the torch points away from its support.
        facing = st.out
        p = (st.mid[0], tier.walk, st.mid[1])
        support = (st.inner[0], tier.walk, st.inner[1])
        if vox.occupied(support) and not vox.occupied(p):
            vox.set(p, pal.torch(facing))


def _rampart_head(vox: Voxels, pal: Palette, tier: Tier,
                  stations: Sequence[Station], rng: random.Random) -> None:
    """Level 0's head: the stockade crest, rough and uneven."""
    w = pal.wood
    walk = tier.body_top + 1
    axis = stations[0].run_axis if stations else "z"
    for st in stations:
        vox.set((st.outer[0], walk, st.outer[1]), w.post)
        # Stakes of unequal height: the ragged skyline is what carries a palisade.
        if st.i % 3 != 2:
            vox.set((st.outer[0], walk + 1, st.outer[1]), w.post)
        vox.set((st.inner[0], walk, st.inner[1]), w.fence(axis))


def hoarding(vox: Voxels, pal: Palette, tier: Tier,
             stations: Sequence[Station], styles: Dict[int, ColumnStyle],
             rng: random.Random, step_at: int) -> None:
    """The covered timber fighting gallery (`0fee`, `81de`).

    The most valuable device in the reference set: a large, unmistakably
    military upgrade costing only oak on top of a wall the town already has.

    The correction over the first attempt is the whole point of the level. A
    hoarding is not an ornament sitting on the parapet — it is the fighting
    deck, and you stand IN it. So its floor is the top of the stone wall,
    carried outward on brackets, and the roof clears two cells above. Built the
    other way it left one cell of headroom and nothing could stand there.

        roof         oak stairs and slabs, spanning the whole width
        loophole     the void between posts — the dark bays of the reference
        rail         fence between oak posts, at the gallery's outer edge
        floor        stone wall top, extended outward over beam brackets
        brackets     stripped log beams projecting from the face
    """
    m = tier.stone
    w = pal.wood
    axis = stations[0].run_axis if stations else "z"
    y_roof = tier.walk + HEAD_CLEAR                 # 9: clears the walk

    for st in stations:
        out1, out2 = st.face(1), st.face(2)
        # Brackets, every other station, visibly carrying the floor.
        if st.i % 2 == 0:
            vox.set((out1[0], tier.body_top - 1, out1[1]), w.beam(
                "x" if axis == "z" else "z"))
        # Floor: the stone wall top, continued outward one cell.
        vox.set((st.outer[0], tier.body_top, st.outer[1]), m.block("main"))
        vox.set((st.mid[0], tier.body_top, st.mid[1]), w.planks)
        vox.set((out1[0], tier.body_top, out1[1]), w.planks)

        # Rail and posts at the gallery's outer edge; the gap above the rail is
        # the loophole, and it is a void rather than a block.
        if st.i % 3 == 0:
            vox.set((out1[0], tier.walk, out1[1]), w.post)
            vox.set((out1[0], tier.walk + 1, out1[1]), w.post)
        else:
            vox.set((out1[0], tier.walk, out1[1]), w.fence(axis))
        # Inner rail, so the gallery can be walked without falling off.
        vox.set((st.inner[0], tier.walk, st.inner[1]), w.fence(axis))

        # Roof: a shallow gable over the whole gallery, clear of the walk. The
        # eave oversails at ROOF level, not at floor level — carried out one
        # further cell as a slab. Put at the floor instead it became a ledge
        # sticking out past the rail, which is both wrong to look at and a strip
        # of standable cells nobody can ever reach.
        vox.set((out2[0], y_roof, out2[1]), w.slab("bottom"))
        vox.set((out1[0], y_roof, out1[1]),
                w.stairs(facing=OPPOSITE[st.out], half="bottom"))
        vox.set((st.outer[0], y_roof, st.outer[1]), w.planks)
        vox.set((st.mid[0], y_roof, st.mid[1]), w.planks)
        vox.set((st.inner[0], y_roof, st.inner[1]),
                w.stairs(facing=st.out, half="bottom"))
        vox.set((st.outer[0], y_roof + 1, st.outer[1]), w.slab("bottom"))
        vox.set((st.mid[0], y_roof + 1, st.mid[1]), w.slab("bottom"))

    # Same reasoning as `_devices(repeating=)`: only the shorter runs, which
    # belong to corners and towers, get roof planting.
    if len(stations) < RUN_LEN:
        _roof_planting(vox, pal, stations, y_roof + 1, rng)
    _interior_lighting(vox, pal, tier, stations, rng)


def _roof_planting(vox: Voxels, pal: Palette, stations: Sequence[Station],
                   y: int, rng: random.Random) -> None:
    """Leaves lying on the roof, in clumps (`0fee`, `81de`)."""
    k = 1
    while k < len(stations) - 1:
        if rng.random() < 0.78:
            k += 1
            continue
        for j in range(rng.choice((2, 3))):
            if k + j >= len(stations):
                break
            c = stations[k + j].mid
            p = (c[0], y + 1, c[1])
            if in_box(vox, p) and not vox.occupied(p):
                vox.set(p, pal.leaves())
        k += rng.choice((3, 4))


# ── connectors ──────────────────────────────────────────────────────

def connector(vox: Voxels, pos: Coord, facing: str, entry: bool,
              target: str = MILITARY_POOL, pool: str = MILITARY_POOL) -> None:
    """Place one jigsaw marker.

    The distinction that matters: `BuildSchematic.readJigsawPoints` **skips
    every connector whose pool is empty**, so a terminator is consumed on
    attachment and never becomes a free connection point. A piece with two
    terminators can be placed but leaves nothing to build onto, which is why
    the earliest wall segments could not chain at all.

    Each piece therefore carries exactly one terminator (`entry=True`) and one
    or more active connectors that survive into the town's free list. This is
    the author's own arrangement: `street_1` has three active and one
    terminator.
    """
    vox.set(pos, state("jigsaw", orientation=JIGSAW_ORIENTATION[facing]),
            jigsaw(target, "minecraft:empty" if entry else pool))


# ── pieces ──────────────────────────────────────────────────────────
#
# Fixed plans, one per kind. All of them put the walk on `A_MID` at `WALK`, so
# any piece meets any other with no step.
#
#   x = 0, 1    outer margin — the hoarding gallery projects into these
#   x = 2       outer face      x = 3  walk      x = 4  inner face
#   x = 5       inner margin

RUN_LEN = 8            # stations in a straight segment
CORNER_ARM = 4         # stations per arm beyond the elbow
TOWER_SIDE = 5
TOWER_RISE = WALK + 3  # highest tower block: its deck is walked at WALK + 4


def tower_rise(tier: Tier) -> int:
    """The tower's top solid course: three above this tier's own walk.

    A tower has to clear the curtain it flanks, so it is measured from the walk
    rather than from the module constant. On the halved rampart the constant put
    the deck six cells above a bank three courses high, and the ladder that
    climbed to it started from a floor the piece no longer had.
    """
    return tier.walk + 3
# The column the tower ladder occupies, and the wall it hangs on at LADDER_Z - 1.
# It is a constant because two passes have to agree about it: the arrow slits are
# cut before the ladder is hung, so they cannot look for a ladder that is not
# there yet — they have to know the coordinate instead. Cutting one here left a
# rung with nothing behind it and a gap in the climb.
LADDER_Z = 2


def _devices(vox: Voxels, pal: Palette, tier: Tier,
             runs: Sequence[List[Station]], seed: int,
             repeating: bool = False) -> None:
    """The full device stack, then the walk-clearance guarantee.

    `repeating` marks a piece that tiles — the straight segment. Distinctive
    decoration is suppressed there: a leaf clump baked into a tiling piece is not
    randomness, it is a pattern. The same clump lands in the same cell of every
    segment of the run and the eye catches the repeat at once. A single NBT cannot
    vary between its own placements, so the only safe amount is none.
    """
    for ri, stations in enumerate(runs):
        rng = random.Random(seed * 131 + ri * 7717 + 11)
        styles = column_styles(stations, tier, rng)
        step_at = step_point(stations, rng)
        build_body(vox, pal, tier, stations, styles, rng)
        arrow_loops(vox, tier, stations, styles, rng)
        wall_head(vox, pal, tier, stations, styles, rng, step_at)
        rubble(vox, pal, tier, stations, rng)
        base_planting(vox, pal, stations, rng)
        if not repeating:
            climbing_leaves(vox, pal, stations, rng)
        clear_walk(vox, stations, tier.body_top + 1)


def compose_straight(tier: Tier, seed: int = 0,
                     pal: Optional[Palette] = None) -> Voxels:
    """A plain run of curtain wall: most of the perimeter is this piece."""
    pal = pal or Palette(stone=tier.stone)
    size = (OUTER_MARGIN + THICK + 1, box_height(tier), RUN_LEN)
    vox = Voxels(size, {}, "wall_segment")
    stations = straight_run("west", "z", 0, RUN_LEN - 1)
    rng = random.Random(seed + 501)
    lay_ground(vox, pal, rng, [c for st in stations
                               for c in (st.outer, st.mid, st.inner)])
    _devices(vox, pal, tier, [stations], seed, repeating=True)
    connector(vox, (A_MID, 0, 0), "north", entry=True)
    connector(vox, (A_MID, 0, RUN_LEN - 1), "south", entry=False)
    return vox


def compose_corner(tier: Tier, seed: int = 0,
                   pal: Optional[Palette] = None) -> Voxels:
    """The piece that makes the perimeter turn, and therefore close.

    Growth is connector-driven with no notion of a centre or a radius, so a ring
    cannot be planned — it falls out of the geometry. A corner with a connector
    on each of two perpendicular edges is the whole mechanism.

    The elbow is an **open platform** at walk level with a taller parapet round
    it, not the solid bastion the first version built. That block read as a
    corner tower and dammed the walk at all four corners of a ring, which is the
    single worst thing a wall piece can do.
    """
    pal = pal or Palette(stone=tier.stone)
    span = OUTER_MARGIN + THICK + CORNER_ARM        # 9
    size = (span, box_height(tier, extra=2), span)
    vox = Voxels(size, {}, "wall_corner")

    lo, hi = A_OUT, A_IN                            # elbow spans 2..4
    arm_a = straight_run("west", "z", hi + 1, span - 1)
    arm_b = straight_run("north", "x", hi + 1, span - 1)

    ground = [c for st in arm_a + arm_b
              for c in (st.outer, st.mid, st.inner)]
    ground += [(x, z) for x in range(lo, hi + 1) for z in range(lo, hi + 1)]
    rng = random.Random(seed + 733)
    lay_ground(vox, pal, rng, ground)

    _devices(vox, pal, tier, [arm_a, arm_b], seed)
    _elbow(vox, pal, tier, lo, hi, rng)

    # Which end is the entry decides which way the chain turns, and so which
    # side of the finished ring is the outside. It must agree with the straight
    # segment: a straight runs entry(north) -> exit(south) with its outer face
    # west, so travelling the chain the outside is always on the RIGHT. Arm A's
    # outer face is west, so the chain travels south along it — arm A is the
    # exit. Arm B's outer face is north, so the chain travels west along it —
    # arm B is the entry. Backwards, this builds a perfectly closed ring inside
    # out, with the loops and weathering facing the courtyard.
    connector(vox, (span - 1, 0, A_MID), "east", entry=True)
    connector(vox, (A_MID, 0, span - 1), "south", entry=False)
    return vox


def _elbow(vox: Voxels, pal: Palette, tier: Tier, lo: int, hi: int,
           rng: random.Random) -> None:
    """The corner platform: solid to the walk, open above, parapet around it."""
    m = tier.stone
    w = pal.wood
    for x in range(lo, hi + 1):
        for z in range(lo, hi + 1):
            edge = x == lo or z == lo          # the two outer faces
            for y in range(1, tier.body_top + 1):
                if tier.rampart:
                    blk = w.post if edge else pal.trodden(rng)
                elif tier.frame and y > tier.plinth:
                    blk = w.post if (x in (lo, hi) and z in (lo, hi)) \
                        else w.planks
                else:
                    blk = m.block("main") if rng.random() < 0.82 \
                        else m.block("weathered")
                vox.set((x, y, z), blk)
            # The deck. Everything above it is cleared below, so the walk turns
            # through the corner instead of stopping at it.
            #
            # The two outer rows keep a masonry top course. Nobody walks on them —
            # the parapet stands there — and they are the cells you see from
            # outside, so plank floor or grass there reads as the floor sticking
            # out through the wall. This is the cell the corner was reported on:
            # (2, 6, 4) came out `oak_planks`. Thickening the parapet to cover it
            # was the wrong fix; it hid the edge by making the corner fat.
            if edge:
                deck = w.post if tier.rampart else m.block("main")
            else:
                deck = w.planks if (tier.dressed or tier.hoarding) \
                    else (pal.grass() if tier.rampart else m.block("main"))
            vox.set((x, tier.body_top, z), deck)

    # Clear the platform, then rebuild only the parapet. Clearing first is what
    # guarantees the turn is passable regardless of what the arms left behind.
    # It must stop at `hi`: reaching one cell further took the parapet and rail
    # off the arms' first station and opened a hole in the curtain.
    walk = tier.body_top + 1
    for x in range(lo - 1, hi + 1):
        for z in range(lo - 1, hi + 1):
            for y in range(walk, walk + HEAD_CLEAR + 3):
                if in_box(vox, (x, y, z)):
                    vox.set((x, y, z), None)

    if tier.rampart:
        # Same cell rule as the masonry tiers below: the two outer faces get
        # the stockade, only the single inner corner gets a rail, and the turn
        # stays open.
        for x in range(lo, hi + 1):
            for z in range(lo, hi + 1):
                if x == lo or z == lo:
                    vox.set((x, walk, z), w.post)
                elif x == hi and z == hi:
                    vox.set((x, walk, z), w.fence("z"))
        return

    # A parapet one course taller than the curtain's: the corner reads as a
    # bastion without becoming an obstacle.
    #
    # Which elbow cells may be built on is worth being exact about, because the
    # walk has to turn through here. Each cell of the 3x3 sits in BOTH arms'
    # cross-sections at once:
    #
    #        z=2   z=3   z=4          arm A runs along z: outer x=2, walk x=3
    #   x=2  out   out   out          arm B runs along x: outer z=2, walk z=3
    #   x=3  out   WALK  WALK
    #   x=4  out   WALK  rail
    #
    # So the outer parapet is every cell with x == lo or z == lo — both outer
    # faces including where they meet — and the only inner cell is the single
    # inner corner. Testing `x == hi or z == hi` instead walls off (3,4) and
    # (4,3), which are precisely the two cells the turn needs.
    for x in range(lo, hi + 1):
        for z in range(lo, hi + 1):
            if x == lo or z == lo:
                vox.set((x, walk, z), m.block("main"))
                # Carry the parapet one cell out on the oversail tiers too, so the
                # corner reads continuous with the arms instead of stepping back
                # from them at the joint.
                if tier.oversail:
                    for out in ((lo - 1, z) if x == lo else None,
                                (x, lo - 1) if z == lo else None):
                        if out and in_box(vox, (out[0], walk, out[1])):
                            vox.set((out[0], walk, out[1]), m.block("main"))
                if (x + z) % 2 == 0:
                    vox.set((x, walk + 1, z), m.block("main"))
                    vox.set((x, walk + 2, z), m.slab("bottom"))
            elif x == hi and z == hi:
                vox.set((x, walk, z), w.fence(None))
    # One torch at the corner, on the inside face of the outer parapet. It has
    # to hang on a full block: a `*_wall` block has no solid face to take a
    # torch, so the inner railing is not a candidate however convenient it looks.
    vox.set((A_MID, walk, lo + 1), pal.torch("south"))


def compose_gate(tier: Tier, seed: int = 0,
                 pal: Optional[Palette] = None) -> Voxels:
    """A gate in the run: arched passage below, wall walk continuing above.

    From `440b` and `2b41`: stone pierced by an arch, two flanking towers, and a
    timber storey oversailing the passage on a visible beam course. It carries a
    street connector on the outer face so a road grows out of the gate, which is
    the author's own habit on `street_4`.

    The flanking towers are **hollow at walk level**. Built solid they dammed
    the curtain, so a ring with a gate in it could not be walked at all.
    """
    pal = pal or Palette(stone=tier.stone)
    size = (OUTER_MARGIN + THICK + 1, box_height(tier, extra=3), RUN_LEN)
    vox = Voxels(size, {}, "gatehouse")
    stations = straight_run("west", "z", 0, RUN_LEN - 1)
    rng = random.Random(seed + 977)
    lay_ground(vox, pal, rng, [c for st in stations
                               for c in (st.outer, st.mid, st.inner)])
    _devices(vox, pal, tier, [stations], seed)

    # Off centre on purpose: a gate in the middle of the run makes the piece
    # mirror-symmetric, which the corpus gate rejects outright.
    z0 = 3 if seed % 2 == 0 else 4
    _pierce_gate(vox, pal, tier, z0, z0 + 1, rng)

    _courtyard_steps(vox, pal, tier)

    connector(vox, (A_MID, 0, 0), "north", entry=True)
    connector(vox, (A_MID, 0, RUN_LEN - 1), "south", entry=False)
    connector(vox, (0, 0, z0), "west", entry=False,
              target=STREET_TARGET, pool=STREET_POOL)
    return vox


def _courtyard_steps(vox: Voxels, pal: Palette, tier: Tier) -> None:
    """A stepped stone flight on the courtyard side, ground to wall walk.

    **Ladder inside, stepped stone outside.** The tower is climbed by a ladder in
    a shaft; a wall is reached by steps against its inner face, which is what the
    references show and what a mason would build. It also means the walk has a way
    up that is not through a tower.

    The flight lands level with the walk, and the inner railing is opened where it
    arrives — a rail across the top of a stair is a wall.
    """
    # Level 0 gets a timber flight, not a stone one: earth has no stair block, and
    # wood is what that level is made of. Without it the rampart had no way up at
    # all — the bank is vertical, and you cannot walk up a vertical bank.
    m = tier.stone
    w = pal.wood
    timber = tier.rampart or tier.frame
    x = A_IN + 1
    if x >= vox.size[0]:
        return
    # Start one cell in from the edge. Put the bottom tread on the box edge and
    # there is nowhere to step onto it from — the next piece begins there — so the
    # flight was unclimbable despite being built correctly.
    for k in range(tier.body_top):
        z = (RUN_LEN - 2) - k
        y = 1 + k
        if z <= 0:
            break
        # Solid under every tread, or the flight is a staircase of floating steps.
        for fill in range(1, y):
            if not vox.occupied((x, fill, z)):
                vox.set((x, fill, z),
                        pal.trodden(random.Random(z * 31 + fill)) if timber
                        else m.block("main"))
        # The tall half faces the way you are climbing: the run ascends toward
        # -z, so north.
        vox.set((x, y, z), w.stairs(facing="north") if timber
                else m.stairs(facing="north", half="bottom"))
        if y == tier.body_top:
            # Open the rail where it arrives, and keep the landing clear.
            for dy in range(tier.walk, tier.walk + HEAD_CLEAR):
                vox.set((A_IN, dy, z), None)
                vox.set((x, dy, z), None)


HEAD = 3           # passage clearance: a player plus room to spare


def _pierce_gate(vox: Voxels, pal: Palette, tier: Tier, z0: int, z1: int,
                 rng: random.Random) -> None:
    """Cut the passage, arch its head, hang the doors, tower the flanks."""
    m = tier.stone
    w = pal.wood

    # Clearance through the passage. A player is two cells tall, so two is the
    # floor. The halved earth rampart is three courses high and its top course has
    # to carry the walk across the gap, which leaves exactly two — so level 0 gets
    # the minimum and every tier above it gets the extra cell.
    head = max(2, min(HEAD, tier.body_top - 1))
    arched = head + 2 <= tier.body_top

    # The passage, and its head built as a real arch profile rather than as one
    # stair and a full block. Reading upward from the opening:
    #
    #     y = body_top   solid: carries the wall walk over the gate
    #     y = head + 2   stairs, half=top, springing in toward the centre
    #     y = head + 1   top slabs, chamfering the head of the opening
    #     y = 1 .. head  clear
    #
    # The slab course is what makes it read as an arch: the opening narrows in
    # half-block steps instead of stopping dead on a square lintel.
    for z in (z0, z1):
        for x in range(A_OUT, A_IN + 1):
            for y in range(1, head + 1):
                vox.set((x, y, z), None)
            vox.set((x, 0, z), state("dirt_path"))
            if arched:
                vox.set((x, head + 1, z), m.slab("top"))
                vox.set((x, head + 2, z), m.stairs(
                    facing="north" if z == z0 else "south", half="top"))
                # Solid between the arch and the walk floor: the walk is carried.
                for y in range(head + 3, tier.body_top + 1):
                    vox.set((x, y, z), m.block("main"))
            else:
                # A three-course bank has no room to arch. The gap is spanned by
                # a timber lintel — which is what carries the walk across, and
                # what timber is for. Stone that shallow over an opening is the
                # one thing masonry cannot do.
                for y in range(head + 1, tier.body_top + 1):
                    vox.set((x, y, z), w.beam("z"))

    # A projecting slab hood over the outer mouth: a drip course, and the detail
    # that tells you which side of the gate is outside. It needs a wall above it
    # to project from, so the unarched gateway goes without.
    if arched:
        for z in range(z0 - 1, z1 + 2):
            if 0 <= z < vox.size[2]:
                vox.set((A_OUT - 1, head + 2, z), m.slab("bottom"))

    # Double doors in the middle of the tunnel, opening inward.
    if not tier.rampart:
        for z, hinge in ((z0, "left"), (z1, "right")):
            for half in ("lower", "upper"):
                vox.set((A_MID, 1 if half == "lower" else 2, z),
                        w.door("west", half, hinge))

    # Flanking towers. Unequal heights so the gate is not symmetric, and hollow
    # on the walk line so the curtain runs through them.
    for k, z in enumerate((z0 - 1, z1 + 1)):
        if not (0 <= z < vox.size[2]):
            continue
        rise = tier.walk + (3 if k == 0 else 2)
        for y in range(1, rise + 1):
            for x in range(A_OUT, A_IN + 1):
                if tier.rampart:
                    vox.set((x, y, z), w.post)
                else:
                    vox.set((x, y, z), m.block("main") if rng.random() < 0.82
                            else m.block("weathered"))
        # The doorway through the tower: two cells of clearance on the walk.
        for y in range(tier.walk, tier.walk + HEAD_CLEAR):
            vox.set((A_MID, y, z), None)
        # A lintel over the doorway, so it reads as a gate. Timber on the earth
        # tier: a cobblestone lintel in an oak stockade was stone appearing a
        # level before the village has any.
        vox.set((A_MID, tier.walk + HEAD_CLEAR, z),
                w.beam("z") if tier.rampart else m.block("main"))
        if not tier.rampart:
            for x in (A_OUT, A_IN):
                vox.set((x, rise + 1, z), m.block("main"))
                vox.set((x, rise + 2, z), m.slab("bottom"))
        # The hoarding gallery cannot run through a tower; clear its remains.
        for x in (0, 1):
            for y in range(1, vox.size[1]):
                if in_box(vox, (x, y, z)):
                    vox.set((x, y, z), None)

    if tier.frame or tier.oversail:
        _gate_storey(vox, pal, tier, z0, z1)


def _gate_storey(vox: Voxels, pal: Palette, tier: Tier,
                 z0: int, z1: int) -> None:
    """A timber storey over the passage, carried on a visible beam course.

    The beam course is the point: the upper storey must be seen to be carried,
    not to float. Beam ends project past the posts, the detail the isolated gate
    study `1b95` exists to show. It sits above the walk's clearance so the
    curtain still runs underneath it.
    """
    w = pal.wood
    y0 = tier.walk + HEAD_CLEAR         # clear of the walk
    for z in (z0, z1):
        for x in range(A_OUT - 1, A_IN + 1):
            vox.set((x, y0, z), w.beam("z"))
        for y in (y0 + 1, y0 + 2):
            vox.set((A_OUT - 1, y, z), w.planks)
            vox.set((A_OUT, y, z), w.post if y == y0 + 1 else w.planks)
            vox.set((A_MID, y, z), w.planks)
            vox.set((A_IN, y, z), w.planks)
    # Beam ends projecting past the posts.
    for z in (z0 - 1, z1 + 1):
        if 0 <= z < vox.size[2]:
            vox.set((A_OUT - 1, y0, z), w.beam("z"))
    # A pitched cap over the storey.
    y_top = y0 + 3
    for z in (z0, z1):
        vox.set((A_OUT - 1, y_top, z), w.stairs(facing="east"))
        vox.set((A_OUT, y_top, z), w.slab("bottom"))
        vox.set((A_MID, y_top, z), w.slab("bottom"))
        vox.set((A_IN, y_top, z), w.stairs(facing="west"))


def compose_tower(tier: Tier, seed: int = 0,
                  pal: Optional[Palette] = None) -> Voxels:
    """A flanking tower, and the way onto the wall.

    Per `00ed`: square, projecting outward beyond the curtain so it can cover
    the face, rising above the wall head, flat-topped with a crenellated
    parapet.

    It is the only access point in the set, and that is deliberate — a tower is
    where you climb, a curtain is where you walk. So it carries a door at ground
    level on the courtyard side and a **spiral stair from that floor all the way
    to the roof**, passing the wall walk on the way. The first version had an
    external stair that reached the walk and a sealed chamber above it, so the
    top of the tower could not be reached at all.
    """
    pal = pal or Palette(stone=tier.stone)
    depth = 9
    size = (OUTER_MARGIN + THICK + 2, box_height(tier, extra=6), depth)
    vox = Voxels(size, {}, "wall_tower")

    # Stubs of curtain either side, deliberately unequal so the piece is not
    # mirror-symmetric along its own run.
    stub_n = straight_run("west", "z", 0, 0)
    stub_s = straight_run("west", "z", 6, depth - 1, start=6)
    body = [(x, z) for x in range(0, A_IN + 1)
            for z in range(1, TOWER_SIDE + 1)]

    rng = random.Random(seed + 313)
    lay_ground(vox, pal, rng, [c for st in stub_n + stub_s
                               for c in (st.outer, st.mid, st.inner)] + body)
    _devices(vox, pal, tier, [stub_n, stub_s], seed)
    _tower_body(vox, pal, tier, rng)

    connector(vox, (A_MID, 0, 0), "north", entry=True)
    connector(vox, (A_MID, 0, depth - 1), "south", entry=False)
    return vox


# The spiral runs in the two columns beside the walk lane, so the through-route
# along the curtain stays flat. Six cells, orthogonally adjacent end to end.
SPIRAL = ((1, 2), (2, 2), (2, 3), (2, 4), (1, 4), (1, 3))


def _tower_body(vox: Voxels, pal: Palette, tier: Tier,
                rng: random.Random) -> None:
    m = tier.stone
    w = pal.wood
    x0, x1 = 0, A_IN
    z0, z1 = 1, TOWER_SIDE
    rise = tower_rise(tier)

    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            if not edge:
                continue
            for y in range(1, rise + 1):
                if tier.rampart:
                    blk = w.post
                elif tier.frame and y > tier.plinth:
                    blk = w.post if (x in (x0, x1) and z in (z0, z1)) \
                        else w.planks
                else:
                    blk = m.block("main") if rng.random() < 0.82 \
                        else m.block("weathered")
                vox.set((x, y, z), blk)

    # Floors: the wall-walk level and the roof deck.
    deck = w.planks if (tier.dressed or tier.hoarding) else m.block("main")
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            vox.set((x, tier.body_top, z), deck)
            vox.set((x, rise, z), deck)

    # Order matters. The top rung hangs on the parapet, so the parapet has to be
    # standing before the ladder is hung — running the ladder first meant its
    # support did not exist yet and the run stopped one course below the deck.
    _tower_openings(vox, pal, tier, z0, z1, rise)
    _tower_top(vox, pal, tier, x0, x1, z0, z1, rise, rng)
    _spiral(vox, pal, tier, rise)


def _spiral(vox: Voxels, pal: Palette, tier: Tier, rise: int) -> None:
    """A ladder from the ground floor to the roof deck.

    **A ladder, not a stair run.** A staircase eats the floor of a small room,
    and at village scale a ladder is what people actually build. It also frees
    the whole chamber, which the spiral did not.

    A ladder hangs on the block behind it, so it goes against the outer tower
    wall and `facing` points away from that wall — into the room. The floors it
    passes through are punched open here rather than by arithmetic elsewhere:
    working it out by hand is how the earlier version ran a staircase into a
    ceiling.
    """
    # Off the centre line on purpose. The arrow slits are punched at the middle
    # z, and putting the ladder there had them cut away the very wall it hangs
    # on: the run stopped one course short of the deck and the top rungs were
    # left with nothing behind them.
    lx, lz = 1, LADDER_Z                # against the outer wall at x = 0
    lad = state("ladder", facing="east", waterlogged="false")
    # All the way to the deck. Standing on the deck puts your feet at rise + 1,
    # and stepping sideways off a ladder needs the two cells level — so the run
    # has to reach rise + 1, not rise. Stopping at rise left a one-block rise
    # onto the deck, which is a jump, so the climb failed at the last move.
    m = tier.stone
    # The parapet is crenellated, so the cell behind the top rung is there only
    # by chance. Make it certain: a ladder needs a solid face behind every rung.
    top_support = (lx - 1, rise + 1, lz)
    if in_box(vox, top_support) and not vox.occupied(top_support):
        vox.set(top_support, m.block("main"))
    for y in range(1, rise + 2):
        if not vox.occupied((lx - 1, y, lz)):
            continue                    # no wall behind it: nothing to hang on
        vox.set((lx, y, lz), lad)
    # Somewhere to step off at the deck, level with the top rung.
    for dy in (0, 1):
        q = (lx + 1, rise + 1 + dy, lz)
        cur = vox.get(q)
        if cur is not None and cur.short != "ladder":
            vox.set(q, None)

    # The ladder already replaces the floor in its own cell, which IS the
    # stairwell opening. An earlier version then "punched the floors" at the same
    # coordinates and deleted the ladder at both floor levels — the run came out
    # with two gaps in it. Only the landing beside it needs clearing.
    for floor_y in (tier.body_top, rise):
        for dy in (1, 2):
            q = (lx + 1, floor_y + dy, lz)
            cur = vox.get(q)
            if cur is not None and cur.short != "ladder":
                vox.set(q, None)


def _tower_openings(vox: Voxels, pal: Palette, tier: Tier,
                    z0: int, z1: int, rise: int) -> None:
    """The doorway at ground level and the walk passing through the tower."""
    m = tier.stone
    w = pal.wood
    # The curtain runs straight through: clear the walk lane and both doorways.
    for z in range(z0, z1 + 1):
        for y in range(tier.walk, tier.walk + HEAD_CLEAR):
            vox.set((A_MID, y, z), None)
    # Arched heads on the two through-openings, so they read as doorways.
    for z in (z0, z1):
        vox.set((A_MID, tier.walk + HEAD_CLEAR, z),
                w.beam("z") if tier.rampart else m.block("main"))

    # Ground-floor door on the courtyard side, and room to walk in.
    # Never clear a stair: the first two steps of the spiral live here, and an
    # earlier version of this loop deleted them, which left the ground floor
    # sealed off from its own staircase.
    for z in range(z0 + 1, z1):
        for x in range(1, A_IN):
            for y in (1, 2):
                cur = vox.get((x, y, z))
                # Never clear the way up. This loop deleted the bottom two rungs
                # of the ladder — and before that the first two steps of the
                # spiral — leaving the ground floor sealed off from its own
                # staircase with nothing to board it from.
                if cur is not None and (cur.short.endswith("_stairs")
                                        or cur.short == "ladder"):
                    continue
                vox.set((x, y, z), None)
    zc = (z0 + z1) // 2
    if not tier.rampart:
        vox.set((A_IN, 1, zc), w.door("east", "lower", "left"))
        vox.set((A_IN, 2, zc), w.door("east", "upper", "left"))
    else:
        vox.set((A_IN, 1, zc), None)
        vox.set((A_IN, 2, zc), None)
    # A torch inside the ground floor, on the wall by the door.
    vox.set((A_MID, 2, zc + 1 if zc + 1 < z1 else zc - 1), pal.torch("west"))

    # Arrow slits on the outward face, at two heights — but never through the
    # wall the ladder hangs on. Cutting one there left rungs with nothing behind
    # them, which is both unsupported and a hole in the climb.
    def slit(y: int, z: int) -> None:
        if z == LADDER_Z or y < 1:
            return                      # the ladder hangs on this wall
        vox.set((0, y, z), None)

    if tier.loops:
        for z in (z0 + 1, z1 - 1):
            slit(tier.body_top - 3, z)
        slit(tier.body_top - 1, zc)
        slit(rise - 1, zc)


def _tower_top(vox: Voxels, pal: Palette, tier: Tier, x0: int, x1: int,
               z0: int, z1: int, rise: int, rng: random.Random) -> None:
    """The roof deck: crenellated parapet, clear inside, reachable."""
    m = tier.stone
    w = pal.wood
    stand = rise + 1
    # Clear the deck so it can be stood on.
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(stand, stand + HEAD_CLEAR):
                if in_box(vox, (x, y, z)) and vox.get((x, y, z)) is not None \
                        and not vox.get((x, y, z)).short.endswith("_stairs"):
                    vox.set((x, y, z), None)
    if tier.rampart:
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x in (x0, x1) or z in (z0, z1):
                    vox.set((x, stand, z), w.fence(
                        "x" if z in (z0, z1) else "z"))
        return
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if not (x in (x0, x1) or z in (z0, z1)):
                continue
            vox.set((x, stand, z), m.block("main"))
            if (x + z) % 2 == 0:
                vox.set((x, stand + 1, z), m.block("main"))
                vox.set((x, stand + 2, z), m.slab("bottom"))
    # A torch on the deck, inside the parapet.
    vox.set((x1 - 1, stand, z1 - 1), pal.torch("west"))


# ── the set ─────────────────────────────────────────────────────────

KINDS = {
    "wall_segment": compose_straight,
    "wall_corner": compose_corner,
    "gatehouse": compose_gate,
    "wall_tower": compose_tower,
}


def compose(kind: str, level: int, seed: int = 0) -> Voxels:
    """Build one piece of the fortification set at one level."""
    tier = TIERS[max(0, min(len(TIERS) - 1, level))]
    vox = trim(KINDS[kind](tier, seed=seed))
    vox.name = kind if level == 0 else f"{kind}_lvl{level}"
    return vox


# ── the promise, as a test ──────────────────────────────────────────

def walk_level(vox: Voxels) -> int:
    """The piece's own walk elevation, read off what was built.

    Not the shared `WALK` constant: the earth rampart is half height, so asking
    about the constant would test a cell in its open sky. Derived from the piece
    means one check works for every tier.
    """
    from .traverse import standable
    _sx, sy, sz = vox.size
    if standable(vox, (A_MID, WALK, sz - 1)) is not None:
        return WALK
    # Scanning from the very top would find the roof of the hoarding gallery, not
    # the walk, so the shared elevation is tried first and this is only for the
    # tiers that really are lower.
    for y in range(min(sy - 1, WALK), 0, -1):
        if standable(vox, (A_MID, y, sz - 1)) is not None:
            return y
    return WALK


def walk_endpoints(kind: str, vox: Voxels) -> Tuple[List[Coord], List[Coord]]:
    """The two ends of the walk, in the piece's own coordinates."""
    sx, _sy, sz = vox.size
    w = walk_level(vox)
    if kind == "wall_corner":
        return [(A_MID, w, sz - 1)], [(sx - 1, w, A_MID)]
    return [(A_MID, w, 0)], [(A_MID, w, sz - 1)]


def climb_endpoints(vox: Voxels) -> Tuple[List[Coord], List[Coord]]:
    """Ground outside the tower door, and the roof deck.

    The deck elevation is read off the piece, not taken from `TOWER_RISE`: the
    rampart tier's tower is shorter, and a goal cell in its open sky is a test
    that can only fail.
    """
    from .traverse import standable
    _sx, sy, _sz = vox.size
    zc = (1 + TOWER_SIDE) // 2
    ground = [(A_IN + 1, 1, zc), (A_IN + 2, 1, zc)]
    inside = [(x, z) for x in range(1, A_IN) for z in range(2, TOWER_SIDE)]
    # The highest standable course inside the parapet is the deck. Scanning down
    # from the box top finds it whatever the tier's height turned out to be.
    for y in range(sy - 1, 1, -1):
        deck = [(x, y, z) for x, z in inside
                if standable(vox, (x, y, z)) is not None]
        if deck:
            return ground, deck
    return ground, []
