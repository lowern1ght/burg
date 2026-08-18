# domain-settlement — settlement-sot-promote

ADR-0016 dual-writes the construction queue and quest log caches on
`Town`, mirroring the `StockLedger` discipline from ADR-0013. The
existing `ConstructionQueue` and `QuestLog` value objects stay exactly
as they were after ADR-0011 / ADR-0012; the new code lives in the
strangler facades on `Town`.

## ADDED Requirements

### construction queue cache

The system **SHALL** keep a cached Minecraft-free `ConstructionQueue`
view on `Town` (the `constructionQueueDomain` field) in sync with the
Minecraft-side `constructionQueue` list at every known mutation site:
`tryAddToConstructionQueue`, `tryQueueUpgrade`, `forceQueueUpgrade`,
`removeFromConstructionQueue`, `consumeQueueEntry`, and the
construction-queue branch of `fromNbt`.

The system **SHALL** detect the "missed a sync" case via a cheap
emptiness + size consistency check (`constructionQueueCacheIsConsistent`)
and fall back to a full rebuild via `syncConstructionQueueFromLegacy()`
when the check fails.

The system **SHALL NOT** rename the NBT keys `ConstructionQueue` or
`QueueReservedStock`.

### quest log cache

The system **SHALL** keep a cached Minecraft-free `QuestLog` view on
`Town` (the `questLogDomain` field) in sync with the legacy
`activeQuests` list and `questDefLastCompleted` map at every known
mutation site: `addQuest`, `removeQuest`, `cleanupOrphanedQuestData`,
`stampQuestCompletion`, and the quest-log branch of `fromNbt`.

The system **SHALL** detect the "missed a sync" case via a cheap
emptiness + size consistency check (`questLogCacheIsConsistent`) and
fall back to a full rebuild via `syncQuestLogFromLegacy()` when the
check fails.

The system **SHALL NOT** rename the NBT keys `ActiveQuests` or
`QuestDefLastCompleted`.

### stampQuestCompletion sanctioned write path

The system **SHALL** expose `Town.stampQuestCompletion(defId, gameTime)`
as the only sanctioned write entry point for the completion map.

The system **SHALL** reject null or empty `defId` and negative
`gameTime` by silently dropping the call.

The system **SHALL** keep `getQuestDefLastCompleted()` as a read-only
view; mutating it directly from outside `Town` is unsupported.

### call-site migration

The system **SHALL** use `stampQuestCompletion` at every site that
records a quest-def completion tick. `C2SContributeQuestPacket.handle`
migrates to `stampQuestCompletion`; the previous
`town.getQuestDefLastCompleted().put(...)` idiom is replaced.

## MODIFIED Requirements

### Town.constructionQueueView

The previous rebuild-on-every-read contract (ADR-0011) is replaced by
the cached fast path with a consistency-check fallback. The return type
and the public signature are unchanged; only the internal cost model
changes.

### Town.questLog

The previous rebuild-on-every-read contract (ADR-0012) is replaced by
the cached fast path with a consistency-check fallback. The return type
and the public signature are unchanged; only the internal cost model
changes.

## REMOVED Requirements

None.

## Non-goals

- No `applyConstructionQueue` (domain → MC write path). Re-pricing
  `queueReservedStock` from each entry's building def needs
  `BuildingDataHandler` lookups + a refund cycle.
- No `applyQuestLog` (domain → MC write path). `QuestRef` carries only
  `(defId, type, status)`; the rich per-quest data `Quest` carries
  (conditions, rewards, TaskDef binding) cannot be re-materialised from
  the domain view.
- No BuildExecutor rewrite, no QuestManager rewrite, no NBT rename, no
  tick-logic change.
