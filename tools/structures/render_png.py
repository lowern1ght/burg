"""PNG renderer — the piece that lets an agent actually see what it built.

The previous toolchain rendered SVG and HTML. An agent cannot read either, so
every "does this look right?" question had to go to a human, and every fix was
a guess. PNG closes that loop: the agent renders its candidate next to a real
author structure and compares them directly.

Projection is 2:1 isometric. A cell (x, y, z) maps to

    screen_x = (x - z) * HW
    screen_y = (x + z) * HH - y * VH        with HW = 2*HH, VH = 2*HH

so an offset of (1, 1, 1) lands on the same pixel: the view direction is
(1, 1, 1) and the visible faces are +Y (top), +X and +Z. Painter's order is
therefore ascending x+y+z — far blocks first.

CLI:
    python -m structures.render_png <file.nbt> [more.nbt ...] -o sheet.png
    python -m structures.render_png --compare mine.nbt theirs.nbt -o cmp.png
"""

from __future__ import annotations

import argparse
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence, Tuple

from PIL import Image, ImageDraw, ImageFont

from .appearance import colour_of, shape_of
from .nbtio import BlockState, Voxels, load

BG = (24, 26, 30)
INK = (232, 232, 236)
DIM = (150, 152, 158)
GRID = (44, 47, 53)


# ── colour helpers ──────────────────────────────────────────────────

def _rgb(hex_colour: str) -> Tuple[int, int, int]:
    h = hex_colour.lstrip("#")
    if len(h) != 6:
        return (155, 143, 134)
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))


def _scale(c: Tuple[int, int, int], f: float) -> Tuple[int, int, int]:
    return tuple(max(0, min(255, int(v * f))) for v in c)  # type: ignore[return-value]


TOP_F, XSIDE_F, ZSIDE_F, EDGE_F = 1.0, 0.76, 0.58, 0.42


# ── box geometry ────────────────────────────────────────────────────

Box = Tuple[float, float, float, float, float, float]  # x0,x1,y0,y1,z0,z1

POST_IN = 0.32     # how far a fence/pane post is inset from the cell edge
TINY_IN = 0.34
FLAT_H = 0.09
PLATE_H = 0.14
DOOR_T = 0.2


def boxes_for(b: BlockState, x: int, y: int, z: int) -> List[Box]:
    """Sub-cell boxes that approximate this block's real shape."""
    kind, param = shape_of(b)
    X0, X1, Y0, Y1, Z0, Z1 = x, x + 1.0, y, y + 1.0, z, z + 1.0

    if kind == "full":
        return [(X0, X1, Y0, Y1, Z0, Z1)]

    if kind == "slab":
        if param == "top":
            return [(X0, X1, Y0 + 0.5, Y1, Z0, Z1)]
        return [(X0, X1, Y0, Y0 + 0.5, Z0, Z1)]

    if kind == "stairs":
        facing, half = (param.split(":") + ["bottom"])[:2]
        # Lower slab plus a quarter step. The step sits on the side OPPOSITE
        # `facing` — vanilla `facing` names the direction the low side faces.
        if half == "top":
            base = (X0, X1, Y0 + 0.5, Y1, Z0, Z1)
            step_y = (Y0, Y0 + 0.5)
        else:
            base = (X0, X1, Y0, Y0 + 0.5, Z0, Z1)
            step_y = (Y0 + 0.5, Y1)
        sy0, sy1 = step_y
        if facing == "north":      # low side north (-Z) → step at +Z
            step = (X0, X1, sy0, sy1, Z0 + 0.5, Z1)
        elif facing == "south":
            step = (X0, X1, sy0, sy1, Z0, Z0 + 0.5)
        elif facing == "west":     # low side west (-X) → step at +X
            step = (X0 + 0.5, X1, sy0, sy1, Z0, Z1)
        else:                       # east
            step = (X0, X0 + 0.5, sy0, sy1, Z0, Z1)
        return [base, step]

    if kind == "post":
        i = POST_IN
        return [(X0 + i, X1 - i, Y0, Y1, Z0 + i, Z1 - i)]

    if kind == "flat":
        return [(X0, X1, Y0, Y0 + FLAT_H, Z0, Z1)]

    if kind == "plate":
        if param == "top":
            return [(X0, X1, Y1 - PLATE_H, Y1, Z0, Z1)]
        return [(X0, X1, Y0, Y0 + PLATE_H, Z0, Z1)]

    if kind == "door":
        f = param
        if f == "north":
            return [(X0, X1, Y0, Y1, Z0, Z0 + DOOR_T)]
        if f == "south":
            return [(X0, X1, Y0, Y1, Z1 - DOOR_T, Z1)]
        if f == "west":
            return [(X0, X0 + DOOR_T, Y0, Y1, Z0, Z1)]
        return [(X1 - DOOR_T, X1, Y0, Y1, Z0, Z1)]

    if kind == "plant":
        i = 0.22
        return [(X0 + i, X1 - i, Y0, Y0 + 0.8, Z0 + i, Z1 - i)]

    # tiny
    i = TINY_IN
    return [(X0 + i, X1 - i, Y0, Y0 + 0.5, Z0 + i, Z1 - i)]


# ── isometric render ────────────────────────────────────────────────

def render_iso(vox: Voxels, tile: int = 14, outline: bool = True,
               pad: int = 12) -> Image.Image:
    """Isometric view of a structure."""
    hh = max(2, tile // 2)
    hw, vh = hh * 2, hh * 2
    sx, sy, sz = vox.size

    def proj(px: float, py: float, pz: float) -> Tuple[float, float]:
        return ((px - pz) * hw, (px + pz) * hh - py * vh)

    # Extent over every cube corner so nothing clips.
    corners = [proj(a, b, c)
               for a in (0, sx) for b in (0, sy) for c in (0, sz)]
    min_x = min(p[0] for p in corners); max_x = max(p[0] for p in corners)
    min_y = min(p[1] for p in corners); max_y = max(p[1] for p in corners)
    w = int(max_x - min_x) + pad * 2
    h = int(max_y - min_y) + pad * 2
    ox, oy = pad - min_x, pad - min_y

    img = Image.new("RGB", (max(w, 40), max(h, 40)), BG)
    dr = ImageDraw.Draw(img)

    def draw_box(bx: Box, base: Tuple[int, int, int]) -> None:
        x0, x1, y0, y1, z0, z1 = bx
        top = [proj(x0, y1, z0), proj(x1, y1, z0), proj(x1, y1, z1), proj(x0, y1, z1)]
        xs = [proj(x1, y0, z0), proj(x1, y1, z0), proj(x1, y1, z1), proj(x1, y0, z1)]
        zs = [proj(x0, y0, z1), proj(x1, y0, z1), proj(x1, y1, z1), proj(x0, y1, z1)]
        edge = _scale(base, EDGE_F) if outline else None
        for pts, f in ((zs, ZSIDE_F), (xs, XSIDE_F), (top, TOP_F)):
            shifted = [(p[0] + ox, p[1] + oy) for p in pts]
            dr.polygon(shifted, fill=_scale(base, f), outline=edge)

    # far to near
    for (x, y, z), b in sorted(vox.solid_items(), key=lambda kv: sum(kv[0])):
        base = _rgb(colour_of(b))
        for bx in boxes_for(b, x, y, z):
            draw_box(bx, base)

    return img


# ── orthographic renders ────────────────────────────────────────────

VIEWS = ("front", "side", "top")


def render_ortho(vox: Voxels, view: str = "front", px: int = 8,
                 pad: int = 8) -> Image.Image:
    """Flat silhouette view. `front` looks north→south, `side` west→east,
    `top` looks straight down. Best check for roof pitch and wall rhythm."""
    sx, sy, sz = vox.size

    if view == "front":     # X across, Y up; nearest = smallest z
        cols, rows = sx, sy
        def pick(a: int, b: int) -> Optional[BlockState]:
            for z in range(sz):
                blk = vox.get((a, b, z))
                if blk is not None and not blk.is_air:
                    return blk
            return None
    elif view == "side":    # Z across, Y up; nearest = smallest x
        cols, rows = sz, sy
        def pick(a: int, b: int) -> Optional[BlockState]:
            for x in range(sx):
                blk = vox.get((x, b, a))
                if blk is not None and not blk.is_air:
                    return blk
            return None
    else:                   # top: X across, Z down; highest y wins
        cols, rows = sx, sz
        def pick(a: int, b: int) -> Optional[BlockState]:
            for y in range(sy - 1, -1, -1):
                blk = vox.get((a, y, b))
                if blk is not None and not blk.is_air:
                    return blk
            return None

    img = Image.new("RGB", (cols * px + pad * 2, rows * px + pad * 2), BG)
    dr = ImageDraw.Draw(img)
    for a in range(cols):
        for b in range(rows):
            blk = pick(a, b)
            if blk is None:
                continue
            # Y axis points up on screen for front/side; top view uses Z down.
            row = (rows - 1 - b) if view != "top" else b
            x0 = pad + a * px
            y0 = pad + row * px
            dr.rectangle([x0, y0, x0 + px - 1, y0 + px - 1],
                         fill=_rgb(colour_of(blk)))
    return img


# ── composition ─────────────────────────────────────────────────────

def _font(size: int = 13) -> ImageFont.ImageFont:
    try:
        return ImageFont.load_default(size=size)
    except TypeError:      # very old Pillow
        return ImageFont.load_default()


def _text_w(dr: ImageDraw.ImageDraw, s: str, font) -> int:
    return int(dr.textlength(s, font=font))


def stat_line(vox: Voxels) -> str:
    sx, sy, sz = vox.size
    top = vox.top_y()
    waste = sy - 1 - top if top >= 0 else sy
    s = (f"{sx}x{sy}x{sz}  solid={vox.solid_count}  "
         f"dens={vox.density:.2f}  palette={vox.palette_size}  topY={top}")
    if waste > 0:
        s += f"  EMPTY_TOP={waste}"
    return s


def panel(vox: Voxels, label: str, tile: int = 14,
          with_ortho: bool = True) -> Image.Image:
    """One structure: iso view, the three ortho views, label and stats."""
    font = _font(13)
    small = _font(11)

    iso = render_iso(vox, tile=tile)
    parts: List[Image.Image] = [iso]
    if with_ortho:
        parts += [render_ortho(vox, v, px=max(5, tile // 2)) for v in VIEWS]

    gap = 10
    body_w = sum(p.width for p in parts) + gap * (len(parts) - 1)
    body_h = max(p.height for p in parts)
    head_h, caption_h, foot_h = 22, 16, 18

    img = Image.new("RGB", (body_w + 20, head_h + body_h + caption_h + foot_h), BG)
    dr = ImageDraw.Draw(img)
    dr.text((10, 4), label, fill=INK, font=font)

    # Captions all sit on one baseline under the tallest view, so a tall iso
    # render can never collide with them or with the stat line.
    caption_y = head_h + body_h + 1
    names = ("iso",) + VIEWS
    x = 10
    for i, p in enumerate(parts):
        img.paste(p, (x, head_h))
        if i < len(names):
            dr.text((x + 2, caption_y), names[i], fill=DIM, font=small)
        x += p.width + gap

    dr.text((10, caption_y + caption_h), stat_line(vox), fill=DIM, font=small)
    return img


def sheet(items: Sequence[Tuple[Voxels, str]], tile: int = 14,
          with_ortho: bool = True) -> Image.Image:
    """Stack panels vertically into one contact sheet."""
    panels = [panel(v, lbl, tile=tile, with_ortho=with_ortho) for v, lbl in items]
    w = max(p.width for p in panels)
    h = sum(p.height for p in panels) + 8 * (len(panels) - 1)
    img = Image.new("RGB", (w, h), BG)
    dr = ImageDraw.Draw(img)
    y = 0
    for i, p in enumerate(panels):
        img.paste(p, (0, y))
        y += p.height
        if i < len(panels) - 1:
            dr.line([(0, y + 4), (w, y + 4)], fill=GRID)
            y += 8
    return img


def compare(mine: Voxels, reference: Voxels, tile: int = 14,
            labels: Tuple[str, str] = ("CANDIDATE", "AUTHOR REFERENCE")
            ) -> Image.Image:
    """Candidate on top, author reference below — the sheet to actually look at."""
    return sheet([(mine, f"{labels[0]}  ({mine.name})"),
                  (reference, f"{labels[1]}  ({reference.name})")], tile=tile)


# ── CLI ─────────────────────────────────────────────────────────────

def main(argv: Optional[Sequence[str]] = None) -> int:
    ap = argparse.ArgumentParser(description="Render structure NBT to PNG.")
    ap.add_argument("files", nargs="+", help="NBT files to render")
    ap.add_argument("-o", "--out", default="render.png")
    ap.add_argument("--tile", type=int, default=14)
    ap.add_argument("--compare", action="store_true",
                    help="first file is the candidate, second the reference")
    ap.add_argument("--no-ortho", action="store_true")
    a = ap.parse_args(argv)

    voxels = []
    for f in a.files:
        v = load(f)
        v.name = Path(f).name
        voxels.append(v)

    if a.compare:
        if len(voxels) < 2:
            ap.error("--compare needs two files")
        img = compare(voxels[0], voxels[1], tile=a.tile)
    else:
        img = sheet([(v, v.name) for v in voxels], tile=a.tile,
                    with_ortho=not a.no_ortho)

    out = Path(a.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out)
    print(f"wrote {out}  ({img.width}x{img.height})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
