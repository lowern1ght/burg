# ADR-0019: Hub becomes a window — the act-4 transition (first gameplay carve)

- **Status**: Accepted
- **Date**: 2026-08-19
- **Builds on**: ADR-0009, ADR-0011, ADR-0014, ADR-0016, ADR-0018

## Context

Ruling 3 (ROADMAP.md §"Three rulings" + VISION.md §"the hub is a window")
reads the town hub as a *window onto town intent*, not a *command console
the player queues into*. Today the hub is the latter: the player right-
clicks the Town Anchor, picks a building, the queue grows. The grilling
of 2026-07-31 settled that the act-4 transition flips this — at the
structural act-4 threshold, the hub becomes a read-only view and the
player's lever becomes supply, not orders.

This change is the **first gameplay carve** of that transition. It
lands three things on the bare JVM and two seams in `Town.java`:

1. The domain value objects that name the act-4 transition:
   `HubMode { CONSTRUCTION, SUPPLY }` and the `HubView` record that
   carries the mode + the empty/present predicate the GUI will branch
   on.
2. The `Town#hubView()` accessor that derives the HubView from the
   construction queue + acquisition — derived, not stored, so a
   server with this change and an old world has no migration.
3. The application-layer routing of the construction setter
   (`Town.tryAddToConstructionQueue`) through `ConstructionQueue.enqueue`
   as a domain method, matching the carve shape ADR-0018 calls out for
   the act-4 player path.

It also adds a single TODO comment to `C2SDepositPacket` for the
deposit-path rewiring (the act-4 deposit path migrates to
`new SupplyStock.Handler(adapter).handle(...)` per ADR-0018 §"diffuse
rewiring to hub-becomes-window"). That migration is deliberately
deferred: it lands with the SUPPLY-mode widget carve so the wiring and
the widget set arrive in one PR.

## Decision

### Domain types (JDK-only)

| Type | Kind | Where | Shape |
|---|---|---|---|
| `HubMode` | enum `CONSTRUCTION, SUPPLY` | `domain/settlement/` | additive default `CONSTRUCTION` |
| `HubView` | record `(HubMode mode)` with `EMPTY` sentinel | `domain/settlement/` | `EMPTY` is `(CONSTRUCTION)`, referentially stable |

Both are part of the settlement bounded context; both are covered by
the `DomainPurityTest` fence (`noMinecraftImports` +
`noMinecraftTypeNamesAsTypes`). `HubViewTest` (bare JUnit) pins the
shape: empty is referentially stable, mode is preserved, record
equality is per-component.

### `Town#hubView()` — derived, not stored

The HubView is recomputed at every call. The access is O(1) today —
one `constructionQueueView().isEmpty()` check plus an enum compare — so
the caching discipline `StockLedger`/`ConstructionQueue`/`QuestLog`
use (cached field, sync at every mutation site, rebuild on miss) is
overhead without payoff. When the SUPPLY-mode widget carve adds
content to HubView (intent list, stock-gap, supply widget set), the
caching discipline lands under it.

```java
public HubView hubView() {
    if (constructionQueueView().isEmpty()) return HubView.EMPTY;
    if (acquisition != Acquisition.ELEVATED && acquisition != Acquisition.FOUNDED) {
        return HubView.EMPTY;
    }
    return new HubView(HubMode.SUPPLY);
}
```

The empty path covers two cases the spec calls out separately:
**constructionView empty** (the town has nothing for the player to
influence) and **acquisition outside the act-4 set** (`FREE` for a
town the player has not been elevated into; `CAPTURED` for a town
under a different realm's hold). In both cases the HubView falls back
to the legacy CONSTRUCTION shape — today's screen, rendered
unchanged. The additive carve preserves all existing player-visible
behavior; the SUPPLY mode is what changes it.

`Town#hubMode()` is a thin convenience accessor (`hubView().mode()`)
for callers that need only the mode — gateways, log lines, debug
menus. No caller in this PR uses it yet; it lands with the widget
carve that needs it.

### Construction setter — routed through `ConstructionQueue.enqueue`

The construction setter (`Town.tryAddToConstructionQueue`) builds the
operation as a `ConstructionIntent.NewBuild` first and validates
against the immutable `ConstructionQueue` view's capacity before
mutating the legacy list. The legacy list stays the NBT-roundtrip
owner and the source of truth on disk; the domain projection is the
shape the act-4 SUPPLY-mode widget reads back from.

This is the carve-shape application of "make Hub setter forward to
`ConstructionQueue.enqueue` through a domain method" (per ADR-0018's
"or just" alternative — the heavier AdjustStanding-shaped use case +
port infrastructure lands when the act-4 widget needs it, not before).
The setter still mutates the legacy list directly; the domain intent
is the value object the legacy list mirrors.

```java
public boolean tryAddToConstructionQueue(String defId) {
    BuildingDef def = BuildingDataHandler.get(defId).orElse(null);
    if (def == null) return false;
    long entryId = nextEntryId++;
    ConstructionIntent intent = new ConstructionIntent.NewBuild(entryId, defId);
    // Domain-side validation: would the immutable queue accept this intent?
    if (!constructionQueueView().hasCapacity()) return false;
    // Weight cap: block new builds (not upgrades) that would exceed the era limit.
    if (getCurrentWeight() + def.weight > getCurrentMaxWeight()) return false;
    TownInventory inv = getTownInventory();
    for (ItemCost cost : def.constructionCost) {
        if (inv.getStock(cost.item()) < cost.amount()) return false;
    }
    inv.removeStock(def.constructionCost);
    for (ItemCost cost : def.constructionCost) {
        queueReservedStock.merge(cost.item(), cost.amount(), Integer::sum);
    }
    constructionQueue.add(new QueueEntry.NewBuild(entryId, defId));
    syncConstructionQueueFromLegacy();
    return true;
}
```

No `nextEntryId++` moved, no caller signature changed, no NBT rename,
no `QueueEntry` rename — the carve is additive over the existing
method body. The `entryId` minting is the only structural change; the
id was previously stamped at the legacy-list-append line and now
stamps at the domain-intent-construction line so the two stay in
lockstep on the same `nextEntryId` counter.

### `C2SDepositPacket` — TODO only, no rewiring

Per ADR-0018 §"diffuse rewiring to hub-becomes-window", the act-4
player-facing deposit path migrates to `new SupplyStock.Handler(adapter).
handle(...)` when the SUPPLY-mode widget lands in `TownHubScreen`. This
PR adds a single TODO comment on the packet recording the follow-up
and pointing at ADR-0018 §"What this does NOT do (today)".

The motivation for the deferred migration is the same as ADR-0018's:
`Town.getTownInventory().addStock` shares the `reserveStock` map with
the StockLedger cache (`Town.stockLedger()` rebuilds on every read);
routing through `SupplyStock.apply(...)` would `clear()` the map
first (`applyStockLedger` semantics) and re-merge from the ledger —
but the current `applyStockLedger` does not know about `queueReservedStock`
re-pricing, which is at least one other reserve mutator. The
half-doing-it-here version of this migration would silently drop
state; the doing-it-right version requires a future carve that
formalizes the reserveStock ownership story.

### Spec deltas — strict-validating, full scenarios

The OpenSpec change lands full delta specs for the three affected
capabilities:

- `construction-mode-supply-mode/spec.md` — three new ADDED requirements
  (hub has two modes; structural predicate; hub UI respects mode).
- `player-role/spec.md` — one MODIFIED requirement
  ("hub becomes a window (act 4) — permadeath on the transition")
  with a permadeath-on-the-transition scenario + a stranger-mode
  scenario. Strengthened with SHALL/MUST language for strict
  validation this PR.
- `npc-builder-actor/spec.md` — one MODIFIED requirement
  ("builder stays alive on its own — builder consumes player-supplied
  items in SUPPLY mode") with a new SUPPLY-mode scenario. Strengthened
  with SHALL/MUST language for strict validation this PR.

`openspec validate hub-becomes-window --type change --strict` exits 0
on the resulting state.

### What does NOT land this PR

- No `TownHubScreen` fork, no SUPPLY-mode widget set.
- No `_supply_` packet.
- No `TownAnchorBlock.use()` branching on `Town#hubMode`.
- No `BuilderConfigDataHandler` keys (`hub.transition_standing_threshold`,
  `hub.transition_structure_required`).
- No `C2SDepositPacket` rewiring (TODO comment only).
- No `C2SQueueBuildingPacket` rewiring (lands with the widget carve).
- No end-to-end Town-fixture predicate test (lands with the widget
  carve; the bare-JVM `HubViewTest` covers the type shape this PR).

These all have explicit tasks in `openspec/changes/hub-becomes-window/
tasks.md`; the next carve picks them up.

## Consequences

- + The act-4 transition has a domain shape: `HubMode` names the two
  modes, `HubView` carries the empty/present predicate, `Town#hubView()`
  derives the mode from the existing queue + acquisition state without
  a migration. Worlds saved before this PR load unchanged; the predicate
  recomputes on next read.
- + The construction setter expresses the operation as a
  `ConstructionIntent` first; the legacy list mutation is a mirror of
  the domain intent, not the source. This is the carve-shape route to
  "AdjustStanding-shaped call" — the heavy infrastructure (use case +
  port + adapter) lands when the act-4 widget needs it.
- + The spec deltas are full scenarios (not just proposal-level) and
  validate strict-clean.
- + `DomainPurityTest` fence is green over the new types; the spec
  deltas are aligned with the cap spec language.
- − `hubView()` is derived per call (no cache). Cheap today; becomes a
  cache candidate when the SUPPLY-mode widget content lands.
- − The TODO on `C2SDepositPacket` is a one-line marker; the rewiring
  itself is a future carve. Future agents touching the packet must
  read ADR-0018 §"What this does NOT do (today)" before doing more
  than the TODO promises.
- − The full predicate test (Town fixture + standing book + structural
  flags) is not exercised on the bare JVM this PR. The HubView type
  shape is, but the act-4 transition end-to-end requires a Minecraft-
  free Town facade — that's a future carve too.

## Verification

- `./gradlew :common:test --no-daemon` exits 0 — `DomainPurityTest`
  green, `HubViewTest` green alongside the existing suite.
- `openspec validate hub-becomes-window --type change --strict`
  exits 0 (no warnings).
- No file under `behavior/` appears in the diff.
- No NBT key rename, no `QueueEntry` rename, no `Town` field rename —
  the carve is additive over the existing surface area.

## Related

- [ADR-0008](ADR-0008-ddd-foundation.md) — bounded contexts, the
  Minecraft-free fence that covers the new domain types.
- [ADR-0009](ADR-0009-standing-acquisition.md) — the additive NBT
  discipline the HubView inherits (no migration on old worlds).
- [ADR-0011](ADR-0011-construction-queue.md) — `ConstructionQueue`
  domain method the construction setter routes through.
- [ADR-0014](ADR-0014-settlement-application.md) — the application-
  layer seam pattern (`AdjustStanding`/`SupplyStock` + ports) the
  heavier wire-up follows when it lands.
- [ADR-0016](ADR-0016-queue-quest-dual-write.md) — the dual-write
  strangler facade the HubView piggybacks on (derived, not stored).
- [ADR-0018](ADR-0018-application-wiring.md) — the application-
  wiring note that defers `C2SDepositPacket` rewiring to this change
  (and the present PR defers it further to the widget carve).
- `openspec/changes/hub-becomes-window/` — the change proposal +
  delta specs + tasks.