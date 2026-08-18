# ADR-0003: A farm fence never turns to stone

- **Status**: Accepted
- **Date**: standing rule; formalised 2026-07-31
- **Decided by**: owner

## Context

The fortification style in [`STYLE.md`](../05-craft/STYLE.md) runs a material
ladder that earns stone as the village rises. It would be natural — and wrong —
to apply that same ladder to a farm boundary, so that a "richer" pasture gets a
stone wall instead of a timber fence.

The owner ruled against this, more than once, on the realistic grounds that a
better year on a farm buys straighter timber, closer posts, boards and a proper
gate — not masonry. Masonry belongs to the byre plinth, the trough kerb and the
dip basin: the parts that actually carry weight or hold water. The run does not.

This keeps the livestock set deliberately off the fortification style: a farm is
oak, cobblestone and mossy cobblestone, with `mud` for the pigs and `packed_mud`
as the one sanctioned addition. Its progression comes from devices rather than
richer stone.

## Decision

The boundary of a livestock farm is oak at every rung. The progression runs
**rail → doubled hurdle → boarded panel**, differing per animal: cattle get an
airy post-and-rail, sheep get close-set hurdles, pigs get boarded early because
they push.

Stone on a farm belongs only to the **byre plinth**, the **trough kerb** and the
**dip basin** — never to the run. The stone-pier fence of `05d4` stays a
fortification and garden device; it does not migrate onto a pasture.

## Consequences

- The livestock palette stays small (oak / cobblestone / mossy cobblestone, plus
  `mud` and `packed_mud`); the fortification material ladder does not reach into
  a byre.
- `pasture.py` keeps its own palette rather than importing `wall.py`'s
  fortification ladder. Coupling a cattle shed to the wall material ladder is
  forbidden — a wall-tier change must not reach into the byre.
- The boundary grammar, not the material, carries the rung: a higher rung is
  closer timber and boards, not stone.

## Related

- [`CLAUDE.md`](../../CLAUDE.md) §"Livestock, and animals" — the source of the ruling.
- [`../05-craft/STYLE.md`](../05-craft/STYLE.md) — the fortification style this set deliberately does not follow.
