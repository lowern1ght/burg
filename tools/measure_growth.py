"""How the author's buildings grow from level to level, measured.

Run from tools/:  python measure_growth.py

Three questions, and every answer here is a count over
`structure/plains/**` rather than an opinion:

1. **Do his levels develop or get rebuilt?** For each ladder, how much of level N
   survives into N+1 at the same position and state, how much is added, how much is
   replaced in place, and how much is removed.
2. **Which blocks persist** once they appear — the ones that do a job and are never
   taken out — and which are the churn.
3. **What unlocks when.** The rung at which each material first appears, averaged
   over every ladder, which is the material progression as he actually built it.
"""

from __future__ import annotations

import re
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Dict, List, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))

from structures.nbtio import CorruptStructure, Voxels, load


def safe(path: Path):
    """Four corpus files have a damaged deflate stream; skip, never die."""
    try:
        return load(path)
    except CorruptStructure:
        return None

PLAINS = Path("../common/src/main/resources/data/burg/structure/plains")


def ladders() -> Dict[str, List[Tuple[int, Path]]]:
    """Group plains files into families: base plus `_lvlN`."""
    fams: Dict[str, List[Tuple[int, Path]]] = defaultdict(list)
    for f in sorted(PLAINS.rglob("*.nbt")):
        m = re.match(r"^(.*?)(?:_lvl(\d+))?$", f.stem)
        base, lvl = m.group(1), int(m.group(2) or 0)
        if "manualtest" in base:
            continue
        fams[base].append((lvl, f))
    return {k: sorted(v) for k, v in fams.items() if len(v) > 1}


def compare(a: Voxels, b: Voxels) -> Dict[str, int]:
    """Cell-by-cell diff of two consecutive levels."""
    ga = {p: str(s) for p, s in a.solid_items()}
    gb = {p: str(s) for p, s in b.solid_items()}
    kept = sum(1 for p, s in ga.items() if gb.get(p) == s)
    changed = sum(1 for p, s in ga.items() if p in gb and gb[p] != s)
    removed = sum(1 for p in ga if p not in gb)
    added = sum(1 for p in gb if p not in ga)
    return {"kept": kept, "changed": changed, "removed": removed,
            "added": added, "from": len(ga), "to": len(gb)}


def main() -> int:
    fams = ladders()
    print(f"=== 1. do levels develop or get rebuilt?  ({len(fams)} ladders) ===")
    print(f"  {'family':22s} {'rungs':>5s} {'kept':>6s} {'changed':>8s} "
          f"{'removed':>8s} {'added':>6s}   (share of the earlier level)")
    totals = Counter()
    for name, items in fams.items():
        rows = []
        for (l1, f1), (l2, f2) in zip(items, items[1:]):
            a, b = safe(f1), safe(f2)
            if a is None or b is None:
                continue
            d = compare(a, b)
            rows.append(d)
            for k in ("kept", "changed", "removed", "added", "from"):
                totals[k] += d[k]
        if not rows:
            continue
        n = sum(r["from"] for r in rows) or 1
        k = sum(r["kept"] for r in rows) / n
        c = sum(r["changed"] for r in rows) / n
        r_ = sum(r["removed"] for r in rows) / n
        a = sum(r["added"] for r in rows) / n
        print(f"  {name:22s} {len(items):5d} {k:6.0%} {c:8.0%} {r_:8.0%} {a:6.0%}")
    n = totals["from"] or 1
    print(f"\n  ALL LADDERS: kept={totals['kept']/n:.0%} changed={totals['changed']/n:.0%} "
          f"removed={totals['removed']/n:.0%} added={totals['added']/n:.0%}")

    # ── 2. which blocks persist once placed ────────────────────────
    survive: Dict[str, List[int]] = defaultdict(list)
    for name, items in fams.items():
        for (l1, f1), (l2, f2) in zip(items, items[1:]):
            a, b = safe(f1), safe(f2)
            if a is None or b is None:
                continue
            gb = {p: s.short for p, s in b.solid_items()}
            per = Counter()
            tot = Counter()
            for p, s in a.solid_items():
                tot[s.short] += 1
                if gb.get(p) == s.short:
                    per[s.short] += 1
            for k, t in tot.items():
                survive[k].append(round(100 * per[k] / t))
    rows = [(sum(v) / len(v), len(v), k) for k, v in survive.items() if len(v) >= 6]
    rows.sort(reverse=True)
    print("\n=== 2. survival of a block from one rung to the next ===")
    print("  the ones that stay (top): the building grows around them")
    for pct, n, k in rows[:14]:
        print(f"    {k:24s} {pct:3.0f}%   seen in {n} rung transitions")
    print("  the churn (bottom):")
    for pct, n, k in rows[-10:]:
        print(f"    {k:24s} {pct:3.0f}%   seen in {n} rung transitions")

    # ── 3. what unlocks when ───────────────────────────────────────
    first: Dict[str, List[int]] = defaultdict(list)
    for name, items in fams.items():
        seen = set()
        for lvl, f in items:
            v = safe(f)
            if v is None:
                continue
            for _, s in v.solid_items():
                if s.short not in seen:
                    seen.add(s.short)
                    first[s.short].append(lvl)
    print("\n=== 3. the rung a material first appears on, averaged over ladders ===")
    order = sorted(((sum(v) / len(v), len(v), k) for k, v in first.items()
                    if len(v) >= 3), key=lambda t: t[0])
    for avg, n, k in order:
        print(f"    rung {avg:4.1f}  {k:26s} (first seen in {n} ladders)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
