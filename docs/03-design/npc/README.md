# NPC life — design

What a villager *is* once the village becomes more than a backdrop: needs,
morale, the emergence of a chief from the population, and how NPCs react when
held by force. Acts 0–3 treat the NPC as a body that builds; acts 4–5 need him
to also choose, resist, and speak for the town. The act 4–5 design was settled
in the 2026-07-31 grilling; what remains open is the conversation surface and
the exact shapes of revolt events.

## Decisions

1. **Morale is two-level: a village baseline plus a per-citizen modifier.**
   The baseline comes from the acquisition path (founded/elevated = high,
   captured = low) and town-wide factors (food supply, crowding, garrison
   presence); the per-citizen layer carries personal factors (family killed in
   war, well-fed, friend executed). Quests come from individuals — the baker
   offers a quest based on the baker's mood — and market trade depends on the
   merchant's mood, so morale has to live on both levels. Rebellion seeds in
   one or two citizens and either spreads or fizzles. The player never sees a
   number; he reads morale through behaviour (slow work, refusal to build,
   complaints in conversation, empty streets, open revolt).

2. **Demography is already built; families and inheritance are not.** `DaySim`
   runs births with crowding-control, deaths from causes (not old age),
   emigration as the release valve, and the `Departure` enum records *why*
   someone left. Homelessness already exists as a feature (crowding →
   discontent → people leave). Still open as future work, not as design
   questions: families as entities (who is whose child), inheritance of
   profession and wealth, and child growth stages.

3. **A soldier is a Role on the existing `Npc` class, not a new entity.** Per
   pillar 4 (extend `Npc`, don't add classes), an NPC takes up arms, joins a
   formation, and follows the combat AI subsystem. No subclass unless a
   benchmark proves the single class breaks pathfinding or the tick budget at
   army size — a defer that the war doc's scale decision
   ([../war/README.md](../war/README.md)) now makes unlikely.

4. **The chief is a named citizen who emerges from the population.** A
   high-influence individual, not a spawned actor: the town's speaker and the
   required counterpart for diplomacy between realms. Emergence is from
   standing and influence within the town, not player nomination, which keeps
   the elevation path ([VISION](../../01-vision/VISION.md)) honest — the
   village names the player chief, not the other way round.

## Open questions

1. **Conversation beyond the trade screen.** Act 1's right-click a person
   (ROADMAP) is the only interaction built or planned. Diplomacy and the chief
   role need more: negotiation, orders, refusal. *Why it matters:* the chief
   NPC and the player-as-king both act through conversation
   ([../diplomacy/README.md](../diplomacy/README.md)), so the surface has to
   carry more than trade.
   - `Merchant`-style standalone screen per conversation type (trade, parley,
     command).
   - A single dialogue screen whose options change with standing and role.
   - Notes-and-barks only, no negotiation UI — keep pillar 5 (vanilla feel).

2. **Revolt event mechanics.** The morale model settles *that* a captured town
   resists and *how* it reads to the player; the specific event shapes do not.
   *Why it matters:* revolt is the cost of the capture path and the only thing
   distinguishing it from a willing puppet
   ([../war/README.md](../war/README.md)).
   - Passive output penalty (slow building, low production) keyed off morale.
   - Active revolt events (strike, sabotage, garrison desertion) on a timer.
   - Both, gated by garrison strength — pull the garrison and it boils over.

## Dependencies

- **Needs:** a `morale`/loyalty field on `Npc` and a baseline on `Town`; the
  schedule (sleep/work, ROADMAP act 1) before morale is meaningful.
- **Blocks:** the realm and war stubs both assume an NPC can hold a `role` and
  a morale value; diplomacy assumes the chief NPC can speak for the town.
- Save-format migration: new fields on `Npc` and possibly `Town` must round-
  trip through `toNbt`/`fromNbt`
  ([ARCHITECTURE §"State layer"](../../04-engineering/ARCHITECTURE.md)).

## Status

`design settled 2026-07-31 (pending implementation)` — the morale model, the
soldier-as-role, and chief emergence are decided. Acts 0–3 NPC AI (the build
state machine) is built and verified by contact sheet only; the two open
questions above are scoped for a later pass.

## Related

- [VISION](../../01-vision/VISION.md) — autonomy slider, three acquisition paths
- [ROADMAP](../../02-roadmap/ROADMAP.md) — act 1 schedule, act 5 chief
- [ARCHITECTURE](../../04-engineering/ARCHITECTURE.md) — `Npc` as single class
- [../realm/README.md](../realm/README.md) — chief → king chain
- [../war/README.md](../war/README.md) — soldier role, army scale
- [../diplomacy/README.md](../diplomacy/README.md) — chief as speaker for the town
