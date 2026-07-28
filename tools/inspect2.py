import gzip
from nbtlib import File
with gzip.open("tools/structures/out/manual/house.nbt", "rb") as f:
    nbt = File.parse(f)
palette = nbt["palette"]
blocks = nbt["blocks"]
for b in blocks:
    if int(b["pos"][1]) == 2 and int(b["pos"][2]) == 2:
        idx = int(b["state"])
        e = palette[idx]
        name = e["Name"].replace("minecraft:", "")
        print(f"x={int(b['pos'][0])}: {name}")
