"""One command that re-checks the work and draws the places it complains about.

Run from tools/:

    python selfgate.py                 # the livestock set
    python selfgate.py --set livestock --open

Why it exists: the checks were spread over four commands whose output is numbers,
and a number like `rider at (4, 3, 5)` costs minutes to turn into an understanding
of what is actually there. This runs everything, then renders an **annotated
section** through every fault — the horizontal layer plus both vertical cuts, with
the cell ringed and every block drawn at its real sub-cell shape, so a half-slab
problem is visible rather than inferred.

Output lands in `structures/out/selfgate/`: one `report.md` and one PNG per fault.
Read the report, look at the pictures, fix the cause.
"""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))

import check_fabric
import check_pens
from structures import render_png
from structures.critic import judge
from structures.nbtio import Coord, Voxels, load

LIVESTOCK = Path("../common/src/main/resources/data/burg/structure/livestock")

OUT = Path("structures/out/selfgate")
COORD = re.compile(r"\((-?\d+),\s*(-?\d+),\s*(-?\d+)\)")


# What the author's own 121 files do, per file. A finding **inside** his band is
# reported and drawn but is not a fault: a gate that cries wolf gets ignored, and
# then it is worse than no gate. Numbers come from `check_fabric`'s calibration.
AUTHOR_BAND = {
    "roof-hanging": 0,
    "roof-holed": 2,
    "slab-rider": 0,
    "cantilever": 0,
    "fence-props": 0,      # for generated files: we place every block deliberately
    "fence-gap": 4,
    "escape": 0,
}


@dataclass
class Finding:
    file: str
    source: str          # which checker
    kind: str
    message: str
    at: Optional[Coord]
    picture: Optional[str] = None
    over_band: bool = True


def coord_in(text: str) -> Optional[Coord]:
    m = COORD.search(text)
    if not m:
        return None
    return (int(m.group(1)), int(m.group(2)), int(m.group(3)))


def gather(paths: Sequence[Path]) -> Tuple[List[Finding], Dict[str, Voxels]]:
    """Check the **shipped files**, not a fresh compose.

    The first version recomposed the ladder at seed 0 and reported faults that were
    not in any file: the driver searches seeds, so the shipped set is not the seed-0
    set. What ships is what gets checked.
    """
    findings: List[Finding] = []
    voxels: Dict[str, Voxels] = {}
    for path in paths:
        vox = load(path)
        name = path.stem
        voxels[name] = vox

        for f in judge(vox).failures:
            findings.append(Finding(name, "style", f.code,
                                    f.message.splitlines()[0],
                                    coord_in(f.message)))
        leaks, herd, _seen = check_pens.escapes(vox)
        if leaks:
            findings.append(Finding(name, "function", "escape",
                                    f"{herd} animals can reach the plot edge at "
                                    f"{leaks[:3]}", leaks[0]))
        wrong, _stumps = check_fabric.fence_faults(vox)
        for w in wrong:
            findings.append(Finding(name, "fabric", "fence-props", w, coord_in(w)))
        roof = check_fabric.roof_faults(vox)
        for kind in ("hanging", "holed"):
            for item in roof[kind]:
                findings.append(Finding(name, "fabric", f"roof-{kind}", item,
                                        coord_in(item)))
        for item in check_fabric.slab_faults(vox):
            findings.append(Finding(name, "fabric", "slab-rider", item,
                                    coord_in(item)))
        for item in check_fabric.cantilever_faults(vox):
            findings.append(Finding(name, "fabric", "cantilever", item,
                                    coord_in(item)))
        for item in check_fabric.line_faults(vox)["diagonal"]:
            findings.append(Finding(name, "fabric", "fence-gap", item,
                                    coord_in(item)))
    return findings, voxels


def draw(findings: Sequence[Finding], voxels: Dict[str, Voxels]) -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for i, f in enumerate(findings):
        if f.at is None:
            continue
        img = render_png.section(voxels[f.file], f.at)
        path = OUT / f"{i:02d}_{f.file}_{f.kind}.png"
        img.save(path)
        f.picture = path.name


def write_report(findings: Sequence[Finding], voxels: Dict[str, Voxels]) -> Path:
    OUT.mkdir(parents=True, exist_ok=True)
    path = OUT / "report.md"
    lines = ["# selfgate", "",
             f"{len(voxels)} structures checked, {len(findings)} finding(s).", ""]
    if not findings:
        lines += ["Nothing to look at: every checker is quiet.", "",
                  "That is not the same as the build being good — no gate can tell "
                  "you whether a roof reads as a roof. Look at the contact sheets "
                  "in `../livestock/`."]
    else:
        faults = sum(1 for f in findings if f.over_band)
        lines += [f"{faults} exceed what the author does himself; the rest are "
                  f"inside his measured band and are here to look at, not to fix.",
                  "",
                  "| file | verdict | kind | where | picture |", "|---|---|---|---|---|"]
        for f in sorted(findings, key=lambda f: (not f.over_band, f.file)):
            lines.append(f"| `{f.file}` | "
                         f"{'**fault**' if f.over_band else 'his band'} | {f.kind} | "
                         f"{f.at if f.at else '—'} | "
                         f"{'![]('+f.picture+')' if f.picture else '—'} |")
        lines += ["", "## detail", ""]
        for f in findings:
            lines.append(f"* **{f.file}** — {f.source}/{f.kind}: {f.message}")
            if f.picture:
                lines.append(f"  ![{f.kind}]({f.picture})")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return path


def main(argv: Optional[Sequence[str]] = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--only", help="substring filter on the file name")
    ap.add_argument("files", nargs="*", help="specific NBTs; default: the whole set")
    a = ap.parse_args(argv)

    paths = [Path(f) for f in a.files] or sorted(LIVESTOCK.rglob("*.nbt"))
    if a.only:
        paths = [p for p in paths if a.only in p.stem]
    findings, voxels = gather(paths)
    # A kind is a fault only where it exceeds what he does himself, counted per file.
    per_file: Dict[Tuple[str, str], int] = {}
    for f in findings:
        per_file[(f.file, f.kind)] = per_file.get((f.file, f.kind), 0) + 1
    for f in findings:
        allowed = AUTHOR_BAND.get(f.kind, 0)
        f.over_band = per_file[(f.file, f.kind)] > allowed
    draw(findings, voxels)
    report = write_report(findings, voxels)

    faults = [f for f in findings if f.over_band]
    print(f"  {len(voxels)} structures: {len(faults)} fault(s), "
          f"{len(findings) - len(faults)} finding(s) inside his own band")
    for f in sorted(findings, key=lambda f: (not f.over_band, f.file))[:10]:
        print(f"    {'FAULT   ' if f.over_band else 'his band'} {f.file:24s} "
              f"{f.kind:14s} {f.at}"
              + (f"  -> {f.picture}" if f.picture else ""))
    print(f"\n  report: {report}")
    if not faults:
        print("  no faults — and the pictures are still the only thing that can "
              "tell you whether it looks right.")
    return 1 if faults else 0


if __name__ == "__main__":
    raise SystemExit(main())
