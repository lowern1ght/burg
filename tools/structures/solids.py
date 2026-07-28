"""What volume a block actually occupies — one source of truth.

`appearance.shape_of` already knows that a slab is half a cell and a stair is a
base plus a quarter step, because the renderer needs it to draw. The **writer**
did not, and every geometric bug in this repo's history came from that gap:

* a leaf placed "on" a bottom slab, hanging half a block clear of it — 38 of them
  in one commit, against 6 in the author's whole 121-file corpus and all of his
  side-attached;
* an eave slab written into the cell past the posts, resting on nothing;
* a lean-to pitched backwards, because a roof was reasoned about as a stack of
  cells rather than as a plane of shapes;
* a post cap under a rail, a combination he never builds.

So the predicates a builder needs live here, derived from the same shape model the
renderer draws from. Nothing hand-lists block ids.
"""

from __future__ import annotations

from typing import Optional

from .appearance import shape_of
from .nbtio import BlockState

# Sub-cell heights used by the shape model, in cell units.
PLATE_H = 0.14
FLAT_H = 0.09


def fills_cell(b: Optional[BlockState]) -> bool:
    """A whole cube: something can be placed on it, or against it, safely."""
    if b is None:
        return False
    return shape_of(b)[0] == "full"


def top_face(b: Optional[BlockState]) -> Optional[float]:
    """Height of the block's own top surface within its cell, or None if it has no
    usable one. 1.0 means the cell is filled to the top."""
    if b is None:
        return None
    kind, param = shape_of(b)
    if kind == "full":
        return 1.0
    if kind == "slab":
        return 1.0 if param == "top" else 0.5
    if kind == "stairs":
        return 1.0                      # its high half reaches the top
    if kind == "plate":
        return 1.0 if param == "top" else PLATE_H
    if kind == "flat":
        return FLAT_H
    if kind in ("post", "door"):
        return 1.0                      # full height, but not full width
    return None                         # tiny, plant: nothing rests on these


def carries_above(b: Optional[BlockState]) -> bool:
    """Can something be placed in the cell above this one without a visible gap?

    The question is **height only**: does this block reach the top of its own cell.
    Measured over his 121 readable files, footprint does not enter into it — he puts
    a cube over a fence post 370 times in 86 files and a fence over a fence 743
    times in 99, because a post fills its cell vertically even though it is thin.
    What he almost never does is bridge a *vertical* gap: 15 cubes and 1 rail over a
    half-height top in the whole corpus. That gap is the fault; a thin support is
    not.

    The first version of this predicate demanded a full footprint and so reported
    443 faults on a build with none — the reason a checker gets calibrated on his
    work before it is believed.
    """
    t = top_face(b)
    return t is not None and t >= 1.0


def half_step(b: Optional[BlockState]) -> bool:
    """A **bottom slab**: the one support that reads as structure and is not one.

    This is the trap, stated as narrowly as his corpus states it. Of the 16 blocks he
    ever stacks over a partial top, 9 sit on a trapdoor, 4 on a pressure plate, 1 on
    a bed, 1 on a carpet — floor fittings, flush with the floor, no visible gap. Not
    one sits on a bottom slab, in 121 files.

    So a thin fitting is not a fault to build on, and half a cell of structural
    timber is.

    A bed is drawn with the same half-cell shape and is *not* this: he stacks a log
    and a fence over one in `house_manualtest`, which is furniture against a wall.
    Hence the id test as well as the shape test — with both, the rule fires nowhere
    in his 121 files, which is the only calibration that means anything.
    """
    if b is None:
        return False
    kind, param = shape_of(b)
    return kind == "slab" and param != "top" and b.short.endswith("_slab")


def full_footprint(b: Optional[BlockState]) -> bool:
    """Reaches the top of its cell across the whole cell — a floor you can walk on
    rather than a post you can stand a beam on."""
    if b is None:
        return False
    kind, param = shape_of(b)
    if kind == "full":
        return True
    if kind in ("slab", "plate"):
        return param == "top"
    return False                        # stairs, posts, doors: partial footprint


def side_attached(b: Optional[BlockState]) -> bool:
    """Hangs on a face rather than resting on the cell below it."""
    if b is None:
        return False
    n = b.short
    return (n.endswith(("_trapdoor", "_wall_sign", "_wall_banner", "_torch"))
            or n in ("ladder", "wall_torch", "lantern", "chain", "tripwire_hook",
                     "lily_pad", "vine", "short_grass", "grass", "flower_pot"))


def is_rail(b: Optional[BlockState]) -> bool:
    """Fence, wall, pane, bars: 1.5 tall, connects, cannot be stood on easily."""
    if b is None:
        return False
    return b.short.endswith(("_fence", "_fence_gate", "_wall", "_pane", "_bars"))


def is_roof_material(b: Optional[BlockState]) -> bool:
    if b is None:
        return False
    return b.short.endswith(("_slab", "_stairs"))


def perch_top(b: Optional[BlockState]) -> Optional[float]:
    """Height an animal's feet reach standing on this, in cell units above its own
    cell floor. A rail is 1.5 — that is why a full block beside a fence is a step
    and a slab-capped post is not."""
    if b is None:
        return None
    if is_rail(b):
        return 1.5
    return top_face(b)
