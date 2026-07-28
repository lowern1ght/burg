"""Gate a candidate structure against the author's measured corpus.

Every threshold here was measured by `corpus.py` over the 98 building-like
NBTs the author hand-built, and every one of them was kept only because it
actually separates good from bad. Several candidate gates were measured and
*discarded*, which matters as much as the ones that stayed:

  * `empty_top` — the author leaves declared headroom above the highest block
    in 70 of 98 buildings, up to 8 layers. Empty top layers are normal, not a
    defect. (An early version of this tool flagged them as one.)
  * `roof_taper` / `roof_min_step` — a flat slab pancake capped by a one-block
    ridge scores 0.87 taper and 0.15 min-step, both squarely inside the
    author's band. Aggregate roof numbers do not separate a pancake from a
    pitch, so they are reported but never gated on.
  * `density` and `detail` — the old StyleKit output sits at the corpus median
    for both. They are wide bands, useful only for catching gross outliers.

What survives is either a hard correctness check or a distribution the author
never violates. Shape judgement is deliberately NOT attempted here: render the
candidate with `render_png` next to a real structure and look at it.

CLI:
    python -m structures.critic candidate.nbt [more.nbt ...]
    python -m structures.critic --json out.json cand.nbt
"""

from __future__ import annotations

import argparse
import gzip
import io
import json
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple

from nbtlib import File

from .anatomy import analyse
from .corpus import RENAMES_1_21, Metrics, measure
from .nbtio import Voxels, load

# ── measured bands (corpus.py over 98 author buildings) ─────────────
# name -> (p05, p95). Soft: outside means "look at it", not "reject".
BANDS: Dict[str, Tuple[float, float]] = {
    "density": (0.124, 0.423),
    "detail": (0.075, 0.179),
    "cover_share": (0.109, 0.284),
}
# Mirror symmetry. p95 warns; only a value above the corpus maximum fails.
# Using p95 as a hard gate is a category error: by construction it rejects ~5%
# of the author's own work on each axis, which measured out at 12 of 98 files.
# The observed ceilings are mirror_x 0.785 and mirror_z 0.686, and the old
# StyleKit output lands at 0.78-0.85 / 0.67-0.70 — overlapping the author's
# ceiling, so this discriminates far more weakly than it first appeared.
MIRROR_X_WARN, MIRROR_X_FAIL = 0.685, 0.80
MIRROR_Z_WARN, MIRROR_Z_FAIL = 0.596, 0.72
# Author max is 2, and 95 of 98 buildings have none.
FLOATING_MAX = 2
# Share of author buildings whose roof zone contains stairs, by total height.
ROOF_STAIR_EXPECTED = {"h7-9": 0.61, "h>=10": 0.82}


@dataclass
class Finding:
    level: str        # fail | warn | info
    code: str
    message: str

    def __str__(self) -> str:
        tag = {"fail": "FAIL", "warn": "warn", "info": "info"}[self.level]
        return f"  [{tag}] {self.code}: {self.message}"


@dataclass
class Verdict:
    name: str
    metrics: Metrics
    findings: List[Finding] = field(default_factory=list)

    @property
    def failures(self) -> List[Finding]:
        return [f for f in self.findings if f.level == "fail"]

    @property
    def warnings(self) -> List[Finding]:
        return [f for f in self.findings if f.level == "warn"]

    @property
    def ok(self) -> bool:
        return not self.failures

    def report(self) -> str:
        m = self.metrics
        head = (f"{self.name}: {'PASS' if self.ok else 'FAIL'}  "
                f"({len(self.failures)} fail, {len(self.warnings)} warn)")
        stat = (f"  size={m.size} solid={m.solid} dens={m.density:.2f} "
                f"detail={m.detail:.3f} cover={m.cover_share:.2f} "
                f"mirror={m.mirror_x:.2f}/{m.mirror_z:.2f} "
                f"roof={m.roof_layers}L taper={m.roof_taper:.2f}")
        lines = [head, stat] + [str(f) for f in self.findings]
        return "\n".join(lines)


def duplicate_positions(path: str | Path) -> int:
    """Count block entries that share a position — always 0 in the corpus."""
    raw = Path(path).read_bytes()
    try:
        data = gzip.decompress(raw)
    except Exception:
        data = raw
    nbt = File.parse(io.BytesIO(data))
    c = Counter((int(b["pos"][0]), int(b["pos"][1]), int(b["pos"][2]))
                for b in nbt["blocks"])
    return sum(v - 1 for v in c.values() if v > 1)


# Blocks that hang on the face of another block, where `facing` names the
# direction they point and the support is one step the OTHER way.
#
# Deliberately narrow. A first version also listed `lever` and
# `oak_wall_hanging_sign` and reported five failures in the author's own
# corpus — but a lever carries a `face` property and may stand on the floor or
# hang from a ceiling, and a hanging sign uses different geometry again. Both
# were the checker being wrong, not the build.
WALL_ATTACHED = {
    "white_wall_banner", "red_wall_banner", "brown_wall_banner", "wall_torch",
    "ladder", "oak_wall_sign", "tripwire_hook",
}
TRAPDOORS = {"oak_trapdoor", "spruce_trapdoor", "iron_trapdoor"}
BEHIND = {"north": (0, 0, 1), "south": (0, 0, -1),
          "west": (1, 0, 0), "east": (-1, 0, 0)}
GROUND_LIKE = {
    "grass_block", "dirt", "coarse_dirt", "dirt_path", "podzol", "mud",
    "rooted_dirt", "farmland", "water", "short_grass", "grass", "moss_block",
}


def _neighbours(p: Tuple[int, int, int]) -> List[Tuple[int, int, int]]:
    x, y, z = p
    return [(x + 1, y, z), (x - 1, y, z), (x, y, z + 1),
            (x, y, z - 1), (x, y + 1, z), (x, y - 1, z)]


def _unsupported(vox: Voxels) -> List[str]:
    out: List[str] = []
    for p, b in vox.solid_items():
        n = b.short
        if n in WALL_ATTACHED:
            off = BEHIND.get(b.get("facing", ""))
            if off and not vox.occupied((p[0] + off[0], p[1] + off[1],
                                        p[2] + off[2])):
                out.append(f"{n}@{p} facing={b.get('facing')}")
        elif n in TRAPDOORS:
            off = BEHIND.get(b.get("facing", ""))
            ok = bool(off) and vox.occupied((p[0] + off[0], p[1] + off[1],
                                            p[2] + off[2]))
            if not ok:
                ok = (vox.occupied((p[0], p[1] - 1, p[2]))
                      or vox.occupied((p[0], p[1] + 1, p[2])))
            if not ok:
                out.append(f"{n}@{p}")
    return out


def _decor_runs(vox: Voxels, limit: int = 3) -> List[str]:
    """Straight runs of `limit`+ identical trapdoors — a duplication artefact."""
    out: List[str] = []
    seen = set()
    for p, b in vox.solid_items():
        if b.short not in TRAPDOORS or p in seen:
            continue
        for step in ((1, 0, 0), (0, 0, 1), (0, 1, 0)):
            run = [p]
            q = p
            while True:
                q = (q[0] + step[0], q[1] + step[1], q[2] + step[2])
                o = vox.get(q)
                if o is None or o.short != b.short:
                    break
                run.append(q)
            if len(run) >= limit:
                seen.update(run)
                out.append(f"{b.short} x{len(run)} from {p}")
                break
    return out


def _orphans(vox: Voxels) -> List[str]:
    out: List[str] = []
    for p, b in vox.solid_items():
        if p[1] == 0 or b.short in GROUND_LIKE or b.short.endswith("_leaves"):
            continue
        if not any(vox.occupied(q) for q in _neighbours(p)):
            out.append(f"{b.short}@{p}")
    return out


def judge(vox: Voxels, dupes: int = 0) -> Verdict:
    """Score one candidate. Hard failures first, then distribution warnings."""
    m = measure(vox, dupes)
    v = Verdict(name=vox.name or "candidate", metrics=m)
    add = v.findings.append

    # ---- hard correctness ----
    if dupes:
        add(Finding("fail", "duplicate-positions",
                    f"{dupes} block entries share a position with another. "
                    "The author's files have zero. A carved window or doorway "
                    "still holding its wall block is the usual cause."))
    if m.legacy_ids:
        add(Finding("fail", "invalid-block-id",
                    f"not valid on 1.21.1: {', '.join(m.legacy_ids)} "
                    f"(rename to {', '.join(RENAMES_1_21[i] for i in m.legacy_ids)})"))
    if m.floating > FLOATING_MAX:
        add(Finding("fail", "floating-blocks",
                    f"{m.floating} unsupported blocks with no neighbour; the "
                    f"author's worst case is {FLOATING_MAX}"))
    elif m.floating:
        add(Finding("warn", "floating-blocks",
                    f"{m.floating} unsupported block(s) — within the author's "
                    "range but check they are intentional"))
    if m.solid == 0:
        add(Finding("fail", "empty", "no solid blocks"))

    # Jigsaw connectors are useless without their block-entity data:
    # BuildSchematic.replaceJigsawBlocks reads `final_state` to decide what the
    # marker turns into after placement. A bare jigsaw block stays in the world.
    bare = [p for p, b in vox.solid_items()
            if b.short == "jigsaw" and p not in vox.block_nbt]
    if bare:
        add(Finding("fail", "jigsaw-without-nbt",
                    f"{len(bare)} jigsaw block(s) carry no block-entity data at "
                    f"{bare[:3]}; final_state/pool/target are required or the "
                    "marker is left in the world"))

    # Attachment validity. `floating` only asks whether a block has *any*
    # neighbour; these blocks need one on a specific face. A ladder whose
    # facing points away from its wall hangs in mid-air, and a wall banner with
    # nothing behind it pops off — both survived the floating check because
    # they sit next to something.
    unsupported = _unsupported(vox)
    if unsupported:
        add(Finding("fail", "unsupported-attachment",
                    f"{len(unsupported)} block(s) attach to a face with no "
                    f"support behind them: {unsupported[:4]}"))

    # Runs of the same face-attached decoration. Stretching a donor duplicates
    # whole slices, so a single trapdoor becomes a row of four.
    runs = _decor_runs(vox)
    if runs:
        # Warn, not fail: the author runs trapdoors horizontally on purpose as
        # pen railing in `pig_farm_lvl6..8`, up to five in a line. A run is only
        # a defect when it came from duplicating a slice, which the generator
        # now prevents at the source rather than detecting after the fact.
        add(Finding("warn", "duplicated-decor",
                    f"{len(runs)} run(s) of 3+ identical attached decorations "
                    f"in a line — deliberate in the author's pig farms, an "
                    f"artefact when it comes from a stretch: {runs[:3]}"))

    orphans = _orphans(vox)
    if orphans:
        # Warn: this counts 6-connected neighbours only, so a slab held on by a
        # diagonal reads as orphaned. The author has three such blocks.
        add(Finding("warn", "orphan-block",
                    f"{len(orphans)} structural block(s) with no orthogonal "
                    f"neighbour: {orphans[:4]}"))

    # ---- style distributions ----
    for axis, val, warn, fail_at, med in (
            ("x", m.mirror_x, MIRROR_X_WARN, MIRROR_X_FAIL, 0.25),
            ("z", m.mirror_z, MIRROR_Z_WARN, MIRROR_Z_FAIL, 0.34)):
        if val > fail_at:
            add(Finding("fail", f"mirror-symmetry-{axis}",
                        f"mirror_{axis}={val:.2f} is above anything in the "
                        f"corpus (max {fail_at - 0.015:.2f}, median {med:.2f}). "
                        "Break up the terrain apron, window spacing and decor "
                        "— exact symmetry is the loudest generated-build tell."))
        elif val > warn:
            add(Finding("warn", f"mirror-symmetry-{axis}",
                        f"mirror_{axis}={val:.2f} above the author's p95 of "
                        f"{warn:.2f} (median {med:.2f}) but still inside the "
                        "observed range"))

    for key, (lo, hi) in BANDS.items():
        val = float(getattr(m, key))
        if val < lo:
            add(Finding("warn", f"{key}-low",
                        f"{key}={val:.3f} below the author's p05 of {lo:.3f}"))
        elif val > hi:
            add(Finding("warn", f"{key}-high",
                        f"{key}={val:.3f} above the author's p95 of {hi:.3f}"))

    # ---- conditional roof gate ----
    ana = analyse(vox)
    height = vox.top_y() + 1
    roof_stairs = sum(1 for (p, b) in vox.solid_items()
                      if ana.roof_lo <= p[1] <= ana.roof_hi
                      and b.short.endswith("_stairs"))
    if height >= 10 and roof_stairs == 0:
        # Warn, not fail: 18% of the author's tall builds really are flat.
        add(Finding("warn", "flat-roof-on-tall-build",
                    f"height {height} with no stairs in the roof zone. "
                    "82% of the author's builds this tall pitch the roof with "
                    "stairs stepping inward one block per layer — check this "
                    "one is deliberately flat."))
    elif 7 <= height <= 9 and roof_stairs == 0:
        add(Finding("warn", "flat-roof",
                    f"height {height} with no stairs in the roof zone; 61% of "
                    "the author's builds this tall use a stair pitch"))

    add(Finding("info", "roof",
                f"zone y={ana.roof_lo}..{ana.roof_hi}, {m.roof_layers} layer(s), "
                f"{roof_stairs} stairs, taper={m.roof_taper:.2f}, "
                f"min_step={m.roof_min_step:.2f} — numbers cannot tell a pitch "
                "from a pancake, render it and look"))
    if m.empty_top:
        add(Finding("info", "empty-top",
                    f"{m.empty_top} empty layer(s) above the build — normal, "
                    "the author does this in 70 of 98 buildings"))
    return v


def judge_file(path: str | Path) -> Verdict:
    vox = load(path)
    vox.name = Path(path).name
    return judge(vox, duplicate_positions(path))


def gate_dir(paths: Sequence[str | Path]) -> List[Verdict]:
    return [judge_file(p) for p in paths]


def main(argv: Optional[Sequence[str]] = None) -> int:
    ap = argparse.ArgumentParser(description="Gate structures against the corpus.")
    ap.add_argument("files", nargs="+")
    ap.add_argument("--json", help="write verdicts here")
    ap.add_argument("-q", "--quiet", action="store_true",
                    help="only print failures")
    a = ap.parse_args(argv)

    verdicts = [judge_file(f) for f in a.files]
    for v in verdicts:
        if a.quiet and v.ok:
            continue
        print(v.report())
        print()

    bad = [v for v in verdicts if not v.ok]
    print(f"{len(verdicts) - len(bad)}/{len(verdicts)} passed")
    if a.json:
        Path(a.json).write_text(json.dumps(
            [{"name": v.name, "ok": v.ok,
              "findings": [{"level": f.level, "code": f.code,
                            "message": f.message} for f in v.findings]}
             for v in verdicts], indent=2))
    return 1 if bad else 0


if __name__ == "__main__":
    raise SystemExit(main())
