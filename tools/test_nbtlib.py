"""Test the nbtlib: build a small house, save, slice, check it looks like
carpenter.nbt in spirit (cornice, FPF pattern, ground, etc).
"""
import sys
sys.path.insert(0, "tools")
from structure_builder import StructureBuilder
import gzip
from pathlib import Path
from collections import Counter
from nbtlib import File

# Build a 9x9 house. The total Y is:
#  Y=0 ground
#  Y=1 wall_ground (with horizontal beams)
#  Y=2 wall_upper_row
#  Y=3 cornice
#  Y=4 roof_body
#  Y=5 roof_cap
# total = 6 layers
nb = StructureBuilder((11, 6, 11))

# Materials: oak. Materials are baked into the shortcuts (oak_planks, oak_log,
# etc). To switch era, just use a different shortcut (spruce_planks,
# cobble, stone_bricks).

# Origin = (1, 0, 1) so the building is at x=1..9, y=0..5, z=1..9
origin = (1, 0, 1)
width, depth = 9, 9
door_x = 5  # centre of 9-wide building

# ── Y=0: ground
nb.ground_pad(origin, width=width, depth=depth)

# ── Y=1: ground-floor wall row. The horizontal beams at front and back.
nb.log_wall_row(y=1, origin=origin, width=width, depth=depth, door_x=door_x)

# ── Y=2: upper wall row, same pattern
nb.log_wall_row(y=2, origin=origin, width=width, depth=depth, door_x=door_x)

# ── Door (2 blocks) at the front, centred
nb.door((door_x, 1, origin[2] + 1), facing="north")

# ── Interior: bed, crafting table, lantern
nb.bed((2, 2, 2), part="head", facing="south")
nb.bed((2, 2, 3), part="foot", facing="south")
nb.table((8, 2, 8))
nb.lantern((8, 3, 2))
nb.carpet((5, 2, 5))
nb.wall_torch((2, 2, 6), facing="north")  # NB: this places at (x,y,z), facing north means attached to south wall

# ── Y=3: cornice (chair-rail + FPF + cornice overhang)
nb.cornice_row(y=3, origin=origin, width=width, depth=depth, door_x=door_x)

# ── Y=4: roof body
nb.roof_body_row(y=4, origin=origin, width=width, depth=depth)

# ── Y=5: roof cap
nb.roof_cap(y=5, origin=origin, width=width, depth=depth)

# ── Save
out = Path("tools/structures/out/manual")
out.mkdir(parents=True, exist_ok=True)
nb.save(str(out / "house_v5.nbt"))
print(f"Saved: {out / 'house_v5.nbt'} ({len(nb.blocks)} blocks, palette={len(nb._palette_list)})")

# Now slice and print, to verify the Y layers look like carpenter
def name(p): return p["Name"].replace("minecraft:", "")

print()
print("=== Y=1 (wall_ground) ===")
with gzip.open(out / "house_v5.nbt", "rb") as fh:
    nbt = File.parse(fh)
palette = nbt["palette"]
blocks = nbt["blocks"]
sx = int(nbt["size"][0]); sy = int(nbt["size"][1]); sz = int(nbt["size"][2])

def glyph(entry):
    n = name(entry)
    pr = {}
    if "Properties" in entry:
        for k in entry["Properties"]:
            pr[k] = str(entry["Properties"][k])
    if n == "air": return " "
    if "slab" in n:
        t = pr.get("type", "bottom")
        return "=" if t == "top" else "_"
    if "log" in n:
        return "|" if pr.get("axis") == "y" else "=" if pr.get("axis") == "x" else "~"
    if n == "oak_planks": return "P"
    if "fence" in n: return "F"
    if "leaves" in n: return "l"
    if "coarse_dirt" in n: return "c"
    if "dirt_path" in n: return "p"
    if "grass" in n: return "g"
    if n == "dirt": return "d"
    if n == "torch": return "t"
    if n == "wall_torch": return "T"
    if n == "lantern": return "L"
    if n == "crafting_table": return "W"
    if "bed" in n: return "B"
    if "carpet" in n: return "C"
    if "door" in n: return "D"
    if "trapdoor" in n: return "X"
    if "jigsaw" in n: return "J"
    return n[0].upper()

for y in range(sy):
    grid = {(int(b["pos"][0]), int(b["pos"][2])): palette[int(b["state"])] for b in blocks if int(b["pos"][1]) == y}
    print(f"--- Y={y} ---")
    for z in range(sz):
        line = ""
        for x in range(sx):
            line += glyph(grid[(x, z)]) if (x, z) in grid else "."
        print(f"  z={z:2d}: {line}")
