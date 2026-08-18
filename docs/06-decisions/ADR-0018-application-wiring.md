# ADR-0018: Application wiring note — no call-site rewiring in this PR

- **Status**: Accepted
- **Date**: 2026-08-19

## Context

The application layer (ADR-0014) ships `AdjustStanding` and `SupplyStock` use cases that call a `Town` through the `TownStandingPort` / `TownStockPort` interfaces. The use cases are exercised by `AdjustStandingTest` and `SupplyStockTest` against fakes — the contract is unit-tested on a bare JVM. The Town adapters (`TownStandingAdapter`, `TownStockAdapter`) are infrastructure-only and exist for the eventual wire-up.

A natural next step would be to replace direct `Town.adjustStanding(UUID, int)` and `Town.getTownInventory().addStock(...)` call sites with the use cases. The motivation is correctness — `SupplyStock` (the use case) explicitly rejects zero and negative quantities, while `TownInventory.addStock` is silent. Routing player-facing deposits through `SupplyStock` would surface malformed packets.

## Decision

**Do not rewire call sites in this PR.** The rewiring has shape risks the tests cannot catch:

1. **Town.getTownInventory() returns a shared Map view.** `TownInventory.addStock` mutates the same `reserveStock` map that `Town.stockLedger()` rebuilds from on every read. Routing through `SupplyStock.apply(ledger, itemId, qty)` would `clear()` the map first (`applyStockLedger` semantics) and re-merge from the ledger — but the wiring between the legacy reserve and the new ledger is only sound if the call site is the SOLE mutator of `reserveStock`. There is at least one other mutator (`queueReservedStock` re-prices a removed entry), and the current `applyStockLedger` does not know about it.

2. **Town.adjustStanding is fine as-is.** The use case `AdjustStanding` is a 1:1 wrapper — its only added value is the `CitizenId` overload. Direct callers (`Town` internals only — no external player-facing call site yet) use the UUID form. Rewiring internal callers in this PR is busy-work; the value is in the future player-facing entry point, which is a different PR.

3. **Use case wiring belongs in the act-4 transition (`hub-becomes-window` change)** — that's where player-facing call sites for "supply materials" land. Mixing the wiring here scatters the migration across two PRs and makes the diff harder to reason about.

## Consequence

The use case classes stay alive but unreferenced from `town/` and `tick/`. They are exercised by bare-JVM tests against fakes. Adapters exist but are also unreferenced. The day a player-facing entry point lands in `hub-becomes-window`, the call site is `new AdjustStanding(adapter).run(uuid, delta)` — same shape as the existing tests.

## Migration checklist for the eventual wire-up PR

- [ ] `Town.adjustStanding(UUID, int)` becomes a thin delegate to `AdjustStanding(adapter).run(uuid, delta)` for the act-4 player path.
- [ ] `SupplyStock` replaces the **player-facing** stock deposit path (`C2SDepositPacket`, `/town deposit` command) — internal founders (`Town.tryAddToConstructionQueue` reserve-pricing) keep the direct path.
- [ ] Adapters get registered in the production wiring (`TownApplication` holder in `infrastructure/`, plumbed through the existing Town `getTownInventory` seam).
- [ ] `SupplyStockTest` and `AdjustStandingTest` continue to pass against fakes; add one integration test on the bare JVM that wires a real `TownStandingAdapter` + `TownStockAdapter` against a `Town` and asserts no behavior change on the legacy reserve / standing map.
- [ ] Delete this ADR. (The wiring is no longer "future"; it is now the act-4 path.)
