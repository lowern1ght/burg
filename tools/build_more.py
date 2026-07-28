"""Build more OUAT-style buildings using the structure_builder library.

Each building is constructed Y-layer by Y-layer, using carpenter.nbt
as the reference. The library gives primitives (log_pillar, log_beam,
slab_top, etc.) so the caller controls exactly what goes where.

Buildings generated:
  - barracks  : 9x9 with 4 beds, table, lantern. Era 1 (spruce).
  - workshop  : 7x9 with crafting table, furnace, lantern. Era 2 (cobble).
  - market    : 9x11 wide building with 4 chests and a counter. Era 0 (oak).
  - gate      : 5x9 gate with a 2-block arch. Era 0 (oak).
"""
import sys
sys.path.insert(0, "tools")
from structure_builder import StructureBuilder
from pathlib import Path

OUT = Path("tools/structures/out/manual")
OUT.mkdir(parents=True, exist_ok=True)


# ────────────────────────────────────────────────────────────────────
# Barracks (era 1: spruce)
# 9 wide x 9 deep x 6 tall, with 4 beds and a table.
# ────────────────────────────────────────────────────────────────────
def build_barracks():
    nb = StructureBuilder((11, 6, 11))
    origin = (1, 0, 1)
    width, depth = 9, 9
    door_x = 5

    nb.ground_pad(origin, width=width, depth=depth)

    # Y=1, Y=2 walls (2 rows)
    for y in (1, 2):
        nb.log_pillar((1, y, 1), height=1)
        nb.log_pillar((width - 2, y, 1), height=1)
        nb.log_pillar((1, y, depth - 2), height=1)
        nb.log_pillar((width - 2, y, depth - 2), height=1)
        # Front beam split around door
        left_len = door_x - 2
        right_len = (width - 2) - door_x - 1
        if left_len > 0:
            nb.log_beam((2, y, 1), length=left_len, axis="x")
        if right_len > 0:
            nb.log_beam((door_x + 1, y, 1), length=right_len, axis="x")
        # Back beam
        nb.log_beam((2, y, depth - 2), length=width - 2 - 2, axis="x")
        # Side beams
        nb.log_beam((1, y, 1), length=depth - 2 - 1, axis="z")
        nb.log_beam((width - 2, y, 1), length=depth - 2 - 1, axis="z")
        # Plank fill (front + back interior)
        for x in range(2, width - 2):
            if x == door_x:
                continue
            nb.spruce_planks((x, y, 2))
            nb.spruce_planks((x, y, depth - 2))
        if y == 1:
            nb.door((door_x, y, 1), facing="north", block="spruce_door")

    # Interior: 4 beds in 2x2 grid + table + lantern
    for bx, bz in [(2, 2), (2, 6), (6, 2), (6, 6)]:
        nb.bed((bx, 2, bz), part="head", facing="south")
        nb.bed((bx, 2, bz + 1), part="foot", facing="south")
    # Table in centre
    nb.table((5, 2, 5))
    # Lantern from ceiling in middle
    nb.lantern((5, 3, 5))
    # Torch near the door
    nb.wall_torch((2, 3, depth - 2), facing="south")

    # Y=3 cornice
    nb.cornice_row(y=3, origin=origin, width=width, depth=depth, door_x=door_x)
    # Y=4 roof body
    nb.roof_body_row(y=4, origin=origin, width=width, depth=depth)
    # Y=5 cap
    nb.roof_cap(y=5, origin=origin, width=width, depth=depth)

    nb.save(str(OUT / "barracks.nbt"))
    print(f"barracks.nbt: {len(nb.blocks)} blocks")


# ────────────────────────────────────────────────────────────────────
# Workshop (era 2: cobblestone walls + oak beams)
# 7 wide x 9 deep x 6 tall, with crafting_table, furnace, lantern.
# ────────────────────────────────────────────────────────────────────
def build_workshop():
    nb = StructureBuilder((9, 6, 11))
    origin = (1, 0, 1)
    width, depth = 7, 9
    door_x = 4

    nb.ground_pad(origin, width=width, depth=depth)

    for y in (1, 2):
        # 4 corner cobble log pillars (era 2: planks=cobble, log=oak)
        nb.log_pillar((1, y, 1), height=1)
        nb.log_pillar((width - 2, y, 1), height=1)
        nb.log_pillar((1, y, depth - 2), height=1)
        nb.log_pillar((width - 2, y, depth - 2), height=1)
        # Front beam
        left_len = door_x - 2
        right_len = (width - 2) - door_x - 1
        if left_len > 0:
            nb.log_beam((2, y, 1), length=left_len, axis="x")
        if right_len > 0:
            nb.log_beam((door_x + 1, y, 1), length=right_len, axis="x")
        # Back beam
        nb.log_beam((2, y, depth - 2), length=width - 2 - 2, axis="x")
        # Side beams
        nb.log_beam((1, y, 1), length=depth - 2 - 1, axis="z")
        nb.log_beam((width - 2, y, 1), length=depth - 2 - 1, axis="z")
        # Plank fill
        for x in range(2, width - 2):
            if x == door_x:
                continue
            nb.cobble((x, y, 2))
            nb.cobble((x, y, depth - 2))
        if y == 1:
            nb.door((door_x, y, 1), facing="north", block="oak_door")

    # Interior: crafting table in centre, furnace on the side
    nb.table((width // 2 + 1, 2, depth // 2 + 1))
    nb.furnace((2, 2, depth - 2), facing="south", lit="false")
    nb.lantern((width - 2, 3, 2))
    # Wall torch near the door
    nb.wall_torch((door_x + 1, 2, 2), facing="north")
    # Anvil (workshop decoration)
    nb.set((width - 3, 2, 2), "minecraft:anvil", facing="west")

    # Y=3 cornice
    nb.cornice_row(y=3, origin=origin, width=width, depth=depth, door_x=door_x)
    # Y=4 roof body
    nb.roof_body_row(y=4, origin=origin, width=width, depth=depth)
    # Y=5 cap
    nb.roof_cap(y=5, origin=origin, width=width, depth=depth)

    nb.save(str(OUT / "workshop.nbt"))
    print(f"workshop.nbt: {len(nb.blocks)} blocks")


# ────────────────────────────────────────────────────────────────────
# Market (era 0: oak)
# 9 wide x 11 deep x 6 tall. A 9-wide room with a "counter" in the middle.
# Uses fences as the counter (carpenter Y=1 uses fence as decoration).
# ────────────────────────────────────────────────────────────────────
def build_market():
    nb = StructureBuilder((11, 6, 13))
    origin = (1, 0, 1)
    width, depth = 9, 11
    door_x = 5

    nb.ground_pad(origin, width=width, depth=depth)

    for y in (1, 2):
        nb.log_pillar((1, y, 1), height=1)
        nb.log_pillar((width - 2, y, 1), height=1)
        nb.log_pillar((1, y, depth - 2), height=1)
        nb.log_pillar((width - 2, y, depth - 2), height=1)
        left_len = door_x - 2
        right_len = (width - 2) - door_x - 1
        if left_len > 0:
            nb.log_beam((2, y, 1), length=left_len, axis="x")
        if right_len > 0:
            nb.log_beam((door_x + 1, y, 1), length=right_len, axis="x")
        nb.log_beam((2, y, depth - 2), length=width - 2 - 2, axis="x")
        nb.log_beam((1, y, 1), length=depth - 2 - 1, axis="z")
        nb.log_beam((width - 2, y, 1), length=depth - 2 - 1, axis="z")
        for x in range(2, width - 2):
            if x == door_x:
                continue
            nb.oak_planks((x, y, 2))
            nb.oak_planks((x, y, depth - 2))
        if y == 1:
            nb.door((door_x, y, 1), facing="north", block="oak_door")

    # Interior: a "counter" running across the middle (z=6). The
    # counter is a row of fences (carpenter uses fences as decoration).
    # Behind the counter (z=2..5): the seller area with a table.
    # In front of the counter (z=7..depth-2): customer area.
    # Counter: a line of fences at z=6, with a single gap for entry.
    for x in range(2, width - 2):
        if x in (door_x - 1, door_x, door_x + 1):
            continue  # gap for the entry
        nb.fence((x, 2, 6))
    # Seller side: a crafting table, a flower pot, a wall torch
    nb.table((3, 2, 3))
    nb.flower_pot((3, 3, 3))
    nb.wall_torch((2, 3, 4), facing="west")
    # Customer side: a lantern
    nb.lantern((width - 2, 3, depth - 2))
    # Carpet on the customer floor
    nb.carpet((width // 2, 2, depth - 3))

    # Y=3 cornice
    nb.cornice_row(y=3, origin=origin, width=width, depth=depth, door_x=door_x)
    # Y=4 roof body
    nb.roof_body_row(y=4, origin=origin, width=width, depth=depth)
    # Y=5 cap
    nb.roof_cap(y=5, origin=origin, width=width, depth=depth)

    nb.save(str(OUT / "market.nbt"))
    print(f"market.nbt: {len(nb.blocks)} blocks")


# ────────────────────────────────────────────────────────────────────
# Gate (era 0: oak) — a 5-wide gate in a 7-wide footprint.
# Front and back are open (no wall), only sides. Two log pillars
# flank the opening. The opening is at z=1..2 (front) and z=depth-2..depth-1 (back).
# ────────────────────────────────────────────────────────────────────
def build_gate():
    nb = StructureBuilder((7, 9, 9))
    origin = (1, 0, 1)
    width, depth = 5, 7

    # Ground: path across the whole base
    for x in range(width):
        for z in range(depth):
            nb.path((origin[0] + x, 0, origin[2] + z))
    # Jigsaw connector in the path
    nb.jigsaw((origin[0] + width // 2, 0, origin[2] - 1), orientation="south_up")

    # Two tall log pillars at the front (z=1) flanking the opening
    for y in range(1, 6):
        nb.log_pillar((1, y, 1), height=1)
        nb.log_pillar((width - 2, y, 1), height=1)
    # Two log pillars at the back (z=depth-2)
    for y in range(1, 6):
        nb.log_pillar((1, y, depth - 2), height=1)
        nb.log_pillar((width - 2, y, depth - 2), height=1)
    # Front beam (axis=x) connecting the two front pillars, OVER the opening
    nb.log_beam((1, 6, 1), length=width - 2 - 1, axis="x")
    # Back beam
    nb.log_beam((1, 6, depth - 2), length=width - 2 - 1, axis="x")
    # Side beams (axis=z) connecting front to back
    nb.log_beam((1, 1, 1), length=depth - 2 - 1, axis="z")
    nb.log_beam((width - 2, 1, 1), length=depth - 2 - 1, axis="z")
    # Side plank fill (between front and back beams, on the side faces)
    for z in range(2, depth - 2):
        nb.oak_planks((1, 2, z))
        nb.oak_planks((width - 2, 2, z))
        nb.oak_planks((1, 3, z))
        nb.oak_planks((width - 2, 3, z))
    # Above the opening: a plank ceiling at y=4
    for z in range(2, depth - 2):
        nb.oak_planks((3, 4, z))
    # Slab_top on the sides at y=4 (cornice-style)
    for z in range(2, depth - 2):
        nb.slab_top((1, 4, z), "oak_slab", underlay="oak_planks")
        nb.slab_top((width - 2, 4, z), "oak_slab", underlay="oak_planks")
    # Roof above (y=5..7): a simple peaked roof made of slab_top + planks
    # Y=5: planks across the opening
    for z in range(2, depth - 2):
        nb.oak_planks((3, 5, z))
    # Y=5 slab_top on sides
    for z in range(2, depth - 2):
        nb.slab_top((1, 5, z), "oak_slab", underlay="oak_planks")
        nb.slab_top((width - 2, 5, z), "oak_slab", underlay="oak_planks")
    # Y=6: just slab_top on the centre beam
    for z in range(2, depth - 2):
        nb.slab_top((3, 6, z), "oak_slab", underlay="oak_planks")
    # Y=7: capstone (slab_bottom centre line)
    cx = origin[0] + width // 2
    for z in range(2, depth - 2):
        nb.slab_bottom((cx, 7, z), "oak_slab", underlay="oak_planks")
    # Lanterns inside the gate
    nb.lantern((3, 4, 2))
    nb.lantern((3, 4, depth - 3))

    nb.save(str(OUT / "gate.nbt"))
    print(f"gate.nbt: {len(nb.blocks)} blocks")


build_barracks()
build_workshop()
build_market()
build_gate()
print("All done")
