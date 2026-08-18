# Build language

Visual devices extracted from the reference set in `house.mrs/`, written as
recipes rather than as material lists. The mod is a progression mod: the first
levels are oak and cobblestone by definition, so a device is only useful here if
it can be built from the author's own vocabulary and then *refined* later.

Every recipe below has a tier-1 form in oak / cobblestone / mossy cobblestone —
all three already present in the author's 127 structures.

Source images referenced by their leading hash characters.

---

## 1. Wall articulation

The single biggest gap in the generated buildings was flat, uniform wall fields.
Every reference wall carries rhythm and tonal depth.

| device | what it is | tier 1 recipe |
|---|---|---|
| **piers** | vertical pilasters projecting 1 from the face, at a regular interval | `cobblestone` columns, spacing 3, projecting 1 |
| **quoins** | corners in the pier material but **flush**, not projecting | `cobblestone` at the corner cells |
| **two-tone** | piers light, recessed bays dark | piers `cobblestone`, bays `mossy_cobblestone` |
| **vertical streaking** | tonal bands running *vertically* through the field, not random speckle (`2b41`, `81de`) | per-pier bay assigned its own mossy/plain ratio |
| **ragged base** | the wall does not meet the ground in a straight line: columns of stone run down to different depths, rubble scattered at the foot (`2b41`, `81de`) | extend 1-3 cells of each pier below grade, scatter loose `cobblestone` at the foot |
| **arrow loops** | 1-block dark recesses at a consistent height in the piers (`1b95`, `81de`) | recess one cell, leave air, or `glass_pane` |
| **arch heads** | bay tops closed with stairs springing off the piers | `cobblestone_stairs` `half=top` at the bay ends |

## 2. Wall head

| device | what it is | tier 1 recipe |
|---|---|---|
| **corbel table** | the head projects outward on a row of brackets with gaps between them — the machicolation (`9432`, `2b41`, `1b95`) | `cobblestone_stairs` `half=top` projecting 1, one per cell |
| **capped merlons** | crenellations finished with a slab, not left as bare cubes | `cobblestone_wall` + `cobblestone_slab` `type=bottom` |
| **timber hoarding** | a **wooden fighting gallery** built on top of a stone wall: projecting floor, fence railing, dark loopholes, shed roof over it (`0fee`, `81de`) | `oak_slab` floor on projecting `oak_log` beams, `oak_fence` rail, `oak_stairs` roof |
| **stepped top profile** | the wall top steps down in terraces rather than running level (`55a3`) | drop the parapet 1-2 courses over part of the run |

`timber hoarding` is the most valuable find in the set: it is a large, obviously
military upgrade that costs only oak over an existing cobblestone wall, which
makes it the natural top tier for `wall_segment`.

## 3. Volume and massing

| device | what it is | tier 1 recipe |
|---|---|---|
| **jetty** | upper storey projecting beyond the stone base, carried on a visible beam course (`078d`, `440b`, `2b41`) | `oak_log` beam ring projecting 1, floor above it |
| **beam ends** | short log stubs projecting from the top of posts (`1b95`, `725f`, `81de`) | `oak_log` with the horizontal axis, one cell proud |
| **rafter / dentil course** | a repeating row of small blocks under the eave (`078d`, `2b41`, `725f`) | alternating `oak_slab` / air under the eave line |
| **cluster massing** | several roof planes at different heights instead of one box (`0c6c`, `4c7e`) | attach a lower lean-to to the main volume |
| **dominant chimney** | a tall tapering stack that anchors the silhouette (`0c6c`, `4c7e`) | `cobblestone` stack, `cobblestone_wall` cap |
| **open work bay** | a deep unglazed recess under the roof for a workshop (`0c6c`, `40fe`) | omit a wall panel, roof it, put the forge inside |
| **external stair** | stone steps up the outside to an upper door or wall walk (`b42d`, `35962`, `40fe`) | `cobblestone_stairs` run with an `oak_fence` rail |

## 4. Openings

| device | tier 1 recipe |
|---|---|
| **framed doorway** — entrance surround in a contrasting material, arched head | `cobblestone` jambs, `cobblestone_stairs` arch |
| **panel grid** — log posts + horizontal beams forming rectangles, each panel infilled differently: solid / window / railing (`440b`, `078d`) | `oak_log` posts, `oak_planks` infill, `glass_pane` or `oak_fence` in chosen panels |
| **shutters** — trapdoor pairs flanking a window | `oak_trapdoor`, stacked vertically (the author's own habit: 1-3 vertical pairs per house, never horizontal runs) |
| **crossed bracing** — an X of beams in the gable tympanum (`4c7e`) | `oak_log` diagonal-ish step pattern |
| **window boxes** — planter troughs under windows (`078d`, `35962`, `4c7e`) | `oak_trapdoor` front + `oak_leaves` |

## 5. Roof

| device | tier 1 recipe |
|---|---|
| **stair pitch stepping inward** one course per layer, slab ridge — the author's own method, 82% of his builds ≥10 tall | `oak_stairs` per side + `oak_slab` ridge |
| **horizontal shingle courses** — overlapping stair/slab courses so the slope reads as layered, not smooth (`5985`, `1150`, `725f`) | alternate `oak_stairs` and `oak_slab` per course |
| **deep eave overhang** with the eave line emphasised | project the lowest roof course 1-2 beyond the wall |
| **dormers** — small gabled windows in the roof plane (`078d`, `1150`) | 1-wide gable with `glass_pane` |
| **roof planters** — leaves and pots sitting on the roof (`00ed`, `81de`, `40fe`) | `oak_leaves` clusters on the slope |

## 6. Ground

| device | tier 1 recipe |
|---|---|
| **trodden apron** — worn earth hugging the walls, fading to grass at the plot edge | `coarse_dirt` / `dirt_path` near the shell, `grass_block` beyond |
| **dashed path** — the path is patches, not a solid ribbon (`35962`, `389f`) | scattered `dirt_path` with gaps |
| **distinct yard floor** — the drill ground is a different material from the surroundings, clearly bounded (`40fe`) | `coarse_dirt` inside the walls |
| **terrain layering** — ground steps up in slabs, path edged | `cobblestone_slab` steps |
| **base vegetation** — softens the wall/ground junction, asymmetric and heaviest on one side (`9432`, `55a3`) | `short_grass`, `oak_leaves` clumps against one face only |

## 7. Fittings

| device | tier 1 recipe |
|---|---|
| **stone-pier fence** — masonry posts with fence infill between them (`05d4`, `389f`). This is what a palisade should be; a line of loose logs reads as litter | `cobblestone` post + `cobblestone_slab` cap every 3, `oak_fence` between |
| **lamp post** — masonry base, timber bracket, lantern on a chain (`2610`, `55a3`) | `cobblestone` plinth, `oak_stairs` bracket, `lantern` |
| **bracket sign** — projecting beam with a barrel or sign hung on a chain (`1150`, `55a3`) | `oak_log` arm + `chain` + `barrel` |
| **pier-cap planter** — chiselled accent block capping a pier with greenery on top (`55a3`). The cap must be a **top** slab or a full block: greenery over a *bottom* slab floats half a cell, and this entry as first written produced 38 of them | `cobblestone_slab` `type=top` cap + `oak_leaves`, or the greenery at the pier foot |
| **firewood stacks** (`35962`) | `oak_log` with horizontal axis, stacked against a wall |
| **hoist beam** — projecting beam with a pulley at a loft opening (`0c6c`) | `oak_log` arm + `chain` |

---

## Composition targets per building

Taken from the two annotated references, which are worth more than any single
device: `40fe` (a labelled garrison compound) and `00ed` (a small keep).

### watchtower — `40fe`, `00ed`
**Squat, not thin.** Stone base with an **external stair wrapping it**, an open
**roofed observation deck** on posts with a fence rail. The current generated
tower is a tall narrow shaft with an internal ladder, which is the wrong shape.

### wall_segment — `0fee`, `81de`, `9432`
piers with ragged bases and arrow loops → corbel table → parapet.
Top tier replaces the parapet with a **timber hoarding gallery under a shed
roof**.

### gatehouse — `440b`, `078d`, `35962`
Stone base, two piers, **arched passage**; **jettied half-timber upper storey**
on a beam course, panel grid with mixed infill; deep dark roof. Optionally a
**timber beam pergola** over the outer face of the arch.

### barracks — `40fe` "garrison quarters"
Long two-storey block with an **open timber colonnade along the ground floor**,
tiled roof, planters. The current stretched `house_2` is already close; the
missing device is the ground-floor arcade.

### armory — `0c6c`, `4c7e`
**Dominant chimney** plus an **open work bay** under the roof. The current
stretched `house_3` has the forge but reads as a house; the chimney needs to be
taller and the work bay opened up.

### training_yard — `40fe`
Not a fenced patch of dirt. A **walled compound** with a distinct sandy floor,
**lean-to canopies along the inside of the walls** (covered shooting positions),
**target butts** against one wall, and a gate. This is the composition the
current version is missing entirely.

---

## Coverage

Twenty of the thirty-nine reference images were read in depth. The catalogue
converged well before the end — the last several images contributed no new
devices, only further examples of ones already listed. The set breaks down as
roughly: 2 annotated compound plans, 4 wall/gate studies, 3 isolated detail
studies, and the remainder cottages and manors that repeat the same devices in
different materials.

## Implementation status

Done, in `tools/structures/facade.py`:
piers, quoins, two-tone, arch heads, corbel table, string course, capped
merlons, **vertical streaking** (per-column mossy ratio rather than a per-cell
coin flip), **ragged base** (piers running to varying depth with spill at the
foot).

Done, in `tools/structures/compose.py`:
- **`training_yard` recomposed as a walled compound** — crenellated curtain
  wall, bounded trodden drill floor, lean-to canopies along up to three inner
  walls, target butts, gate gap with a timber lintel.
- **watchtower reshaped squat** — wide short shaft, **external stair wrapping
  the base**, **open roofed observation deck** on corner posts with a rail.
  Levels 3+ close the deck into battlements, 5+ into a pitched-roof keep.

Done, from the user's review of `barracks` and `armory`:
- **campfire on the floor**, no cobblestone pedestal.
- **banners gated to lvl4+**; `podzol` dropped, `packed_mud` sanctioned (the
  first block used from outside the author's corpus).
- **workstations no longer duplicate** when a slice is repeated — `furnace`,
  `crafting_table`, `smoker`, `cauldron` and friends joined `NO_DUPLICATE`.
  Beds deliberately still duplicate: extra bunks in a longer barracks are the
  point.
- **slice choice by content similarity**, not by the detected shell. The old
  behaviour copied a gable-end slice into the middle of `barracks_lvl3`'s second
  floor and cut the room in half. Middle-of-run slices score 0.47–0.62 against
  0.20–0.34 for end caps, so the threshold is relative (≥ 0.8 × best) with a
  0.35 floor below which stretching is refused.
- **posts capped with a stair** as in `house_2_lvl6`, and only on lvl4+. The
  rough half-slab cap on low tiers is the author's intent, not a defect.

Done, in `tools/structures/wall.py` — **the fortification set**:

Four piece kinds x five levels, sharing one material ladder. A wall is not a
building with the roof off, so this has its own grammar: two faces and a walk
between them, with nearly all the visual interest coming from the difference
between the faces.

| level | recipe |
|---|---|
| 0 | earth bank behind a log stockade; the bank steps down inward so it can be walked up |
| 1 | timber wall on a two-course cobble plinth, fence rail at the head |
| 2 | cobblestone: piers, vertical streaking, mossy skirt, arrow loops, capped merlons |
| 3 | + projecting parapet on brackets with shadow slots, plank wall walk, planters, hanging lanterns |
| 4 | + **timber hoarding gallery** under an oversailing shed roof |

Devices implemented here, each traced to its reference: vertical streaking,
mossy skirt of varying height, rubble spill, arrow loops in pairs, projecting
parapet, brackets with the shadow slot between them, hanging lantern on a chain,
capped merlons at irregular spacing, dressed wall walk, timber hoarding, beam
ends, external stair, **stepped top profile**, clumped climbing vegetation.

The four kinds:

* **`wall_segment`** — the straight run, most of the perimeter.
* **`wall_corner`** — the piece that makes the chain turn, and therefore the
  reason the ring closes at all. The elbow thickens into a bastion standing
  above the curtain (`00ed`).
* **`gatehouse`** — arched passage through the thickness, double doors, flanking
  towers of unequal height, jettied timber storey on a visible beam course
  (`440b`, `2b41`). Also carries a street connector so a road grows out of it.
* **`wall_tower`** — a flanking tower projecting two cells beyond the face, with
  the **external stair** that is the only access to the wall walk in the set.

Two constraints, both measured rather than assumed:

* **The footprint is identical at every level; only the height changes.** The
  author does this in all 98 of his buildings without exception. It also means an
  upgrade never has to fit a bigger plan and a mixed-tier ring still lines up.
* **The connector sits on the middle cell of the three-cell thickness.** Since
  placement positions a piece by its connector rather than its box, that one rule
  is what makes any piece meet any other whatever margins it carries.

`tools/ring_preview.py` assembles a perimeter offline by replaying the mod's own
`computeRequiredRotation` / `computeCandidatePosition` / `StructureTemplate.transform`,
so "the ring closes" is verified rather than asserted. It caught two real bugs:
segments that could not chain at all (both their connectors were terminators, and
a terminator never becomes a free connection point) and a ring that closed
perfectly but inside out.

Done, in `tools/structures/pasture.py` — **the livestock set**:

Three buildings x six levels, written by `tools/build_livestock.py` into
`structure/livestock/{cow_pasture,pig_sty,sheep_fold}/`. Each one is a
**farmstead**, not a pen: the author's own house, with the animal's yard wrapped
round it and the byre built against the house wall.

The building is **his**. He built seven levels of `house` — footprint 9x11 at
every one, palette 27 rising to 57 — so the ladder installs `house`,
`house_lvl1`, `house_lvl2`, `house_lvl3`, `house_lvl4` and `house_lvl6` in turn
and the farmstead's whole material progression is the author's work. Ours is the
yard, the byre and the ground. Two earlier attempts to *compose* the building
were rejected — a free-standing shed read as a plank tower, and a yard parked
alongside the house read as a house with a fenced strip next to it.

**The yard is an L** wrapping the back and one flank, so the house stands inside
its own farmstead: two of its walls are boundary, the front stays clear for the
street and the door, and the two arms do different work — the narrow strip behind
the house is the trodden working yard (byre, hay rack, muck heap), the wide flank
is grazing.

**Yard area differs per animal, from life.** Cattle need the most ground per head
and are kept at pasture; sheep graze wide but are folded tight and want a holding
pen to be sorted and shorn in; a sty is deliberately compact and churned to mud,
because pigs root and wallow rather than graze. Same house, three yards: 11x11,
9x10, 7x8 on plots of 22x17, 20x17 and 18x16 — against the author's own
`pig_farm` at 26x19.

Grafting a donor forces three things to be measured rather than assumed, each of
which cost a cycle and each caught by the functional gate:

* `house.nbt` is **eight** cells wide inside its nine-wide box; `house_lvl2` and
  up are nine. Assuming the wall sat at the box edge left a one-cell corridor
  between house and yard, open to the plot edge, and the animals walked down it.
  The donor is now shifted east so its wall lands on the same column at every
  rung — the plot must be identical at every level, because `UpgradeAction`
  replaces the NBT at the same origin.
* The donor plants tufts of grass along its own walls, so *is the neighbour cell
  occupied* is the wrong test for *is this run closed*. A run is closed only by
  two solid courses.
* Donor levels ship a raw `minecraft:villager`, an item frame, up to three street
  connectors and pre-1.20.3 `minecraft:grass` — dropped or renamed on load.

| level | house | yard |
|---|---|---|
| 0 | `house` | crooked oak fence, a drinking puddle dug into the terrain, one bale |
| 1 | `house_lvl1` | fence put straight on capped posts, open byre off the house wall |
| 2 | `house_lvl2` | fence framed on capped posts, kerbed trough and a filled cauldron, hay rack |
| 3 | `house_lvl3` | byre gabled and shuttered, muck heap, holding pen for a flock, worn paths |
| 4 | `house_lvl4` | byre on a stone plinth, posts capped, a lantern **under cover** — and the donor's two beds, which is where the JSON grants residents |
| 5 | `house_lvl6` | the byre run out to full length under a deep eave |

The byre is a **lean-to against the house's east wall**, roofed off it with a
single pitch falling into the yard: the longhouse arrangement, so the house
carries the back side and the two volumes read as one farmstead rather than two
buildings. Its eave sits at y=3 — a byre with its posts at y=4 and a ridge at y=6
over a four-cell footprint is a tower with a lid, which is what an earlier version
rendered as.

The three breeds are not one yard with a different mob in it: the pasture is
grazed and open with a milking corner, the sty is churned to `mud` around a
wallow and fed at the house door, the fold is dry stone with a holding pen, a
shearing bench and a wool store.

### The escape model — an animal jumps a full block

This is the fact the whole boundary design turns on, and the first version of
the check did not know it. `pasture.enclosed` only asked whether the ring was
solid at animal height, and by that measure every pen was sound. But a cow does
not need a hole in the fence; it needs a **step**:

| thing | top, above the yard floor | reachable by an animal |
|---|---|---|
| bottom slab, stair | +0.5 | walks up |
| full block | +1.0 | jumps up |
| fence, wall, gate | +1.5 | only from a full block beside it |

So anything a full block high next to the boundary is a mounting block: the
animal hops onto it and steps over the rail from there. `pasture.escape_routes`
models it — full-block jump, free falls, closed gates and doors impassable
(an animal opens nothing), railings perchable, two cells of headroom for a
1.4-tall cow — and it found the leak immediately: **the log posts in the fence
line itself**. Every pen had a boundary with no hole in it anywhere and animals
walked out over its own posts.

Two rules came out of that and both are enforced in the generator:

* **Every full block in a boundary run carries a rail or a slab on top.**
  `_capped_post` exists for this reason, not for looks.
* **A one-cell clear lane inside the fence**, which bales, cauldrons, composters
  and benches keep off. The animals may stand wherever they like.

The open byre also gained a solid back wall for the same reason: its back row
*is* the boundary, and the hay rack in front of a bare rail was a step out.

`tools/check_pens.py` re-checks the **shipped files** rather than the generator's
own idea of the pen: it reads the NBT back off disk and floods from the animals'
recorded positions. Verified against negative controls — a punched fence, a bale
parked inside against the rail, and an uncapped post all report LEAKS, while all
18 shipped files hold.

### A farm fence stays timber

Ruled by the user and it is the realistic call: **the boundary never turns to
stone.** A farmer with a better year buys straighter timber, closer posts, boards
and a proper gate; he does not rebuild his pasture fence in masonry. So the
progression runs inside oak — `rail` → `double` (a hurdle you cannot lean over) →
`panel` (boarded plank with a rail over it) — and differs per animal: cattle keep
airy post-and-rail, sheep get close-set doubled hurdles, pigs are boarded early
because they push and root.

Measured after the change: **zero stone blocks at fence height anywhere in the 18
files.** What stone remains is where stone belongs on a farm — 1249 blocks in the
author's grafted house, 76 in the trough kerbs and the dip basin, 25 in the byre
plinth, and 7 slabs at ground level under a run (kerb, not fence). The stone-pier
fence of `05d4` remains catalogued above as a fortification and garden device.

### What a rail connects to — learned from the corpus, not from memory

The connection rule was **measured off the author's 121 files** instead of being
recalled from the vanilla source: for every fence, pane and wall in the corpus,
which neighbour ids carried the connection flag.

| connects | does not connect |
|---|---|
| `oak_planks` 716/12, `oak_log` 192/8, `cobblestone` 148/5, `white_terracotta` 56/0, `mossy_cobblestone` 43/0, `hay_block` 41/0, `stripped_oak_log` 38/0, `stone` 33/6, `crafting_table` 5/0 | `oak_slab` 1/115, `oak_stairs` 17/52, `oak_trapdoor` 2/38, `stone_slab` 5/38, `white_bed` 0/13, `oak_leaves` 0/516, plants, pots, lanterns, chains, torches, pressure plates |

The finding that mattered: **a fence does not connect to a `*_wall` block** — 0 of
8 in his files, and 0 of 8 again when measured from the wall's side. The first
version had them connecting, which is a wrong property on every rail beside the
sheep fold's dry-stone infill. Switching to the measured rule dropped the
author's own disagreement count from *29 files, worst 20* to *25 files, worst 8* —
the rule now agrees with the man who placed the blocks.

The same bug had a second face: `andesite` was missing from the solid list, so a
stone pier counted as thin air and the containment check walked a herd straight
through it. The animal barrier is now decided by the movement model rather than by
a hand-written list of ids.

### The boundary line — no gaps, no doubles

Two more measured metrics, `check_fabric.line_faults`:

| metric | author max | p95 | median | files with any |
|---|---|---|---|---|
| rails meeting only diagonally | 4 | 2 | 0 | 16 / 121 |
| parallel runs with a dead cell between | 7 | 5 | 1 | 72 / 121 |

Both were far outside his band and both were real:

* **Gaps** — 46 diagonal steps across the set, against his worst file's 4. A
  clipped corner steps diagonally, and a fence connects to nothing diagonally, so
  the run reads as broken while the escape model stays happy (nothing walks
  through a corner either). `close_diagonals` fills the corner cell, preferring
  one inside the yard and falling back to the apron — which is the author's own
  two-cell staircase. Now 0 in 16 of 18 files, 4 in the worst.
* **Doubles** — the yard was sized to the donor's *box*, and the donor's building
  does not fill it: `house.nbt` is eight cells wide in a nine-wide box and every
  level starts a cell in from the north edge. That left a one-cell dead corridor
  between the yard fence and the real wall — the fence built twice, exactly as
  reported. Three fixes: `house_bounds` measures all four walls, `absorb_pockets`
  takes any cell the apron cannot reach into the yard so the boundary follows the
  building's real silhouette, and a strip too shallow to be a yard is no longer
  built at all (the compact sty has none). Per-file doubles are now 1–5 against
  his max of 7, and about half of the remainder are fences **he** placed.

One more invariant came out of it. `close_diagonals` runs last, so a prop that was
placed a safe two cells from the boundary can end up against a brand-new rail —
and the animals left over a muck heap that way. `clear_mounts` sweeps afterwards
and removes any full block left standing beside a rail, leaving a scuff of
`coarse_dirt` where it stood. Structure is safe by construction: a post or a
plinth carries something in the cell above it, and only a block with air above is
a step.

### A slab is half a cell

`oak_slab` looks like a block and is half air, and that half is where things end
up floating. Measured over the author's 121 files against my 18:

| | cubes sitting in the empty half above a bottom slab |
|---|---|
| author | **6** in 121 files, max 2 per file — and every one is an `oak_trapdoor`, which attaches to a side and reads fine |
| mine, before | **39**, up to 11 per file, and **38 of them `oak_leaves`** |

Those were the pier-cap planters: a bottom slab caps the pier at +1.5 and a leaf
block in the cell above starts at +2.0, so the greenery hung half a block clear of
what was supposedly holding it. The planting moved to the **foot** of the pier —
his own base-vegetation device, which sits flush — and the rule now lives in
`fabric.Canvas`, which **refuses the write** rather than reporting it afterwards;
`check_fabric.slab_faults` delegates to it so there is only one copy of the rule.

The reverse is **not** a fault: a top slab with nothing under it occurs 2846 times
in his corpus, up to 90 in one file, because a top slab is a step, a table top and
a railing surface in its own right.

Nor is a **thin** support a fault, and getting that wrong cost a round. The first
write-time rule asked whether the support filled its whole footprint, which reads as
sensible and is not his language at all:

| stack | his corpus (125 files) | verdict |
|---|---|---|
| cube on a **fence post** | 398 in 90 files | his idiom — a post fills its cell vertically |
| fence on a fence | 792 in 103 files | his idiom |
| block over a trapdoor / pressure plate / bed / carpet | 45 in 29 files | floor fittings, flush, fine |
| **cube** over a **bottom slab** | **0** | the fault |
| **rail** over a bottom slab | 1 (`house_3_lvl6`) | recorded as a residual, not a licence |

The footprint rule reported **443 faults on a build that had none**. `solids.half_step`
now names the trap exactly — a bottom slab, by shape *and* by id, so a bed drawn with
the same half-cell shape is not caught — and `calibrate_fabric.py` replays the rules
over his corpus to prove they are silent there before they are trusted.

Fixing this also corrected the boundary metrics. `line_faults` had been asking
whether the corner cell held a *barrier*, which reported 14 false gaps inside the
author's own house where his fences meet across a slab or a trapdoor. Anything
solid bridges a corner visually, and with that rule the generated boundary has
**zero** diagonal gaps against his 12.

### The fabric check — connected fences, whole roofs

`tools/check_fabric.py`, and its thresholds were **measured over the author's 115
building-like NBTs before anything was believed**:

| metric | author max | p95 | median | files with any | verdict |
|---|---|---|---|---|---|
| roof blocks hanging in air | **0** | 0 | 0 | **0 / 115** | hard fault |
| gaps in a roof plane | 2 | 1 | 0 | 22 / 115 | fault above 2 |
| fence props disagreeing with the grid | 20 | 4 | 0 | 29 / 115 | hard fault for generated files |
| rails connecting to nothing | 36 | 19 | 5 | 99 / 115 | information only |
| enclosed cells open to the sky | 7 | 5 | 0 | 25 / 115 | information only |

Zero hanging roof blocks across every file the author ever built makes that the
sharpest single test in this repo. A rail connecting to nothing, by contrast, is
his own idiom — a free-standing post, a leg under a projecting element, 36 of them
in `merchant_shop_lvl6` — so failing it would have been the checker being wrong.
An earlier version of this file also failed *a lean-to course that meets nothing
inward* and reported 35 in `house_lvl6`; that test is gone.

It found four real defects, all of them mine:

* **Post caps read as stubs.** A capped boundary post carried an `oak_fence` on
  top, which connects on no side and renders as a rail sticking out of a post —
  15 of them in one yard, and the author does it nowhere. Caps are
  `oak_slab` now, like the stone piers, and the jump is still defeated because a
  slab top sits at +1.5.
* **`tidy_leaves` was stripping the author's roof planters.** It removes leaves
  with no neighbour; on a grafted donor that means the six leaf clusters
  `house.nbt` carries *on its roof*, and pulling them out left holes in the middle
  of his roof plane. It no longer runs on farmsteads.
* **`cap_pillars` was re-roofing the donor.** 16 cells changed per build, some at
  roof level inside his own pitch, which is where the remaining holes came from —
  and it overwrote the boundary post caps with stairs. Also gone: the byre's posts
  stand under a beam and a roof, so there is nothing left to cap.
* **The byre roof stopped one cell short of the house**, leaving a slot open to
  the sky along the whole join. The top course now lands on the wall column.

Both military-set finishing passes therefore have a rule attached: **never run a
finishing pass over a grafted donor.** The author's building arrives finished.

What remains is one gap per lvl4 file, and it is the author's own: a two-cell slot
in `house_lvl4`'s roof, which only becomes *detectable* because the byre's junction
slab gives it a third roof neighbour. Rendered side by side with the untouched
donor, the roof is identical. It is his design and it stays.

Also fixed while building this: `traverse.surface` treated a jigsaw connector as
unstandable, so the cell above every entry connector was a non-node — and since
the connector sits directly in front of the gate, that removed the only
orthogonal approach to it. It now resolves to its `final_state`.

`pasture.py` keeps **its own palette** rather than importing `wall.py`'s: a farm
is oak, cobblestone and mossy cobblestone, and the fortification material ladder
is a different vocabulary with a different job.

Still open, in order of value:
1. jetty with a visible beam course, panel-grid infill (`440b`) — done for the
   gatehouse, not yet for houses
2. ground-floor arcade for the barracks (`40fe`)
3. dominant chimney and open work bay for the armory (`0c6c`)
4. `barracks` and `barracks_lvl1` have no beds at all — their donors
   (`house_2`, `house_2_lvl1`) contain none — while the JSON declares
   `residents: 4`. Needs a different donor, added bunks, or `residents: 0`
   until lvl2.
5. A **mirrored corner**, for reflex turns. A single corner piece can only turn
   one way: `attemptPlacement` hardcodes `Mirror.NONE`, so rotation alone cannot
   produce the opposite hand. Four same-handed corners close a ring, which is why
   this is not urgent, but it would add variety to the outline.
6. Zoning — houses outside the wall, garrison inside — needs Java in
   `attemptPlacement`. Inside/outside is not expressible in the connector graph.

## Layout

One folder per building, one level deeper than the author's own
`plains/houses` split:

```
structure/military/watchtower/watchtower.nbt
                             watchtower_lvl1.nbt … _lvl6.nbt
                   barracks/  armory/  training_yard/
                   wall_segment/  wall_corner/  wall_tower/  gatehouse/
structure/livestock/cow_pasture/cow_pasture.nbt
                               cow_pasture_lvl1.nbt … _lvl5.nbt
                    pig_sty/  sheep_fold/
```

Generated sets live **beside** `plains/`, never inside it: `structure/plains/**`
is the author's finished work and is read-only for us. We read it — harvest block
states, graft donors, measure statistics — and write only to our own folders.

Resource ids follow: `burg:military/watchtower/watchtower_lvl3`,
`burg:livestock/sheep_fold/sheep_fold_lvl5`.
`build_military.py` writes into these folders directly.
