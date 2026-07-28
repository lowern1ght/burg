"""Can the animals that ship in a pen file get out of it?

Run from tools/:

    python check_pens.py                       # every livestock NBT
    python check_pens.py path/to/one.nbt ...

This deliberately trusts **nothing from the generator**. `build_livestock.py`
gates candidates while they are still in memory, using the octagon it just
built as the definition of "inside"; if that definition were wrong the gate
would agree with itself and pass. So this reads the shipped file back off disk
and starts the flood fill from the animals' own recorded positions, which are
also in the file. The question it answers is the one that matters in a world:

    starting where the cow actually stands, can it reach the edge of the box?

The movement model is the animal's, not the player's — see `pasture.py`:

* a full-block jump up (which is why any bare full block in a fence line is an
  escape route, and why every post in these pens carries a rail on top)
* falls are free
* a closed gate or door is a wall, because an animal cannot open one
* a fence, wall or gate can be **perched on** at +1.5, so a mounting block
  beside the boundary defeats it
* two clear cells of headroom: a cow is 1.4 tall
"""

from __future__ import annotations

import sys
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))

from structures.nbtio import Coord, Voxels, load
from structures.pasture import MOB_JUMP, NEIGH4, _mob_passable, _mob_surface

LIVESTOCK = Path("../common/src/main/resources/data/onceuponatown/structure/livestock")


def animal_cells(vox: Voxels) -> List[Coord]:
    """Where the shipped animals stand, from the file's own `entities` list."""
    out: List[Coord] = []
    for e in vox.entities:
        bp = e.get("blockPos")
        if bp is None:
            continue
        out.append((int(bp[0]), int(bp[1]), int(bp[2])))
    return out


def standable_map(vox: Voxels) -> Dict[Coord, float]:
    sx, sy, sz = vox.size
    cells: Dict[Coord, float] = {}
    for x in range(sx):
        for z in range(sz):
            for y in range(1, sy):
                p = (x, y, z)
                if not _mob_passable(vox.get(p)):
                    continue
                if not _mob_passable(vox.get((x, y + 1, z))):
                    continue
                h = _mob_surface(vox, (x, y - 1, z))
                if h is not None:
                    cells[p] = h
    return cells


def escapes(vox: Voxels) -> Tuple[List[Coord], int, int]:
    """(cells on the box edge the herd can reach, herd size, cells explored)."""
    cells = standable_map(vox)
    sx, _sy, sz = vox.size
    start = [c for c in animal_cells(vox) if c in cells]
    # An animal recorded standing in a cell the model calls unstandable is worth
    # knowing about too, so fall back to the cell above it before giving up.
    for c in animal_cells(vox):
        if c not in cells and (c[0], c[1] + 1, c[2]) in cells:
            start.append((c[0], c[1] + 1, c[2]))
    seen = set(start)
    stack = list(start)
    out: List[Coord] = []
    while stack:
        p = stack.pop()
        if p[0] in (0, sx - 1) or p[2] in (0, sz - 1):
            out.append(p)
        for dx, dz in NEIGH4:
            for dy in (1, 0, -1, -2, -3, -4):
                q = (p[0] + dx, p[1] + dy, p[2] + dz)
                if q not in cells or q in seen:
                    continue
                if cells[q] - cells[p] > MOB_JUMP + 1e-6:
                    continue
                seen.add(q)
                stack.append(q)
                break
    return sorted(out), len(animal_cells(vox)), len(seen)


def main(argv: Sequence[str]) -> int:
    paths = [Path(a) for a in argv] or sorted(LIVESTOCK.rglob("*.nbt"))
    if not paths:
        print(f"no NBT found under {LIVESTOCK}")
        return 1
    bad = 0
    for p in paths:
        vox = load(p)
        vox.name = p.stem
        leaks, herd, explored = escapes(vox)
        tag = "HOLDS" if not leaks else "LEAKS"
        print(f"  {tag}  {p.name:26s} animals={herd} reachable_cells={explored:4d}"
              + (f"  out at {leaks[:4]}" if leaks else ""))
        if leaks:
            bad += 1
    print(f"\n{len(paths) - bad}/{len(paths)} pens hold their animals")
    return 1 if bad else 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
