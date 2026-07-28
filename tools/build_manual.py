"""Build NBTs for OUAT-style buildings, using the structure_builder library.

The library gives primitives (log_beam, log_pillar, slab_top, etc.) and a
few common patterns (ground_pad, cornice_row, roof_body_row, roof_cap).
This script composes them into 3 buildings that should look like the
author's carpenter-style buildings.

Style notes extracted from carpenter.nbt / house_3.nbt:
  Y=0  ground_pad (grass border, dirt footprint, path, jigsaw, leaves)
  Y=1  wall_ground: corners (axis=y), front beam (axis=x), back beam (axis=x),
       side beam (axis=z), plank fill with fence/dirt decoration
  Y=2  same pattern as Y=1
  Y=3  cornice: |FPDPF| at corners, =F...F= sides, =P...P= inner plank,
       slab_top overhang
  Y=4  roof body: _PPPPP_ (slab_bottom border, plank fill, asymmetric deco)
  Y=5  roof cap: one slab_bottom line through centre
"""
import sys
sys.path.insert(0, "tools")
from structure_builder import StructureBuilder
from pathlib import Path

OUT = Path("tools/structures/out/manual")
OUT.mkdir(parents=True, exist_ok=True)


# ────────────────────────────────────────────────────────────────────
# House (era 0: oak)
# 9 wide x 9 deep x 6 tall, with door at front-centre
# ────────────────────────────────────────────────────────────────────
def build_house():
    nb = StructureBuilder((11, 6, 11))
    origin = (1, 0, 1)
    width, depth = 9, 9
    door_x = 5

    # Y=0
    nb.ground_pad(origin, width=width, depth=depth)

    # Y=1: wall_ground. The log_beam is the "lintel" of the wall.
    # Front face is z=1, back face is z=depth-2.
    # 4 corner log pillars
    nb.log_pillar((1, 1, 1), height=1)
    nb.log_pillar((width - 2, 1, 1), height=1)
    nb.log_pillar((1, 1, depth - 2), height=1)
    nb.log_pillar((width - 2, 1, depth - 2), height=1)
    # Front beam (axis=x), splits around the door:
    # left  of door: x=2..door_x-1 (length = door_x - 2)
    # right of door: x=door_x+1..width-3 (length = (width-2) - door_x - 1)
    left_len = door_x - 2
    right_len = (width - 2) - door_x - 1
    if left_len > 0:
        nb.log_beam((2, 1, 1), length=left_len, axis="x")
    if right_len > 0:
        nb.log_beam((door_x + 1, 1, 1), length=right_len, axis="x")
    # Back beam (axis=x), full width between corners
    nb.log_beam((2, 1, depth - 2), length=width - 2 - 2, axis="x")
    # Side beams (axis=z), full length of the side
    nb.log_beam((1, 1, 1), length=depth - 2 - 1, axis="z")
    nb.log_beam((width - 2, 1, 1), length=depth - 2 - 1, axis="z")
    # Plank fill: just the row INSIDE the front beam (z=2) and
    # INSIDE the back beam (z=depth-2). Interior stays air.
    for x in range(2, width - 2):
        if x == door_x:
            continue
        nb.oak_planks((x, 1, 2))
        nb.oak_planks((x, 1, depth - 2))
    # Door (2 blocks at door_x, z=1)
    nb.door((door_x, 1, 1), facing="north")

    # Y=2: same pattern as Y=1
    nb.log_pillar((1, 2, 1), height=1)
    nb.log_pillar((width - 2, 2, 1), height=1)
    nb.log_pillar((1, 2, depth - 2), height=1)
    nb.log_pillar((width - 2, 2, depth - 2), height=1)
    if left_len > 0:
        nb.log_beam((2, 2, 1), length=left_len, axis="x")
    if right_len > 0:
        nb.log_beam((door_x + 1, 2, 1), length=right_len, axis="x")
    nb.log_beam((2, 2, depth - 2), length=width - 2 - 2, axis="x")
    nb.log_beam((1, 2, 1), length=depth - 2 - 1, axis="z")
    nb.log_beam((width - 2, 2, 1), length=depth - 2 - 1, axis="z")
    for x in range(2, width - 2):
        if x == door_x:
            continue
        nb.oak_planks((x, 2, 2))
        nb.oak_planks((x, 2, depth - 2))
    # Side walls (between front and back beams, on the side faces)
    # Just planks at the cells adjacent to the corners. Interior stays
    # air for the player to walk.
    # (carpenter Y=1 has ~ beam on sides — already done above)

    # Interior furniture (Y=2)
    nb.bed((2, 2, 2), part="head", facing="south")
    nb.bed((2, 2, 3), part="foot", facing="south")
    nb.table((width - 3, 2, depth - 2))
    nb.flower_pot((width - 3, 3, depth - 2))
    nb.lantern((width - 2, 3, 2))
    nb.carpet((5, 2, 5))
    nb.wall_torch((2, 3, depth - 3), facing="south")

    # Y=3: cornice (FPF + slab_top overhang)
    nb.cornice_row(y=3, origin=origin, width=width, depth=depth, door_x=door_x)

    # Y=4: roof body
    nb.roof_body_row(y=4, origin=origin, width=width, depth=depth)

    # Y=5: roof cap (slab_bottom line, underlay = slab_top ridge at y=4)
    nb.roof_cap(y=5, origin=origin, width=width, depth=depth)

    nb.save(str(OUT / "house.nbt"))
    print(f"house.nbt: {len(nb.blocks)} blocks, palette={len(nb._palette_list)}")


# ────────────────────────────────────────────────────────────────────
# Watchtower (era 0: oak, 5x5 base, tall)
# ────────────────────────────────────────────────────────────────────
def build_watchtower():
    nb = StructureBuilder((7, 9, 7))
    origin = (1, 0, 1)
    width, depth = 5, 5
    door_x = 3

    nb.ground_pad(origin, width=width, depth=depth)

    # Walls (3 rows). For a 5-wide tower there's no room for "left of
    # door" and "right of door" beams separately — the door takes the
    # entire front. So we just put the corner pillars and planks.
    for y in range(1, 4):
        # 4 corner pillars
        nb.log_pillar((1, y, 1), height=1)
        nb.log_pillar((width - 2, y, 1), height=1)
        nb.log_pillar((1, y, depth - 2), height=1)
        nb.log_pillar((width - 2, y, depth - 2), height=1)
        # side beams (axis=z), full length of the side
        nb.log_beam((1, y, 1), length=depth - 2 - 1, axis="z")
        nb.log_beam((width - 2, y, 1), length=depth - 2 - 1, axis="z")
        # back beam (axis=x), full
        nb.log_beam((1, y, depth - 2), length=width - 2 - 1, axis="x")
        # FRONT: full beam (the door is going to be inserted ON TOP of
        # the front beam at door_x)
        nb.log_beam((1, y, 1), length=width - 2 - 1, axis="x")
        # Fill: planks between front beam (z=1) and back beam (z=depth-2)
        for x in range(2, width - 2):
            if x == door_x and y == 1:
                # door position on Y=1 lower half — skip
                continue
            if x == door_x and y == 2:
                # door position on Y=2 upper half — skip
                continue
            # Place a plank at every z between the two beams
            for z in range(2, depth - 1):
                nb.oak_planks((x, y, z))
        if y == 1:
            nb.door((door_x, y, 1), facing="north")

    # Ladder
    for y in range(1, 5):
        nb.ladder((door_x, y, depth - 2), facing="west")

    # Lanterns
    nb.lantern((1, 3, 2))
    nb.lantern((width - 2, 3, 2))

    # Cornice + battlements
    for x in range(0, width):
        nb.slab_top((x, 4, 0), "oak_slab")
        nb.slab_top((x, 4, depth - 1), "oak_slab")
    for z in range(0, depth):
        nb.slab_top((0, 4, z), "oak_slab")
        nb.slab_top((width - 1, 4, z), "oak_slab")
    # Battlements (carpenter pattern)
    for x in range(1, width - 1):
        if (x - 1) % 2 == 0:
            nb.slab_top((x, 5, 1), "oak_slab")
            nb.slab_top((x, 5, depth - 2), "oak_slab")
    for z in range(2, depth - 2):
        if (z - 1) % 2 == 0:
            nb.slab_top((1, 5, z), "oak_slab")
            nb.slab_top((width - 2, 5, z), "oak_slab")
    # Battlements platform (interior)
    for x in range(1, width - 1):
        for z in range(1, depth - 1):
            nb.oak_planks((x, 4, z))

    nb.save(str(OUT / "watchtower.nbt"))
    print(f"watchtower.nbt: {len(nb.blocks)} blocks")


# ────────────────────────────────────────────────────────────────────
# Wall segment (era 0: oak, 1-wide x 6 long)
# ────────────────────────────────────────────────────────────────────
def build_wall():
    nb = StructureBuilder((3, 6, 8))
    length = 6
    height = 4

    # Path strip underneath
    for z in range(length):
        nb.path((0, 0, z))
        nb.coarse_dirt((1, 0, z))
    # Wall body (1-wide). Log pillars every 3 blocks + plank between
    for y in range(1, height + 1):
        for z in range(length):
            if z % 3 == 0:
                nb.log((0, y, z), axis="y")
            else:
                nb.oak_planks((0, y, z))
    # Cornice: slab_top on the top
    for z in range(length):
        nb.slab_top((0, height + 1, z), "oak_slab")
    # Crenellations
    for z in range(length):
        if z % 2 == 0:
            nb.slab_top((0, height + 2, z), "oak_slab")
    # Jigsaw at both ends
    nb.jigsaw((0, 2, 0), orientation="north_up")
    nb.jigsaw((0, 2, length - 1), orientation="south_up")

    nb.save(str(OUT / "wall.nbt"))
    print(f"wall.nbt: {len(nb.blocks)} blocks")


build_house()
build_watchtower()
build_wall()
print("All done")
