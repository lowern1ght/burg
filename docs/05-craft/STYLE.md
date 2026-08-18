# Burg build style

The style any agent must be able to reproduce from this document alone. It is
written as rules with the reasoning attached, because every rule here replaced
something that was tried and looked wrong.

Sources: the reference sets in `house.mrs/` and `house.mrs/gradient/`, referenced
by the leading characters of their filenames.

---

## 1. Gradient — the single most important technique

A gradient is **not** several materials mixed together. That is speckle, and it
reads as grey mush from three blocks away. A gradient is an **ordered ramp of
blocks whose textures blend into their neighbours**, traversed along an axis,
with only **two adjacent steps of the ramp mixed at any one point**.

The reference `61287e8a` and `e738d707` state the ramps explicitly, as labelled
strips. `c57b33be` and `9d7fa607` show them built.

### The rule

1. Choose a **ramp**: an ordered list of blocks, each visually close to the next.
2. Pick the axis the gradient runs along — for a wall it is **vertical**.
3. Map position along the axis to a real-valued **ramp position** `t`.
4. At each cell, `t` falls between ramp steps `i` and `i+1`. Place step `i` with
   probability `1 - frac(t)` and step `i+1` with probability `frac(t)`.

That is a dither. Never mix step `i` with step `i+2`. Never draw from the whole
ramp at once.

```
       t = 0.0   ████████████  step 0 only
       t = 0.3   ███▓█▓██▓███  mostly step 0, some step 1
       t = 0.5   █▓█▓▓█▓█▓▓█▓  half and half
       t = 0.8   ▓▓▓█▓▓▓▓█▓▓▓  mostly step 1
       t = 1.0   ▓▓▓▓▓▓▓▓▓▓▓▓  step 1 only
```

Transition zones are **3 to 5 blocks** deep (`9d7fa607`). Shorter reads as a
seam, longer loses the sense of two distinct materials.

### The ramps

Measured from the labelled references. Ordered; adjacent steps blend.

**Green to stone** (`61287e8a`) — for anything meeting the ground:

```
grass_block → moss_block → mossy_cobblestone → cobblestone → stone
```

**Grey, light and smooth to dark and rough** (`e738d707`):

```
smooth_stone → stone → andesite → cobblestone → deepslate
```

**Warm timber** (`33a7f4e5`), light to dark:

```
stripped_oak_log → oak_log → oak_planks → spruce_log → dark_oak_log
```

Extend a ramp only with a block that genuinely blends into its neighbour. Useful
additions, all rough: `tuff`, `cobbled_deepslate`, `dripstone_block`, `basalt`,
`blackstone`, `granite`, `diorite`, `packed_mud`, `coarse_dirt`.

### Direction on a wall

Bottom = damp, mossy, rough and dark. Top = cleaner and plainer. So a curtain
wall runs *up* the green-to-stone ramp: moss and mossy cobblestone at the foot,
cobblestone through the middle, stone and andesite at the head.

---

## 2. Randomness — in the silhouette, not in the material

`33a7f4e5` and `9485c249` are both about this. Their material variety is modest;
what makes them read is that **no two neighbouring elements are the same
height**.

- Palisade posts: every post a different height, differences of 1 to 4 blocks,
  some carrying a single thin block higher still.
- Stone merlons: irregular width AND irregular height, 1 to 3 courses.
- The base never meets the ground on one line: blocks step in and out, slabs and
  stairs break the corner, moss and grass interrupt it.
- Horizontal detail blocks — beam stubs, plank bands, trapdoors — sit at
  **irregular heights**, not on one course.

**A material mix cannot rescue a straight silhouette, and a ragged silhouette
carries a plain material.** If forced to choose one, choose the silhouette.

---

## 3. Structural realism — the material follows what it can physically do

The reason behind most of the rules below, and the tiebreaker whenever two of
them disagree. **Stone bears. Timber spans and projects.**

- **Anything cantilevered beyond the wall face is timber.** Galleries,
  hoardings, jetties, oversailing floors. Stone can corbel out one or two
  courses as brackets, and what those brackets carry is timber. Stone is heavy
  and does not hang.
- **The heavier the stone, the lower it sits.** Deepslate and the coarsest stone
  belong in the plinth and the foundation, where the load is; lighter stone goes
  above. A dark heavy stone up at the parapet reads top-heavy and wrong.
- **Roof structure is timber.**
- **Moss follows water.** It soaks up from the ground, so it is heaviest at the
  foot and fades out going up. It is a dampness gradient, not one of the level's
  stone types, and it does not consume a stone slot.
- **Timber on top of a stone wall is not a step backwards in the ladder.** A
  projecting fighting gallery is harder to build than more wall and adds real
  defence — you can shoot straight down the foot of the wall from it. It is the
  expensive, skilled addition, and it *has* to be timber, because stone that
  heavy cannot be hung out over a face. `0fee` is exactly this.

Prefer the reading that a real builder would arrive at. If a block is doing a job
its material could not do, it is wrong however good it looks.

---

## 4. Materials

### Three stones per level

**A level uses three kinds of stone. No more.** Cobblestone and stone are two of
them at every level, and the third is the gradient stone that advances with the
ladder. Moss does not count against the three: it is weathering.

| level | what it is | stones |
|---|---|---|
| 0 | earth and wood — the first thing the villagers built | — |
| 1 | the first stone: a plinth under a timber frame | mossy cobblestone, cobblestone |
| 2 | built from surface stone | mossy cobblestone, cobblestone, stone |
| 3 | better-quarried stone | cobblestone, stone, andesite |
| 4 | strong stone | cobblestone, stone, deepslate |

Deepslate arrives last because it is the visibly strong stone. It goes where
strength is *seen and needed* — the plinth, the foundation, the piers, the corner
bastion — and not dithered through the field, both because heavy stone belongs low
and because cobblestone and deepslate sit two steps apart on the ramp and mixing
them directly is the harsh contrast the ramp exists to avoid.

### Rough stone only

Rough stone carries every level. **Dressed stone — `stone_bricks`,
`polished_andesite` — is the TOP level's unlock and appears nowhere earlier**,
because stone-working is a skill the village acquires, and that acquisition is
exactly what the last rung of the ladder should show. It arrives as a minority
accent diluting the rough field, never as the field itself: `stone_bricks` at
75-84% was the measured failure that made the first attempt read as a palace
somebody else built.

No Nether stone at any level. Basalt and blackstone are the wrong world.

### Base and gradient

**Cobblestone and stone are the base of everything, at every level.** They carry
the bulk. Everything else is gradient laid over them. Target on visible faces:

| role | share |
|---|---|
| base — cobblestone + stone | **60–70%** |
| gradient stone | 15–25% |
| damp — moss, mossy cobblestone | 10–20% |

A gradient stone that becomes the dominant block stops being a gradient and
becomes the wall's identity. Measured failure: `stone_bricks` at 75–84%, and
andesite at 36% when each tier picked its own dominant.

### Timber is used as timber

- `oak_log` **stands** as a post, and runs down **to the floor**. A log that
  starts and ends in mid-air reads as a block someone forgot to remove.
- `stripped_oak_log` **lies** as a beam, horizontal axis.
- `oak_planks` only ever fills a **panel between** posts and beams. Planks used
  as general building material reads as a shed.
- `oak_stairs` / `oak_slab` build roofs and break edges.
- `oak_fence` is a **rail** — and it is the right railing, in preference to a
  stone `*_wall` block.

### Vertical circulation: ladder inside, stepped stone outside

- **Inside a tower or a building: a `ladder`.** Not a stair run. A staircase eats
  the floor of a small room, and at village scale a ladder is what people
  actually build. This replaces the spiral stair currently in `wall_tower`.
- **Outside: a stepped run**, built from stairs and full blocks so it reads as
  masonry steps against the wall.

### Foliage is sparing

Leaves are seasoning, not texture. Clumps of two or more, always against
something solid, and on **some** bays only. When in doubt place fewer: excess
leaves read as noise and they hide the stonework the gradient exists to show.

### Blocks to avoid

| block | why |
|---|---|
| `*_wall` (stone) | looks odd in quantity; use `oak_fence` for railings and full blocks for merlons |
| `basalt`, `blackstone`, any Nether stone | wrong world; a village never dug it |
| dressed stone below the top level | processing is a skill the village earns, see below |
| `gravel`, `sand` | gravity: they drop out of a wall face and leave holes |
| `podzol` | out of the palette |
| lanterns and chains outdoors | stage prop; see lighting |

`packed_mud` is sanctioned.

---

## 5. Lighting

**Interior only, and torches.** Nobody lights the outside of their own wall for
the benefit of whoever is attacking it. On a wall walk: `wall_torch` on the inner
parapet, pointing inward. Inside a building: `torch` or `wall_torch`. A lantern
on a chain hanging off the battlements is a showcase-render detail, not a
fortification.

---

## 6. Function — non-negotiable

A structure that cannot be used is not finished, however it looks. `critic.py`
measures appearance and cannot see any of this; `traverse.py` can.

- **Walk the whole thing.** Joined pieces connect with no jumping. Every route
  is stairs and slabs, never a full-block step up.
- **One walk elevation across an entire set**, at every level and for every
  piece kind. Vary it by tier and a mixed ring becomes a staircase of jumps.
- **Two clear cells above any walkable surface.** A player is two blocks tall.
- **Towers are climbable from the ground floor to the roof**, by stairs or
  ladder, with the way in at ground level.
- **Buildings are enterable, and their upper floors reachable.**
- **Crenellation is functional**: embrasure one course, merlon two. A player's
  eyes sit above the embrasure and below the merlon, which is what makes walking
  the wall worth doing. Four courses of parapet means you see nothing.
- **Corners and gates must not dam the walk.** A solid corner bastion blocks a
  ring at all four corners.

Verify with:

```
python build_military.py --dry-run     # style gate + usability gate
python ring_preview.py                 # assemble a perimeter, check the circuit
python check_usable.py                 # enterability and upper floors
# `palette_lab.py` is superseded: it painted ONE level's geometry three times,
# so the samples all shared level 3's shape. Work directly on the real set in
# structure/military/ instead — each level must generate its own geometry with
# its own palette.
```

---

## 7. Composition devices

Kept from `docs/05-craft/BUILD_LANGUAGE.md`, which holds the full catalogue with sources.
The ones that carry the most weight:

- **piers** — vertical pilasters at intervals, projecting one; the gradient stone
  belongs here, on the outer face only
- **projecting parapet on brackets** — the head oversails, stepping down onto a
  bracket at each pier, and the **gap between brackets is the device**
- **arrow loops** — one-deep recesses, in pairs, at the eye height of someone on
  the walk
- **timber hoarding** — a covered fighting gallery projecting beyond the stone
  that you stand **in**; the floor is the top of the stone wall
- **arch heads** — narrow the opening in half-block steps with top slabs, then
  stairs. Never a square lintel
- **damp skirt** — weathering climbs the foot to a different height in every
  column
- **rubble at the foot** — loose slabs on the ground against the wall
- **clumped vegetation** — leaves only ever in clumps of two or more, always
  against something solid, heavy on some bays and absent on others

---

## 8. Scale and footprint

- **The footprint is identical at every level; only the height changes.** The
  mod author does this in all 98 of his buildings — `house` is 9x11 from level 0
  to 6, `pig_farm` 26x19 across nine levels. An upgrade must never need a bigger
  plan.
- Avoid exact mirror symmetry: it is the loudest generated-build tell. Break it
  with the silhouette and the apron, not by adding materials.

---

## 9. What was tried and rejected

Recorded so it is not tried again.

| attempt | why it failed |
|---|---|
| several materials mixed at every height | speckle, not gradient. Reads as grey mush |
| each tier picks its own dominant stone | the gradient becomes the wall's identity |
| dressed masonry for the upper tiers | palace, not village fortification |
| `*_wall` blocks as merlons | not how a battlement is built, and odd in quantity |
| stepped top profile on a short segment | one tooth taller than its neighbours: a horn |
| slab cap on top of a merlon | with the base course beneath it the parapet reached four courses |
| lanterns on chains outside | showcase detail, and it lights the ground for the attacker |
| gravel as worn stone | falls out of the wall on first chunk load |
| painting the buried core of a wall | a third of the palette spent where nobody can look |
| judging any of this from a flat-colour render | these blocks differ by texture, not by colour. Look at the NBT or in game |
