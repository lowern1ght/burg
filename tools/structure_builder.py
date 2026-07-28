"""nbtlib — a Python library for building Minecraft 1.21.1 NBT structures.

Design goals:
- Every block placement respects the block's required properties
  (e.g. slab needs type, stairs need facing+half, log needs axis)
- The library is not a "build a house" generator. It's primitives +
  compositions. The caller decides what to build.
- Property values are auto-filled with sensible defaults (so a
  caller can write `nb.oak_planks((1,2,3))` and get a valid block).

Block name shortcuts are `nb.<shortcut>(pos, **overrides)`. The full
list of shortcuts is in `BLOCKS` below. The shortcut does not have
to match the block name exactly; it just has to be a stable key.

Usage:

    from tools.nbtlib import *

    nb = StructureBuilder((11, 6, 11))
    nb.grass_block((0,0,0)).grass_block((0,0,1))  # chain
    nb.dirt((1,1,1), axis='y')                       # axis gets defaulted
    nb.oak_planks((2,1,2))
    nb.save('out/house.nbt')

    # Helpers for common patterns (all take (origin_x, origin_y, origin_z))
    # and return the builder for chaining:
    nb.ground_pad(0, 0, 0, width=9, depth=9)
    nb.fence_window((2, 1, 1))        # one block of fence at a position
    nb.horizontal_log_beam(0, 1, 0, length=9, axis='x')  # 9 logs in a row
"""

from __future__ import annotations
import gzip
import random
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence, Tuple, Union

import nbtlib
from nbtlib import Compound, File, Int, IntArray, List, String

Coord = Tuple[int, int, int]


# ────────────────────────────────────────────────────────────────────
# Block defaults — every block in the mod's palette with required props
# ────────────────────────────────────────────────────────────────────

BLOCK_DEFAULTS: Dict[str, Dict[str, str]] = {
    "minecraft:air": {},
    "minecraft:grass_block": {"snowy": "false"},
    "minecraft:dirt": {},
    "minecraft:coarse_dirt": {},
    "minecraft:dirt_path": {},
    "minecraft:oak_leaves": {"persistent": "true", "distance": "1", "waterlogged": "false"},
    "minecraft:spruce_leaves": {"persistent": "true", "distance": "1", "waterlogged": "false"},
    "minecraft:short_grass": {},
    "minecraft:oak_planks": {},
    "minecraft:spruce_planks": {},
    "minecraft:cobblestone": {},
    "minecraft:stone_bricks": {},
    "minecraft:stone": {},
    "minecraft:oak_slab": {"type": "bottom", "waterlogged": "false"},
    "minecraft:spruce_slab": {"type": "bottom", "waterlogged": "false"},
    "minecraft:cobblestone_slab": {"type": "bottom", "waterlogged": "false"},
    "minecraft:stone_brick_slab": {"type": "bottom", "waterlogged": "false"},
    "minecraft:oak_stairs": {"facing": "north", "half": "bottom", "shape": "straight", "waterlogged": "false"},
    "minecraft:spruce_stairs": {"facing": "north", "half": "bottom", "shape": "straight", "waterlogged": "false"},
    "minecraft:cobblestone_stairs": {"facing": "north", "half": "bottom", "shape": "straight", "waterlogged": "false"},
    "minecraft:stone_brick_stairs": {"facing": "north", "half": "bottom", "shape": "straight", "waterlogged": "false"},
    "minecraft:oak_log": {"axis": "y"},
    "minecraft:spruce_log": {"axis": "y"},
    "minecraft:stone_brick_wall": {"up": "false", "north": "false", "south": "false", "west": "false", "east": "false"},
    "minecraft:oak_fence": {"east": "false", "north": "false", "south": "false", "west": "false", "waterlogged": "false"},
    "minecraft:spruce_fence": {"east": "false", "north": "false", "south": "false", "west": "false", "waterlogged": "false"},
    "minecraft:nether_brick_fence": {"east": "false", "north": "false", "south": "false", "west": "false", "waterlogged": "false"},
    "minecraft:iron_bars": {"east": "false", "north": "false", "south": "false", "west": "false", "waterlogged": "false"},
    "minecraft:glass_pane": {"east": "false", "north": "false", "south": "false", "west": "false", "waterlogged": "false"},
    "minecraft:oak_door": {"hinge": "right", "half": "lower", "powered": "false", "facing": "north", "open": "false"},
    "minecraft:spruce_door": {"hinge": "right", "half": "lower", "powered": "false", "facing": "north", "open": "false"},
    "minecraft:iron_door": {"hinge": "right", "half": "lower", "powered": "false", "facing": "north", "open": "false"},
    "minecraft:oak_trapdoor": {"half": "bottom", "powered": "false", "facing": "north", "open": "false", "waterlogged": "false"},
    "minecraft:spruce_trapdoor": {"half": "bottom", "powered": "false", "facing": "north", "open": "false", "waterlogged": "false"},
    "minecraft:iron_trapdoor": {"half": "bottom", "powered": "false", "facing": "north", "open": "false", "waterlogged": "false"},
    "minecraft:lantern": {"hanging": "false", "waterlogged": "false"},
    "minecraft:wall_torch": {"facing": "north"},
    "minecraft:torch": {},
    "minecraft:crafting_table": {},
    "minecraft:furnace": {"facing": "north", "lit": "false"},
    "minecraft:white_bed": {"part": "head", "facing": "south", "occupied": "false"},
    "minecraft:red_bed": {"part": "head", "facing": "south", "occupied": "false"},
    "minecraft:white_carpet": {},
    "minecraft:red_carpet": {},
    "minecraft:flower_pot": {},
    "minecraft:ladder": {"facing": "north", "waterlogged": "false"},
    "minecraft:jigsaw": {"orientation": "south_up"},
    "minecraft:item_frame": {"facing": "north"},
}


# Shortcut names — a stable string the caller can type instead of the
# full minecraft: namespace. Mapped at build time.
SHORTCUTS: Dict[str, str] = {
    "air": "minecraft:air",
    "grass": "minecraft:grass_block",
    "dirt": "minecraft:dirt",
    "coarse_dirt": "minecraft:coarse_dirt",
    "path": "minecraft:dirt_path",
    "leaves": "minecraft:oak_leaves",
    "spruce_leaves": "minecraft:spruce_leaves",
    "tall_grass": "minecraft:short_grass",
    "oak_planks": "minecraft:oak_planks",
    "planks": "minecraft:oak_planks",
    "spruce_planks": "minecraft:spruce_planks",
    "cobble": "minecraft:cobblestone",
    "stone_bricks": "minecraft:stone_bricks",
    "oak_slab": "minecraft:oak_slab",
    "slab": "minecraft:oak_slab",
    "spruce_slab": "minecraft:spruce_slab",
    "cobble_slab": "minecraft:cobblestone_slab",
    "stone_brick_slab": "minecraft:stone_brick_slab",
    "oak_stairs": "minecraft:oak_stairs",
    "stairs": "minecraft:oak_stairs",
    "spruce_stairs": "minecraft:spruce_stairs",
    "cobble_stairs": "minecraft:cobblestone_stairs",
    "stone_brick_stairs": "minecraft:stone_brick_stairs",
    "oak_log": "minecraft:oak_log",
    "log": "minecraft:oak_log",
    "spruce_log": "minecraft:spruce_log",
    "stone_brick_wall": "minecraft:stone_brick_wall",
    "fence": "minecraft:oak_fence",
    "spruce_fence": "minecraft:spruce_fence",
    "nether_brick_fence": "minecraft:nether_brick_fence",
    "iron_bars": "minecraft:iron_bars",
    "glass": "minecraft:glass_pane",
    "oak_door": "minecraft:oak_door",
    "spruce_door": "minecraft:spruce_door",
    "iron_door": "minecraft:iron_door",
    "trapdoor": "minecraft:oak_trapdoor",
    "oak_trapdoor": "minecraft:oak_trapdoor",
    "spruce_trapdoor": "minecraft:spruce_trapdoor",
    "iron_trapdoor": "minecraft:iron_trapdoor",
    "lantern": "minecraft:lantern",
    "wall_torch": "minecraft:wall_torch",
    "torch": "minecraft:torch",
    "table": "minecraft:crafting_table",
    "crafting_table": "minecraft:crafting_table",
    "furnace": "minecraft:furnace",
    "bed": "minecraft:white_bed",
    "carpet": "minecraft:white_carpet",
    "flower_pot": "minecraft:flower_pot",
    "ladder": "minecraft:ladder",
    "jigsaw": "minecraft:jigsaw",
    "item_frame": "minecraft:item_frame",
}


# Faces
NORTH = (0, 0, -1)
SOUTH = (0, 0, 1)
EAST = (1, 0, 0)
WEST = (-1, 0, 0)
UP = (0, 1, 0)
DOWN = (0, -1, 0)


def resolve(shortcut: str) -> str:
    if shortcut in BLOCK_DEFAULTS:
        return shortcut
    if shortcut in SHORTCUTS:
        return SHORTCUTS[shortcut]
    raise KeyError(f"unknown block: {shortcut!r}")


# ────────────────────────────────────────────────────────────────────
# StructureBuilder
# ────────────────────────────────────────────────────────────────────


class StructureBuilder:
    """Builds a 1.21.1 vanilla structure NBT in memory and writes to a file.

    All block placements validate the (x, y, z) is within the declared size.
    Out-of-bounds placements are silently dropped (so a caller can use
    `if`-based logic without checking bounds manually).
    """

    def __init__(self, size: Coord):
        self.size = size
        self._palette: Dict[Tuple[str, frozenset], int] = {}
        self._palette_list: List[Dict] = []
        self.blocks: List[Tuple[int, int, int, int]] = []  # (x, y, z, palette_idx)
        self.entities: List[Dict] = []

    # ── core block placement ─────────────────────────────────

    def set(self, pos: Coord, block: str, **props: str) -> "StructureBuilder":
        """Place a single block. `block` can be a shortcut ('oak_planks')
        or a full namespace ('minecraft:oak_planks'). Properties are
        merged with defaults. Returns self for chaining.
        """
        x, y, z = pos
        sx, sy, sz = self.size
        if not (0 <= x < sx and 0 <= y < sy and 0 <= z < sz):
            return self
        name = resolve(block)
        defaults = BLOCK_DEFAULTS.get(name, {})
        merged = {**defaults, **props}
        idx = self._intern(name, merged)
        # Replace any earlier block at this position
        self.blocks = [(bx, by, bz, bi) for (bx, by, bz, bi) in self.blocks
                        if not (bx == x and by == y and bz == z)]
        self.blocks.append((x, y, z, idx))
        return self

    # ── filled boxes ─────────────────────────────────────────

    def fill(self, a: Coord, b: Coord, block: str, **props) -> "StructureBuilder":
        """Fill an axis-aligned box (inclusive of both endpoints).
        Skip positions listed in `skip=`. Returns self.
        """
        ax, ay, az = a
        bx, by, bz = b
        for x in range(min(ax, bx), max(ax, bx) + 1):
            for y in range(min(ay, by), max(ay, by) + 1):
                for z in range(min(az, bz), max(az, bz) + 1):
                    self.set((x, y, z), block, **props)
        return self

    def fill_hollow(self, a: Coord, b: Coord, wall: str, floor: str = None,
                    ceiling: str = None, **props) -> "StructureBuilder":
        """A box with walls and optional floor/ceiling. Interior is air.
        `wall` is the block for the 4 walls; floor/ceiling default to
        skipping (i.e. only walls drawn).
        """
        ax, ay, az = a
        bx, by, bz = b
        if floor:
            self.fill((ax, ay, az), (bx, ay, bz), floor)
        if ceiling:
            self.fill((ax, by, az), (bx, by, bz), ceiling)
        for x in range(ax, bx + 1):
            self.fill((x, ay + 1, az), (x, by - 1, az), wall, **props)
            self.fill((x, ay + 1, bz), (x, by - 1, bz), wall, **props)
        for z in range(az + 1, bz):
            self.fill((ax, ay + 1, z), (ax, by - 1, z), wall, **props)
            self.fill((bx, ay + 1, z), (bx, by - 1, z), wall, **props)
        return self

    # ── line of blocks ──────────────────────────────────────

    def line(self, start: Coord, end: Coord, block: str, **props) -> "StructureBuilder":
        """Place a line from start to end. Always traverses in the longest
        axis first; uses Bresenham-like stepping. Good for beams."""
        sx, sy, sz = start
        ex, ey, ez = end
        dx = ex - sx
        dy = ey - sy
        dz = ez - sz
        steps = max(abs(dx), abs(dy), abs(dz), 1)
        for i in range(steps + 1):
            t = i / steps
            x = round(sx + dx * t)
            y = round(sy + dy * t)
            z = round(sz + dz * t)
            self.set((x, y, z), block, **props)
        return self

    # ── common patterns from the OUAT NBT vocabulary ──────────

    def log_corner(self, pos: Coord, height: int = 1, axis: str = "y") -> "StructureBuilder":
        """A vertical (axis=y) log pole from y to y+height-1. Default
        axis=y matches corner posts. For a beam, use log_beam()."""
        for h in range(height):
            self.set((pos[0], pos[1] + h, pos[2]), "log", axis=axis)
        return self

    def log_beam(self, start: Coord, length: int, axis: str = "x") -> "StructureBuilder":
        """A horizontal log beam. axis=x means beam extends along X.
        axis=z means beam extends along Z. Length is the number of
        blocks, including the start position.
        """
        sx, sy, sz = start
        for i in range(length):
            if axis == "x":
                self.set((sx + i, sy, sz), "log", axis="x")
            elif axis == "z":
                self.set((sx, sy, sz + i), "log", axis="z")
        return self

    def log_pillar(self, pos: Coord, height: int) -> "StructureBuilder":
        """Vertical log pillar, full height."""
        return self.log_corner(pos, height=height, axis="y")

    def slab_top(self, pos: Coord, block: str = "slab", underlay: str = None) -> "StructureBuilder":
        """Place a slab_top. If `underlay` is set, also place a full
        block below it (e.g. `oak_planks`) so the slab doesn't float.
        The underlay is ONLY placed if the position below is currently
        empty (air) — we don't want to overwrite a wall beam."""
        self.set(pos, block, type="top", waterlogged="false")
        if underlay is not None:
            self._underlay_if_empty((pos[0], pos[1] - 1, pos[2]), underlay)
        return self

    def slab_bottom(self, pos: Coord, block: str = "slab", underlay: str = None) -> "StructureBuilder":
        """Place a slab_bottom. `underlay` goes one block below."""
        self.set(pos, block, type="bottom", waterlogged="false")
        if underlay is not None:
            self._underlay_if_empty((pos[0], pos[1] - 1, pos[2]), underlay)
        return self

    def stairs(self, pos: Coord, facing: str = "north", half: str = "bottom",
              underlay: str = None) -> "StructureBuilder":
        """Place stairs. `underlay` is the full block under the bottom
        half (for the bottom-half stair, it must rest on something)."""
        self.set(pos, "stairs", facing=facing, half=half, shape="straight", waterlogged="false")
        if underlay is not None and half == "bottom":
            self._underlay_if_empty((pos[0], pos[1] - 1, pos[2]), underlay)
        return self

    def _underlay_if_empty(self, pos: Coord, block: str) -> None:
        """Set block at pos ONLY if it's currently empty/air. Used for
        slab underlays to avoid overwriting wall beams or other blocks."""
        x, y, z = pos
        for (bx, by, bz, _bi) in self.blocks:
            if bx == x and by == y and bz == z:
                return  # already a block here, don't overwrite
        self.set(pos, block)

    def ladder_on_wall(self, pos: Coord, facing: str = "west",
                       wall: str = "oak_planks") -> "StructureBuilder":
        """Place a ladder against a wall. The block at the same
        position as the ladder (in the facing direction) is the wall.
        We set that wall block too, so the ladder isn't floating."""
        self.set(pos, "ladder", facing=facing, waterlogged="false")
        dx, dz = {"north": (0, -1), "south": (0, 1),
                  "east": (1, 0), "west": (-1, 0)}[facing]
        self.set((pos[0] + dx, pos[1], pos[2] + dz), wall)
        return self

    # ── door / bed / furniture ──────────────────────────────

    def door(self, pos: Coord, facing: str = "north", hinge: str = "right",
             open: bool = False, block: str = "door") -> "StructureBuilder":
        """Two-block door (lower + upper halves). pos is the lower
        half position. `block` is the door material (default: oak)."""
        self.set(pos, block, half="lower", facing=facing, hinge=hinge, open=str(open).lower())
        self.set((pos[0], pos[1] + 1, pos[2]), block, half="upper", facing=facing, hinge=hinge, open=str(open).lower())
        return self

    def bed(self, pos: Coord, facing: str = "south", part: str = "head",
            block: str = "bed") -> "StructureBuilder":
        """Single bed part. part='head' at pos, part='foot' at pos+(0,0,1)
        (when facing=south). Use for one half only — call once for head
        and once for foot."""
        return self.set(pos, block, part=part, facing=facing, occupied="false")

    # ── full patterns observed in the real NBTs ──────────────

    def ground_pad(self, origin: Coord, width: int, depth: int,
                   rng: random.Random = None) -> "StructureBuilder":
        """The OUAT Y=0 pattern: grass border, dirt footprint, scattered
        coarse_dirt, a dirt_path down the centre that extends 4 blocks
        past the front, leaves + tall_grass around the edges. jigsaw
        connector in the path at the centre-front.
        """
        if rng is None:
            rng = random.Random(0)
        ox, oy, oz = origin
        # Grass border across the whole bounding box
        for x in range(width):
            for z in range(depth):
                self.set((ox + x, oy, oz + z), "grass")
        # Dirt footprint in the centre
        for x in range(1, width - 1):
            for z in range(1, depth - 1):
                self.set((ox + x, oy, oz + z), "dirt")
        # Random coarse_dirt inside
        n_dirty = max(2, (width - 2) * (depth - 2) // 6)
        for _ in range(n_dirty):
            tx = rng.randint(1, width - 2)
            tz = rng.randint(1, depth - 2)
            self.set((ox + tx, oy, oz + tz), "coarse_dirt")
        # Dirt path down the centre
        cx = ox + width // 2
        for z in range(depth):
            self.set((cx, oy, oz + z), "path")
        # Path extends 4 blocks past the front
        for z in range(1, 5):
            self.set((cx, oy, oz - z), "path")
        # jigsaw connector at the centre-front
        self.set((cx, oy, oz), "jigsaw", orientation="south_up")
        # Leaves + tall_grass around the edges (asymmetric)
        for _ in range(rng.randint(3, 6)):
            for _ in range(2):
                tx = rng.randint(-1, width)
                tz = rng.randint(-2, depth + 1)
                self.set((ox + tx, oy, oz + tz), "leaves",
                         persistent="true", distance="1", waterlogged="false")
                if rng.random() < 0.5:
                    self.set((ox + tx, oy + 1, oz + tz), "tall_grass")
        return self

    def log_wall_row(self, y: int, origin: Coord, width: int, depth: int,
                      door_x: int = -1, beam_at_back: bool = True,
                      beam_at_front: bool = True) -> "StructureBuilder":
        """A single horizontal row of the wall. Places:
        - 4 corner log posts (axis=y)
        - Optional log beams (axis=x) at the front and back walls,
          full width. The beam at the front skips the door position.
        - Random decoration (planks, fence, coarse_dirt) between the
          corners. Probability knobs: 70% plank, 15% fence, 15% dirt.
        - Outside the building: random leaves at the side edges.
        """
        ox, oy, oz = origin
        # corner logs
        for cx in (ox + 1, ox + width - 2):
            for cz in (oz + 1, oz + depth - 2):
                self.set((cx, y, cz), "log", axis="y")
        # back beam
        if beam_at_back:
            for x in range(ox + 1, ox + width - 1):
                self.set((x, y, oz + depth - 2), "log", axis="x")
        # front beam (skip door)
        if beam_at_front:
            for x in range(ox + 1, ox + width - 1):
                if x == door_x:
                    continue
                self.set((x, y, oz + 1), "log", axis="x")
        # front wall fill (between front beam and front face)
        for x in range(ox + 2, ox + width - 2):
            if x == door_x:
                continue
            self._decor(x, y, oz + 1)
        # back wall fill
        for x in range(ox + 2, ox + width - 2):
            self._decor(x, y, oz + depth - 2)
        # side walls (between front beam and back beam, on the side faces)
        for z in range(oz + 2, oz + depth - 2):
            for side_x in (ox + 1, ox + width - 2):
                self._decor(side_x, y, z)
        # leaves outside
        for x_off in (-1, width):
            for z in range(oz, oz + depth):
                if random.random() < 0.3:
                    self.set((ox + x_off, y, z), "leaves",
                             persistent="true", distance="1", waterlogged="false")
        return self

    def _decor(self, x: int, y: int, z: int) -> None:
        r = random.random()
        if r < 0.10:
            self.set((x, y, z), "coarse_dirt")
        elif r < 0.20:
            self.set((x, y, z), "fence")
        else:
            self.set((x, y, z), "oak_planks")

    def cornice_row(self, y: int, origin: Coord, width: int, depth: int,
                     door_x: int = -1) -> "StructureBuilder":
        """The chair-rail + cornice row at the top of the wall.
        Pattern (from carpenter.nbt Y=3):
        - 4 corner log posts (axis=y)
        - Fence at the 2 cells adjacent to each corner (FPF pattern)
        - Plank fill in between
        - Slab_top on the OUTER ring (overhangs 1 block past the wall)
        - Trapdoor above the door (attic access)
        """
        ox, oy, oz = origin
        # corner logs
        for cx in (ox + 1, ox + width - 2):
            for cz in (oz + 1, oz + depth - 2):
                self.set((cx, y, cz), "log", axis="y")
        # FPF: fence at the 2 cells adjacent to each corner on each wall
        for cz in (oz + 1, oz + depth - 2):
            self.set((ox + 2, y, cz), "fence")
            self.set((ox + width - 3, y, cz), "fence")
        for cx in (ox + 1, ox + width - 2):
            self.set((cx, y, oz + 2), "fence")
            self.set((cx, y, oz + depth - 3), "fence")
        # Plank fill between the FPF pattern (front + back)
        for x in range(ox + 3, ox + width - 3):
            for cz in (oz + 1, oz + depth - 2):
                if x == door_x:
                    continue
                self.set((x, y, cz), "oak_planks")
        # Plank fill on the sides
        for z in range(oz + 3, oz + depth - 3):
            for cx in (ox + 1, ox + width - 2):
                self.set((cx, y, z), "oak_planks")
        # Trapdoor above the door
        if door_x >= 0:
            self.set((door_x, y, oz + 1), "trapdoor",
                     half="top", facing="north", open="true", waterlogged="false")
        # Cornice overhang: slab_top in the OUTER ring.
        # Each slab needs a solid block underneath (the wall plank at y-1).
        for x in range(ox, ox + width):
            self.slab_top((x, y, oz), "oak_slab", underlay="oak_planks")
            self.slab_top((x, y, oz + depth - 1), "oak_slab", underlay="oak_planks")
        for z in range(oz, oz + depth):
            self.slab_top((ox, y, z), "oak_slab", underlay="oak_planks")
            self.slab_top((ox + width - 1, y, z), "oak_slab", underlay="oak_planks")
        return self

    def roof_body_row(self, y: int, origin: Coord, width: int, depth: int,
                      rng: random.Random = None) -> "StructureBuilder":
        """The roof body. Pattern from carpenter.nbt Y=4:
        - Border ring: slab_bottom (1 cell inside the cornice)
        - Interior: a single ROW of slab_top down the centre (forming
          a "ridge" beam) plus planks filling the rest
        - The slab_top in the centre creates the visual step seen in
          carpenter Y=4 (`_= =_`).
        - Asymmetric: wall_torch on the front-side, leaves on the back
        """
        if rng is None:
            rng = random.Random(0)
        ox, oy, oz = origin
        for x in range(ox + 1, ox + width - 1):
            for z in range(oz + 1, oz + depth - 1):
                is_border = (x == ox + 1 or x == ox + width - 2
                             or z == oz + 1 or z == oz + depth - 2)
                is_centre = (x == ox + width // 2)
                if is_border:
                    self.slab_bottom((x, y, oz + z), "oak_slab", underlay="oak_planks")
                elif is_centre:
                    # Slab_top centre line — gives the roof a visible ridge
                    self.slab_top((x, y, oz + z), "oak_slab", underlay="oak_planks")
                else:
                    r = rng.random()
                    if r < 0.05:
                        self.set((x, y, oz + z), "coarse_dirt")
                    elif r < 0.10:
                        self.set((x, y, oz + z), "leaves",
                                 persistent="true", distance="1", waterlogged="false")
                    else:
                        self.set((x, y, oz + z), "oak_planks")
        # Asymmetric decoration
        cx = ox + width // 2
        if width >= 6 and depth >= 6:
            self.set((cx - 1, y, oz + 1), "wall_torch", facing="north")
            self.set((cx + 1, y, oz + depth - 2), "leaves",
                     persistent="true", distance="1", waterlogged="false")
        return self

    def roof_cap(self, y: int, origin: Coord, width: int, depth: int) -> "StructureBuilder":
        """The roof cap (carpenter Y=5). A single line of slab_bottom
        running through the centre, along the z axis. The underlay is
        the slab_top ridge from the body row (y-1) so the cap doesn't
        float. We check if the underlay position is occupied before
        placing a plank (the ridge slab_top is already there)."""
        ox, oy, oz = origin
        cx = ox + width // 2
        for z in range(oz + 1, oz + depth - 1):
            self.slab_bottom((cx, y, oz + z), "oak_slab", underlay="oak_planks")
        return self

    # ── save ────────────────────────────────────────────────

    def save(self, path: Union[str, Path]) -> None:
        path = Path(path)
        path.parent.mkdir(parents=True, exist_ok=True)
        size_x, size_y, size_z = self.size
        # build nbtlib objects (nbtlib 2.0.4 uses generic List[T]([...]))
        nbt_blocks = List[Compound]()
        for (x, y, z, idx) in self.blocks:
            nbt_blocks.append(Compound({
                "pos": List[Int]([nbtlib.Int(x), nbtlib.Int(y), nbtlib.Int(z)]),
                "state": nbtlib.Int(idx),
            }))
        nbt_palette = List[Compound]()
        for entry in self._palette_list:
            name, props = entry["name"], entry["props"]
            nbt_palette.append(Compound({
                "Name": nbtlib.String(name),
                "Properties": Compound(
                    {k: nbtlib.String(v) for k, v in props.items()}
                ),
            }))
        nbt_entities = List[Compound](self.entities)
        root = Compound({
            "size": List[Int]([nbtlib.Int(size_x), nbtlib.Int(size_y), nbtlib.Int(size_z)]),
            "palette": nbt_palette,
            "blocks": nbt_blocks,
            "entities": nbt_entities,
            "palette_max": nbtlib.Int(len(self._palette_list)),
            "version": nbtlib.Int(1792610050),
            "author": nbtlib.String("nbtlib"),
            "data_version": nbtlib.Int(3465),
        })
        File(root, gzipped=True, root_name="").save(path)

    # ── internals ────────────────────────────────────────────

    def _intern(self, name: str, props: Dict[str, str]) -> int:
        # canonical key: name + sorted properties (frozenset for hashability)
        key = (name, frozenset(props.items()))
        if key in self._palette:
            return self._palette[key]
        idx = len(self._palette_list)
        self._palette_list.append({"name": name, "props": dict(props)})
        self._palette[key] = idx
        return idx


# ────────────────────────────────────────────────────────────────────
# Shortcut method generation — `nb.oak_planks((x,y,z))` etc.
# ────────────────────────────────────────────────────────────────────

def _make_shortcut_method(shortcut: str) -> None:
    block_id = SHORTCUTS.get(shortcut, shortcut)

    def method(self, pos: Coord, **props) -> "StructureBuilder":
        return self.set(pos, block_id, **props)

    method.__name__ = shortcut
    method.__doc__ = f"Place {block_id} at pos. Auto-fills default properties."
    setattr(StructureBuilder, shortcut, method)


for _sc in SHORTCUTS:
    _make_shortcut_method(_sc)
