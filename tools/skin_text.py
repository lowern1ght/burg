"""Read a skin as TEXT, so an agent can actually see one.

`draw_citizens.py` authors a body *from* text. This is the missing return trip, and it exists
because of a plain fact about the people doing the work: **an agent cannot see a PNG.** Every
skin review in this project's history has gone through a human looking at a contact sheet, which
means an agent could draw a face, pass every numeric gate, and still ship something flat — and
did, twice. A colour count cannot see a repaint; a luminance total cannot see that eight
differing cells differ by an invisible amount. A reader can see both at a glance.

What this prints, and why each part is here:

* **One box face at a time**, not the raw 64x64 sheet. A sheet dump is unreadable; `head/front`
  with the eyes in it is a thing you can check against the rule "eyes on row 4".
* **A legend built from the file's own colours**, ordered darkest to lightest with hex and count.
  So `a` is always the deepest tone in THAT file and the reader never has to guess a palette.
* **`.` for transparent**, which is what makes a hair painting legible at all: its whole content
  is the alpha shape, and as text the silhouette simply reads.
* **The measured statistics beside the picture** — distinct colours, which regions are painted,
  and the luminance spread WITHIN each row of the face — so the dump IS the report and nobody
  has to run three tools to review one file. Deliberately not a "brow contrast" figure: the
  first version measured assumed cells and reported 6.5 on a face whose contrast is in the
  fifties, because that file draws its dark cells one column further in. A number derived from a
  guess about layout is worse than no number.
* **Composites**, layered the way the game layers them, so what an agent reviews is what a player
  sees rather than one file in isolation.

Usage:

    python skin_text.py citizen_body_00.png                  # every sampled face
    python skin_text.py citizen_body_00.png --face head       # one part
    python skin_text.py citizen_hair_02.png --face hat        # a covering's silhouette
    python skin_text.py --composite citizen_body_00.png farmer_clothes.png citizen_hair_00.png
    python skin_text.py --diff citizen_body_00.png citizen_body_08.png
"""

import argparse
import sys
from collections import Counter
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from npc_uv import PLAYER_BOXES, faces  # noqa: E402

TEX = Path(__file__).resolve().parent.parent / (
    "common/src/main/resources/assets/onceuponatown/textures/entity/npc")

# Enough symbols to letter a dense body without repeating. Ordered so the darkest tone gets the
# first symbol, which makes two files comparable by eye: `a` is the shadow in both.
SYMBOLS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

# Faces worth printing, in reading order. `bottom` is left out by default — it is the sole of a
# foot or the underside of a jaw and nothing is ever judged on it.
FACE_ORDER = ["front", "right", "left", "back", "top"]


def load(name):
    path = TEX / name if not Path(name).exists() else Path(name)
    if not path.exists():
        raise SystemExit(f"! no such texture: {name}")
    return Image.open(path).convert("RGBA")


def lum(rgb):
    r, g, b = rgb[:3]
    return 0.299 * r + 0.587 * g + 0.114 * b


def legend_for(images_and_regions):
    """One legend across everything being printed, so a symbol means the same thing throughout.

    Built per dump rather than fixed, because a fixed palette would either be enormous or wrong;
    and sorted by luminance so the symbols themselves carry the ramp.
    """
    counts = Counter()
    for im, regions in images_and_regions:
        px = im.load()
        for (x, y, w, h) in regions:
            for yy in range(y, y + h):
                for xx in range(x, x + w):
                    r, g, b, a = px[xx, yy]
                    if a:
                        counts[(r, g, b)] += 1
    ordered = sorted(counts, key=lum)
    if len(ordered) > len(SYMBOLS):
        print(f"# NOTE {len(ordered)} distinct colours and only {len(SYMBOLS)} symbols;"
              f" the {len(ordered) - len(SYMBOLS)} rarest share the last symbol.")
    mapping = {}
    for i, rgb in enumerate(ordered):
        mapping[rgb] = SYMBOLS[min(i, len(SYMBOLS) - 1)]
    return mapping, counts, ordered


def grid(im, rect, mapping):
    x, y, w, h = rect
    px = im.load()
    rows = []
    for yy in range(y, y + h):
        row = []
        for xx in range(x, x + w):
            r, g, b, a = px[xx, yy]
            row.append("." if a == 0 else mapping.get((r, g, b), "?"))
        rows.append("".join(row))
    return rows


def annotate(part, face, rows):
    """Mark the rows a rule talks about, so a reader checks instead of counting.

    Only the head front has rules this specific, and they are the ones that were got wrong:
    eyes on row 4, mouth on 6 or 7, row 5 plain in vanilla's nine but a LIT nose bridge in 17 of
    the owner's 31 references.
    """
    if not (part == "head" and face == "front"):
        return ["    " + r for r in rows]
    notes = {3: "brow", 4: "EYES  sclera cols 1,6 / iris 2,5",
             5: "nose  bridge should be the LIGHTER half", 6: "mouth", 7: "mouth (alt)"}
    out = []
    for i, r in enumerate(rows):
        tag = notes.get(i, "")
        out.append(f"{i:>2}  {r}   {tag}" if tag else f"{i:>2}  {r}")
    return out


def stats(im, name):
    px = im.load()
    lines = []
    colours = Counter()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a:
                colours[(r, g, b)] += 1
    lines.append(f"distinct colours : {len(colours)}"
                 f"   (references 139 median; a flat generated body was 17)")

    filled = []
    for part, box in PLAYER_BOXES.items():
        u, v, w, h, d = box
        total = used = 0
        for rect in faces(u, v, w, h, d).values():
            x, y, fw, fh = rect
            for yy in range(y, y + fh):
                for xx in range(x, x + fw):
                    total += 1
                    if px[xx, yy][3]:
                        used += 1
        if used:
            filled.append(f"{part} {used}/{total}")
    lines.append("regions painted  : " + (", ".join(filled) if filled else "NONE"))

    # Per-row spread on the head front, and NOT a "brow vs cheek" figure.
    #
    # The first version of this measured assumed cells -- cols 2..5 as the brow against 0 and 7
    # as the cheek -- and reported 6.5 on a body whose brow contrast is genuinely in the fifties,
    # because THIS file puts its dark cells at cols 2 and 5. A number derived from a guess about
    # layout is worse than no number: that is the `--check` lesson, which reported 126-304
    # phantom faults for exactly this reason.
    #
    # So: the spread within each row, which is layout-independent. It cannot name the brow, but
    # it answers the question that matters -- does this row have any modelling in it at all --
    # and 7 is this repo's measured invisibility threshold.
    hx, hy = PLAYER_BOXES["head"][0], PLAYER_BOXES["head"][1]
    fx, fy = hx + 8, hy + 8
    lines.append("head front, spread within each row (7 = invisible):")
    for row in range(8):
        vals = []
        for c in range(8):
            r, g, b, a = px[fx + c, fy + row]
            if a:
                vals.append(lum((r, g, b)))
        if not vals:
            lines.append(f"  row {row}  --")
            continue
        spread = max(vals) - min(vals)
        flag = "  <- flat" if spread < 7 else ""
        lines.append(f"  row {row}  {spread:5.1f}{flag}")
    return lines


def dump(name, only_face=None, show_stats=True):
    im = load(name)
    parts = [p for p in PLAYER_BOXES if not only_face or p == only_face]
    regions = []
    for p in parts:
        u, v, w, h, d = PLAYER_BOXES[p]
        regions += list(faces(u, v, w, h, d).values())
    mapping, counts, ordered = legend_for([(im, regions)])

    print(f"=== {name} ===")
    if show_stats:
        for line in stats(im, name):
            print(line)
    print()
    print("legend, darkest first:")
    for rgb in ordered:
        print(f"  {mapping[rgb]} #{rgb[0]:02x}{rgb[1]:02x}{rgb[2]:02x}"
              f"  lum {lum(rgb):5.1f}  x{counts[rgb]}")
    print("  . transparent")
    print()

    for p in parts:
        u, v, w, h, d = PLAYER_BOXES[p]
        fs = faces(u, v, w, h, d)
        printed = False
        for f in FACE_ORDER:
            if f not in fs:
                continue
            rows = grid(im, fs[f], mapping)
            if all(set(r) == {"."} for r in rows):
                continue                      # an empty face says nothing; skip it
            if not printed:
                print(f"-- {p} --")
                printed = True
            print(f"  [{f}]")
            for line in annotate(p, f, rows):
                print("  " + line)
        if printed:
            print()


def composite(names):
    """Layer them the way the game does, then print the result.

    A body is reviewed in isolation far too easily; what a player sees is the base plus a trade's
    tunic plus a covering, and the interesting faults live where those meet.
    """
    ims = [load(n) for n in names]
    out = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for im in ims:
        out.alpha_composite(im)
    tmp = Path(__file__).resolve().parent / "_composite.png"
    out.save(tmp)
    print(f"# composite of: {', '.join(names)}")
    print("# NOTE tints are NOT applied -- the game multiplies each layer by its own ARGB.")
    try:
        dump(str(tmp))
    finally:
        tmp.unlink(missing_ok=True)


def diff(a, b):
    """Where two skins differ, symbolically.

    The question a reviewer actually has is "is this a new person or a repaint", and a pixel
    diff answers the wrong one: recolouring every pixel scores 100% different while being the
    same drawing. So this reports both -- cells that differ at all, and cells whose SYMBOL
    differs once each file is lettered by its own ramp.
    """
    ia, ib = load(a), load(b)
    pa, pb = ia.load(), ib.load()
    ma, _, _ = legend_for([(ia, [(0, 0, 64, 64)])])
    mb, _, _ = legend_for([(ib, [(0, 0, 64, 64)])])

    for part, box in PLAYER_BOXES.items():
        u, v, w, h, d = box
        cells = raw = sym = 0
        for rect in faces(u, v, w, h, d).values():
            x, y, fw, fh = rect
            for yy in range(y, y + fh):
                for xx in range(x, x + fw):
                    ca, cb = pa[xx, yy], pb[xx, yy]
                    if ca[3] == 0 and cb[3] == 0:
                        continue
                    cells += 1
                    if ca != cb:
                        raw += 1
                    sa = ma.get(ca[:3], ".") if ca[3] else "."
                    sb = mb.get(cb[:3], ".") if cb[3] else "."
                    if sa != sb:
                        sym += 1
        if cells:
            print(f"{part:14s} {raw*100//cells:3d}% pixels differ, "
                  f"{sym*100//cells:3d}% SHAPE differs   ({cells} cells)")
    print()
    print("# SHAPE is the honest number. A repaint of one drawing scores near 0 there while")
    print("# scoring 100 on pixels; the roster's floor for two different people is 35%.")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("names", nargs="*")
    ap.add_argument("--face", help="one part only, e.g. head or hat")
    ap.add_argument("--composite", action="store_true", help="layer the given files first")
    ap.add_argument("--diff", action="store_true", help="compare two files")
    ap.add_argument("--no-stats", action="store_true")
    args = ap.parse_args()

    if args.diff:
        if len(args.names) != 2:
            raise SystemExit("! --diff takes exactly two files")
        diff(*args.names)
    elif args.composite:
        if not args.names:
            raise SystemExit("! --composite needs at least one file")
        composite(args.names)
    elif args.names:
        for n in args.names:
            dump(n, args.face, not args.no_stats)
    else:
        raise SystemExit(__doc__)
