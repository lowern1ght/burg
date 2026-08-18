# ADR-0014: Settlement application services

- **Status**: Accepted
- **Date**: 2026-08-19

## Decision

Application layer ports + use cases (no Minecraft):

- `TownStandingPort` / `AdjustStanding`
- `TownStockPort` / `SupplyStock`

Infrastructure adapters wrap `Town` (`TownStandingAdapter`, `TownStockAdapter`). DomainPurityTest fences `application/` against `net.minecraft` / `net.neoforged` imports.

## Non-goals

No Town.java edits in this change (avoids merge conflicts with parallel carves).
