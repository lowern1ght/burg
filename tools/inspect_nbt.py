"""Inspect a settlement.nbt file to see its palette and jigsaw connectors."""
import nbtlib
import gzip
import io
from pathlib import Path

p = Path("common/src/main/resources/data/onceuponatown/structures/plains/starters/settlement.nbt")
data = p.read_bytes()
nbt = nbtlib.File.parse(io.BytesIO(gzip.decompress(data)))

print("=== FULL PALETTE ({} entries) ===".format(len(nbt["palette"])))
for i, entry in enumerate(nbt["palette"]):
    name = entry["Name"]
    print("  [{:2d}] {}".format(i, name))

print()
print("=== Block counts ===")
counts = {}
for b in nbt["blocks"]:
    s = int(b.get("state", -1))
    name = str(nbt["palette"][s]["Name"]) if 0 <= s < len(nbt["palette"]) else "?"
    counts[name] = counts.get(name, 0) + 1
for name, c in sorted(counts.items(), key=lambda x: -x[1]):
    print("  {:4d}  {}".format(c, name))

print()
print("=== jigsaw positions ===")
jigsaw_count = 0
for b in nbt["blocks"]:
    s = int(b.get("state", -1))
    name = str(nbt["palette"][s]["Name"]) if 0 <= s < len(nbt["palette"]) else ""
    if "jigsaw" in name:
        jigsaw_count += 1
        pos = b.get("pos")
        # In MC structure format, jigsaw config lives in the nested 'nbt' field
        inner = b.get("nbt", None)
        if inner is not None and pos is not None:
            cfg = {}
            for k in ["name", "target", "pool", "final_state", "joint"]:
                if k in inner:
                    cfg[k] = str(inner[k])
            print("  pos={} cfg={}".format(list(pos), cfg))
        elif pos is not None:
            print("  pos={} NO NESTED nbt".format(list(pos)))
print("Total jigsaw blocks: {}".format(jigsaw_count))