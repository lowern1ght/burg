"""Audit the livestock set against every documented rule, one line per rule.

Run from tools/:  python audit_livestock.py

`check_fabric` and `check_pens` answer "is it sound". This answers "does it obey
what `CLAUDE.md`, `docs/BUILD_LANGUAGE.md` and `docs/STYLE.md` actually say" —
including the rules that are about taste rather than integrity, and which no gate
was watching. Every threshold that can be measured off the author's corpus is,
rather than being asserted here.
"""

from __future__ import annotations

import sys
from collections import Counter
from pathlib import Path
from typing import Dict, List, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))

from structures import pasture as P
from structures.anatomy import analyse
from structures.nbtio import Voxels, load, load_corpus

ROOT = Path("../common/src/main/resources/data/burg")
MINE = ROOT / "structure/livestock"

VEC = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}


def rung_of(stem: str) -> int:
    return 0 if "_lvl" not in stem else int(stem.split("_lvl")[1])


def breed_of(stem: str) -> P.Breed:
    return {"cow": P.COW, "pig": P.PIG, "she": P.SHEEP}[stem[:3]]


def main() -> int:
    files = {f.stem: load(f) for f in sorted(MINE.rglob("*.nbt"))}
    author, _ = load_corpus(str(ROOT / "structure"))
    author = {n: v for n, v in author.items()
              if not n.startswith(("military/", "livestock/"))}
    rows: List[Tuple[str, bool, str]] = []

    def rule(name: str, ok: bool, detail: str) -> None:
        rows.append((name, ok, detail))

    # ── palette rulings (CLAUDE.md, burg-material-palette) ──────────
    banned = Counter()
    for n, v in files.items():
        for k in ("podzol", "gravel", "sand", "basalt", "blackstone",
                  "stone_bricks", "deepslate_bricks"):
            if v.counts().get(k):
                banned[k] += v.counts()[k]
    rule("no podzol / gravel / sand / worked or nether stone", not banned,
         f"found {dict(banned)}" if banned else "clean")

    legacy = {n: [k for k in v.counts() if k == "grass"] for n, v in files.items()}
    rule("no pre-1.20.3 ids", not any(legacy.values()),
         "clean" if not any(legacy.values()) else str(legacy))

    # ── level gradation (burg-level-gradation-rules) ────────────────
    bad = {}
    for n, v in files.items():
        if rung_of(n) >= 4:
            continue
        pen = P.compose_farmstead(breed_of(n), P.LADDER[rung_of(n)], seed=0)
        hx, hz = pen.house_at
        bx0, bx1, bz0, bz1 = P.house_bounds(
            P.donor_house(P.breed_donors(breed_of(n))[rung_of(n)]))
        found = Counter()
        for p2, b in v.solid_items():
            if b.short not in ("lantern", "red_wall_banner", "white_wall_banner"):
                continue
            # Inside his walls it is **his** fitting at **his** level of richness,
            # and his ladder is not ours to gate.
            if hx + bx0 <= p2[0] <= hx + bx1 and hz + bz0 <= p2[2] <= hz + bz1:
                continue
            found[b.short] += 1
        if found:
            bad[n] = dict(found)
    rule("prestige fittings we place: only from lvl4", not bad,
         "clean" if not bad else str(bad))

    # ── campfire on the floor (block-placement-rules) ───────────────
    raised = []
    for n, v in files.items():
        for p, b in v.solid_items():
            if b.short == "campfire" and p[1] != 1:
                raised.append(f"{n}@{p}")
    rule("campfire sits on the floor", not raised, "none used" if not raised
         else str(raised))

    # ── stair facing: the tall side, as his own runs prove ──────────
    runs_toward = runs_against = 0
    for n, v in files.items():
        for p, b in v.solid_items():
            if not b.short.endswith("_stairs") or b.get("half") != "bottom":
                continue
            f = b.get("facing")
            if f not in VEC:
                continue
            dx, dz = VEC[f]
            up = v.get((p[0] + dx, p[1] + 1, p[2] + dz))
            down = v.get((p[0] - dx, p[1] + 1, p[2] - dz))
            if up is not None and up.short == b.short and up.get("facing") == f:
                runs_toward += 1
            if down is not None and down.short == b.short and down.get("facing") == f:
                runs_against += 1
    rule("stair runs ascend toward their facing (author: 648/0)",
         runs_against == 0, f"toward={runs_toward} against={runs_against}")

    # A lean-to's high side is the house wall: its stairs must face the house.
    wrong_pitch = []
    for n, v in files.items():
        breed = breed_of(n)
        if breed.byre_form != "lean":
            continue
        pen = P.compose_farmstead(breed, P.LADDER[rung_of(n)], seed=0)
        if pen.shed is None:
            continue
        for p, b in v.solid_items():
            if not b.short.endswith("_stairs"):
                continue
            if pen.shed.x0 <= p[0] <= pen.shed.x1 + 1 \
                    and pen.shed.z0 <= p[2] <= pen.shed.z1 \
                    and b.get("facing") == "east":
                wrong_pitch.append(f"{n}@{p}")
    rule("lean-to pitch rises toward the house wall", not wrong_pitch,
         "clean" if not wrong_pitch else f"{len(wrong_pitch)} inverted stairs")

    # ── timber as timber (STYLE.md) ─────────────────────────────────
    def plank_share(v: Voxels) -> float:
        c = v.counts()
        built = sum(n for k, n in c.items()
                    if k not in P.TERRAIN_LIKE and k != "water")
        return c.get("oak_planks", 0) / built if built else 0.0
    his = sorted(plank_share(v) for v in author.values())
    mine = {n: plank_share(v) for n, v in files.items()}
    ceiling = his[int(len(his) * 0.95)]
    over = {n: round(s, 3) for n, s in mine.items() if s > ceiling}
    rule(f"plank share within his p95 ({ceiling:.2f})", not over,
         f"max mine {max(mine.values()):.3f}" + (f", over: {over}" if over else ""))

    # ── water grammar: dug at y=0, basin at y=1 in masonry ──────────
    bad_water = []
    for n, v in files.items():
        for p, b in v.solid_items():
            if b.short != "water" or p[1] in (0, 1):
                bad_water.append(f"{n}@{p}") if b.short == "water" and p[1] > 1 else None
    rule("water only at y=0 (dug) or y=1 (basin)", not bad_water,
         "clean" if not bad_water else str(bad_water[:3]))

    lily_bad = []
    for n, v in files.items():
        for p, b in v.solid_items():
            if b.short != "lily_pad":
                continue
            below = v.get((p[0], p[1] - 1, p[2]))
            if below is None or below.short != "water":
                lily_bad.append(f"{n}@{p}")
    rule("lily pads sit directly over water (his 53/54)", not lily_bad,
         "clean" if not lily_bad else str(lily_bad[:3]))

    # ── connectors: exactly one terminator, with its block entity ───
    conn = {}
    for n, v in files.items():
        jig = [p for p, b in v.solid_items() if b.short == "jigsaw"]
        pools = [str((v.block_nbt.get(p) or {}).get("pool")) for p in jig]
        conn[n] = (len(jig), pools)
    ok = all(c == 1 and pools == ["minecraft:empty"] for c, pools in conn.values())
    rule("exactly one terminator connector per file", ok,
         "clean" if ok else str({n: c for n, c in conn.items() if c[0] != 1}))

    # ── footprint constant per breed (CLAUDE.md) ────────────────────
    foot: Dict[str, set] = {}
    for n, v in files.items():
        foot.setdefault(n.split("_lvl")[0], set()).add((v.size[0], v.size[2]))
    ok = all(len(s) == 1 for s in foot.values())
    rule("footprint identical at every level", ok,
         str({k: sorted(s) for k, s in foot.items()}))

    # ── style bands from the corpus profile ────────────────────────
    from structures.critic import BANDS, MIRROR_X_FAIL, MIRROR_Z_FAIL
    from structures.corpus import measure
    out_of_band = {}
    for n, v in files.items():
        m = measure(v)
        for key, (lo, hi) in BANDS.items():
            val = float(getattr(m, key))
            if val < lo or val > hi:
                out_of_band.setdefault(n, []).append(f"{key}={val:.3f}")
        if m.mirror_x > MIRROR_X_FAIL or m.mirror_z > MIRROR_Z_FAIL:
            out_of_band.setdefault(n, []).append("mirror")
    soft = sum(1 for v in out_of_band.values())
    rule("density / detail / cover inside his p05–p95 (soft)", soft == 0,
         f"{soft} files outside a soft band: "
         + str({k: v for k, v in list(out_of_band.items())[:3]}))


    # ── the two newest rulings in CLAUDE.md ─────────────────────────
    FUNCTIONAL = {"chest", "barrel", "composter", "cauldron", "water_cauldron",
                  "crafting_table", "furnace", "smoker", "bell", "lectern",
                  "white_bed", "campfire", "anvil", "stonecutter", "loom"}
    walls_mine = Counter()
    stacks_mine = []
    for n, v in files.items():
        pen = P.compose_farmstead(breed_of(n), P.LADDER[rung_of(n)], seed=0)
        hx, hz = pen.house_at
        bx0, bx1, bz0, bz1 = P.house_bounds(
            P.donor_house(P.breed_donors(breed_of(n))[rung_of(n)]))

        def his(x: int, z: int) -> bool:
            # One cell of slack: his chimney corbels out past its own wall line.
            return (hx + bx0 - 1 <= x <= hx + bx1 + 1
                    and hz + bz0 - 1 <= z <= hz + bz1 + 1)

        for p2, b in v.solid_items():
            if b.short.endswith("_wall") and not his(p2[0], p2[2]):
                walls_mine[b.short] += 1
            below = v.get((p2[0], p2[1] - 1, p2[2]))
            if below is not None and below.short in FUNCTIONAL                     and not his(p2[0], p2[2]):
                stacks_mine.append(f"{n}: {b.short} on {below.short}@{p2}")
    rule("no stone *_wall block in anything we place", not walls_mine,
         "clean — the rest is his chimney flue inside the grafted house"
         if not walls_mine else str(dict(walls_mine)))
    rule("nothing stacked on a functional block", not stacks_mine,
         "clean — the stacks that exist are his flue over his own furnace"
         if not stacks_mine else str(stacks_mine[:3]))

    # ── report ──────────────────────────────────────────────────────
    width = max(len(r[0]) for r in rows)
    fails = 0
    for name, ok, detail in rows:
        print(f"  {'PASS' if ok else 'FAIL'}  {name:<{width}}  {detail}")
        fails += 0 if ok else 1
    print(f"\n{len(rows) - fails}/{len(rows)} documented rules hold")
    return 1 if fails else 0


if __name__ == "__main__":
    raise SystemExit(main())
