# Audit — livestock content (2026-07-31)

Yes, it is beta — but the beta is in **identity, not fabric**. The structural
work is sound: all 18 pens hold their animals, the write-time fabric guard is
clean on every file, no block floats or strays, and the grafted donor is
intact. What is wrong is that the three breeds are **one farmstead three
times**: cross-breed cosine similarity is 0.905 mean (0.94–0.97 at the bottom
rungs a player sees first), where the author's own three animal fields sit at
0.02–0.59. The single shared donor house is most of the mass and swamps the
boundary/device differentiation, which only lands at lvl4–lvl5.

## Method

From `tools/`, on the shipped NBT under
`common/src/main/resources/data/burg/structure/livestock/`:

- `selfgate.py` — every checker; report at `tools/structures/out/selfgate/report.md`.
- `check_pens.py` — flood-fill from each animal's recorded position.
- `describe.py` on `cow_pasture_lvl3`, `pig_sty_lvl3`, `sheep_fold_lvl3`, and the
  author's `plains/jobs/pig_farm_lvl5` for comparison.
- `check_fabric.py --calibrate` (author corpus first) and `check_stray.py`.
- `audit_livestock.py` — the 14 documented rules.
- Cosine similarity via `describe.similarity` (block-state counts, terrain layer
  `y<2` excluded, the function the docs themselves cite), computed pairwise and
  within-ladder.

## Findings by farmstead

### cow_pasture

- **Cross-breed similarity at the bottom is near-identical to the other two.**
  `cow_pasture` base vs pig/sheep = 0.952 / 0.941; `cow_pasture_lvl1` = 0.972 /
  0.978. The pasture does not read as cattle until lvl4–lvl5 (0.865–0.912).
- **`packed_mud` is present and should not be.** `packed_mud` x11 (lvl1) → x14
  (lvl3) → x17 (lvl5), flagged by `describe` as *"absent from his corpus"* every
  rung. The constitutional context (`CLAUDE.md`, BUILD_LANGUAGE §"A farm fence
  stays timber") sanctions `mud`/`packed_mud` **for the pigs**; its appearance in
  the cattle pasture leaks the one material signal meant to mark the sty.
- **Detail density below the author's soft band.** `audit_livestock.py` FAIL:
  *"density / detail / cover inside his p05–p95 (soft)"* — `cow_pasture`
  detail=0.060, `cow_pasture_lvl1`=0.069, `cow_pasture_lvl2`=0.069. The low
  cattle rungs are sparser than his p05. (13/14 rules pass; this is the one
  failure, and it is the only breed that fails it.)
- **One `roof-holed` finding** at lvl3 (5, 3, 7), inside the author's band
  (`selfgate`); not a defect, recorded for the look.
- **Entry doesn't reach the south edge** (`describe`, function): the north
  connector reaches east but not south — yard circulation is cut by the
  house/byre. The pig sty reaches south; this is inconsistent across breeds.
- Reachable yard cells 132–155 across the ladder — cattle genuinely get the most
  ground per head (vs pig 18–57, sheep 88–112). **This proportion is correct.**

### pig_sty

- **The one breed whose material differentiation is real and on-brief.** `mud`
  x22–32 and `packed_mud` x24–26 every rung; the wallow reads. Boundary
  `oak_trapdoor` rises 9 → 20 → 22 (boarded early, as documented).
- **But it is the least rich and the most cramped.** `kinds=31` at lvl3 vs the
  author's `pig_farm_lvl5` `kinds=39`; `describe` top ids are a short list
  (`oak_slab`, `oak_planks`, `coarse_dirt`, `oak_fence`). At lvl5 the pen gives
  **18 reachable cells to 4 pigs (4.5 cells/pig)** — tight even for a sty, and a
  3rd pig at lvl2 has only 30 cells to share.
- **A device with zero free neighbours.** `pig_sty_lvl3`: `furnace@(3,1,5)
  free_neighbours=0` — a functional block sandwiched so tight it has no working
  face. (NOT_A_PEDESTAL itself passes; this is a cramped-placement smell, not a
  stacked-prop fault.)
- **Shutter grammar is thin.** `describe` rotation: *"trapdoors: 1/20 are his
  shutter"* at lvl3, vs cow 6/9 and sheep 2/2. The sty is the breed that is
  *supposed* to be boarded, yet carries the fewest author-style shutters.
- **One `roof-holed` finding** at lvl4 (9, 3, 7), inside the author's band.
- Within-ladder, the lvl4→lvl5 transition drops to **0.758** (the only sub-0.9
  step in any of our ladders) — the top house donor (`house_lvl6`) lands hard.

### sheep_fold

- **The best-differentiated breed, structurally.** Dry-stone grammar is present
  and correct: `cobblestone_wall` 5→8→11, `mossy_cobblestone` 3→81→86, two
  upper-storey doors, `oak_fence_gate` x11 at lvl5 (vs 1 each for cow/pig), and
  the only `white_wool`/`bookshelf`/`white_bed`. Sheep-pig similarity falls to
  **0.745 at lvl4** — the lowest cross-breed pair in the set. This is what the
  other two breeds should look like.
- **`packed_mud` leaks in here too** (x9–10 every rung, "absent from his
  corpus") — same vocabulary leak as the cattle pasture.
- **The only breed with fence-gap findings.** `selfgate`: two `fence-gap`
  findings on `sheep_fold_lvl1` — `(11,10)` meets `(12,11)` only diagonally
  (both inside the author's band, but no other breed flags this).
- **One `roof-holed` finding** at lvl2 (10, 4, 10), inside the author's band.
- Entry doesn't reach the south edge (like the cow, unlike the pig).

## Cross-cutting

- **The headline: the three breeds collapse into one at the bottom.** Cross-breed
  cosine similarity (`describe.similarity`, terrain excluded), per level:

  | level | cow-pig | cow-sheep | pig-sheep |
  |---|---|---|---|
  | base | 0.952 | 0.941 | 0.960 |
  | lvl1 | 0.972 | 0.978 | 0.974 |
  | lvl2 | 0.964 | 0.927 | 0.915 |
  | lvl3 | 0.961 | 0.918 | 0.859 |
  | lvl4 | 0.912 | 0.865 | 0.745 |
  | lvl5 | 0.754 | 0.850 | 0.834 |

  **Mean 0.905.** At base/lvl1/lvl2 the three are 0.91–0.98 — effectively one
  building. OPEN-WORK frames this as "0.86–0.87"; the live number is higher, and
  the worst of it (0.94–0.97) is exactly the rungs a village actually builds.
- **The "his 0.79 / 0.67" baseline in the docs does not reproduce.** The same
  function on the author's own three animal fields returns **0.151**
  (cow_field vs sheep_field), **0.012–0.021** (cow_field vs pig_farm), and
  **0.50–0.59** (sheep_field vs pig_farm). His three are genuinely different
  propositions — a 9×9 cow paddock, an 11×11 sheep pen, a 21×17 pig *farmstead*
  with a barn — not three wrappers round one house. Our gap to him is therefore
  far larger than the docs imply; the "0.67" figure (cited in SKILL §"measure
  sameness" and BUILD_LANGUAGE) measures something other than this function.
- **Cause: one donor grafted three ways.** `house`, `house_lvl1…lvl4`,
  `house_lvl6` are installed identically into all three. The shared mass
  (`oak_slab`, `oak_planks`, `oak_log`, `grass_block`, `coarse_dirt`,
  `oak_fence`) is 60 %+ of every file and nearly identical in count across
  breeds at every rung. The boundary grammar (pig mud/trapdoor, sheep
  drystone+mossy, cow post-and-rail) **is** differentiated — verified by block-id
  counts — but it is a margin on a shared body, so cosine stays high.
- **Within-ladder rungs are more static than his.** Consecutive rung similarity:
  cow 0.976, pig 0.937, sheep 0.936 mean — against the author's house-ladder
  band of 0.79. Our rungs change less per step than his do; cow in particular
  barely moves between rungs (0.948–0.994).
- **`packed_mud` is spread across all three breeds** (cow 11–17, pig 24–26,
  sheep 9–10). It is the one material token supposed to single out the sty, and
  it is in every yard. The shared-devices note in OPEN-WORK (trough/rack/muck
  heap) is the same pattern at the prop layer: `hay_block` appears in all three
  (cow 2–5, pig 2, sheep 4–6), and the `describe` device lists are dominated by
  the *same* donor-house fittings (furnace, crafting_table, barrel, cauldron)
  rather than breed-specific devices.

## What is NOT wrong

- **Animal escape containment holds.** `check_pens.py`: **18/18 HOLDS**, flooded
  from each animal's own recorded position (reachable cells 18–155). The escape
  model — capped posts, the one-cell clear lane, the byre's solid back — is
  sound and verified on shipped bytes, not the generator's self-image.
- **The write-time fabric guard is clean and calibrated.** `check_fabric.py
  --calibrate`: author corpus **8/8 clean**, livestock **18/18 clean**. The only
  non-zero counts are `holes` (cow_lvl3, pig_sty_lvl4, sheep_fold_lvl2 — one
  each) and `rails-to-nothing`, all inside the author's measured band.
- **No strays.** `check_stray.py`: worst file stray=0, spike=0.
- **NOT_A_PEDESTAL passes.** `audit_livestock.py`: *"nothing stacked on a
  functional block — clean — the stacks that exist are his flue over his own
  furnace."* No prop ended up on a chest/trough.
- **Donor grafting integrity holds.** The finishing passes that used to chew the
  donor (`tidy_leaves`, `cap_pillars`) are correctly excluded from farmsteads;
  roof planters survive (`oak_leaves` 9–22 per file); `selfgate` flags 0 faults
  and 5 "his band" findings only.
- **Boundary grammar is differentiated per breed** (pig mud+trapdoor, sheep
  drystone+mossy, cow timber) — the differentiation exists, it is just small
  relative to the shared donor mass.
- **Yard proportions differ as documented** — cattle most ground, sheep
  middle with a holding pen, pig compact-to-mud. 13/14 documented rules pass.
- **Footprint is invariant per ladder** at every level (cow 25×18, pig 19×14,
  sheep 21×19) — the `UpgradeAction` same-origin contract holds.

## Verdict

It is beta in the way the owner suspected, and the beta is **sameness**, not
breakage. The hard parts — containment, fabric, float-free geometry, donor
integrity — are done and gated. The three farmsteads read as one building three
times because they *are* one building three times at the rungs that matter: 0.94
cosine at base, 0.97 at lvl1, differentiating only at lvl4–lvl5, against an
author whose three animal fields are 0.02–0.59 apart. Top three problems by
impact:

1. **Cross-breed identity collapse at the low rungs** (0.94–0.97 at base/lvl1) —
   a village builds lvl1 long before lvl5, and at lvl1 the breeds are
   indistinguishable.
2. **One donor house in all three farmsteads** — the shared body that drives the
   0.9+ similarity; the author uses three genuinely different buildings.
3. **`packed_mud` leaking into cow and sheep** — the one material meant to mark
   the sty is in every yard, blurring the clearest cheap signal.

## Related

- [OPEN-WORK](OPEN-WORK.md) — the livestock item (devices / similarity lever).
- [STATUS](STATUS.md) — the per-subsystem tracker.
- [.agents/skills/burg-buildings/SKILL.md](../../.agents/skills/burg-buildings/SKILL.md) —
  the 5 laws and "Law 2 in code: how the livestock ladder was made to grow".
- [docs/05-craft/BUILD_LANGUAGE.md](../05-craft/BUILD_LANGUAGE.md) — the
  livestock section (escape model, boundary grammar, fabric-check history).
