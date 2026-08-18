# Realm / Kingdom layer — design

The `Realm` abstraction that sits above `Town` so a king can hold many
villages. It does not exist today — `Town` is the top entity in the state
layer ([ARCHITECTURE §"State layer"](../../04-engineering/ARCHITECTURE.md)).
The 2026-07-31 grilling settled the conceptual shape (a metropolis plus its
dependent colonies, not a bag of equal villages) and the colony-founding
flow; storage representation and autonomy drift remain open.

## Decisions

1. **A realm is a metropolis plus its dependent colonies, not a bag of equal
   villages.** The player starts one settlement; it grows village → city. When
   the city needs external resources (wood, ore, food, livestock), it sends a
   party 1000+ blocks away to found a *small resource village* that depends on
   the metropolis. Trade, exchange, and delivery flow between them. This is
   the load-bearing correction to the earlier "ruler + `villages: List<Town>`"
   sketch in [VISION](../../01-vision/VISION.md): the member towns are not
   peers, they are a capital and its supply network.

2. **Colonies are founded by player-equipped expeditions (Hybrid C).** The
   metropolis signals a need (through conversation with the chief, or a
   rathaus announcement); the player equips the expedition (assigns people,
   gives resources, indicates a search zone); the NPCs go and found it. This
   is the supply-steers-building pattern at macro scale — the player provides,
   the NPC builder executes (pillar 4 holds even here).

3. **Water is a hard constraint on colony placement.** Settlements need water
   (rivers, lakes). Worldgen must supply it: if no natural water exists at the
   colony site, the generator spawns a lake or stream. This is a placement
   rule, not a hope — a colony without water is not a valid site.

4. **The three acquisition paths apply to foreign villages; the player's own
   colonies are always founded.** Elevated / founded / captured
   ([VISION §"three ways to hold a village"](../../01-vision/VISION.md))
   describe how a *foreign* (AI) village becomes yours. Your own colonies are
   always the "founded" path, by expedition. The two scales do not mix.

5. **`Town.acquisition` gains a `COLONY` value; the autonomy slider gates hub
   access and order-taking.** The field is now `FREE | ELEVATED | FOUNDED |
   CAPTURED | COLONY`, and the autonomy–control slider
   ([VISION §"autonomy–control slider"](../../01-vision/VISION.md)) gates the
   construction queue and order-taking paths against it. The hub's current
   unconditional queue acceptance is stranger-phase behaviour and gets gated
   once `acquisition` exists.

6. **Kingship is proclaimed at a threshold, in the rathaus; ceremony is
   optional.** When the metropolis and its colonies reach a threshold, the
   player can proclaim himself king (an option in the rathaus). A ceremony is
   optional and gives a bonus to colony loyalty; without it, he is king by
   fact. This replaces the earlier "N villages / quorum of chiefs" options —
   the threshold counts the metropolis + colony network, not peer villages.

## Open questions

1. **Realm storage representation.** The conceptual shape is settled; the
   storage is not. *Why it matters:* it sets the save-format migration.
   - New `LevelRealms` `SavedData` mirroring `LevelTowns`; a `Town` gains a
     nullable `realmId`.
   - `Realm` as a value object on the player profile; `Town` carries
     `acquisition` + a back-reference.
   - How a player with no realm is represented.

2. **Autonomy drift over time.** *Why it matters:* it decides whether a held
   village is a static line or a living relationship.
   - A founded village hardens toward `ELEVATED` (loyalty with age) — or stays
     at its founding autonomy forever.
   - A colony's autonomy as it matures.

3. **Save-format migration specifics.** Adding `Realm` and the
   `acquisition`/`autonomy` fields to `Town` changes `toNbt`/`fromNbt`.
   *Why it matters:* existing saves must load as `FREE` villages under no
   realm without data loss. Default-on-load: every pre-migration `Town` reads
   as `FREE`, `realmId = null`; verify with a converted-save fixture before
   shipping.

## Dependencies

- **Needs:** a `chief` NPC per town ([npc stub](../npc/README.md)) for the
  elevation path; an `acquisition`/`autonomy` field on `Town`.
- **Blocks:** diplomacy and war both act on `Realm`; the autonomy slider gates
  every command path in those systems.
- Hub changes: the unconditional queue acceptance is stranger-phase and must
  be gated once `acquisition` exists.

## Status

`design settled 2026-07-31 (pending implementation)` — the metropolis + colony
shape, the expedition founding flow, the water constraint, and the kingship
threshold are decided. Storage representation, autonomy drift, and the save
migration rule remain open.

## Related

- [VISION](../../01-vision/VISION.md) — §"immediate architecture consequence",
  §"three ways to hold a village", §"autonomy–control slider"
- [ARCHITECTURE](../../04-engineering/ARCHITECTURE.md) — `Town` as today's top entity
- [../npc/README.md](../npc/README.md) — chief emergence
- [../diplomacy/README.md](../diplomacy/README.md) — realm-to-realm relations
- [../war/README.md](../war/README.md) — capture as the forced path
- [../economy/README.md](../economy/README.md) — caravans between metropolis and colony
