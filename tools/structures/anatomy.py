"""Work out a building's anatomy from its voxels alone.

Nothing here is hand-tuned per building. Given any author structure we need to
know, automatically:

    ground zone   the terrain apron (grass / dirt / path / coarse dirt)
    floor y       the first interior floor
    wall zone     the y range where the plan reads as a ring: perimeter
                  occupied, interior largely empty
    roof zone     everything above the wall zone
    shell box     the x/z bounding box of the walls themselves, i.e. the
                  building proper without its terrain margin

Those boundaries are what make parts extraction possible: a "wall column" is
only meaningful once you know where the wall zone starts and stops.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, List, Optional, Set, Tuple

from .nbtio import BlockState, Voxels

TERRAIN = {
    "grass_block", "dirt", "coarse_dirt", "dirt_path", "podzol", "rooted_dirt",
    "farmland", "mud", "moss_block", "stone", "gravel", "sand", "water",
    "snow_block",
}
VEGETATION = {
    "oak_leaves", "spruce_leaves", "birch_leaves", "dark_oak_leaves",
    "grass", "short_grass", "tall_grass", "fern", "large_fern", "lily_pad",
    "dandelion", "poppy", "allium", "azure_bluet", "cornflower", "oxeye_daisy",
    "red_tulip", "pink_tulip", "white_tulip", "orange_tulip", "oak_sapling",
    "moss_carpet", "seagrass", "wheat", "carrots", "potatoes", "sweet_berry_bush",
}
# Blocks that carry a wall: what "structural" means when finding the shell.
NON_STRUCTURAL = TERRAIN | VEGETATION | {"jigsaw", "torch", "flower_pot"}

WINDOW_MARKERS = ("_pane", "_bars", "_fence")
OPENING_MARKERS = ("_door", "_gate")


@dataclass
class Anatomy:
    """Where each functional zone of a building lives."""

    ground_top: int          # highest y that is still terrain apron
    floor_y: int             # y of the interior floor
    wall_lo: int             # first wall layer
    wall_hi: int             # last wall layer (inclusive)
    roof_lo: int             # first roof layer
    roof_hi: int             # last solid layer
    shell: Tuple[int, int, int, int]   # x0, x1, z0, z1 of the wall ring
    layer_kind: Dict[int, str]         # y -> ground|floor|wall|roof|empty

    @property
    def wall_height(self) -> int:
        return self.wall_hi - self.wall_lo + 1

    @property
    def roof_height(self) -> int:
        return max(0, self.roof_hi - self.roof_lo + 1)

    @property
    def shell_size(self) -> Tuple[int, int]:
        x0, x1, z0, z1 = self.shell
        return (x1 - x0 + 1, z1 - z0 + 1)

    def describe(self) -> str:
        w, d = self.shell_size
        return (f"ground<={self.ground_top} floor={self.floor_y} "
                f"wall={self.wall_lo}..{self.wall_hi}(h{self.wall_height}) "
                f"roof={self.roof_lo}..{self.roof_hi}(h{self.roof_height}) "
                f"shell={w}x{d} @x{self.shell[0]}-{self.shell[1]},"
                f"z{self.shell[2]}-{self.shell[3]}")


def _structural_cells(vox: Voxels, y: int) -> Set[Tuple[int, int]]:
    return {(x, z) for (x, yy, z), b in vox.solid_items()
            if yy == y and b.short not in NON_STRUCTURAL}


def _terrain_frac(vox: Voxels, y: int) -> float:
    cells = [b for (x, yy, z), b in vox.solid_items() if yy == y]
    if not cells:
        return 0.0
    return sum(1 for b in cells if b.short in TERRAIN) / len(cells)


def _ring_scores(cells: Set[Tuple[int, int]]) -> Tuple[float, float, Tuple[int, int, int, int]]:
    """(perimeter occupancy, interior occupancy, bbox) for one layer's cells."""
    if not cells:
        return (0.0, 0.0, (0, 0, 0, 0))
    xs = [c[0] for c in cells]; zs = [c[1] for c in cells]
    x0, x1, z0, z1 = min(xs), max(xs), min(zs), max(zs)
    perim, inner = [], []
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            (perim if edge else inner).append((x, z))
    p = sum(1 for c in perim if c in cells) / len(perim) if perim else 0.0
    i = sum(1 for c in inner if c in cells) / len(inner) if inner else 0.0
    return (p, i, (x0, x1, z0, z1))


def _cover_frac(vox: Voxels, y: int) -> float:
    """Share of the layer made of slabs and stairs.

    This is the roof signal. Perimeter-vs-interior occupancy looked like the
    obvious discriminator but it collapses on small builds — a 3x3 watchtower
    shell has exactly one interior cell, so "hollow middle" is meaningless.
    Roofing material is what actually separates a roof layer from a wall layer
    in this author's work.
    """
    cells = [b for (x, yy, z), b in vox.solid_items()
             if yy == y and b.short not in VEGETATION]
    if not cells:
        return 0.0
    n = sum(1 for b in cells
            if b.short.endswith("_slab") or b.short.endswith("_stairs"))
    return n / len(cells)


def analyse(vox: Voxels, floor_y: Optional[int] = None,
            wall_hi: Optional[int] = None,
            shell: Optional[Tuple[int, int, int, int]] = None) -> Anatomy:
    """Infer the anatomy of a building.

    Heuristic, and deliberately overridable: pass `floor_y`, `wall_hi` or
    `shell` to pin any boundary the detector gets wrong. Render
    `debug_overlay()` and read `layer_table()` to check a guess before
    trusting it.
    """
    sx, sy, sz = vox.size
    top = vox.top_y()
    if top < 0:
        return Anatomy(0, 0, 0, 0, 1, 0, (0, 0, 0, 0), {})

    kind: Dict[int, str] = {}
    ring: Dict[int, Tuple[float, float, Tuple[int, int, int, int]]] = {}
    for y in range(top + 1):
        ring[y] = _ring_scores(_structural_cells(vox, y))

    # Ground: contiguous terrain-dominated layers from the bottom. A plank
    # floor patch sitting in the middle of the apron is normal, so structural
    # cells are not disqualifying — only the terrain share matters.
    ground_top = -1
    for y in range(top + 1):
        if _terrain_frac(vox, y) >= 0.5:
            ground_top = y
            kind[y] = "ground"
        else:
            break

    auto_floor = ground_top + 1
    wall_lo = floor_y if floor_y is not None else auto_floor
    wall_lo = max(0, min(wall_lo, top))

    if wall_hi is not None:
        w_hi = max(wall_lo, min(wall_hi, top))
    else:
        # Roof begins at the first roofing-material layer that leaves room for
        # a wall of at least two courses below it.
        w_hi = top
        for y in range(wall_lo + 2, top + 1):
            if _cover_frac(vox, y) >= 0.45:
                w_hi = y - 1
                break
        w_hi = max(wall_lo, min(w_hi, top))
    wall_hi = w_hi

    floor_y = wall_lo
    for y in range(wall_lo, wall_hi + 1):
        kind[y] = "wall"
    kind[floor_y] = "floor" if floor_y == wall_lo else kind.get(floor_y, "wall")

    roof_lo, roof_hi = wall_hi + 1, top
    for y in range(roof_lo, roof_hi + 1):
        kind[y] = "roof"
    for y in range(top + 1, sy):
        kind[y] = "empty"

    # Shell box: union of wall-zone bounding boxes (walls can taper).
    if shell is None:
        boxes = [ring[y][2] for y in range(wall_lo, wall_hi + 1)
                 if _structural_cells(vox, y)]
        if boxes:
            shell = (min(b[0] for b in boxes), max(b[1] for b in boxes),
                     min(b[2] for b in boxes), max(b[3] for b in boxes))
        else:
            shell = (0, sx - 1, 0, sz - 1)

    return Anatomy(ground_top, floor_y, wall_lo, wall_hi, roof_lo, roof_hi,
                   shell, kind)


def layer_table(vox: Voxels, ana: Optional[Anatomy] = None) -> str:
    """Per-layer numbers behind the zone split — read this before trusting it.

    Columns: y, assigned zone, solid cells, terrain share, slab+stair share,
    perimeter occupancy, interior occupancy, and the layer's x/z bounding box.
    """
    ana = ana or analyse(vox)
    sx, sy, sz = vox.size
    lines = [f"{vox.name or 'structure'}  size={sx}x{sy}x{sz}  {ana.describe()}",
             "  y  zone    cells  terr  cover  perim  inner  bbox"]
    for y in range(sy - 1, -1, -1):
        cells = [b for (p, b) in vox.solid_items() if p[1] == y]
        if not cells and y > vox.top_y():
            lines.append(f"{y:3d}  EMPTY       0     -      -      -      -  -")
            continue
        p, i, bb = _ring_scores(_structural_cells(vox, y))
        lines.append(
            f"{y:3d}  {ana.layer_kind.get(y, '-'):7s} {len(cells):4d}  "
            f"{_terrain_frac(vox, y):.2f}  {_cover_frac(vox, y):.2f}   "
            f"{p:.2f}   {i:.2f}  x{bb[0]}-{bb[1]},z{bb[2]}-{bb[3]}")
    return "\n".join(lines)


# ── roof profile ────────────────────────────────────────────────────

@dataclass
class RoofProfile:
    """How the donor's roof steps in as it rises.

    `layers` is one entry per roof y: (inset from the shell box, the block
    states used at that layer keyed by role). This is what lets us replay the
    author's roof on a different footprint instead of inventing one.
    """

    layers: List[Tuple[int, Dict[str, BlockState]]]
    kind: str          # "pitched" | "flat" | "none"
    ridge: Optional[BlockState] = None

    def describe(self) -> str:
        insets = ",".join(str(i) for i, _ in self.layers)
        return f"{self.kind} h={len(self.layers)} insets=[{insets}]"


def roof_profile(vox: Voxels, ana: Anatomy) -> RoofProfile:
    """Read the donor's roof: per-layer inset plus the blocks it uses."""
    x0, x1, z0, z1 = ana.shell
    layers: List[Tuple[int, Dict[str, BlockState]]] = []
    stair_layers = 0

    for y in range(ana.roof_lo, ana.roof_hi + 1):
        cells = {(x, z): b for (x, yy, z), b in vox.solid_items()
                 if yy == y and b.short not in VEGETATION}
        if not cells:
            continue
        xs = [c[0] for c in cells]; zs = [c[1] for c in cells]
        # Inset measured against the shell; negative means an overhang.
        inset = min(min(xs) - x0, min(zs) - z0,
                    x1 - max(xs), z1 - max(zs))
        roles: Dict[str, BlockState] = {}
        for (x, z), b in cells.items():
            edge = x in (min(xs), max(xs)) or z in (min(zs), max(zs))
            roles.setdefault("edge" if edge else "fill", b)
            if b.short.endswith("_stairs"):
                roles["stairs"] = b
        if any(b.short.endswith("_stairs") for b in cells.values()):
            stair_layers += 1
        layers.append((inset, roles))

    if not layers:
        return RoofProfile([], "none")
    kind = "pitched" if stair_layers >= 2 or len(layers) >= 3 else "flat"
    ridge = layers[-1][1].get("fill") or layers[-1][1].get("edge")
    return RoofProfile(layers, kind, ridge)


# ── wall columns ────────────────────────────────────────────────────

@dataclass
class Column:
    """One perimeter cell's vertical stack through the wall zone."""

    blocks: Tuple[Optional[BlockState], ...]   # index 0 == wall_lo
    role: str          # corner | door | window | solid
    side: str          # north | south | east | west | corner

    @property
    def height(self) -> int:
        return len(self.blocks)

    def signature(self) -> str:
        return "/".join(str(b) if b else "." for b in self.blocks)


def _classify(stack: Tuple[Optional[BlockState], ...], is_corner: bool) -> str:
    names = [b.short for b in stack if b]
    if is_corner and any("_log" in n or n.endswith("_planks") for n in names):
        return "corner"
    if any(n.endswith(OPENING_MARKERS) for n in names):
        return "door"
    if any(n.endswith(WINDOW_MARKERS) or n == "glass" for n in names):
        return "window"
    return "solid"


def wall_columns(vox: Voxels, ana: Anatomy) -> Dict[Tuple[int, int], Column]:
    """Extract every perimeter column of the wall zone, keyed by (x, z)."""
    x0, x1, z0, z1 = ana.shell
    out: Dict[Tuple[int, int], Column] = {}
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            on_edge = x in (x0, x1) or z in (z0, z1)
            if not on_edge:
                continue
            stack = tuple(vox.get((x, y, z))
                          for y in range(ana.wall_lo, ana.wall_hi + 1))
            if not any(stack):
                continue
            is_corner = x in (x0, x1) and z in (z0, z1)
            if is_corner:
                side = "corner"
            elif z == z0:
                side = "north"
            elif z == z1:
                side = "south"
            elif x == x0:
                side = "west"
            else:
                side = "east"
            out[(x, z)] = Column(stack, _classify(stack, is_corner), side)
    return out


def interior_cells(vox: Voxels, ana: Anatomy) -> Dict[Tuple[int, int, int], BlockState]:
    """Furniture and floor inside the shell, positions relative to the shell."""
    x0, x1, z0, z1 = ana.shell
    out = {}
    for (x, y, z), b in vox.solid_items():
        if ana.wall_lo <= y <= ana.wall_hi and x0 < x < x1 and z0 < z < z1:
            out[(x - x0, y - ana.wall_lo, z - z0)] = b
    return out


def exterior_decor(vox: Voxels, ana: Anatomy) -> Dict[Tuple[int, int, int], BlockState]:
    """Vegetation, garden fences and props outside the shell, relative to it."""
    x0, x1, z0, z1 = ana.shell
    out = {}
    for (x, y, z), b in vox.solid_items():
        if x0 <= x <= x1 and z0 <= z <= z1:
            continue
        if b.short in TERRAIN:
            continue
        out[(x - x0, y - ana.wall_lo, z - z0)] = b
    return out
