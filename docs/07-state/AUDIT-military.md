# Audit — military content (2026-07-31)

The military set is **beta quality, and the diagnosis is measurable, not subjective**: 36 of 43 shipped files (84 %) carry a `check_fabric` FAULT, the watchtower's signature device is built from a construction the author uses **zero times in 125 files**, and the armory's top level is a **byte-for-byte block-mix copy (cosine 1.00)** of `house_3_lvl5`. The walls are genuinely composed; the buildings (barracks/armory/training_yard) are unmodified house donors wearing a thin garrison prop layer, exactly the failure `BUILD_LANGUAGE.md` names.

## Method

Ran the project's measurement tools from `tools/` against every shipped NBT under `structure/military/**` (43 files: 7 watchtower, 7 barracks, 6 armory, 3 training_yard, 5 each of wall_segment/corner/tower/gatehouse):

- `python selfgate.py` — **does not cover military at all** (scans `LIVESTOCK` only, `selfgate.py:36`); its "0 faults" report is about the livestock set, not this one. First process finding.
- `python check_fabric.py <each military nbt>` — 36/43 FAULT, 7 OK. Corpus band verified silent via `--calibrate`.
- `python check_stray.py --set military` + `--calibrate` — stray/spike vs author corpus (worst 15/8).
- `python check_stairs.py --set military` — downhill-stair scan (0 found).
- `python check_usable.py` — storey reachability per level.
- `python describe.py` on `house_lvl6` (author reference), one+ level of each military kind, and every watchtower level.
- Cosine similarity of block-count vectors (terrain layer excluded, per `SKILL.md`): author house-ladder mean recalibrated to **0.72** (skill states 0.79; same band), then internal self-similarity per military family + donor-distance for each family's top level. Inline measurement script (now removed) modelled on `measure_trade.py`.

## Findings by building kind

### watchtower

**HARD — fabric FAULTs on all 7 levels:**

- **slab_riders (cube over a bottom slab — 0 occurrences in the author's 125 files):** `watchtower.nbt`=**21**, `lvl1`=18, `lvl2`=20, `lvl3`=7, `lvl4`=11. These are the **open observation deck itself** — `oak_log` corner posts + `oak_fence` railing sitting on a bottom-slab deck floor (e.g. `watchtower.nbt` riders at `(1,6,1)` oak_log, `(1,6,3..5)` oak_fence …). The watchtower's defining device is built from the one construction the author never uses; the rail/posts float half a cell clear of the floor. The livestock set had this exact bug (38 floating pier-cap leaves) and fixed it by moving greenery to the pier foot; the watchtower deck has not been fixed.
- **EMPTY_TOP=1 still shipping** (OPEN-WORK item, verified): `watchtower_lvl3` size 9x**14**x8 top_y=12 → 1 wasted empty layer; `watchtower_lvl4` identical (9x14x8, top_y=12).
- **props (fence disagrees with grid):** `watchtower.nbt`=16 (hard fault — generated files must be 0).

**SOFT — "reads as":**

- **Crown reads heavy** (OPEN-WORK item, confirmed): `watchtower_lvl6` top courses are `y14` = 63 cells timber 98 %, `y15` = 35 cells timber 97 %. Deck floor + pitched roof are both solid oak from outside.
- **roughness 3.43** on `lvl3` (silhouette `[0,8,12,12,11,11,8,0]`) — above his max 1.58; the 4-cell jumps at the crown read as a stepped block, not a resolved cap.

**Not wrong:** `check_usable` = 0 unreachable floors; the ladder reaches the top at all 7 levels (the earlier NO-STAIR regression, OPEN-WORK "Done", holds). `check_stairs` clean.

### barracks

**HARD — fabric FAULTs on 6 of 7 levels:**

- **props:** `lvl2`=8, `lvl3`=8, `lvl4`=12, `lvl5`=12, `lvl6`=16. Only `barracks_lvl1` clean.
- **cantilever (hanging roof block — author's sharpest test, 0 across 115 files):** `barracks.nbt`=1.
- **roof holes:** `barracks.nbt`=1, `lvl2`=1 (both inside his band ≤2, but present).

**SOFT — donor-dependency (the headline):**

- **`barracks_lvl6` is `house_2_lvl6` at cosine 0.99.** The whole ladder's top level is the author's house donor with no transformation. `BUILD_LANGUAGE.md` ("the current stretched `house_2` is already close; the missing device is the ground-floor arcade") is still the live state — the **ground-floor arcade is absent**.
- **0 beds in `barracks` and `barracks_lvl1`** while `buildings/barracks.json` declares `residents: 4` (OPEN-WORK #4, verified still open). `lvl2`+ jump to 10 beds (the donor's bunks) — so the resident grant has no bed backing for the first two rungs.
- **Flat slab vocabulary:** `barracks_lvl3` top id is `oak_slab` 421 / 613 solid (69 %) — a slab roof/floor field, not the author's `oak_stairs` pitched roof (his `house_lvl6` top id is `oak_stairs` 257).
- **Fortification material ladder not applied:** top ids are `oak_slab` / `mossy_cobblestone` / `cobblestone` — **no `stone_brick`** at any barracks rung, while the wall set reaches `stone_brick_slab` at lvl4. OPEN-WORK "Apply the fortification style to barracks" is not done.

### armory

**HARD — fabric FAULTs on 5 of 6 levels:**

- **props:** `lvl1`=3, `lvl3`=**17**, `lvl4`=11, `lvl5`=14. (`armory.nbt` clean.)

**SOFT — worst donor-dependency in the set:**

- **`armory_lvl5` is `house_3_lvl5` at cosine 1.00** — a verbatim block-mix copy. The armory has no identity of its own.
- **No dominant chimney / no open work bay** (`BUILD_LANGUAGE.md` composition target, absent): the forge is a single `furnace` on the ground (lvl1-5); `armory_lvl3` course `y8` = 3 cells stone 100 % — a stub, not a tall tapering stack past the ridge. `anvil` appears only at `lvl5` (1). There is no vertical chimney column anchoring the silhouette.
- **Self-similarity 0.90** (max pair 0.99) — above the 0.79 band; the ladder barely changes vocabulary rung to rung.
- **Slab-dominated:** `armory_lvl3` top id `oak_slab` 311 / 576 (54 %).

### training_yard

**HARD — fabric FAULTs:**

- **stray/spike exceeds the author's worst file:** `training_yard_lvl2` stray=**18** / spike=**15** vs author corpus worst 15/8. The offenders are the capped merlons — `mossy_cobblestone@(1,5,1)`, `@(1,5,3)`, `@(1,5,11)` — each isolated cube reads as a stray because the crenellation spacing leaves every merlon cap touching one neighbour. (Fabric itself clean on all 3 levels — the only multi-level ladder fully fabric-clean alongside `wall_tower` lvl2-3.)

**SOFT — no growth:**

- **Internal self-similarity 0.99** (min 0.99, max 0.99 across 3 levels) — literally "one building three times" (`SKILL.md`: "A set at 0.93 is one building three times"). Base→top growth cosine = 0.99: the levels grow in *size* (1440 → 2095 bytes) but not in *vocabulary*. The walled-compound shape (`BUILD_LANGUAGE.md` target) **is present** — silhouette `[3,5,4,4,2,0,0,0,0,0,0,4,0]` shows walls around an open drill yard — but it does not develop across the three rungs.
- **kinds=13** on `lvl2` vs author `house_lvl6` kinds=30 — a low-diversity build.

### walls (segment / corner / tower / gatehouse)

The wall set is **the part that works** (genuinely composed in `wall.py`, not stretched donors), but it still carries fabric FAULTs and a couple of real defects:

**HARD — fabric FAULTs:**

- **props on every wall piece:** `wall_segment` (base-4) 1-3 each, `wall_corner` (base-4) **3 each**, `wall_tower.nbt`=6, `gatehouse.nbt`=3, `gatehouse_lvl1`=3.
- **slab_riders:** `gatehouse_lvl3`=1, `gatehouse_lvl4`=2, `wall_corner_lvl1`=1, `wall_corner_lvl4`=3.
- **roof holes:** `gatehouse_lvl1`=2, `gatehouse_lvl3`=2, `gatehouse_lvl4`=2 (all at his band ceiling of 2).
- **stray spikes:** `wall_tower` lvl1-4 each = 8 (the `cobblestone_slab@(0,13,2/4)` + `(1,13,1/5)` quartet — a repeated parapet-cap pattern pinging the checker at every level).

**SOFT / measured-good:**

- **Real growth:** wall_segment/corner/tower internal self-similarity **0.54-0.58** (below the 0.79 band — varied); base→top growth cosine **0.08-0.16** (the levels change substantially). This is what development looks like; it is what barracks/armory/training_yard lack.
- **timber hoarding top tier works:** `wall_segment_lvl4` shows the timber/stone split at `y6` (timber 50 % / stone 50 % = the gallery over the curtain).
- `gatehouse` is the most varied building in the set (self-similarity 0.59, growth 0.11) — the jettied timber storey + arched passage compose.

## Cross-cutting issues

1. **The build gate does not check fabric.** `build_military.py` fails a rung only on `not-usable` (functional) findings (`build_military.py:327`); it never scores `fabric` faults. `build_livestock.py` does (`build_livestock.py:66,78,107,149` — fabric is in the failure score, and "the class of bug cannot ship"). This single wiring gap is why 84 % of military files ship with FAULTs the livestock set cannot.
2. **`selfgate.py` is blind to military.** It hardcodes `LIVESTOCK` (`selfgate.py:36`). The "single most important output" / "no faults" line the project relies on reports on 18 livestock files and silently asserts nothing about the 43 military ones.
3. **Three buildings are unmodified house donors.** armory→`house_3` (cos 1.00), barracks→`house_2` (cos 0.99), and both stay inside their donor's vocabulary across the whole ladder (armory self-sim 0.90, base→top growth 0.75). The "garrison dressing pass" (`military_fittings`) sprinkles props but does not change the building's identity — `SKILL.md` law 8: "sprinkling props" is explicitly listed as what does **not** move sameness.
4. **Fortification style not repainted onto our output** (OPEN-WORK, confirmed): barracks/armory/training_yard carry no `stone_brick` rung; they remain in the house oak/cobble palette while `wall.py` runs a full cobble→mossy→stone_brick ladder. The two grammars never meet on the same building.
5. **Missing devices across the board:** no ground-floor arcade (barracks), no dominant chimney + open work bay (armory), no device development (training_yard). Every composition target in `BUILD_LANGUAGE.md` "Composition targets per building" is unmet for the three buildings; only the four wall pieces meet theirs.
6. **Bottom-slab deck construction** recurs as a class: watchtower deck (18-21 riders/file) + gatehouse/wall_corner parapet caps. Same bug family the livestock set already fixed via `fabric.Canvas` refusal — the military composer is not writing through `Canvas` for these passes.

## What is NOT wrong

- **`check_stairs`: 0 downhill stairs across all 43 files** — the stair-rotation regression that shipped before is genuinely gone.
- **`check_usable`: 0 unreachable floors.** Every military level is enterable; the watchtower is climbable at all 7 rungs (the prior NO-STAIR-on-six-levels bug, OPEN-WORK "Done", holds).
- **The wall set composes correctly.** wall_segment/corner/tower/gatehouse show real level-to-level growth (self-sim 0.54-0.59, growth cos 0.08-0.16), the timber hoarding top tier reads, and `ring_preview.py` verified the perimeter closes (per `BUILD_LANGUAGE.md`).
- **training_yard has the walled-compound silhouette** — walls around an open sandy drill floor; the *shape* target is met, only the *development* and *finish* are not.
- **`check_fabric --calibrate` is silent on the author's 8-file sample** — the metrics themselves are not firing on his work, so the 36 FAULTs are real findings, not checker noise.
- **Roof-plane holes are within band** everywhere (max 2, his ceiling 2) — no hanging roof blocks except the single `barracks.nbt` cantilever.

## Verdict

**Yes — this is beta.** "Shipped" would mean the build gate refuses fabric FAULTs (it does not), the signature devices are present (the watchtower deck floats, the armory has no chimney, the barracks has no arcade), and each building reads as its trade rather than a house donor (armory = house_3 at cos 1.00, barracks = house_2 at cos 0.99). None of those three hold today.

Ranked by impact, the top 3 problems:

1. **The build gate doesn't check fabric, and `selfgate` doesn't see military** — so the entire quality machinery that made the livestock set ship clean is switched off for this set. Wiring `fabric` into `build_military.py`'s failure score (as `build_livestock.py` already does) and pointing `selfgate.py` at military would have caught every HARD defect below before any file was written. This is the highest-leverage fix because it gates all the others.
2. **The watchtower observation deck is built from a forbidden construction at scale** — 18-21 slab_riders per file (cube-over-bottom-slab = 0 in 125 author files). The tower's central device is geometrically the bug the project already fixed once for livestock pier-caps; it needs the deck floor flipped to top slabs / full blocks and the build written through `fabric.Canvas`.
3. **barracks/armory are untransformed house donors** (cos 0.99/1.00 to `house_2`/`house_3`, missing the arcade / chimney-and-work-bay devices, no fortification material ladder) — three of the six building kinds have no identity of their own, which is the "reads as a big house" failure `BUILD_LANGUAGE.md` warns about by name.

## Related

- [OPEN-WORK](OPEN-WORK.md)
- [STATUS](STATUS.md)
- [.agents/skills/burg-buildings/SKILL.md](../../.agents/skills/burg-buildings/SKILL.md)
- [docs/05-craft/BUILD_LANGUAGE.md](../05-craft/BUILD_LANGUAGE.md)
