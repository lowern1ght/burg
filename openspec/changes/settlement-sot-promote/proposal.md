# Why
Dual-write construction queue and quest log caches (ADR-0016). Mirror ADR-0013 StockLedger discipline.
# Capabilities
## Modified Capabilities
- domain-settlement: construction queue cache + stampQuestCompletion
- domain-settlement: quest log cache
# Impact
Town sync + stampQuestCompletion; NBT keys ConstructionQueue, QueueReservedStock, ActiveQuests, QuestDefLastCompleted unchanged.
