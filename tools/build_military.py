"""Generate the military structure set with per-level gradation.

Run from tools/:

    python build_military.py                 # write NBTs + contact sheets
    python build_military.py --dry-run       # gate only, write nothing

Two generation strategies, picked per building by what the corpus can support:

* **Stretched donors** — `barracks`, `armory`, `training_yard`. The author
  already built six increasingly rich levels of `house_2` (four beds, stone
  base, timber upper) and `house_3` (furnace, heavy cobble). Stretching level N
  along its ridge turns a house into a long bunkhouse or workshop while keeping
  every voxel his. This is the highest-fidelity option and is used wherever a
  donor of roughly the right shape exists.

* **Composed from harvested parts** — `watchtower`, `wall_segment`,
  `gatehouse`. The corpus contains nothing tower-shaped or wall-shaped, so
  these are assembled from block states harvested out of `house_3_lvl5` (the
  stone-base/timber-upper grammar) and `house_lvl6` (roof stairs, per side).

Every candidate goes through the critic, and the seed is searched until it
passes. Anything still failing is reported rather than written.
"""

from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass, field, replace
from pathlib import Path
from typing import Callable, Dict, List, Optional, Sequence, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))

from structures import assemble, render_png, wall
from structures.compose import (TowerPlan, WallPlan, YardPlan, Vocabulary,
                                cap_pillars, compose_tower, compose_wall, military_fittings,
                                tidy_leaves,
                                compose_yard, harvest, merge, militarize)
from structures.corpus import modernize
from structures.critic import Finding, Verdict, judge
from structures.traverse import check_route
from structures.nbtio import Voxels, load, save

CORPUS = Path("../common/src/main/resources/data/onceuponatown/structure")
OUT = CORPUS / "military"
SHEETS = Path("structures/out/military")
SEED_TRIES = 24


# ── donors ──────────────────────────────────────────────────────────

def donor(rel: str) -> Voxels:
    v = load(CORPUS / rel)
    v.name = Path(rel).stem
    return v


def build_vocabulary() -> Vocabulary:
    """Stone/timber grammar from house_3_lvl5, roof stairs from house_lvl6.

    house_3_lvl5 needs wall_hi=5: its cobblestone-slab course at y=3 is the
    floor of the timber storey, not the roof, and the zone detector reads
    roofing material as a roof.
    """
    return merge(harvest(donor("plains/houses/house_3_lvl5.nbt"), wall_hi=5),
                 harvest(donor("plains/houses/house_lvl6.nbt")))


# ── build recipes ───────────────────────────────────────────────────

@dataclass
class Recipe:
    """One output NBT: how to build it, and what it is for."""

    name: str
    make: Callable[[int], Voxels]
    note: str = ""
    militarise: bool = True
    palisade: bool = False
    banners: bool = False        # prestige fitting: lvl4+ only
    rich: bool = False           # a top-tier build: gets the stair post cap
    fittings: bool = False       # spear rack / shields / armourer's corner
    kind: str = ""               # fortification kind, for the traversal check


def usable(vox: Voxels, kind: str) -> List[str]:
    """Hard functional checks for a fortification piece.

    A wall that cannot be walked is not a wall, so this sits beside the style
    gate rather than under it. The style gate cannot see the difference: the
    first version of the set rendered well and ten of its twelve stone pieces
    were impassable.
    """
    if not kind:
        return []
    problems: List[str] = []
    start, goal = wall.walk_endpoints(kind, vox)
    r = check_route(vox, start, goal, "walk")
    if not r.ok:
        problems.append(r.reason)
    if kind == "wall_tower":
        cs, cg = wall.climb_endpoints(vox)
        c = check_route(vox, cs, cg, "climb")
        if not c.ok:
            problems.append(c.reason)
    return problems


def tower_recipes(v: Vocabulary) -> List[Recipe]:
    """A lookout that grows into a stone keep.

    Shafts keep one even dimension on purpose. A square shaft is mirror
    symmetric by construction and scores ~0.72 against a corpus median of 0.34;
    an even span has no centre column to be symmetric about and lands near 0.58.
    """
    steps = [
        # Squat and wide, reached from outside — the shape of the garrison
        # tower in reference `40fe`, not the thin internal-ladder shaft the
        # first version built.
        ("watchtower", TowerPlan(shell=5, shell_z=4, storeys=1,
                                 stone_courses=1, open_deck=True,
                                 external_stair=True, beams=False),
         "cobble base, open lookout deck"),
        ("watchtower_lvl1", TowerPlan(shell=5, shell_z=4, storeys=2,
                                      stone_courses=1, open_deck=True,
                                      external_stair=True),
         "second storey under the deck"),
        ("watchtower_lvl2", TowerPlan(shell=5, shell_z=4, storeys=2,
                                      stone_courses=2, open_deck=True,
                                      external_stair=True, banner=True),
         "stone up to the deck, banner"),
        ("watchtower_lvl3", TowerPlan(shell=5, shell_z=4, storeys=3,
                                      stone_courses=2, battlements=True,
                                      external_stair=True),
         "deck closed off, battlements"),
        ("watchtower_lvl4", TowerPlan(shell=5, shell_z=4, storeys=3,
                                      stone_courses=3, battlements=True,
                                      external_stair=True, banner=True),
         "all-stone shaft"),
        ("watchtower_lvl5", TowerPlan(shell=5, shell_z=5, storeys=4,
                                      stone_courses=3, pitched_roof=True,
                                      external_stair=True),
         "keep with a stair-pitched roof"),
        ("watchtower_lvl6", TowerPlan(shell=7, shell_z=5, storeys=4,
                                      stone_courses=4, pitched_roof=True,
                                      external_stair=True, banner=True),
         "full keep"),
    ]
    return [Recipe(n, (lambda p=p: lambda s: compose_tower(v, p, seed=s))(), d)
            for n, p, d in steps]


def fortification_recipes() -> List[Recipe]:
    """The wall set: straight run, corner, gate and flanking tower.

    Five levels each, sharing one material ladder — earth and logs, then timber
    on a cobble plinth, then cobblestone, then a projecting parapet, then the
    timber hoarding gallery. Four kinds x five levels lets a village grow a
    stockade into a castle curtain without ever changing a footprint.

    These do NOT go through `militarize`: `wall.py` lays its own trodden ground
    and planting, and the garrison dressing pass would add barrels, hay and
    village props that belong in a yard rather than on a wall.
    """
    out: List[Recipe] = []
    for kind in ("wall_segment", "wall_corner", "gatehouse", "wall_tower"):
        for lvl, tier in enumerate(wall.TIERS):
            name = kind if lvl == 0 else f"{kind}_lvl{lvl}"

            def make(seed: int, kind=kind, lvl=lvl) -> Voxels:
                return wall.compose(kind, lvl, seed=seed)

            out.append(Recipe(name, make, tier.note, militarise=False,
                              kind=kind))
    return out


def yard_recipes(v: Vocabulary) -> List[Recipe]:
    steps = [
        ("training_yard", YardPlan(width=11, depth=9, pells=2, canopy_sides=1,
                                   wall_h=2, battlements=False),
         "walled drill yard, one canopy"),
        ("training_yard_lvl1", YardPlan(width=13, depth=9, pells=3,
                                        canopy_sides=2, wall_h=3),
         "battlements, second canopy"),
        ("training_yard_lvl2", YardPlan(width=13, depth=11, pells=4,
                                        canopy_sides=3, wall_h=3),
         "canopies on three sides"),
    ]
    return [Recipe(n, (lambda p=p: lambda s: compose_yard(v, p, seed=s))(), d)
            for n, p, d in steps]


def stretched_recipes(prefix: str, donors: Sequence[str], along: int,
                      note: str, palisade: bool = False,
                      banners: bool = True,
                      fittings: bool = False) -> List[Recipe]:
    """One output per donor level, each stretched along its own ridge."""
    out: List[Recipe] = []
    for i, rel in enumerate(donors):
        name = prefix if i == 0 else f"{prefix}_lvl{i}"

        def make(seed: int, rel=rel, along=along) -> Voxels:
            d = donor(rel)
            return assemble.variant(d, along=along, seed=seed, jitter=0.4)

        # `banners` gates the prestige fittings AND the lvl4+ stair cap on
        # posts; pass False to suppress banners on a building where they are
        # the wrong signal.
        out.append(Recipe(name, make, f"{note} (from {Path(rel).stem})",
                          palisade=palisade,
                          banners=banners and i >= 4,
                          rich=i >= 4, fittings=fittings))
    return out


def all_recipes(v: Vocabulary) -> List[Recipe]:
    recipes = tower_recipes(v) + fortification_recipes()
    # barracks: house_2 already carries four beds plus a stone base.
    recipes += stretched_recipes(
        "barracks",
        ["plains/houses/house_2.nbt"]
        + [f"plains/houses/house_2_lvl{i}.nbt" for i in range(1, 7)],
        along=3, note="long bunkhouse", palisade=True)
    # armory: house_3 carries the furnace and the heaviest cobble in the corpus.
    # lvl6 is one of the four files corrupted by the .gitattributes bug, so the
    # ladder stops at lvl5.
    recipes += stretched_recipes(
        "armory",
        ["plains/houses/house_3.nbt"]
        + [f"plains/houses/house_3_lvl{i}.nbt" for i in range(1, 6)],
        along=2, note="workshop with forge", palisade=True, banners=False,
        fittings=True)
    # Training yard is composed, not stretched. granary was the closest donor
    # by shape but it is an open barn with a water trough and reads as a farm
    # building however it is dressed.
    recipes += yard_recipes(v)
    return recipes


# ── run ─────────────────────────────────────────────────────────────

@dataclass
class Result:
    recipe: Recipe
    vox: Optional[Voxels] = None
    verdict: Optional[Verdict] = None
    seed: int = 0
    error: str = ""


def build_one(r: Recipe) -> Result:
    """Try seeds until the critic passes; keep the best attempt either way."""
    best: Optional[Tuple[Verdict, Voxels, int]] = None
    for seed in range(SEED_TRIES):
        try:
            vox = r.make(seed)
        except Exception as exc:            # a refused stretch, a bad donor
            return Result(r, error=f"{type(exc).__name__}: {exc}")
        vox.name = r.name
        # Every military build gets the garrison dressing pass: trodden ground,
        # village decoration stripped, banners and stores added.
        if r.militarise:
            vox = militarize(vox, seed=seed, palisade=r.palisade,
                             banners=r.banners)
            vox.name = r.name
        # Richer levels finish their posts with a stair cap, as house_2_lvl6
        # does. Low levels keep the author's rough half-slab cap: that
        # unfinished look is intentional and is part of the progression.
        if r.rich:
            cap_pillars(vox)
        if r.fittings:
            # Shields are the showiest of the three, so they wait for a rich
            # level; the rack and the forge kit read as working equipment and
            # belong from the start.
            military_fittings(vox, seed=seed, shields=r.rich)
        # Foliage last: bushes, donor planting and decor jitter all contribute
        # leaves, so the no-lone-no-floating rule is enforced in one pass.
        tidy_leaves(vox)
        # Donors carry pre-1.20.3 ids; never write one into a 1.21.1 structure.
        modernize(vox)
        v = judge(vox)
        # A piece has to pass both gates. Searching on style alone would happily
        # settle on a seed whose wall walk is blocked.
        broken = usable(vox, r.kind)
        for problem in broken:
            v.findings.append(Finding("fail", "not-usable", problem))
        if best is None or len(v.failures) < len(best[0].failures):
            best = (v, vox, seed)
        if v.ok:
            return Result(r, vox, v, seed)
    assert best is not None
    return Result(r, best[1], best[0], best[2])


def main(argv: Optional[Sequence[str]] = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--dry-run", action="store_true",
                    help="gate everything, write nothing")
    ap.add_argument("--only", help="substring filter on output name")
    ap.add_argument("--no-sheets", action="store_true")
    a = ap.parse_args(argv)

    v = build_vocabulary()
    print(v.describe(), "\n")

    recipes = [r for r in all_recipes(v)
               if not a.only or a.only in r.name]
    results = [build_one(r) for r in recipes]

    written, failed = 0, 0
    for res in results:
        r = res.recipe
        if res.error:
            print(f"  ERROR  {r.name}: {res.error}")
            failed += 1
            continue
        assert res.vox is not None and res.verdict is not None
        tag = "ok  " if res.verdict.ok else "FAIL"
        warns = len(res.verdict.warnings)
        print(f"  {tag}  {r.name:24s} {str(res.vox.size):14s} "
              f"solid={res.vox.solid_count:4d} seed={res.seed:2d} "
              f"warn={warns}  {r.note}")
        if not res.verdict.ok:
            failed += 1
            for f in res.verdict.failures:
                print(f"          {f.code}: {f.message.splitlines()[0]}")
            continue
        if not a.dry_run:
            # One folder per building: thirty files in a flat directory is
            # unreadable once each building has seven levels.
            group = OUT / r.name.split("_lvl")[0]
            group.mkdir(parents=True, exist_ok=True)
            save(res.vox, group / f"{r.name}.nbt")
            written += 1

    print(f"\n{len(results) - failed}/{len(results)} passed the gate, "
          f"{written} written to {OUT}")

    if not a.dry_run and not a.no_sheets:
        SHEETS.mkdir(parents=True, exist_ok=True)
        groups: Dict[str, List[Result]] = {}
        for res in results:
            if res.vox is None or res.verdict is None or not res.verdict.ok:
                continue
            key = res.recipe.name.split("_lvl")[0]
            groups.setdefault(key, []).append(res)
        for key, items in groups.items():
            sheet = render_png.sheet(
                [(r.vox, f"{r.recipe.name}  - {r.recipe.note}") for r in items],
                tile=12)
            p = SHEETS / f"{key}.png"
            sheet.save(p)
            print(f"  rendered {p}")
        print("\nLOOK AT THE SHEETS. The gate cannot judge whether a roof "
              "reads as a roof.")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
