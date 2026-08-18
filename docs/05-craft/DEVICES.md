# The device catalogue

Every stylistic device in the author's 125 plains structures, with the recipe, the count
and the file that proves it.

This exists because a build of ours was rejected with "нет изюминки, нет стиля от автора из
plains". The answer to that is not more metrics — it is knowing what he actually *makes*,
at the level of "this block, in this state, on top of that one". A device is an adjacency.
Statistics about material shares cannot express one.

Two sources, both re-runnable, neither of them memory:

```
python scan_devices.py         # the grammar: what he puts on what, over all 125 files
python dump_nbt.py F --layers  # one building, every course as a plan
python describe.py F [--against G]   # one building, banded, and the rung diff
```

Part 1 below is the corpus-wide grammar — measured in one pass, so every number is a
count over 125 files and 55 526 solid cells. Parts 2 and on are the per-family devices.

---

## Part 1 — the grammar

### 1.1 The stone ramp is two stones, not four

`docs/05-craft/STYLE.md` describes a four-step ramp, `mossy_cobblestone → cobblestone → stone →
andesite`, dithered between adjacent steps. **That is not what he does.** Every horizontal
stone-to-stone contact in the corpus, 2472 of them:

| pair | count | share |
|---|---:|---:|
| `cobblestone` + `mossy_cobblestone` | 2272 | **92%** |
| `cobblestone` + `stone` | 142 | 6% |
| `mossy_cobblestone` + `stone` | 30 | 1% |
| `cobblestone` + `smooth_stone` | 22 | 1% |
| `mossy_cobblestone` + `smooth_stone` | 6 | 0% |

`andesite`, `tuff`, `stone_bricks`, `granite`, `diorite`, `deepslate`: **zero contacts,
because he does not place them at all.** His masonry is one stone with one weathered
variant of itself. Vertically the same: `mossy_cobblestone + cobblestone` 392 and
`cobblestone + mossy_cobblestone` 352, i.e. the dither runs both ways with no direction.

A generated wall that ramps through four stone families is not richer than his — it is a
different building material, and that is what "no author style" was pointing at.

### 1.2 What sits on what — the top of his vertical grammar

34 460 fabric stacks (`y >= 1`; the terrain layer is excluded). The first row is 13% of
the whole corpus on its own.

| below + block | count | what it is |
|---|---:|---|
| `air` + `oak_slab` | 4605 | the roof: a slab course with nothing under it |
| `oak_planks` + `oak_planks` | 2492 | a plank wall panel |
| `cobblestone` + `cobblestone` | 2237 | a stone wall |
| `grass_block` + `oak_fence` | 1617 | the plot boundary, straight onto turf |
| `dirt` + `cobblestone` | 1305 | a wall foot bedded in soil, no footing course |
| `oak_planks` + `oak_slab` | 1094 | the eave: slab on the wall plate |
| `grass_block` + `oak_leaves` | 950 | planting, on turf |
| `coarse_dirt` + `oak_fence` | 936 | the boundary again, on worn ground |
| `oak_log` + `oak_log` | 905 | a post, standing |
| `air` + `oak_stairs` | 861 | the roof, his other family |
| `cobblestone` + `oak_planks` | 837 | **the storey change: stone below, timber above** |
| `farmland` + `wheat` | 817 | crop |
| `oak_fence` + `oak_fence` | 604 | a railing or a window screen |
| `oak_fence` + `oak_slab` | 414 | a beam bearing on a post |
| `dirt` + `oak_log` | 404 | a post straight into the ground |
| `mossy_cobblestone` + `cobblestone` | 392 | the dither |
| `oak_log` + `oak_slab` | 387 | eave on a post head |
| `glass_pane` + `glass_pane` | 114 | **a window is a vertical pair of panes** |

### 1.3 Doors

145 doors. Every number here is a share of that.

| part | recipe | share |
|---|---|---|
| threshold (the cell below) | `cobblestone` | **55%** |
| | `oak_planks` | 26% |
| | `mossy_cobblestone` | 10% |
| lintel (two above) | `oak_planks` | 28% |
| | `cobblestone_stairs` | **19%** |
| | `oak_stairs` | **19%** |
| | `oak_slab` | 12% |
| jamb (both cells across the opening) | `cobblestone` 36%, `oak_planks` 35%, `mossy_cobblestone` 12%, `oak_log` 6% |
| the step outside, one down | `dirt_path` | **54%** |
| | `cobblestone` 15%, `oak_planks` 8%, `oak_stairs` 6%, `cobblestone_stairs` 5% |
| height | y=1 66%, y=4–5 27%, y=2 6% |

Three devices fall out of this:

- **A door stands on stone even in a timber house** — 65% of thresholds are cobble or
  mossy cobble. The doorway is the one place stone appears in an otherwise plank wall.
- **The lintel is a stair 38% of the time**, pointing out over the opening. Not a plank,
  not a slab: a stair, so the head of the door reads as a moulding.
- **The path touches the door.** `dirt_path` is directly outside 54% of the time — the
  approach is not a separate object placed nearby, it lands on the threshold cell.
- y=4–5 doors are second-storey doors: `house_2_lvl6` has one at y=5,
  `merchant_shop_lvl6` two.

### 1.4 Windows

255 panes, 519 frame contacts.

- **A window is a vertical stack of `glass_pane`, framed in `oak_planks`.** 71% of all
  frame neighbours are `oak_planks`; `cobblestone` is 5%. 45% of panes have a pane below
  them and 45% a pane above → the stack is 2 or 3 tall, 1 wide.
- The sill is `oak_planks` 33% / `cobblestone` 12%; the head `oak_planks` 39% /
  `oak_slab` 5%.
- **Height is per storey, not absolute.** The corpus-wide peak at y=5–7 (77%) is the
  upper storey of his tall families and nothing else: `house_lvl4` (7 tall) has its panes
  at y=2–3, `house_lvl6` (9 tall) at y=2–4, while `house_2_lvl6` puts them at y=5–7 and
  `merchant_shop_lvl6` at y=5–9. **The sill sits 1–2 courses above the floor of its own
  storey**, and each storey gets its own band.
- `white_terracotta` frames 8% of them — that is his top-rung wall material, so a
  terracotta-framed window is a late-rung device (4 files).

### 1.5 Light

| device | recipe | count |
|---|---|---|
| **torch on a post** | `torch` with `oak_fence` **below** it | 151 of 170 standing torches — **89%** |
| torch under a shelf | `wall_torch` with `oak_slab` directly above | 117 of 168 wall torches — **70%** |
| hanging lantern | `chain` under `chain` (29%) or under nothing (46%), `lantern` at the bottom | 68 chains, 161 lanterns |
| lantern on a pier | `lantern` on `cobblestone_wall` | 14% of lanterns |
| lantern on a post | `lantern` on `oak_fence` | 17% |
| candle on a seat | `candle` on `oak_stairs` | 12 of 13 — **92%** |

**He never stands a torch on the ground.** A standing torch means there is a fence post
under it. A wall torch has a slab over it seven times out of ten — the slab is a hood, and
it is what stops a wall torch reading as a stuck-on decal.

### 1.6 Furniture, and what carries it

| fitting | stands on | note |
|---|---|---|
| `white_bed` | `oak_planks` 41%, `cobblestone` 36%, `oak_slab` 14%, `cobblestone_slab` 7% | the floor, whatever it is |
| beds paired | `white_bed` beside `white_bed` 41% of side contacts | two beds share a wall |
| `crafting_table` | `dirt` 40%, `cobblestone` 19%, `coarse_dirt` 18% | **most of his benches are outdoors on bare ground** |
| `furnace` | `cobblestone` 41%, `dirt` 20%, `mossy_cobblestone` 19% | and see the flue below |
| `oak_pressure_plate` | `oak_stairs` 35%, `oak_slab` 22%, `oak_fence` 11% | **stair + plate = a chair; slab + plate = a table** |
| `flower_pot` | `oak_stairs` 22%, `cobblestone_wall` 21%, `coarse_dirt` 10% | the pier-cap planter |
| `hay_block` | `hay_block` 22%, `dirt` 17%, `oak_slab` 16% | stacked, not scattered |
| `beehive` | `stripped_oak_log` 41%, `cobblestone` 34% | a hive stands on a stand |
| `composter` | `dirt` 54%, `grass_block` 27%; `wheat` on 60% of its side cells | at the field edge |
| `ladder` | `ladder` 66% below, `oak_trapdoor` 10% above | the climb ends at a hatch |
| `white_wool` | air below 62%, `oak_fence` 24% | **wool hangs — it is an awning, not a bed** |

**The flue.** `furnace` has `cobblestone_wall` directly above it 44% of the time and
`cobblestone` 42%; `smoker` has `cobblestone_wall` above it **84%**. This is the single
exception to the no-stone-`*_wall` rule in this repo, and it is his: the wall block is the
chimney above the heat source, nothing else.

Nothing is ever stacked on a chest, a barrel or a bed: `orange_bed`, `composter`,
`red_carpet`, `brown_bed` all read `air` above 100%.

### 1.7 Slabs and stairs, by id

| id | n | halves |
|---|---:|---|
| `oak_slab` | 7590 | `bottom` 48%, `top` 46%, `double` 6% |
| `cobblestone_slab` | 379 | **`top` 65%**, `bottom` 28%, `double` 7% |
| `stone_slab` | 174 | **`bottom` 86%**, `double` 14% |
| `oak_stairs` | 2203 | `bottom` 52%, `top` 48% |
| `cobblestone_stairs` | 393 | `bottom` 59%, `top` 41% |

- `oak_slab` genuinely goes both ways — it is the roof shingle and the floor and the eave.
- **`cobblestone_slab` is a top slab**: a kerb, a bench seat, a flush floor edge.
- **`stone_slab` is a bottom slab and it lives at y=1** (158 of 174) — it is a step and a
  paving device, never a roof.
- There is no `stone_bricks_slab`, no `*_wall` railing, no dark stone anywhere.

### 1.8 Timber

`oak_log` 1790: `axis=y` **71%**, `z` 18%, `x` 11%. `stripped_oak_log` 372: `axis=y` 62%,
`z` 26%, `x` 13%.

A log is a post seven times out of ten, and when it lies down it is a beam under an eave
or across a gable. `stripped_oak_log` lies down more often than `oak_log` does — it is his
dressed timber, so it shows where a beam is meant to be read as a beam.

### 1.9 Trapdoors — 505 of them, and mostly not doors

| state | count | share |
|---|---:|---:|
| `half=top, open=true`, something below | 175 | 35% |
| `half=top, open=true`, **air below** | 131 | 26% |
| `half=bottom, open=true` | 77 | 15% |
| `half=bottom, open=false` | 31 | 6% |
| `half=top, open=false` | 30 | 6% |

**61% are `half=top, open=true`** — a vertical leaf attached at the top of the cell. That
is a shutter, a window box front, a hanging shelf or a bracket, and 26% of them hang with
nothing underneath. A trapdoor is his cheapest way to put a thin plane anywhere, and he
uses it as a *side-attached* fitting far more than as a floor hatch. 23% have another
trapdoor directly above; 24% have an `oak_slab` above — a shelf and its bracket.

### 1.10 Fences

3797 fence cells. **72% sit at y=1** — the boundary is a ground-level object.

| above a fence | count | share |
|---|---:|---:|
| `air` | 2403 | 63% |
| `oak_fence` | 604 | 16% |
| `oak_slab` | 414 | 11% |
| `torch` | 151 | 4% |
| `oak_planks` | 137 | 4% |
| `lantern` | 27 | 1% |

A fence carries three things and only three: another fence (railing/screen), a slab or
plank (a beam bearing on a post), and a light. 63% carry nothing.

### 1.11 The roof leaks on purpose

3482 roof cells have air directly beneath them: `oak_slab` **79%**, `oak_stairs` 20%,
`cobblestone_stairs` 10 cells, `cobblestone_slab` 1. So:

- **A roof is oak.** Stone roofing is 11 cells in 125 buildings.
- The two roof families are the slab pitch (leaky, early rungs) and the stair pitch
  (sealed, late rungs), as already recorded — this measures the ratio: 4 slabs to 1 stair.

### 1.12 The eave: exactly one cell, and never a full block

This is the device two rejected commits of mine got wrong, so it is worth the measurement
in full. Of 3482 roof cells with air beneath them, 2128 (61%) sit in a column with no wall
in it at all — they project past the wall. How far:

| projection past the nearest wall column | count | share |
|---|---:|---:|
| **1 cell** | 2032 | **95%** |
| 2 cells | 92 | 4% |
| 3 cells | 4 | 0.1% |

And what the projecting cell is made of:

| id | count | share |
|---|---:|---:|
| `oak_slab` | 1666 | **82%** (`top` 936, `bottom` 681, `double` 49) |
| `oak_stairs` | 360 | 18% (`top` 230, `bottom` 130) |
| `cobblestone_stairs` | 6 | 0.3% |

**Zero full blocks.** So a projecting eave is his idiom and a hanging block is not the
problem — *the shape* is. A slab or a stair projecting one cell reads as a rafter tail; the
same cell as a full block reads as an unfinished lump, which is exactly what
"убери эти блоки свисающие" and "на краях крыши че это за блоки нахуй" were pointing at.

87% of eave cells have two or more solid cells beside them at the same height — they are
the outer row of a continuous plane, not lone cantilevers. 10% have only one, which is the
corner tip of the overhang, and that is the most he ever cantilevers.

His per-file overhang share is high and stable: `house` 93%, `house_lvl1` 85%, `lvl2` 77%,
`lvl3` 81%, `lvl4` 75%, `lvl5` 81%, `lvl6` 82%. Small early buildings (`carpenter`,
`oven`, `pig_farm`, `kitchen_lvl2`) are 100% — a shed's roof is *entirely* overhang.

### 1.13 Ground

Terrain layer, 21 066 cells: `grass_block` 26%, `coarse_dirt` 19%, `dirt` 15%,
`dirt_path` 15%, `cobblestone` 7%, `farmland` 4%, `water` 3%, `podzol` 3%,
`oak_planks` 2%, `mossy_cobblestone` 2%, `mud` 1%, `moss_block` 1%.

**`coarse_dirt` is a quarter of his worn ground and it is the wear itself** — it appears
where feet go and around posts, with `dirt_path` for the route proper. A plot that is
plain `grass_block` under the building has skipped the single commonest weathering device
in the corpus.

The course above the terrain, y=1, 9952 cells: `oak_fence` **27%**, `cobblestone` 17%,
`oak_leaves` 12%, `wheat` 8%, `oak_log` 5%, `oak_planks` 4%, `mossy_cobblestone` 4%,
`oak_stairs` 3%. The first thing above the ground on one of his plots is a fence.

### 1.14 Corners, and the rhythm between posts

The foot of each of the four corner columns of every building with a real wall (96
corners over the corpus):

| id at the corner foot | count | share |
|---|---:|---:|
| `oak_log` | 48 | **50%** |
| `cobblestone` | 23 | 24% |
| `stone` | 19 | 20% |
| `oak_planks` | 4 | 4% |
| `stripped_oak_log` | 1 | 1% |

**78% of corner columns are a single material from the ground to the top.** The corner is a
post, and it is not interrupted by the courses it passes: half the time literally an
`oak_log[axis=y]` standing the full height, and when it is stone it is stone all the way.
A corner that changes material mid-height is the 21% case, not the rule.

Gap between `oak_log` posts along a wall line, measured at y=2 (222 gaps):

| gap | share |
|---|---:|
| 4 cells | 37% |
| **6 cells** | **47%** |
| 5 cells | 6% |
| 7 cells | 4% |
| 2–3, 8 cells | 3% |

Two spacings and nothing else: a panel between posts is 3 or 5 cells of infill wide. There
is no 1- or 2-cell bay anywhere — a post every other cell is not his frame.

---

## Part 2 — per-family devices

Filled from the per-file study; each family's own report lives in
`tools/structures/out/study/devices_*.md`.
