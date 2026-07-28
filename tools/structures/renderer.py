"""Isometric SVG renderer for structures.

Takes a StructureBuilder / Structure and produces an SVG string showing
the building in 3D-isometric projection. Used by the demo pack to give
a visual preview without running Minecraft.

Coordinate system:
    X: east (right on screen)
    Y: up (vertical on screen)
    Z: south (forward / down-right on screen)

Isometric projection:
    screen_x = (x - z) * TILE_W
    screen_y = (x + z) * TILE_H - y * TILE_H_DEPTH

Blocks are drawn back-to-front, with depth-sort by (x + z + y) descending
so foreground blocks correctly occlude background ones. Each block is a
3D-looking cube (top face + 2 side faces) using the registry's color palette.
"""

from __future__ import annotations

import io
from typing import Iterable, List, Set, Tuple

from .builder import Coord, Structure, StructureBuilder, PlacedBlock
from .registry import color_for


TILE_W = 20      # horizontal half-width of a tile (px)
TILE_H = 10      # vertical half-height of a tile (px)
BLOCK_DEPTH = 20 # vertical pixels per Y level (height of a block on screen)


def project(x: int, y: int, z: int) -> Tuple[float, float]:
    """Convert local 3D coords to isometric 2D screen coords."""
    sx = (x - z) * TILE_W
    sy = (x + z) * TILE_H - y * BLOCK_DEPTH
    return sx, sy


def _cube_polys(x: int, y: int, z: int, color: str,
                block_positions: Set[Coord]) -> List[Tuple[str, str, List[Tuple[float, float]]]]:
    """Return (face_name, color, points) for visible faces only.

    Top (Y+): skip if block at (x, y+1, z) exists.
    East (X+): skip if block at (x+1, y, z) exists.
    South (Z+): skip if block at (x, y, z+1) exists.
    Bottom (Y-) and west/north faces never drawn — not affected.
    """
    base = color
    # Slight darken for side faces (visual depth)
    def darken(hex_color: str, factor: float = 0.75) -> str:
        if not hex_color.startswith("#") or len(hex_color) != 7:
            return hex_color
        r = int(hex_color[1:3], 16)
        g = int(hex_color[3:5], 16)
        b = int(hex_color[5:7], 16)
        r = max(0, min(255, int(r * factor)))
        g = max(0, min(255, int(g * factor)))
        b = max(0, min(255, int(b * factor)))
        return f"#{r:02x}{g:02x}{b:02x}"

    side_color = darken(base, 0.75)
    side_color2 = darken(base, 0.55)

    # Top face — quad at y+1
    p_top = [
        project(x,     y + 1, z    ),
        project(x + 1, y + 1, z    ),
        project(x + 1, y + 1, z + 1),
        project(x,     y + 1, z + 1),
    ]
    # East face (x+1)
    p_east = [
        project(x + 1, y    , z    ),
        project(x + 1, y + 1, z    ),
        project(x + 1, y + 1, z + 1),
        project(x + 1, y    , z + 1),
    ]
    # South face (z+1)
    p_south = [
        project(x,     y    , z + 1),
        project(x + 1, y    , z + 1),
        project(x + 1, y + 1, z + 1),
        project(x,     y + 1, z + 1),
    ]
    faces = [
        ("top",   base,        p_top),
        ("east",  side_color,  p_east),
        ("south", side_color2, p_south),
    ]
    return [
        (name, face_color, points) for (name, face_color, points) in faces
        if not (name == "top" and (x, y + 1, z) in block_positions)
        and not (name == "east" and (x + 1, y, z) in block_positions)
        and not (name == "south" and (x, y, z + 1) in block_positions)
    ]


def render_svg(structure: Structure | StructureBuilder,
               title: str = "structure",
               bg: str = "#1a1a1a",
               show_legend: bool = True) -> str:
    """Return an SVG string rendering the structure in isometric view."""
    # Capture the palette BEFORE converting to Structure — Structure doesn't carry
    # _palette_list, so converting first leaves us unable to map palette_index
    # → block name → color (falling through to the gray default).
    palette_lookup: List[str] = []
    if isinstance(structure, StructureBuilder):
        palette_lookup = [p.name for p in structure._palette_list]
        structure = structure.structure()

    blocks = structure.blocks

    # Skip air blocks for cleaner previews (air is at palette index 0 by convention)
    blocks = [b for b in blocks if b.palette_index != 0]

    # Depth sort: blocks further back (smaller x+z) drawn first; higher y on top.
    # Composite key: primary = x + z (back to front), secondary = -y (top of stack first).
    blocks_sorted = sorted(blocks, key=lambda b: (b.x + b.z, -b.y))
    block_positions: Set[Coord] = {(b.x, b.y, b.z) for b in blocks_sorted}

    # Compute bounds in screen space
    margin = 40
    if blocks:
        xs, ys, zs = [b.x for b in blocks], [b.y for b in blocks], [b.z for b in blocks]
        # Use full structure bounds for layout, not just block bounds
        sx_size, sy_size, sz_size = structure.size
        all_pts: List[Tuple[float, float]] = []
        for x in range(sx_size):
            for y in range(sy_size):
                for z in range(sz_size):
                    all_pts.append(project(x, y, z))
                    all_pts.append(project(x + 1, y + 1, z + 1))
        min_x = min(p[0] for p in all_pts)
        max_x = max(p[0] for p in all_pts)
        min_y = min(p[1] for p in all_pts)
        max_y = max(p[1] for p in all_pts)
    else:
        min_x = max_x = min_y = max_y = 0
        sx_size, sy_size, sz_size = 1, 1, 1

    width = int(max_x - min_x + 2 * margin)
    height = int(max_y - min_y + 2 * margin + (40 if show_legend else 0))
    offset_x = -min_x + margin
    offset_y = -min_y + margin

    svg = io.StringIO()
    svg.write(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {height}" ')
    svg.write(f'width="{width}" height="{height}" style="background:{bg}">\n')

    # Title
    svg.write(f'  <text x="{margin}" y="{margin - 12}" fill="#cccccc" '
              f'font-family="monospace" font-size="16" font-weight="bold">{title}</text>\n')
    svg.write(f'  <text x="{margin}" y="{margin + 4}" fill="#888888" '
              f'font-family="monospace" font-size="11">'
              f'{sx_size}×{sy_size}×{sz_size} · {len(blocks)} blocks</text>\n')

    # Draw blocks
    for block in blocks_sorted:
        if palette_lookup:
            block_name = palette_lookup[block.palette_index] if block.palette_index < len(palette_lookup) else "?"
        else:
            block_name = f"#{block.palette_index}"
        color = color_for(block_name)
        if color == "transparent":
            continue
        faces = _cube_polys(block.x, block.y, block.z, color, block_positions)
        for face_name, face_color, points in faces:
            pts_str = " ".join(f"{px + offset_x:.1f},{py + offset_y:.1f}" for px, py in points)
            svg.write(f'  <polygon points="{pts_str}" fill="{face_color}" '
                      f'stroke="#000000" stroke-width="0.4" stroke-opacity="0.3"/>\n')

    # Legend
    if show_legend and palette_lookup:
        seen = set()
        unique_blocks = []
        for block in blocks:
            if block.palette_index not in seen and block.palette_index < len(palette_lookup):
                seen.add(block.palette_index)
                unique_blocks.append((palette_lookup[block.palette_index], color_for(palette_lookup[block.palette_index])))
        unique_blocks.sort(key=lambda x: x[0])
        legend_x = margin
        legend_y = max_y - min_y + margin * 2 + 8
        svg.write(f'  <text x="{margin}" y="{legend_y}" fill="#888888" '
                  f'font-family="monospace" font-size="10">palette:</text>\n')
        col = 0
        row = 0
        for name, color in unique_blocks:
            x = margin + col * 140
            y = legend_y + 14 + row * 14
            svg.write(f'  <rect x="{x}" y="{y - 8}" width="10" height="10" '
                      f'fill="{color}" stroke="#444" stroke-width="0.5"/>\n')
            short = name.replace("minecraft:", "")
            svg.write(f'  <text x="{x + 14}" y="{y}" fill="#aaaaaa" '
                      f'font-family="monospace" font-size="10">{short}</text>\n')
            col += 1
            if col >= 6:
                col = 0
                row += 1

    svg.write('</svg>\n')
    return svg.getvalue()