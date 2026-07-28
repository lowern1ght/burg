"""Are the military buildings enterable, and can you get upstairs?

Distinguishes indoor cells from roof surfaces: a cell counts as indoor only if
something solid roofs its column. Without that, every roof slope reads as an
unreachable upper floor.

**Storeys are found, not assumed.** The first version called anything at y >= 5
an upper floor and anything at y <= 2 the ground. Both are wrong per building:
`barracks_lvl3` puts its upper storey at stand level 4, so its one real upper
floor was scored as ground and the eleven attic cells above it — a void with no
floor and no way in — became the "upper floor" that measured 1/12. I spent a
round chasing a stair that was never broken. So every stand elevation with real
floor area is listed with its own reachability, lowest first, and nothing decides
on your behalf which of them counts as a storey.

    python check_usable.py            # every military piece
    python check_usable.py --attic    # count roof voids too, normally hidden
    python check_usable.py --ladder   # per-level equipment: anything that vanishes
"""
import argparse
import sys
from pathlib import Path
from typing import Dict, List, Optional, Set, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))

from structures.nbtio import load                       # noqa: E402
from structures.traverse import reachable, walkable      # noqa: E402

C = Path("../common/src/main/resources/data/onceuponatown/structure/military")

Coord = Tuple[int, int, int]

# A storey needs room to stand and a floor with area. Fewer cells than this at
# one elevation is furniture, a landing or the void under a pitch — not a floor
# anybody is meant to walk about on.
FLOOR_MIN_CELLS = 8


def indoor(v, cells) -> Set[Coord]:
    sx, sy, sz = v.size
    out = set()
    for p in cells:
        for y in range(p[1] + 2, sy):
            if v.occupied((p[0], y, p[2])):
                out.add(p)
                break
    return out


# The void inside a pitched roof passes the `indoor` test — something solid is
# above it — but it has no floor and is not a storey. It is dropped by area
# (`FLOOR_MIN_CELLS`) rather than by geometry, which is why an attic shows up
# under --attic and nowhere else.


def storeys(ind: Set[Coord]) -> List[List[Coord]]:
    """Every stand elevation with enough floor area to be a storey.

    Deliberately NOT clustered. Merging elevations within two of each other read
    a barracks as one enormous ground floor — its stand levels run 1,2,3,4,5 with
    real area at each, because a stretched donor has a mezzanine and a stair
    landing as well as two storeys, and every gap is 1. Any rule that decides
    which of those is "a floor" is a guess. Listing them is not.
    """
    by_y: Dict[int, List[Coord]] = {}
    for p in ind:
        by_y.setdefault(p[1], []).append(p)
    return [by_y[y] for y in sorted(by_y)
            if len(by_y[y]) >= FLOOR_MIN_CELLS]


# Equipment whose count must never fall as a building is upgraded. The author does
# not remove things: over six of his ladders nothing that appears at one rung is
# missing from a higher one. Mechanically it matters too — `UpgradeAction` spawns
# the per-level delta, so a bench that moves is a bench built twice.
LADDER_KEEP = ("anvil", "stonecutter", "cauldron", "furnace", "smoker",
               "crafting_table", "barrel", "chest", "bed", "lectern",
               "composter", "loom", "smithing_table", "grindstone",
               "blast_furnace", "beehive", "bee_nest")


def _function_of(short: str) -> Optional[str]:
    """The thing a block IS, with its colour thrown away.

    Counting exact ids reported `barracks` losing ten `white_bed` between rungs 3
    and 4 — but it gained ten `orange_bed` in the same step. That is the author
    re-dyeing the bedding, which he does, and it is not equipment going missing.
    A metric that cannot tell a repaint from a removal will cry wolf on his own
    buildings, so the colour comes off before counting.
    """
    for k in LADDER_KEEP:
        if short == k or short.endswith("_" + k):
            return k
    return None


def ladder_families(root: Path, groups) -> int:
    """Per-function counts across each ladder, flagging anything that goes down."""
    import collections
    bad = 0
    for kind, files in groups:
        if not files:
            continue
        hist = []
        for f in files:
            c: collections.Counter = collections.Counter()
            try:
                items = list(load(f).solid_items())
            except Exception:
                continue          # four corpus files are permanently corrupt
            for _p, b in items:
                fn = _function_of(b.short)
                if fn:
                    c[fn] += 1
            hist.append(c)
        # A drop is excused if something else rose in the SAME step: that is a
        # workstation being replaced by a better one, which the author does —
        # `carpenter` trades a chest for a lectern at l7, `pig_farm` its furnace
        # for a second smoker at l4. Two swaps across his fourteen ladders, and
        # the metric flagged both until it learned the difference. What is never
        # allowed is a kind falling with nothing taking its place: that is the
        # `barracks` anvil, present at l1 and l3 and gone from l4 up.
        kinds = sorted({k for c in hist for k in c})
        excused = set()
        for i in range(len(hist) - 1):
            deltas = {k: hist[i + 1].get(k, 0) - hist[i].get(k, 0) for k in kinds}
            gained = sum(d for d in deltas.values() if d > 0)
            lost = -sum(d for d in deltas.values() if d < 0)
            if gained >= lost > 0:
                excused.update((i, k) for k, d in deltas.items() if d < 0)
        print(f"{kind}:  {len(files)} rungs")
        for k in kinds:
            seq = [c.get(k, 0) for c in hist]
            drop = any(b < a and (i, k) not in excused
                       for i, (a, b) in enumerate(zip(seq, seq[1:])))
            swap = any(b < a for a, b in zip(seq, seq[1:])) and not drop
            bad += drop
            tag = "  <-- VANISHES" if drop else ("  (replaced)" if swap else "")
            print(f"   {k:18s} {seq}{tag}")
    print()
    print(f"{bad} ladder(s) where equipment disappears between rungs")
    return bad


# What the calibrated metric still reports on the author's own ladders, and why it
# is allowed to. Two of his fourteen ladders tidy up at the top rung rather than
# only adding to it:
#
#   carpenter  l6 -> l7: loses a chest and a composter, gains a lectern. He is
#              clearing the bench, not losing equipment.
#   pig_farm   l3 -> l4: the furnace goes and the second smoker does not arrive
#              until l6. A genuine removal, deliberate, two rungs apart.
#
# Three flagged counts across those two families. Anything above this on OUR
# output is a real finding: our own case was the `barracks` anvil, present at l1
# and l3 and simply absent from l4 up because it was being scattered by dice.
CORPUS_LADDER_RESIDUAL = 3


def ladder_report(calibrate: bool = False) -> int:
    """Ours, and — with `--calibrate` — the author's own ladders first."""
    import re
    if calibrate:
        plains = C.parent / "plains"
        fams: dict = {}
        for f in sorted(plains.rglob("*.nbt")):
            m = re.match(r"(.+?)(?:_lvl(\d+))?$", f.stem)
            fams.setdefault(m.group(1), []).append((int(m.group(2) or 0), f))
        groups = [(k, [f for _l, f in sorted(v)])
                  for k, v in sorted(fams.items()) if len(v) > 2]
        print("author's ladders (calibration):")
        got = ladder_families(plains, groups)
        if got > CORPUS_LADDER_RESIDUAL:
            print("  NOT quiet on his work — fix the metric, not the buildings")
        else:
            print(f"  calibrated: {got} known residual, "
                  f"see CORPUS_LADDER_RESIDUAL")
        print()
    ours = [(k, sorted(C.joinpath(k).glob("*.nbt")))
            for k in ("barracks", "armory", "watchtower", "training_yard")]
    return ladder_families(C, ours)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--attic", action="store_true",
                    help="also count elevations too small to be a floor")
    ap.add_argument("--ladder", action="store_true",
                    help="check that no equipment count falls between rungs")
    ap.add_argument("--calibrate", action="store_true",
                    help="with --ladder: run the author's own ladders first")
    args = ap.parse_args()
    if args.ladder:
        return ladder_report(args.calibrate)
    if args.attic:
        global FLOOR_MIN_CELLS
        FLOOR_MIN_CELLS = 1

    print("%-22s %-13s %s" % ("building", "size", "storeys, ground first"))
    bad = 0
    for kind in ("barracks", "armory", "watchtower", "training_yard"):
        for f in sorted(C.joinpath(kind).glob("*.nbt")):
            v = load(f)
            sx, _sy, sz = v.size
            cells = walkable(v)
            ind = indoor(v, cells)
            outside = [p for p in cells
                       if (p[0] in (0, sx - 1) or p[2] in (0, sz - 1))
                       and p not in ind]
            seen = reachable(v, outside)
            flats = storeys(ind)
            parts = []
            for i, floor in enumerate(flats):
                got = sum(1 for p in floor if p in seen)
                y = min(p[1] for p in floor)
                tag = ""
                if got == 0:
                    tag = "  NO-WAY-UP" if i else "  ENTER-FAIL"
                    bad += 1
                elif got < len(floor) * 3 // 4:
                    tag = "  part"
                parts.append("y%d %d/%d%s" % (y, got, len(floor), tag))
            print("%-22s %-13s %s" % (f.stem, str(v.size),
                                      "  |  ".join(parts) or "no floor found"))
    print(f"\n{bad} unreachable floor(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
