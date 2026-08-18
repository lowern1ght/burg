# ADR-0012: Quest log domain carve

- **Status**: Accepted
- **Date**: 2026-08-19

## Decision

Expose active quests + last-completed map as:

- `QuestRef` (defId, type NOTE|TASK, optional status)
- `QuestLog` (immutable refs + lastCompleted ticks)

`Town.activeQuests` / `questDefLastCompleted` and NBT `ActiveQuests` / `QuestDefLastCompleted` stay SoT. `Town.questLog()` is a read-only rebuild.

## Non-goals

No QuestManager rewrite; no datapack schema change.
