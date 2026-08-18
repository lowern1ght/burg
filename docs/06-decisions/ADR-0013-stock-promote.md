# ADR-0013: Dual-write StockLedger with reserveStock

- **Status**: Accepted
- **Date**: 2026-08-19

## Decision

Keep `reserveStock` as NBT SoT. Cache a `StockLedger` field synced after mutations (`addStock`, queue refund, `fromNbt`, `TownInventory` callback). Add `applyStockLedger(StockLedger)` for domain→MC writes (skip unknown ItemIds).

NBT key `ReserveStock` unchanged.

## Non-goals

ProductionManager not rewritten; ledger is not yet the sole SoT.
