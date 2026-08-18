# Audit — generator framework (2026-07-31)

The code is **not** uniformly "beta". What is mature is excellent — the write-time
`Canvas` guard, the single `solids` shape model, and the `--calibrate` discipline
on the best checkers are genuinely senior work, each earned by a specific shipped
bug. What is beta is the **coverage and consistency of that machinery**: the guard
only protects one of the three generators, half the checkers are uncalibrated or
untested, the "one command" gate runs a minority of the checkers, and the two
largest modules are god-files held together by copy-pasted constants. The owner's
"beta / rushed" verdict lands on the seams between good parts, not on the parts
themselves.

## Method

Read the context docs (`burg-buildings` SKILL, `HOW_WE_WORK`, `ARCHITECTURE`,
`OPEN-WORK`, `CLAUDE.md`), then inventoried every `.py` under `tools/` (76 files,
~24k LOC) and read in full the generation core (`fabric.py`, `solids.py`,
`halves.py`, `compose.py`, `wall.py`, `pasture.py`), all seven checkers
(`check_integrity`, `check_fabric`, `check_stray`, `check_usable`, `check_pens`,
`check_stairs`, `selfgate`), the calibration/selftest (`calibrate_fabric`), both
drivers (`build_livestock`, `build_military`), and the style gate (`critic.py`).
Searched for beta markers, deepslate, dead code, and CI. No checker was run; this
is a static read of the framework, as asked.

Skin/texture tooling (`draw_citizens.py` 5073 LOC, `draw_garments.py`, the
`make_*_skins.py`, `npc_uv.py`, `export_rig.py`, `remap_npc_uv.py`, `skin_text.py`)
is out of scope for this audit — it is a different pipeline that does not touch
`structures/`.

## Module inventory

Only the building-generation framework. LOC from `Get-Content | Measure-Object -Line`.

| file | LOC | job | smell |
|---|---|---|---|
| `structures/pasture.py` | 2540 | the whole livestock set: palette, ground, fence, byre, shed, props, escape model, planning, build_rung | **god-module** (12+ concerns) |
| `structures/compose.py` | 1541 | donor harvest + tower/yard/wall compose + militarize + fittings + work ladder | **god-module** (8+ concerns) |
| `structures/wall.py` | 1434 | fortification set: curtain, corner, gate, tower | large but cohesive; clean |
| `structures/desert.py` | 884 | desert variant set | not read in depth |
| `structures/street.py` | 542 | street pieces | not read in depth |
| `build_bench.py` | 427 | bench/test driver | clean; sole sanctioned `deepslate_tile_slab` user |
| `structures/assemble.py` | 347 | donor `stretch`/`variant` | clean |
| `build_military.py` | 346 | military driver + `usable()` route wiring | thin orchestration; **no fabric gate** |
| `structures/render_png.py` | 345 | PNG renderer | utility |
| `structures/critic.py` | 311 | style gate (measured bands) | clean, well-documented |
| `structures/render_tex.py` | 306 | textured renderer | utility |
| `structures/facade.py` | 301 | facade articulation | clean |
| `check_fabric.py` | 297 | fence/roof/slab/cantilever checker | calibrated; **couples to `pasture`** |
| `structures/anatomy.py` | 288 | zone detector | clean |
| `structures/corpus.py` | 287 | corpus measurement | clean |
| `structures/nbtio.py` | 258 | NBT load/save + `Voxels` | core; clean |
| `structures/traverse.py` | 226 | walk/climb graph | core; clean |
| `structures/fabric.py` | 213 | the `Canvas` write-time guard | **the mature core** |
| `check_sides.py` | 211 | (legacy?) side checker | not referenced by selfgate |
| `audit_livestock.py` | 209 | documented-rules audit | clean |
| `check_usable.py` | 200 | storey reachability + ladder keep | **default mode never fails** |
| `check_integrity.py` | 198 | the 5 primitives | **no `--calibrate`; roof gate admitted broken** |
| `check_wrap.py` | 195 | ? | not referenced by selfgate |
| `structures/builder.py` | 193 | ? | not referenced by drivers |
| `selfgate.py` | 164 | "one command" gate | **runs 3 of 7 checkers** |
| `calibrate_fabric.py` | 155 | guard calibration + 9-case selftest | clean; the model checker |
| `build_livestock.py` | 153 | livestock driver | thin orchestration; clean |
| `structures/solids.py` | 114 | the single shape model | clean; the mature core |
| `structures/stylekit.py` | 113 | corpus-band profiler | clean |
| `check_stray.py` | 110 | stray/spike blocks | calibrated; clean |
| `check_pens.py` | 99 | animal escape | **no `--calibrate`, no selftest**; couples to `pasture` |
| `structures/halves.py` | 97 | half-cell slot detection | clean |
| `check_stairs.py` | 144 | stair facing | calibrated; clean |
| `inspect_nbt.py` | 41* | one-off | **dead** (hardcoded path) |
| `inspect_one.py` / `inspect2/3/4.py` | 17–22 | one-off | **dead** (hardcoded `out/manual/house.nbt`) |
| `inspect_nbt_bytes.py` / `inspect_author.py` / `inspect_desert.py` | 8–15 | one-off | **dead** |
| `growth_desert.py` | 35 | one-off | **dead** |
| `check_bounds.py` | 23 | one-off | **dead** |

`*` LOC shown as measured; several `inspect*` files are scripts, not modules.

## Findings

### Write-time guard (fabric.Canvas)

The guard itself (`fabric.py`) is the strongest part of the framework. It enforces
all three always-wrong cases the skill names, and it enforces them at write time
*and* at finished-file time through the same `_inspect`:

- **cube on a bottom slab** → `fabric.py:158-176` (`_inspect`, `rider` fault), via
  `solids.half_step` + `solids.fills_cell`.
- **rail over a bottom slab** → `fabric.py:181-183` (`rail-on-half` fault), with
  the one known corpus residual pinned in `calibrate_fabric.py:34`
  (`CORPUS_RESIDUAL = {"rail-on-half": 1}`, the `house_3_lvl6` fence-gate).
- **roof block with nothing under or beside it** → `fabric.py:187-205` (`_finish`,
  `floating` fault), deliberately deferred to device exit because the first cell of
  a plane would otherwise look floating.
- **donor wrap is correct**: `fabric.py:80-85` documents `checked=False`, and
  `fabric.py:145` skips the check while `fabric.py:154` still records `origin` so a
  later device tripping over a grafted cell names whose it was. Verified against
  `pasture.build_rung`, which wraps the graft in `with vox.device("graft " + ...,
  checked=False)`.
- `put_on` (`fabric.py:214-234`) refuses a half-slab support and accepts a post,
  exactly as the skill describes; pinned by selftest case 8
  (`calibrate_fabric.py:123-131`).

**The guard is bypassed by two of the three generators.** This is the single
biggest architectural finding.

- `pasture.build_rung` constructs `Canvas(Voxels(...))` (`pasture.py:2256`) and
  `Canvas(prev.copy(...))` (`pasture.py:2262`); its 24 `with vox.device(...)`
  blocks and the 111 `vox.set(...)` calls inside device functions all go through
  the `Canvas.set` override (the device functions are typed `Voxels` but receive a
  `Canvas`, which quacks as `Voxels` via `__getattr__`, `fabric.py:107`). So
  livestock is guarded. ✓
- `compose.compose_tower` / `compose_yard` / `compose_wall` construct a raw
  `Voxels(...)` (`compose.py:339`, `:772`, `:897`) and call `vox.set(...)` straight
  on it — **no `Canvas`, no `device()` tags, no `_inspect`**. Every block the
  military set places is unchecked at write time.
- `wall.compose_*` likewise constructs raw `Voxels(...)` (`wall.py:1045`, `:1072`,
  `:1210`, `:1422`) and writes through `vox.set`. The whole fortification set is
  unchecked at write time.

And the guard is not picked up post-hoc either: `build_military.py` runs only
`critic.judge` (style) and `usable()` (route) — there is no `fabric` field on its
`Result` (`build_military.py:270-276`) and no call to `Canvas.inspect_all` or
`check_fabric` anywhere in the military path. `selfgate.py`, which *does* run
`check_fabric.slab_faults`, is hardcoded to the livestock directory
(`selfgate.py:36`, `LIVESTOCK = .../structure/livestock`). So the class of bug the
guard exists to refuse — a cube resting on a bottom slab, a rail over one, a roof
block bearing on nothing — can ship unchecked in every watchtower, wall segment,
gatehouse and training yard. The framework's signature feature covers half its
output.

### Donor harvest (compose.py)

The harvest is well-designed and the skill's claims hold:

- **Align by wall, not origin**: `house_bounds` in `pasture.py:315-335` measures
  the donor's real wall footprint at y=1..2 (excluding terrain and the donor's own
  garden fence), and the plot is shifted so the wall lands on the same column at
  every rung. The comment at `pasture.py:316-323` records the bug this fixed (the
  one-cell dead corridor). ✓
- **One donor family per building**: `pasture.HOUSE_LADDERS`
  (`pasture.py:276-283`) gives `house`/`house_2`/`house_3` to cow/pig/sheep
  separately, and `pasture.py:262-275` documents *why* in measured cosine
  numbers (0.93 with one donor vs 0.78 across his ladder). ✓
- **Drop on load**: `corpus.modernize` is called in `donor_house`
  (`pasture.py:297`) and again in both drivers (`build_livestock.py:99`,
  `build_military.py:321`). Raw `minecraft:villager`, item frames, extra street
  connectors and pre-1.20.3 `minecraft:grass` are handled (`pasture.py:53-54`,
  `pasture.py:289-299`). ✓ — though note `modernize` is applied **twice** on the
  military path (once inside any donor load, once after `tidy_leaves`), which is
  harmless but a smell.

Two caveats:

- `compose.merge` (`compose.py:118-151`) lets a Vocabulary combine block states
  from different donor families (`house_3_lvl5` + `house_lvl6`,
  `build_military.py:77-78`). The skill says "one donor family per building"; the
  military vocabulary is explicitly two families merged. That is justified by the
  docstring (`compose.py:118-126`) — no single donor covers stone grammar and
  roof stairs — but it is a documented exception, not the rule the skill states.
- `militarize` uses `packed_mud` in its `TRODDEN` mix (`compose.py:1034`), and the
  comment at `compose.py:1032-1034` admits it: "the first block used here that
  does NOT occur in the author's corpus." The module's opening promise
  (`compose.py:19-22`, "restricted to block ids that occur in the corpus") is
  quietly violated in one constant. Sanctioned, but the contradiction is real.

### Material ladders (wall.py, pasture.py)

- **Deepslate ruling is enforced by absence, not by code.** `wall.py:179` carries
  the comment "No deepslate and nothing black", and indeed no generator under
  `tools/structures/` writes any deepslate id — `grep` finds deepslate only in
  utility/reference files (`palette_lab`, `slice`, `scan_devices`, `appearance`,
  `validator`), in `audit_livestock`'s banned list, and in `build_bench.py:274`
  (`deepslate_tile_slab`) which is the bench, i.e. the one place sanctioned to
  exercise the ruling. So the "top two rungs, roof only, never a wall, never below
  rung 5" rule holds — but because nobody reaches for deepslate, not because a
  guard stops them. A future `Masonry(grad="deepslate", ...)` would sail through.
- The fortification material ladder (`wall.TIERS`, `wall.py:349-361`; `Masonry`,
  `wall.py:132-172`; `RAMPS`, `wall.py:273-288`) is clean: cobblestone + stone
  base at every tier, only the gradient stone (`andesite` → `tuff` →
  `stone_bricks`) changes. Matches `docs/05-craft/STYLE.md`.
- **Pasture palette decoupling is real and deliberate**, exactly as OPEN-WORK
  records. `pasture.py:94-100` carries its own `Stone`/`Timber`/`Palette`
  dataclasses with a comment naming the reason, and imports nothing from `wall.py`.
  The coupling runs the **other** way and is the smell: `check_fabric.py:64`
  imports `FULL_BLOCKS, NEIGH4, STURDY, VEC, AXIS_OF` from `pasture`, and
  `check_pens.py:37` imports `MOB_JUMP, NEIGH4, _mob_passable, _mob_surface` from
  `pasture`. Two checkers depend on the builder module's internals. If `pasture`
  changes its `STURDY` set or its `_mob_passable` predicate, the checkers change
  silently with them — the opposite of "the checker must not mark the generator's
  own homework."

### Checkers

Every checker reads the **shipped NBT off disk** (`load(p)` on a path under
`structure/...`), not the generator's in-memory state. This is uniformly true and
is the framework's strongest invariant. The check that `check_pens` "trusts nothing
from the generator" is explicit at `check_pens.py:8-16`. `selfgate.gather` makes
the same point at `selfgate.py:75-80`.

The gaps are in calibration, self-test, and which checkers get run:

| checker | reads disk? | has `--calibrate`? | self-test? | calibrate sweeps whole corpus? | gates? |
|---|---|---|---|---|---|
| `check_integrity.py` | ✓ | **no** | no | n/a | walls yes (`WALL_HOLE_MAX=21`); **roof admitted broken** (`ROOF_BARE_MAX_PCT=1000`, `:47`); doors/floating/room yes |
| `check_fabric.py` | ✓ | ✓ | via `calibrate_fabric` | **no — 8 hand-picked files** (`:340-343`) | yes (hanging/holes/riders/fence-props) |
| `check_stray.py` | ✓ | ✓ | no | **yes** — all `plains/*.nbt` (`:130`) | count + exit code |
| `check_usable.py` | ✓ | ✓ (`--ladder` only) | no | yes for ladder (`:164-179`) | **default mode returns 0 always** (`:227`); report-only |
| `check_pens.py` | ✓ | **no** | **no** | n/a | yes (exit 1 if leak) |
| `check_stairs.py` | ✓ | ✓ | no | **yes** — all `plains/*.nbt` (`:155`) | report + residual (`CORPUS_RESIDUAL=1`) |
| `calibrate_fabric.py` | ✓ | (is the calibrator) | **yes — 9 cases** (`:72-161`) | yes (`:175-181`) | n/a |

Concrete problems:

- **`check_integrity` is the layer `HOW_WE_WORK` calls "primitives before
  statistics" — the layer that should have caught "doors in the sky" — and it has
  no `--calibrate` at all.** Its thresholds are documented as measured
  (`WALL_HOLE_MAX=21`, `:39`) but there is no command to re-prove that against the
  corpus. Worse, the roof check is explicitly broken:
  `check_integrity.py:40-47` sets `ROOF_BARE_MAX_PCT = 1000.0` with a comment
  saying "not a gate yet, and must not be trusted as one" — the definition of
  "interior" is the bounding box, which for an open shed is not a room. So one of
  the five primitives is known not to work.
- **`check_pens` has no `--calibrate` and no self-test.** It is the functional
  gate for livestock and it works, but unlike `check_fabric`/`check_stairs`/
  `check_stray` there is no corpus run and no pinned case list; the model is
  trusted on faith.
- **`check_usable`'s default (storey-reachability) mode never fails**
  (`check_usable.py:227`, `return 0`). It prints `NO-WAY-UP` / `ENTER-FAIL` and
  then exits 0. So the military enterability check is report-only; only its
  `--ladder` subcommand can gate, and only on equipment counts.
- **`check_fabric`'s `--calibrate` sweeps 8 hand-picked reference files**
  (`check_fabric.py:340-343`: four houses + four jobs), not the whole 125-file
  corpus. `check_stray` and `check_stairs` both sweep all of `plains/`; `check_fabric`
  does not. A regression that fires on, say, `merchant_shop_lvl6` would not be
  caught by its calibration pass.
- **`selfgate.py` runs 3 of 7 checkers.** `selfgate.gather` (`:74-114`) calls
  `critic.judge`, `check_pens.escapes`, and `check_fabric` (fence/roof/slab/
  cantilever/line-diagonal). It does **not** run `check_integrity`,
  `check_stray`, `check_stairs`, or `check_usable`. The docstring's promise —
  "One command that re-checks the work" — is true for livestock-fabric, false for
  integrity, stairs, strays and storey reachability. Its `AUTHOR_BAND`
  (`selfgate.py:45-53`) is also a **second copy** of the thresholds that live in
  `check_fabric` (`HANGING_MAX`, `HOLES_MAX`, `SLAB_RIDERS_MAX`), so the band and
  the gate can drift apart — the exact failure mode CLAUDE.md records ("for one
  afternoon the two copies disagreed").

### Shape model (solids.py)

There is **one** shape model. `solids.py` is the single source of truth for
sub-cell shape, derived from `appearance.shape_of` (`solids.py:23`). The write-time
guard (`fabric._inspect`) calls `solids.half_step`/`fills_cell`/`is_rail`
(`fabric.py:171-183`), and `check_fabric.slab_faults` delegates to
`Canvas(vox).inspect_all()` rather than keeping a second implementation
(`check_fabric.py:238-248`, with a comment naming the drift bug this fixed).
`halves.py` is a separate concern (half-cell *slot* detection across a surface)
and also delegates to `appearance.shape_of`, not a competing shape model. This is
the cleanest part of the framework.

The one partial duplication: the **roof/floating** logic exists in two places with
different constants. `fabric._finish` (`fabric.py:187-205`) has its own
roof-floating check using `solids.is_roof_material`, while `check_fabric.roof_faults`
+ `cantilever_faults` (`check_fabric.py:127-286`) carry their own `ROOF_MATERIAL`,
`SIDE_ATTACHED_SOFT`, and a different reach-based cantilever test. The slab rule is
unified; the roof rule is not.

### Beta markers

Remarkably clean. A repo-wide grep for `TODO|FIXME|HACK|XXX|NotImplementedError`
plus `# temporary|provisional|WIP|TEMP` and `print("debug")` returns **zero**
matches in the generation framework (the only hits are `XXXX`/`YYYY` texture-map
literals in the out-of-scope skin files). No commented-out blocks of consequence,
no `raise NotImplementedError`. The code does not *look* beta.

The beta signal is instead the **dead one-off scripts** at the bottom of the
inventory: `inspect_nbt.py`, `inspect_one.py`, `inspect2.py`, `inspect3.py`,
`inspect4.py`, `inspect_nbt_bytes.py`, `inspect_author.py`, `inspect_desert.py`,
`growth_desert.py`, `check_bounds.py` — ten files, 8–41 LOC each, most hardcoded to
`tools/structures/out/manual/house.nbt`, all clearly throwaway inspection probes
never deleted. They are not imported anywhere and do no harm, but they inflate the
file count and obscure the real modules.

Two **dead switches** in `build_military.py`: `ROOF_TO_STAIRS = False`
(`build_military.py:59`) gates a whole `stair_pitch` pass that is documented as
not-shipped ("Off by default… Not shipped until the uphill direction is decided
per cell", `:310-316`). The pass itself (`compose.stair_pitch`, `compose.py:1038`)
lives in the module, reachable but never called. That is half-finished work kept
around behind a flag rather than deleted.

### Test/CI coverage

- **There is no CI.** `.github/` does not exist. The only git hook is
  `.githooks/commit-msg` (enforcing the `[.stbl](feat/...)` commit format). Nothing
  runs the checkers, the calibrators, or the self-test automatically.
- **One self-test exists**: `calibrate_fabric.py --selftest`
  (`calibrate_fabric.py:72-161`), 9 cases pinning both directions of the guard
  (the leaf-over-slab and fence-over-slab bugs must fire; his four legitimate
  stacks, a cell-by-cell roof course, `put_on` refuse/accept, and a sabotaged
  driver that must be rejected). It is the model for what a checker self-test
  should be. **No other checker has one**, and nothing invokes it — a future
  refactor that silently breaks the guard would not be caught until a human ran the
  command by hand.
- The four-gate model the skill describes (`build_<set>.py --dry-run` +
  `check_fabric` + `check_pens` + `audit_livestock`) is real, but it is a
  **human-run** protocol. There is no "run before commit" script and the
  `selfgate` one-command gate (which comes closest) runs only a minority of the
  checkers (see above).

## Cross-cutting

The architectural issues that span files:

1. **The guard is opt-in per generator, and only one opted in.** `Canvas` is
   designed to wrap `Voxels` transparently (`fabric.py:99-109`, same `set`/`get`
   surface, delegating `__getattr__`), precisely so that the military generators
   *could* adopt it with a one-line change at construction. They did not.
   `compose.py` and `wall.py` both call `Voxels(size, {}, name)`; changing those to
   `Canvas(Voxels(size, {}, name))` and wrapping their passes in
   `with vox.device(...)` is structurally trivial and was never done. Half the
   output of the framework is unguarded.

2. **Direction maps and ground mixes are copy-pasted across modules.**
   - `OPPOSITE`/`OPPOSITE_SIDE`/`outward` dicts: `compose.py:465`, `compose.py:631`,
     `compose.py:640`, `compose.py:1718`, `wall.py:89`, `pasture.py:189`.
   - `VEC`/`OUT_VEC`: `wall.py:88`, `pasture.py:190`, inline in `compose.py:435`.
   - `NEIGH4`: `check_stray.py:55`, `check_integrity.py:34`, `pasture.py:187`,
     `compose.py` (inline), `check_fabric` (via pasture).
   - `TRODDEN` ground tuple: `compose.py:1034`, `wall.py:223`, `pasture.py:235` —
     three copies of the same mix, each with its own comment.
   - The `Timber` dataclass appears in both `wall.py:185` and `pasture.py:132`,
     near-identical.
   This is precisely the drift substrate CLAUDE.md warns about. Today they agree;
   the first edit to one copy will break that.

3. **Checkers depend on builder internals.** `check_fabric` and `check_pens`
   import constants and predicates from `pasture` (the builder). The integrity of
   "the checker does not mark the generator's homework" rests on the checker
   reading disk (which it does) — but the *rules* (what counts as a sturdy block,
   what an animal can pass) are shared with the generator. Change the generator's
   escape model and the escape checker changes with it.

4. **`pasture.py` and `compose.py` are god-modules.** `pasture.py` (2540 LOC)
   holds the palette, the ground mixes, the fence/rail/post/cap logic, three byre
   forms (`gable_byre`, `lean_to`, `low_sty`), the shed, yard props, lighting,
   planting, pond, dip pool, runnel, the animal escape model, farmstead planning,
   `build_rung`, `compose_ladder`, and `check_pen`. `compose.py` (1541 LOC) holds
   donor harvest, three compose functions (tower/yard/wall), the `militarize`
   dressing pass, prop/vegetation scatter, `tidy_leaves`, `cap_pillars`,
   `stair_pitch`, the `work_spots` niche ranker, `military_fittings`, and the
   `ARMOURY_LADDER` table. Each is one file doing the work of six to twelve. They
   are exceptionally well-commented — the comments are why the bugs they record
   stayed fixed — but the size is the beta signature the owner is reacting to.

5. **Thresholds live in two places.** `check_fabric`'s `HANGING_MAX`/`HOLES_MAX`/
   `SLAB_RIDERS_MAX` are the gate; `selfgate.AUTHOR_BAND` is a second copy used to
   classify the same findings. They are not derived from one source.

## What is NOT wrong

Honest credit where it is due — much of the framework is senior work:

- **The `Canvas` / `solids` / `halves` core** is the right design, clearly
  motivated by real shipped bugs (the 38 leaves over slabs, the 443 false
  positives from a too-broad rule, the post-cap-under-rail). `inspect_all` reusing
  `_inspect` so the writer and the gate cannot drift is exactly correct.
- **The `--calibrate` discipline, where it exists, is exemplary.** `check_stray`,
  `check_stairs`, `check_fabric` and `calibrate_fabric` each print the author's own
  numbers first and state that a metric firing on his work is a wrong metric, not a
  finding. The documented residuals (`CORPUS_RESIDUAL`, `CORPUS_LADDER_RESIDUAL`)
  are the right mechanism: a measured licence, above which a finding is ours.
- **Checkers read disk, not generator memory.** Universally true. The generators
  cannot mark their own homework on the geometric/escape checks.
- **The four-gate model** (style critic + functional route + fabric + documented
  rules) is the right shape, and the livestock driver actually wires all four
  (`build_livestock.py:100-102`, gating on `verdict` + `problems` + `fabric`).
- **`wall.py`'s invariants** (`BODY_TOP`, `WALK`, `A_MID` shared across every tier
  so a mixed ring has no step) are encoded as constants and re-derived from the
  built piece in tests (`walk_level`, `climb_endpoints`). That is the right way to
  keep a promise.
- **Measured provenance everywhere.** Thresholds carry their measurement in
  comments ("median 8, p95 21, max 30 (`merchant_shop_lvl6`)", `check_integrity:36`).
  The "what was tried and discarded" record in `critic.py:7-20` and `check_fabric`'s
  docstring is how a mature checker documents its own history.
- **No beta markers in code.** No TODO/FIXME/HACK sludge; the half-finished work is
  behind explicit named switches (`ROOF_TO_STAIRS`), not scattered comments.

## Verdict

Yes — this is beta architecture, but **not** because the parts are bad. The parts
are unusually good. It is beta because the good parts were built for one generator
(livestock) and never extended to the other two (military, fortifications), and
because the discipline that makes the good parts trustworthy (calibrate +
self-test + one-command gate) is applied unevenly and enforced by nothing
automated. The framework's central promise — "the always-wrong geometry is refused
as it is written" — is currently true for cattle pens and false for watchtowers.

Top 3 problems, by impact:

1. **The write-time guard and the fabric gate cover only the livestock set.**
   `compose.py` and `wall.py` (together ~3000 LOC, the entire military and
   fortification output) write through raw `Voxels` and are never fabric-checked;
   `build_military.py` has no fabric field and `selfgate` is hardcoded to
   `structure/livestock`. The class of bug the guard exists for can ship in every
   non-livestock piece. (Fixing is structurally one line per generator; see
   `fabric.py:99-109`.)

2. **Checker coverage is patchy and unenforced.** `check_integrity` — the
   "primitives before statistics" layer — has no `--calibrate` and an
   admitted-broken roof gate; `check_pens` has no calibrate and no self-test;
   `check_usable`'s main mode exits 0; `check_fabric`'s calibrate sweeps only 8
   files; and `selfgate` ("one command") runs 3 of 7 checkers. There is no CI and
   no hook runs the one self-test that exists (`calibrate_fabric --selftest`), so
   the trust rests entirely on a human remembering to run the right commands.

3. **God-modules plus copy-pasted constants are a drift factory.** `pasture.py`
   (2540 LOC, 12+ concerns) and `compose.py` (1541 LOC, 8+ concerns) each hold a
   whole subsystem, and the direction maps (`OPPOSITE`/`VEC`), ground mixes
   (`TRODDEN`), `Timber` dataclass, and threshold tables are duplicated across
   `compose`/`wall`/`pasture`/`selfgate`. CLAUDE.md already records that "for one
   afternoon the two copies disagreed" about the slab rule; the same substrate
   exists today for every other duplicated constant, and the checkers' imports from
   `pasture` couple the rules to the generator they audit.

## Related

- [OPEN-WORK](OPEN-WORK.md)
- [STATUS](STATUS.md)
- [.agents/skills/burg-buildings/SKILL.md](../../.agents/skills/burg-buildings/SKILL.md)
- [docs/04-engineering/ARCHITECTURE.md](../04-engineering/ARCHITECTURE.md)
- [docs/05-craft/HOW_WE_WORK.md](../05-craft/HOW_WE_WORK.md)
