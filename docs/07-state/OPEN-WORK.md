# Burg — open work

Backlog of open work, ordered by value. Extracted from `CLAUDE.md` on 2026-07-31; update in
place. The entries below earned their phrasing in CLAUDE.md and are kept verbatim where
possible — do not paraphrase them softer.

## Open

- [ ] Livestock: the three yards still share their **devices** (trough, rack, muck heap),
      which keeps level-to-level similarity at 0.86–0.87 against his 0.79. Differentiating
      them per breed is the next lever; grafting a second author structure at the top rungs
      is the one after.
- [ ] `pasture.py` keeps **its own small palette** (oak, cobblestone, mossy) rather than
      importing `wall.py`'s: that module carries the fortification material ladder, which is
      a different vocabulary with a different job, and coupling a cattle shed to it means
      every wall-tier change reaches into the byre.
- [ ] Soften abrupt geometry joins with stairs and slabs.
- [ ] The watchtower's **crown reads heavy** — the open deck and the pitched roof are both a
      solid oak block from outside. Function is fine at every level now; this is a looks
      problem, and the contact sheet is where to judge it.
- [ ] `watchtower_lvl3`/`lvl4` still emit `EMPTY_TOP=1`.
- [ ] Apply the fortification style to `barracks` / `armory` / `training_yard`. It has to be
      a repaint of **our output**: the donors live in `plains/` and are read-only.

## Done since the list was written

- [x] The ramp dither is in `wall.py`.
- [x] `wall_tower` climbs by ladder.
- [x] The watchtower is enterable and climbable at all seven levels (it was NO-STAIR on six
      of them) and its broken wrap-around flight is gone.
- [x] `barracks_lvl3` was never broken — the report's threshold was.
- [x] `armory_lvl2`'s ground floor went 30/45 to 46/46.

## How to update

When an item lands, move it from **Open** to **Done** with the commit/PR link appended to
the line; do not delete history. New items go at the bottom of **Open** in CLAUDE.md's voice
(measured, specific, naming the lever) — vague entries get ignored.

## Related

- [`STATUS.md`](STATUS.md) — the per-subsystem tracker this backlog feeds.
- [`CLAUDE.md`](../../CLAUDE.md) — the rules for agents; the source this was extracted from.
