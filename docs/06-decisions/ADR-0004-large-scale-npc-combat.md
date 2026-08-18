# ADR-0004: Large-scale NPC combat (60+ soldiers) via a custom battle state-machine, not vanilla mob AI

- **Status**: Accepted
- **Date**: 2026-07-31
- **Decided by**: owner (grilling session)

## Context

The owner's ambition, recorded in [`VISION.md`](../01-vision/VISION.md), is
realm-vs-realm war at the scale of Mount & Blade or Manor Lords — armies at the
walls, not skirmishes in a field. Act 5 names this the load-bearing,
deliberately-unsolved design problem of the whole project.

Minecraft's vanilla mob AI will not carry it. Vanilla AI is 1v1-oriented (target,
path, strike, repeat) and collapses the server tick well before battle scale: at
40+ actively-fighting entities per chunk the TPS drops out from under the fight,
and the war is lost to lag before it is lost to swords. Every option that keeps
vanilla AI in the loop is a known TPS-killer at the scale VISION demands.

During the grilling (Q5/Q6), three scales were put on the table: small skirmishes
(≤20 NPC), medium bands (20–60), or large campaigns (60–200). The owner chose
**option C — large campaigns** — and ruled that the combat system is built for
that ceiling, not throttled down to what vanilla AI happens to survive.

Feasibility was checked against the Villager Recruits mod (talhanation/recruits,
"All Rights Reserved" — patterns studied, code not copied), which reaches exactly
this scale by offloading target-scan to a daemon thread (`AsyncManager`), driving
squads through leader attack controllers (`IAttackController`), and expressing
soldier behaviour as 30+ granular `Goal` classes behind formation command GUIs.
The patterns are proven; the code is not ours to lift.

## Decision

Combat is a **separate subsystem with its own battle state-machine** — not an
extension of the existing `SimpleStateMachine`, which models building upgrades and
has the wrong shape for a fight. The two machines coexist in the codebase and do
not share a base.

A soldier is a **Role on the existing `Npc` class**, not a new entity — pillar 4
(extend, don't add entity classes) holds inside the combat layer as it does
everywhere else. The subsystem comprises per-soldier `Goal`s, squad-leader
tactics, formation commands, and a throttled tick scheduler that pushes
target-scan off the main thread and runs soldiers at LOD-based near/far tick
rates so a 120-NPC battle costs what a 40-NPC vanilla brawl would.

The command UI is a radial menu in the Mount & Blade style — hold F1, pick a
category, issue "charge" / "hold" / "shield wall" — so the player commands a
battle he does not fight by hand (see ADR-0005 for where his own sword is and is
not allowed).

## Consequences

- + Unlocks realm-vs-realm war at scale — the load-bearing endgame system the
  vision depends on.
- + The Villager Recruits patterns (async target-scan, leader attack controllers,
  goal-AI, formation commands) are a proven proof-of-feasibility we can study
  without copying their code.
- − Months of engineering: combat AI from scratch, formation logic, an async
  scheduling layer, and a whole command UI.
- − Two state machines now live in the codebase (building + battle), different in
  nature and not unified — a deliberate cost, not an accident.
- Rules out: vanilla-mob-AI combat, a proven TPS-killer at this scale; and pure
  abstract / auto-resolve, which loses the emotion of watching troops at the walls.

## Related

- [`VISION.md`](../01-vision/VISION.md) — act 5, war at scale, the unsolved problem this addresses.
- [ADR-0001](ADR-0001-earned-crown-trajectory.md) — the trajectory this combat system serves.
- [ADR-0005](ADR-0005-ruling3-raid-vs-war.md) — where the player's own sword is permitted (raid) vs forbidden (war).
- [`../03-design/war/README.md`](../03-design/war/README.md) — the war design layer (to be written against this decision).
