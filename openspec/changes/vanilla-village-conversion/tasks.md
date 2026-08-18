# tasks — `vanilla-village-conversion`

## 1. Bridgehead NBT

- [ ] 1.1 Author `common/src/main/resources/data/onceuponatown/structure/plains/bridgehead.nbt`
  in Blockbench. Vanilla format (jigsaw-compatible). Size: 5x3x5 footprint.
  Two outward-facing `ConnectionPoint`s on opposite faces. Materials
  consistent with `plains/` style (no stone furniture, mixed
  dirt/oak_log/dirt_wall palette).
- [ ] 1.2 Run `python tools/check_integrity.py plains/bridgehead.nbt` and
  paste the 5-primitive result into the commit message.
- [ ] 1.3 Run `python tools/selfgate.py` and paste the drawn-section output
  for `bridgehead.nbt` into the commit message.

## 2. TownAnchor bind path

- [ ] 2.1 Add a `BindResult` enum and a new constructor path on
  `Town` that takes a vanilla-village meeting point + the populated
  footprint list (existing houses). Reuse the regular `Town(...)` ctor
  after registering.
- [ ] 2.2 In `TownAnchorBlock.use()`, branch on whether the placement
  world location is within an unregistered vanilla village (vanilla
  `Village` POI detection). If yes, run the bind path; if no, fall through
  to today's hub-open behavior (the change does not touch act-3+ hubs).
- [ ] 2.3 Add the "anchor unbreakable without operator privilege" guard
  on placement success — see `specs/npc-builder-actor` §"trying to break
  the anchor".

## 3. Bridgehead placement

- [ ] 3.1 Identify the village edge nearest to vanilla's empty grass;
  flat-ground check (the burg-buildings skill rules apply).
- [ ] 3.2 Place the NBT at that coordinate, generate a fresh
  `ConnectionPoint` per outward face.
- [ ] 3.3 Worldgen-jigsaw the bridgehead into the existing vanilla
  village's none-connection system: the bridgehead is *not* a vanilla
  jigsaw piece, but the worldgen step that builds it does not require
  vanilla-jigsaw compatibility — it is direct placement.

## 4. Build green + verify in game

- [ ] 4.1 `gradle :common:compileJava` — exit 0, no warnings.
- [ ] 4.2 `openspec validate vanilla-village-conversion --type change` —
  exit 0.
- [ ] 4.3 Manual smoke-test inside an SMP test world: spawn in plains,
  `/locate biome minecraft:plains`, walk to the nearest vanilla village,
  place the anchor. Verify: binding chat messages, bridgehead appears,
  enlisted villagers do not despawn on first night, NPC builder attaches
  the first new piece within 30 in-game minutes.
- [ ] 4.4 Record the walk-through to `docs/07-state/WALKS/act0-bridgehead.md`.
  STATUS.md `worldgen` row moves from `build-green` to `verified-in-game`
  only after that file exists and links the session notes.

## 5. STATUS.md

- [ ] 5.1 Update `worldgen` row: "Plains/meadow vanilla-village
  conversion + bridgehead piece landed (change hub-becomes-window /
  vanilla-village-conversion). Verified-in-game on a recorded walk-through
  dated YYYY-MM-DD."
- [ ] 5.2 Do NOT mark `verified-in-game` until step 4.4 is complete and
  linked. A green build is not evidence.
