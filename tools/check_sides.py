"""Is one side of the figure bare? A face painted while its opposite number is blank.

    python check_sides.py                    # every npc texture the mod ships
    python check_sides.py --calibrate        # the author's own skin and the 31 references first
    python check_sides.py citizen_hair_00.png -v      # one file, every pair printed

THE FAULT THIS EXISTS TO CATCH, and it is the SECOND time this shape of it has shipped:

* `left_arm` and `left_leg` were empty in every texture the mod shipped for a whole revision. The
  retired villager mesh mirrored its right limbs; the player mesh does not — `l_arm` carries its own
  `texOffs(32, 48)` and `l_leg` its own `(16, 48)` — so the symmetry has to be put into the TEXTURE.
  `remap_npc_uv.py` and `npc_uv.MIRROR_SWAP` both exist because of that.
* Then the same mistake landed on the head. Measured on the thirteen shipped coverings: every one of
  them paints `right` and leaves `left` at **exactly zero**.

      hat net:  top (40,0)  bottom (48,0)  right (32,8)  front (40,8)  left (48,8)  back (56,8)

      file                     top  bottom  right  front   left   back
      citizen_hair_00           64      0      29     20      0     38
      citizen_hair_02           64      0      63     33      0     64
      citizen_headwear_03       64     24      64     42      0     64
      citizen_beard_03           0     64      25     20      0      0

  In game that is hair missing down one side of a citizen's head, which is how the owner found it.

WHY NO EXISTING GATE COULD SEE IT. `verify_head` checked that nothing is painted OUTSIDE the `hat`
net and that the face window is left alone; both pass a file with a blank face. `check_wrap` walks
right, front, left, back looking for a step at the seams and returns `None` for a column with no
opaque texels, so a whole blank face is simply skipped. `make_npc_textures --check` looks for paint
nobody can see, which is the opposite question. A blank face is invisible in every one of them.

WHAT IS GATED, AND WHAT IS ONLY REPORTED — calibrated on `default_skin.png` and the 31 references
first, because a metric that fires on the corpus is wrong. The first version of this file gated
"either pair, any box" and reported **10 of the 32 as faulty**. Two things came out of the numbers
and both changed the rule:

    pair, over every box that paints either      one-sided
    right / left                                 23 of 338      <- but see below
    front / back                                 12 of 360      <- reported only
    top   / bottom                               67 of 293      <- reported only

    right / left, per box                        one-sided
    head 0/32   hat 0/25   body 0/32                            <- GATED
    r_arm 0/32  l_arm 0/32  r_leg 0/32  l_leg 0/32              <- GATED
    body_outer 1/19  r_arm_outer 3/27  l_arm_outer 4/27
    r_leg_outer 8/24  l_leg_outer 7/24                          <- reported only

**Not one of the 32 leaves a side face of a BASE cube bare, and 23 do it on the second layer.** Which
is the right answer twice over: on the head and the torso the two side faces are the figure's own two
sides, and nobody has hair on one temple; on a limb they are that limb's outer and inner flank, and
the inner flank of an arm is against the body where nothing shows. The second layer is a jacket, a
sleeve or a legging, and a skin that paints the outside of a sleeve and not the inside is drawing what
can be seen.

The one-sided front/back and top/bottom pairs are legitimate for the same reason and ours use them:
paint on the front of a head has no back, a beard has an underside and no top, a hat has a top and no
underside. So those are printed rather than gated, and the print is why.

ALSO MEASURED, and it is what stops the fix from being "copy `right` onto `left`": where both sides
ARE painted, the mirrored cells disagree by a median of 0 on head, both arms and both legs — exactly
zero on all 32 files — and by 2 on the hat, which is hand-drawn hair. The corpus mirrors the base
cubes and does not mirror its second layer. `draw_citizens.verify_head` therefore holds OUR coverings
to the stricter standard, an exact alpha mirror, because ours are generated from one; this file only
reports the number, because a median of 2 on the corpus means an exact rule would fire on it.

The gate is also inside `draw_citizens.verify_head`, at write time. This file is the sweep: it reads
what is on disk, over every box of every texture, so it also covers the garments, `default_skin.png`
and the retired sets that no generator will ever run over again.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Dict, List, Sequence, Tuple

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from npc_uv import PLAYER_BOXES as BOXES, faces as net  # noqa: E402

TEX = Path(__file__).resolve().parent.parent / (
    "common/src/main/resources/assets/burg/textures/entity/npc")
REFS = Path.home() / "Downloads/house.mrs/skins"

# The three opposite pairs of a box net. Only the sides are gated, and only on a base cube; see the
# header for the per-box calibration that decided it.
PAIRS = (("right", "left"), ("front", "back"), ("top", "bottom"))
SIDES = ("right", "left")
BASE_BOXES = ("head", "hat", "body", "r_arm", "l_arm", "r_leg", "l_leg")

# What the corpus scores, so a number above it is ours and not the metric's. From `--calibrate`:
# zero, on the mod's own hand-drawn skin and on all 31 references.
CORPUS_RESIDUAL = 0


def is_gated(box: str, a: str, b: str) -> bool:
    return (a, b) == SIDES and box in BASE_BOXES

# A texel counts as painted at the same threshold every other tool in `tools/` uses.
OPAQUE = 8


def cover(px, rect) -> int:
    x, y, w, h = rect
    return sum(1 for yy in range(y, y + h) for xx in range(x, x + w) if px[xx, yy][3] > OPAQUE)


def mismatched_cells(px, a_rect, b_rect) -> int:
    """Cells where one side is painted and the mirrored cell on the other is not.

    Mirrored, not aligned: the four upright faces unwrap right, front, left, back and that strip is
    the box ROLLED, so `right` col 7 is against `front` col 0 while `left` col 0 is against `front`
    col 7. The front edge of one side face is therefore col 7 and of the other col 0, and comparing
    them column for column would call a perfectly good mirror a fault.
    """
    ax, ay, w, h = a_rect
    bx, by, bw, _ = b_rect
    if bw != w:
        return 0
    return sum(1 for cy in range(h) for cx in range(w)
               if (px[ax + cx, ay + cy][3] > OPAQUE)
               != (px[bx + (w - 1 - cx), by + cy][3] > OPAQUE))


def measure(im: Image.Image) -> List[dict]:
    px = im.load()
    out = []
    for box, dims in BOXES.items():
        fs = net(*dims)
        for a, b in PAIRS:
            ca, cb = cover(px, fs[a]), cover(px, fs[b])
            if not ca and not cb:
                continue
            out.append(dict(box=box, a=a, b=b, ca=ca, cb=cb,
                            gated=is_gated(box, a, b),
                            blank=(ca == 0) != (cb == 0),
                            cells=mismatched_cells(px, fs[a], fs[b]) if (a, b) == SIDES else 0))
    return out


def faults(im: Image.Image) -> List[str]:
    bad = []
    for r in measure(im):
        if not r["gated"] or not r["blank"]:
            continue
        bad.append(f"{r['box']}: `{r['a']}` paints {r['ca']} texels and `{r['b']}` {r['cb']} — one "
                   f"SIDE of the figure is bare. Not one of the 32 calibration files leaves a side "
                   f"face of a base cube blank, and the mod already shipped an empty left arm and "
                   f"left leg for a whole revision")
    return bad


def report(path: Path, verbose: bool = False) -> List[str]:
    im = Image.open(path).convert("RGBA")
    if im.size != (64, 64):
        return []
    bad = faults(im)
    print(f"{path.name:30s} {'BARE SIDE  ' + str(len(bad)) if bad else 'clean'}")
    if verbose:
        for r in measure(im):
            mark = "  <- GATED" if r["gated"] else "  (reported only)"
            if r["blank"]:
                mark = "  <- ONE SIDE BLANK" if r["gated"] else "  one-sided, allowed"
            extra = f", {r['cells']} mirrored cells disagree" if r["cells"] else ""
            print(f"     {r['box']:12s} {r['a']:6s} {r['ca']:3d} / {r['b']:6s} {r['cb']:3d}"
                  f"{extra}{mark}")
    for b in bad:
        print(f"    ! {b}")
    return bad


def shape_summary(paths: Sequence[Path]) -> Tuple[Dict[Tuple[str, str], List[int]],
                                                  Dict[str, List[int]],
                                                  Dict[str, List[int]]]:
    """How often each pair is one-sided across a pool, which is what decides who is gated.

    Three tables, because the first version of this gate was calibrated on the first one alone and
    was wrong: per pair, per box for the sides, and — where both sides are painted — how far the two
    are from being each other's mirror.
    """
    tally: Dict[Tuple[str, str], List[int]] = {p: [0, 0] for p in PAIRS}
    per_box: Dict[str, List[int]] = {b: [0, 0] for b in BOXES}
    mirror: Dict[str, List[int]] = {b: [] for b in BOXES}
    for p in paths:
        im = Image.open(p).convert("RGBA")
        if im.size != (64, 64):
            continue
        for r in measure(im):
            key = (r["a"], r["b"])
            tally[key][1] += 1
            if r["blank"]:
                tally[key][0] += 1
            if key == SIDES:
                per_box[r["box"]][1] += 1
                if r["blank"]:
                    per_box[r["box"]][0] += 1
                else:
                    mirror[r["box"]].append(r["cells"])
    return tally, per_box, mirror


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("names", nargs="*")
    ap.add_argument("--calibrate", action="store_true",
                    help="the author's own skin and the 31 references first")
    ap.add_argument("-v", "--verbose", action="store_true")
    args = ap.parse_args()

    if args.calibrate:
        pool = [TEX / "default_skin.png"]
        if REFS.is_dir():
            pool += sorted(REFS.glob("*.png"))
        else:
            print(f"# no references at {REFS} — calibrating on default_skin.png alone")
        hits = 0
        for p in pool:
            if report(p, args.verbose):
                hits += 1
        tally, per_box, mirror = shape_summary(pool)
        print()
        for (a, b), (one, seen) in tally.items():
            print(f"  {a:6s} / {b:6s}   one-sided in {one:3d} of the {seen:3d} boxes that paint "
                  f"either   {'sides — see per box below' if (a, b) == SIDES else 'reported only'}")
        print()
        print("  right / left, per box — this is the table the rule is built on:")
        for b in BOXES:
            one, seen = per_box[b]
            if not seen:
                continue
            m = sorted(mirror[b])
            med = m[len(m) // 2] if m else None
            print(f"    {b:14s} one-sided {one:3d}/{seen:3d}   "
                  f"{'GATED' if is_gated(b, *SIDES) else 'reported only':14s}"
                  + (f"  mirrored cells disagree: median {med}, max {max(m)} over {len(m)}"
                     if m else ""))
        print()
        print(f"{hits} of {len(pool)} calibration files carry a bare side; CORPUS_RESIDUAL is "
              f"{CORPUS_RESIDUAL}. Anything above that on our own output is ours.")
        return 0

    names = args.names or sorted(p.name for p in TEX.glob("*.png"))
    total = 0
    for n in names:
        p = Path(n) if Path(n).exists() else TEX / n
        total += len(report(p, args.verbose or bool(args.names)))
    print()
    print(f"{total} bare-side faults over {len(names)} files")
    return 1 if total > CORPUS_RESIDUAL else 0


if __name__ == "__main__":
    raise SystemExit(main())
