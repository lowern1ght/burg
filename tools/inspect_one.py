import gzip
from nbtlib import File
with gzip.open("tools/structures/out/manual/house.nbt", "rb") as f:
    nbt = File.parse(f)
palette = nbt["palette"]
blocks = nbt["blocks"]
# Find what's at (6, 2, 2) and surrounding
for b in blocks:
    x, y, z = int(b["pos"][0]), int(b["pos"][1]), int(b["pos"][2])
    if y == 2 and 5 <= x <= 8 and 1 <= z <= 3:
        idx = int(b["state"])
        e = palette[idx]
        name = e["Name"].replace("minecraft:", "")
        props = {}
        if "Properties" in e:
            for k in e["Properties"]:
                props[k] = str(e["Properties"][k])
        print(f"({x}, {y}, {z}) = {name} {props}")
