"""Measure the author's corpus, so the critic's thresholds are derived.

Every gate in `critic.py` has to come from a number measured here. The old
StyleKit failed precisely because its rules were asserted from memory — one of
them ("the author never roofs with stairs") was not merely imprecise but
false, and it is what produced the flat pancake roofs.

CLI:
    python -m structures.corpus                 # distributions + gate proposal
    python -m structures.corpus --json p.json   # write the profile
"""

from __future__ import annotations

import argparse
import json
import statistics as stats
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence, Tuple

from .anatomy import TERRAIN, VEGETATION
from .nbtio import CORPUS_ROOT, TOOL_AUTHOR, BlockState, Voxels, load_corpus

# Blocks that legitimately have nothing beneath them.
FLOAT_OK = set(VEGETATION) | {
    "oak_leaves", "spruce_leaves", "birch_leaves", "dark_oak_leaves",
    "lantern", "chain", "wall_torch", "ladder", "vine", "cobweb",
    "oak_wall_sign", "oak_wall_hanging_sign", "white_wall_banner",
    "red_wall_banner", "brown_wall_banner", "tripwire_hook", "bell",
    "oak_trapdoor", "spruce_trapdoor", "iron_trapdoor", "jigsaw",
    "glass_pane", "iron_bars", "oak_fence", "oak_fence_gate", "water",
    "fire", "beehive", "bee_nest", "honey_block", "lever", "oak_button",
    "oak_wall_torch", "redstone_torch", "candle", "white_candle", "red_candle",
}

# Renames the 1.21.1 registry requires. `minecraft:grass` was replaced by
# `short_grass` in 1.20.3; 28 palette entries in this corpus still use the old
# id, which will not resolve on the target version.
RENAMES_1_21 = {
    "minecraft:grass": "minecraft:short_grass",
    "minecraft:grass_path": "minecraft:dirt_path",
}


def modernize(vox: Voxels) -> int:
    """Rewrite block ids that no longer resolve on 1.21.1. Returns the count.

    The corpus itself is affected — 28 of the author's structures still contain
    `minecraft:grass`, removed in 1.20.3 — so anything derived from a donor
    inherits the problem and must be fixed on the way out rather than shipped.
    """
    fixed = 0
    for pos, b in list(vox.solid_items()):
        if b.name in RENAMES_1_21:
            vox.set(pos, BlockState(RENAMES_1_21[b.name], b.props),
                    vox.block_nbt.get(pos))
            fixed += 1
    return fixed


@dataclass
class Metrics:
    """Everything the critic scores, measured for one structure."""

    name: str = ""
    size: Tuple[int, int, int] = (0, 0, 0)
    solid: int = 0
    density: float = 0.0
    palette: int = 0
    detail: float = 0.0          # palette entries per solid block
    cover_share: float = 0.0     # slab+stair share of solid blocks
    veg_share: float = 0.0
    empty_top: int = 0           # declared height above the highest block
    floating: int = 0            # unsupported blocks that should not float
    duplicate_positions: int = 0
    mirror_x: float = 0.0        # 1.0 == perfectly mirror-symmetric in X
    mirror_z: float = 0.0
    roof_taper: float = 0.0      # how much the roof narrows bottom -> top
    roof_steps: float = 0.0      # share of roof layers that narrow at all
    roof_min_step: float = 1.0   # harshest single narrowing (area ratio)
    roof_layers: int = 0
    legacy_ids: List[str] = field(default_factory=list)


def _floating(vox: Voxels) -> int:
    n = 0
    for (x, y, z), b in vox.solid_items():
        if y == 0 or b.short in FLOAT_OK:
            continue
        below = vox.get((x, y - 1, z))
        if below is not None and not below.is_air:
            continue
        # Anything with a solid orthogonal neighbour is plausibly attached.
        if any(vox.occupied(p) for p in
               ((x + 1, y, z), (x - 1, y, z), (x, y, z + 1), (x, y, z - 1))):
            continue
        n += 1
    return n


def _mirror(vox: Voxels, axis: str) -> float:
    """Share of solid cells whose mirror twin holds the same block id."""
    sx, sy, sz = vox.size
    solid = [(p, b) for p, b in vox.solid_items()]
    if not solid:
        return 0.0
    same = 0
    for (x, y, z), b in solid:
        m = (sx - 1 - x, y, z) if axis == "x" else (x, y, sz - 1 - z)
        other = vox.get(m)
        if other is not None and other.short == b.short:
            same += 1
    return same / len(solid)


def _roof_shape(vox: Voxels) -> Tuple[float, float, float, int]:
    """(taper, steps, min_step, layers) for the roof zone.

    A stair-pitched roof narrows as it rises; a flat slab pancake does not.
    `cover_share` cannot tell them apart — a pancake built from slabs scores
    just as high — so shape has to be measured separately.

    `taper` alone is not enough either: a pancake topped by a one-block ridge
    line tapers from full plate to almost nothing and scores 0.87, right in the
    author's band. What separates them is *gradualness* — `min_step` is the
    harshest single narrowing, so the author's 9->7->5->3->1 stays near 0.6
    while a plate->ridge jump collapses toward 0.1.
    """
    from .anatomy import analyse   # local import: anatomy imports nothing here

    ana = analyse(vox)
    areas: List[int] = []
    for y in range(ana.roof_lo, ana.roof_hi + 1):
        cells = [1 for (p, b) in vox.solid_items()
                 if p[1] == y and b.short not in VEGETATION]
        if cells:
            areas.append(len(cells))
    if len(areas) < 2:
        return (0.0, 0.0, 1.0, len(areas))
    taper = max(0.0, 1.0 - areas[-1] / areas[0])
    steps = sum(1 for a, b in zip(areas, areas[1:]) if b < a) / (len(areas) - 1)
    min_step = min(b / a for a, b in zip(areas, areas[1:]) if a)
    return (taper, steps, min_step, len(areas))


def measure(vox: Voxels, duplicate_positions: int = 0) -> Metrics:
    counts = vox.counts()
    solid = vox.solid_count or 1
    cover = sum(v for k, v in counts.items()
                if k.endswith("_slab") or k.endswith("_stairs"))
    veg = sum(v for k, v in counts.items() if k in VEGETATION)
    legacy = sorted({b.name for _, b in vox.solid_items()
                     if b.name in RENAMES_1_21})
    taper, steps, min_step, rlayers = _roof_shape(vox)
    return Metrics(
        name=vox.name,
        size=vox.size,
        solid=vox.solid_count,
        density=vox.density,
        palette=vox.palette_size,
        detail=vox.palette_size / solid,
        cover_share=cover / solid,
        veg_share=veg / solid,
        empty_top=vox.size[1] - 1 - vox.top_y(),
        floating=_floating(vox),
        duplicate_positions=duplicate_positions,
        mirror_x=_mirror(vox, "x"),
        mirror_z=_mirror(vox, "z"),
        roof_taper=taper,
        roof_steps=steps,
        roof_min_step=min_step,
        roof_layers=rlayers,
        legacy_ids=legacy,
    )


# ── corpus profile ──────────────────────────────────────────────────

# Structures that are terrain or scenery, not buildings. Their statistics would
# skew every building gate, so the profile reports them separately.
NON_BUILDING_HINTS = ("lake", "field", "grove", "wild_spot", "well",
                      "street", "path", "garden", "place", "bridge")


def is_building(name: str) -> bool:
    stem = name.rsplit("/", 1)[-1].replace(".nbt", "")
    return not any(h in stem for h in NON_BUILDING_HINTS)


def is_reference(vox: Voxels) -> bool:
    """True for hand-built author structures only.

    Generated output is written into `structure/military/`, i.e. inside the
    corpus tree. Left unfiltered it would feed its own statistics back into the
    bands the critic gates on, so the profile keeps only files this toolchain
    did not write.
    """
    return vox.author != TOOL_AUTHOR


@dataclass
class Band:
    """An observed range: p05..p95 with the median, for one metric."""

    lo: float
    med: float
    hi: float
    n: int

    def as_tuple(self) -> Tuple[float, float, float]:
        return (self.lo, self.med, self.hi)


def band(values: Sequence[float]) -> Band:
    vs = sorted(values)
    if not vs:
        return Band(0, 0, 0, 0)
    def pct(p: float) -> float:
        if len(vs) == 1:
            return vs[0]
        i = min(len(vs) - 1, max(0, int(round(p * (len(vs) - 1)))))
        return vs[i]
    return Band(pct(0.05), stats.median(vs), pct(0.95), len(vs))


def profile(root: str | Path = CORPUS_ROOT,
            buildings_only: bool = True) -> Dict[str, object]:
    """Measure the corpus and return the reference profile."""
    corp, broken = load_corpus(root)
    rows: List[Metrics] = []
    for name, vox in corp.items():
        if buildings_only and not is_building(name):
            continue
        if not is_reference(vox):
            continue
        rows.append(measure(vox))

    def col(attr: str) -> List[float]:
        return [float(getattr(m, attr)) for m in rows]

    bands = {k: asdict(band(col(k))) for k in
             ("density", "detail", "cover_share", "veg_share",
              "mirror_x", "mirror_z", "solid")}
    return {
        "n_structures": len(rows),
        "n_corrupt": len(broken),
        "corrupt": [b[0] for b in broken],
        "bands": bands,
        "empty_top": {
            "max": max((m.empty_top for m in rows), default=0),
            "share_nonzero": (sum(1 for m in rows if m.empty_top > 0)
                              / max(1, len(rows))),
            "p95": band(col("empty_top")).hi,
        },
        "floating": {
            "max": max((m.floating for m in rows), default=0),
            "share_nonzero": (sum(1 for m in rows if m.floating > 0)
                              / max(1, len(rows))),
            "p95": band(col("floating")).hi,
        },
        "duplicate_positions_max": max((m.duplicate_positions for m in rows),
                                       default=0),
        "legacy_id_files": sorted(m.name for m in rows if m.legacy_ids),
    }


def _hist(values: Sequence[float], width: int = 34) -> str:
    if not values:
        return ""
    lo, hi = min(values), max(values)
    if hi <= lo:
        return f"all == {lo:.3f}"
    buckets = [0] * 10
    for v in values:
        buckets[min(9, int((v - lo) / (hi - lo) * 10))] += 1
    top = max(buckets)
    out = []
    for i, c in enumerate(buckets):
        a = lo + (hi - lo) * i / 10
        bar = "#" * int(c / top * width)
        out.append(f"    {a:7.3f} |{bar:<{width}} {c}")
    return "\n".join(out)


def main(argv: Optional[Sequence[str]] = None) -> int:
    ap = argparse.ArgumentParser(description="Profile the author's corpus.")
    ap.add_argument("--root", default=CORPUS_ROOT)
    ap.add_argument("--json", help="write the profile here")
    ap.add_argument("--all", action="store_true",
                    help="include fields, lakes and streets")
    a = ap.parse_args(argv)

    corp, broken = load_corpus(a.root)
    rows = [measure(v) for n, v in corp.items()
            if (a.all or is_building(n)) and is_reference(v)]
    print(f"corpus: {len(corp)} readable, {len(broken)} corrupt, "
          f"{len(rows)} counted as buildings\n")

    for attr, label in (("density", "density (solid/volume)"),
                        ("detail", "detail (palette/solid)"),
                        ("cover_share", "slab+stair share"),
                        ("veg_share", "vegetation share"),
                        ("mirror_x", "mirror symmetry X"),
                        ("mirror_z", "mirror symmetry Z")):
        vs = [float(getattr(m, attr)) for m in rows]
        b = band(vs)
        print(f"{label}:  p05={b.lo:.3f}  median={b.med:.3f}  p95={b.hi:.3f}")
        print(_hist(vs))
        print()

    et = [m.empty_top for m in rows]
    fl = [m.floating for m in rows]
    print(f"empty top layers: max={max(et)} nonzero={sum(1 for v in et if v)}"
          f"/{len(et)} p95={band([float(v) for v in et]).hi:.0f}")
    print(f"floating blocks:  max={max(fl)} nonzero={sum(1 for v in fl if v)}"
          f"/{len(fl)} p95={band([float(v) for v in fl]).hi:.0f}")
    worst = sorted(rows, key=lambda m: -m.floating)[:5]
    for m in worst:
        if m.floating:
            print(f"    {m.floating:4d} floating  {m.name}")

    legacy = [m for m in rows if m.legacy_ids]
    if legacy:
        print(f"\n1.21.1-invalid block ids in {len(legacy)} structures:")
        for m in legacy[:12]:
            print(f"    {m.name}: {', '.join(m.legacy_ids)}")

    if broken:
        print(f"\ncorrupt (unreadable) files: {len(broken)}")
        for n, e in broken:
            print(f"    {n}")

    if a.json:
        Path(a.json).write_text(json.dumps(profile(a.root, not a.all), indent=2))
        print(f"\nwrote profile to {a.json}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
