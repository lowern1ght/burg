"""Colour and shape for every block the corpus actually uses.

Two separate concerns, both needed by the PNG renderer:

* COLOURS — an approximate top-face colour per block id. Covers all 121 ids
  present in the author's corpus, so a render never degrades into grey mush.
* shape_of() — how much of its cell a block fills. This is what makes a
  stair-pitched roof read as a *slope* instead of a solid wedge, which is the
  whole reason the renderer exists.
"""

from __future__ import annotations

from typing import Dict, Tuple

from .nbtio import BlockState

# ── colours ─────────────────────────────────────────────────────────
# Keyed by short id (no "minecraft:"). Approximate in-game top-face tint.

COLOURS: Dict[str, str] = {
    # ground
    "grass_block": "#79a54b", "dirt": "#8b6a47", "coarse_dirt": "#7a5b3c",
    "dirt_path": "#9c8253", "rooted_dirt": "#95705a", "podzol": "#5c3f18",
    "farmland": "#6b4a2a", "mud": "#3d3a3c", "moss_block": "#5b7327",
    "moss_carpet": "#5b7327", "stone": "#8f8f8f", "smooth_stone": "#a3a3a3",
    "water": "#3f5fbf", "seagrass": "#3f8f3f", "lily_pad": "#3f7f3f",
    "fire": "#e8871e", "cobweb": "#dcdcdc",

    # wood
    "oak_planks": "#b78d54", "oak_log": "#6f5735", "stripped_oak_log": "#b89055",
    "oak_slab": "#b78d54", "oak_stairs": "#ac8149", "oak_fence": "#8f6f42",
    "oak_fence_gate": "#8f6f42", "oak_door": "#9a7440", "oak_trapdoor": "#8d6a3a",
    "oak_pressure_plate": "#b78d54", "oak_button": "#b78d54",
    "oak_sapling": "#5b8f34", "oak_leaves": "#4f7f2f", "oak_wall_sign": "#a37f4a",
    "oak_wall_hanging_sign": "#a37f4a", "bookshelf": "#9c7a48",
    "chiseled_bookshelf": "#a5814c", "lectern": "#8f6c3d", "barrel": "#7f6238",
    "chest": "#9a7440", "crafting_table": "#7f5f38", "composter": "#7a5f38",
    "ladder": "#8f6f42", "note_block": "#6f5735", "beehive": "#c39a52",
    "bee_nest": "#c8a052", "honey_block": "#e0a12f", "honeycomb_block": "#e5a12b",
    "decorated_pot": "#b06a4a", "flower_pot": "#a05a3c",

    # stone family
    "cobblestone": "#7f7f7f", "cobblestone_slab": "#7f7f7f",
    "cobblestone_stairs": "#767676", "cobblestone_wall": "#767676",
    "mossy_cobblestone": "#6f7f5f", "mossy_cobblestone_slab": "#6f7f5f",
    "mossy_cobblestone_stairs": "#67775a", "mossy_cobblestone_wall": "#67775a",
    "stone_slab": "#8f8f8f", "stone_bricks": "#9a9a9a",
    # The 1.21 stone families the fortification set builds from. Sampled from
    # the vanilla textures: without these the renderer fell back to a default
    # grey and a deepslate wall rendered the same as a cobblestone one, which
    # made the whole material ladder invisible in the contact sheets.
    "tuff": "#6b6b62", "tuff_stairs": "#6b6b62", "tuff_slab": "#6b6b62",
    "tuff_wall": "#6b6b62", "polished_tuff": "#767771", "tuff_bricks": "#72736c",
    "andesite": "#88898a", "andesite_stairs": "#88898a",
    "andesite_slab": "#88898a", "andesite_wall": "#88898a",
    "polished_andesite": "#8b8c8b",
    "stone_brick_stairs": "#9a9a9a", "stone_brick_slab": "#9a9a9a",
    "stone_brick_wall": "#9a9a9a", "mossy_stone_bricks": "#7d8a72",
    "cracked_stone_bricks": "#918f8c", "chiseled_stone_bricks": "#969696",
    "deepslate_bricks": "#4b4b4f", "deepslate_brick_stairs": "#4b4b4f",
    "deepslate_brick_slab": "#4b4b4f", "deepslate_brick_wall": "#4b4b4f",
    "cobbled_deepslate": "#565659", "polished_deepslate": "#4f4f52",
    "deepslate_tiles": "#39393c", "deepslate": "#5b5b60",
    "white_terracotta": "#d1b1a1", "stonecutter": "#8a8a8a",
    "cauldron": "#4a4a4a", "water_cauldron": "#3f5fbf", "anvil": "#4a4a4a",
    "furnace": "#767676", "smoker": "#5f4a33", "blast_furnace": "#5a5a5a",
    "campfire": "#a4552a", "bell": "#e0b23c", "lever": "#8f8f8f",
    "chain": "#4a4e57", "tripwire_hook": "#8f8f8f",

    # light
    "torch": "#ffcc44", "wall_torch": "#ffcc44", "redstone_torch": "#e04f2f",
    "lantern": "#f3b45f", "candle": "#e8dcc0", "white_candle": "#f0ece0",
    "red_candle": "#c04434",

    # cloth / beds
    "white_bed": "#e4e4e4", "red_bed": "#a63a34", "orange_bed": "#d1732f",
    "brown_bed": "#7f5533", "lime_bed": "#7fc030",
    "white_wool": "#e9ecec", "red_wool": "#a12722", "brown_wool": "#7f5533",
    "yellow_wool": "#f0af15", "lime_wool": "#7fc030",
    "white_carpet": "#e9ecec", "red_carpet": "#a12722", "yellow_carpet": "#f0af15",
    "white_wall_banner": "#e9ecec", "red_wall_banner": "#a12722",
    "brown_wall_banner": "#7f5533",

    # glass
    "glass": "#c8e4f0", "glass_pane": "#d4ebf5",

    # plants
    "grass": "#7fb04f", "short_grass": "#7fb04f", "wheat": "#c9b247",
    "carrots": "#e07a2c", "potatoes": "#c0a45f", "hay_block": "#c8ab30",
    "carved_pumpkin": "#d1791f",
    "dandelion": "#f0d93c", "poppy": "#d2453c", "allium": "#b48fd4",
    "azure_bluet": "#e0e0e0", "red_tulip": "#cf3a34", "pink_tulip": "#e8a8c8",
    "white_tulip": "#e8e8e8", "cornflower": "#5f7fd4", "oxeye_daisy": "#e8e8d8",
    "potted_cornflower": "#5f7fd4", "potted_allium": "#b48fd4",
    "potted_oxeye_daisy": "#e8e8d8", "potted_azure_bluet": "#e0e0e0",
    "potted_red_tulip": "#cf3a34", "potted_orange_tulip": "#e08a2c",
    "potted_white_tulip": "#e8e8e8", "potted_dandelion": "#f0d93c",
    "potted_lily_of_the_valley": "#e8e8e8", "potted_oak_sapling": "#5b8f34",

    # markers
    "jigsaw": "#c04ad0",
    "burg:town_anchor": "#d4a017",
    "town_anchor": "#d4a017",
}

# ── shapes ──────────────────────────────────────────────────────────
# A shape is (kind, params). The renderer turns these into boxes.
#   full       — the whole cell
#   slab       — half height, "bottom" or "top"
#   stairs     — lower half plus a quarter step on one side
#   post       — narrow vertical column (fence, wall, pane, bars)
#   flat       — thin plate on the floor (carpet, pressure plate, lily pad)
#   plate      — thin plate at an arbitrary height (trapdoor)
#   tiny       — small centred blob (torch, flower, button)
#   door       — narrow full-height panel against one side

FULL_OVERRIDES = {
    # These read better as full cubes even though they are technically not.
    "farmland", "hay_block", "moss_block", "water", "fire", "cobweb",
    "honey_block", "honeycomb_block", "bookshelf", "chiseled_bookshelf",
    "crafting_table", "furnace", "smoker", "blast_furnace", "note_block",
    "beehive", "bee_nest", "barrel", "composter", "white_terracotta",
    "smooth_stone", "stone_bricks", "carved_pumpkin", "stonecutter",
    "decorated_pot", "chest", "anvil", "lectern", "mud", "podzol",
    "rooted_dirt",
}

POST_SUFFIXES = ("_fence", "_fence_gate", "_wall", "_pane", "_bars")
FLAT_SUFFIXES = ("_carpet", "_pressure_plate")
TINY_NAMES = {
    "torch", "wall_torch", "redstone_torch", "lantern", "candle",
    "white_candle", "red_candle", "flower_pot", "lever", "tripwire_hook",
    "bell", "chain", "cauldron", "water_cauldron", "campfire",
}
TINY_SUFFIXES = ("_button", "_sapling", "_tulip", "_daisy", "_bluet",
                 "_banner", "_sign")
PLANT_NAMES = {
    "grass", "short_grass", "wheat", "carrots", "potatoes", "dandelion",
    "poppy", "allium", "cornflower", "seagrass", "oak_sapling",
}

Shape = Tuple[str, str]


def shape_of(b: BlockState) -> Shape:
    """Classify a block state into a render shape."""
    n = b.short
    base = n.split(":")[-1]

    if base in FULL_OVERRIDES:
        return ("full", "")
    if base.startswith("potted_"):
        return ("tiny", "")
    if base.endswith("_slab"):
        t = b.get("type", "bottom")
        if t == "double":
            return ("full", "")
        return ("slab", t)
    if base.endswith("_stairs"):
        return ("stairs", f"{b.get('facing', 'north')}:{b.get('half', 'bottom')}")
    if base.endswith("_trapdoor"):
        if b.get("open", "false") == "true":
            return ("post", b.get("facing", "north"))
        return ("plate", b.get("half", "bottom"))
    if base.endswith("_door"):
        return ("door", b.get("facing", "north"))
    if base.endswith("_bed"):
        return ("slab", "bottom")
    if base.endswith(POST_SUFFIXES):
        return ("post", "")
    if base.endswith(FLAT_SUFFIXES) or base in ("lily_pad", "moss_carpet", "snow"):
        return ("flat", "")
    if base == "ladder":
        return ("post", b.get("facing", "north"))
    if base in TINY_NAMES or base.endswith(TINY_SUFFIXES):
        return ("tiny", "")
    if base in PLANT_NAMES:
        return ("plant", "")
    if base.endswith("_leaves"):
        return ("full", "")
    return ("full", "")


def colour_of(b: BlockState) -> str:
    """Approximate colour for a block state, never failing."""
    n = b.short
    if n in COLOURS:
        return COLOURS[n]
    base = n.split(":")[-1]
    if base in COLOURS:
        return COLOURS[base]
    # Family fallbacks so an unlisted variant still renders sensibly.
    for suffix, key in (("_slab", "oak_slab"), ("_stairs", "oak_stairs"),
                        ("_planks", "oak_planks"), ("_log", "oak_log"),
                        ("_leaves", "oak_leaves"), ("_fence", "oak_fence"),
                        ("_wall", "cobblestone_wall"), ("_door", "oak_door"),
                        ("_bed", "white_bed"), ("_wool", "white_wool"),
                        ("_carpet", "white_carpet"), ("_pane", "glass_pane")):
        if base.endswith(suffix):
            return COLOURS[key]
    return "#9b8f86"


def unknown_blocks(ids) -> list:
    """Which of these short ids have no explicit colour (for coverage checks)."""
    return sorted({i for i in ids
                   if i not in COLOURS and i.split(":")[-1] not in COLOURS})
