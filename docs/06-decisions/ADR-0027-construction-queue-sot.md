# ADR-0027: ConstructionQueue is the source of truth (flip the dual-write)

- **Status**: Accepted
- **Date**: 2026-08-21
- **Builds on**: ADR-0011, ADR-0016
- **Supersedes**: the construction-queue half of ADR-0016 (the quest-log half stays)

## Context

ADR-0011 exposed the MC `List<QueueEntry> constructionQueue` as a read-only
`ConstructionQueueView()` that rebuilt on every call. ADR-0016 cached that
rebuild in a `constructionQueueDomain` field synced at every known mutation
site (`tryAddToConstructionQueue`, `tryQueueUpgrade`, `forceQueueUpgrade`,
`removeFromConstructionQueue`, `consumeQueueEntry`, `fromNbt`), with a
rebuild fallback when the cache disagreed with the legacy list.

The dual-write pattern was safe and cheap, but wasteful and racy:

- every mutation rebuilds the cache eagerly (N allocations per write, even
  when nobody reads the view)
- the consistency check fires on every read, paying for an O(1) emptiness
  test that exists only to catch "missed a sync" bugs that the static
  discipline was meant to prevent
- the act-5 SUPPLY-mode gameplay loop reads `constructionQueueView()` on
  every idle tick per builder per town — the read is a fast-path cache hit
  today, but the "fast path" exists only because of the dual-write

## Decision

Promote the immutable domain `ConstructionQueue` to the source of truth on
`Town`. The MC `List<QueueEntry>` is now a derived view materialized on
demand by `getConstructionQueue()`.

Concretely:

- The field on `Town` is `private ConstructionQueue constructionQueue =
  ConstructionQueue.EMPTY;` — the domain value object IS the state.
- `Town.tryAddToConstructionQueue` / `tryQueueUpgrade` / `forceQueueUpgrade`
  mint a `ConstructionIntent` and assign `constructionQueue =
  constructionQueue.enqueue(intent)`. `removeFromConstructionQueue` and
  `consumeQueueEntry` assign `constructionQueue = constructionQueue.without(idx)`.
- `getConstructionQueue()` rebuilds the legacy `List<QueueEntry>` from the
  domain on every call. The queue is bounded at `QUEUE_CAPACITY` (54), so
  the O(N) materialization cost is negligible; the per-mutation savings
  (no cache rebuild, no consistency check, no sync helper) outweigh the
  per-read cost by a lot.
- `constructionQueueView()` returns the SoT directly — no cache, no
  consistency check, no rebuild fallback. The "missed a sync" safety net
  is gone because the SoT and the cache are the same field now.
- `QueueEntry.toIntent(QueueEntry)` and `QueueEntry.fromIntent(ConstructionIntent)`
  are the new MC ↔ domain boundary converters. They live on `QueueEntry`
  because `QueueEntry` already hosts the `serialize` / `deserialize` NBT
  boundary; this is the same shape (one shape, two worlds), just one
  step further.
- `ConstructionQueue.without(int)` is the new domain operation that
  returns a new queue with the entry at `index` removed. Out-of-range
  index is a no-op (returns `this`); a drain-to-zero collapses to
  `EMPTY` — same referential-stability discipline `dequeue` already uses.

The quest-log dual-write (the other half of ADR-0016) is untouched in
this PR — `Quest` carries rich per-quest data (conditions, rewards,
TaskDef binding) that `QuestRef` does not, so flipping the quest log
to be the SoT would require adding the rich shape to the domain
object. That is a future carve; the construction-queue flip does not
unblock any act-5 quest work, so it stays separate.

NBT keys `ConstructionQueue` and `QueueReservedStock` are unchanged.
`toNbt` materializes a `ListTag` of `QueueEntry` NBT compounds from the
domain via `QueueEntry.fromIntent` + `QueueEntry.serialize`. `fromNbt`
reads the same NBT list into a local `List<QueueEntry>`, applies the
pre-ADR-0002 `NextEntryId` restamp if needed, then collapses the legacy
list into a fresh `ConstructionQueue` via `QueueEntry.toIntent` +
`ConstructionQueue.of`. Worlds saved before this change load unchanged.

## Trade-offs

- **Per-read cost goes up from O(1) to O(N) for `getConstructionQueue()`.**
  The queue is bounded at 54 entries. The `SimpleStateMachine` builder NPC
  reads it on every idle tick per builder per town; with the 20-tick
  idle schedule, that's ~3 reads per second per town, and each read is
  at most 54 domain-to-MC conversions plus one ArrayList allocation. The
  cost is in the noise; the dual-write's per-mutation overhead was
  already paying for the same conversions at every write.
- **No `applyConstructionQueue` write path this PR.** The quest-log
  side has the same carve-out (ADR-0016 §"Non-goals") and it stands.
  A domain→legacy apply would have to re-price `queueReservedStock`
  from each entry's building def, which needs `BuildingDataHandler`
  lookups and a refund cycle for whatever the legacy reserve currently
  holds. That's a real change, not a symmetric mirror of
  `applyStockLedger`. The flip makes the write side a future
  concern, not an immediate one.

## Non-goals

- Flipping the quest log to be the SoT (the other half of ADR-0016) —
  deferred to a future carve. See ADR-0016 §"Non-goals" for the
  per-quest data blocker.
- Introducing an `applyConstructionQueue` write path.
- Changing the NBT format. The pre-ADR-0027 wire format is preserved
  byte-for-byte; the load path handles the old format unchanged.
- Refactoring `SimpleStateMachine` or `TownHubDataBuilder` to consume
  the domain `ConstructionQueue` directly. Both currently read through
  `getConstructionQueue()` and `QueueEntry` continues to be the shape
  they work with. Switching them to the domain type is a follow-up carve
  that does not unblock any act-5 work.
