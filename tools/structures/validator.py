"""Static NBT validator — checks structures against the target MC registry.

Can be used standalone:
    python -m tools.structures.validator path/to/structure.nbt

Or as a library:
    from tools.structures.validator import validate_structure
    issues = validate_structure(path, registry=KNOWN_BLOCKS)
"""

from __future__ import annotations

import sys
from pathlib import Path
from typing import Dict, List, Set

import nbtlib
from nbtlib import Compound

from .registry import KNOWN_RENAMES, REMOVED_BLOCKS


# Minimal block registry for 1.21.1 vanilla Minecraft.
# This is a subset — only blocks our recipes use. Extend as needed.
# Generated manually from MC 1.21.1 data; verified against the wiki.
VANILLA_BLOCKS_1_21: Set[str] = {
    # air / structure
    "minecraft:air",
    "minecraft:structure_void",
    # wood
    "minecraft:oak_planks", "minecraft:spruce_planks", "minecraft:birch_planks",
    "minecraft:jungle_planks", "minecraft:acacia_planks", "minecraft:dark_oak_planks",
    "minecraft:mangrove_planks", "minecraft:cherry_planks", "minecraft:bamboo_planks",
    "minecraft:oak_log", "minecraft:spruce_log", "minecraft:birch_log",
    "minecraft:jungle_log", "minecraft:acacia_log", "minecraft:dark_oak_log",
    "minecraft:oak_stairs", "minecraft:spruce_stairs", "minecraft:birch_stairs",
    "minecraft:stone_stairs", "minecraft:cobblestone_stairs",
    "minecraft:oak_slab", "minecraft:spruce_slab", "minecraft:stone_slab",
    "minecraft:cobblestone_slab", "minecraft:oak_fence", "minecraft:spruce_fence",
    "minecraft:oak_door", "minecraft:oak_trapdoor", "minecraft:spruce_door",
    # stone
    "minecraft:cobblestone", "minecraft:stone", "minecraft:stone_bricks",
    "minecraft:mossy_cobblestone", "minecraft:mossy_stone_bricks",
    "minecraft:granite", "minecraft:diorite", "minecraft:andesite",
    "minecraft:deepslate", "minecraft:deepslate_bricks", "minecraft:deepslate_tiles",
    "minecraft:bricks", "minecraft:nether_bricks",
    # glass / light
    "minecraft:glass", "minecraft:glass_pane", "minecraft:white_stained_glass_pane",
    "minecraft:torch", "minecraft:lantern", "minecraft:soul_lantern",
    "minecraft:oak_pressure_plate",
    # earth
    "minecraft:dirt", "minecraft:grass_block", "minecraft:podzol",
    "minecraft:coarse_dirt", "minecraft:rooted_dirt",
    "minecraft:dirt_path",  # 1.21 rename from grass_path
    "minecraft:farmland", "minecraft:water",
    # crops
    "minecraft:wheat", "minecraft:carrots", "minecraft:potatoes",
    "minecraft:beetroots", "minecraft:melon", "minecraft:pumpkin",
    "minecraft:beehive", "minecraft:campfire", "minecraft:soul_campfire",
    # functional
    "minecraft:crafting_table", "minecraft:furnace", "minecraft:lit_furnace",
    "minecraft:smoker", "minecraft:lit_smoker", "minecraft:blast_furnace",
    "minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel",
    "minecraft:bookshelf", "minecraft:lectern", "minecraft:loom",
    "minecraft:cartography_table", "minecraft:fletching_table",
    "minecraft:smithing_table", "minecraft:grindstone", "minecraft:stonecutter",
    "minecraft:anvil",
    # decor
    "minecraft:ladder", "minecraft:vine", "minecraft:flower_pot",
    "minecraft:hay_block",
    "minecraft:sign", "minecraft:oak_sign", "minecraft:spruce_sign",
    "minecraft:hanging_sign", "minecraft:oak_hanging_sign",
    # storage / misc
    "minecraft:barrel", "minecraft:composter",
}


def validate_structure(path: Path, registry: Set[str] = VANILLA_BLOCKS_1_21) -> List[str]:
    """Return a list of issue strings. Empty list = OK.

    Each issue is one line of human-readable text.
    """
    issues: List[str] = []
    path = Path(path)
    if not path.exists():
        return [f"file not found: {path}"]

    nbt = nbtlib.load(str(path))

    # Required top-level keys
    for required_key in ("size", "palette", "blocks", "palette_max"):
        if required_key not in nbt:
            issues.append(f"missing required key: {required_key}")

    # Palette entries — check block IDs
    palette = nbt.get("palette", nbtlib.List[Compound]())
    seen_blocks: Set[str] = set()
    for i, entry in enumerate(palette):
        name = str(entry["Name"]) if "Name" in entry else ""
        if not name:
            issues.append(f"palette[{i}]: missing Name")
            continue
        seen_blocks.add(name)

        if name not in registry:
            if name in KNOWN_RENAMES:
                new_name = KNOWN_RENAMES[name]
                issues.append(f"palette[{i}] {name}: renamed to {new_name} in 1.21")
            elif name in REMOVED_BLOCKS:
                issues.append(f"palette[{i}] {name}: removed in 1.21 (no replacement)")
            else:
                issues.append(f"palette[{i}] {name}: not in 1.21 vanilla registry")

    # blocks — check state indices in range, positions in bounds
    size = nbt.get("size", nbtlib.List[nbtlib.Int]())
    sx, sy, sz = int(size[0]), int(size[1]), int(size[2])
    palette_max = int(nbt.get("palette_max", len(palette)))
    blocks = nbt.get("blocks", nbtlib.List[Compound]())
    for i, block in enumerate(blocks):
        if "pos" not in block or len(block["pos"]) != 3:
            issues.append(f"blocks[{i}]: missing or invalid pos")
            continue
        pos = block["pos"]
        x, y, z = int(pos[0]), int(pos[1]), int(pos[2])
        if not (0 <= x < sx and 0 <= y < sy and 0 <= z < sz):
            issues.append(f"blocks[{i}] at ({x},{y},{z}): out of bounds size=({sx},{sy},{sz})")
        state = int(block.get("state", -1))
        if not (0 <= state < palette_max):
            issues.append(f"blocks[{i}] at ({x},{y},{z}): state={state} out of palette range [0, {palette_max})")

    return issues


def validate_path(path: Path, *, quiet: bool = False) -> int:
    """Validate a path (file or directory). Returns 0 if OK, 1 if issues found.

    Walks directories recursively looking for .nbt files.
    """
    path = Path(path)
    if path.is_file():
        files = [path]
    elif path.is_dir():
        files = sorted(path.rglob("*.nbt"))
    else:
        if not quiet:
            print(f"path not found: {path}", file=sys.stderr)
        return 1

    total_issues = 0
    files_with_issues = 0
    for f in files:
        issues = validate_structure(f)
        if issues:
            files_with_issues += 1
            total_issues += len(issues)
            if not quiet:
                print(f"\n{f}:")
                for issue in issues:
                    print(f"  {issue}")

    if not quiet:
        print(f"\nScanned {len(files)} files. {files_with_issues} with issues ({total_issues} total).")

    return 0 if files_with_issues == 0 else 1


if __name__ == "__main__":
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("common/src/main/resources/data/burg/structures")
    sys.exit(validate_path(target))