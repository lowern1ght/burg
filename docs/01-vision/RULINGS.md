# Rulings — the constitutional decisions

A ruling is a decision by the owner that overrides preference or convenience. It
is constitutional — a proposal that contradicts one is wrong, not merely
different. This file is the flat index; the reasoning lives in the linked ADRs
or, for the measurements, in [`CLAUDE.md`](../../CLAUDE.md).

Measurement-derived rules bind just as hard, but they were not decided by the
owner — they were discovered in the author's corpus, and each one overturned a
confident guess. They are flagged differently below so the difference is visible.

## Owner rulings

- **2026-07-31** — Burg is both a stranger-sim and a lord-sim, in sequence; the player's power is earned by act, and pillar 2 ("player helps, not commands") is the starting role, not an eternal rule. [ADR-0001](../06-decisions/ADR-0001-earned-crown-trajectory.md).
- **2026-07-31** *(grilling)* — Raid vs war: the player may fight personally at raid-scale (a village garrison is worker-militia; vanilla combat; a geared player can solo it). War-scale (realm-vs-realm armies, sieges) stays NPC-vs-NPC — the player commands, supplies, and watches. Ruling 3 is patched, not repealed. *(pending ADR-0005)*.
- **2026-07-31** *(grilling)* — The realm grows from inside: the player's realm is a metropolis (his first settlement grown to a city) plus colonies founded by expedition (small resource satellites 1000+ blocks out that depend on and trade with the metropolis), not a bag of acquired villages. The three acquisition paths (elevated/founded/captured) apply to foreign AI villages, not the player's own colonies — those are always founded by expedition. *(pending ADR-0006)*.
- **2026-07-29** — Roof tiles on the top two rungs may be deepslate (`deepslate_tile_slab`); slate is what a roof of that rank is actually tiled with. Roof only, never a wall, never below rung 5; the roof stays timber to rung 4. *(not yet formalised as ADR)*.
- *(date not recorded in source)* — `structure/plains/**` is read-only; it is the author's finished work and the corpus every measurement is calibrated against. The one exception is byte-exact CRLF repair. [ADR-0002](../06-decisions/ADR-0002-plains-readonly.md).
- *(date not recorded in source)* — The player FINDS the village; he does not start in it. A settlement is a reward for exploring, and where possible an ordinary Minecraft village becomes one of ours. *(not yet formalised as ADR)*.
- *(date not recorded in source)* — The player starts as a stranger and earns his way in. Guest is the key role, not a formality; he is not the owner of the village until the village decides he is. *(not yet formalised as ADR)*.
- *(date not recorded in source)* — The player only trades and supplies. He never lays a block for the town and never fights for it; every loop must be legible through those two verbs. *(not yet formalised as ADR)*.
- *(date not recorded in source)* — A farm fence never turns to stone; a better year buys straighter timber, closer posts, boards and a gate. Stone on a farm belongs to the byre plinth, trough kerb and dip basin. [ADR-0003](../06-decisions/ADR-0003-farm-fence-stays-timber.md).
- *(date not recorded in source)* — No stone `*_wall` block anywhere we build — not as a railing, not as a battlement, not as a dry-stone fold. A merlon is a full block. *(not yet formalised as ADR)*.

## Measurement-derived rules

- A slab is half a cell; the half-block trap is the bottom slab and nothing else — a cube over a bottom slab floats (zero occurrences in 125 files), while a cube on a fence post is correct (398 times in 90 files). *(measurement, not a ruling — see CLAUDE.md §"Measured facts")*
- Zero roof blocks hang in the air across 121 files; the sharpest test in the repo — fail on any. *(measurement, not a ruling — see CLAUDE.md §"Measured facts")*
- A fence does not connect to a `*_wall` block (0 of 8, measured from both sides); connection props must be derived from the finished grid, not from memory. *(measurement, not a ruling — see CLAUDE.md §"Measured facts")*
- A leaky roof is a rung, not a defect, and it is sealed at rung 5 — the share of leaking roof cells falls to ~0–6% at the second-to-last rung after rising through the middle. Do not "fix" these gaps. *(measurement, not a ruling — see CLAUDE.md §"Measured facts")*
- `facing` on a stair names the TALL half; a step you climb has its tall half toward the climb. Every comment in the generator once said the opposite. *(measurement, not a ruling — see CLAUDE.md §"Measured facts")*

## Related

- [`VISION.md`](VISION.md) — the earned-crown vision; the top-level authority on what kind of game Burg is.
- [`PHILOSOPHY.md`](PHILOSOPHY.md) — the five pillars; pillar 2 and the hard bans are reclassified by VISION.
- [`../06-decisions/`](../06-decisions/) — formalised ADRs.
