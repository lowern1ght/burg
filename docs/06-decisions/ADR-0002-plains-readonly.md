# ADR-0002: Keep the author's structure/plains/** read-only

- **Status**: Accepted
- **Date**: standing rule; formalised 2026-07-31
- **Decided by**: owner

## Context

`common/src/main/resources/data/burg/structure/plains/**` is the mod
author's finished work — 125 hand-built NBTs, and the corpus every measurement in
[`CLAUDE.md`](../../CLAUDE.md) is calibrated against. It is the reference the
style gates, the fabric checkers and the half-block rule are all staked to. If
that corpus drifts, the rules lose their anchor.

Two pressures work against leaving it alone. First, the temptation to "restyle"
an author building inline rather than painting our own output that sits beside
it. Second, a real maintenance need: four files in the corpus were CRLF-mangled
(in the working tree, in upstream's repo, and in the shipped jars) and had to be
repaired, or those levels could not load in game.

The owner's standing rule names the corpus as read-only, with the repair work as
the single documented exception. This ADR records it so a later contributor does
not mistake `plains/**` for editable ground.

## Decision

`structure/plains/**` is read-only. Read it, harvest from it, measure it — never
write to it.

The one exception is byte-exact CRLF repair (`repair_crlf_nbt.py`), verified
against the gzip CRC: the repaired corpus reads 125 of 125, and the mangled
originals are kept in `backup/plains_corrupt_2026-07-29/`. That exception was
asked for explicitly and is the only one.

Generated sets get their **own** top-level folder (`structure/military/**`,
`structure/livestock/**`); they are never filed under `plains/`, even as a
subfolder. Restyle only what we authored.

## Consequences

- Donor buildings are grafted whole and left as-is; we do not run a finishing
  pass over them, because a donor arrives finished.
- The corpus stays a trustworthy reference — the measurements and the gates keep
  their anchor.
- Re-styling `barracks` / `armory` / `training_yard` has to be a repaint of our
  own output, not of the donors those pieces graft. The donors live in `plains/`
  and stay untouched.
- Never delete before verifying: write the new thing, run every check, and only
  then remove the old — having asked first.

## Related

- [`CLAUDE.md`](../../CLAUDE.md) §"Never touch" — the source of the rule.
- [`VISION.md`](../01-vision/VISION.md) — the corpus is the anchor for every style measurement.
