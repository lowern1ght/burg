# Check rules handbook — what the structure gates actually prove

> Recorded 2026-07-31 from the TypeScript engine, the original Python checkers,
> and [`bicalibration-report.json`](../../studio/scripts/bicalibration-report.json).
> This is a handbook of implemented rules, not a claim that geometry can judge
> beauty. A green check means the named defect was not found. It does not mean the
> building reads well in game.

## Purpose

Burg's structure checks answer questions that palette shares and screenshots do
not:

- does a block occupy the volume the generator assumes it occupies?
- is a fence connected in the shipped NBT, rather than merely intended to be?
- is a roof a continuous supported plane?
- do walls, doors and rooms exist as usable geometry?
- can a player enter the building and reach each real storey?
- did an upgrade remove equipment that an earlier rung had already built?

The system has three layers.

1. [`appearance.ts`](../../studio/src/engine/appearance.ts) classifies the shape
   of every block: full cell, slab, stair, plate, flat, post, door, tiny or plant.
2. [`solids.ts`](../../studio/src/engine/solids.ts) turns that classification into
   geometric predicates: full cell, top-face height, full footprint, rail, roof
   material and side attachment.
3. The checkers apply local and whole-structure rules to the finished grid.
   [`fabric.ts`](../../studio/src/engine/fabric.ts) also applies the sharpest
   rules at write time, before a bad placement can ship.

The governing principle is **earned by measurement**. A plausible metric is not
trusted merely because it sounds right. It is first run over the author's plains
corpus. If it calls the author's normal craft broken, the metric is changed or
kept informational. Three of the first five fabric tests were wrong before that
calibration.

### Reading the words in this document

| Term | Meaning |
|---|---|
| **hard fault** | Any finding fails Burg-authored/generated output. |
| **banded fault** | A finding fails only above the measured allowance. |
| **information only** | Counted and shown, but not used as a gate. |
| **corpus residual** | A known count left by the calibrated metric on the author's work; our output must not exceed it. |
| **author p95 / max / median** | Statistic measured from the author's NBT corpus, not a design preference. |

## The shared shape model

All half-block rules depend on one answer to “how much of this cell exists?” The
classifier is deliberately approximate, but it is centralized: the writer and
all checkers ask the same function.

```text
shapeOf(block):
  double slab                         -> full
  top or bottom slab                  -> slab(top|bottom)
  stair                               -> stairs(facing:half)
  open trapdoor                       -> post(facing)
  closed trapdoor                     -> plate(top|bottom)
  bed                                 -> slab(bottom)
  fence, gate, wall, pane, bars       -> post
  carpet, pressure plate, snow        -> flat
  torch, lantern, pot, chain, ...     -> tiny
  crop, grass, flower                 -> plant
  otherwise                           -> full
```

The derived heights are:

```text
topFace(full)          = 1.00
topFace(top slab)      = 1.00
topFace(bottom slab)   = 0.50
topFace(stairs)        = 1.00
topFace(top plate)     = 1.00
topFace(bottom plate)  = 0.14
topFace(flat)          = 0.09
topFace(post or door)  = 1.00
```

`halfStep` is narrower than “anything half high”: it is a non-top `*_slab`.
That distinction is why a cube over a bottom slab is rejected while a beam over
a fence post is not. The heights `0.14` and `0.09` are implementation constants
from [`solids.ts`](../../studio/src/engine/solids.ts), not corpus statistics.

## Rule categories

### 1. Write-time fabric guard

Implementation: [`fabric.ts`](../../studio/src/engine/fabric.ts), using
[`appearance.ts`](../../studio/src/engine/appearance.ts) and
[`solids.ts`](../../studio/src/engine/solids.ts). Finished structures invoke the
same authority through `slabFaults` in
[`checkFabric.ts`](../../studio/src/engine/checkFabric.ts).

#### 1.1 Full-cell rider over a bottom slab

**Rule.** A full cube cannot rest in the cell above a bottom slab. The slab's top
is at half-cell height, so the cube hangs half a block above its supposed
support. Side-attached blocks and placements at `y <= 1` are exempt.

```text
gap = halfStep(blockBelow)
if gap and fillsCell(blockPlaced):
    fault "rider"
```

| Gate | Value | Source |
|---|---:|---|
| Generated-output allowance | 0 | Hardcoded `SLAB_RIDERS_MAX = 0`; the original Python calibration found six side-attached trapdoors in 121 files and exempted that class rather than allowing cube riders. |
| Historical top-slab counterexample | 2,846 | Python comment, measured over 121 files; top slabs are legitimate surfaces and are not bottom-slab gaps. |
| Current report count | 3,650 in 114/125 files; worst 71 | Raw 2026-07-31 bicalibration artifact. Its samples are explicit `air` states over slabs, so this number records loader/report behaviour in that artifact, not an allowance for generated geometry. |

There is no legitimate rider allowance. The raw report's 3,650 is retained here
because the evidence says it; it must not silently replace the rule's original
calibration.

#### 1.2 Rail over a bottom slab

**Rule.** A fence, gate, wall, pane or bars cannot stand on the empty upper half
above a bottom slab.

```text
if halfStep(blockBelow) and isRail(blockPlaced):
    fault "rail-on-half"
```

| Gate | Value | Source |
|---|---:|---|
| Original allowance | 0 | `calibrate_fabric.py` selftest case 2: zero before the repaired corpus was readable. |
| Known Python corpus residual | 1 | `calibrate_fabric.py`: `house_3_lvl6`, oak fence gate over cobblestone slab at `(1,3,6)`. |
| TS `checkFabric` allowance | 0 | `SLAB_RIDERS_MAX = 0`; rail and cube faults share the slab bucket. |

The residual is an exception in the author's file, not permission to repeat it.

#### 1.3 Isolated roof block at device finish

**Rule.** A slab or stair written as roof material must have a block immediately
below, beside it at the same height, or beside it one step above or below. The
check runs after a device pass so a roof course laid cell by cell can support
itself.

```text
if roofMaterial(block) and y > 1 and below is empty:
    near = horizontal neighbours at y-1, y, and y+1
    if every near cell is empty:
        fault "floating"
```

| Gate | Value | Source |
|---|---:|---|
| Allowance | 0 | Always-wrong write-time case; selftest cases 9–10 establish the boundary. |
| Selftest support radius | 1 orthogonal cell, across `y-1..y+1` | Hardcoded neighbourhood in `FabricGuard.finishDevice`. |

### 2. Fence state and boundary-line checks

Implementation: [`checkFabric.ts`](../../studio/src/engine/checkFabric.ts).
Python origin: [`check_fabric.py`](../../tools/check_fabric.py).

#### 2.1 Fence, pane, bars and wall connection properties

**Rule.** Recompute each north/south/east/west connection from the shipped grid
and compare it with the block state's stored properties. A fence gate connects
when its axis crosses the queried direction. Rail families connect to rail
families; walls connect to walls; fences and walls do not connect to each other.

```text
want[d] = sturdy(neighbour[d])
       or perpendicularFenceGate(neighbour[d], d)
       or sameConnectionFamily(neighbour[d])
have[d] = stored connection property for d
fault if any have[d] != want[d]
```

| Gate | Value | Source |
|---|---:|---|
| Generated files | 0 | Hard fault: Burg places every state deliberately. |
| Original author distribution | median 0, p95 4, max 20; 29/115 files | Python calibration table. Informational on hand-built author files because stale states are present there. |
| Current report | 80 cells in 33/125 files; worst 8 | Raw bicalibration artifact; includes `house_manualtest.nbt`. |

#### 2.2 Rail stumps

**Rule.** Count a fence-like cell for which none of the four recomputed
connections is true.

```text
stump = not any(want[north], want[south], want[east], want[west])
```

| Gate | Value | Source |
|---|---:|---|
| Status | Information only | The author uses free-standing posts as supports and markers. |
| Original author distribution | median 5, p95 19, max 36; 99/115 files | Python calibration table; max is `merchant_shop_lvl6`. |
| Current report | 748 total | Raw bicalibration artifact over 125 files. |

#### 2.3 Diagonal boundary step

**Rule.** At boundary height `y=1`, two rails that meet only at a diagonal read
as a broken run. Either shared orthogonal corner cell may bridge them. The bridge
may be any substantial block, not merely another barrier; requiring a barrier
produced 14 false positives in the author's house.

```text
for each rail p and diagonal rail q:
    if neither shared orthogonal corner contains a substantial block:
        report diagonal step
```

| Gate | Value | Source |
|---|---:|---|
| Default scan height | `y = 1` | Hardcoded default in `lineFaults`. |
| Threshold in combined fabric gate | Information only | `checkFabric` returns the bucket but does not add it to `faults`. |
| Current report | 0 total | Raw bicalibration artifact over 125 files. |

#### 2.4 Duplicate parallel boundary run

**Rule.** Two rail cells two blocks apart on x or z, with an unsubstantial dead
cell between them, form duplicate parallel runs.

```text
if rail(x,z) and rail(x+2,z) and middle is not substantial: report
if rail(x,z) and rail(x,z+2) and middle is not substantial: report
```

| Gate | Value | Source |
|---|---:|---|
| Separation | 2 cells, with one dead middle cell | Hardcoded `PARALLEL` offsets. |
| Threshold | Information only | Returned for diagnosis; never inserted into `faults`. |
| Current report | 0 total | Raw per-file report fields over the 125-file corpus. |

### 3. Roof fabric checks

Implementation: [`checkFabric.ts`](../../studio/src/engine/checkFabric.ts).

#### 3.1 Hanging roof material

**Rule.** Above `minY=3`, a slab or stair is hanging when it has no block below,
no orthogonal neighbour at its own height, and no orthogonal stepped neighbour
one level above or below.

```text
hanging = roofMaterial
       and y >= 3
       and empty(below)
       and empty(all four at y)
       and empty(all four at y-1 and y+1)
```

| Gate | Value | Source |
|---|---:|---|
| `HANGING_MAX` | 0 | Author max = p95 = median = 0; 0/115 building-like files. |
| Current report | 0 in 0/125 files | Raw bicalibration artifact. |

This is the sharpest whole-structure rule: the author never does it.

#### 3.2 Hole in a roof plane

**Rule.** An empty interior cell is a roof hole when roof blocks surround it on
at least three of four sides at the same height and there is no block directly
above the empty cell. Roof-ring material includes slabs, stairs, oak planks and
hay.

```text
ring = count(roofBlock at north, south, east, west)
hole = empty(cell) and ring >= 3 and empty(cell above)
```

| Gate | Value | Source |
|---|---:|---|
| `HOLES_MAX` | 2 per file | Original author max 2 (`granary_lvl5..7`), p95 1, median 0; 22/115 files. |
| Current report | 0 in 0/125 files | Raw bicalibration artifact. |

#### 3.3 Enclosed floor cell open to the sky

**Rule.** At `y=1`, count an empty cell walled on all four horizontal sides with
no occupied cell anywhere above it.

```text
sky = empty(x,1,z)
   and occupied(all four horizontal neighbours at y=1)
   and no occupied(x,y,z) for y >= 2
```

| Gate | Value | Source |
|---|---:|---|
| Status | Information only | Courtyards and open work bays are legitimate. |
| Original author distribution | median 0, p95 5, max 7; 25/115 files | Python calibration table. |
| Current report examples | Per-file `sky` field | Raw artifact records the count but does not aggregate it in `summary.totals`. |

#### 3.4 Unsupported cantilever

**Rule.** A roof slab or stair at `y>=2` is supported if its own column contains
any block from `y=1` to `y-1`. If not, search Manhattan rings one, two and three
cells away for a roof cell at the same height whose column is supported.

```text
if no block below in own column:
    reach = nearest same-height roof cell with a supported column,
            searched at Manhattan radius 1..3
    fault if no reach exists
```

| Gate | Value | Source |
|---|---:|---|
| Maximum accepted reach | 3 cells | Measured over 121 author files: radius 1 = 2,076 cases; radius 2 = 355; radius 3 = 10; radius 4 = 1 (`oven.nbt`). The implemented rule draws the line before 4. |
| Fault allowance | 0 | Any returned cantilever enters the combined fault list. |
| Current report | 0 in 0/125 files | Raw bicalibration artifact. |

### 4. Stray and skyline checks

Implementation: [`checkStray.ts`](../../studio/src/engine/checkStray.ts).
Python origin: [`check_stray.py`](../../tools/check_stray.py).

Blocks whose purpose is to stand alone — posts, lights, plants, signs, doors,
beds and similar fittings — are exempt. The ground row `y=0` is also exempt,
because its missing neighbour is terrain outside the NBT.

#### 4.1 Stray block

**Rule.** A non-exempt block above ground is stray when at most one of its six
orthogonal neighbours is occupied.

```text
neighbours = occupied(±x, ±y, ±z)
stray = not exempt and y != 0 and neighbours <= 1
```

| Gate | Value | Source |
|---|---:|---|
| Geometric threshold | `<= 1` neighbour | Definition in TS and Python. |
| Author band in current report | worst 1 per file; 3 findings in 3/125 files | Raw bicalibration artifact: `house_lvl5`, `kitchen_lvl6`, `street_4`. |
| Operational status | Reported finding; no separate allowance constant | Inferred from `checkStray` and the report's `gatedFailures`. |

#### 4.2 Spike block

**Rule.** A non-exempt block above ground is a spike when all four neighbours at
its own height are empty and there is no block directly above. A block with one
above is part of a column, not a spike.

```text
spike = not exempt and y != 0
     and empty(north, south, east, west at y)
     and empty(directly above)
```

| Gate | Value | Source |
|---|---:|---|
| Author band in current report | worst 1 per file; 2 findings in 2/125 files | Raw artifact: `house_lvl5` and `street_4`. |
| Operational status | Reported finding; described as a band because chimneys can match | Code and checker comments; no numeric gate constant exists. |

### 5. Downhill stair audit

Implementation: [`checkStairs.ts`](../../studio/src/engine/checkStairs.ts).
Python origin: [`check_stairs.py`](../../tools/check_stairs.py).

**Rule.** Minecraft stair `facing` names the tall half. On a visible roof slope,
that tall half must point uphill. Inverted stairs and buried stairs are excluded.
Furniture and fittings do not count as evidence of a rising structure.

```text
for each bottom stair that is not buried:
    forward = structural block one forward and one up
    behind  = structural block one behind and one up
    behind must continue at least two cells across the course
    if behind and not forward:
        report tall half pointing downhill
```

| Gate | Value | Source |
|---|---:|---|
| Historical corpus residual | 1 cell | Python calibration: `wheat_farm_lvl3` `(5,4,11)`, a three-stair hip corner with no single uphill direction. |
| TS exported residual | 1 | Hardcoded `CORPUS_RESIDUAL`. Anything above it is described as real. |
| Current report | 0 in 0/125 files | Raw bicalibration artifact. |
| Earlier false-positive count | 22 | Commented calibration history: chairs were mistaken for roof slopes until fittings were excluded. |

The rule was measured against `house_lvl6`: ridge at `x=4`, west slope facing
east and east slope facing west. Both tall halves point toward the ridge.

### 6. Primitive integrity checks

Implementation: [`checkIntegrity.ts`](../../studio/src/engine/checkIntegrity.ts).
Python origin: [`check_integrity.py`](../../tools/check_integrity.py).

The checker first derives a building box from non-ground building fabric above
`y=0`. No such fabric produces `building: no building fabric at all`; a plot or
street is not silently treated as a house.

#### 6.1 Wall gaps

**Rule.** Inside the building box, count empty cells with building fabric on
both opposite x sides or both opposite z sides at the same height.

```text
hole = empty(x,y,z) and
       ((fabric(x-1,y,z) and fabric(x+1,y,z)) or
        (fabric(x,y,z-1) and fabric(x,y,z+1)))
fault if count > 21
```

| Gate | Value | Source |
|---|---:|---|
| `WALL_HOLE_MAX` | 21 | Author p95 over 118 readable buildings; median 8, max 30 (`merchant_shop_lvl6`). The rejected barracks hall had 75. |
| Current report | 0 gated wall failures in 0/125 files | Raw artifact reports post-threshold failures, not raw hole counts. |

Windows, post spacing and hatches are why this is p95 rather than zero.

#### 6.2 Roof coverage of the building box

**Rule.** For each interior x/z column, find its highest occupied cell. A column
is bare when nothing reaches `y=3`. Compute bare share over the interior box.

```text
bare = count(interior columns whose max occupied y < 3)
inner = max(1, (x1-x0-1) * (z1-z0-1))
share = 100 * bare / inner
```

| Gate | Value | Source |
|---|---:|---|
| Implemented threshold | 1,000% | Deliberately unreachable `ROOF_BARE_MAX_PCT`; this metric reports and never fails. |
| Author distribution | median 12%, p95 91%, max 100% | Measured over 118 readable buildings; max is the open `workshop`. |
| Rejected-build counterexample | 6% | Python comment: a broken barracks scored below the author median, proving the definition cannot be trusted as a gate. |

#### 6.3 Door support and frame

**Rule.** For each lower door half: require a block below, at least two occupied
horizontal side cells, and a lintel at `y+2`.

```text
if empty(below): fault and stop this door
if occupied(horizontal sides) < 2: fault
if empty(y+2): fault
```

| Gate | Value | Source |
|---|---:|---|
| Allowance | 0 | Hard structural invariant in TS and Python; no calibrated residual is declared. |
| Current report | 0 door failures in the sampled per-file records shown by the artifact | Raw artifact exposes per-file `integrity.doors`; it does not aggregate this bucket in `summary.totals`. |

#### 6.4 Floating block

**Rule.** Above `y=0`, every non-hanger block must touch at least one occupied
cell in the six-connected neighbourhood. Torches, lanterns, signs, banners,
chains, trapdoors, ladders, vines, plants, leaves and jigsaws are exempt.

```text
floating = y != 0 and not hanger and
           no occupied neighbour in (±x, ±y, ±z)
```

| Gate | Value | Source |
|---|---:|---|
| Allowance | 0 | Hard structural invariant. |
| Current report | 0 in 0/125 files | Raw bicalibration artifact. |

#### 6.5 Room existence and headroom

**Rule.** A standable cell is empty with a non-empty floor below. Flood through
six-connected standable cells seeded inside the building box. Keep cells strictly
inside the footprint. A room needs at least four such cells, and no more than
half may have an occupied cell directly above.

```text
interior = floodFill(standable cells) restricted to inside footprint
if interior.count < 4: fault "no room"
cramped = count(cell where cell above is occupied)
if cramped > floor(interior.count / 2): fault "no headroom"
```

| Gate | Value | Source |
|---|---:|---|
| Minimum room area | 4 standable cells | Hardcoded geometric minimum in TS and Python. |
| Maximum cramped share | 50%; fault only when strictly greater | Hardcoded player-height rule. |
| Current report | Many plot/street/open-piece failures | Raw artifact applies the building checker to all 125 plains files; `gatedFailures` shows that “no room” is not meaningful for every corpus category. No author-wide room residual is claimed. |

### 7. Usability and upgrade-ladder checks

Implementation: [`checkUsable.ts`](../../studio/src/engine/checkUsable.ts), with
walk graph operations from `traverse.ts`. Python origin:
[`check_usable.py`](../../tools/check_usable.py).

#### 7.1 Indoor cells and storeys

**Rule.** A walkable cell is indoors only when its own column has an occupied
cell at least two blocks above it. Storeys are not assigned fixed heights; every
stand elevation with enough indoor floor cells is listed independently.

```text
indoor(cell at y) = any occupied cell in same column from y+2 upward
storey(y) = indoor cells at exactly y
keep storey when cell count >= 8
```

| Gate | Value | Source |
|---|---:|---|
| `FLOOR_MIN_CELLS` | 8 | Hardcoded after `barracks_lvl3` showed that fixed y bands and clustered elevations misidentify attics and mezzanines. |
| Attic mode | 1 | Caller lowers `minCells` to 1; Python `--attic` does the same by changing the constant. |

#### 7.2 Enterability and climbability

**Rule.** Flood the walk graph from walkable, non-indoor cells on the x/z edge of
the NBT box. Score each storey by how many cells the flood reaches.

```text
outside = walkable boundary cells that are not indoor
seen = reachable(outside)
for each storey, lowest first:
    got = reached cells / total cells
    0 on first storey  -> ENTER-FAIL
    0 on later storey  -> NO-WAY-UP
    0 < got < floor(3*total/4) -> partial
    otherwise          -> reached
```

| Gate | Value | Source |
|---|---:|---|
| Reached floor | at least `floor(3N/4)` cells | Hardcoded 75% boundary in TS and Python. |
| Enterability | first storey is not `enter-fail` | Implemented predicate. No author-corpus statistic is declared. |
| Climbability | no storey is `no-way-up` | Implemented predicate. Partial floors do not make `climbable` false. |

#### 7.3 Equipment survival across upgrade rungs

**Rule.** Count 18 equipment functions per rung, stripping colour/variant prefixes
(`white_bed` and `orange_bed` are both `bed`). A count drop is excused when total
equipment gained in the same step is at least total equipment lost; that is a
workstation swap. Any other function that falls has vanished.

```text
for each adjacent rung pair:
    delta[k] = next[k] - current[k]
    gained = sum(positive delta)
    lost = -sum(negative delta)
    if gained >= lost > 0:
        excuse declining functions for this step
fault a function if any unexcused next[k] < current[k]
```

| Gate | Value | Source |
|---|---:|---|
| Equipment functions | 18 | Hardcoded `LADDER_KEEP` list. |
| Corpus residual | 3 flagged counts across 2/14 author families | Comments in TS/Python: carpenter l6→l7 loses chest and composter while gaining lectern; pig farm l3→l4 loses furnace before the second smoker arrives at l6. |
| Generated-output boundary | Anything above residual 3 is real | Exported `CORPUS_LADDER_RESIDUAL = 3`. |

## Calibration corpus

The current bicalibration artifact was generated on **2026-07-31 at
04:41:17Z** from:

```text
common/src/main/resources/data/burg/structure/plains
```

| Measure | Value | Source |
|---|---:|---|
| NBT files discovered | 125 | `bicalibration-report.json.fileCount` |
| Readable | 125 | `readable` |
| Unreadable | 0 | `unreadable` |
| Blocks read | 216,463 | `blockTotal` |
| Stray findings | 3 in 3 files; worst 1 | `summary.strayStrays` |
| Spike findings | 2 in 2 files; worst 1 | `summary.straySpikes` |
| Hanging roof findings | 0 | `summary.totals.fabricRoofHanging` |
| Roof-plane holes | 0 | `summary.totals.fabricRoofHoles` |
| Cantilevers | 0 | `summary.totals.fabricCantilever` |
| Wrong fence-state cells | 80 in 33 files; worst 8 | `summary.fabricFenceProps` |
| Fence stumps | 748 | `summary.totals.fabricFenceStumps` |
| Downhill stairs | 0 | `summary.totals.stairsDownhill` |
| Post-threshold wall failures | 0 | `summary.totals.integrityWalls` |
| Floating integrity failures | 0 | `summary.totals.integrityFloating` |
| Slab bucket in this artifact | 3,650 in 114 files; worst 71 | `summary.fabricSlabRiders`; samples identify explicit `air` blocks as riders. |

The older calibration comments use **115 building-like files** for the first
fabric table, **121 files** for slab/cantilever measurements, and **118 readable
buildings** for integrity distributions. Those sample sizes are retained beside
the numbers they produced. They must not be silently rewritten as “125” merely
because the later artifact can read all repaired files.

### What bicalibration proves

The parity run executes the same grids through the Python and TypeScript ports
and records one result set only after their normalized outputs agree. For the
rules included by the script, there were **no Python/TypeScript mismatches across
125 readable files and 216,463 blocks**. That proves behavioural parity of the
ports on this corpus. It does not prove that every gate is a good gate: the
integrity roof percentage remains deliberately non-gating, and the raw slab
bucket demonstrates why evidence must be read rather than merely counted.

## Fabric guard selftest

[`calibrate.test.ts`](../../studio/src/engine/calibrate.test.ts) contains ten
minimal cases. They check the checker, not a building.

| # | Case | Expected | What it proves | Number source |
|---:|---|---|---|---|
| 1 | Leaf over bottom slab | `rider` | The shipped 38-leaf half-gap bug is detected. | Test comment, originating in Python selftest. |
| 2 | Fence over bottom slab | `rail-on-half` | A rail cannot occupy the unsupported upper half; original author count was 0 before corpus repair. | Python/TS selftest comments. |
| 3 | Cube over fence post | quiet | A post carries vertically even without a full footprint; about 370 author cases. | Python calibration selftest comment. |
| 4 | Fence over fence | quiet | Vertical fence stacks are legitimate; about 743 author cases. | Python calibration selftest comment. |
| 5 | Block over closed bottom trapdoor | quiet | Side-attached/plate geometry must not be mistaken for a slab rider; about 9 author cases. | Python calibration selftest comment. |
| 6 | Block over pressure plate | quiet | A thin fitting below is not automatically a half-step defect. | TS test; no corpus count claimed. |
| 7 | `put_on` over bottom slab | refused, null placement, `rider` | The placement API blocks the fault before writing. | TS port of Python combined `put_on` case. |
| 8 | `put_on` over fence post | accepted at `(2,2,2)` | The write guard permits the calibrated post idiom. | TS port of Python combined `put_on` case. |
| 9 | Lone roof stair | `floating` | Device-finish inspection catches a roof cell with no under/side support. | TS split of Python roof-course boundary. |
| 10 | Two adjacent roof stairs | quiet | A legitimate course supports itself and is not called floating. | TS split of Python roof-course case. |

The Python selftest has **nine named cases**, because it groups the four
legitimate stacks and the two `put_on` outcomes. It also has a generator-driver
sabotage case: monkey-patch a livestock planting device to create a rider and
prove the driver refuses every affected rung. The TS unit suite expands the
guard-level behaviours to ten tests and deliberately omits that driver case;
its comment records why a full-generator integration has no unit-test analogue.

## TypeScript ↔ Python parity

“Identical” means the implemented algorithm and constants are a direct port;
where the bicalibration artifact covers the bucket, it also means equal corpus
output. “Divergent” is reserved for an intentional API or test-harness difference,
not formatting of finding strings.

| Rule | TypeScript | Python | Status |
|---|---|---|---|
| Shape classification | [`appearance.ts`](../../studio/src/engine/appearance.ts) | [`structures/appearance.py`](../../tools/structures/appearance.py) | Identical port; shared dependency, not a report bucket. |
| Solid/height predicates | [`solids.ts`](../../studio/src/engine/solids.ts) | [`structures/solids.py`](../../tools/structures/solids.py) | Identical port; shared dependency, not a report bucket. |
| Rider / rail-on-half guard | [`fabric.ts`](../../studio/src/engine/fabric.ts) | [`structures/fabric.py`](../../tools/structures/fabric.py) | Identical algorithm; corpus bucket identical in bicalibration. |
| Device-finish floating roof | [`fabric.ts`](../../studio/src/engine/fabric.ts) | [`structures/fabric.py`](../../tools/structures/fabric.py) | Identical algorithm; selftest arrangement differs. |
| Fence connection props / stumps | [`checkFabric.ts`](../../studio/src/engine/checkFabric.ts) | [`check_fabric.py`](../../tools/check_fabric.py) | Identical corpus results. |
| Roof hanging / holes / sky | [`checkFabric.ts`](../../studio/src/engine/checkFabric.ts) | [`check_fabric.py`](../../tools/check_fabric.py) | Identical corpus results. |
| Diagonal / duplicate line | [`checkFabric.ts`](../../studio/src/engine/checkFabric.ts) | [`check_fabric.py`](../../tools/check_fabric.py) | Identical corpus results. |
| Cantilever reach | [`checkFabric.ts`](../../studio/src/engine/checkFabric.ts) | [`check_fabric.py`](../../tools/check_fabric.py) | Identical corpus results. |
| Stray / spike | [`checkStray.ts`](../../studio/src/engine/checkStray.ts) | [`check_stray.py`](../../tools/check_stray.py) | Identical corpus results. |
| Downhill stair | [`checkStairs.ts`](../../studio/src/engine/checkStairs.ts) | [`check_stairs.py`](../../tools/check_stairs.py) | Identical corpus results; both retain residual constant 1 although current artifact reports 0. |
| Building box / walls / roof / doors / float | [`checkIntegrity.ts`](../../studio/src/engine/checkIntegrity.ts) | [`check_integrity.py`](../../tools/check_integrity.py) | Identical corpus results for reported buckets. |
| Room flood | [`checkIntegrity.ts`](../../studio/src/engine/checkIntegrity.ts) | [`check_integrity.py`](../../tools/check_integrity.py) | Divergent implementation: TS performs its own standable-cell BFS; Python delegates to `structures.traverse.walkable`. Same four-cell and half-headroom verdict thresholds. |
| Indoor/storey reachability | [`checkUsable.ts`](../../studio/src/engine/checkUsable.ts) | [`check_usable.py`](../../tools/check_usable.py) | Identical algorithm; not included in the bicalibration report summary. TS exposes `minCells` as a parameter; Python mutates the module constant for `--attic`. |
| Ladder monotonicity | [`checkUsable.ts`](../../studio/src/engine/checkUsable.ts) | [`check_usable.py`](../../tools/check_usable.py) | Identical algorithm and residual 3; not included in the bicalibration report. |
| Guard selftest | [`calibrate.test.ts`](../../studio/src/engine/calibrate.test.ts) | [`calibrate_fabric.py`](../../tools/calibrate_fabric.py) | Divergent harness: ten TS unit cases versus nine grouped Python cases; Python alone includes sabotaged-driver integration. |

## What these checks do not prove

- They do not judge silhouette, palette, proportion or whether a roof *reads* as
  a roof. The user still looks in game or in the preview.
- They do not make an information-only metric hard by repetition. Open sky,
  stumps and bare-box roof share remain descriptive.
- They do not authorize copying a corpus residual. A residual marks the boundary
  above which a finding is certainly ours; it is not a style allowance.
- They do not make the plains corpus writable. It is evidence, not output.

The order remains: inspect the shipped NBT, read the numbers beside their source,
then look. A checker that contradicts the author's craft is repaired before the
building is.
