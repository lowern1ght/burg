# War and combat at scale — design

Army-scale conflict between realms. [VISION](../../01-vision/VISION.md) named
this the hardest open problem in the mod and deferred it; the 2026-07-31
grilling settled the scale (large campaigns, custom battle state-machine),
patched ruling 3 into a scale distinction, and named the three ways to take a
village. What remains open is the garrison pool model and the casualty
replacement rate.

## Decisions

1. **Scale is C: large campaigns with a custom battle state-machine, not
   vanilla mob AI.** 60+ NPC combat with a purpose-built battle state-machine.
   This is confirmed feasible by the Villager Recruits mod
   (`github.com/talhanation/recruits`), which does exactly this: async
   target-scan offloaded to a daemon thread (`AsyncManager`), squad-leader
   controllers (`IAttackController`), 30+ granular `Goal` classes (melee,
   ranged, defend, follow, hold, mount, dodge, …), and formation GUIs. The
   shape is proven; Burg builds its own.

2. **Ruling 3 is patched into a scale distinction (raid-scale vs war-scale).**
   "The player never wins a war with his own sword"
   ([VISION](../../01-vision/VISION.md)) is refined, not repealed:
   - **Raid-scale** — attacking a village whose defenders are workers-turned-
     militia: the player *can* fight personally with vanilla combat. A geared
     player can solo the garrison. The mod does not touch vanilla PvP or
     combat mechanics.
   - **War-scale** — realm-vs-realm armies, sieges, campaigns: NPC-vs-NPC,
     always. The player commands and supplies; he never swings the sword.

3. **Three ways to take a village: siege, NPC assault, solo raid.** Capture is
   one of the three acquisition paths (VISION) and now has a resolution
   mechanic for each taste:
   1. **Siege** — economic pressure until capitulation. Always available, slow,
      realistic, generates stories.
   2. **NPC assault** — send soldiers, auto-resolve via the combat
      state-machine. Faster, costs casualties.
   3. **Solo raid** — the geared player fights the worker-militia personally
      (raid-scale). Pays in time and risk, not soldiers.

4. **Combat AI is a separate subsystem from the build state machine.** The
   existing `SimpleStateMachine` drives building; battle gets its own state
   machine: per-soldier goals + squad-leader tactics + formation commands + a
   throttled tick (async target-scan, near/far level-of-detail). The two do
   not share a class.

5. **Command UI is a Mount & Blade-style radial overlay.** Hold a key →
   categories (movement, combat, formations) → "charge", "hold position",
   "shield wall" (the F1-style radial). Client-side overlay; it sends commands
   to the server, it does not grant the player direct unit control beyond
   issuing orders.

## Open questions

1. **Garrison pool model.** The autonomy slider
   ([VISION §"autonomy–control slider"](../../01-vision/VISION.md)) says a
   captured village obeys only under garrison and revolts if it is withdrawn;
   war consumes that garrison. *Why it matters:* the same pool suppressing
   revolt at home and fighting abroad is a real strategic tension — or a
   bookkeeping annoyance.
   - One garrison pool per town, deployable abroad or held for suppression;
     pulling it to war risks revolt at home.
   - Separate "home guard" and "field army" pools, no shared tension.

2. **Casualties, recruitment, replacement.** An army that never bleeds is a
   button; one that bleeds too fast makes war suicidal. *Why it matters:* war
   has to cost something or it is the only rational path. Barracks throughput
   caps army size (the training yard already exists, ROADMAP act 5);
   casualties take real time to replace — the leading option, not yet fixed.

## Dependencies

- **Needs:** the `Realm` layer and `acquisition` field
  ([realm stub](../realm/README.md)); soldier NPCs and morale
  ([npc stub](../npc/README.md)).
- **Blocks:** diplomacy's war/truce state; the capture acquisition path.
- Performance: the custom battle state-machine's throttled tick (async
  target-scan, near/far LOD) needs a pathfinding/tick budget measurement
  before it is committed to.

## Status

`design settled 2026-07-31 (pending implementation)` — scale, the ruling-3
patch, the three capture paths, the separate combat subsystem, and the command
UI are decided. No code, no contact sheet, no measurement yet; the garrison
pool and casualty replacement questions above are scoped for the first
implementation pass.

## Related

- [VISION](../../01-vision/VISION.md) — §"the honest scale problem", §"what
  becomes a starting role"
- [ROADMAP](../../02-roadmap/ROADMAP.md) — act 5 army from the barracks
- [../realm/README.md](../realm/README.md) — capture as acquisition path
- [../npc/README.md](../npc/README.md) — soldier role, morale under garrison
- [../diplomacy/README.md](../diplomacy/README.md) — war as one relation state
