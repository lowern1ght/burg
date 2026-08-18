"""Is the fence actually connected, and is the roof actually a roof?

Run from tools/:

    python check_fabric.py                    # the livestock set
    python check_fabric.py --calibrate        # the author's own builds first
    python check_fabric.py path/to/file.nbt

Two questions the style gate cannot answer and the escape model does not care
about, both raised on review:

**The fence must always connect.** A fence carries `north/south/east/west`
booleans. Placed with the wrong ones it renders as a line of loose stumps with
gaps of air between the rails — the boundary still holds animals, so no
functional check complains, and it looks broken. `pasture.reconnect` derives
these from the finished grid; this re-derives them from the *shipped file* and
reports any cell that disagrees, plus any post that connects to nothing at all.

**The roof must be whole**: no block hanging with nothing under it, and no gap
left in the middle of a plane.

**Calibrated over the author's 115 building-like NBTs before any of it was
believed**, which changed three of the five tests:

| metric | author max | p95 | median | files with any |
|---|---|---|---|---|
| roof blocks hanging | **0** | 0 | 0 | **0 / 115** |
| gaps in a roof plane | 2 | 1 | 0 | 22 / 115 |
| fence props disagreeing | 20 | 4 | 0 | 29 / 115 |
| rails connecting to nothing | 36 | 19 | 5 | 99 / 115 |
| enclosed cells open to the sky | 7 | 5 | 0 | 25 / 115 |

So:

* **hanging is a hard fault** — zero across every file the author ever built,
  which makes it the sharpest test in this repo.
* **gaps are a fault above 2**, his worst case (`granary_lvl5..7`).
* **wrong fence props are a hard fault for generated files only.** The author
  has them in 29 of his own — he built by hand and left stale states behind —
  but we place every block deliberately, so ours must be exact.
* **a rail connecting to nothing is NOT a fault.** It is his own idiom: a
  free-standing post, a leg under a projecting element, 36 of them in
  `merchant_shop_lvl6`. Reported for information only.
* An enclosed cell open to the sky is likewise normal — courtyards and open work
  bays — so it is counted, not failed.

A first version of this file also failed a *lean-to course that meets nothing
inward*, and reported 35 of them in `house_lvl6`. That was the checker being
wrong about how a pitch is built, not the author leaving a roof unfinished; it is
gone. Measure first, then assert.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))

from structures.fabric import Canvas
from structures.nbtio import BlockState, Coord, Voxels, load
from structures.pasture import FULL_BLOCKS, NEIGH4, STURDY, VEC, AXIS_OF

LIVESTOCK = Path("../common/src/main/resources/data/burg/structure/livestock")
CORPUS = Path("../common/src/main/resources/data/burg/structure")

ROOF_MATERIAL = ("_slab", "_stairs")
# Too thin or too small to bridge a corner or to fill a dead cell.
SIDE_ATTACHED_SOFT = {"short_grass", "grass", "oak_sapling",
                      "flower_pot", "torch", "wall_torch", "lantern",
                      "chain", "dandelion", "poppy"}
RAIL_SUFFIX = ("_fence", "_pane", "_bars")


# ── fences ──────────────────────────────────────────────────────────

def _links(vox: Voxels, p: Coord, direction: str, family: str) -> bool:
    """Would a rail at `p` connect toward `direction` in the actual game?

    The same rule `pasture.reconnect` derives from, and it is measured off the
    author's corpus rather than guessed — including the part that says a fence
    and a `*_wall` block do not connect to each other.
    """
    dx, dz = VEC[direction]
    nb = vox.get((p[0] + dx, p[1], p[2] + dz))
    if nb is None:
        return False
    n = nb.short
    if n in STURDY:
        return True
    if n.endswith("_fence_gate"):
        return AXIS_OF[nb.get("facing", "north")] != AXIS_OF[direction]
    if family == "rail":
        return n.endswith(("_fence", "_pane", "_bars"))
    return n.endswith("_wall")


def fence_faults(vox: Voxels) -> Tuple[List[str], List[str]]:
    """(cells whose connection props are wrong, posts connecting to nothing)."""
    wrong: List[str] = []
    stumps: List[str] = []
    for p, b in sorted(vox.solid_items()):
        n = b.short
        if not n.endswith(RAIL_SUFFIX + ("_wall",)):
            continue
        family = "wall" if n.endswith("_wall") else "rail"
        want = {d: _links(vox, p, d, family)
                for d in ("north", "south", "east", "west")}
        if n.endswith("_wall"):
            have = {d: b.get(d, "none") != "none" for d in want}
        else:
            have = {d: b.get(d, "false") == "true" for d in want}
        if have != want:
            bad = [d for d in want if have[d] != want[d]]
            wrong.append(f"{n}@{p} {','.join(bad)}: has "
                         f"{[d for d in want if have[d]]} wants "
                         f"{[d for d in want if want[d]]}")
        if not any(want.values()):
            stumps.append(f"{n}@{p}")
    return wrong, stumps


# ── roofs ───────────────────────────────────────────────────────────

def _is_roof(b: Optional[BlockState]) -> bool:
    return b is not None and (b.short.endswith(ROOF_MATERIAL)
                              or b.short in ("oak_planks", "hay_block"))


def roof_faults(vox: Voxels, min_y: int = 3) -> Dict[str, List[str]]:
    """Hanging blocks, holes in a plane, and lean-to courses short of the wall."""
    out: Dict[str, List[str]] = {"hanging": [], "holed": [], "sky": []}
    solid = {p for p, b in vox.solid_items()}

    for p, b in sorted(vox.solid_items()):
        if p[1] < min_y or not b.short.endswith(ROOF_MATERIAL):
            continue
        below = (p[0], p[1] - 1, p[2])
        lateral = [(p[0] + dx, p[1], p[2] + dz) for dx, dz in NEIGH4]
        stepped = [(p[0] + dx, p[1] + dy, p[2] + dz)
                   for dx, dz in NEIGH4 for dy in (-1, 1)]
        if below in solid:
            continue
        if any(q in solid for q in lateral):
            continue
        if any(q in solid for q in stepped):
            continue
        out["hanging"].append(f"{b.short}@{p}")

    # A hole: empty cell with roof on three or more sides at its own height.
    sx, sy, sz = vox.size
    for y in range(min_y, sy):
        for x in range(1, sx - 1):
            for z in range(1, sz - 1):
                if (x, y, z) in solid:
                    continue
                ring = sum(1 for dx, dz in NEIGH4
                           if _is_roof(vox.get((x + dx, y, z + dz))))
                if ring >= 3 and not vox.occupied((x, y + 1, z)):
                    out["holed"].append(f"gap@{(x, y, z)} roof on {ring} sides")

    # Enclosed floor cells with nothing above them. Normal in the corpus —
    # courtyards, open work bays — so counted rather than failed.
    for x in range(1, sx - 1):
        for z in range(1, sz - 1):
            if vox.occupied((x, 1, z)):
                continue
            walled = all(vox.occupied((x + dx, 1, z + dz)) for dx, dz in NEIGH4)
            roofed = any(vox.occupied((x, y, z)) for y in range(2, sy))
            if walled and not roofed:
                out["sky"].append(f"enclosed cell open to the sky@{(x, 1, z)}")
    return out


# ── the boundary line ───────────────────────────────────────────────

def line_faults(vox: Voxels, y: int = 1) -> Dict[str, List[str]]:
    """Two ways a fence line reads as broken even when it holds animals.

    * **diagonal step** — two rails meeting only at a corner. Nothing can walk
      through it, so the escape model is happy, and it looks like a gap in the
      run because a fence connects to nothing diagonally.
    * **duplicate run** — two parallel lines of barrier one cell apart with a
      dead cell between them. Usually means one of the two was built against a
      wall that is not where the code thought it was.
    """
    out: Dict[str, List[str]] = {"diagonal": [], "duplicate": []}
    rails = {(p[0], p[2]) for p, b in vox.solid_items()
             if p[1] == y and b.short.endswith(("_fence", "_fence_gate", "_wall"))}
    # Anything solid in the corner cell bridges the step visually — a slab, a
    # trapdoor, a bed. Requiring a *barrier* there reported 14 false positives,
    # every one inside the author's own house where his fences meet across a slab
    # or a trapdoor. The question this metric asks is whether the run looks
    # broken, not whether a cow could squeeze through.
    barrier = {(p[0], p[2]) for p, b in vox.solid_items()
               if p[1] == y and b.short not in SIDE_ATTACHED_SOFT}
    for (x, z) in sorted(rails):
        for dx, dz in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
            q = (x + dx, z + dz)
            if q not in rails:
                continue
            # A shared orthogonal neighbour makes the two part of one run.
            if (x + dx, z) in barrier or (x, z + dz) in barrier:
                continue
            out["diagonal"].append(f"{(x, z)} meets {q} only diagonally")
    seen = set()
    for (x, z) in sorted(rails):
        for dx, dz in ((2, 0), (0, 2)):
            q = (x + dx, z + dz)
            mid = (x + dx // 2, z + dz // 2)
            if q in rails and mid not in barrier and (mid, q) not in seen:
                seen.add((mid, q))
                out["duplicate"].append(
                    f"{(x, z)} and {q} run parallel with {mid} dead between")
    return out


# ── half blocks ─────────────────────────────────────────────────────

# A bottom slab fills the **lower** half of its cell, so a cube in the cell above
# it hangs half a block clear of what is supposedly holding it up. Measured over
# the author's 121 files: 6 occurrences in total, max 2 per file, and every one is
# an `oak_trapdoor` — which attaches to a side and reads fine. So cubes resting on
# a bottom slab are a fault, and side-attached blocks are not.
#
# The reverse — a top slab with nothing beneath it — is NOT a fault: he has 2846
# of them, up to 90 in one file, because a top slab is a step, a table top and a
# railing surface in its own right.
SLAB_RIDERS_MAX = 0
SIDE_ATTACHED = {"oak_trapdoor", "spruce_trapdoor", "iron_trapdoor", "torch",
                 "wall_torch", "lantern", "ladder", "oak_wall_sign", "chain",
                 "tripwire_hook", "white_wall_banner", "red_wall_banner",
                 "brown_wall_banner", "short_grass", "grass", "flower_pot"}


def slab_faults(vox: Voxels) -> List[str]:
    """Cubes and rails sitting in the empty upper half of a bottom slab's cell.

    Delegated to `fabric.Canvas`, which is the same code that refuses the write in
    the first place. It used to be a second implementation here, with its own list of
    side-attached ids, and two copies of a rule are two rules: the writer once
    rejected a fence over a fence that this one allowed. One authority, checked by
    `calibrate_fabric.py --selftest`.
    """
    return [f"{f.kind}: {f.detail} at {f.pos}"
            for f in Canvas(vox).inspect_all()]


def cantilever_faults(vox: Voxels, min_y: int = 2) -> List[str]:
    """Roof blocks too far from anything holding them up.

    Measured over the author's 121 files: a roof block with no column under it is
    1 cell from a supported column 2076 times, 2 cells 355 times, 3 cells ten
    times and 4 cells once (`oven.nbt`). So a deep eave is his idiom and a shelf
    floating four cells out is not. Mine had two blocks with **no** supported
    column within four cells — genuinely hanging in the air.
    """
    solid = {p for p, b in vox.solid_items()}

    def supported(x: int, z: int, y: int) -> bool:
        return any((x, yy, z) in solid for yy in range(1, y))

    out: List[str] = []
    for p, b in sorted(vox.solid_items()):
        x, y, z = p
        if y < min_y or not b.short.endswith(ROOF_MATERIAL):
            continue
        if supported(x, z, y):
            continue
        reach = None
        for r in range(1, 4):
            for dx in range(-r, r + 1):
                dz = r - abs(dx)
                for cand in ((x + dx, y, z + dz), (x + dx, y, z - dz)):
                    if cand in solid and supported(cand[0], cand[2], y):
                        reach = r
                        break
                if reach:
                    break
            if reach:
                break
        if reach is None:
            out.append(f"{b.short}@{p} has no support within 3 cells")
    return out


# ── reporting ───────────────────────────────────────────────────────

# Thresholds, measured above. Only three of the five gate anything.
HANGING_MAX = 0
HOLES_MAX = 2


def report(paths: Sequence[Path], strict_props: bool = True) -> int:
    bad = 0
    for p in paths:
        vox = load(p)
        vox.name = p.stem
        wrong, stumps = fence_faults(vox)
        roof = roof_faults(vox)
        riders = slab_faults(vox)
        floaters = cantilever_faults(vox)
        faults = []
        if floaters:
            faults.append(("cantilever", floaters))
        if len(riders) > SLAB_RIDERS_MAX:
            faults.append(("slab-rider", riders))
        if strict_props and wrong:
            faults.append(("fence-props", wrong))
        if len(roof["hanging"]) > HANGING_MAX:
            faults.append(("roof-hanging", roof["hanging"]))
        if len(roof["holed"]) > HOLES_MAX:
            faults.append(("roof-holes", roof["holed"]))
        print(f"  {'OK   ' if not faults else 'FAULT'} {p.name:26s} "
              f"props={len(wrong)} hanging={len(roof['hanging'])} "
              f"holes={len(roof['holed'])} slab_riders={len(riders)} cantilever={len(floaters)}   "
              f"(info: rails-to-nothing={len(stumps)}, "
              f"open-to-sky={len(roof['sky'])})")
        for label, items in faults:
            for it in items[:4]:
                print(f"          {label}: {it}")
        if faults:
            bad += 1
    print(f"\n{len(paths) - bad}/{len(paths)} clean")
    return bad


def main(argv: Sequence[str]) -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("files", nargs="*")
    ap.add_argument("--calibrate", action="store_true",
                    help="run the author's own builds first")
    a = ap.parse_args(argv)

    if a.calibrate:
        print("=== the author's own builds (the checker must be quiet here) ===")
        ref = [CORPUS / f"plains/houses/{n}.nbt" for n in
               ("house", "house_lvl3", "house_lvl6", "house_2_lvl4")]
        ref += [CORPUS / f"plains/jobs/{n}.nbt" for n in
                ("pig_farm_lvl3", "sheep_field", "cow_field", "carpenter_lvl4")]
        report([p for p in ref if p.exists()], strict_props=False)
        print()

    paths = [Path(f) for f in a.files] or sorted(LIVESTOCK.rglob("*.nbt"))
    print("=== the livestock set ===")
    return 1 if report(paths) else 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
