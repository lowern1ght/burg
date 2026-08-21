"""Generate empty5x5.snbt fixture for BurgGameTests.

SNBT format: zlib-compressed NBT (raw deflate, no gzip header).
Root is a TAG_Compound (no name) with:
- 'size': TAG_List(TAG_Int) [x, y, z] dimensions
- 'palette': TAG_List(TAG_Int) [block state ids]
- 'blocks': TAG_List(TAG_Compound) of {'pos': TAG_List(TAG_Int) [3], 'state': TAG_Int}
- 'entities': empty TAG_List
- TAG_End

The 5x5x1 block is all minecraft:stone (state id 1) at Y=0 (the gametest default
spawn height is a level above the platform).
"""
import os
import struct
import zlib


def _tag(tag_id: int, name: str = "") -> bytes:
    """Return a NBT tag header (id + name string)."""
    encoded = name.encode("utf-8") if isinstance(name, str) else name
    return struct.pack(">b", tag_id) + struct.pack(">H", len(encoded)) + encoded


def _tag_end() -> bytes:
    return b"\x00"


def _name(name: str) -> bytes:
    """A named-tag header: TAG_String (0x08) with the name (for keyed children)."""
    encoded = name.encode("utf-8")
    return struct.pack(">b", 0x08) + struct.pack(">H", len(encoded)) + encoded


def _int(v: int) -> bytes:
    return struct.pack(">b", 0x03) + struct.pack(">i", v)


def _short(v: int) -> bytes:
    return struct.pack(">b", 0x02) + struct.pack(">h", v)


def _long(v: int) -> bytes:
    return struct.pack(">b", 0x04) + struct.pack(">q", v)


def build_empty5x5() -> bytes:
    """Return zlib-compressed NBT bytes for a 5x5x1 platform of minecraft:stone."""
    root = b""
    # Root compound (no name)
    root += struct.pack(">b", 0x0A)  # TAG_Compound
    root += struct.pack(">H", 0)      # empty name
    # 'size' (TAG_List of Tag.Int) — 3 ints
    root += _name("size")
    root += struct.pack(">b", 0x09)  # TAG_List
    root += struct.pack(">b", 0x03)  # element tag id: Int
    root += struct.pack(">i", 3)     # count
    root += struct.pack(">i", 5)     # x
    root += struct.pack(">i", 1)     # y
    root += struct.pack(">i", 5)     # z
    # 'palette' (TAG_List of Tag.Int) — 1 entry, stone=1
    root += _name("palette")
    root += struct.pack(">b", 0x09)
    root += struct.pack(">b", 0x03)
    root += struct.pack(">i", 1)
    root += struct.pack(">i", 1)  # minecraft:stone = 1
    # 'blocks' (TAG_List of Tag.Compound) — 25 entries
    root += _name("blocks")
    root += struct.pack(">b", 0x09)
    root += struct.pack(">b", 0x0A)  # element: Compound
    root += struct.pack(">i", 25)
    for x in range(5):
        for y in range(1):
            for z in range(5):
                # inner Compound: {pos: [x,y,z], state: 1}
                root += struct.pack(">b", 0x0A)  # Compound
                # 'pos' (TAG_List of Int)
                root += _name("pos")
                root += struct.pack(">b", 0x09)
                root += struct.pack(">b", 0x03)
                root += struct.pack(">i", 3)
                root += struct.pack(">i", x)
                root += struct.pack(">i", y)
                root += struct.pack(">i", z)
                # 'state' (TAG_Int)
                root += _name("state")
                root += _int(1)
                root += _tag_end()  # close inner compound
    # close 'blocks' list
    root += _tag_end()
    # 'entities' (TAG_List of Compound) — empty
    root += _name("entities")
    root += struct.pack(">b", 0x09)
    root += struct.pack(">b", 0x0A)
    root += struct.pack(">i", 0)
    root += _tag_end()
    # close root compound
    root += _tag_end()
    return zlib.compress(root)


def main() -> None:
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    if os.path.basename(repo_root) == ".agents":
        repo_root = os.path.dirname(repo_root)
    out_dir = os.path.join(
        repo_root,
        "common",
        "src",
        "main",
        "resources",
        "data",
        "burg",
        "structure",
    )
    os.makedirs(out_dir, exist_ok=True)
    out_path = os.path.join(out_dir, "empty5x5.snbt")
    with open(out_path, "wb") as f:
        f.write(build_empty5x5())
    print(f"wrote {out_path} ({os.path.getsize(out_path)} bytes)")


if __name__ == "__main__":
    main()
