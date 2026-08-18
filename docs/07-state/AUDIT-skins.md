# Audit — citizen skins (2026-07-31)

The shipped citizen skins are **better than the "beta" label implies at the level of a single
face, but beta where it matters for a village**: each individual body is competently drawn and
satisfies the contrast/nose/hair laws the skill exists to enforce, but the *roster* is a small
number of face-and-torso templates repainted across 14 files, and the two systems that are
supposed to make a town read as varied — wealth tiers and identity keying — are architecturally
present but **not actually wired**. The single highest-impact defect is that `NpcLook.wealthOf()`
returns a constant, so every citizen renders at the UNDYED rung forever and the four-tier wealth
ladder (plus the trim layer) is dead code at runtime.

## Method

All measurements taken from the live texture directory
`common/src/main/resources/assets/onceuponatown/textures/entity/npc/`, run through
`tools/skin_text.py` (the project's "read a skin as text" tool, since an agent cannot see a PNG).
Files inspected:

- **14 current bodies**: `citizen_body_00.png` … `citizen_body_13.png` — confirmed as the wired
  set; `CitizenLook.body()` (`client/CitizenLook.java:295`) builds `citizen_body_<slug>.png` and
  both renderers go through it (`CititizenRenderer.getTextureLocation:71`,
  `TownVillagerRenderer` per its retired-array comment at line 73).
- **5 hair files** `citizen_hair_00..04`, **3 beards** `citizen_beard_01..03`, **5 headwear**
  `citizen_headwear_01..05`, **8 garments** (`builder/chief/farmer/forester/mason/smith/soldier/
  soldier_veteran_clothes`), `citizen_trim.png`, `default_skin.png`.
- **Retired-on-disk sets** (law 9 — retire, don't delete): `citizen_skin_0..5`,
  `citizen_skin_f0..5`, and the 96-file `citizen_m|w_cN_fM` matrix. Read for comparison; not
  shipped.

Tools run: `skin_text.py <file> --face head` on every body and a sample of the retired sets;
`--face hat` on all hair; full dump on all 8 garments; `--diff` on eight body pairs for the
crowd metric; `--no-stats` colour-count pass over all 14 bodies. The 31 owner reference skins
are **not** in the repo (only `references/corpus.md`), so the reference numbers below are cited
from that measured table. `skin_text.py` deliberately does not emit a single "brow-vs-cheek"
number (its header explains why — a layout-guess-derived number is worse than none); the
per-row luminance spread it does emit is used as the proxy, against the repo's own 7-point
invisibility threshold.

No PNG was redrawn, regenerated, or modified. The 31 references were not touched. Nothing was
copied from the reference corpus into any shipped asset.

## Findings by layer

### Bodies / complexions / faces

**The single face is good. The crowd it makes is not.** Each individual body clears the
contrast and nose laws; the roster does not clear the "two people being two people" bar.

**Colour count (law 5) — drawn, but ~25 colours short of the reference median, and slightly
below even the first drawn pass.** Distinct colours per body file, from `skin_text.py` stats:

| file | colours |  | file | colours |
|---|---|---|---|---|
| body_00 | 115 |  | body_07 | 103 |
| body_01 | 114 |  | body_08 | 110 |
| body_02 | 108 |  | body_09 | 104 |
| body_03 | 108 |  | body_10 | 113 |
| body_04 | 116 |  | body_11 | 108 |
| body_05 | 105 |  | body_12 | (in range) |
| body_06 | 100 |  | body_13 | 101 |

Range **100–116, median ≈ 108.** Reference median is **139** (`corpus.md`); the flat generated
set was 17; the first two hand-drawn bodies were **122 and 118**. So the current 14 are
dramatically better than the generated disaster and solidly "drawn", but they sit at **~78% of
the reference density and have actually drifted down from the first drawn pass (118–122 →
~108).** The shading is a touch thinner than it was on the first two.

**Brow and nose contrast (laws 3, 4) — satisfied per face.** Per-row luminance spread on the
head front (7 = invisible, per this repo's own measurement):

| body | row 3 brow | row 5 nose | row 6 mouth | rows 1–2 forehead |
|---|---|---|---|---|
| 00 | 54.0 | 48.2 | 69.4 | **4.9 / 5.4 flat** |
| 01 | 67.5 | 69.1 | 80.0 | 9.9 / 12.6 |
| 02 | 49.0 | 49.4 | 52.0 | 7.1 / 9.3 |
| 07 | 48.6 | 54.2 | 57.1 | 9.8 / 9.8 |
| 13 | 56.9 | 58.4 | 66.0 | 8.0 / 10.0 |

Brow spread 48–67 against a reference brow contrast of **60.7**; nose spread 48–69 against a
reference nose-bridge contrast of **57.3**. Both sit in the right band — this is **not** the
"flat but colourful" body (that one was 21.3 / 11.3). Law 3's core failure mode did not recur.

**The nose bridge is LIT, not shadowed (law 4) — correct.** Spot-checking the grids: `body_00`
row 5 `usrDClnx` — bridge cols 3,4 are `D`,`C` (lum 149/148, the bright end of the file's ramp),
flank col 5 is `l` (lum 100.8, shadowed), col 2 mid. Bridge lighter than flank, only one flank
shadowed. `body_13` row 5 `tsrDClpw` — same pattern (bridge `D`,`C` at 133/131, flank `l` at 75).
The direction law 4 demands (lit bridge, single shadowed flank) holds. A 2px shadow merging
into the mouth — the old vanilla-derived failure — does not occur.

**No villager-monobrow drift (law 3).** The brow cells on every sampled body are darker steps
of the **flesh ramp** (`k`,`l`,`y` in body_00 — lum 96–144), never a near-black hair tone.
Villager-ness has not crept back in.

**The forehead is the one flat region.** Rows 1–2 (above the brow) sit at 4.9–12.6 spread;
`body_00` rows 1 and 2 are flagged `< 7` (invisible). The face below the brow is modelled; the
forehead above it largely is not. Minor, but it is the only place a face goes flat.

**The crown is painted (the "crown is a quarter of the cube" law).** Every sampled body paints
the head `[top]` face; the region does not go ungated. That specific shipped defect (a groove
down the crown no view covered) did not recur here.

**The crowd problem — quantified, and it is real.** `--diff` reports two numbers; SHAPE is the
honest one and 35% is the roster floor for two different people (`skin_text.py:278`,
`corpus.md`). Head and body SHAPE-diff across pairs:

| pair | head SHAPE | body SHAPE | arms | legs |
|---|---|---|---|---|
| 00 ↔ 01 | **33%** | **33%** | 35% | 87% |
| 08 ↔ 09 | **38%** | 53% | 38% | 94% |
| 10 ↔ 11 | 46% | **32%** | **32%** | 92% |
| 02 ↔ 13 | 84% | **20%** | 41% | 75% |
| 03 ↔ 04 | 67% | 38% | 53% | 94% |
| 05 ↔ 06 | 59% | 70% | 77% | 99% |
| 00 ↔ 07 | 86% | 41% | 40% | 91% |
| 00 ↔ 13 | 90% | 37% | 47% | 94% |

Two failures are clear: **(a)** several adjacent pairs are at or below the 35% floor on the
head (00↔01 at 33%, 08↔09 at 38%) — those are the *same face* repainted; **(b)** the **torso**
(`body`) is consistently the samey region — 20–38% SHAPE across most pairs, bottoming at **20%**
between 02 and 13. The 14 bodies are not 14 people; they are a handful of face templates and a
near-constant shift-torso, repainted with different flesh/cloth ramps. The legs vary a lot
(75–99%) — trousers-vs-hose — so the variety that exists lives below the waist, exactly where
the garment covers it. This is the complaint the nine laws exist to answer, and the roster
answers it only partially.

### Hair

**Law 2 (silhouette is texture, not geometry) — satisfied; the 42-cube regression did not
return.** All five `citizen_hair_*` files paint the `hat` region only (the +0.5 inflated shell),
at 118–287 of 384 cells. Both renderers state it explicitly — `CitizenRenderer.java:46` and
`TownVillagerRenderer.java:86`: *"Hair, beard and headwear: PAINT on the `hat` cube the rig
already carries, not geometry. 31 of 31 reference skins use the head's second layer; the cubes
this replaces also cost a black screen."* `NpcHeadModels` does register `HAIR_LAYERS` /
`BEARD_LAYERS` / `HEADWEAR_LAYERS` arrays, but those back texture variants rendered by
`NpcHairLayer`, not extra cubes.

**Variety and coverage — adequate, greyscale-correct.** Five distinct alpha silhouettes:
`hair_00` short cap (180 cells), `hair_01` mid/long (215), `hair_02` full-frame long (287),
`hair_03` balding crown-only (118), `hair_04` mid back-length (170). All are drawn in near-white
greys (lum 100–252), which is **correct** for the architecture — hair colour is a render-time
tint on the layer (law 6: "hair colour | tint on the hair layer | free"), so the drawn file must
start bright or the multiply has no range.

**Crown coverage respected.** Where a hair file covers the crown it paints `[top]` (`hair_00`,
`01`, `02`, `04` full; `hair_03` intentionally bare in the centre — the balding style). No
unpainted crown groove.

The only hair-level concern is **count**: five silhouettes is thin against the 14 bodies and the
reference corpus's 31/31 hat usage; combined with the samey bodies, two UUID-adjacent citizens
can share both face and hair.

### Garments (trade clothes)

**Law 7 mask — correct, and shared consistently across all eight trades.** Reading the
`body_outer [front]` off each garment file, every one of the eight uses the same sleeveless
deep-V mask: torso rows 6–11 fully covered (`99999999`-style), rows 0–5 carrying only the
shoulder wedges at cols 0–1 and 6–7 with the V-neck cut out of cols 2–5 (e.g. `farmer` rows 0–5
`99....99` / `999...99`; identical pattern in `smith`, `builder`, `chief`, `soldier`,
`soldier_veteran`, `forester`, `mason`). `r_arm_outer` / `l_arm_outer` carry only the 4–5-row
shoulder-cap taper (~25 of 224 cells) — sleeveless, as specified. The mask gate law 7 asks for
would pass on `farmer_clothes.png`.

**Trade legibility — rests entirely on palette, and holds at the UNDYED rung.** Because the mask
is shared, trades differ only by colour: `farmer` earth-browns (lum 74–201), `smith` sooty dark
browns/near-black (44–176), `forester` greens (53–213), `mason` stone-greys + brown apron
(63–202), `soldier` blue-grey steel (20–188), `soldier_veteran` very dark/black (1–99),
`builder` neutral grey, `chief` deep crimson (17–194). Each reads as its trade. Colour counts
104–116 — drawn, not flat. `NpcLook.CLOTHES` maps vanilla professions correctly (farmer, mason,
weaponsmith/toolsmith/armorer→smith, fletcher→forester). `chief` and `builder` are mod-authored.

**The shared mask is a legibility ceiling, not a defect** — law 7 explicitly wants one mask so
the garment layer can stay. But it means no trade has a silhouette of its own; a smith and a
farmer are the same shape in different colours. That is the intended design, not a bug; it just
moves all the burden of "is this a different person" onto the body and the tint.

### Wealth tiers

**The four wealth tiers are built and **not wired** — this is the largest single "beta" defect
in the system.** `NpcLook.wealthOf(Mob)` (`client/NpcLook.java:196`) is the documented seam and
it returns `DEFAULT_WEALTH` (`UNDYED = 1`) unconditionally. Its own javadoc is honest: *"It
returns a constant today… Wealth is the first axis in this system that is not derivable from the
UUID… needs a synced summary and does not have one yet."*

Consequences, measured against the architecture in the same file:

- `TINTS_BY_WEALTH` (`NpcLook.java:117`) is a 4-rung ladder (FADED / UNDYED / DYED / COSTLY),
  each row researched (madder, weld, woad, murrey…). **Only row 1 is ever selected.** Rows 0, 2,
  3 — the worn, the dyed, and the woad-and-trim — are dead at runtime.
- `citizen_trim.png` and `TRIM_TINTS` (`NpcLook.java:157`) draw the braid at the COSTLY rung
  only (`trim()` returns null unless `wealthRung == COSTLY`). COSTLY never fires, so **the trim
  layer is never rendered.** A rich citizen is visually identical to a destitute one.
- `people.Wealth` (`DESTITUTE/POOR/COMFORTABLE/RICH` with nugget floors 0/16/96/512) and
  `Wealth.of(purse)` are entirely unused by rendering. The population model's wealth "does not
  reach the client" — the comment at line 183-194 says so outright.
- The tint roll **does** work within the dead rung: `clothesTint` → `Citizens.tintOf` picks 1 of
  the 4 UNDYED fleece tints per UUID, so two farmers differ slightly. But the variety budget is
  capped at 4 undyed colours forever.

Net: a prosperous town is **not** "visibly a prosperous town, from a distance, with no number
on any screen" — which is the explicit goal in `Wealth.java:20`. The mechanism is half-built:
the ladder, the enum, the trim layer, and the braid tints all exist and are calibrated, and the
one line that would animate them returns a constant.

## Cross-cutting

**Identity keying (law 8) — UUID-derived, not person-derived; the law is not satisfied.** Law 8
states *"Everything visible derives from the PERSON's id, never from the entity's UUID… key the
look to the body and somebody who walks out of range returns as a different human being."* The
current code keys on the entity UUID everywhere: `Citizens.nameOf` → `CitizenNames.of(getUUID())`
(`Citizens.java:207`); `isFemale` → `CitizenNames.isFeminine(getUUID())` (`:226`);
`faceOf`/`tintOf` → `CitizenNames.variant(getUUID(), …)` (`:303`); and `CitizenLook.of()` reads
`mob.getUUID()` directly for every axis (`CitizenLook.java:260-279`). A server-side override path
**exists** on `CitizenData` (the `variant()` helper checks `field.applyAsInt(data(villager))`
first, `Citizens.java:300`), but it is deliberately not published to the client — *"Client:
always the UUID's answer, because an override is not published and a chief is server-authored"*
(`:294-295`). So on the rendering side the look is 100% UUID-keyed. This is exactly the state
law 8 was written to prevent. It is not yet causing visible identity drift in practice because
citizens today *are* the villager entities (one stable UUID per spawn, not recycled), but the
"body is a puppet lent to a record" model the law assumes does not exist yet; the look is keyed
to the puppet, and the override the law wants is half-wired and client-invisible.

**No compliant contact sheet exists (the "before you report" law).** The only sheets on disk are
two stale artefacts in `tools/structures/out/npc/` — `clothes_sheet.png` (13 KB) and
`garments_sheet.png` (82 KB), both dated 2026-07-29 — that predate the `citizen_body_*` set.
Nothing carries the four views the skill mandates: a 26× head row, no-garment **and** with-garment
rows, a crowd row of 8–10, and a wealth row. The crowd row in particular — *"the only view that
answers 'a village reads as one face repainted'"* — has not been generated, so the samey-roster
finding above has not been eyeballed by a human at the scale a player sees. Regenerating it is a
prerequisite for any fix work, not a follow-up.

## What is NOT wrong

- **The tinting architecture (law 6) is correct and verified.** Complexion is drawn (base pass
  is hardcoded `-1`, cannot be tinted); hair colour, garment colour, and beard colour are tints
  on their layers. The `citizen_hair_*` / `citizen_beard_*` / `citizen_trim` files are correctly
  near-white greys so a multiply has range. The past transparency bug (the `0x000A0000` overlay
  alpha=0 leftover that drew garments fully transparent, `NpcLook.java:247-253`) is fixed —
  `clothesTint` returns full-alpha ARGB.
- **Hair is texture-on-shell (law 2).** No geometry cubes; the 42-cube crash did not return.
- **The face contrast laws (3, 4) hold per face.** Brow 48–67, nose 48–69, lit bridges, no
  monobrow, no villager drift. This is genuinely the hard-won part.
- **The garment mask (law 7) is correct and consistent** across all eight trades.
- **Trade palettes are legible** at the (stuck) UNDYED rung.
- **The crown/top face is painted** on bodies and hair — the "crown is a quarter of the cube"
  defect did not recur.
- **Law 9 is being respected** — three superseded skin sets (`citizen_skin_*`, `citizen_skin_f*`,
  the 96-file `citizen_m|w_c*_f*` matrix) are retired-on-disk, not deleted, and the renderers
  carry comments naming what retired them.
- **The retired sets confirm the trajectory:** the 96-file generated matrix is the corpus's "17
  colours, 0 noses, 3 alpha masks across 12 files" disaster (verified — `citizen_m_c0_f0` and
  `citizen_w_c0_f0` are byte-identical 7-colour files with an unlit nose; `citizen_skin_0`
  carries a stray pure-green pixel and inverted eye placement). The current `citizen_body_*` set
  is a real improvement over all of them.

## Verdict

Yes, this is beta — but the beta is **not in the pixels, it is in the wiring and the roster.**
The three highest-impact problems, in order:

1. **Wealth is dead at runtime.** `NpcLook.wealthOf()` returns a constant, so the entire
   four-rung dye/trim ladder — the system specifically designed to make a prosperous town read
   as prosperous — never fires. Every citizen renders UNDYED forever; rich and destitute are
   identical. Months of calibrated dye research and the `citizen_trim` layer are unreachable
   code. This single wired-up line is the difference between "the wealth system exists" and "it
   doesn't."

2. **The body roster is a few faces repainted, not 14 people.** Head SHAPE-diff between
   adjacent bodies drops to 33% (00↔01) and 38% (08↔09) — below the 35% "two different people"
   floor — and the torso is the samey region across the board (20–38%, bottoming at 20% for
   02↔13). The 14 files realise roughly a handful of face-and-torso templates cycled through
   different ramps; the variety that exists lives in the legs, where the garment hides it. This
   is precisely the "village reads as one face repainted" complaint the nine laws exist to
   answer, and the current roster only partly answers it.

3. **Identity keys on the entity UUID, not the person (law 8), and no compliant contact sheet
   exists to catch the above by eye.** `CitizenLook.of()` and every `Citizens.*` accessor derive
   from `mob.getUUID()`, with a server override that is deliberately unpublished to the client.
   Layered on top, there is no current 26× / no-garment+garment / crowd-of-8-10 / wealth contact
   sheet — the four views the skill requires before any skin work is called done — so the samey
   roster and the dead wealth tiers have not been seen at the scale a player actually views.

The individual face, taken alone, is the part that is **not** beta — it clears laws 3, 4, and 6,
and law 2 is firmly back in force. The work remaining is variety (more distinct face/torso
templates so the roster clears the 35% floor pairwise), wiring (`wealthOf` → the synced
`Wealth.of(purse)`, plus publishing the override), and the review surface (regenerate the
contact sheet before calling any of this fixed).

## Related
- [.agents/skills/burg-skins/SKILL.md](../../.agents/skills/burg-skins/SKILL.md)
- [.agents/skills/burg-skins/references/corpus.md](../../.agents/skills/burg-skins/references/corpus.md)
- [OPEN-WORK](OPEN-WORK.md)
- [STATUS](STATUS.md)
