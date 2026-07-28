"""A writer that refuses geometric nonsense, and names who wrote it.

The gates in this repo find faults *after* a build finishes, in a file assembled by
a dozen passes, and then the work is to guess which pass did it. That guessing cost
most of a day: a hay bale placed clear of the fence became a mounting block when a
later pass added a rail beside it; a post cap became a rider when the next rung's
donor put its own fence overhead; an eave slab landed on nothing at all.

`Canvas` closes that gap from the other end. It wraps `Voxels`, keeps the same
`set`/`get` interface so existing device code needs no rewrite, and:

* **checks each write against the shape model** in `solids` — a cube on a bottom
  slab, a rail on a bottom slab, a roof block with nothing bearing it;
* **tags every write with the device that made it**, so a fault reports
  `byre roof` or `pier cap` rather than a bare coordinate;
* can be **strict** (raise on the first offence, for development) or lenient
  (collect and report, for a build that must finish).

It does not replace the gates. It moves the cheap, local, always-wrong cases to the
moment they are made, and leaves the gates for what needs the whole build:
containment, routes, similarity.
"""

from __future__ import annotations

from contextlib import contextmanager
from dataclasses import dataclass, field
from typing import Dict, Iterator, List, Optional, Sequence, Tuple

from nbtlib import Compound

from . import solids
from .nbtio import BlockState, Coord, Voxels


@dataclass
class Fault:
    """One geometric complaint, with the device that caused it."""

    kind: str
    pos: Coord
    device: str
    detail: str

    def __str__(self) -> str:
        return f"{self.kind} at {self.pos} from '{self.device}': {self.detail}"


class FabricError(Exception):
    """Raised on the first fault when the canvas is strict."""


@dataclass
class Canvas:
    """A `Voxels` that argues back.

    Use it exactly like the raw grid — `set`, `get`, `occupied` — and wrap each
    device in `with canvas.device("name")`.
    """

    vox: Voxels
    strict: bool = False
    faults: List[Fault] = field(default_factory=list)
    _device: str = "?"
    _checked: bool = True
    _written: Optional[List[Coord]] = None
    origin: Dict[Coord, str] = field(default_factory=dict)

    # ── plumbing ───────────────────────────────────────────────────

    @contextmanager
    def device(self, name: str, checked: bool = True) -> Iterator["Canvas"]:
        """Tag every write inside the block, and check the plane on the way out.

        Support-from-below can be judged as each block lands, because what is under
        it is already there. "Nothing beside it either" cannot: a device lays a roof
        course cell by cell, so the first cell of every plane would be reported as
        floating. That check waits until the device is finished.

        `checked=False` still records who wrote each cell but passes no judgement.
        That is for the author's own voxels arriving through a graft: his chimney
        stands on his furnace and he is not the one being audited. Keeping the
        tagging is the point — when a later device trips over a cell, the report
        says whether we put it there or he did.
        """
        previous, self._device = self._device, name
        prev_checked, self._checked = self._checked, checked
        written: List[Coord] = []
        self._written = written
        try:
            yield self
        finally:
            if checked:
                self._finish(name, written)
            self._device = previous
            self._checked = prev_checked
            self._written = None

    # ── it must be usable *as* a Voxels ────────────────────────────
    #
    # Every device in `pasture.py` takes a `Voxels` and calls `set`, `get`,
    # `occupied`, `solid_items`, `entities`, `block_nbt`, `size`. Rewriting forty
    # call sites to a new interface would be a large diff with nothing to show for
    # it, so the canvas delegates everything it does not check, and the three
    # attributes that get *assigned* have explicit properties.

    def __getattr__(self, name: str):
        # Only reached for attributes the dataclass does not define.
        return getattr(self.vox, name)

    @property
    def size(self) -> Coord:
        return self.vox.size

    @size.setter
    def size(self, value: Coord) -> None:
        self.vox.size = value

    @property
    def name(self) -> str:
        return self.vox.name

    @name.setter
    def name(self, value: str) -> None:
        self.vox.name = value

    @property
    def entities(self):
        return self.vox.entities

    @entities.setter
    def entities(self, value) -> None:
        self.vox.entities = value

    def get(self, pos: Coord) -> Optional[BlockState]:
        return self.vox.get(pos)

    def occupied(self, pos: Coord) -> bool:
        return self.vox.occupied(pos)

    # ── the checked write ──────────────────────────────────────────

    def set(self, pos: Coord, block: Optional[BlockState],
            nbt: Optional[Compound] = None) -> None:
        if block is not None and self._checked:
            for f in self._inspect(pos, block):
                self.faults.append(f)
                if self.strict:
                    raise FabricError(str(f))
        self.vox.set(pos, block, nbt)
        if block is None:
            self.origin.pop(pos, None)
        else:
            self.origin[pos] = self._device
            if self._written is not None:
                self._written.append(pos)

    def _inspect(self, pos: Coord, block: BlockState) -> List[Fault]:
        x, y, z = pos
        below = self.vox.get((x, y - 1, z))
        out: List[Fault] = []

        if solids.side_attached(block) or y <= 1:
            return out

        # Both rules are about a **vertical** gap in something structural, and his
        # corpus draws that line narrowly: a post carries a beam (743 + 370 cases), a
        # floor fitting carries a block (16 cases), a bottom slab carries nothing
        # (0 cases in 121 files). So the test is the bottom slab — not the footprint,
        # and not every partial top.
        gap = solids.half_step(below)

        if gap and solids.fills_cell(block):
            out.append(Fault("rider", pos, self._device,
                             f"{block.short} rests on {below.short}, whose top is "
                             f"at {solids.top_face(below)} of its cell"))

        # 1 case in his 121 readable files.
        if gap and solids.is_rail(block):
            out.append(Fault("rail-on-half", pos, self._device,
                             f"{block.short} over {below.short}"))

        return out

    def _finish(self, name: str, written: Sequence[Coord]) -> None:
        """Checks that need the device's whole plane in place."""
        for pos in written:
            x, y, z = pos
            block = self.vox.get(pos)
            if block is None or not solids.is_roof_material(block) or y <= 1:
                continue
            if self.vox.get((x, y - 1, z)) is not None:
                continue
            near = [self.vox.get((x + dx, y + dy, z + dz))
                    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))
                    for dy in (-1, 0, 1)]
            if any(b is not None for b in near):
                continue
            f = Fault("floating", pos, name,
                      f"{block.short} has nothing under or beside it")
            self.faults.append(f)
            if self.strict:
                raise FabricError(str(f))

    # ── surfaces, so a device never has to guess ───────────────────

    def surface(self, x: int, z: int, y: int) -> Optional[float]:
        """Absolute height of the top of the block at (x, y, z)."""
        t = solids.top_face(self.vox.get((x, y, z)))
        return None if t is None else y + t

    def put_on(self, x: int, z: int, y_support: int,
               block: BlockState) -> Optional[Coord]:
        """Place `block` **on top of** the block at (x, y_support, z).

        Returns the cell used, or None with a fault recorded when the support
        cannot carry anything — which is the half-slab case, stated once here
        instead of being rediscovered by each device.
        """
        support = self.vox.get((x, y_support, z))
        blocked = solids.top_face(support) is None or solids.half_step(support)
        if blocked and self._checked:
            self.faults.append(Fault(
                "no-support", (x, y_support + 1, z), self._device,
                f"cannot place {block.short} on "
                f"{support.short if support else 'air'}"))
            if self.strict:
                raise FabricError(str(self.faults[-1]))
            return None
        cell = (x, y_support + 1, z)
        self.set(cell, block)
        return cell

    def inspect_all(self) -> List[Fault]:
        """Apply the write-time rules to every block already in the grid.

        The same code that guards a write also judges a finished file, so the gate
        and the writer can never drift apart — which they did once already, when the
        gate allowed a fence over a fence and the writer did not.
        """
        found: List[Fault] = []
        for pos, block in self.vox.solid_items():
            for f in self._inspect(pos, block):
                found.append(Fault(f.kind, f.pos,
                                   self.origin.get(pos, "?"), f.detail))
        return found

    def report(self) -> str:
        if not self.faults:
            return "fabric: clean"
        by_device: Dict[str, List[Fault]] = {}
        for f in self.faults:
            by_device.setdefault(f.device, []).append(f)
        lines = [f"fabric: {len(self.faults)} fault(s)"]
        for dev, fs in sorted(by_device.items(), key=lambda t: -len(t[1])):
            lines.append(f"  {dev}: {len(fs)}")
            for f in fs[:3]:
                lines.append(f"     {f.kind} at {f.pos} — {f.detail}")
        return "\n".join(lines)
