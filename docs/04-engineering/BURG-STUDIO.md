# Burg Studio — content viewer + checker dashboard + light NBT editor

A standalone Vite + React + Three.js web app for viewing Burg mod content
(buildings and citizen skins) in 3D, running the Python checkers from a UI,
and touching up individual blocks after the generator has run. It is a dev
and content tool — it is **not** part of the Minecraft mod, is not built by
Gradle, and ships nothing. It exists because the four things a building/skin
session needs every time — *see it real, see the ladder, see the checker
results on the model, fix the one wrong block* — are today four separate
CLI commands, four scattered HTML files, and one renderer with a known
face-culling bug.

## Why it exists

Each pain below is documented in an audit or a skill and is repeated every
session:

- **The Lodestone slab-culling bug.** Lodestone's mesher culls a shared face
  whenever two blocks share a name and the block is flagged self-culling —
  without ever comparing the two shapes — so a bottom slab beside a *top*
  slab loses its face and the roof shows through, reading exactly like a
  missing texture. Vanilla renders it fine; the build was never broken. Two
  rounds were spent guessing at `waterlogged` and rebuilding the roof grammar
  before the cause was read out of the mesher (`CLAUDE.md` "Working habits").
  The fix today is a runtime patch to `scripts/preview.mjs` that rewrites
  `block-flags/non_self_culling.txt` on the fly. Burg Studio owns its own
  renderer and does not have this bug.
- **The checker results and the model live in different places.** `selfgate`
  writes `structures/out/selfgate/report.md` plus one annotated PNG per
  finding — a horizontal layer and both vertical cuts, cell ringed
  (`burg-buildings/SKILL.md` §4). To act on a finding you read the PNG, find
  the coordinate in the report, and re-orient mentally onto the 3D model.
  Burg Studio overlays the flagged cells on the model in the same view.
- **Contact sheets do not exist, and the one that matters most is the one a
  village actually shows.** `AUDIT-skins.md` records that no compliant sheet
  exists: the four views the skin skill mandates before any skin work is
  called done — a 26× head row, no-garment *and* with-garment rows, a crowd
  row of 8–10, and a wealth row — have not been generated, so the samey-roster
  finding has not been eyeballed at the scale a player sees. The only sheets
  on disk predate the `citizen_body_*` set.
- **An agent cannot see a PNG.** `skin_text.py` exists because "you cannot
  see a skin" (`burg-skins/SKILL.md` "You cannot see a PNG") — it letters a
  skin as text by its own ramp so two files are comparable by eye. It is the
  project's workaround for a review surface that is, fundamentally, a picture.
  Burg Studio makes the picture.
- **"Look at lvl 3 beside lvl 5" is impossible today without a CLI.**
  `render_tex.py` can sheet several NBTs into one PNG, but it is a command
  per comparison. The corpus (`plains/**`, 125 NBTs) has no browseable
  gallery; `gallery.html` is a flat-SVG prototype that paints one flat colour
  per block id, which `CLAUDE.md` has said from the start *cannot judge these
  palettes* — cobblestone, stone, andesite and tuff differ by texture.
- **Four scattered HTML viewers.** `tools/viewer/citizens.html` (skins,
  CSS-3D), `tools/structures/out/stylekit/gallery.html` (flat-SVG corpus),
  `scripts/preview.mjs` (Lodestone, per-build), plus the `render_tex.py` /
  `render_png.py` PNG output. Each is a prototype for one view; none is the
  surface an author or an agent works in.

## What it does (scope)

### In scope

1. **Viewer** — buildings and skins in 3D with real block textures, ladder
   navigation (jump rung-to-rung), and side-by-side comparison of any two
   levels of any ladder (ours or the author's).
2. **Checker dashboard** — run `check_fabric`, `describe`, `selfgate`,
   `check_integrity`, `check_stray`, `check_stairs`, `check_pens`,
   `check_usable` from the UI against the file currently loaded, and see the
   findings overlaid on the 3D model as highlighted cells.
3. **Light NBT editor** — click a block in the 3D view; replace it with
   another block, delete it, or add one beside it. For the few touch-ups
   after the generator ran, not for building from scratch. Save back to the
   `.nbt` file.

### Out of scope

- **A skin drawing tool.** Drawing a skin with a mouse was explicitly
  rejected; `draw_citizens.py` owns authoring. Burg Studio renders and
  compares.
- **A visual building builder.** Building geometry from scratch in a UI was
  rejected (`HOW_WE_WORK.md` "What is proven not to work": "The agent
  authoring geometry from parameters"). The generators own authoring; Burg
  Studio renders and touch-edits.
- **Anything that replaces the Python tools.** The checkers and measurements
  stay in `tools/*.py` — they are the source of truth, calibrated against the
  corpus. Burg Studio calls them; it does not reimplement them.
- **Shipped content.** Nothing under `studio/` is part of the mod, the
  Gradle build, or any jar.

## Architecture

Vite + React + Three.js in the browser; a thin Node layer on the same host
reads NBT and PNG off disk and shells out to the Python tools. One process,
one port, started by the author when they sit down to review content.

```
                         studio/ dev server (Node)
                         ┌──────────────────────────────────────────────┐
  disk                   │  /nbt?path=...     read .nbt → JSON voxels     │
  structure/*.nbt  ─────▶│  /png?path=...     read .png  → data URL       │
  textures/*.png  ─────▶│  /atlas            serve Lodestone atlas +     │
                         │                    assets.json (real textures)│
                         │  /check?tool=...   subprocess: python check_* │
                         │                    parse stdout/report.md      │
                         └─────────────┬────────────────────────────────┘
                                       │ JSON / data URL over HTTP
                                       ▼
                         browser (React + Three.js)
                         ┌──────────────────────────────────────────────┐
                         │  NBT → voxel grid → instanced Three.js mesh    │
                         │  skin PNG → humanoid rig (hat shell +0.5,      │
                         │              second layer +0.25)              │
                         │  checker findings → red-highlighted cells      │
                         │  click a cell → edit panel → POST /nbt save    │
                         └──────────────────────────────────────────────┘
```

The Node layer is the only thing with disk and subprocess access. It owns:
reading NBT (via Lodestone's `NbtFile.read` — already a transitive
dependency, see *Open questions*), serving the Lodestone default resource
pack (the same `atlas.png` + `assets.json` that `render_tex.py` already
uses for tile lookup), and spawning `python <tool> <args>` and parsing the
result. The browser is pure render and interaction: it never touches the
filesystem, never runs Python.

THREE.js is already present transitively under the Lodestone skill's
`node_modules`; Vite likewise. The app reuses both rather than introducing a
second copy.

## Sections

### Buildings

The primary surface. Load any `.nbt` under `structure/**`; render it as an
instanced voxel grid with real block textures, correct sub-cell shapes
(bottom slab = lower half, top slab = upper half, plates thin, fences/rails
thin), and correct block-state faces (stairs drawn with their real step, not
as a cube — the approximation `render_tex.py` still makes).

Navigation and comparison:

- **Ladder rail** — for a `<building>.json` with `nbt_levels`, list every
  rung; clicking switches the loaded file. The footprint invariant
  (`UpgradeAction` same-origin) means the camera can hold still across
  rungs, so growth reads as growth, not as a jump.
- **Side-by-side** — pin two rungs (or a rung and its donor) into a split
  view. This is the view that does not exist today and that every audit
  wants: livestock cross-breed similarity, military donor distance, ladder
  development.
- **Donor lookup** — given a generated rung, open the donor it was grafted
  from (`house*` / `house_2*` / `house_3*`) beside it; the cosine-similarity
  number from `describe` sits under each pair.

### Citizens

The 3D rig viewer, subsuming `tools/viewer/citizens.html`. That file is a
working CSS-3D prototype — the rig table, the per-layer tinting, the crowd
grid, the manifest-driven panel — and its logic ports to Three.js directly:
the rig is data (`DATA.rig`, generated by `export_rig.py` out of
`npc_uv.PLAYER_BOXES`, the single owner of the mesh table), and tinting is
a per-pixel multiply, which is what the game does.

Surfaces:

- **Single citizen, 3D** — pick body / hair / beard / headwear / garment
  from the manifest; toggle each layer; apply any tint from `TINTS_BY_WEALTH`
  / `hairTints` to the tintable layers. The hat shell is +0.5, the second
  layer +0.25, exactly as in game — so silhouette reads as the texture's
  alpha, which is the law (`burg-skins/SKILL.md` law 2).
- **Wealth row** — one body at every tier (FADED / UNDYED / DYED / COSTLY),
  so the dead-`wealthOf` finding (`AUDIT-skins.md`) is visible at a glance
  even before it is wired.
- **Crowd row** — 8–10 citizens rolled at random from the body/garment
  pools, the only view that answers "a village reads as one face repainted"
  (`burg-skins/SKILL.md` "Before you report"). Does not exist today.
- **Diff view** — `skin_text.py --diff` reports two numbers; SHAPE is the
  honest one and 35% is the roster floor. Burg Studio shows the pair
  side-by-side with both numbers; the bodies the audit flags (00↔01 at 33%,
  08↔09 at 38%, 02↔13 at 20% torso) are one click.

### Corpus

A read-only gallery of the author's 125 `plains/**` NBTs — the calibration
corpus every measurement is taken against, and the donor pool every
generator harvests. Browse by family (`house`, `house_2`, `house_3`, the
job buildings, the three animal fields), open one in the building viewer,
and read the `describe.py` numbers (kinds, top ids, self-similarity, the
material ladder) beside it. Device overlay: paint each cell by its
`appearance.shape_of` kind so the devices (`burg-buildings/SKILL.md` law 4,
`DEVICES.md`) are visible as a pattern, not as a block list.

`plains/**` is read-only here too (ADR-0002). The corpus gallery never
offers the edit panel.

## The renderer

Burg Studio owns its NBT-to-3D pipeline end to end. It does **not** run the
Lodestone mesher, so it does not inherit the slab-culling bug — a bottom
slab beside a top slab renders both faces, because the renderer compares
occlusion shapes (a half-cell top against a half-cell bottom) rather than
block names. `render_tex.py` already reasons about sub-cell shape correctly
(`height` and `lift` for slabs/plates/flat); the browser renderer lifts
that same model into 3D geometry rather than 2:1 isometric.

Real block textures come from the Lodestone default pack — the same
`atlas.png` + `assets.json` index `render_tex.py`'s `Pack` class reads. The
Node layer serves the atlas and the index; the browser does UV lookup
(block id → blockstate → model → texture slot → atlas rect) once per block
state and caches. Grass/foliage tinting (the `TINTED` map in `render_tex.py`)
is applied in-shader so a ground mixture does not read as a grey chequerboard.

What has to be right, because it has been wrong before:

- **Slab height and lift** — a bottom slab fills the lower half; a top slab
  the upper half; getting this from `solids.top_face` (which reports what a
  cell can *carry*, not its geometry) drew top slabs as full cubes and was
  the "slab has no sides" bug (`render_tex.py` cube_sprite comment).
- **Stair facing** — `facing` is the tall side; on a roof it points toward
  the ridge (`burg-buildings/SKILL.md` law 3). Drawn as a cube this reads
  fine; drawn wrong it reads as a notch.
- **Waterlogged and block states generally** — a waterlogged slab is still a
  slab; the renderer reads state, not just id.
- **The crown is a quarter of the cube** — for skins, every face the
  texture paints must be visible, including `top`/`bottom`
  (`burg-skins/SKILL.md` "The crown is a quarter of the cube"). The 3D rig
  shows all six.

## Python tools integration

The checkers are the source of truth and stay in Python. Burg Studio calls
them, it does not port them. The Node layer spawns `python <tool> <args>`
with the loaded file's path and parses the result:

- **Text output** — `check_fabric`, `check_integrity`, `check_stray`,
  `check_stairs`, `check_usable` print findings as coordinate-bearing lines.
  Parse with a small per-tool adapter (the line formats are stable and
  documented in each checker's docstring); map each finding to `(x, y, z)`
  so the browser can highlight the cell.
- **Report output** — `selfgate` writes `structures/out/selfgate/report.md`
  plus one annotated PNG per finding. Burg Studio reads the report, overlays
  each finding cell on the model, and offers the annotated PNG as a hover
  panel — the layer cut and both vertical cuts that `selfgate` already draws.
- **Structured output** — `describe` exposes `similarity`, `stylekit`
  profiles, etc. as importable functions; where a tool has a Python entry
  point that returns data (rather than printing), the Node layer calls a
  thin `python -c` shim that JSON-dumps the result, avoiding text parsing
  entirely. (See *Open questions* — which tools already have this and which
  need a shim is undecided.)

Findings overlay onto the 3D model as red-ringed cells, classified the way
`selfgate` classifies them: `FAULT` (worse than his worst file) vs `his band`
(look, do not fix). The distinction is the one the skill insists on
(`burg-buildings/SKILL.md` §4) and it is what stops a metric firing on the
author's own work from being read as a finding.

`--calibrate` is a first-class button: run any checker against the author's
corpus first, in the same view, so a metric that fires on his work is seen
to be a wrong metric before it is believed on ours.

## NBT editor

Single-block granularity, invoked from the 3D view: click a cell → a panel
offers *replace / delete / add beside*. The replacement picker is the block
palette from the loaded file plus the vanilla block list the renderer knows.
Saving writes the full structure back to the `.nbt` file via Lodestone's
`NbtFile.write`, gzip, same `DataVersion` — byte-for-byte the format
`UpgradeAction` places in the world.

Guardrails, hard:

- **`plains/**` is read-only in the editor, not just in the corpus gallery**
  (ADR-0002). The editor panel does not open for files under
  `structure/plains/`. The one exception the ruling names — byte-exact CRLF
  repair — stays in `tools/repair_crlf_nbt.py`.
- **Versioned saves.** Before any overwrite, copy the current file to
  `<file>.bak-<timestamp>.nbt` alongside it. Generated output under
  `structure/military/`, `structure/livestock/` is untracked, so git will
  not bring a bad edit back; the backup is the safety net.
- **The write-time guard runs on save.** `fabric.Canvas.inspect_all` is the
  guard that refuses a cube on a bottom slab, a rail over one, and a roof
  block bearing on nothing (`burg-buildings/SKILL.md` §5, `AUDIT-framework.md`
  "Write-time guard"). An edit that would introduce one of those is refused
  in the panel before the file is written — the same refusal the generator
  gets. (See *Open questions* — this means a Python round-trip on save.)
- **Not a schematic editor.** No copy/paste of regions, no brushes, no
  box-fill. One block at a time. Anything more is the generator's job.

## Layout

`studio/` at the repo root, a sibling of `common/`, `tools/`, `docs/`. Not
`web/` — that name implies the mod's own frontend, and this is a dev tool.

```
studio/
├── package.json          vite, react, three, lodestone (the parser only)
├── vite.config.{ts,js}   dev server, /nbt /png /atlas /check proxies
├── src/
│   ├── server/           Node layer: NBT read, atlas serve, python subprocess
│   ├── viewer/           Three.js building renderer + skin rig
│   ├── dashboard/        checker panels, findings overlay, describe numbers
│   ├── editor/           block picker, save pipeline, guard round-trip
│   └── app/              React shell, routing between the three sections
└── README.md             how to run it (one command), what it does not do
```

Added to `.gitignore`: `studio/node_modules/` and `studio/dist/`. The source
commits; the deps and build output do not. Not referenced from the Gradle
build, the root `settings.gradle`, or any jar. `.gitattributes` already
keeps `*.nbt binary`; the studio never commits NBT anyway (it reads and
writes the existing tree).

## Open questions

1. **NBT parser — reuse Lodestone's JS or hand-write one?** Lodestone's
   `NbtFile.read` is already a transitive dependency, handles gzip/zlib
   detection, and round-trips with `write` (`references/lodestone/nbt.md`).
   The alternative is a ~200-line hand-written JS parser. *Lean: reuse
   Lodestone's.* It is one fewer format to maintain and the save path gets
   `write` for free. The cost is taking a Lodestone dependency into the
   studio app proper (not just its atlas) — needs a decision.
2. **Checker output format — JSON, or text-parse?** Some tools
   (`check_fabric`, `check_stray`, `check_stairs`) print stable
   coordinate-bearing lines; `selfgate` writes a markdown report + PNGs;
   `describe` exposes Python functions. The clean answer is a thin
   `python -c` JSON shim per tool, but that is new surface on the Python
   side. *Open: which tools already return data vs print, and do we add a
   `--json` flag to each checker or keep parsing text?* The audits read the
   text output fine; a shim is cleaner but touches `tools/`.
3. **Block textures in the browser — serve the atlas, or pre-bake?**
   `render_tex.py` already does blockstate → model → texture slot → atlas
   rect resolution server-side in Python. Burg Studio needs the same
   resolution in the browser. *Open: port that lookup to JS once (with the
   atlas + `assets.json` served as static assets), or have the Node layer
   emit a pre-resolved `{blockId: {top, side}}` map per file?* The JS port
   is reusable; the pre-bake is less code but per-file work.
4. **Save pipeline — does the write-time guard run in Python or JS?** The
   guard (`fabric.Canvas`) is Python and calibrated there; re-implementing
   it in JS would be the exact drift substrate `AUDIT-framework.md` warns
   about. *Lean: round-trip through Python on save* (serialize the edited
   voxel grid to a temp NBT, run `Canvas.inspect_all`, refuse on fault).
   Slower than a pure-JS check, but it is the same check the generator
   gets, and it cannot drift.

## Status

`design only — not started`

## Related

- [AUDIT-framework](../07-state/AUDIT-framework.md) — the guard covers half
  the output; checker coverage is patchy; god-modules + duplicated constants
- [AUDIT-military](../07-state/AUDIT-military.md) — 36/43 files fabric-FAULT;
  the watchtower deck built from a forbidden construction; donor-dependency
- [AUDIT-livestock](../07-state/AUDIT-livestock.md) — three breeds collapse
  into one at the low rungs; `packed_mud` leak; needs side-by-side to see
- [AUDIT-skins](../07-state/AUDIT-skins.md) — wealth dead at runtime; roster
  is a few faces repainted; no compliant contact sheet exists
- [HOW_WE_WORK](../05-craft/HOW_WE_WORK.md) — the five-layer protocol;
  "agent judging looks from renders" and "flat-colour renders cannot judge
  these palettes" are the proven-not-to-work list Burg Studio's renderer
  has to clear
- [.agents/skills/burg-buildings/SKILL.md](../../.agents/skills/burg-buildings/SKILL.md)
  — the five laws; the checker overlay classifications
- [.agents/skills/burg-skins/SKILL.md](../../.agents/skills/burg-skins/SKILL.md)
  — the nine laws; "you cannot see a PNG"; the four-view contact sheet
- [ARCHITECTURE](ARCHITECTURE.md) — the mod's subsystem map; Burg Studio is
  outside it
- [ADR-0002-plains-readonly](../06-decisions/ADR-0002-plains-readonly.md) —
  the corpus is read-only; the editor's hard guard
