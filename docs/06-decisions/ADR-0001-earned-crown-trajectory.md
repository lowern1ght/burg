# ADR-0001: Adopt the earned-crown trajectory — stranger-sim becoming lord-sim

- **Status**: Accepted
- **Date**: 2026-07-31
- **Decided by**: owner

## Context

Burg's two founding documents commit hard to the stranger-sim reading.
[`PHILOSOPHY.md`](../01-vision/PHILOSOPHY.md) makes "player helps, not commands"
pillar 2 and lists MineColonies-style colony management, player-controlled NPCs
and combat overhaul as hard bans. [`ROADMAP.md`](../02-roadmap/ROADMAP.md)
reinforces this with three constitutional rulings: the player finds the village,
starts as a stranger, and only ever trades and supplies.

The owner's actual ambition is larger and older than either document: a medieval
life-sim where help, or force, or time can raise a stranger to a king over many
villages — the lineage of Mount & Blade and Manor Lords, reached *through* the
stranger arc rather than instead of it. Read literally, the canon forbids the
second half of that ambition, which left every downstream system (diplomacy,
combat, the state layer) blocked on an unresolved fork.

The fork was resolved on 2026-07-31 in [`VISION.md`](../01-vision/VISION.md).
This ADR records the decision and its consequences so it is not re-litigated.

## Decision

Burg is **both a stranger-sim and a lord-sim, in sequence**. The player's power
is earned by act, not given. Pillar 2 is reclassified from an eternal rule to the
contract of the early game (acts 0–3); the hard bans become the rules of the
stranger/guest phase, lifted in the late game through earned progression.

The trajectory, with the **power** column this decision adds:

| Act | Player's verb | What he can hold | Power over a village |
|---|---|---|---|
| 0 — Arrival | travel | nothing | none — he is not of it |
| 1 — Stranger | look, speak | nothing | none, but he is noticed |
| 2 — Guest | trade, supply | nothing | influence only (standing) |
| 3 — Trusted | choose | nothing yet, but heard | his supply steers what gets built |
| 4 — A town, a wall | keep choosing | a village may name him chief | soft command of one village |
| 5 — The far end | rule, negotiate, conquer | a realm of many villages | hard command, scaled by how each was acquired |

A village becomes "yours" by exactly one of three paths, and the path decides how
obedient it is: **elevated** (named chief through help — most loyal), **founded**
(settled from nothing — loyal but young), or **captured** (taken by force — fast,
but it resists).

## Consequences

- Pillar 2 lifts in acts 4–5 for villages the player founded or captured; the
  hard bans on colony management and player-controlled NPCs become phase-gated
  rather than absolute.
- A `Realm` / `Kingdom` layer above `Town` becomes required architecture — a king
  over many villages, and capture, both need it. It is the next architectural
  decision, tracked separately, not a feature in this ADR.
- Pillar 4 stays eternal: the player never lays a block in a village, even a
  captured one. The founding anchor is the one scoped exception.
- ROADMAP ruling 3 stays: the player supplies, orders and watches wars; he never
  wins one with his own sword.
- The army-scale war problem (Mount & Blade scale vs Minecraft's 1v1 combat) is
  now load-bearing and unsolved. It is named in VISION as act 5's hardest design
  problem and deliberately deferred here.
- Pillars 1, 3, 4 and 5 are unchanged and remain constitutional for every act.

## Related

- [`VISION.md`](../01-vision/VISION.md) — the full resolution; top-level authority.
- [`PHILOSOPHY.md`](../01-vision/PHILOSOPHY.md) — the five pillars; supersede banner at the top.
- [`ROADMAP.md`](../02-roadmap/ROADMAP.md) — the act order; act 5 expanded by VISION.
- [`../04-engineering/ARCHITECTURE.md`](../04-engineering/ARCHITECTURE.md) — the state layer where `Realm` will land above `Town`.
