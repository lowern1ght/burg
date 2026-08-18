# ADR-0016: Dual-write construction queue and quest log

- **Status**: Accepted
- **Date**: 2026-08-19
- **Builds on**: ADR-0011, ADR-0012, ADR-0013

## Decision

Mirror the `StockLedger` discipline from ADR-0013 onto the two read-only
strangler facades from ADR-0011 + ADR-0012:

- `Town.constructionQueueView()` now returns a cached `constructionQueueDomain`
  field, kept in sync at every known mutation site
  (`tryAddToConstructionQueue`, `tryQueueUpgrade`, `forceQueueUpgrade`,
  `removeFromConstructionQueue`, `consumeQueueEntry`, `fromNbt`). A cheap
  consistency check (`constructionQueueCacheIsConsistent`) detects the
  "missed a sync" case and falls back to a full rebuild via
  `syncConstructionQueueFromLegacy()`.
- `Town.questLog()` now returns a cached `questLogDomain` field, kept in
  sync at every known mutation site (`addQuest`, `removeQuest`,
  `cleanupOrphanedQuestData`, `stampQuestCompletion`, `fromNbt`). Same
  consistency-check + rebuild-fallback shape as the queue.
- New sanctioned write path: `Town.stampQuestCompletion(defId, gameTime)`.
  Replaces the previous `town.getQuestDefLastCompleted().put(...)` idiom
  at call sites (currently only `C2SContributeQuestPacket.handle`); the
  accessor stays read-only and the new method is the only entry point
  that mutates the completion map while keeping the cache in sync.

NBT keys `ConstructionQueue`, `QueueReservedStock`, `ActiveQuests`,
`QuestDefLastCompleted` are unchanged.

## Non-goals

No `applyConstructionQueue` / `applyQuestLog` write path this PR — a
domain→MC write for the queue would have to re-price `queueReservedStock`
from each entry's building def (needs `BuildingDataHandler` lookups and a
refund cycle), and a domain→MC apply for the quest log would have to
re-materialize the rich per-quest data `Quest` carries (conditions,
rewards, TaskDef binding) that `QuestRef` does not. Sync-from-legacy
covers the read direction; both write sides stay future carves.
