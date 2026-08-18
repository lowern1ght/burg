"""What identifies a trade, and how the author turns his blocks.

Run from tools/:  python measure_trade.py

Two tables the skill needs and nobody had written down:

1. **Profession signature** — for each job family in `plains/jobs`, the blocks that
   are peculiar to it (rare elsewhere), plus how many of them persist up the ladder.
   A building reads as a bakery because of the oven and the bread, not because of
   its roof.
2. **Rotation** — how each directional block is actually turned relative to the
   building: doors, beds, furnaces, trapdoors, ladders, wall torches, logs, hay,
   stairs. Guessing these is how a roof comes out inside out and a ladder ends up
   hanging in the air.
"""

from __future__ import annotations

import re
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Dict, List, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))

from structures.anatomy import analyse
from structures.nbtio import CorruptStructure, Voxels, load

PLAINS = Path("../common/src/main/resources/data/burg/structure/plains")
VEC = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
OPP = {"north": "south", "south": "north", "west": "east", "east": "west"}


def safe(p: Path):
    try:
        return load(p)
    except CorruptStructure:
        return None


def families() -> Dict[str, List[Path]]:
    fams: Dict[str, List[Path]] = defaultdict(list)
    for f in sorted(PLAINS.rglob("*.nbt")):
        base = re.match(r"^(.*?)(?:_lvl\d+)?$", f.stem).group(1)
        if "manualtest" in base:
            continue
        fams[base].append(f)
    return fams


def main() -> int:
    fams = families()
    loaded = {k: [v for v in (safe(f) for f in fs) if v] for k, fs in fams.items()}

    # ── 1. profession signature ────────────────────────────────────
    per_family_ids: Dict[str, set] = {}
    id_families: Counter = Counter()
    for name, vs in loaded.items():
        ids = set()
        for v in vs:
            ids |= set(v.counts())
        per_family_ids[name] = ids
        for i in ids:
            id_families[i] += 1
    total = len(loaded)
    print(f"=== 1. what marks a trade ({total} families) ===")
    print("   blocks that appear in this family and in at most two others\n")
    for name in sorted(per_family_ids):
        rare = sorted(i for i in per_family_ids[name] if id_families[i] <= 3)
        if not rare:
            continue
        # how many survive the whole ladder (present at the top rung too)
        top = loaded[name][-1].counts()
        kept = [i for i in rare if top.get(i)]
        print(f"  {name:20s} {', '.join(rare[:9])}")
        if len(rare) > 9:
            print(f"  {'':20s} {', '.join(rare[9:18])}")
        print(f"  {'':20s} -> {len(kept)}/{len(rare)} still there at the top rung")

    # ── 2. rotation, measured against the shell ────────────────────
    print("\n=== 2. how he turns things ===")
    stats: Dict[str, Counter] = defaultdict(Counter)
    for name, vs in loaded.items():
        for v in vs:
            try:
                ana = analyse(v)
            except Exception:
                continue
            x0, x1, z0, z1 = ana.shell
            cx, cz = (x0 + x1) / 2, (z0 + z1) / 2
            for p, b in v.solid_items():
                f = b.get("facing")
                n = b.short
                if f in VEC:
                    dx, dz = VEC[f]
                    # does `facing` point outward from the middle, or inward?
                    outward = (dx * (p[0] - cx) + dz * (p[2] - cz)) > 0
                    key = {"oak_door": "door", "white_bed": "bed",
                           "furnace": "furnace", "smoker": "furnace",
                           "blast_furnace": "furnace",
                           "oak_trapdoor": "trapdoor", "ladder": "ladder",
                           "wall_torch": "wall_torch", "barrel": "barrel",
                           "oak_stairs": "stairs", "cobblestone_stairs": "stairs",
                           "oak_wall_sign": "wall_sign",
                           "white_wall_banner": "wall_banner"}.get(n)
                    if key:
                        stats[key]["outward" if outward else "inward"] += 1
                if n.endswith(("_log", "_wood")) and b.get("axis"):
                    stats["log axis"][b.get("axis")] += 1
                if n == "hay_block":
                    stats["hay axis"][b.get("axis")] += 1
                if n.endswith("_slab") and b.get("type"):
                    stats["slab type"][b.get("type")] += 1
                if n.endswith("_stairs") and b.get("half"):
                    stats["stair half"][b.get("half")] += 1
                if n.endswith("_trapdoor"):
                    stats["trapdoor half"][b.get("half")] += 1
                    stats["trapdoor open"][b.get("open")] += 1
    for key in ("door", "bed", "furnace", "barrel", "trapdoor", "ladder",
                "wall_torch", "wall_sign", "wall_banner", "stairs",
                "log axis", "hay axis", "slab type", "stair half",
                "trapdoor half", "trapdoor open"):
        c = stats.get(key)
        if not c:
            continue
        tot = sum(c.values())
        parts = ", ".join(f"{k}={v} ({100*v//tot}%)" for k, v in c.most_common())
        print(f"  {key:15s} {parts}")

    # ── 3. the one rule that decides a ladder's facing ─────────────
    print("\n=== 3. stair runs: does a staircase ascend toward its facing? ===")
    toward = against = 0
    for name, vs in loaded.items():
        for v in vs:
            for p, b in v.solid_items():
                if not b.short.endswith("_stairs") or b.get("half") != "bottom":
                    continue
                f = b.get("facing")
                if f not in VEC:
                    continue
                dx, dz = VEC[f]
                up = v.get((p[0] + dx, p[1] + 1, p[2] + dz))
                dn = v.get((p[0] - dx, p[1] + 1, p[2] - dz))
                if up is not None and up.short == b.short and up.get("facing") == f:
                    toward += 1
                if dn is not None and dn.short == b.short and dn.get("facing") == f:
                    against += 1
    print(f"  ascends toward facing: {toward}    against: {against}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
