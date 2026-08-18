# ADR-0011: Construction queue domain carve

- **Status**: Accepted
- **Date**: 2026-08-19

## Decision

Expose the player's construction queue as Minecraft-free domain types:

- `ConstructionIntent` (sealed `NewBuild` / `Upgrade`; world pos as `Long.toString(BlockPos.asLong())`)
- `ConstructionQueue` (immutable ordered list, `EMPTY`, capacity, enqueue/dequeue)

`Town.constructionQueue` / NBT keys `ConstructionQueue` + `QueueReservedStock` remain source of truth. `Town.constructionQueueView()` rebuilds the domain view on read.

## Non-goals

No change to BuildExecutor, queue claims, or NBT shape.
