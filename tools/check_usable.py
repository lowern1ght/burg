"""Are the military buildings enterable, and can you get upstairs?

Distinguishes indoor cells from roof surfaces: a cell counts as indoor only if
something solid roofs its column. Without that, every roof slope reads as an
unreachable upper floor.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from structures.nbtio import load                       # noqa: E402
from structures.traverse import walkable, reachable      # noqa: E402

C = Path("../common/src/main/resources/data/onceuponatown/structure/military")


def indoor(v, cells):
    sx, sy, sz = v.size
    out = set()
    for p in cells:
        for y in range(p[1] + 2, sy):
            if v.occupied((p[0], y, p[2])):
                out.add(p)
                break
    return out


def main():
    print("%-22s %-13s %-18s %s" % ("building", "size", "indoor ground",
                                    "indoor upper y>=5"))
    for kind in ("barracks", "armory", "watchtower", "training_yard"):
        for f in sorted(C.joinpath(kind).glob("*.nbt")):
            v = load(f)
            sx, sy, sz = v.size
            cells = walkable(v)
            ind = indoor(v, cells)
            outside = [p for p in cells
                       if (p[0] in (0, sx - 1) or p[2] in (0, sz - 1))
                       and p not in ind]
            seen = reachable(v, outside)
            g = [p for p in ind if p[1] <= 2]
            gu = [p for p in g if p in seen]
            u = [p for p in ind if p[1] >= 5]
            uu = [p for p in u if p in seen]
            ground = "%d/%d%s" % (len(gu), len(g),
                                  "  ENTER-FAIL" if g and not gu else "")
            upper = ("%d/%d%s" % (len(uu), len(u),
                                  "  NO-STAIR" if u and not uu else "")
                     if u else "-")
            print("%-22s %-13s %-18s %s" % (f.stem, str(v.size), ground, upper))


if __name__ == "__main__":
    main()
