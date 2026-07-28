"""Can a player actually walk through this structure?

Every judgement in `critic.py` is about how a build *looks*. None of it asks
whether the thing works, and that turned out to be the gap that mattered: the
first wall set rendered well and was impassable. A solid corner bastion, a gate
blocked by its own flanking piers and a tower with no way up all passed the
style gate without complaint.

So this module models a walking player and answers three questions:

  `walkable(vox)`      which cells can be stood in
  `reachable(...)`     what can be reached from a starting cell
  `check_route(...)`   is there a route from A to B, and if not, why not

The movement rules follow Minecraft, and one of them is the whole point:

* A player occupies **two** cells of height, so a surface with only one clear
  cell above it is not walkable however solid it looks.
* Stepping **up half a block** — onto a bottom slab or a stair — needs no jump.
* Stepping up a **full** block does need a jump. The requirement is a route with
  no jumping, so a full-block rise is treated as a wall, not as a step.
* Fences, walls and fence gates are 1.5 blocks tall: they block movement and
  cannot be stood on. That is what makes them railings.
* Ladders are climbable in both directions, which is how a tower gets a top.
"""

from __future__ import annotations

from collections import deque
from dataclasses import dataclass, field
from typing import Dict, Iterable, List, Optional, Sequence, Set, Tuple

from .nbtio import BlockState, Coord, Voxels

# Blocks a player walks straight through.
PASSABLE = {
    "air", "cave_air", "void_air", "short_grass", "tall_grass", "grass",
    "fern", "large_fern", "dead_bush", "torch", "wall_torch", "soul_torch",
    "redstone_torch", "lantern", "chain", "rail", "tripwire", "vine",
    "sugar_cane", "wheat", "carrots", "potatoes", "beetroots", "melon_stem",
    "pumpkin_stem", "lily_pad", "snow", "light", "sunflower", "poppy",
    "dandelion", "cornflower", "azure_bluet", "oxeye_daisy", "allium",
    "blue_orchid", "white_tulip", "red_tulip", "pink_tulip", "orange_tulip",
    "lilac", "rose_bush", "peony", "sweet_berry_bush", "water",
}
# Climbable in both directions.
CLIMBABLE = {"ladder", "vine", "scaffolding", "twisting_vines", "weeping_vines"}
# 1.5 blocks tall: an obstacle you cannot stand on. This is the point of a rail.
RAILING_SUFFIX = ("_wall", "_fence", "_fence_gate")
# Open doors and trapdoors are walk-through; closed ones are not.
DOORS = ("_door", "_trapdoor", "_gate")

BLOCK_HEIGHT = 1.0
SLAB_HEIGHT = 0.5


def _short(b: Optional[BlockState]) -> str:
    return "" if b is None else b.short


def is_passable(b: Optional[BlockState]) -> bool:
    """True if a player's body can occupy the same cell."""
    if b is None:
        return True
    n = b.short
    if n in PASSABLE or n in CLIMBABLE:
        return True
    if n.endswith("_leaves"):
        return False               # leaves are solid to walk into
    if n.endswith("_door"):
        # A wooden door counts as passable even when closed: a player — and a
        # villager — can open it, so it is a doorway, not a wall. Iron needs a
        # signal, so it stays shut.
        return not n.startswith("iron_")
    if n.endswith("_fence_gate"):
        return True
    if n.endswith("_trapdoor") and b.get("open") == "true":
        # An open trapdoor stands vertical: passable to walk through, though it
        # is really a wall on one side. Close enough for routing.
        return True
    if n == "jigsaw":
        return True                # replaced by final_state on placement
    return False


def surface(vox: Voxels, p: Coord) -> Optional[float]:
    """Elevation of the walkable top of the block at `p`, or None if you
    cannot stand on it."""
    b = vox.get(p)
    if b is None:
        return None
    n = b.short
    if n == "jigsaw":
        # A connector is not what stands there in the world: placement replaces
        # it with its `final_state`, which is `dirt_path` everywhere in this
        # repo. Treating it as unstandable made the cell *above* a connector a
        # non-node, and since every entry connector sits directly in front of
        # its gate, that removed the only orthogonal approach to the gate —
        # routes failed on a doorway that is walkable in the actual game.
        final = (vox.block_nbt.get(p) or {}).get("final_state")
        if final is not None and "air" in str(final):
            return None
        return p[1] + BLOCK_HEIGHT
    if n in PASSABLE or n in CLIMBABLE:
        return None
    if n.endswith(RAILING_SUFFIX):
        return None                # too tall to step onto
    if n.endswith("_slab"):
        t = b.get("type")
        if t == "bottom":
            return p[1] + SLAB_HEIGHT
        return p[1] + BLOCK_HEIGHT          # top and double are full height
    if n.endswith("_stairs"):
        # Standing on a stair puts you on its upper half.
        return p[1] + BLOCK_HEIGHT
    if n.endswith(("_trapdoor", "_door")):
        return None
    if n.endswith("_leaves"):
        return p[1] + BLOCK_HEIGHT
    if n.endswith("_carpet") or n == "snow":
        return None
    return p[1] + BLOCK_HEIGHT


def standable(vox: Voxels, p: Coord) -> Optional[float]:
    """If a player can occupy cell `p`, the height their feet are at.

    Two cells of clearance, because a player is two blocks tall. This is the
    check a one-block-headroom walkway fails.
    """
    if not is_passable(vox.get(p)):
        return None
    above = (p[0], p[1] + 1, p[2])
    if not is_passable(vox.get(above)):
        return None
    # Hanging on a ladder needs no floor. Requiring one made every ladder cell
    # a non-node, so a tower with a perfectly good ladder in it measured as
    # having no way up — the ladder was there and the graph could not see it.
    if _short(vox.get(p)) in CLIMBABLE:
        return float(p[1])
    s = surface(vox, (p[0], p[1] - 1, p[2]))
    if s is None:
        return None
    return s


def walkable(vox: Voxels) -> Dict[Coord, float]:
    """Every cell a player can stand in, mapped to its surface height."""
    sx, sy, sz = vox.size
    out: Dict[Coord, float] = {}
    for x in range(sx):
        for z in range(sz):
            for y in range(1, sy):
                s = standable(vox, (x, y, z))
                if s is not None:
                    out[(x, y, z)] = s
    return out


def _climb_links(vox: Voxels, p: Coord) -> Iterable[Coord]:
    """A ladder lets you move straight up or down."""
    if _short(vox.get(p)) in CLIMBABLE:
        yield (p[0], p[1] + 1, p[2])
        yield (p[0], p[1] - 1, p[2])


def _allowed_rise(vox: Voxels, target: Coord, base: float) -> float:
    """How far you may climb to stand in `target`, in blocks.

    Half a block normally. A **stair** allows a full block, because you step
    onto its lower half first and then walk up its upper half — which is the
    whole reason a staircase of stairs is walkable and a staircase of full
    blocks is not. Getting this wrong would have made every internal stair
    look impassable and pushed the design towards ladders everywhere.
    """
    sup = vox.get((target[0], target[1] - 1, target[2]))
    if sup is not None and sup.short.endswith("_stairs"):
        return BLOCK_HEIGHT
    return base


def neighbours(vox: Voxels, cells: Dict[Coord, float],
               p: Coord, max_rise: float = SLAB_HEIGHT) -> List[Coord]:
    """Cells reachable from `p` in one step, without jumping."""
    out: List[Coord] = []
    here = cells[p]
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        for dy in (0, 1, -1, -2, -3):
            q = (p[0] + dx, p[1] + dy, p[2] + dz)
            if q not in cells:
                continue
            rise = cells[q] - here
            # Falling is free; climbing is limited by what you step onto.
            if rise > _allowed_rise(vox, q, max_rise) + 1e-6:
                continue
            if rise < -3.0:
                continue                    # too far to drop safely
            out.append(q)
            break
    for q in _climb_links(vox, p):
        if q in cells:
            out.append(q)
    # A ladder in the cell above or below links vertically too.
    for dy in (1, -1):
        q = (p[0], p[1] + dy, p[2])
        if q in cells and _short(vox.get(q)) in CLIMBABLE:
            out.append(q)
    return out


def reachable(vox: Voxels, start: Sequence[Coord],
              max_rise: float = SLAB_HEIGHT) -> Set[Coord]:
    """Flood fill from `start` over walkable cells."""
    cells = walkable(vox)
    seen: Set[Coord] = set()
    q = deque(p for p in start if p in cells)
    seen.update(q)
    while q:
        p = q.popleft()
        for n in neighbours(vox, cells, p, max_rise):
            if n not in seen:
                seen.add(n)
                q.append(n)
    return seen


@dataclass
class Route:
    ok: bool
    reason: str = ""
    reached: Set[Coord] = field(default_factory=set)

    def __bool__(self) -> bool:
        return self.ok


def check_route(vox: Voxels, start: Sequence[Coord],
                goal: Sequence[Coord], label: str = "") -> Route:
    """Is any goal cell reachable from any start cell without jumping?"""
    cells = walkable(vox)
    live_start = [p for p in start if p in cells]
    live_goal = [p for p in goal if p in cells]
    if not live_start:
        return Route(False, f"{label}: no standable start cell among "
                            f"{list(start)[:4]}")
    if not live_goal:
        return Route(False, f"{label}: no standable goal cell among "
                            f"{list(goal)[:4]}")
    seen = reachable(vox, live_start)
    hit = [p for p in live_goal if p in seen]
    if hit:
        return Route(True, f"{label}: reachable", seen)
    return Route(False, f"{label}: {len(live_goal)} standable goal cell(s) but "
                        f"none reachable from the start", seen)


def column_top(vox: Voxels, x: int, z: int) -> int:
    """Highest occupied y in a column, or -1."""
    best = -1
    for y in range(vox.size[1]):
        if vox.occupied((x, y, z)):
            best = y
    return best
