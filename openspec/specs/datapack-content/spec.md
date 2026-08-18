# specs/datapack-content/spec.md

## Purpose

Encode **Pillar 3** — *"datapack-first content"* — into a testable capability. New buildings, eras, quests, prices, and builder behavior are added by dropping JSON files into a datapack; code changes are not required for content additions. The pillar is **eternal**.

Source: P3 (PHILOSOPHY.md §"Pillar 3").

---

## Requirements

### Requirement: the five JSON handlers are the content contract

Burg MUST load and apply mod content through these five datapack handlers — no others. A feature that bypasses the handlers (a hardcoded building list, a code-only quest trigger, a non-JSON era) is a regression and fails this spec.

| Handler | Owns | Reads |
|---|---|---|
| `BuildingDataHandler` | Building definitions, `construction_cost`, `production`, `weight`, level layout | `data/onceuponatown/buildings/*.json` |
| `EraTransitionDataHandler` | Era tree, tier transitions, era prerequisites | `data/onceuponatown/era/*.json` |
| `QuestDataHandler` | Quest definitions, prerequisites, deliverable, reward | `data/onceuponatown/quests/*.json` |
| `BuilderConfigDataHandler` | Builder behavior tuning (placement speed, work hours, etc.) | `data/onceuponatown/builder/*.json` |
| `TradePriceDataHandler` | Stock prices, buy/sell rates per item | `data/onceuponatown/trade/*.json` |

#### Scenario: a new building ships in a datapack
- **WHEN** a user drops a JSON file matching the building schema into their datapack's `data/onceuponatown/buildings/<id>.json` and points `nbt` at a valid vanilla-format structure
- **THEN** the building appears in the catalog on next server start without any JAR modification, code recompile, or restart of the world file.

#### Scenario: datapack override wins
- **WHEN** a datapack defines an entry with the same `id` as one in `common/src/main/resources/data/onceuponatown/`
- **THEN** the datapack's entry is loaded and the bundled one is ignored. This is how a server admin rebalances without a fork.

### Requirement: schema stability across versions

A JSON file in `data/onceuponatown/` shipped with Burg vX MUST load without error in any future vY ≥ X within the same major. Adding optional keys is allowed; renaming or removing existing keys is a breaking change that requires a version-gated migration.

#### Scenario: legacy JSON loads cleanly
- **WHEN** a 1.0.0 datapack is mounted on a 1.x.x server
- **THEN** `BuildingDataHandler` (and the four others) parse every entry; unknown keys are ignored, missing required keys produce a clear load-time error in `logs/latest.log`.

### Requirement: hot-reload is not a promise

Five JSON loaders MUST reload on server start. Mid-session JSON edits are NOT required to take effect without a `/reload` (or equivalent). This requirement documents the contract; it does not promise more.

#### Scenario: edit a price mid-session
- **WHEN** an admin edits `trade/prices.json` while the server is running
- **THEN** the change is reflected after `/reload`; before reload, the old price is in effect. No silent inconsistency.

### Requirement: single source of truth per concept

Each concept (a building, an era, a quest, a price, a builder behavior) MUST live in **exactly one** JSON file under the right folder. Two files defining the same `id` MUST be rejected at load time. This is data, not code — no `#include` or partials.

#### Scenario: duplicate id at load time
- **WHEN** two JSON files under `data/onceuponatown/buildings/` define the same `id`
- **THEN** the loader reports a single error naming both files and stops loading that handler; the server fails to start with a clear `logs/latest.log` entry.

---

## Anti-patterns

- ❌ Hardcoded building list inside the construction queue.
- ❌ Code-only quest trigger that bypasses `QuestDataHandler`.
- ❌ Era advancement gated on a `if (era == "industrial")` Java check rather than a JSON-driven rule.
- ❌ JSON keys whose names duplicate Java identifiers (stringly-typed pricing).
- ❌ A new `XxxDataHandler` that does the same job as one of the five — extend, don't fork.

---

## Cross-references

- PHILOSOPHY.md §"Pillar 3" + §"Implementation discipline" Q4 *"Can this be done in a datapack?"*
- ARCHITECTURE.md §"datapack loaders"
- `data/onceuponatown/buildings/<id>.json` fields: `nbt_levels` count from the JSON itself (each building type has its own ladder, never assume).
