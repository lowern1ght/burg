# ADR-0005: Ruling 3 refined — raid-scale allows the player's own sword; war-scale stays NPC-only

- **Status**: Accepted
- **Date**: 2026-07-31
- **Decided by**: owner (grilling session)

## Context

Ruling 3, as recorded in [`VISION.md`](../01-vision/VISION.md) and
[`ROADMAP.md`](../02-roadmap/ROADMAP.md), is constitutional: *the player never
wins a war with his own sword — war is NPC-vs-NPC, supplied and ordered but not
fought by hand.* ADR-0001 carried it forward unchanged, and
[`PHILOSOPHY.md`](../01-vision/PHILOSOPHY.md) leans on it as a hard ban on
player-fought war.

During the grilling (Q5), the owner judged this too absolute. A geared player —
enchanted armour, good weapons, the vanilla progression the player has been on
since act 0 — *should* be able to take a village garrison of workers-turned-militia
solo. That is a raid, not a war; the garrison are not a professional army. The
principle the ruling protects (war is a strategic, NPC-fought layer) is sound; the
scale at which the player's own combat is allowed was drawn one notch too tight.

This is a refinement of a constitutional ruling, so it is recorded here rather
than edited silently into the canon. Ruling 3 is **patched, not repealed**.

## Decision

Ruling 3 is split by **scale, not principle**:

- **Raid-scale** — attacking a village whose defenders are worker-militia — the
  player **can** fight personally, through vanilla combat. The mod does not touch
  vanilla PvP or combat mechanics. A geared player can defeat such a garrison
  alone.
- **War-scale** — realm-vs-realm armies, sieges, campaigns at the scale of
  ADR-0004 — is **always NPC-vs-NPC**. The player commands, supplies and watches;
  he does not swing the sword. The combat subsystem in ADR-0004 is the only
  resolution path at this scale.

The line is the nature of the defenders: workers with tools are a raid; an army
in formation is a war. A solo raid becomes a third way to take a village, alongside
siege and NPC-assault.

## Consequences

- + The player's vanilla gear progression stays relevant into the late game via
  raids — the enchanted-kit grind is not wasted once war begins.
- + The strategic war layer remains untouched and NPC-only; the thing ruling 3
  actually protects is preserved.
- + A third path to taking a village (solo raid) opens up beside siege and
  NPC-assault, widening the late-game play space.
- − The raid/war boundary must be legible to the player — a visual or UI cue that
  says "these are workers with tools, not an army", or he walks a raid into a war
  and dies.
- − Ruling 3 is now conditional rather than absolute; the hard-ban table in
  [`PHILOSOPHY.md`](../01-vision/PHILOSOPHY.md) needs a footnote pointing here.

## Related

- [`VISION.md`](../01-vision/VISION.md) §"What becomes a starting role" — ruling 3's home.
- [ADR-0001](ADR-0001-earned-crown-trajectory.md) — carried ruling 3 forward; this refines it.
- [ADR-0004](ADR-0004-large-scale-npc-combat.md) — the war-scale combat that stays NPC-only.
- [`RULINGS.md`](../01-vision/RULINGS.md) and [`PHILOSOPHY.md`](../01-vision/PHILOSOPHY.md) — the canon this patches.
