"""Generate the livestock set: cow pasture, pig sty, sheep fold.

Run from tools/:

    python build_livestock.py                # write NBTs + contact sheets
    python build_livestock.py --dry-run      # gate only, write nothing
    python build_livestock.py --only sheep   # substring filter

Three buildings x six levels = eighteen files, written into

    structure/livestock/<building>/<building>[_lvlN].nbt

Two hard gates, both of which have to pass before a file is written:

* `critic.judge` — the style gate: duplicate positions, ids invalid on 1.21.1,
  floating blocks, attachment validity, mirror symmetry above the corpus
  ceiling.
* `pasture.check_pen` — the functional gate. A pen that leaks, a gate you cannot
  walk through, a shelter you cannot get into or a loft you cannot climb is a
  failure even when it renders beautifully. This is the pen's equivalent of
  `traverse.check_route` on the wall set.

The seed is searched until both pass, and anything still failing is reported
rather than written.

Unlike the military set there is no donor to stretch and nothing to harvest: the
corpus has three animal fields and they are 9x9 fenced patches with a puddle in
them, so there is no *shelter* anywhere to reuse. The block states are therefore
built from the same oak/cobblestone vocabulary the author uses, in the
arrangement described in `structures/pasture.py`.
"""

from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional, Sequence

sys.path.insert(0, str(Path(__file__).resolve().parent))

from structures import pasture, render_png
from structures.corpus import modernize
from structures.critic import Verdict, judge
from structures.nbtio import save
from structures.pasture import BREEDS, LADDER, Breed, Pen, Tier, check_pen

CORPUS = Path("../common/src/main/resources/data/onceuponatown/structure")
OUT = CORPUS / "livestock"
SHEETS = Path("structures/out/livestock")
SEED_TRIES = 24


@dataclass
class Result:
    breed: Breed
    tier: Tier
    pen: Optional[Pen] = None
    verdict: Optional[Verdict] = None
    problems: List[str] = None          # functional failures
    seed: int = 0
    error: str = ""

    @property
    def name(self) -> str:
        return (self.breed.key if self.tier.key == "base"
                else f"{self.breed.key}_{self.tier.key}")

    @property
    def ok(self) -> bool:
        return (self.verdict is not None and self.verdict.ok
                and not self.problems and not self.error)


def build_one(breed: Breed, tier: Tier) -> Result:
    """Search seeds until both gates pass; keep the least-bad if none does."""
    best: Optional[Result] = None
    for seed in range(SEED_TRIES):
        try:
            pen = pasture.compose_pen(breed, tier, seed=seed)
        except Exception as exc:                     # noqa: BLE001
            return Result(breed, tier, error=f"{type(exc).__name__}: {exc}")
        vox = pen.vox
        # NOT `cap_pillars` and NOT `tidy_leaves` here. Both are military-set
        # finishing passes and both reach into the **grafted donor**, which is
        # the author's own house and is already finished:
        #
        #   `cap_pillars` re-caps his interior posts — 16 cells changed, some at
        #   roof level inside his own pitch, which `check_fabric` then reported
        #   as a hole in the roof plane. It also overwrote the boundary posts'
        #   slab caps with stairs.
        #   `tidy_leaves` removes leaves with no neighbour, which on a donor
        #   means his **roof planters** — six of them on `house.nbt`.
        #
        # The byre's own posts stand under a beam and a roof, so there is nothing
        # left to cap, and the yard's planting always sits on the ground. It removes leaves with no neighbour, which on a
        # grafted donor means the author's own **roof planters** — six of them on
        # `house.nbt` — and pulling them out left a gap in the middle of his roof
        # plane. `check_fabric` reported it as a hole, and it was. The yard's own
        # planting always sits on the ground, so there is nothing to tidy.
        modernize(vox)
        verdict = judge(vox)
        problems = check_pen(pen)
        res = Result(breed, tier, pen, verdict, problems, seed)
        if res.ok:
            return res
        score = len(verdict.failures) + 2 * len(problems)
        if best is None or score < (len(best.verdict.failures)
                                    + 2 * len(best.problems)):
            best = res
    assert best is not None
    return best


def main(argv: Optional[Sequence[str]] = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--dry-run", action="store_true",
                    help="gate everything, write nothing")
    ap.add_argument("--only", help="substring filter on output name")
    ap.add_argument("--no-sheets", action="store_true")
    a = ap.parse_args(argv)

    jobs = [(b, t) for b in BREEDS for t in LADDER]
    results = [build_one(b, t) for b, t in jobs
               if not a.only or a.only in f"{b.key}_{t.key}"]

    written, failed = 0, 0
    for res in results:
        if res.error:
            print(f"  ERROR  {res.name}: {res.error}")
            failed += 1
            continue
        assert res.pen is not None and res.verdict is not None
        vox = res.pen.vox
        tag = "ok  " if res.ok else "FAIL"
        print(f"  {tag}  {res.name:22s} {str(vox.size):13s} "
              f"solid={vox.solid_count:4d} herd={len(vox.entities)} "
              f"seed={res.seed:2d} warn={len(res.verdict.warnings)}  "
              f"{res.tier.note}")
        if not res.ok:
            failed += 1
            for f in res.verdict.failures:
                print(f"          style: {f.code}: {f.message.splitlines()[0]}")
            for p in res.problems or []:
                print(f"          function: {p}")
            continue
        if not a.dry_run:
            group = OUT / res.breed.key
            group.mkdir(parents=True, exist_ok=True)
            save(vox, group / f"{res.name}.nbt")
            written += 1

    print(f"\n{len(results) - failed}/{len(results)} passed both gates, "
          f"{written} written to {OUT}")

    if not a.dry_run and not a.no_sheets:
        SHEETS.mkdir(parents=True, exist_ok=True)
        groups: Dict[str, List[Result]] = {}
        for res in results:
            if not res.ok:
                continue
            groups.setdefault(res.breed.key, []).append(res)
        for key, items in groups.items():
            sheet = render_png.sheet(
                [(r.pen.vox, f"{r.name}  - {r.tier.note}") for r in items],
                tile=12)
            p = SHEETS / f"{key}.png"
            sheet.save(p)
            print(f"  rendered {p}")
        print("\nLOOK AT THE SHEETS. The gates can tell you a pen holds its "
              "animals; they cannot tell you it looks like a farm.")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
