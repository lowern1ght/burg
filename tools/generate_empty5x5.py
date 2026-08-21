"""Generate empty5x5.nbt fixture for BurgGameTests.

The structure resource path is `data/burg/structure/<X>.nbt` where `<X>` is
the test's structure name. NeoForge's GameTestRegistry prefixes the
class simple-name (lowercased) to the template when prefixGameTestTemplate
is enabled (default), so {@code @GameTestHolder("burg")} +
{@code @GameTest(template = "empty5x5")} on a class named {@code BurgGameTests}
resolves the resource location {@code burg:burggametests.empty5x5} → file
{@code data/burg/structure/burggametests.empty5x5.nbt}.

StructureTemplate format (vanilla 1.21.1, read by StructureTemplateManager via
`new FileToIdConverter("structure", ".nbt")` -> `data/<ns>/structure/<path>.nbt`):

  Root TAG_Compound (no name):
    size    : TAG_List(TAG_Int)          [x=5, y=1, z=5]
    palette : TAG_List(TAG_Compound)     [ {Name: "minecraft:stone"} ]   -- BlockState NBT
    blocks  : TAG_List(TAG_Compound)     [ {pos: [x,y,z], state: 0} ] -- state is palette INDEX
    entities: TAG_List (empty)

Compression: gzip (NbtIo.readCompressed wraps a GZIPInputStream — zlib/DEFLATE
without a gzip header is rejected).

The 5x5x1 block is all minecraft:stone. The gametest default spawn height is a
level above the platform (y=1), so the player lands on y=1 above the platform
top.

Round-tripped by EmptyFixtureTest:
  - byte count (pinned)
  - gzip magic header (1F 8B)
  - decoded root tag id (TAG_Compound = 0x0A) and zero-length name
  - palette + block count
"""
import gzip
import os
import struct


# The test class is `BurgGameTests`; the prefix is the lowercased simple
# class name (`burggametests.`). The `@GameTest(template = "empty5x5")`
# annotation supplies the second half. Together they form the structure
# name and the on-disk resource path.
TEST_CLASS_SIMPLE = "BurgGameTests"
TEMPLATE_NAME = "empty5x5"
STRUCTURE_FILE_NAME = TEST_CLASS_SIMPLE.lower() + "." + TEMPLATE_NAME + ".nbt"


# NBT tag type ids (https://minecraft.wiki/w/NBT_format)
TAG_END = 0x00
TAG_INT = 0x03
TAG_STRING = 0x08
TAG_LIST = 0x09
TAG_COMPOUND = 0x0A


def _short(v: int) -> bytes:
    """Unsigned big-endian 16-bit (NBT string length, List payload length)."""
    return struct.pack(">H", v)


def _int(v: int) -> bytes:
    return struct.pack(">i", v)


def _named_child(tag_type: int, name: str, value: bytes) -> bytes:
    """A NAMED child tag: type-byte + uint16 name-length + utf-8 name + value bytes.

    This is what every child of a TAG_Compound looks like. The {@code tag_type}
    is the type of the *value* (e.g. 0x09 for TAG_List, 0x03 for TAG_Int),
    NOT a fixed header marker — there's no special "name" tag id; the child
    header just declares what follows.
    """
    encoded = name.encode("utf-8")
    return struct.pack(">b", tag_type) + _short(len(encoded)) + encoded + value


def _root_compound_open() -> bytes:
    """The root of a StructureTemplate is an UNNAMED TAG_Compound:
    `0x0A` (Compound type) + `0x00 0x00` (name length = 0). No body yet —
    children get appended separately, then the file ends with a TAG_End
    (single 0x00 byte) to close the Compound.
    """
    return struct.pack(">b", TAG_COMPOUND) + _short(0)


def _block_state(block_id: str) -> bytes:
    """A palette entry: an UNNAMED Compound with one NAMED TAG_String
    child `{Name: "<id>"}`. No Properties for default stone.

    Called as an element of a TAG_List of TAG_Compound, so this payload
    is JUST the Compound body — no Compound-type byte (the list's
    element-type byte already declared these are Compounds) and no
    Compound-name field (an unnamed Compound has no name). The payload
    ends with a TAG_End (single 0x00 byte); without it the parser would
    keep reading into the next list element.
    """
    name_string_value = _short(len(block_id.encode("utf-8"))) + block_id.encode("utf-8")
    inner_compound_body = (
        # NAMED child: TAG_String (type 0x08) named "Name", value <id>.
        struct.pack(">b", TAG_STRING) + _short(4) + b"Name"
        + name_string_value
        + b"\x00"  # close the BlockState Compound
    )
    return inner_compound_body


def _int_list(values: list[int]) -> bytes:
    """The VALUE of a TAG_List that holds TAG_Int entries: 0x03 (element
    type Int) + int32 count + int32 values. The TAG_List header byte
    itself (0x09) is NOT here — it's the child tag's type byte emitted by
    {@link #_named_child}.
    """
    out = struct.pack(">b", TAG_INT) + _int(len(values))
    for v in values:
        out += _int(v)
    return out


def build_empty5x5() -> bytes:
    """Return gzip-compressed NBT bytes for a 5x5x1 platform of minecraft:stone."""
    root = _root_compound_open()

    # size: TAG_List of 3 ints [5, 1, 5]
    root += _named_child(TAG_LIST, "size", _int_list([5, 1, 5]))

    # palette: TAG_List of 1 BlockState Compound. The TAG_List header
    # byte is emitted by _named_child (TAG_LIST type byte); the value here
    # is just the List payload: element-type Compound, count, then each
    # Compound entry.
    palette_list_value = (
        struct.pack(">b", TAG_COMPOUND)
        + _int(1)
        + _block_state("minecraft:stone")
    )
    root += _named_child(TAG_LIST, "palette", palette_list_value)

    # blocks: TAG_List of 25 unnamed Compounds {pos: [x,y,z], state: 0}.
    # Each element of a TAG_List of Compound is JUST a Compound body
    # (NAMED children + a trailing TAG_End) — no Compound-type byte or
    # Compound-name field, because the list's element-type byte already
    # declared "every element is a Compound" and Compound elements in a
    # list are unnamed.
    blocks_payload = (
        struct.pack(">b", TAG_COMPOUND)  # list element type
        + _int(25)                        # list count
    )
    for x in range(5):
        for y in range(1):
            for z in range(5):
                block_body = b""
                block_body += _named_child(TAG_LIST, "pos", _int_list([x, y, z]))
                block_body += _named_child(TAG_INT, "state", _int(0))
                block_body += b"\x00"  # close the block Compound
                blocks_payload += block_body
    root += _named_child(TAG_LIST, "blocks", blocks_payload)

    # entities: empty TAG_List
    entities_payload = (
        struct.pack(">b", TAG_COMPOUND) + _int(0)
    )
    root += _named_child(TAG_LIST, "entities", entities_payload)

    # close root compound
    root += b"\x00"

    return gzip.compress(root)


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

    # Generate BOTH the prefixed (`burg:burggametests.empty5x5`, used when
    # NeoForge's `prefixGameTestTemplate` hook returns true — the default
    # behaviour per {@link GameTestHooks#prefixGameTestTemplate}) and the
    # unprefixed (`burg:empty5x5`) variants. The framework's behaviour
    # has shifted between NeoForge patch versions, so we ship both and
    # let the framework pick whichever it queries; the unused one is
    # dead weight at < 200 bytes. The class-name prefix is the
    # lowercased simple class name (here `BurgGameTests` →
    # `burggametests.`) per GameTestRegistry.turnMethodIntoTestFunction.
    data = build_empty5x5()
    for filename in (STRUCTURE_FILE_NAME, TEMPLATE_NAME + ".nbt"):
        out_path = os.path.join(out_dir, filename)
        with open(out_path, "wb") as f:
            f.write(data)
        print(f"wrote {out_path} ({len(data)} bytes)")

    # Remove the legacy `empty5x5.snbt` from the empty-fixture PR — the
    # GameTest framework only reads `.nbt` files in the singular
    # `structure/` folder (new FileToIdConverter("structure", ".nbt")
    # .idToFile). SNBT text parsing only fires for the IDE-only
    # `loadFromTestStructures` path; resource packs always use `.nbt`.
    legacy = os.path.join(out_dir, "empty5x5.snbt")
    if os.path.exists(legacy):
        os.remove(legacy)
        print(f"removed stale {legacy}")


if __name__ == "__main__":
    main()
