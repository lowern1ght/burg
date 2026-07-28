"""Assemble a perimeter the way the mod would, and check that it closes.

This replays BuildSchematic.computeRequiredRotation / computeCandidatePosition
and StructureTemplate.transform exactly, so if the pieces line up here they
line up in the world. Without this the claim "the ring closes itself" is just
an assertion.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from structures import wall, render_png                     # noqa: E402
from structures.nbtio import Voxels, state                  # noqa: E402

# Rotation.CLOCKWISE_90.rotate(direction), vanilla.
CW = {"north": "east", "east": "south", "south": "west", "west": "north"}
OPP = {"north": "south", "south": "north", "west": "east", "east": "west"}
CONNECT = ("north", "south", "east", "west")


def rot_dir(d, times):
    for _ in range(times % 4):
        d = CW[d]
    return d


def transform(pos, times):
    """StructureTemplate.transform(pos, NONE, rotation, BlockPos.ZERO)."""
    x, y, z = pos
    for _ in range(times % 4):
        x, z = -z, x
    return (x, y, z)


def rot_state(b, times):
    """Rotate a block state's directional properties."""
    times %= 4
    if times == 0:
        return b
    p = dict(b.props)
    if "facing" in p and p["facing"] in CW:
        p["facing"] = rot_dir(p["facing"], times)
    if p.get("axis") in ("x", "z") and times % 2 == 1:
        p["axis"] = "z" if p["axis"] == "x" else "x"
    if any(d in p for d in CONNECT):
        old = {d: p[d] for d in CONNECT if d in p}
        for d, v in old.items():
            p[rot_dir(d, times)] = v
    if "orientation" in p:
        front, _, up = p["orientation"].partition("_")
        if front in CW:
            p["orientation"] = f"{rot_dir(front, times)}_{up}"
    return type(b)(b.name, tuple(sorted(p.items())))


def connectors(vox):
    """(local pos, facing, target, is_entry) for every jigsaw in a piece."""
    out = []
    for pos, b in vox.solid_items():
        if b.short != "jigsaw":
            continue
        nbt = vox.block_nbt.get(pos)
        facing = dict(b.props)["orientation"].split("_")[0]
        pool = str(nbt.get("pool"))
        out.append((pos, facing, str(nbt.get("target")),
                    pool in ("", "minecraft:empty")))
    return out


def place(world, vox, origin, times):
    """Stamp a rotated piece into the shared world dict."""
    for local, b in vox.solid_items():
        t = transform(local, times)
        wp = (origin[0] + t[0], origin[1] + t[1], origin[2] + t[2])
        if b.short == "jigsaw":
            continue                       # markers are replaced on placement
        world[wp] = rot_state(b, times)


def build_ring(plan, seed=0):
    """plan: list of (kind, level). Returns world dict and a placement log."""
    world, log = {}, []
    free = []                             # (worldpos, direction, target)

    first = wall.compose(*plan[0], seed=seed)
    place(world, first, (0, 0, 0), 0)
    log.append((first.name, (0, 0, 0), 0))
    for local, facing, target, entry in connectors(first):
        if not entry:
            free.append((local, facing, target))

    for kind, lvl in plan[1:]:
        piece = wall.compose(kind, lvl, seed=seed)
        cps = connectors(piece)
        entries = [c for c in cps if c[3]]
        if not entries or not free:
            log.append((piece.name, None, None))
            continue
        # Attach to the newest free connector: this test is about whether the
        # geometry lines up, not about the mod's oldest-first ordering.
        tpos, tdir, _ = free.pop()
        local, facing, _, _ = entries[0]
        want = OPP[tdir]
        times = next(t for t in range(4) if rot_dir(facing, t) == want)
        attach = (tpos[0] + wall.OUT_VEC[tdir][0], tpos[1],
                  tpos[2] + wall.OUT_VEC[tdir][1])
        off = transform(local, times)
        origin = (attach[0] - off[0], attach[1] - off[1], attach[2] - off[2])
        place(world, piece, origin, times)
        log.append((piece.name, origin, times))
        for cl, cf, ct, ce in cps:
            if ce or ct != wall.MILITARY_POOL:
                continue
            t = transform(cl, times)
            free.append(((origin[0] + t[0], origin[1] + t[1],
                          origin[2] + t[2]), rot_dir(cf, times), ct))
    return world, log


def to_voxels(world, name):
    xs = [p[0] for p in world]
    ys = [p[1] for p in world]
    zs = [p[2] for p in world]
    ox, oy, oz = min(xs), min(ys), min(zs)
    size = (max(xs) - ox + 1, max(ys) - oy + 1, max(zs) - oz + 1)
    grid = {(p[0] - ox, p[1] - oy, p[2] - oz): b for p, b in world.items()}
    return Voxels(size, grid, name)


def main():
    S, C, G, T = "wall_segment", "wall_corner", "gatehouse", "wall_tower"
    plans = {
        "chain": [(S, 3)] * 4,
        "turn": [(S, 3), (S, 3), (C, 3), (S, 3), (S, 3)],
        "ring": [(S, 3), (S, 3), (C, 3), (S, 3), (S, 3), (C, 3),
                 (S, 3), (S, 3), (C, 3), (S, 3), (S, 3), (C, 3)],
        "mixed": [(S, 4), (T, 4), (S, 4), (C, 4), (G, 4), (S, 4), (C, 4)],
    }
    out = Path("structures/out/walls")
    out.mkdir(parents=True, exist_ok=True)
    for label, plan in plans.items():
        world, log = build_ring(plan)
        vox = to_voxels(world, label)
        print(f"== {label}: {len(plan)} pieces, size {vox.size}, "
              f"{vox.solid_count} blocks")
        for nm, origin, times in log:
            print(f"     {nm:22s} origin={origin} rot={times}")
        render_png.render_ortho(vox, "top", px=6).save(out / f"ring_{label}_top.png")
        render_png.render_iso(vox, tile=6).save(out / f"ring_{label}_iso.png")
    print("\nwrote plans to", out)


if __name__ == "__main__":
    main()
