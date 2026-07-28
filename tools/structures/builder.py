"""Parametric StructureBuilder — produces vanilla Minecraft NBT structures.

Output format: modern Java Edition structure NBT (1.19.4+ palette format),
which is what BuildSchematic in the mod expects.

Usage:
    b = StructureBuilder((7, 5, 5))
    b.fill_box((0, 0, 0), (6, 0, 4), "minecraft:oak_planks")
    b.set_block((3, 1, 0), "minecraft:oak_door")
    b.save("out/cottage.nbt")
"""

from __future__ import annotations

import io
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Tuple

import nbtlib
from nbtlib import Compound, File, IntArray, List as NbtList

from .registry import BLOCK_PROPERTIES


Coord = Tuple[int, int, int]


@dataclass
class PlacedBlock:
    """One block in a structure: local position + palette reference."""
    x: int
    y: int
    z: int
    palette_index: int

    def to_nbt(self) -> Compound:
        return Compound({
            "pos": NbtList([nbtlib.Int(self.x), nbtlib.Int(self.y), nbtlib.Int(self.z)]),
            "state": nbtlib.Int(self.palette_index),
        })


@dataclass
class PaletteEntry:
    """One entry in the block palette."""
    name: str
    properties: Dict[str, str]

    def to_nbt(self) -> Compound:
        props = Compound({k: nbtlib.String(v) for k, v in self.properties.items()})
        return Compound({
            "Name": nbtlib.String(self.name),
            "Properties": props,
        })


@dataclass
class Structure:
    """In-memory representation of a Minecraft structure."""
    size: Coord
    blocks: List[PlacedBlock] = field(default_factory=list)
    entities: List[Compound] = field(default_factory=list)

    @property
    def block_count(self) -> int:
        return len(self.blocks)

    @property
    def bounds(self) -> Coord:
        """Inclusive bounding box of placed blocks, or (0,0,0) if empty."""
        if not self.blocks:
            return (0, 0, 0)
        xs = [b.x for b in self.blocks]
        ys = [b.y for b in self.blocks]
        zs = [b.z for b in self.blocks]
        return (min(xs), min(ys), min(zs))


class StructureBuilder:
    """Build a Structure incrementally, then write to NBT."""

    def __init__(self, size: Coord):
        self.size = size
        self._palette: Dict[Tuple[str, str], int] = {}
        self._palette_list: List[PaletteEntry] = []
        # Keyed by position so a later write REPLACES an earlier one. Appending
        # to a flat list silently emitted duplicate positions — 106 of 360
        # entries in the old stylekit house — which left carved-out windows and
        # doorways still holding their original wall block.
        self._cells: Dict[Coord, int] = {}
        self.entities: List[Compound] = []
        # auto-register air at index 0 (vanilla convention)
        self._intern("minecraft:air", {})

    @property
    def blocks(self) -> List[PlacedBlock]:
        """Placed blocks in deterministic position order."""
        return [PlacedBlock(x, y, z, idx)
                for (x, y, z), idx in sorted(self._cells.items())]

    # ---- palette management ----

    def _intern(self, block_id: str, properties: Dict[str, str]) -> int:
        """Add (or look up) a palette entry. Returns its index."""
        # canonical key: (block_id, sorted properties)
        key = (block_id, tuple(sorted(properties.items())))
        if key in self._palette:
            return self._palette[key]
        idx = len(self._palette_list)
        entry = PaletteEntry(name=block_id, properties=dict(properties))
        self._palette_list.append(entry)
        self._palette[key] = idx
        return idx

    def _default_properties(self, block_id: str) -> Dict[str, str]:
        """Return the canonical default properties for a block, if defined."""
        return dict(BLOCK_PROPERTIES.get(block_id, {}))

    # ---- block placement ----

    def set_block(self, pos: Coord, block_id: str, properties: Optional[Dict[str, str]] = None) -> None:
        """Place a single block at local pos. Overwrites any previous block at pos."""
        x, y, z = pos
        sx, sy, sz = self.size
        if x < 0 or x >= sx or y < 0 or y >= sy or z < 0 or z >= sz:
            return
        if properties is None:
            properties = self._default_properties(block_id)
        idx = self._intern(block_id, properties)
        self._cells[(x, y, z)] = idx

    def fill_box(self, a: Coord, b: Coord, block_id: str,
                 properties: Optional[Dict[str, str]] = None,
                 skip: Iterable[Coord] = ()) -> int:
        """Fill an axis-aligned box (inclusive). Returns the count of blocks placed."""
        if properties is None:
            properties = self._default_properties(block_id)
        idx = self._intern(block_id, properties)
        skip_set = set(skip)
        count = 0
        x1, y1, z1 = a
        x2, y2, z2 = b
        sx, sy, sz = self.size
        for x in range(x1, x2 + 1):
            for y in range(y1, y2 + 1):
                for z in range(z1, z2 + 1):
                    if (x, y, z) in skip_set:
                        continue
                    if not (0 <= x < sx and 0 <= y < sy and 0 <= z < sz):
                        continue
                    self._cells[(x, y, z)] = idx
                    count += 1
        return count

    def hollow_box(self, a: Coord, b: Coord, block_id: str,
                   properties: Optional[Dict[str, str]] = None,
                   floor: Optional[str] = None,
                   ceiling: Optional[str] = None) -> int:
        """A box outline: floor + ceiling + 4 walls, with interior hollow."""
        x1, y1, z1 = a
        x2, y2, z2 = b
        count = 0
        if floor is not None:
            count += self.fill_box((x1, y1, z1), (x2, y1, z2), floor)
        if ceiling is not None:
            count += self.fill_box((x1, y2, z1), (x2, y2, z2), ceiling)
        # 4 walls (avoid duplicating corners)
        for x in range(x1, x2 + 1):
            count += self.fill_box((x, y1 + 1, z1), (x, y2 - 1, z1), block_id, properties)
            count += self.fill_box((x, y1 + 1, z2), (x, y2 - 1, z2), block_id, properties)
        for z in range(z1 + 1, z2):
            count += self.fill_box((x1, y1 + 1, z), (x1, y2 - 1, z), block_id, properties)
            count += self.fill_box((x2, y1 + 1, z), (x2, y2 - 1, z), block_id, properties)
        return count

    def add_entity(self, x: int, y: int, z: int, entity_id: str,
                   extra_nbt: Optional[Dict] = None) -> None:
        """Add an entity (sign text, chest contents — tile entities are entities in this format)."""
        ent = Compound({
            "pos": NbtList([nbtlib.Double(float(x) + 0.5),
                            nbtlib.Double(float(y) + 0.5),
                            nbtlib.Double(float(z) + 0.5)]),
            "blockPos": NbtList([nbtlib.Int(x), nbtlib.Int(y), nbtlib.Int(z)]),
            "nbt": Compound(extra_nbt or {}),
            "id": nbtlib.String(entity_id),
        })
        self.entities.append(ent)

    # ---- output ----

    def structure(self) -> Structure:
        return Structure(self.size, list(self.blocks), list(self.entities))

    def to_nbt(self) -> "File[Compound]":
        """Build the NBT file object in the Java Edition structure format.

        The key names matter. Vanilla `StructureTemplate` reads `DataVersion`
        with a capital D — this used to emit lowercase `data_version`, which
        means the version was simply absent and the data fixers had nothing to
        work from. `palette_max` and `version` were invented and are ignored.
        Compared against the author's own files, which carry exactly
        size / palette / blocks / entities / DataVersion.
        """
        size_x, size_y, size_z = self.size
        # Explicit subtypes: nbtlib locks an empty NbtList to the End subtype,
        # which strict readers reject.
        nbt_blocks = NbtList[Compound]([b.to_nbt() for b in self.blocks])
        nbt_palette = NbtList[Compound]([p.to_nbt() for p in self._palette_list])
        nbt_entities = NbtList[Compound](list(self.entities))

        root = Compound({
            "size": NbtList[nbtlib.Int]([nbtlib.Int(size_x), nbtlib.Int(size_y),
                                         nbtlib.Int(size_z)]),
            "palette": nbt_palette,
            "blocks": nbt_blocks,
            "entities": nbt_entities,
            "DataVersion": nbtlib.Int(3955),   # 1.21.1
            "author": nbtlib.String("burg-structure-tools"),
        })
        return File(root, gzipped=True, root_name="")  # gzip root, root name ""

    def save(self, path: str | Path) -> None:
        """Write NBT to file. Compressed (gzip), little-endian."""
        path = Path(path)
        path.parent.mkdir(parents=True, exist_ok=True)
        nbt_file = self.to_nbt()
        # nbtlib's write expects File with root
        nbt_file.save(path)