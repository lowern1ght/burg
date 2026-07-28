"""Building recipes — parametric Python functions that build a StructureBuilder.

Each recipe takes parameters (footprint, height, materials, etc.) and
mutates a StructureBuilder with the building's blocks. Reusable across
different buildings with similar shapes.

Conventions:
    - All coords are local to the structure (0,0,0) at the southwest bottom corner.
    - The structure size is (width, height, depth) including the footprint.
    - Y=0 is the floor level. The ground below is air / decoration.
"""

from __future__ import annotations

from typing import Optional

from ..builder import StructureBuilder


# ---- helpers ----

def _door(b: StructureBuilder, x: int, y: int, z: int) -> None:
    b.set_block((x, y, z), "minecraft:oak_door",
                {"facing": "north", "half": "lower", "hinge": "left", "open": "false"})
    b.set_block((x, y + 1, z), "minecraft:oak_door",
                {"facing": "north", "half": "upper", "hinge": "left", "open": "false"})


def _window_pane(b: StructureBuilder, x: int, y: int, z: int) -> None:
    b.set_block((x, y, z), "minecraft:glass_pane")


def _torch(b: StructureBuilder, x: int, y: int, z: int) -> None:
    b.set_block((x, y, z), "minecraft:torch")


def _lantern(b: StructureBuilder, x: int, y: int, z: int) -> None:
    b.set_block((x, y, z), "minecraft:lantern")


def _slab(b: StructureBuilder, x: int, y: int, z: int, material: str) -> None:
    b.set_block((x, y, z), f"minecraft:{material}_slab", {"type": "bottom"})


def _stairs(b: StructureBuilder, x: int, y: int, z: int, material: str = "oak",
            facing: str = "east") -> None:
    b.set_block((x, y, z), f"minecraft:{material}_stairs",
                {"facing": facing, "half": "bottom"})


# ---- recipes ----

def cottage_small(width: int = 5, depth: int = 4, height: int = 3) -> StructureBuilder:
    """Small timber cottage. Roof is oak stairs forming a triangle peak."""
    size = (width, height + 2, depth)
    b = StructureBuilder(size)
    # Floor
    b.fill_box((0, 0, 0), (width - 1, 0, depth - 1), "minecraft:oak_planks")
    # Walls (oak planks)
    b.hollow_box((0, 1, 0), (width - 1, height - 1, depth - 1),
                 "minecraft:oak_planks", floor=None, ceiling=None)
    # Windows
    _window_pane(b, 1, 2, 0)
    _window_pane(b, 3, 2, 0)
    _window_pane(b, 0, 2, 2)
    _window_pane(b, width - 1, 2, 2)
    # Door (south face)
    _door(b, width // 2, 1, depth - 1)
    # Interior light
    _lantern(b, width // 2, height - 1, depth // 2)
    # Roof — peaked stairs
    # South slope
    for layer in range(height - 1, height + 1):
        b.fill_box((0, layer, depth - 1), (width - 1, layer, depth - 1), "minecraft:oak_stairs",
                   {"facing": "south", "half": "bottom"})
    # North slope (face=other direction; for a peaked roof we use slabs)
    for layer in range(height - 1, height + 1):
        b.fill_box((0, layer, 0), (width - 1, layer, 0), "minecraft:oak_stairs",
                   {"facing": "north", "half": "bottom"})
    # Top of roof — slab ridge
    _slab(b, 0, height + 1, depth // 2, "oak")
    return b


def smithy_stone(width: int = 6, depth: int = 5, height: int = 3) -> StructureBuilder:
    """Stone smithy with forge and chimney."""
    size = (width, height + 2, depth)  # extra height for chimney
    b = StructureBuilder(size)
    # Cobble floor
    b.fill_box((0, 0, 0), (width - 1, 0, depth - 1), "minecraft:cobblestone")
    # Stone brick walls
    b.hollow_box((0, 1, 0), (width - 1, height - 1, depth - 1),
                 "minecraft:stone_bricks", floor=None, ceiling=None)
    # Forge (furnace block in the back)
    b.set_block((1, 1, 1), "minecraft:blast_furnace")
    b.set_block((2, 1, 1), "minecraft:anvil")
    # Door
    _door(b, width // 2, 1, depth - 1)
    # Windows
    _window_pane(b, 1, 2, 0)
    _window_pane(b, width - 2, 2, 0)
    # Roof — stone brick slabs
    b.fill_box((0, height, 0), (width - 1, height, depth - 1), "minecraft:stone_slab",
               {"type": "top"})
    # Chimney — stone bricks going up from the back
    b.fill_box((width - 2, height, 1), (width - 2, height + 1, 1), "minecraft:stone_bricks")
    # Smoke
    _torch(b, width - 2, height + 1, 1)
    # Interior light
    _lantern(b, width // 2, height - 1, depth // 2)
    return b


def watchtower_wood(width: int = 3, depth: int = 3, height: int = 8) -> StructureBuilder:
    """Wooden watchtower with roof. Tall vertical structure."""
    size = (width, height + 1, depth)
    b = StructureBuilder(size)
    # Cobble base (one block up from ground)
    b.fill_box((0, 0, 0), (width - 1, 0, depth - 1), "minecraft:cobblestone")
    # Walls — spruce logs as posts at corners
    for x, z in [(0, 0), (width - 1, 0), (0, depth - 1), (width - 1, depth - 1)]:
        b.fill_box((x, 1, z), (x, height - 1, z), "minecraft:spruce_log")
    # Plank walls between posts
    b.fill_box((1, 2, 0), (width - 2, height - 2, 0), "minecraft:spruce_planks")
    b.fill_box((1, 2, depth - 1), (width - 2, height - 2, depth - 1), "minecraft:spruce_planks")
    b.fill_box((0, 2, 1), (0, height - 2, depth - 2), "minecraft:spruce_planks")
    b.fill_box((width - 1, 2, 1), (width - 1, height - 2, depth - 2), "minecraft:spruce_planks")
    # Floor at top
    b.fill_box((0, height - 1, 0), (width - 1, height - 1, depth - 1), "minecraft:spruce_planks")
    # Windows on top floor
    for x, z in [(1, 0), (1, depth - 1), (0, 1), (width - 1, 1)]:
        _window_pane(b, x, height - 1, z)
    # Battlements (crenellations) around the top
    for x in range(width):
        b.set_block((x, height, 0), "minecraft:spruce_planks")
        b.set_block((x, height, depth - 1), "minecraft:spruce_planks")
    for z in range(1, depth - 1):
        b.set_block((0, height, z), "minecraft:spruce_planks")
        b.set_block((width - 1, height, z), "minecraft:spruce_planks")
    # Lantern at top
    _lantern(b, width // 2, height - 1, depth // 2)
    # Peaked roof using slabs
    _slab(b, 0, height, depth // 2, "spruce")
    _slab(b, width - 1, height, depth // 2, "spruce")
    return b


def market_stall(width: int = 4, depth: int = 3, height: int = 3) -> StructureBuilder:
    """Open market stall with awning. No back wall."""
    size = (width, height, depth)
    b = StructureBuilder(size)
    # Floor
    b.fill_box((0, 0, 0), (width - 1, 0, depth - 1), "minecraft:oak_planks")
    # Posts at corners (no walls)
    for x, z in [(0, 0), (width - 1, 0), (0, depth - 1), (width - 1, depth - 1)]:
        b.fill_box((x, 1, z), (x, height - 1, z), "minecraft:oak_log")
    # Counter at front
    b.fill_box((1, 1, depth - 1), (width - 2, 1, depth - 1), "minecraft:oak_slab",
               {"type": "top"})
    # Awning roof — alternating colored wool? Use oak slabs for now
    b.fill_box((0, height - 1, 0), (width - 1, height - 1, depth - 1), "minecraft:oak_slab",
               {"type": "top"})
    # Lantern hanging in the middle
    _lantern(b, width // 2, height - 2, depth // 2)
    # Display chest on counter
    b.set_block((1, 2, depth - 1), "minecraft:chest")
    return b


def bridge_section(width: int = 6, depth: int = 2, height: int = 1) -> StructureBuilder:
    """Flat bridge section. Replaces air over a gap."""
    size = (width, height + 2, depth)
    b = StructureBuilder(size)
    # Plank deck
    b.fill_box((0, 0, 0), (width - 1, 0, depth - 1), "minecraft:oak_planks")
    # Side rails (spruce fence)
    for x in range(width):
        b.set_block((x, 1, 0), "minecraft:oak_fence")
        b.set_block((x, 1, depth - 1), "minecraft:oak_fence")
    # Lanterns at both ends
    _lantern(b, 0, 2, depth // 2)
    _lantern(b, width - 1, 2, depth // 2)
    return b


# ---- catalog ----

RECIPE_CATALOG = {
    "cottage_small":    (cottage_small,    "Small timber cottage",         {"width": 5, "depth": 4, "height": 3}),
    "smithy_stone":      (smithy_stone,      "Stone smithy with chimney",    {"width": 6, "depth": 5, "height": 3}),
    "watchtower_wood":   (watchtower_wood,   "Wooden watchtower",            {"width": 3, "depth": 3, "height": 8}),
    "market_stall":      (market_stall,      "Open market stall",            {"width": 4, "depth": 3, "height": 3}),
    "bridge_section":    (bridge_section,    "Flat bridge section",          {"width": 6, "depth": 2, "height": 1}),
}