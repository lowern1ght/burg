# How we work on buildings

Written after a session where every metric passed and the building had three doors
hanging at roof height, walls that were 85% air, and 25 of 169 interior columns open to
the sky. The owner's verdict was "это даже постройкой назвать сложно", and he was right:
the checkers measured material shares and never asked whether there was a wall.

This file is the protocol that follows from that. It exists so the next session starts
here instead of improvising.

---

## What is proven not to work

Each of these was tried and rejected in one session, with evidence:

- **The agent authoring geometry from parameters.** Walls, roofs and yards written by
  hand or by rule were rejected every single time — "хуйня", "полный кринж", "ужасно".
- **Statistics as a proxy for quality.** Family shares, corpus bands, similarity, stray
  counts: all inside the author's bands on a build with doors in the sky.
- **Extruding or stretching his cross-sections.** A section repeated along a length is a
  corridor of repeats. `barracks` is `house_2` stretched ×3 and reads as "just a big
  house"; assembling slices by hand reproduced the same monotony by another route.
- **The agent judging looks from renders.** Wrong roughly five times in one session,
  including "reads as a bridge", "materials are right", and "looks almost the same" —
  the last while looking at an angle that hid the subject entirely.
- **Long turns with many artefacts.** Six variants at once turns the review into "all of
  it is bad", which carries no information about which decision was wrong.

## What is proven to work

- **Measuring his corpus for facts.** Every law derived this way held up: the roof seals
  at rung 5 by swapping slabs for stairs, leakiness is a rung and not a defect, post
  spacing peaks at 4 and 6, plot margins run 1 to 8 cells, the growth law is exempt at
  rung 0→1 because that rung is a rebuild.
- **Grafting his whole buildings.** `livestock` grafts a whole house per rung and was
  accepted; two attempts to compose a shed were rejected as "a plank tower in a field".
- **Gates on mechanics, not looks.** Walkability, animal escape, stray blocks, stair
  pitch, corrupt files: every one of them caught something real.
- **Reproduce-and-diff.** Rebuilding his `house` from a written recipe scored 39% exact
  cells. That number is worth more than any amount of self-assessment, because it comes
  with a list of the cells the agent does not understand.

---

## The five layers

### 1. Knowledge: a recipe, proven by reproduction

For each family of his buildings the agent writes a recipe — an ordered list of devices
with rules, not statistics — then **rebuilds his file from it and diffs cell by cell**.
The diff percentage is the agent's competence score for that family.

**No generator exists for a family whose recipe scores under 90%.** This turns
"understand the style" into something measurable and self-checkable, and it needs nothing
from the owner.

### 2. Integrity: primitives before statistics

Five checks, all hard, all before any style metric. `tools/check_integrity.py`:

1. **Walls are closed** — every perimeter cell of every wall course is filled, except
   deliberate openings.
2. **The roof covers the room** — every interior column has something above it.
3. **A door stands and is framed** — floor under it, wall either side, lintel over.
4. **Nothing floats** — no block whose only neighbour is diagonal or none.
5. **There is a room** — an enclosed volume you can stand up in and walk through.

**A build that fails any of these is never shown.** The build with doors in the sky
should have died here.

### 3. The ladder: rung 0 first, one at a time

The deliverable is always a ladder, and a ladder starts at its crudest rung. Rung 0 is
built and reviewed **alone**; its acceptance gates everything above it. Growth then
follows his measured mechanics: 55-65% of cells kept on early rungs and 90-96% on late
ones, height rising once or twice, materials by rung, roof sealed at rung 5.

### 4. Review: make the verdict cheap

- **One small artefact per turn.** Not a row of six.
- A textured render (`structures/render_tex.py`, real block textures off the Lodestone
  pack) plus a preview link (`scripts/preview.mjs` — pan with shift-drag or WASD).
- The owner answers with a word and **at most one** thing to change. The agent changes
  only that.
- When the agent cannot tell whether something reads right, it **asks with two concrete
  options** instead of guessing. Its own aesthetic judgement is not evidence.

### 5. Source material: when the corpus has nothing

His 125 plains files contain **no military building at all**, no paved street, no pier.
When the needed shape does not exist in the corpus, the answer is not for the agent to
invent it — it is **one reference file**: a `.schem`, a `.litematic`, or a structure-block
export from a world. Lodestone reads all three. One file unblocks a whole family, and
grafting is the technique that already works.

---

## The bench

**Test structures are not mod content.** Anything generated to exercise the pipeline goes
to `tools/structures/out/bench/` and never into
`common/src/main/resources/data/onceuponatown/structure/`. The point of a bench build is
to test the agent, so the more varied the subjects the better — a barracks, a gate, a
pier, a bridge, a tower — and none of them has to ship.

Shipping is a separate, explicit decision, taken after the owner accepts the ladder.

## The ledger

Every accepted decision becomes a line in the style documents **with the file that proves
it**. Every rejection becomes a rule with its reason. `CLAUDE.md` already works this way
and it is the part of the process that has held up: the laws in it were all earned by a
mistake or a measurement, and they are what keep the next session from repeating this
one.
