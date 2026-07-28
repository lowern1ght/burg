"""StyleKit — one entry point for the generate → gate → look loop.

    python -m structures.stylekit profile
        Measure the author's corpus. Run this first; every threshold the gate
        applies comes from here.

    python -m structures.stylekit inspect <nbt> [-o png]
        Zone split, per-layer numbers and a render. Use it to check the
        anatomy detector before trusting a donor.

    python -m structures.stylekit variant <donor> --along N [--seed S]
        Derive a variant, gate it, and render it beside the donor.

    python -m structures.stylekit gate <nbt>...
        Gate existing files.

The loop only closes if you actually open the PNG. The gate catches broken
NBT and out-of-band statistics; it does not and cannot judge whether a roof
reads as a roof.
"""

from __future__ import annotations

import argparse
from pathlib import Path
from typing import List, Optional, Sequence

from . import anatomy, assemble, corpus, critic, render_png
from .nbtio import CORPUS_ROOT, load, save

# Anchored to this package, not the working directory, so the CLI writes to the
# same place whether it is run from the repo root or from tools/.
DEFAULT_OUT = Path(__file__).resolve().parent / "out" / "stylekit"


def cmd_profile(a: argparse.Namespace) -> int:
    return corpus.main(["--root", a.root] + (["--json", a.json] if a.json else [])
                       + (["--all"] if a.all else []))


def cmd_inspect(a: argparse.Namespace) -> int:
    vox = load(a.nbt)
    vox.name = Path(a.nbt).name
    ana = anatomy.analyse(vox)
    print(anatomy.layer_table(vox, ana))
    print(f"\nridge axis: {assemble.ridge_axis(vox, ana)}")
    print(f"roof profile: {anatomy.roof_profile(vox, ana).describe()}")
    cols = anatomy.wall_columns(vox, ana)
    roles: dict = {}
    for c in cols.values():
        roles[c.role] = roles.get(c.role, 0) + 1
    print(f"wall columns: {len(cols)} {roles}")
    out = a.out or str(DEFAULT_OUT / f"inspect_{Path(a.nbt).stem}.png")
    img = render_png.sheet([(vox, vox.name)], tile=a.tile)
    Path(out).parent.mkdir(parents=True, exist_ok=True)
    img.save(out)
    print(f"\nrendered {out} — open it before trusting the zone split")
    return 0


def cmd_variant(a: argparse.Namespace) -> int:
    donor = load(a.donor)
    donor.name = Path(a.donor).stem
    ana = anatomy.analyse(donor)
    ridge = assemble.ridge_axis(donor, ana)
    print(f"donor {donor.name}: {ana.describe()}")
    print(f"  ridge={ridge}  roof={anatomy.roof_profile(donor, ana).describe()}")

    outdir = Path(a.outdir)
    outdir.mkdir(parents=True, exist_ok=True)
    made: List[Path] = []
    for seed in range(a.count):
        v = assemble.variant(donor, along=a.along, seed=a.seed + seed,
                             jitter=a.jitter)
        p = outdir / f"{v.name}.nbt"
        save(v, p)
        made.append(p)
        print(f"  wrote {p}  size={v.size} solid={v.solid_count}")

    print("\n--- gate ---")
    failed = 0
    for p in made:
        verdict = critic.judge_file(p)
        print(verdict.report())
        print()
        failed += 0 if verdict.ok else 1

    png = outdir / f"{donor.name}_variants.png"
    items = [(donor, f"DONOR {donor.name}")]
    for p in made:
        v = load(p)
        v.name = p.name
        items.append((v, f"VARIANT {p.name}"))
    render_png.sheet(items, tile=a.tile).save(png)
    print(f"rendered {png}")
    print("LOOK AT IT. The gate cannot tell a pitched roof from a pancake.")
    return 1 if failed else 0


def cmd_gate(a: argparse.Namespace) -> int:
    return critic.main(a.files + (["--json", a.json] if a.json else []))


def main(argv: Optional[Sequence[str]] = None) -> int:
    ap = argparse.ArgumentParser(prog="stylekit", description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)

    p = sub.add_parser("profile", help="measure the author's corpus")
    p.add_argument("--root", default=CORPUS_ROOT)
    p.add_argument("--json")
    p.add_argument("--all", action="store_true")
    p.set_defaults(fn=cmd_profile)

    p = sub.add_parser("inspect", help="zone split + layer table + render")
    p.add_argument("nbt")
    p.add_argument("-o", "--out")
    p.add_argument("--tile", type=int, default=14)
    p.set_defaults(fn=cmd_inspect)

    p = sub.add_parser("variant", help="derive variants from a donor, then gate")
    p.add_argument("donor")
    p.add_argument("--along", type=int, default=0,
                   help="blocks to add/remove along the ridge axis")
    p.add_argument("--seed", type=int, default=0)
    p.add_argument("--count", type=int, default=1)
    p.add_argument("--jitter", type=float, default=0.35)
    p.add_argument("--outdir", default=str(DEFAULT_OUT))
    p.add_argument("--tile", type=int, default=14)
    p.set_defaults(fn=cmd_variant)

    p = sub.add_parser("gate", help="gate existing NBT files")
    p.add_argument("files", nargs="+")
    p.add_argument("--json")
    p.set_defaults(fn=cmd_gate)

    a = ap.parse_args(argv)
    return int(a.fn(a))


if __name__ == "__main__":
    raise SystemExit(main())
