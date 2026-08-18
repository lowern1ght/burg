# ADR-0008: DDD foundation — bounded contexts, layering, and the Town strangler plan

- **Status**: Accepted
- **Date**: 2026-08-19
- **Decided by**: owner (architecture session)

## Context

[`ARCHITECTURE.md`](../04-engineering/ARCHITECTURE.md) already names it:
`Town` is a ~1000-line god-object. Everything that mutates village state
goes through it — buildings, construction queue, quests, production, era,
standing, chat subscribers — and its `toNbt()`/`fromNbt()` pair is the de
facto save format. Meanwhile the roadmap needs systems that do not fit
inside one class: the Realm layer above Town (act 5), diplomacy between
realms, war-scale NPC combat ([ADR-0004](ADR-0004-large-scale-npc-combat.md)),
and a growing content corpus of building/era/quest definitions.

A big-bang rewrite of `Town.java` is not an option: the plains corpus is
read-only calibration data, existing worlds must keep loading, and the
mod's behavior is verified in-game, not in unit tests — a rewrite would
destroy the only evidence base we have.

## Decision

The codebase moves toward **domain-driven design**, landed as a foundation
(docs + package skeleton + capability specs) with a **strangler** migration
out of `Town`. Nothing gameplay-facing changes in this step.

### Bounded contexts

Five contexts, aligned with the design docs in `03-design/`:

| Context | Domain of | Design doc |
|---|---|---|
| **Settlement** | one town's life: buildings, queue, production, standing, quests | `ARCHITECTURE.md` state/tick layers |
| **Realm** | the layer above Town: many villages, acquisition, autonomy slider | `03-design/realm/` |
| **Diplomacy** | relations between realms, AI chiefs, tribute, alliances | `03-design/diplomacy/` |
| **War** | realm-vs-realm NPC combat (ADR-0004/0005 scale) | `03-design/war/` |
| **Content** | shared kernel of building/era/quest definitions (the datapack contract) | `03-design/economy/`, `DATA-FORMATS.md` |

Content acts as the **shared kernel** the other four contexts consume; it
is not a gameplay context of its own.

### Layers inside each context

Classic three, per context:

```
domain/          pure model: entities, value objects, domain events.
                 No net.minecraft, no NeoForge, no I/O.
application/     use cases / orchestration. Depends on domain only,
                 talks to the outside through ports (interfaces).
infrastructure/  adapters: SavedData persistence, NeoForge events,
                 packets, datapack handlers. Depends on domain +
                 application, never the reverse.
```

The dependency rule is one-way: `infrastructure → application → domain`.
A domain test MUST run on a bare JVM with no Minecraft classes on the
classpath — that is the enforcement signal, not review.

### Aggregate and value objects

- **Town remains the Aggregate Root of Settlement.** Production,
  ConstructionQueue, Standing, QuestLog are carved out as entities /
  value objects *later*, one at a time, each behind the same root.
- **Minecraft types leave the domain** behind value-object wrappers:
  `TownId`, `BlockCoord`, `CitizenId`, `ItemId`. Conversion to/from
  `BlockPos`, `UUID`, `Holder<Item>` happens at the infrastructure edge
  only.

### Migration: strangler + shim

- `Town` stays where it is. New code goes into the new packages.
- A `Town` **facade** keeps the same NBT shape (`ouat_towns` SavedData)
  for as long as the strangler runs — old worlds must load unchanged.
- Responsibilities move out of `Town` incrementally, each move verified
  in a running game before the next. No flag-day.

## Non-goals (this change)

- No split of `Town.java` — not in this PR, not partially. The skeleton
  lands empty; carving Production/ConstructionQueue/Standing/QuestLog is
  future work, one carve per change.
- No gameplay behavior change, no new product features, no pillar impact.
- No move of the plains corpus or any NBT assets.
- Package rename is landed separately (ADR-0007); this ADR assumes
  `org.lowern1ght.burg`.

## Consequences

- + Every act-4/act-5 system (realm, diplomacy, war) gets a home that is
  not "another field on Town".
- + The domain layer becomes unit-testable on a bare JVM — the first
  fast feedback loop in a project whose only verification bar is
  in-game.
- + Existing worlds and the read-only corpus are untouched; the risk of
  this step is near zero because it adds empty packages and documents
  intent.
- − Two layouts coexist (`town/`, `tick/`, `datapack/` beside
  `domain/`, `application/`, `infrastructure/`) until the strangler
  finishes — navigation cost, paid deliberately.
- − The layering discipline needs enforcement (architecture test) once
  real classes arrive; a skeleton alone cannot keep Minecraft imports
  out of `domain/`.

## Related

- [`ARCHITECTURE.md`](../04-engineering/ARCHITECTURE.md) §"Target DDD shape" — the map this ADR authorizes.
- [ADR-0004](ADR-0004-large-scale-npc-combat.md) — the War context's combat scale.
- [ADR-0005](ADR-0005-ruling3-raid-vs-war.md) — raid/war boundary the War context must respect.
- `openspec/changes/ddd-foundation/` — the change proposal + `domain-settlement` capability spec.
- Issue [#11](https://github.com/lowern1ght/burg/issues/11) — package rename, orthogonal.
