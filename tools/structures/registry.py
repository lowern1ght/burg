"""Block palette and Minecraft 1.20 → 1.21 known renames.

Block registry is intentionally minimal — only blocks we actually use in
the demo pack recipes. Extend as new buildings are added.

Color palette is for the isometric SVG renderer. Hex values approximate
the in-game texture colors at a glance.
"""

from typing import Dict, Tuple


# (block_id) -> (palette_color, [common property states])
PALETTE: Dict[str, Tuple[str, Tuple[str, ...]]] = {
    # Wood
    "minecraft:oak_planks":        ("#b88a4e", ()),
    "minecraft:spruce_planks":     ("#7a5a32", ()),
    "minecraft:birch_planks":      ("#dcc888", ()),
    "minecraft:dark_oak_planks":   ("#4a3320", ()),
    "minecraft:oak_log":           ("#6b4a25", ()),
    "minecraft:spruce_log":        ("#3b2c1a", ()),
    "minecraft:oak_stairs":        ("#a07540", ()),
    "minecraft:oak_slab":          ("#b88a4e", ()),
    "minecraft:oak_fence":         ("#7a5a32", ()),
    "minecraft:oak_door":          ("#9c703c", ()),
    "minecraft:oak_trapdoor":      ("#8a6234", ()),

    # Stone
    "minecraft:cobblestone":       ("#7e7e7e", ()),
    "minecraft:stone":             ("#9a9a9a", ()),
    "minecraft:stone_bricks":      ("#a0a0a0", ()),
    "minecraft:mossy_cobblestone": ("#6f8060", ()),
    "minecraft:stone_stairs":      ("#9a9a9a", ()),
    "minecraft:stone_slab":        ("#9a9a9a", ()),

    # Glass / light
    "minecraft:glass":             ("#cfe9f5", ()),
    "minecraft:glass_pane":        ("#dfeef7", ()),
    "minecraft:torch":             ("#ffcc44", ()),
    "minecraft:lantern":           ("#f5b76b", ()),

    # Roof / covering
    "minecraft:oak_stairs[facing=east,half=bottom]": ("#a07540", ()),  # not real syntax, see properties below

    # Misc blocks
    "minecraft:hay_block":         ("#c9b14a", ()),
    "minecraft:dirt":              ("#8b6a3f", ()),
    "minecraft:grass_block":       ("#5fa84a", ()),
    "minecraft:water":             ("#3b6ec4", ()),
    "minecraft:air":               ("none",    ()),
    "minecraft:crafting_table":    ("#7a5a32", ()),

    # Crops / farms
    "minecraft:wheat":             ("#d4c042", ()),
    "minecraft:carrots":           ("#e07a2c", ()),
    "minecraft:potatoes":          ("#b59c5e", ()),
    "minecraft:beehive":           ("#e6c66e", ()),
    "minecraft:campfire":          ("#a04a2c", ()),

    # Decor
    "minecraft:bookshelf":         ("#9c703c", ()),
    "minecraft:chest":             ("#9c703c", ()),
    "minecraft:barrel":            ("#7a5a32", ()),
    "minecraft:flower_pot":        ("#a87347", ()),
    "minecraft:ladder":            ("#7a5a32", ()),
}


# Properties we use in recipes (subset of vanilla block states).
# Each entry is the canonical property dict for that block state.
BLOCK_PROPERTIES = {
    "minecraft:oak_stairs": {"facing": "north", "half": "bottom"},
    "minecraft:spruce_stairs": {"facing": "north", "half": "bottom"},
    "minecraft:oak_slab": {"type": "bottom"},
    "minecraft:stone_slab": {"type": "bottom"},
    "minecraft:oak_fence": {"east": "true", "north": "true", "south": "true", "west": "true"},
    "minecraft:oak_door": {"facing": "north", "half": "lower", "hinge": "left", "open": "false"},
    "minecraft:glass_pane": {"east": "true", "north": "true", "south": "true", "west": "true"},
}


# Known Minecraft renames between 1.20.1 and 1.21.1 that affect vanilla blocks.
# Used by the validator to suggest a fix instead of just failing.
# Format: old_id -> new_id (block stayed, name changed).
KNOWN_RENAMES: Dict[str, str] = {
    "minecraft:grass_path":   "minecraft:dirt_path",
    # add more as we discover them
}


# Block IDs that exist in 1.20.1 vanilla but were REMOVED in 1.21.1.
# The validator reports these as "removed" rather than "renamed".
REMOVED_BLOCKS: set = set()
# Populated as we discover them during validation runs.

# Color overrides for special cases (block IDs that look fine but render badly).
COLOR_OVERRIDES: Dict[str, str] = {
    "minecraft:oak_stairs":      "#a07540",
    "minecraft:oak_slab":        "#b88a4e",
    "minecraft:stone_stairs":    "#9a9a9a",
    "minecraft:stone_slab":      "#9a9a9a",
}


def color_for(block_id: str) -> str:
    """Return the SVG fill color for a block ID. Falls back to gray for unknown."""
    if block_id in COLOR_OVERRIDES:
        return COLOR_OVERRIDES[block_id]
    if block_id in PALETTE:
        color, _ = PALETTE[block_id]
        return color if color != "none" else "transparent"
    return "#888888"  # unknown block — gray with hash prefix