"""Slice and print a NBT file as ASCII art."""
import sys, gzip
from pathlib import Path
from collections import defaultdict
from nbtlib import File

def name(p): return p["Name"].replace("minecraft:", "")

GLYPH = {
    "air": " ", "grass_block": "g", "dirt": "d", "dirt_path": "p",
    "coarse_dirt": "c", "oak_leaves": "l", "spruce_leaves": "L",
    "oak_planks": "P", "spruce_planks": "S", "cobblestone": "C",
    "stone_bricks": "B", "deepslate_bricks": "b",
    "stone": "s", "short_grass": "'",
}
LOG_GLYPH = {"y": "|", "x": "=", "z": "~"}
STAIR_TOP = {"north": "^", "south": "v", "east": ">", "west": "<"}
STAIR_BOT = {"north": "A", "south": "B", "east": "C", "west": "D"}
SLAB_TOP, SLAB_BOT = "=", "_"
SPECIAL = {
    "glass_pane": "G", "iron_bars": "I", "oak_fence": "F",
    "spruce_fence": "f", "stone_brick_wall": "W",
    "oak_door": "D", "spruce_door": "d", "iron_door": "D",
    "oak_trapdoor": "X", "spruce_trapdoor": "x", "iron_trapdoor": "X",
    "lantern": "L", "wall_torch": "T", "torch": "t",
    "crafting_table": "W", "furnace": "F", "white_bed": "B",
    "white_carpet": "C", "flower_pot": "P", "jigsaw": "J",
    "ladder": "L", "item_frame": "[",
}

def glyph(p):
    n = name(p)
    pr = {}
    if "Properties" in p:
        for k in p["Properties"]:
            pr[k] = str(p["Properties"][k])
    if n == "air": return " "
    if "slab" in n and "double" not in n:
        t = pr.get("type", "bottom")
        return SLAB_TOP if t == "top" else SLAB_BOT
    if "stairs" in n and "slab" not in n:
        f = pr.get("facing", "north")
        h = pr.get("half", "bottom")
        if h == "top":
            return STAIR_TOP.get(f, "?")
        return STAIR_BOT.get(f, "?")
    if "log" in n:
        return LOG_GLYPH.get(pr.get("axis", "y"), "|")
    if n in SPECIAL: return SPECIAL[n]
    if n in GLYPH: return GLYPH[n]
    return n[0].upper() if n else "?"


def slice_file(path):
    with gzip.open(path, "rb") as fh:
        nbt = File.parse(fh)
    palette = nbt["palette"]
    blocks = nbt["blocks"]
    sx = int(nbt["size"][0]); sy = int(nbt["size"][1]); sz = int(nbt["size"][2])
    print(f"=== {path} ({sx}x{sy}x{sz}, {len(blocks)} blocks, palette={len(palette)}) ===")
    for y in range(sy):
        grid = {}
        for b in blocks:
            if int(b["pos"][1]) == y:
                grid[(int(b["pos"][0]), int(b["pos"][2]))] = palette[int(b["state"])]
        print(f"--- Y={y} ---")
        for z in range(sz):
            line = ""
            for x in range(sx):
                line += glyph(grid[(x, z)]) if (x, z) in grid else "."
            print(f"  z={z:2d}: {line}")


if __name__ == "__main__":
    for arg in sys.argv[1:]:
        slice_file(arg)
