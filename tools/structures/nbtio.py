"""Robust structure-NBT I/O.

The corpus is the mod author's hand-built structures. Reading it must never
crash the pipeline: 4 of the 127 files in this repo have a damaged deflate
stream (a `* text eol=lf` .gitattributes rule normalized CRLF inside the
gzip bytes before they were committed). Those files are unrecoverable, so
`load_corpus` skips them and reports them rather than dying.

Everything downstream works on `Voxels`: a flat dict of position -> BlockState.
That is deliberately dumber than a builder — parts extraction, rendering and
scoring all want random access by coordinate, not an append log.
"""

from __future__ import annotations

import glob
import gzip
import io
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, Iterator, List, Optional, Tuple

import nbtlib
from nbtlib import Compound, File, List as NbtList

Coord = Tuple[int, int, int]

AIR = ("minecraft:air", ())
EMPTY = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}


@dataclass(frozen=True)
class BlockState:
    """A block id plus its properties, hashable so it can key a palette."""

    name: str
    props: Tuple[Tuple[str, str], ...] = ()

    @property
    def short(self) -> str:
        return self.name.replace("minecraft:", "")

    @property
    def prop_dict(self) -> Dict[str, str]:
        return dict(self.props)

    def get(self, key: str, default: str = "") -> str:
        return self.prop_dict.get(key, default)

    def with_props(self, **changes: str) -> "BlockState":
        p = self.prop_dict
        p.update(changes)
        return BlockState(self.name, tuple(sorted(p.items())))

    @property
    def is_air(self) -> bool:
        return self.name in EMPTY

    def __str__(self) -> str:
        if not self.props:
            return self.short
        inner = ",".join(f"{k}={v}" for k, v in self.props)
        return f"{self.short}[{inner}]"


def state(name: str, **props: str) -> BlockState:
    """Convenience constructor: state("oak_slab", type="top")."""
    if ":" not in name:
        name = "minecraft:" + name
    return BlockState(name, tuple(sorted(props.items())))


@dataclass
class Voxels:
    """A structure as a sparse grid. Air is simply absent from `grid`.

    `block_nbt` holds per-position block-entity data — jigsaw `pool` /
    `target` / `final_state`, chest and furnace `Items`, bed and sign data.
    It has to live beside the grid rather than inside BlockState, because
    BlockState is hashable and doubles as the palette key. Dropping it silently
    strips every jigsaw connector's configuration, which is exactly what
    BuildSchematic.replaceJigsawBlocks reads.
    """

    size: Coord
    grid: Dict[Coord, BlockState] = field(default_factory=dict)
    name: str = ""
    entities: List[Compound] = field(default_factory=list)
    block_nbt: Dict[Coord, Compound] = field(default_factory=dict)
    # The NBT `author` field. Our own output stamps a TOOL_AUTHOR value, which
    # is how the corpus profile tells hand-built reference structures apart from
    # generated ones now that generated output lives inside the corpus tree.
    author: str = ""

    # ---- basic queries ----

    def __len__(self) -> int:
        return len(self.grid)

    def get(self, pos: Coord) -> Optional[BlockState]:
        return self.grid.get(pos)

    def occupied(self, pos: Coord) -> bool:
        b = self.grid.get(pos)
        return b is not None and not b.is_air

    def set(self, pos: Coord, block: Optional[BlockState],
            nbt: Optional[Compound] = None) -> None:
        """Place or clear a cell. Clearing also drops its block-entity data."""
        if block is None or block.is_air:
            self.grid.pop(pos, None)
            self.block_nbt.pop(pos, None)
        else:
            self.grid[pos] = block
            if nbt is not None:
                self.block_nbt[pos] = nbt
            else:
                self.block_nbt.pop(pos, None)

    def take(self, source: "Voxels", src: Coord, dst: Coord,
             block: Optional[BlockState] = None) -> None:
        """Copy source's cell at `src` to `dst` here, block-entity data included.

        The data has to be read from `source`, not from self: when building a
        fresh Voxels the destination's own `block_nbt` is empty, so reading from
        self silently dropped every jigsaw configuration.
        """
        st = block if block is not None else source.grid.get(src)
        if st is None:
            return
        self.set(dst, st, source.block_nbt.get(src))

    def copy(self, name: Optional[str] = None) -> "Voxels":
        """A deep-enough copy: grid, block-entity data and entities."""
        return Voxels(self.size, dict(self.grid),
                      self.name if name is None else name,
                      list(self.entities), dict(self.block_nbt), self.author)

    @property
    def solid_count(self) -> int:
        return sum(1 for b in self.grid.values() if not b.is_air)

    @property
    def volume(self) -> int:
        sx, sy, sz = self.size
        return sx * sy * sz

    @property
    def density(self) -> float:
        return self.solid_count / self.volume if self.volume else 0.0

    @property
    def palette_size(self) -> int:
        return len({b for b in self.grid.values() if not b.is_air})

    def top_y(self) -> int:
        """Highest Y holding a solid block, or -1 when empty."""
        ys = [y for (x, y, z), b in self.grid.items() if not b.is_air]
        return max(ys) if ys else -1

    def layer(self, y: int) -> Dict[Tuple[int, int], BlockState]:
        """The (x, z) slice at height y."""
        return {(x, z): b for (x, y2, z), b in self.grid.items()
                if y2 == y and not b.is_air}

    def solid_items(self) -> Iterator[Tuple[Coord, BlockState]]:
        for pos, b in self.grid.items():
            if not b.is_air:
                yield pos, b

    def counts(self) -> Dict[str, int]:
        """Solid block count keyed by short block id (properties collapsed)."""
        out: Dict[str, int] = {}
        for _, b in self.solid_items():
            out[b.short] = out.get(b.short, 0) + 1
        return out


# ────────────────────────────────────────────────────────────────────
# read
# ────────────────────────────────────────────────────────────────────


class CorruptStructure(Exception):
    """The file's gzip/deflate stream is damaged beyond recovery."""


def _parse(raw: bytes) -> Compound:
    """Parse structure NBT from raw bytes, gzipped or not."""
    try:
        data = gzip.decompress(raw)
    except Exception as exc:
        # Not gzipped? Some tools write plain NBT.
        try:
            return File.parse(io.BytesIO(raw))
        except Exception:
            raise CorruptStructure(str(exc)) from exc
    return File.parse(io.BytesIO(data))


def load(path: str | Path) -> Voxels:
    """Read a structure NBT into Voxels. Raises CorruptStructure if damaged."""
    path = Path(path)
    root = _parse(path.read_bytes())

    size = (int(root["size"][0]), int(root["size"][1]), int(root["size"][2]))
    palette: List[BlockState] = []
    for entry in root["palette"]:
        name = str(entry["Name"])
        props: Tuple[Tuple[str, str], ...] = ()
        if "Properties" in entry:
            props = tuple(sorted((str(k), str(v)) for k, v in entry["Properties"].items()))
        palette.append(BlockState(name, props))

    vox = Voxels(size=size, name=path.stem)
    for b in root["blocks"]:
        st = palette[int(b["state"])]
        if st.is_air:
            continue
        pos = (int(b["pos"][0]), int(b["pos"][1]), int(b["pos"][2]))
        vox.grid[pos] = st
        if "nbt" in b:
            vox.block_nbt[pos] = b["nbt"]

    if "entities" in root:
        vox.entities = list(root["entities"])
    if "author" in root:
        vox.author = str(root["author"])
    return vox


CORPUS_ROOT = "common/src/main/resources/data/onceuponatown/structure"


def load_corpus(root: str | Path = CORPUS_ROOT,
                verbose: bool = False) -> Tuple[Dict[str, Voxels], List[Tuple[str, str]]]:
    """Load every NBT under `root`.

    Returns (structures keyed by path relative to root, list of (path, error)).
    Corrupt files are reported, never raised — the pipeline must survive them.
    """
    root = Path(root)
    out: Dict[str, Voxels] = {}
    broken: List[Tuple[str, str]] = []
    for p in sorted(glob.glob(str(root / "**" / "*.nbt"), recursive=True)):
        rel = str(Path(p).relative_to(root)).replace("\\", "/")
        try:
            vox = load(p)
        except Exception as exc:
            broken.append((rel, f"{type(exc).__name__}: {exc}"))
            continue
        vox.name = rel
        out[rel] = vox
    if verbose:
        print(f"loaded {len(out)} structures from {root}")
        for rel, err in broken:
            print(f"  SKIPPED (corrupt): {rel} — {err}")
    return out, broken


# ────────────────────────────────────────────────────────────────────
# write
# ────────────────────────────────────────────────────────────────────

# 1.21.1. BuildSchematic reads these through vanilla StructureTemplate, so the
# data_version has to be one the target game accepts.
DATA_VERSION = 3955
MC_VERSION = "1.21.1"

# Stamped into every structure this toolchain writes. The author's own files
# carry no `author` field at all, so its presence marks generated content.
TOOL_AUTHOR = "burg-stylekit"


def save(vox: Voxels, path: str | Path, author: str = TOOL_AUTHOR) -> None:
    """Write Voxels out as gzipped structure NBT readable by 1.21.1."""
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)

    # Air first — vanilla convention, and it keeps index 0 meaningful.
    palette: Dict[BlockState, int] = {}
    order: List[BlockState] = []

    def intern(st: BlockState) -> int:
        if st not in palette:
            palette[st] = len(order)
            order.append(st)
        return palette[st]

    # Build plain lists first: nbtlib locks an empty NbtList to the End subtype,
    # so the wrapper has to see its items to infer the element tag.
    block_items = []
    for pos, st in sorted(vox.grid.items()):
        if st.is_air:
            continue
        idx = intern(st)
        entry = Compound({
            "pos": NbtList[nbtlib.Int]([nbtlib.Int(pos[0]), nbtlib.Int(pos[1]),
                                        nbtlib.Int(pos[2])]),
            "state": nbtlib.Int(idx),
        })
        be = vox.block_nbt.get(pos)
        if be is not None:
            entry["nbt"] = be
        block_items.append(entry)

    palette_items = []
    for st in order:
        entry = Compound({"Name": nbtlib.String(st.name)})
        if st.props:
            entry["Properties"] = Compound(
                {k: nbtlib.String(v) for k, v in st.props})
        palette_items.append(entry)

    root = Compound({
        "size": NbtList[nbtlib.Int]([nbtlib.Int(v) for v in vox.size]),
        "palette": NbtList[Compound](palette_items),
        "blocks": NbtList[Compound](block_items),
        "entities": NbtList[Compound](list(vox.entities)),
        "DataVersion": nbtlib.Int(DATA_VERSION),
        "author": nbtlib.String(author),
    })
    File(root, gzipped=True, root_name="").save(path)
