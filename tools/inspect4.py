import gzip
from nbtlib import File
with gzip.open("tools/structures/out/manual/house.nbt", "rb") as f:
    nbt = File.parse(f)
palette = nbt["palette"]
blocks = nbt["blocks"]
print("All blocks at y=2 z=1:")
for b in blocks:
    if int(b["pos"][1]) == 2 and int(b["pos"][2]) == 1:
        idx = int(b["state"])
        e = palette[idx]
        name = e["Name"].replace("minecraft:", "")
        props = {}
        if "Properties" in e:
            for k in e["Properties"]:
                props[k] = str(e["Properties"][k])
        print(f"  x={int(b['pos'][0])}: {name} {props}")
