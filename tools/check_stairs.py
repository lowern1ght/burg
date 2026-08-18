"""Which way does a stair face?

`facing` names the **tall** half of a stair, not the direction it descends.
Measured, not remembered: on `plains/houses/house_lvl6` the ridge stands at x=4,
the west slope carries `facing=east` and the east slope `facing=west` — both
pointing at the ridge. Every comment in `wall.py` had said the opposite, so every
stair the generator placed was mirrored.

So the rule this checker enforces is: **on a roof slope the tall half points
uphill.** A stair whose tall half points away from the rise leaves a step in the
pitch and reads as a notch cut out of the roof.

    python check_stairs.py --calibrate    # the author's 121 files first
    python check_stairs.py                # our output

`--calibrate` is not optional in spirit. Three of five tests in `check_fabric`
were wrong the first time and the author's own files are what proved it; a metric
that is noisy on his work is a broken metric, not a finding.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Dict, List, Tuple

from structures.nbtio import Voxels, load

HERE = Path(__file__).resolve().parent
CORPUS = HERE.parent / ("common/src/main/resources/data/burg/"
                        "structure/plains")
OURS = HERE.parent / ("common/src/main/resources/data/burg/"
                      "structure")

# What the calibrated metric still reports on the author's own 121 files, and
# why it is allowed to: `wheat_farm_lvl3` (5, 4, 11), where three stairs meet at
# a hip-roof corner — (6,4,11) faces north and (6,4,12) faces south, so the two
# planes diverge and "which way is uphill" has no single answer at the cell where
# they meet. One cell in 121 files. Anything above this is a real finding.
CORPUS_RESIDUAL = 1

STEP: Dict[str, Tuple[int, int]] = {
    "north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0),
}


# What counts as evidence that the build continues upward on one side. It has to
# be structure, not furniture — and this is the whole calibration. The first
# version accepted any block and reported 22 hits on the author's own files;
# every one was a stair used as a CHAIR, flagged because the thing "uphill" of it
# was the pressure plate, flower pot or cobblestone-wall table it was drawn up to.
# A chair has no pitch to get backwards.
NOT_STRUCTURE = (
    "_pressure_plate", "_pot", "_wall", "_fence", "_fence_gate", "_pane",
    "_torch", "_sign", "_button", "_carpet", "_rail", "_door", "_trapdoor",
    "_bed", "_candle", "_head", "_banner", "_sapling", "_bush", "_grass",
    "_flower", "_mushroom", "_crop", "_stem", "lantern", "ladder", "vine",
    "chain", "campfire", "cauldron", "lever", "tripwire", "snow", "water",
)


def structural(vox: Voxels, p: Tuple[int, int, int]) -> bool:
    """A block that can carry a roof plane or a flight of steps."""
    x, y, z = p
    sx, sy, sz = vox.size
    if not (0 <= x < sx and 0 <= y < sy and 0 <= z < sz):
        return False
    b = vox.get(p)
    return b is not None and not b.short.endswith(NOT_STRUCTURE)


def solid(vox: Voxels, p: Tuple[int, int, int]) -> bool:
    """Anything at all — used only to ask whether a stair is buried."""
    x, y, z = p
    sx, sy, sz = vox.size
    if not (0 <= x < sx and 0 <= y < sy and 0 <= z < sz):
        return False
    b = vox.get(p)
    if b is None:
        return False
    return not b.short.endswith(("_torch", "ladder", "_sign", "lantern",
                                 "vine", "_pane"))


def audit(vox: Voxels) -> List[Tuple[Tuple[int, int, int], str, str]]:
    """Stairs whose tall half points downhill. Roof slopes only."""
    out = []
    for p, b in vox.solid_items():
        if not b.short.endswith("_stairs"):
            continue
        if b.get("half") != "bottom":
            continue                    # inverted stairs corbel, they do not pitch
        f = b.get("facing")
        if f not in STEP:
            continue
        x, y, z = p
        if solid(vox, (x, y + 1, z)):
            continue                    # buried: not a visible pitch
        dx, dz = STEP[f]
        up_f = structural(vox, (x + dx, y + 1, z + dz))
        up_b = structural(vox, (x - dx, y + 1, z - dz))
        # A slope is a PLANE, not a point: the course above-behind has to be at
        # least two cells wide across the run. Without this, one stray block
        # sitting diagonally above a flat cornice course reads as a rise and the
        # cornice's stairs are all reported backwards — the residual five hits on
        # our own set were every one of them that, a lone cobblestone above the
        # deck roof of `watchtower_lvl2`.
        if up_b:
            bx, bz = x - dx, z - dz
            up_b = any(structural(vox, (bx + ox, y + 1, bz + oz))
                       for ox, oz in ((dz, dx), (-dz, -dx)))
        if up_b and not up_f:
            out.append((p, b.short, f))
    return out


def sweep(paths: List[Path], label: str, quiet_expected: bool) -> int:
    worst: List[Tuple[int, str]] = []
    total = files = 0
    for path in sorted(paths):
        try:
            vox = load(path)
        except Exception as exc:                  # corrupt corpus files
            print(f"  skip {path.name}: {exc}")
            continue
        files += 1
        bad = audit(vox)
        total += len(bad)
        if bad:
            worst.append((len(bad), path.name))
            if len(worst) <= 12:
                cells = ", ".join(f"{p}={f}" for p, _s, f in bad[:4])
                print(f"  {path.name:34s} {len(bad):3d}  {cells}")
    worst.sort(reverse=True)
    print(f"\n{label}: {total} downhill stairs over {files} files")
    if quiet_expected and total > CORPUS_RESIDUAL:
        print("  the metric is NOT quiet on the author's work — it is the metric")
        print("  that is wrong, not the buildings. Do not act on our numbers.")
    elif quiet_expected:
        print(f"  calibrated: {total} known residual, see CORPUS_RESIDUAL")
    return total


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--calibrate", action="store_true",
                    help="run against the author's own buildings first")
    ap.add_argument("--set", default="military,livestock",
                    help="comma-separated folders under structure/ to check")
    args = ap.parse_args()

    if args.calibrate:
        print("author's corpus (must be quiet):")
        sweep(list(CORPUS.rglob("*.nbt")), "plains", quiet_expected=True)
        print()

    for name in args.set.split(","):
        root = OURS / name.strip()
        if not root.exists():
            print(f"{name}: nothing written yet")
            continue
        print(f"{name}:")
        sweep(list(root.rglob("*.nbt")), name, quiet_expected=False)
        print()
    return 0


if __name__ == "__main__":
    sys.exit(main())
