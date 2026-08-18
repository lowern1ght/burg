"""Run the canvas's write-time rules over the author's own corpus.

A rule that fires on his buildings is a wrong rule, not a finding. That sentence has
now cost two rewrites, so it gets a command:

    python calibrate_fabric.py                # his plains corpus
    python calibrate_fabric.py --set livestock

The first version of `carries_above` demanded a full footprint and reported 443
faults on a build with none — every one of them a fence over a fence or a beam over
a post, both of which he does hundreds of times. This prints his numbers per rule so
the next rule gets checked before it is believed.

It also reports files that will not decompress, because four of his do.
"""

from __future__ import annotations

import argparse
import collections
from pathlib import Path
from typing import Dict, List, Sequence

from structures.fabric import Canvas
from structures.nbtio import CorruptStructure, load

DATA = Path("../common/src/main/resources/data/burg/structure")


def scan(paths: Sequence[Path]) -> None:
    per_rule: collections.Counter = collections.Counter()
    files_with: Dict[str, set] = collections.defaultdict(set)
    examples: Dict[str, str] = {}
    broken: List[str] = []
    read = 0

    for path in paths:
        try:
            vox = load(path)
        except CorruptStructure as exc:
            broken.append(f"{path.relative_to(path.parents[2])}: {exc}")
            continue
        read += 1
        for f in Canvas(vox).inspect_all():
            per_rule[f.kind] += 1
            files_with[f.kind].add(path.stem)
            examples.setdefault(f.kind, f"{path.stem} {f.pos} — {f.detail}")

    print(f"  {read} file(s) read, {len(broken)} unreadable")
    for line in broken:
        print(f"    UNREADABLE  {line}")
    if not per_rule:
        print("    every write-time rule is quiet on this set")
    for kind, n in per_rule.most_common():
        print(f"    {kind:14s} {n:5d} in {len(files_with[kind]):3d} file(s)"
              f"   e.g. {examples[kind]}")
    print("\n  Read the counts as the band, not as bugs: what he does, we may do.")


def selftest() -> int:
    """Does the guard fire on what is wrong and stay quiet on what is his?

    A checker nobody checks is a checker that quietly stops working. These are the
    exact cases that have shipped broken or been falsely reported in this repo.
    """
    from structures.fabric import Canvas
    from structures.nbtio import Voxels, state

    def canvas() -> Canvas:
        return Canvas(Voxels((8, 8, 8)))

    cases = []

    # 1. The bug that shipped: 38 leaves half a block over their slab.
    c = canvas()
    c.set((3, 1, 3), state("oak_slab", type="bottom"))
    with c.device("pier-cap planter"):
        c.set((3, 2, 3), state("oak_leaves", persistent="true"))
    cases.append(("leaf over a bottom slab", [f.kind for f in c.faults] == ["rider"],
                  c.faults))

    # 2. A fence over a bottom slab: 0 in his corpus.
    c = canvas()
    c.set((5, 1, 5), state("oak_slab", type="bottom"))
    with c.device("boundary"):
        c.set((5, 2, 5), state("oak_fence"))
    cases.append(("fence over a bottom slab",
                  [f.kind for f in c.faults] == ["rail-on-half"], c.faults))

    # 3-6. His own idioms, which the first version of this rule reported 443 times.
    c = canvas()
    c.set((2, 1, 2), state("oak_fence"))
    c.set((4, 1, 4), state("oak_trapdoor", half="bottom", open="false",
                           facing="north"))
    c.set((6, 1, 6), state("oak_slab", type="top"))
    c.set((7, 1, 7), state("oak_fence"))
    with c.device("frame"):
        c.set((2, 2, 2), state("oak_planks"))      # beam on a post: 370 cases
        c.set((4, 2, 4), state("oak_planks"))      # block over a hatch: 9 cases
        c.set((6, 2, 6), state("hay_block", axis="y"))   # on a top slab: 2846
        c.set((7, 2, 7), state("oak_fence"))       # fence on fence: 743 cases
    cases.append(("his four legitimate stacks", not c.faults, c.faults))

    # 7. A roof course laid cell by cell must not be called floating.
    c = canvas()
    with c.device("byre roof"):
        for x in range(2, 7):
            c.set((x, 3, 4), state("oak_stairs", facing="east", half="bottom"))
    cases.append(("a legitimate roof course", not c.faults, c.faults))

    # 8. put_on refuses a half support and accepts a post.
    c = canvas()
    c.set((3, 1, 3), state("oak_slab", type="bottom"))
    c.set((2, 1, 2), state("oak_fence"))
    with c.device("stores"):
        refused = c.put_on(3, 3, 1, state("hay_block", axis="y"))
        allowed = c.put_on(2, 2, 1, state("oak_planks"))
    cases.append(("put_on: refuses a slab, accepts a post",
                  refused is None and allowed == (2, 2, 2), c.faults))

    # 9. The driver must refuse to ship a rung with a fault.
    import build_livestock
    from structures import pasture
    real = pasture.planting

    def sabotaged(vox, breed, mask, free, rng):
        real(vox, breed, mask, free, rng)
        for (x, z) in list(mask)[:1]:
            vox.set((x, 1, z), state("oak_slab", type="bottom"))
            vox.set((x, 2, z), state("oak_leaves", persistent="true"))

    pasture.planting = sabotaged
    try:
        rows = build_livestock.build_family(pasture.BREEDS[0])
        blocked = all(not r.ok and r.fabric for r in rows)
    finally:
        pasture.planting = real
    cases.append(("the driver rejects a sabotaged device", blocked, []))

    print("  guard selftest")
    bad = 0
    for name, passed, faults in cases:
        print(f"    {'pass' if passed else 'FAIL'}  {name}")
        if not passed:
            bad += 1
            for f in faults:
                print(f"          got {f}")
    print(f"\n  {len(cases) - bad}/{len(cases)} pass")
    return 1 if bad else 0


def main(argv: Sequence[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--set", default="plains",
                    help="folder under structure/ (default: plains, his corpus)")
    ap.add_argument("--selftest", action="store_true",
                    help="check the guard itself: fires on the known bugs, silent "
                         "on his idioms, and blocks a sabotaged build")
    a = ap.parse_args(argv)
    if a.selftest:
        return selftest()
    root = DATA / a.set
    paths = sorted(root.rglob("*.nbt"))
    if not paths:
        print(f"  nothing under {root}")
        return 1
    print(f"  {a.set}: {len(paths)} structure(s)")
    scan(paths)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
